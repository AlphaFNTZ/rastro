package com.example.rastro

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat

enum class AbaNavegacao { HOME, DISPOSITIVOS, HISTORICO }

object NavegacaoInferior {
    fun configurar(activity: Activity, abaAtiva: AbaNavegacao) {
        val abas = listOf(
            Triple(AbaNavegacao.HOME, R.id.nav_item_home, R.id.nav_icon_home),
            Triple(AbaNavegacao.DISPOSITIVOS, R.id.nav_item_devices, R.id.nav_icon_devices),
            Triple(AbaNavegacao.HISTORICO, R.id.nav_item_history, R.id.nav_icon_history))
        abas.forEach { (aba, itemId, iconId) ->
            val item = activity.findViewById<View>(itemId) ?: return@forEach
            val selecionado = aba == abaAtiva
            item.isSelected = selecionado
            item.setBackgroundResource(if (selecionado) R.drawable.bg_nav_item_selected else android.R.color.transparent)
            activity.findViewById<ImageView>(iconId).setColorFilter(ContextCompat.getColor(activity,
                if (selecionado) R.color.nav_item_selected_fg else R.color.nav_icon_unselected))
            item.setOnClickListener {
                if (selecionado) return@setOnClickListener
                val destino = when (aba) {
                    AbaNavegacao.HOME -> MainActivity::class.java
                    AbaNavegacao.DISPOSITIVOS -> DispositivosActivity::class.java
                    AbaNavegacao.HISTORICO -> HistoricoActivity::class.java
                }
                activity.startActivity(Intent(activity, destino).addFlags(
                    if (aba == AbaNavegacao.HOME) Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    else Intent.FLAG_ACTIVITY_SINGLE_TOP))
                // A principal é a raiz; não acumular telas de abas ao navegar.
                if (activity !is MainActivity) activity.finish()
            }
        }
    }
}
