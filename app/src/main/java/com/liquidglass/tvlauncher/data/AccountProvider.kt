package com.liquidglass.tvlauncher.data

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent

/**
 * Best-effort lookup of the Google account on this device, for the avatar
 * in the top bar.
 *
 * Android only lets an app see accounts it has been given access to, and a
 * TV has no Google profile photo API without a full sign-in, so all we can
 * ever show is the first letter of the address. If the account can't be
 * read silently, the user picks it once through the system's own account
 * chooser, and the choice is remembered.
 */
object AccountProvider {
    private const val PREFS = "launcher"
    private const val KEY_ACCOUNT = "account"
    private const val GOOGLE = "com.google"

    fun googleAccountName(context: Context): String? {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACCOUNT, null)
        if (saved != null) return saved
        return runCatching {
            AccountManager.get(context).getAccountsByType(GOOGLE).firstOrNull()?.name
        }.getOrNull()
    }

    fun save(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ACCOUNT, name).apply()
    }

    fun chooserIntent(): Intent =
        AccountManager.newChooseAccountIntent(null, null, arrayOf(GOOGLE), null, null, null, null)
}
