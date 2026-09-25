package com.example.rastro.mapa

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.MaterialColors
import com.example.rastro.service.EstadoRastro
import com.example.rastro.sos.LocalizacaoSos
import com.example.rastro.sos.EstadoLocalizacao
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.*
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.*

class MapaRastro(private val activity: AppCompatActivity, host: FrameLayout, saved: Bundle?, private val status: (String) -> Unit) {
    private val view: MapView
    private var map: MapLibreMap? = null
    private var estilo: Style? = null
    private var estado = EstadoRastro()
    private var raio: Double? = null
    private var cameraEscolhida = saved?.getBoolean("rastro-camera") ?: false
    private var destruido = false
    private val acoesPendentes = mutableListOf<() -> Unit>()
    private var geracao = 0
    private val handler = Handler(Looper.getMainLooper())
    private val primary = MaterialColors.getColor(host, com.google.android.material.R.attr.colorPrimary)
    private val error = MaterialColors.getColor(host, com.google.android.material.R.attr.colorError)
    private val tertiary = MaterialColors.getColor(host, com.google.android.material.R.attr.colorTertiary)
    val offline: MapasOffline

    init {
        MapLibre.getInstance(activity.applicationContext)
        offline = MapasOffline.obter(activity)
        view = MapView(activity, MapLibreMapOptions.createFromAttributes(activity).textureMode(true))
        host.addView(view, FrameLayout.LayoutParams(-1, -1))
        view.onCreate(saved)
        configurarGestos()
        view.getMapAsync { m ->
            if (destruido) return@getMapAsync
            map = m
            m.uiSettings.isAttributionEnabled = true
            val margem = (activity.resources.displayMetrics.density * 86).toInt()
            m.uiSettings.setAttributionMargins(16, 0, 0, margem)
            m.uiSettings.setLogoMargins(16, 0, 0, margem)
            m.addOnCameraMoveStartedListener { motivo -> if (motivo == 1) cameraEscolhida = true }
            m.addOnCameraIdleListener { informarCobertura() }
            if (saved == null) m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(-14.2, -51.9), 3.0))
            // O estilo vazio garante marcadores mesmo se nunca houve internet neste aparelho.
            // Não iniciar outro setStyle dentro do callback anterior: o SDK ainda
            // está esvaziando sua fila de callbacks e pode descartar o próximo.
            vazio { handler.post { if (!destruido) carregarBase() } }
            acoesPendentes.toList().forEach { handler.post { if (!destruido) it() } }
            acoesPendentes.clear()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun configurarGestos() {
        view.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
            false
        }
    }
    private fun vazio(depois: () -> Unit = {}) {
        val background = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurface, 0xffeeeeee.toInt())
        map?.setStyle(Style.Builder().fromJson("""{"version":8,"sources":{},"layers":[{"id":"fundo","type":"background","paint":{"background-color":"#%06X"}}]}""".format(background and 0xffffff))) {
            if (!destruido) { instalar(it); depois() }
        }
    }
    fun carregarBase() {
        val m = map ?: return
        val tentativa = ++geracao
        estilo = null
        status("Carregando mapa-base; coordenadas disponíveis nos detalhes.")
        m.setStyle(Style.Builder().fromUri(MapasOffline.STYLE_URL)) {
            if (!destruido && tentativa == geracao) { instalar(it); informarCobertura() }
        }
        handler.postDelayed({
            if (!destruido && tentativa == geracao && estilo == null) {
                geracao++
                vazio { status("Mapa-base indisponível. Marcadores e coordenadas disponíveis; prepare a região com internet.") }
            }
        }, 10_000)
    }
    private fun instalar(style: Style) {
        estilo = style
        // A referência usa cartografia escura nos dois temas. Apenas os controles
        // e superfícies acompanham o DayNight; os dados e o cache continuam iguais.
        style.layers.forEach { layer ->
            when (layer) {
                is BackgroundLayer -> layer.setProperties(backgroundColor("#182438"))
                is FillLayer -> layer.setProperties(fillColor(when {
                    layer.id.contains("water", true) -> "#102034"
                    layer.id.contains("building", true) -> "#293D68"
                    layer.id.contains("park", true) || layer.id.contains("landcover", true) -> "#233E3C"
                    else -> "#1B293F"
                }))
                is LineLayer -> layer.setProperties(lineColor(if (layer.id.contains("road", true) || layer.id.contains("transport", true)) "#526578" else "#344D60"))
                is SymbolLayer -> layer.setProperties(textColor("#CED8E3"), textHaloColor("#182438"), textHaloWidth(1f))
            }
        }
        for ((id, color) in listOf("local" to primary, "sos" to error)) {
            style.addSource(GeoJsonSource("$id-ponto", FeatureCollection.fromFeatures(emptyList())))
            style.addSource(GeoJsonSource("$id-precisao", FeatureCollection.fromFeatures(emptyList())))
            style.addLayer(FillLayer("$id-area", "$id-precisao").withProperties(fillColor(color), fillOpacity(0.18f)))
            style.addLayer(LineLayer("$id-borda", "$id-precisao").withProperties(lineColor(color), lineWidth(1.5f)))
            style.addLayer(CircleLayer("$id-marcador", "$id-ponto").withProperties(circleColor(color), circleRadius(if (id == "sos") 9f else 6f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
        }
        style.addSource(GeoJsonSource("alcance", FeatureCollection.fromFeatures(emptyList())))
        style.addLayer(LineLayer("alcance-tracejado", "alcance").withProperties(lineColor(tertiary), lineWidth(2f), lineDasharray(arrayOf(3f, 2f))))
        desenhar()
    }
    fun atualizar(novo: EstadoRastro, raioEstimado: Double?) {
        estado = novo; raio = raioEstimado
        desenhar()
    }
    private fun desenhar() {
        val s = estilo ?: return
        val local = estado.posicaoLocal?.posicao
        val remoto = estado.ultimoSos?.mensagem?.localizacao
        for ((id, p) in listOf("local" to local, "sos" to remoto)) {
            val ponto = p?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }
            s.getSourceAs<GeoJsonSource>("$id-ponto")?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(ponto)))
            val poligono = p?.precisaoMetros?.let { circulo(p, it) }
            s.getSourceAs<GeoJsonSource>("$id-precisao")?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(poligono)))
        }
        s.getLayerAs<CircleLayer>("local-marcador")?.setProperties(circleOpacity(if (estado.estadoLocalizacao == EstadoLocalizacao.RECENTE) 1f else 0.45f))
        s.getSourceAs<GeoJsonSource>("alcance")?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(
            remoto?.let { p -> raio?.let { circulo(p, it) } })))
        if (!cameraEscolhida && local != null) centralizar(local)
    }
    private fun circulo(p: LocalizacaoSos, metros: Double): Feature = Feature.fromGeometry(Polygon.fromLngLats(listOf(
        GeometriaMapa.circulo(p.latitude, p.longitude, metros).map { (lat, lon) -> Point.fromLngLat(lon, lat) })))
    private fun centralizar(p: LocalizacaoSos) {
        cameraEscolhida = true
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.latitude, p.longitude), 15.0))
    }
    fun minhaLocalizacao() { estado.posicaoLocal?.posicao?.let(::centralizar) ?: status("Localização indisponível") }
    fun verSos() { estado.ultimoSos?.mensagem?.localizacao?.let(::centralizar) ?: status("Último SOS sem coordenadas") }
    fun verRegiao() { offline.definicao?.bounds?.let { map?.animateCamera(CameraUpdateFactory.newLatLngBounds(it, 20)); cameraEscolhida = true } }
    fun areaVisivel(): LatLngBounds? = map?.projection?.visibleRegion?.latLngBounds
    fun quandoPronto(acao: () -> Unit) { if (map != null) acao() else acoesPendentes.add(acao) }
    fun informarCobertura() {
        val m = map ?: return
        status(if (offline.cobertura(m.projection.visibleRegion.latLngBounds, m.cameraPosition.zoom))
            "Área visível preparada para uso offline." else "Área/zoom não preparados por completo; sem internet o mapa-base pode faltar.")
    }
    fun onStart() = view.onStart()
    fun onResume() = view.onResume()
    fun onPause() = view.onPause()
    fun onStop() = view.onStop()
    fun onLowMemory() = view.onLowMemory()
    fun salvar(out: Bundle) { view.onSaveInstanceState(out); out.putBoolean("rastro-camera", cameraEscolhida) }
    fun destruir() { destruido = true; handler.removeCallbacksAndMessages(null); view.onDestroy() }
}
