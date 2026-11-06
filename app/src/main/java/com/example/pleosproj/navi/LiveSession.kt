package com.example.pleosproj.navi

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.pleosproj.BuildConfig
import com.example.pleosproj.location.LocationFix
import com.example.pleosproj.location.LocationListener
import com.example.pleosproj.location.Locations
import com.example.pleosproj.traffic.ItsClient
import com.example.pleosproj.traffic.TrafficEvent
import com.example.pleosproj.ui.state.GuidanceState
import com.example.pleosproj.vehicle.VehicleSignalReader
import com.example.pleosproj.vehicle.VehicleSnapshot
import com.example.pleosproj.weather.KmaClient
import com.example.pleosproj.weather.KmaGrid
import com.example.pleosproj.weather.PrecipType
import com.example.pleosproj.weather.WeatherForecast
import com.example.pleosproj.weather.WeatherNow
import com.example.pleosproj.weather.WeatherWarning
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 실제 주행 세션. 기본 내비 안내(NaviHelper), 내 위치(PLE-16), 차량 신호(PLE-9)를 구독하고
 * 기상청·ITS를 주기적으로 불러서, 1초마다 [LiveGuidance]로 안내 화면 상태를 만든다.
 *
 * 기본 내비가 안내 중이 아니면 [onState]에 null을 넘긴다. 네트워크가 안 되면 마지막으로 받은 날씨·교통 값을 계속 쓴다.
 * 회전 안내와 새 위험 알림은 음성으로도 말한다 (PLE-31, [GuidanceVoice]).
 * 메인 스레드에서 만들고 부른다.
 */
