package com.qtekfun.ultimatevisitcards.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Facts about the installed app (version for backups and «About»). */
class AppInfo @Inject constructor(@ApplicationContext private val context: Context) {
    val versionName: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}
