package com.dccleaner.app.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

private const val PREFERENCES_NAME = "twocaptcha_key_encrypted"
private const val API_KEY = "api_key"

private fun getTwoCaptchaEncryptedPreferences(context: Context): SharedPreferences {
    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    return EncryptedSharedPreferences.create(
        PREFERENCES_NAME,
        masterKeyAlias,
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

fun getSavedTwoCaptchaKey(context: Context): String = try {
    getTwoCaptchaEncryptedPreferences(context).getString(API_KEY, "").orEmpty()
} catch (e: Exception) {
    Log.e("TwoCaptchaKeyStore", "암호화된 2Captcha 키 로드 실패", e)
    ""
}

fun saveTwoCaptchaKey(context: Context, key: String) {
    if (key.isBlank()) return

    try {
        getTwoCaptchaEncryptedPreferences(context)
            .edit()
            .putString(API_KEY, key)
            .apply()
    } catch (e: Exception) {
        Log.e("TwoCaptchaKeyStore", "암호화된 2Captcha 키 저장 실패", e)
    }
}

fun removeSavedTwoCaptchaKey(context: Context) {
    try {
        getTwoCaptchaEncryptedPreferences(context)
            .edit()
            .remove(API_KEY)
            .apply()
    } catch (e: Exception) {
        Log.e("TwoCaptchaKeyStore", "암호화된 2Captcha 키 삭제 실패", e)
    }
}
