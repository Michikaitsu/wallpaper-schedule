package com.wallpaperscheduler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur

data class WallpaperEffect(
    val blur: Float = 0f,        // 0-25
    val dim: Float = 0f,         // 0-100 (percentage)
    val saturation: Float = 1f,  // 0-2 (0=grayscale, 1=normal, 2=vivid)
    val warmth: Float = 0f,      // -100 to 100
    val contrast: Float = 1f     // 0.5-1.5
)

object WallpaperEffects {
    
    fun applyEffects(context: Context, bitmap: Bitmap, effect: WallpaperEffect): Bitmap {
        var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // Apply blur
        if (effect.blur > 0) {
            result = applyBlur(context, result, effect.blur)
        }
        
        // Apply color effects (saturation, warmth, contrast)
        if (effect.saturation != 1f || effect.warmth != 0f || effect.contrast != 1f) {
            result = applyColorMatrix(result, effect)
        }
        
        // Apply dim (darken)
        if (effect.dim > 0) {
            result = applyDim(result, effect.dim)
        }
        
        return result
    }
    
    @Suppress("DEPRECATION")
    private fun applyBlur(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
        val clampedRadius = radius.coerceIn(0.1f, 25f)
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Use RenderEffect for Android 12+
            applyBlurModern(bitmap, clampedRadius)
        } else {
            // Use RenderScript for older versions
            applyBlurLegacy(context, bitmap, clampedRadius)
        }
    }
    
    private fun applyBlurModern(bitmap: Bitmap, radius: Float): Bitmap {
        // For Android 12+, we still need to use a canvas-based approach
        // since RenderEffect is for Views, not Bitmaps directly
        return applyStackBlur(bitmap, radius.toInt().coerceAtLeast(1))
    }
    
    @Suppress("DEPRECATION")
    private fun applyBlurLegacy(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
        return try {
            val rs = RenderScript.create(context)
            val input = Allocation.createFromBitmap(rs, bitmap)
            val output = Allocation.createTyped(rs, input.type)
            val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            
            script.setRadius(radius)
            script.setInput(input)
            script.forEach(output)
            
            val result = bitmap.copy(bitmap.config, true)
            output.copyTo(result)
            
            input.destroy()
            output.destroy()
            script.destroy()
            rs.destroy()
            
            result
        } catch (e: Exception) {
            // Fallback to stack blur
            applyStackBlur(bitmap, radius.toInt().coerceAtLeast(1))
        }
    }
    
    // Simple stack blur implementation
    private fun applyStackBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        
        // Simplified box blur (faster but less quality)
        val scaleFactor = (radius * 2 + 1).coerceAtMost(10)
        val smallW = w / scaleFactor
        val smallH = h / scaleFactor
        
        val small = Bitmap.createScaledBitmap(bitmap, smallW.coerceAtLeast(1), smallH.coerceAtLeast(1), true)
        val blurred = Bitmap.createScaledBitmap(small, w, h, true)
        small.recycle()
        
        return blurred
    }
    
    private fun applyColorMatrix(bitmap: Bitmap, effect: WallpaperEffect): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(result)
        val paint = Paint()
        
        val colorMatrix = ColorMatrix()
        
        // Saturation
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(effect.saturation)
        colorMatrix.postConcat(satMatrix)
        
        // Contrast
        val contrastMatrix = ColorMatrix()
        val scale = effect.contrast
        val translate = (-.5f * scale + .5f) * 255f
        contrastMatrix.set(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        colorMatrix.postConcat(contrastMatrix)
        
        // Warmth (shift red/blue)
        if (effect.warmth != 0f) {
            val warmthMatrix = ColorMatrix()
            val warmth = effect.warmth / 100f
            warmthMatrix.set(floatArrayOf(
                1f + warmth * 0.2f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f - warmth * 0.2f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            colorMatrix.postConcat(warmthMatrix)
        }
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }
    
    private fun applyDim(bitmap: Bitmap, dimPercent: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(result)
        val paint = Paint()
        
        // Draw original
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        
        // Draw dark overlay
        val alpha = (dimPercent / 100f * 255).toInt().coerceIn(0, 255)
        paint.color = android.graphics.Color.argb(alpha, 0, 0, 0)
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        
        return result
    }
}
