package com.example.safeerbrowser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import kotlin.math.max
import kotlin.math.min

class VirtualPointerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var pointerX: Float = 0f
    var pointerY: Float = 0f
    var isPointerVisible: Boolean = false
        set(value) {
            field = value
            visibility = if (value) VISIBLE else GONE
            invalidate()
        }

    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
        setShadowLayer(12f, 0f, 0f, Color.parseColor("#8000E5FF"))
    }

    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val hideRunnable = Runnable {
        isPointerVisible = false
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        visibility = GONE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (pointerX == 0f && pointerY == 0f) {
            pointerX = w / 2f
            pointerY = h / 2f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isPointerVisible) return

        // 1. Zunanji krog z žarenjem (Glow Ring)
        canvas.drawCircle(pointerX, pointerY, 18f, ringPaint)

        // 2. Glavna pikica kazalca (Cyan Circle)
        canvas.drawCircle(pointerX, pointerY, 12f, pointerPaint)

        // 3. Notranja bela točka (Center Specular Dot)
        canvas.drawCircle(pointerX, pointerY, 4f, innerPaint)
    }

    fun movePointer(dx: Float, dy: Float, targetWebView: WebView?): Boolean {
        isPointerVisible = true
        removeCallbacks(hideRunnable)
        postDelayed(hideRunnable, 30000)

        val newX = max(10f, min(width - 10f, pointerX + dx))
        val newY = max(10f, min(height - 10f, pointerY + dy))

        pointerX = newX
        pointerY = newY
        invalidate()

        // Samodejno pomikanje (Auto-scroll) ko se kazalec približa vrhu ali dnu spletne strani
        if (targetWebView != null) {
            val topThreshold = 140f
            val bottomThreshold = height - 120f

            if (pointerY < topThreshold) {
                targetWebView.scrollBy(0, -120)
            } else if (pointerY > bottomThreshold) {
                targetWebView.scrollBy(0, 120)
            }
        }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    fun performClickOnWebView(targetWebView: WebView) {
        if (!isPointerVisible) {
            isPointerVisible = true
            return
        }

        // Izračunaj relativne koordinate na WebViewu
        val location = IntArray(2)
        targetWebView.getLocationOnScreen(location)

        val wvX = pointerX - location[0]
        val wvY = pointerY - location[1]

        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()

        val downEvent = MotionEvent.obtain(
            downTime, eventTime,
            MotionEvent.ACTION_DOWN,
            wvX, wvY, 0
        )

        val upEvent = MotionEvent.obtain(
            downTime, eventTime + 100,
            MotionEvent.ACTION_UP,
            wvX, wvY, 0
        )

        targetWebView.dispatchTouchEvent(downEvent)
        targetWebView.dispatchTouchEvent(upEvent)

        downEvent.recycle()
        upEvent.recycle()

        // Kratka vizualna animacija klika (Pulse)
        invalidate()
    }
}
