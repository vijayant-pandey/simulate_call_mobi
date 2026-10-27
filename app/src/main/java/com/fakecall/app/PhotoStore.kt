package com.fakecall.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File

object PhotoStore {

    /**
     * Copies a picked image into the app's own storage, because access to images
     * from the photo picker or a contact only lasts a short while.
     */
    fun save(context: Context, uri: Uri): String? {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "caller_${System.currentTimeMillis()}.img")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        }.isSuccess
        if (!copied) {
            file.delete()
            return null
        }

        // Only clear out older photos once the new one is safely saved, so a failed
        // copy never leaves the caller without the photo that was already chosen.
        val inUse = CallState.session.value?.caller?.photoPath
        dir.listFiles()
            ?.filter { it != file && it.absolutePath != inUse }
            ?.forEach { it.delete() }
        return file.absolutePath
    }

    /** Loads a photo scaled down to roughly [maxPx] on its longest side. */
    fun load(path: String?, maxPx: Int = 512): Bitmap? {
        if (path == null) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    val scale = maxOf(info.size.width, info.size.height) / maxPx
                    if (scale > 1) decoder.setTargetSampleSize(scale)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / maxPx)
                }
                BitmapFactory.decodeFile(path, options)
            }
        }.getOrNull()
    }
}
