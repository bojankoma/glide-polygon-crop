package co.infinum.polygoncropdemo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.util.Synthetic
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.Arrays
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashSet

private const val VERSION: Int = 1
private const val ID: String = "co.infinum.messe.glide.transformations.PolygonCropTransformation.v$VERSION"

private val MODELS_REQUIRING_BITMAP_LOCK = HashSet(
    Arrays.asList(
        // Moto X gen 2
        "XT1085",
        "XT1092",
        "XT1093",
        "XT1094",
        "XT1095",
        "XT1096",
        "XT1097",
        "XT1098",
        // Moto G gen 1
        "XT1031",
        "XT1028",
        "XT937C",
        "XT1032",
        "XT1008",
        "XT1033",
        "XT1035",
        "XT1034",
        "XT939G",
        "XT1039",
        "XT1040",
        "XT1042",
        "XT1045",
        // Moto G gen 2
        "XT1063",
        "XT1064",
        "XT1068",
        "XT1069",
        "XT1072",
        "XT1077",
        "XT1078",
        "XT1079"
    )
)

private val BITMAP_DRAWABLE_LOCK = if (MODELS_REQUIRING_BITMAP_LOCK.contains(Build.MODEL)) {
    ReentrantLock()
} else {
    NoLock()
}

class PolygonCropTransformation(
    private val sides: Int = 3,
    private val rotation: Int = 0,
    private val cornerRadius: Int = 0
) : BitmapTransformation() {
    private val cropShapePaint = Paint(Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val cropBitmapPaint = Paint(Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    init {
        if (cornerRadius > 0) {
            cropShapePaint.pathEffect = CornerPathEffect(cornerRadius.toFloat())
        }
        cropBitmapPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ) = polygonCrop(pool, toTransform, outWidth, outHeight)

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID.toByteArray(Charset.forName("UTF-8")))
    }

    override fun hashCode(): Int {
        return ID.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is PolygonCropTransformation
    }

    private fun calculatePath(radius: Float, width: Float, height: Float): Path {
        val centerX = width / 2f
        val centerY = height / 2f
        val angle = 2.0 * Math.PI / sides

        val path = Path()
        path.reset()
        val startX = centerX + (radius * Math.sin(0.0)).toFloat()
        val startY = centerY + (radius * Math.cos(0.0)).toFloat()
        path.moveTo(startX, startY)
        for (i in 1 until sides) {
            val nextX = centerX + (radius * Math.sin((angle) * i)).toFloat()
            val nextY = centerY + (radius * Math.cos((angle) * i)).toFloat()
            path.lineTo(nextX, nextY)
        }
        path.transform(Matrix().apply {
            postRotate(rotation.toFloat(), centerX, centerY)
        })
        path.close()

        return path
    }

    private fun polygonCrop(pool: BitmapPool, inBitmap: Bitmap, destWidth: Int, destHeight: Int): Bitmap {
        val destMinEdge = Math.min(destWidth, destHeight)

        val srcWidth = inBitmap.width
        val srcHeight = inBitmap.height

        val scaleX = destMinEdge / srcWidth.toFloat()
        val scaleY = destMinEdge / srcHeight.toFloat()
        val maxScale = Math.max(scaleX, scaleY)

        val scaledWidth = maxScale * srcWidth
        val scaledHeight = maxScale * srcHeight
        val left = (destMinEdge - scaledWidth) / 2.0f
        val top = (destMinEdge - scaledHeight) / 2.0f

        val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)

        // Alpha is required for this transformation.
        val toTransform = alphaSafeBitmap(pool, inBitmap)

        val outConfig = alphaSafeConfig(inBitmap)
        val result = pool.get(destMinEdge, destMinEdge, outConfig)
        result.setHasAlpha(true)

        val radius = destMinEdge / ((2 * Math.cos(Math.toRadians(rotation.toDouble()))).toFloat())

        BITMAP_DRAWABLE_LOCK.lock()
        try {
            val canvas = Canvas(result)
            canvas.drawPath(calculatePath(radius, destWidth.toFloat(), destHeight.toFloat()), cropShapePaint)
            canvas.drawBitmap(toTransform, null, destRect, cropBitmapPaint)
            canvas.setBitmap(null)
        } finally {
            BITMAP_DRAWABLE_LOCK.unlock()
        }

        if (toTransform != inBitmap) {
            pool.put(toTransform)
        }

        return result
    }

    private fun alphaSafeBitmap(pool: BitmapPool, maybeAlphaSafe: Bitmap): Bitmap {
        val safeConfig = alphaSafeConfig(maybeAlphaSafe)
        if (safeConfig == maybeAlphaSafe.config) {
            return maybeAlphaSafe
        }

        val argbBitmap = pool.get(maybeAlphaSafe.width, maybeAlphaSafe.height, safeConfig)
        Canvas(argbBitmap).drawBitmap(maybeAlphaSafe, 0f /*left*/, 0f /*top*/, null /*paint*/)

        // We now own this Bitmap. It's our responsibility to replace it in the pool outside this method
        // when we're finished with it.
        return argbBitmap
    }

    private fun alphaSafeConfig(inBitmap: Bitmap): Bitmap.Config {
        // Avoid short circuiting the sdk check.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Bitmap.Config.RGBA_F16 == inBitmap.config) { // NOPMD
            return Bitmap.Config.RGBA_F16
        }

        return Bitmap.Config.ARGB_8888
    }
}

class NoLock @Synthetic
internal constructor() : Lock {

    override fun lock() {
        // do nothing
    }

    @Throws(InterruptedException::class)
    override fun lockInterruptibly() {
        // do nothing
    }

    override fun tryLock(): Boolean {
        return true
    }

    @Throws(InterruptedException::class)
    override fun tryLock(time: Long, unit: TimeUnit): Boolean {
        return true
    }

    override fun unlock() {
        // do nothing
    }

    override fun newCondition(): Condition {
        throw UnsupportedOperationException("Should not be called")
    }
}