package com.example.pleosproj.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * NaviHelper에서 기본 내비의 안내 값을 받아 [NaviSnapshot] 하나로 모은다 (ADR 0004).
 * 콜백이 스스로 오지 않는 값도 있어서, 남은 거리·현재 위치는 2초, 회전 안내·교통 정보는 10초마다 다시 요청한다.
 * 메인 스레드에서 만들고 부른다.
 */
class NaviSource(context: Context, private val onChange: (NaviSnapshot) -> Unit) : NaviHelperEventListener {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var helper: NaviHelper? = null
    private var ticks = 0

    @Volatile
    var snapshot = NaviSnapshot()
        private set

    private fun update(change: NaviSnapshot.() -> NaviSnapshot) {
        lastResponseAt = SystemClock.elapsedRealtime()
        warnedSilent = false
        val next = synchronized(this) { snapshot.change().also { snapshot = it } }
        main.post { onChange(next) }
    }

    /** 연결이 끝난 시각. 연결 전에 보낸 요청은 버려지므로 그 뒤부터 요청한다 */
    @Volatile private var connectedAt = 0L
    @Volatile private var lastResponseAt = 0L

    /**
     * 기본 내비가 요청에 답하고 있는지. 연결 전이면 null.
     * 연결 뒤 [SILENT_MILLIS] 넘게 아무 답이 없으면 false다. 기본 내비 앱이 요청을 받고도 답하지 않는 상태가
     * 에뮬레이터에서 있었고(11/6), 이때는 기본 내비 앱을 다시 띄워야 풀린다.
     */
    val responding: Boolean?
        get() {
            if (connectedAt == 0L) return null
            return SystemClock.elapsedRealtime() - maxOf(connectedAt, lastResponseAt) < SILENT_MILLIS
        }

    private var warnedSilent = false

    private val poll = object : Runnable {
        override fun run() {
            val h = helper ?: return
            if (connectedAt != 0L) {
                runCatching {
                    h.getDestinationInfo()
                    h.getCurrentLocationInfo()
                    // 안내 중인데 회전 안내가 아직 없으면 바로 다시 받는다
                    val needTurns = snapshot.guiding && snapshot.turns.isEmpty()
                    if (ticks % 5 == 0 || needTurns) {
                        h.getTBTInfo()
                        h.getRouteStateInfo()
                    }
                }.onFailure { Log.w(TAG, "NaviHelper 요청 실패: ${it.message}") }
                ticks++
                if (responding == false && !warnedSilent) {
                    warnedSilent = true
                    Log.w(TAG, "기본 내비가 ${SILENT_MILLIS / 1000}초 넘게 응답하지 않음. 기본 내비 앱을 다시 띄워야 할 수 있다")
                }
            }
            main.postDelayed(this, 2_000)
        }
    }

    /** 초기화에 실패하면 false. 앱은 그래도 날씨·차량 신호만으로 동작한다 */
    fun start(): Boolean {
        if (helper != null) return true
        return runCatching {
            NaviHelper(appContext).also {
                it.initialize(
                    {
                        // 연결되자마자 지금 상태를 한 번에 받는다
                        connectedAt = SystemClock.elapsedRealtime()
                        Log.i(TAG, "NaviHelper 연결")
                        main.removeCallbacks(poll)
                        ticks = 0
                        main.post(poll)
                    },
                    { e -> Log.e(TAG, "NaviHelper 연결 실패", e) },
                )
                it.addListener(this)
                helper = it
            }
            main.post(poll)
        }.onFailure { Log.e(TAG, "NaviHelper 초기화 실패", it) }.isSuccess
    }

    fun stop() {
        main.removeCallbacks(poll)
        helper?.let { runCatching { it.removeListener(this); it.release() } }
        helper = null
        connectedAt = 0L
        warnedSilent = false
    }

    // --- NaviHelper 콜백 ---

    override fun onNavigationStatus(status: NavigationStatus) =
        update { copy(guiding = status == NavigationStatus.ROUTE_DRIVE) }

    override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
        update { copy(guiding = true) }
        helper?.getTBTInfo()
        helper?.getRouteStateInfo()
    }

    override fun onReRouteEnded() {
        helper?.getTBTInfo()
        helper?.getRouteStateInfo()
    }

    override fun onRouteCancelled() = update { NaviSnapshot(known = true) }

    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) =
        update { copy(guiding = false, remainMeters = 0, remainSeconds = 0) }

    override fun onDestinationInfo(destinationInfo: DestinationInfo) = update {
        val d = destinationInfo
        // 안내 중이 아니면 이름이 비어 있고 좌표가 0으로 온다
        if (d.name.isBlank()) copy(known = true, guiding = false)
        else copy(
            known = true,
            guiding = true,
            destinationName = d.name,
            destinationLat = d.latitude,
            destinationLng = d.longitude,
            remainMeters = d.remainDistance,
            remainSeconds = d.remainTime,
        )
    }

    override fun onTBTInfo(tbtInfoList: List<TBTInfo>) = update {
        copy(turns = tbtInfoList.map {
            NaviTurn(NaviMapping.turnKind(it.type), it.distance, it.description, NaviMapping.entersUnderpass(it.type))
        })
    }

    override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) = update {
        copy(lat = currentLocationInfo.latitude, lng = currentLocationInfo.longitude,
            heading = currentLocationInfo.heading, address = currentLocationInfo.address)
    }

    override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = update {
        val t = routeStateInfo.trafficStatus ?: return@update this
        copy(
            congestion = t.congestionItemList.map {
                NaviCongestion(it.offset, it.distance, NaviMapping.heavy(it.trafficCongestionType), it.roadName)
            },
            events = t.trafficEventList.map {
                NaviTrafficEvent(it.latitude, it.longitude, NaviMapping.eventKind(it.eventType, it.description),
                    it.description, NaviMapping.isClosure(it.description))
            },
        )
    }

    override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) = update {
        copy(cautions = cautionPointList.mapNotNull { c ->
            NaviMapping.cautionKind(c.type)?.let { NaviCaution(c.id, it, c.remainDistance) }
        })
    }

    override fun onError(errorCode: NaviErrorCode) {
        Log.w(TAG, "NaviHelper 오류: $errorCode")
    }

    // 쓰지 않는 콜백
    @Deprecated("NaviHelperEventListener에서 deprecated")
    override fun onRouteEnded() {}
    override fun onRoutingCompleted() {}
    override fun onRouteOptionChanged(routeOption: RouteOption) {}
    override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {}
    override fun onBookMarkChanged() {}
    override fun onDestinationChanged() {}
    override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {}
    override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) {}
    override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) {}
    override fun onDrivingInfo(drivingInfo: DrivingInfo) {}
    override fun onTripInfo(tripInfo: TripInfo) {}

    private companion object {
        const val TAG = "NaviSource"
        const val SILENT_MILLIS = 10_000L
    }
}
