package com.siren.mobile.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Implemented by the host Activity.
 *
 * `registerForActivityResult` has to be called before the Activity finishes being
 * created, so the launcher cannot live in [AndroidPlatformServices] — which only holds
 * an application context anyway. The Activity owns the launcher and exposes it through
 * this interface; `AndroidPlatformServices` finds it via the current-activity lambda.
 */
interface ProfilePhotoPicker {
    /** Base64 JPEG, already downscaled, or null if the user backed out. */
    suspend fun pickProfilePhoto(): String?
}

/**
 * Turns a picked image into something small enough to live in a Firestore document.
 *
 * Two stages, and both matter. `inSampleSize` decodes at a reduced size so a 12-megapixel
 * camera photo never becomes a full-size Bitmap in memory — decoding one at full
 * resolution just to shrink it is a routine OutOfMemoryError on a cheap phone. The exact
 * scale afterwards then hits [PROFILE_PHOTO_MAX_PX] precisely.
 */
object ProfilePhotoEncoder {

    private const val TAG = "ProfilePhoto"

    fun encode(context: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "Could not read image bounds from $uri")
            return null
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val scaled = scaleToBound(decoded)
        if (scaled !== decoded) decoded.recycle()

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, PROFILE_PHOTO_QUALITY, out)
        scaled.recycle()

        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.onFailure { Log.e(TAG, "Failed to encode profile photo", it) }.getOrNull()

    /** Largest power-of-two downsample that still leaves us above the target size. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var longest = max(width, height)
        while (longest / 2 >= PROFILE_PHOTO_MAX_PX) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToBound(source: Bitmap): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= PROFILE_PHOTO_MAX_PX) return source
        val ratio = PROFILE_PHOTO_MAX_PX.toFloat() / longest
        val w = (source.width * ratio).roundToInt().coerceAtLeast(1)
        val h = (source.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }
}
