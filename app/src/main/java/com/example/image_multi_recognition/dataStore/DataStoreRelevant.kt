package com.example.image_multi_recognition.dataStore

import androidx.datastore.core.Serializer
import com.example.image_multi_recognition.AppData
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

// this serializer is used by Proto DataStore
@Singleton
class AppDataSerializer @Inject constructor() : Serializer<AppData> {
    // specify what to return when data is not found
    override val defaultValue: AppData = AppData.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): AppData {
        return AppData.parseFrom(input)
    }

    override suspend fun writeTo(t: AppData, output: OutputStream) {
        t.writeTo(output)
    }
}