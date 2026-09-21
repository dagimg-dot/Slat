package com.dagimg.slat.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Floating handle on the screen edge.
 * Tap or swipe to open the clipboard panel; long press and drag to reposition vertically.
 */
@SuppressLint("ViewConstructor")
class EdgeHandleView(
    context: Context,
    private val onTap: () -> Unit,
    private val onDrag: (deltaY: Float) -> Unit,
    private val onDragEnd: () -> Unit,
) : View(context) {
    companion object {
        private const val HANDLE_WIDTH_DP = 8f
        private const val HANDLE_HEIGHT_DP = 100f
        private const val CORNER_RADIUS_DP = 4f
        private const val LONG_PRESS_TIMEOUT_MS = 300L
    }

    private val density = resources.displayMetrics.density
    private val handleWidth = (HANDLE_WIDTH_DP * density).toInt()
    private val handleHeight = (HANDLE_HEIGHT_DP * density).toInt()
    private val cornerRadius = CORNER_RADIUS_DP * density

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#55FFFFFF")
            style = Paint.Style.FILL
        }

    private val highlightPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#AAFFFFFF")
            style = Paint.Style.FILL
        }

    private val dragPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF6C5CE7")
            style = Paint.Style.FILL
        }

    private var isPressed = false
    private var isDragging = false
    private var lastTouchY = 0f
    private var touchDownTime = 0L
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val gestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (!isDragging) {
                        performHapticFeedback()
                        onTap()
                        return true
                    }
                    return false
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    if (!isDragging && velocityX < -300) {
                        performHapticFeedback()
                        onTap()
                        return true
                    }
                    return false
                }
            },
        )

    init {
        isClickable = true
        isFocusable = false
        minimumWidth = handleWidth
        minimumHeight = handleHeight
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(handleWidth, handleHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val path =
            Path().apply {
                moveTo(width, 0f)
                lineTo(cornerRadius, 0f)
                quadTo(0f, 0f, 0f, cornerRadius)
                lineTo(0f, height - cornerRadius)
                quadTo(0f, height, cornerRadius, height)
                lineTo(width, height)
                lineTo(width, 0f)
                close()
            }

        val currentPaint =
            when {
                isDragging -> dragPaint
                isPressed -> highlightPaint
                else -> paint
            }
        canvas.drawPath(path, currentPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val gestureHandled = gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                isDragging = false
                lastTouchY = event.rawY
                touchDownTime = System.currentTimeMillis()
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val elapsed = System.currentTimeMillis() - touchDownTime
                val deltaY = event.rawY - lastTouchY

                // Start dragging after long press threshold OR significant vertical movement
                if (!isDragging && (elapsed > LONG_PRESS_TIMEOUT_MS || kotlin.math.abs(deltaY) > 25)) {
                    isDragging = true
                    performHapticFeedback()
                    invalidate()
                }

                if (isDragging) {
                    onDrag(deltaY)
                    lastTouchY = event.rawY
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                val wasDragging = isDragging
                isPressed = false
                isDragging = false
                invalidate()

                if (wasDragging) {
                    onDragEnd()
                    return true
                } else if (!gestureHandled) {
                    performHapticFeedback()
                    onTap()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                if (isDragging) {
                    isDragging = false
                    onDragEnd()
                }
                invalidate()
                return true
            }
        }
        return gestureHandled || super.onTouchEvent(event)
    }

    private fun performHapticFeedback() {
        vibrator?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(30)
            }
        }
    }
}
