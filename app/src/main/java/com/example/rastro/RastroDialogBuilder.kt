package com.example.rastro

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Common spacing for custom content, alongside the shared light/dark dialog theme. */
class RastroDialogBuilder(context: Context) : MaterialAlertDialogBuilder(context) {
    override fun setView(view: View?): MaterialAlertDialogBuilder {
        if (view == null) return super.setView(view)
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        if (view is EditText) {
            view.setBackgroundResource(R.drawable.bg_search_input)
            view.backgroundTintList = null
            view.setPadding(dp(14), dp(12), dp(14), dp(12))
            view.minHeight = dp(50)
            view.textSize = 16f
            val box = FrameLayout(context).apply {
                setPadding(dp(24), dp(8), dp(24), dp(8))
                addView(view, FrameLayout.LayoutParams(-1, -2))
            }
            return super.setView(box)
        }
        val text = if (view is ScrollView) view.getChildAt(0) as? TextView else view as? TextView
        text?.apply {
            setTextAppearance(R.style.Rastro_DialogBody)
            setPadding(dp(24), dp(8), dp(24), dp(16))
        }
        return super.setView(view)
    }
}
