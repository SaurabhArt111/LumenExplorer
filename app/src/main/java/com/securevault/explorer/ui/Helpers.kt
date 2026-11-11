package com.securevault.explorer.ui

import android.content.Context
import android.content.Intent
import com.securevault.explorer.ExternalStore
import com.securevault.explorer.Kinds
import com.securevault.explorer.Session
import java.io.File

/** Open a decrypted temp/exported file in another app. */
fun openExternally(ctx: Context, file: File) {
    val uri = ExternalStore.uriFor(ctx, file)
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, Kinds.mimeFor(file.name))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    Session.externalBusy = true
    try {
        ctx.startActivity(Intent.createChooser(view, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Session.externalBusy = false
        Session.toast("No app found to open ${file.name}")
    }
}

fun shareFiles(ctx: Context, files: List<File>) {
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { ExternalStore.uriFor(ctx, it) })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = Kinds.mimeFor(files[0].name)
            putExtra(Intent.EXTRA_STREAM, uris[0])
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    Session.externalBusy = true
    try {
        ctx.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Session.externalBusy = false
        Session.toast("Nothing to share with")
    }
}
