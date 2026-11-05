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
        val next = synchronized(this) { snapshot.change().also { snapshot = it } }
        main.post { onChange(next) }
    }

    private val poll = object : Runnable {
        override fun run() {
            val h = helper ?: return
            runCatching {
                h.getDestinationInfo()
                h.getCurrentLocationInfo()
                if (ticks % 5 == 0) {
                    h.getTBTInfo()
                    h.getRouteStateInfo()
                }
            }.onFailure { Log.w(TAG, "NaviHelper 요청 실패: ${it.message}") }
            ticks++
            main.postDelayed(this, 2_000)
        }
    }

    /** 초기화에 실패하면 false. 앱은 그래도 날씨·차량 신호만으로 동작한다 */
    fun start(): Boolean {
        if (helper != null) return true
        return runCatching {
            NaviHelper(appContext).also {
                it.initialize()
                it.addListener(this)
                helper = it
            }
            main.post(poll)
            Log.i(TAG, "NaviHelper 연결")
        }.onFailure { Log.e(TAG, "NaviHelper 초기화 실패", it) }.isSuccess
    }

    fun stop() {
        main.removeCallbacks(poll)
        helper?.let { runCatching { it.removeListener(this); it.release() } }
        helper = null
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

    override fun onRouteCancelled() = update { NaviSnapshot() }

    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) =
        update { copy(guiding = false, remainMeters = 0, remainSeconds = 0) }

    override fun onDestinationInfo(destinationInfo: DestinationInfo) = update {
        val d = destinationInfo
        // 안내 중이 아니면 이름이 비어 있고 좌표가 0으로 온다
        if (d.name.isBlank()) copy(guiding = false)
        else copy(
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
    }
}
