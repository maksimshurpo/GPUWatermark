package com.mshurpo.testview.gpu.utils

import com.mshurpo.testview.gpu.Rotation

/**
 * Created by maksimsurpo on 5/21/17.
 */
object TextureRotationUtil {
    val TEXTURE_NO_ROTATION = floatArrayOf(0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f)

    val TEXTURE_ROTATED_90 = floatArrayOf(1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f)
    val TEXTURE_ROTATED_180 = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f)
    val TEXTURE_ROTATED_270 = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f)

    fun getRotation(rotation: Rotation, flipHorizontal: Boolean,
                    flipVertical: Boolean): FloatArray {
        var rotatedTex: FloatArray
        when (rotation) {
            Rotation.ROTATION_90 -> rotatedTex = TEXTURE_ROTATED_90
            Rotation.ROTATION_180 -> rotatedTex = TEXTURE_ROTATED_180
            Rotation.ROTATION_270 -> rotatedTex = TEXTURE_ROTATED_270
            else -> rotatedTex = TEXTURE_NO_ROTATION
        }
        if (flipHorizontal) {
            rotatedTex = floatArrayOf(flip(rotatedTex[0]), rotatedTex[1], flip(rotatedTex[2]), rotatedTex[3], flip(rotatedTex[4]), rotatedTex[5], flip(rotatedTex[6]), rotatedTex[7])
        }
        if (flipVertical) {
            rotatedTex = floatArrayOf(rotatedTex[0], flip(rotatedTex[1]), rotatedTex[2], flip(rotatedTex[3]), rotatedTex[4], flip(rotatedTex[5]), rotatedTex[6], flip(rotatedTex[7]))
        }
        return rotatedTex
    }


    private fun flip(i: Float): Float {
        if (i == 0.0f) {
            return 1.0f
        }
        return 0.0f
    }
}