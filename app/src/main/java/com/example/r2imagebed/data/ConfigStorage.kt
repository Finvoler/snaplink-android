package com.example.r2imagebed.data

import android.content.Context

class ConfigStorage(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): R2Config {
        return R2Config(
            accountId = preferences.getString(KEY_ACCOUNT_ID, "").orEmpty(),
            accessKeyId = preferences.getString(KEY_ACCESS_KEY_ID, "").orEmpty(),
            secretAccessKey = preferences.getString(KEY_SECRET_ACCESS_KEY, "").orEmpty(),
            bucketName = preferences.getString(KEY_BUCKET_NAME, "").orEmpty()
        )
    }

    fun save(config: R2Config) {
        val normalized = config.normalized()
        preferences.edit()
            .putString(KEY_ACCOUNT_ID, normalized.accountId)
            .putString(KEY_ACCESS_KEY_ID, normalized.accessKeyId)
            .putString(KEY_SECRET_ACCESS_KEY, normalized.secretAccessKey)
            .putString(KEY_BUCKET_NAME, normalized.bucketName)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "r2_config"
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_ACCESS_KEY_ID = "access_key_id"
        const val KEY_SECRET_ACCESS_KEY = "secret_access_key"
        const val KEY_BUCKET_NAME = "bucket_name"
    }
}