class LiveSession(
    private val context: Context,
    private val onState: (GuidanceState?) -> Unit,
) {
    /** 안내 음성(회전 안내·위험 경고)을 낼지. 화면의 "음성 켜짐" 버튼과 연결한다 */
    var voiceEnabled: Boolean
        get() = voice?.enabled ?: pendingVoiceEnabled
        set(value) {
            pendingVoiceEnabled = value
            voice?.enabled = value
        }
    private var pendingVoiceEnabled = true

    /**
     * 확인·시연용: 값이 있으면 지금 위치에 시간당 이만큼 비가 온다고 본다. 맑은 날에도 실제 주행 경고를 보여 줄 때 쓴다.
     * null이면 기상청 값을 그대로 쓴다.
     */
    var simulatedRainMm: Double? = null

    private var voice: GuidanceVoice? = null

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadScheduledExecutor()

    private val navi = NaviSource(context) {}
    private val locations = Locations(context)
    private val vehicleReader = VehicleSignalReader(context) { vehicle = it }

    private val kma = BuildConfig.KMA_SERVICE_KEY.takeIf { it.isNotBlank() }?.let(::KmaClient)
    private val its = BuildConfig.ITS_API_KEY.takeIf { it.isNotBlank() }?.let(::ItsClient)

    // 여러 스레드에서 쓰고 읽는 최신 값
    @Volatile private var fix: LocationFix? = null
    @Volatile private var vehicle: VehicleSnapshot? = null
    @Volatile private var weatherHere: WeatherNow? = null
    @Volatile private var forecastDest: List<WeatherForecast> = emptyList()
    @Volatile private var warnings: List<WeatherWarning> = emptyList()
    @Volatile private var events: List<TrafficEvent> = emptyList()

    /** 이번 목적지의 처음 경로 길이. 목적지가 바뀌면 다시 잰다 */
    private var routeTotal = 0.0
    private var routeKey = ""

    private val tick = object : Runnable {
        override fun run() {
            emit()
            main.postDelayed(this, 1_000)
        }
    }

    fun start() {
        if (!navi.start()) Log.w(TAG, "기본 내비 연결 없이 시작")
        voice = GuidanceVoice(context).also { it.enabled = pendingVoiceEnabled }
        locations.start(object : LocationListener {
            override fun onLocation(fix: LocationFix) { this@LiveSession.fix = fix }
        })
        vehicleReader.start()
        if (kma == null) Log.w(TAG, "기상청 키 없음: 날씨 없이 안내")
        if (its == null) Log.w(TAG, "ITS 키 없음: 돌발상황 없이 안내")
        // 초단기실황은 매시 40분쯤 갱신되므로 10분마다, 특보·돌발상황은 5분마다 부른다
        io.scheduleWithFixedDelay({ safely("날씨") { refreshWeather() } }, 3, 600, TimeUnit.SECONDS)
        io.scheduleWithFixedDelay({ safely("특보") { refreshWarnings() } }, 3, 300, TimeUnit.SECONDS)
        io.scheduleWithFixedDelay({ safely("돌발상황") { refreshEvents() } }, 5, 300, TimeUnit.SECONDS)
        main.post(tick)
    }

    fun stop() {
        voice?.release()
        voice = null
        main.removeCallbacks(tick)
        io.shutdownNow()
        navi.stop()
        locations.release()
        vehicleReader.stop()
    }

    private fun emit() {
        val n = navi.snapshot
        if (!n.guiding || n.turns.isEmpty()) {
            voice?.onState(null)
            onState(null)
            return
        }
        val key = "${n.destinationName}|${n.destinationLat}|${n.destinationLng}"
        if (key != routeKey) {
            routeKey = key
            routeTotal = 0.0
            // 목적지가 바뀌면 목적지 예보부터 다시 받는다
            io.execute { safely("목적지 예보") { refreshDestinationForecast() } }
        }
        routeTotal = maxOf(routeTotal, n.routeMeters)
        val f = fix
        val state = LiveGuidance.build(
                LiveInputs(
                    navi = n,
                    now = LocalDateTime.now(),
                    routeTotalMeters = routeTotal,
                    myLat = f?.lat,
                    myLng = f?.lng,
                    myHeading = f?.heading?.toDouble(),
                    weatherHere = simulatedWeather(),
                    forecastAtDestination = forecastDest,
                    warnings = warnings,
                    trafficEvents = events,
                    vehicle = vehicle,
                ),
            )
        onState(state)
        voice?.onState(state)
    }

    private fun simulatedWeather(): WeatherNow? {
        val rain = simulatedRainMm ?: return weatherHere
        val base = weatherHere ?: here()?.let { (lat, lng) ->
            WeatherNow(KmaGrid.fromLatLng(lat, lng), LocalDateTime.now(), 20.0, 0.0, PrecipType.NONE, 80, 2.0)
        } ?: return null
        return base.copy(rainMm = rain, precip = if (rain > 0) PrecipType.RAIN else PrecipType.NONE)
    }

    // --- 네트워크 (io 스레드) ---

    private fun here(): Pair<Double, Double>? {
        fix?.let { return it.lat to it.lng }
        val n = navi.snapshot
        return if (n.lat != null && n.lng != null && (n.lat != 0.0 || n.lng != 0.0)) n.lat to n.lng else null
    }

    private fun refreshWeather() {
        val client = kma ?: return
        val (lat, lng) = here() ?: return
        weatherHere = client.now(KmaGrid.fromLatLng(lat, lng))
        refreshDestinationForecast()
    }

    private fun refreshDestinationForecast() {
        val client = kma ?: return
        val n = navi.snapshot
        val lat = n.destinationLat ?: return
        val lng = n.destinationLng ?: return
        if (lat == 0.0 && lng == 0.0) return
        forecastDest = client.forecast(KmaGrid.fromLatLng(lat, lng))
    }

    private fun refreshWarnings() {
        val client = kma ?: return
        val now = LocalDateTime.now()
        // 하루 안에 발표·해제된 특보 중 같은 제목은 가장 최근 것만 본다
        warnings = client.warnings(now.minusDays(1), now)
            .sortedByDescending { it.issuedAt }
            .distinctBy { it.kind to it.level }
    }

    private fun refreshEvents() {
        val client = its ?: return
        val (lat, lng) = here() ?: return
        // 내 주변 약 ±5km
        events = client.events(lat - 0.045, lng - 0.056, lat + 0.045, lng + 0.056)
    }

    private inline fun safely(what: String, block: () -> Unit) {
        runCatching(block).onFailure { Log.w(TAG, "$what 갱신 실패, 이전 값 유지: ${it.message}") }
    }

    private companion object {
        const val TAG = "LiveSession"
    }
}
