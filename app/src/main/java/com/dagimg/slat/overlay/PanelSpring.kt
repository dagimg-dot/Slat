package com.dagimg.slat.overlay

import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

object PanelSpring {
    const val OPEN_STIFFNESS = 400f
    const val CLOSE_STIFFNESS = 350f

    private val inFlight = mutableMapOf<View, SpringAnimation>()

    fun slideTo(
        view: View,
        targetX: Float,
        stiffness: Float,
        onSettled: () -> Unit = {},
    ) {
        inFlight[view]?.cancel()

        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val animation = SpringAnimation(view, DynamicAnimation.TRANSLATION_X, targetX)
        animation.spring =
            SpringForce(targetX).apply {
                this.stiffness = stiffness
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }

        var settled = false
        animation.addEndListener { finishedAnimation, canceled, _, _ ->
            view.setLayerType(View.LAYER_TYPE_NONE, null)
            if (inFlight[view] === finishedAnimation) inFlight.remove(view)
            if (!canceled && !settled) {
                settled = true
                onSettled()
            }
        }

        inFlight[view] = animation
        animation.start()
    }
}
