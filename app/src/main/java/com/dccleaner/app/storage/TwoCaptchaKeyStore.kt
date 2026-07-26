package com.dccleaner.app.storage

import android.content.Context
import android.util.Log

private const val PREFERENCES_NAME = "twocaptcha_key_encrypted"
private const val API_KEY = "api_key"

fun getSavedTwoCaptchaKey(context: Context): String = try {
    encryptedPreferences(context, PREFERENCES_NAME).getString(API_KEY, "").orEmpty()
} catch (e: Exception) {
    Log.e("TwoCaptchaKeyStore", "암호화된 2Captcha 키 로드 실패", e)
    ""
}

fun saveTwoCaptchaKey(context: Context, key: String) {
    if (key.isBlank()) return

    try {
        encryptedPreferences(context, PREFERENCES_NAME)
            .edit()
            .putString(API_KEY, key)
            .apply()
    } catch (e: Exception) {
        Log.e("TwoCaptchaKeyStore", "암호화된 2Captcha 키 저장 실패", e)
    }
}

fun removeSavedTwoCaptchaKey(context: Context) {
    try {
        encryptedPreferences(context, PREFERENCES_NAME)
            .edit()
            .remove(API_KEY)
            .apply()
    } catch (e: Exception) {
        Log.e("TwoCaptchaKeyStore", "암호화된 2Captcha 키 삭제 실패", e)
    }
}
