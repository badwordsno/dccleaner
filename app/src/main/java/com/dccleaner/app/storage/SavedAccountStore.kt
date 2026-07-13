package com.dccleaner.app.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.dccleaner.app.model.SavedAccount
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// 암호화된 SharedPreferences 도우미 함수들
private fun getEncryptedSharedPreferences(context: Context): SharedPreferences {
    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    return EncryptedSharedPreferences.create(
        "saved_accounts_encrypted",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

fun getSavedAccounts(context: Context): List<SavedAccount> {
    try {
        val prefs = getEncryptedSharedPreferences(context)
        val json = prefs.getString("accounts", "[]")
        val type = object : TypeToken<List<SavedAccount>>() {}.type
        return Gson().fromJson(json, type) ?: emptyList()
    } catch (e: Exception) {
        // 암호화 오류 발생 시 빈 리스트 반환
        Log.e("DcinsideScreen", "암호화된 계정 정보 로드 실패", e)
        return emptyList()
    }
}

fun saveSavedAccounts(context: Context, accounts: List<SavedAccount>) {
    try {
        val prefs = getEncryptedSharedPreferences(context)
        val json = Gson().toJson(accounts)
        prefs.edit().putString("accounts", json).apply()
    } catch (e: Exception) {
        Log.e("DcinsideScreen", "암호화된 계정 정보 저장 실패", e)
    }
}

fun addSavedAccount(context: Context, account: SavedAccount) {
    val accounts = getSavedAccounts(context).toMutableList()
    // 중복 제거 (동일한 ID가 있으면 업데이트)
    accounts.removeAll { it.id == account.id }
    accounts.add(0, account) // 맨 앞에 추가
    saveSavedAccounts(context, accounts)
}

fun removeSavedAccount(context: Context, accountId: String) {
    val accounts = getSavedAccounts(context).toMutableList()
    accounts.removeAll { it.id == accountId }
    saveSavedAccounts(context, accounts)
}
