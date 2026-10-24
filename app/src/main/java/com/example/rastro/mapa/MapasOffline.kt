package com.example.rastro.mapa

import android.content.Context
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.*

/** Uma região por vez. Usa applicationContext e sobrevive à rotação da Activity. */
class MapasOffline private constructor(context: Context) {
    private val manager = OfflineManager.getInstance(context.applicationContext)
    private var regiao: OfflineRegion? = null
    private var carregando = true
    private var limiteAtingido = false
    var completo = false
        private set
    var baixando = false
        private set
    var mensagem = "Consultando região offline…"
        private set
    private val observadores = linkedSetOf<() -> Unit>()
    fun observar(observador: () -> Unit) { observadores.add(observador) }
    fun removerObservador(observador: () -> Unit) { observadores.remove(observador) }
    val definicao: OfflineTilePyramidRegionDefinition? get() = regiao?.definition as? OfflineTilePyramidRegionDefinition

    init {
        manager.setMaximumAmbientCacheSize(32L * 1024 * 1024, null)
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                carregando = false
                regiao = offlineRegions?.firstOrNull()
                regiao?.let { r ->
                    observarRegiao(r)
                    r.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) { status?.let(::atualizar) }
                        override fun onError(error: String?) = informar("Erro ao consultar mapa: $error")
                    })
                } ?: informar("Região não preparada. Coordenadas e SOS continuam disponíveis.")
            }
            override fun onError(error: String) { carregando = false; informar("Erro no armazenamento do mapa: $error") }
        })
    }

    fun preparar(bounds: LatLngBounds, minZoom: Double, maxZoom: Double, pixelRatio: Float) {
        if (carregando || regiao != null) { informar("Retome a região existente ou remova-a antes de preparar outra."); return }
        require(minZoom in 0.0..16.0 && maxZoom in minZoom..16.0)
        val altura = bounds.northEast.distanceTo(bounds.southEast)
        val largura = bounds.northEast.distanceTo(bounds.northWest)
        if (altura > 20_000 || largura > 20_000 || bounds.longitudeSpan > 180) {
            informar("Aproxime o mapa: a região deve ter no máximo 20 km por lado."); return
        }
        carregando = true
        informar("Preparando área visível, zoom ${minZoom.toInt()}–${maxZoom.toInt()}…")
        manager.createOfflineRegion(OfflineTilePyramidRegionDefinition(STYLE_URL, bounds, minZoom, maxZoom, pixelRatio, false),
            "Rastro — área preparada".toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    carregando = false
                    regiao = offlineRegion
                    completo = false
                    observarRegiao(offlineRegion)
                    retomar()
                }
                override fun onError(error: String) { carregando = false; informar("Não foi possível preparar: $error") }
            })
    }

    private fun observarRegiao(r: OfflineRegion) {
        r.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) = atualizar(status)
            override fun onError(error: OfflineRegionError) {
                pausar()
                informar("Download interrompido: ${error.message}. Verifique a internet e toque em Retomar.")
            }
            override fun mapboxTileCountLimitExceeded(limit: Long) {
                pausar(); informar("Limite de $limit blocos atingido; prepare uma área menor.")
            }
        })
    }

    private fun atualizar(status: OfflineRegionStatus) {
        completo = status.isComplete
        if (status.completedResourceSize >= LIMITE_BYTES) {
            limiteAtingido = true
            completo = false
            pausar()
            informar("Limite de 256 MiB atingido. Remova a região e selecione uma área/zoom menor.")
            return
        }
        if (completo) {
            regiao?.setDownloadState(OfflineRegion.STATE_INACTIVE)
            baixando = false
        }
        val d = definicao
        informar(if (completo) "Região preparada • zoom ${d?.minZoom?.toInt()}–${d?.maxZoom?.toInt()} • ${status.completedResourceSize / 1024 / 1024} MiB"
            else "${if (baixando) "Baixando" else "Download incompleto/pausado"}: ${status.completedResourceCount}/${if (status.isRequiredResourceCountPrecise) status.requiredResourceCount else "?"} recursos • ${status.completedResourceSize / 1024 / 1024} MiB / 256 MiB")
    }

    fun retomar() {
        val r = regiao ?: return
        if (limiteAtingido) { informar("Orçamento de download atingido; remova a região e prepare uma menor."); return }
        baixando = true
        r.setDownloadState(OfflineRegion.STATE_ACTIVE)
        informar("Retomando download da região…")
    }
    fun pausar() {
        regiao?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        baixando = false
        informar("Download pausado; recursos já baixados foram mantidos.")
    }
    fun remover() {
        val r = regiao ?: return
        pausar()
        r.setObserver(null)
        carregando = true
        r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                regiao = null; completo = false; carregando = false; limiteAtingido = false
                informar("Região removida; pode ser preparada novamente com internet.")
            }
            override fun onError(error: String) { carregando = false; observarRegiao(r); informar("Falha ao remover região: $error") }
        })
    }
    fun cobertura(bounds: LatLngBounds, zoom: Double): Boolean = completo && definicao?.let {
        it.bounds?.contains(bounds.northEast) == true && it.bounds?.contains(bounds.southWest) == true && zoom in it.minZoom..it.maxZoom
    } == true
    private fun informar(texto: String) { mensagem = texto; observadores.toList().forEach { it() } }

    companion object {
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/bright"
        const val LIMITE_BYTES = 256L * 1024 * 1024
        private var instancia: MapasOffline? = null
        fun obter(context: Context): MapasOffline = instancia ?: MapasOffline(context).also { instancia = it }
    }
}
