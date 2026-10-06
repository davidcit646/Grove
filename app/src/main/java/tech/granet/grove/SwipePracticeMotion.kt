package tech.granet.grove

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import tech.granet.grove.ui.dp

/** Gesture demonstration and transient feedback own no practice answers. */
internal class SwipePracticeMotion(private val context: Context, private val overlay: FrameLayout,
                                  private val state: FirstRunState) {
    private val ui = FirstRunComponents(context)
    val guide = Guide(context)
    private var success: View? = null
    private var feedback: AnimatorSet? = null
    private var stopped = false
    private val restart = Runnable { if (!stopped) guide.start() }
    fun touched() { guide.removeCallbacks(restart); guide.stop() }
    fun released() { if (!stopped) guide.postDelayed(restart, 650L) }
    fun resume() { stopped = false; guide.start() }
    fun pause() { stopped = true; guide.removeCallbacks(restart); guide.stop(); clearSuccess() }
    fun destroy() = pause()
    fun success(announce: Boolean) {
        if (announce) guide.announceForAccessibility(context.getString(R.string.swipe_practice_done))
        if (feedback != null || !ValueAnimator.areAnimatorsEnabled()) return
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_setup_check)
            imageTintList = ColorStateList.valueOf(ui.onAccent)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ui.accent) }
            setPadding(context.dp(20), context.dp(20), context.dp(20), context.dp(20))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false; isFocusable = false
        }
        val layer = FrameLayout(context).apply {
            isClickable = false; isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(icon, FrameLayout.LayoutParams(context.dp(144), context.dp(144), Gravity.CENTER))
        }
        success = layer; overlay.addView(layer, FrameLayout.LayoutParams(-1, -1))
        val entrance = AnimatorSet().apply {
            playTogether(ObjectAnimator.ofFloat(icon, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(icon, View.TRANSLATION_Y, context.dp(24).toFloat(), 0f),
                ObjectAnimator.ofFloat(icon, View.SCALE_X, .65f, 1f), ObjectAnimator.ofFloat(icon, View.SCALE_Y, .65f, 1f))
            duration = 75L
        }
        val hold = ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 1f).apply { duration = 100L }
        val fade = ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 0f).apply { duration = 75L }
        feedback = AnimatorSet().apply {
            playSequentially(entrance, hold, fade)
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(animation: Animator) { clearSuccess() } })
            start()
        }
    }
    private fun clearSuccess() {
        feedback?.removeAllListeners(); feedback?.cancel(); feedback = null
        success?.let(overlay::removeView); success = null
    }
    inner class Guide(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ui.primary; strokeCap = Paint.Cap.ROUND }
        private var animation: ValueAnimator? = null
        private var fraction = .5f
        private var down = true
        private fun direction(): Boolean? = when {
            state.gestures.swipeDownSearch && !state.practicedDown && state.gestures.swipeUpAppDrawer && !state.practicedUp -> !down
            state.gestures.swipeDownSearch && !state.practicedDown -> true
            state.gestures.swipeUpAppDrawer && !state.practicedUp -> false
            else -> null
        }
        fun start() {
            stop()
            val next = direction() ?: run { visibility = INVISIBLE; return }
            down = next; visibility = VISIBLE
            if (!isAttachedToWindow || stopped || !ValueAnimator.areAnimatorsEnabled()) { fraction = .5f; invalidate(); return }
            animation = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1100L; repeatCount = ValueAnimator.INFINITE
                addUpdateListener { fraction = it.animatedValue as Float; invalidate() }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationRepeat(animation: Animator) {
                        direction()?.let { down = it } ?: stop()
                    }
                })
                start()
            }
        }
        fun stop() { animation?.removeAllListeners(); animation?.cancel(); animation = null }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); start() }
        override fun onDetachedFromWindow() { stop(); removeCallbacks(restart); super.onDetachedFromWindow() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val x = width / 2f; val a = height * .20f; val b = height * .80f
            val from = if (down) a else b; val to = if (down) b else a
            paint.alpha = 60; paint.strokeWidth = context.dp(4).toFloat()
            canvas.drawLine(x, from, x, to, paint)
            val sign = if (down) 1 else -1
            canvas.drawLine(x - context.dp(9), to - sign * context.dp(9), x, to, paint)
            canvas.drawLine(x + context.dp(9), to - sign * context.dp(9), x, to, paint)
            paint.alpha = 255
            canvas.drawCircle(x, from + (to - from) * fraction, context.dp(10).toFloat(), paint)
        }
    }
}
