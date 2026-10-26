package com.github.kr328.kaidl

import android.os.*
import java.lang.IllegalArgumentException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*

private object KaidlScope : CoroutineScope by CoroutineScope(Dispatchers.IO)

private abstract class CompletableBinder : Binder() {
    abstract fun onComplete(data: Parcel)

    abstract fun onCanceled()

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            TRANSACTION_complete -> {
                onComplete(data)
            }
            TRANSACTION_canceled -> {
                onCanceled()
            }
            else -> return super.onTransact(code, data, reply, flags)
        }

        return true
    }

    companion object {
        const val TRANSACTION_complete = IBinder.FIRST_CALL_TRANSACTION + 0
        const val TRANSACTION_canceled = IBinder.FIRST_CALL_TRANSACTION + 1
    }
}

private abstract class TransactionContext : Binder() {
    abstract fun requestCancel()

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            TRANSACTION_requestCancel -> requestCancel()
            else -> super.onTransact(code, data, reply, flags)
        }

        return true
    }

    companion object {
        const val TRANSACTION_requestCancel = IBinder.FIRST_CALL_TRANSACTION + 0
    }
}

suspend fun IBinder.suspendTransact(code: Int, data: Parcel, reply: Parcel): Boolean {
    var finalizer: () -> Unit = {}

    return try {
        val result =
            suspendCancellableCoroutine<Parcel> {
                val completed = AtomicBoolean(false)
                val completable =
                    object : CompletableBinder() {
                        override fun onComplete(data: Parcel) {
                            if (!completed.compareAndSet(false, true)) return
                            // A canceled caller may already have recycled its reply. Binder
                            // callbacks
                            // must only own private data; transfer it after successful coroutine
                            // resume.
                            val snapshot = Parcel.obtain()
                            try {
                                snapshot.appendFrom(data, 0, data.dataSize())
                                snapshot.setDataPosition(0)
                            } catch (error: Exception) {
                                snapshot.recycle()
                                it.resumeWithException(error)
                                return
                            }
                            it.resume(snapshot) { _, value, _ -> value.recycle() }
                        }

                        override fun onCanceled() {
                            if (completed.compareAndSet(false, true)) it.cancel()
                        }
                    }

                val r = Parcel.obtain()

                data.writeStrongBinder(completable)

                val context: IBinder =
                    try {
                        transact(code, data, r, 0)

                        r.readException()

                        r.readStrongBinder()
                    } catch (e: Exception) {
                        if (completed.compareAndSet(false, true)) it.resumeWithException(e)
                        return@suspendCancellableCoroutine
                    } finally {
                        r.recycle()
                    }

                val link =
                    IBinder.DeathRecipient {
                        @Suppress("ThrowableNotThrown")
                        if (completed.compareAndSet(false, true))
                            it.resumeWithException(DeadObjectException())
                    }

                finalizer = {
                    try {
                        context.unlinkToDeath(link, 0)
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                context.linkToDeath(link, 0)

                it.invokeOnCancellation {
                    completed.set(true)
                    val stub = Parcel.obtain()

                    try {
                        context.transact(
                            TransactionContext.TRANSACTION_requestCancel,
                            stub,
                            null,
                            0,
                        )
                    } catch (e: Exception) {
                        // ignore
                    } finally {
                        stub.recycle()
                    }
                }
            }
        try {
            reply.appendFrom(result, 0, result.dataSize())
            reply.setDataPosition(0)
            true
        } finally {
            result.recycle()
        }
    } finally {
        withContext(NonCancellable) { finalizer() }
    }
}

fun suspendTransaction(data: Parcel, reply: Parcel, block: suspend (reply: Parcel) -> Unit) {
    val completable = data.readStrongBinder()

    var finializer: () -> Unit = {}

    val job =
        KaidlScope.launch {
            val r = Parcel.obtain()

            try {
                block(r)

                completable.transact(
                    CompletableBinder.TRANSACTION_complete,
                    r,
                    null,
                    IBinder.FLAG_ONEWAY,
                )
            } catch (e: DeadObjectException) {
                // remote service dead
                // ignore
            } catch (e: Exception) {
                try {
                    withContext(NonCancellable) {
                        if (e is CancellationException) {
                            completable.transact(
                                CompletableBinder.TRANSACTION_canceled,
                                r,
                                null,
                                IBinder.FLAG_ONEWAY,
                            )
                        } else {
                            r.setDataPosition(0)

                            r.writeException(
                                IllegalArgumentException(e.message).apply {
                                    stackTrace = e.stackTrace
                                }
                            )

                            completable.transact(
                                CompletableBinder.TRANSACTION_complete,
                                r,
                                null,
                                IBinder.FLAG_ONEWAY,
                            )
                        }
                    }
                } catch (e: Exception) {
                    // remote service dead
                    // ignore
                }
            } finally {
                withContext(NonCancellable) {
                    r.recycle()

                    finializer()
                }
            }
        }

    val context =
        object : TransactionContext() {
            override fun requestCancel() {
                job.cancel()
            }
        }

    val link = IBinder.DeathRecipient { job.cancel() }

    finializer = {
        try {
            completable.unlinkToDeath(link, 0)
        } catch (ignored: Exception) {}
    }

    completable.linkToDeath(link, 0)

    reply.writeNoException()
    reply.writeStrongBinder(context)
}
