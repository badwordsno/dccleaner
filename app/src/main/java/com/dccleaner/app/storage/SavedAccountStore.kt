package com.dccleaner.app.storage

import android.content.Context
import android.util.Log
import com.dccleaner.app.model.SavedAccount
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private const val PREFERENCES_NAME = "saved_accounts_encrypted"
private const val ACCOUNTS_KEY = "accounts"
private val savedAccountListType = object : TypeToken<List<SavedAccount>>() {}.type

fun getSavedAccounts(context: Context): List<SavedAccount> {
    return try {
        readSavedAccounts(context)
    } catch (firstError: Exception) {
        Log.e("SavedAccountStore", "암호화된 계정 정보 로드 실패, 저장소를 복구합니다.", firstError)
        resetSavedAccountPreferences(context)
        try {
            readSavedAccounts(context)
        } catch (retryError: Exception) {
            Log.e("SavedAccountStore", "계정 정보 저장소 복구 실패", retryError)
            emptyList()
        }
    }
}

fun saveSavedAccounts(context: Context, accounts: List<SavedAccount>): Boolean {
    return writeSavedAccounts(context, accounts)
}

fun addSavedAccount(context: Context, account: SavedAccount): List<SavedAccount>? {
    val accounts = getSavedAccounts(context).toMutableList()
    accounts.removeAll { it.id == account.id }
    accounts.add(0, account)
    return accounts.takeIf { saveSavedAccounts(context, it) }
}

fun removeSavedAccount(context: Context, accountId: String): List<SavedAccount>? {
    val accounts = getSavedAccounts(context).toMutableList()
    accounts.removeAll { it.id == accountId }
    return accounts.takeIf { saveSavedAccounts(context, it) }
}

private fun readSavedAccounts(context: Context): List<SavedAccount> {
    val json = encryptedPreferences(context, PREFERENCES_NAME)
        .getString(ACCOUNTS_KEY, "[]")
    return Gson().fromJson<List<SavedAccount>>(json, savedAccountListType) ?: emptyList()
}

private fun writeSavedAccounts(context: Context, accounts: List<SavedAccount>): Boolean {
    return try {
        val json = Gson().toJson(accounts)
        val saved = encryptedPreferences(context, PREFERENCES_NAME)
            .edit()
            .putString(ACCOUNTS_KEY, json)
            .commit()
        if (!saved) {
            Log.e("SavedAccountStore", "암호화된 계정 정보를 디스크에 저장하지 못했습니다.")
        }
        saved
    } catch (e: Exception) {
        Log.e("SavedAccountStore", "암호화된 계정 정보 저장 실패", e)
        false
    }
}

private fun resetSavedAccountPreferences(context: Context) {
    if (!context.applicationContext.deleteSharedPreferences(PREFERENCES_NAME)) {
        Log.w("SavedAccountStore", "기존 계정 정보 저장소가 없거나 삭제하지 못했습니다.")
    }
}
