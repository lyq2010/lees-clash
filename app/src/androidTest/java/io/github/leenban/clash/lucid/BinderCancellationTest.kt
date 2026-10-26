package io.github.leenban.clash.lucid

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.kaidl.suspendTransact
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BinderCancellationTest {
    private class Endpoint : Binder() {
        val callback = CompletableDeferred<IBinder>()
        var cancelRequests = 0
        private val context =
            object : Binder() {
                override fun onTransact(
                    code: Int,
                    data: Parcel,
                    reply: Parcel?,
                    flags: Int,
                ): Boolean {
                    cancelRequests++
                    return true
                }
            }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            callback.complete(data.readStrongBinder()!!)
            reply!!.writeNoException()
            reply.writeStrongBinder(context)
            return true
        }

        suspend fun complete(value: Int) {
            val parcel = Parcel.obtain()
            try {
                parcel.writeInt(value)
                callback.await().transact(IBinder.FIRST_CALL_TRANSACTION, parcel, null, 0)
            } finally {
                parcel.recycle()
            }
        }
    }

    @Test
    fun lateCompletionCannotWriteIntoCanceledCallersReply() = runBlocking {
        val endpoint = Endpoint()
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            val call =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    endpoint.suspendTransact(1, data, reply)
                }
            endpoint.callback.await()
            call.cancelAndJoin()
            assertEquals(1, endpoint.cancelRequests)
            // Simulate the pooled Parcel having been reassigned, without aborting Android.
            reply.writeInt(0x12345678)
            endpoint.complete(42)
            assertEquals(4, reply.dataSize())
            assertEquals(4, reply.dataPosition())
            reply.setDataPosition(0)
            assertEquals(0x12345678, reply.readInt())
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    @Test
    fun normalReplyIsTransferredOnceAndDuplicateCompletionIsIgnored() = runBlocking {
        val endpoint = Endpoint()
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            val call =
                async(start = CoroutineStart.UNDISPATCHED) {
                    endpoint.suspendTransact(1, data, reply)
                }
            endpoint.complete(42)
            assertTrue(call.await())
            assertEquals(42, reply.readInt())
            endpoint.complete(99)
            assertEquals(4, reply.dataSize())
            assertEquals(4, reply.dataPosition())
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
