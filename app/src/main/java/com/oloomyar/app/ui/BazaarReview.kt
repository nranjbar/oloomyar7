package com.oloomyar.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.oloomyar.app.BuildConfig

internal enum class BazaarDestination { APP, WEB, UNAVAILABLE }

/** ACTION_VIEW never asks for ratings, credentials or network permission inside this app. */
internal fun launchBazaarReview(launch: (Intent) -> Unit): BazaarDestination {
    val id = BuildConfig.APPLICATION_ID
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("bazaar://details?id=$id")).setPackage("com.farsitel.bazaar")
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://cafebazaar.ir/app/$id"))
    fun attempt(intent: Intent): Boolean = try { launch(intent); true }
        catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }
    return when { attempt(app) -> BazaarDestination.APP; attempt(web) -> BazaarDestination.WEB; else -> BazaarDestination.UNAVAILABLE }
}

internal fun openBazaarReview(context: Context): Boolean {
    val result = launchBazaarReview { context.startActivity(it) }
    if (result == BazaarDestination.UNAVAILABLE) Toast.makeText(context,
        "بازار یا مرورگر در دسترس نیست. پس از نصب بازار و انتشار علوم‌یار هفتم می‌توانی نظر بدهی.", Toast.LENGTH_LONG).show()
    return result != BazaarDestination.UNAVAILABLE
}
