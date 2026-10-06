package tech.granet.grove

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.widget.FrameLayout
import android.view.animation.PathInterpolator

/** Cosmetic motion owns no answers. Cancellation always settles the committed page. */
internal class FirstRunMotion {
    private var animation: AnimatorSet? = null
    private var settle: (() -> Unit)? = null
    var busy = false; private set

    fun finish() {
        val pending = settle
        settle = null
        animation?.removeAllListeners()
        animation?.cancel()
        animation = null
        busy = false
        pending?.invoke()
    }

    fun fade(view: View, completed: () -> Unit) {
        finish()
        if (!ValueAnimator.areAnimatorsEnabled()) { view.alpha = 1f; completed(); return }
        view.alpha = 0f
        run(listOf(ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)), 300L) {
            view.alpha = 1f; completed()
        }
    }

    fun slide(host: FrameLayout, outgoing: View?, incoming: View, forward: Boolean, completed: () -> Unit) {
        finish()
        val settlePage = {
            outgoing?.let(host::removeView)
            incoming.translationX = 0f; incoming.alpha = 1f
            incoming.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            completed()
        }
        if (outgoing == null || !ValueAnimator.areAnimatorsEnabled() || host.width == 0) {
            settlePage(); return
        }
        outgoing.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        incoming.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val distance = FirstRunMotionPolicy.offset(host.width, forward, host.layoutDirection == View.LAYOUT_DIRECTION_RTL)
        incoming.translationX = distance
        run(listOf(ObjectAnimator.ofFloat(outgoing, View.TRANSLATION_X, 0f, -distance),
            ObjectAnimator.ofFloat(incoming, View.TRANSLATION_X, distance, 0f)), 220L, settlePage)
    }

    private fun run(animators: List<Animator>, durationMs: Long, done: () -> Unit) {
        busy = true; settle = done
        animation = AnimatorSet().apply {
            playTogether(animators)
            duration = durationMs
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { finish() }
            })
            start()
        }
    }
}

internal object FirstRunMotionPolicy {
    fun offset(width: Int, forward: Boolean, rtl: Boolean): Float =
        width.toFloat() * (if (forward != rtl) 1f else -1f)
}
