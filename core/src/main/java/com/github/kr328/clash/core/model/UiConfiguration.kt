package io.github.leenban.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import io.github.leenban.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

@Serializable
class UiConfiguration : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<UiConfiguration> {
        override fun createFromParcel(parcel: Parcel): UiConfiguration {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<UiConfiguration?> {
            return arrayOfNulls(size)
        }
    }
}
