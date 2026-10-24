package com.example.rastro

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.ViewPager

/** The map owns gestures started on its exposed surface; overlays remain page-swipe areas. */
class PaginasPrincipais @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewPager(context, attrs) {
    private var gestoDoMapa = false
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if(event.actionMasked == MotionEvent.ACTION_DOWN) {
            val map = findViewById<View>(R.id.map_host)
            val home = map?.parent as? ViewGroup
            val area = Rect()
            val top = home?.let { root ->
                (root.childCount - 1 downTo 0).map { root.getChildAt(it) }.firstOrNull {
                    it.visibility == View.VISIBLE && it.getGlobalVisibleRect(area) &&
                        area.contains(event.rawX.toInt(),event.rawY.toInt())
                }
            }
            gestoDoMapa = map != null && top === map
        }
        if(gestoDoMapa) return false
        return super.onInterceptTouchEvent(event)
    }
}
