package com.liquidglass.tvlauncher.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR codes, drawn by the settings screen that shows the phone remote's address. */
object Qr {
    /** The dark squares of the code for [text] (no margin), or null if it can't be encoded. */
    fun modules(text: String): Array<BooleanArray>? = try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 0,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        Array(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix.get(x, y) } }
    } catch (_: Exception) {
        null
    }
}
