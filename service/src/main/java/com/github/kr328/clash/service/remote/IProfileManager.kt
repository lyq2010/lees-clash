package io.github.leenban.clash.service.remote

import com.github.kr328.kaidl.BinderInterface
import io.github.leenban.clash.service.model.Profile
import java.util.*

@BinderInterface
interface IProfileManager {
    suspend fun create(
        type: Profile.Type,
        name: String,
        source: String = "",
        ageSecretKey: String? = null,
    ): UUID

    suspend fun clone(uuid: UUID): UUID

    suspend fun commit(uuid: UUID, callback: IFetchObserver? = null)

    suspend fun release(uuid: UUID)

    suspend fun delete(uuid: UUID)

    suspend fun patch(
        uuid: UUID,
        name: String,
        source: String,
        interval: Long,
        ageSecretKey: String?,
    )

    suspend fun update(uuid: UUID)

    suspend fun updateNow(uuid: UUID, callback: IFetchObserver? = null)

    suspend fun queryByUUID(uuid: UUID): Profile?

    suspend fun queryAll(): List<Profile>

    suspend fun queryActive(): Profile?

    suspend fun setActive(profile: Profile)

    suspend fun exportBackup(uri: String, ui: String)

    suspend fun previewBackup(uri: String): String

    suspend fun restoreBackup(uri: String): String

    suspend fun queryExtension(uuid: UUID): String

    suspend fun saveExtension(uuid: UUID, text: String)
}
