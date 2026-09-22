package com.dagimg.slat.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/**
 * Floating handle on the screen edge.
 * Swipe inward and hold to open the clipboard panel; tap or flick opens it immediately;
 * long press and drag repositions it vertically.
 */
@SuppressLint("ViewConstructor")
class EdgeHandleView(
    context: Context,
    private val onTap: () -> Unit,
    private val onSwipeOpen: () -> Unit,
    private val onDrag: (deltaY: Float) -> Unit,
    private val onDragEnd: () -> Unit,
) : View(context) {
    companion object {
        private const val HANDLE_WIDTH_DP = 8f

        // Must clear the ~30dp system back-gesture zone, or gesture nav swallows
        // the inward drag before it reaches us. The drawn pill stays 8dp.
        private const val TOUCH_WIDTH_DP = 40f
        private const val HANDLE_HEIGHT_DP = 100f
        private const val CORNER_RADIUS_DP = 4f
        private const val LONG_PRESS_TIMEOUT_MS = 300L

        // Swipe-to-open feel taken from Smart Edge (MIT).
        private const val SWIPE_THRESHOLD_DP = 16f
        private const val SWIPE_RETREAT_DP = 4f
        private const val HOLD_DURATION_MS = 250L
    }

    private val density = resources.displayMetrics.density
    private val handleWidth = (HANDLE_WIDTH_DP * density).toInt()
    private val touchWidth = (TOUCH_WIDTH_DP * density).toInt()
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

    private val swipeThreshold = SWIPE_THRESHOLD_DP * density
    private val retreatThreshold = SWIPE_RETREAT_DP * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())

    private var isPressed = false
    private var isDragging = false
    private var lastTouchY = 0f
    private var touchDownTime = 0L
    private var startX = 0f
    private var startY = 0f
    private var swipeCandidate = false
    private var hasPassedThreshold = false
    private var isTriggered = false

    private val holdRunnable =
        Runnable {
            isTriggered = true
            performHapticFeedback()
            resetScale(120)
            onSwipeOpen()
        }
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val gestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (!isDragging && !isTriggered && !swipeCandidate) {
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
                    if (!isDragging && !isTriggered && velocityX < -300) {
                        performHapticFeedback()
                        onTap()
                        return true
                    }
                    return false
                }
            },
        )

    init {
        post { excludeFromSystemGestures() }
        isClickable = true
        isFocusable = false
        minimumWidth = touchWidth
        minimumHeight = handleHeight
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        super.onLayout(changed, left, top, right, bottom)
        excludeFromSystemGestures()
    }

    // Belt-and-braces alongside TOUCH_WIDTH_DP; not honoured for overlay windows
    // on every device, so the width is what actually makes the swipe reachable.
    private fun excludeFromSystemGestures() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && width > 0 && height > 0) {
            systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
        }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(touchWidth, handleHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val pillLeft = width - handleWidth
        val path =
            Path().apply {
                moveTo(width, 0f)
                lineTo(pillLeft + cornerRadius, 0f)
                quadTo(pillLeft, 0f, pillLeft, cornerRadius)
                lineTo(pillLeft, height - cornerRadius)
                quadTo(pillLeft, height, pillLeft + cornerRadius, height)
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
                swipeCandidate = false
                hasPassedThreshold = false
                isTriggered = false
                lastTouchY = event.rawY
                startX = event.rawX
                startY = event.rawY
                touchDownTime = System.currentTimeMillis()
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTriggered) return true

                // Handle sits on the right edge, so inward travel is leftward.
                val inward = startX - event.rawX
                val totalDy = event.rawY - startY

                if (!isDragging && !swipeCandidate &&
                    inward > touchSlop && inward > kotlin.math.abs(totalDy)
                ) {
                    swipeCandidate = true
                }

                if (swipeCandidate) {
                    if (!hasPassedThreshold && inward > swipeThreshold) {
                        hasPassedThreshold = true
                        handler.postDelayed(holdRunnable, HOLD_DURATION_MS)
                        animate().scaleX(0.7f).scaleY(0.9f).setDuration(HOLD_DURATION_MS).start()
                    } else if (hasPassedThreshold && inward < retreatThreshold) {
                        cancelHold()
                    }
                    return true
                }

                val elapsed = System.currentTimeMillis() - touchDownTime
                val deltaY = event.rawY - lastTouchY

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
                val wasSwipe = swipeCandidate
                val wasTriggered = isTriggered
                cancelHold()
                isPressed = false
                isDragging = false
                swipeCandidate = false
                isTriggered = false
                invalidate()

                if (wasDragging) {
                    onDragEnd()
                    return true
                } else if (wasTriggered || wasSwipe) {
                    return true
                } else if (!gestureHandled) {
                    performHapticFeedback()
                    onTap()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelHold()
                isPressed = false
                swipeCandidate = false
                isTriggered = false
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

    private fun cancelHold() {
        hasPassedThreshold = false
        handler.removeCallbacks(holdRunnable)
        resetScale(80)
    }

    private fun resetScale(duration: Long) {
        animate().scaleX(1f).scaleY(1f).setDuration(duration).start()
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
