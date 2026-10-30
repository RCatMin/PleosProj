package com.example.pleosproj.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log

/**
 * PLE-12 확인용: NaviHelper로 기본 내비에 요청을 보내고, 돌아오는 모든 이벤트를 로그로 남긴다.
 * 화면 없이 `adb logcat -s NaviProbe`로 결과를 본다. 확인이 끝나면 실제 연결 코드로 대체한다.
 */
object NaviProbe : NaviHelperEventListener {

    private const val TAG = "NaviProbe"
    private var helper: NaviHelper? = null

    // 확인용 고정 목적지·경유지 (Pleos 공식 예제 앱과 같은 값)
    private val destination = RouteInfo(
        latitude = 37.48544722,
        longitude = 127.03636666,
        poiId = "36khp37hh8gqcm5nqhe5",
        poiName = "42dot",
        poiSubId = "0",
        address = "서울시 강남구 남부순환로 2621",
    )
    // 긴 경로 확인용: 에뮬레이터 기본 위치(성남 수정구)에서 약 22km
    private val farDestination = RouteInfo(
        latitude = 37.56313333,
        longitude = 126.97522222,
        poiId = "36khq959hnn8cm5nqhe3",
        poiName = "서울특별시청 서소문2청사",
        poiSubId = "0",
        address = "서울특별시 중구 서소문로 124",
    )
    private val waypoint = RequestWaypointInfo(
        latitude = 37.41275833,
        longitude = 127.09481944,
        poiName = "소프트웨어드림센터",
        waypointIndex = WaypointIndex.FIRST,
        poiId = "36khp2hdj2cfcm5nqhe3",
        address = "경기도 성남시 수정구 창업로40번길 6",
        poiSubId = "0",
    )

    fun initialize(context: Context) {
        if (helper != null) {
            Log.i(TAG, "이미 초기화됨")
            return
        }
        runCatching {
            NaviHelper(context.applicationContext).also {
                it.initialize()
                it.addListener(this)
                helper = it
            }
        }.onSuccess { Log.i(TAG, "초기화 성공") }
            .onFailure { Log.e(TAG, "초기화 실패", it) }
    }

    fun release() {
        helper?.let {
            runCatching { it.removeListener(this); it.release() }
                .onFailure { e -> Log.e(TAG, "해제 실패", e) }
        }
        helper = null
        Log.i(TAG, "해제")
    }

    /** adb로 받은 명령 이름을 NaviHelper 호출로 바꾼다. */
    fun run(command: String, keyword: String? = null) {
        val h = helper ?: run {
            Log.w(TAG, "초기화 전이라 '$command'를 무시함. 먼저 init을 보내세요")
            return
        }
        Log.i(TAG, ">> $command")
        runCatching {
            when (command) {
                "route" -> h.requestRoute(destination)
                "route_far" -> h.requestRoute(farDestination)
                "route_far_wp" -> h.requestRouteWithWaypoints(RouteWithWaypointsRequest(farDestination, listOf(waypoint)))
                "route_wp" -> h.requestRouteWithWaypoints(RouteWithWaypointsRequest(destination, listOf(waypoint)))
                "add_wp" -> h.addWaypoint(waypoint)
                "remove_wp" -> h.removeWaypoint(WaypointIndex.FIRST)
                "reroute" -> h.requestReRoute()
                "cancel" -> h.cancelRoute()
                "option_short" -> h.changeRouteOption(RouteOption.SHORT_DISTANCE)
                "option_recommended" -> h.changeRouteOption(RouteOption.RECOMMENDED)
                "tbt" -> h.getTBTInfo()
                "state" -> h.getRouteStateInfo()
                "location" -> h.getCurrentLocationInfo()
                "destination" -> h.getDestinationInfo()
                "waypoints" -> h.getWaypointInfo()
                "bookmark" -> h.getBookmarkInfo()
                "recent" -> h.getRecentDestinationInfo()
                "search" -> h.requestNavigationScreen(NavigationScreenRequest.SearchPlace(keyword ?: "서울역"))
                "foreground" -> h.requestNavigationScreen(NavigationScreenRequest.Foreground)
                "trip" -> h.requestTrip(
                    TripRequest(
                        latitude = destination.latitude,
                        longitude = destination.longitude,
                        poiId = destination.poiId,
                        poiName = destination.poiName,
                        poiSubId = destination.poiSubId,
                    )
                )
                else -> Log.w(TAG, "모르는 명령: $command")
            }
        }.onFailure { Log.e(TAG, "'$command' 실패", it) }
    }

    private fun log(event: String, value: Any? = null) {
        Log.i(TAG, "<< $event ${value ?: ""}")
    }

    override fun onRouteStarted(routeStartInfo: RouteStartInfo) = log("onRouteStarted", routeStartInfo)
    @Deprecated("NaviHelperEventListener에서 deprecated")
    override fun onRouteEnded() = log("onRouteEnded")
    override fun onRoutingCompleted() = log("onRoutingCompleted")
    override fun onReRouteEnded() = log("onReRouteEnded")
    override fun onRouteOptionChanged(routeOption: RouteOption) = log("onRouteOptionChanged", routeOption)
    override fun onRouteCancelled() = log("onRouteCancelled")
    override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) = log("onWaypointChanged", waypointChangedInfo)
    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = log("onDestinationArrived", destinationArrivedInfo)
    override fun onBookMarkChanged() = log("onBookMarkChanged")
    override fun onDestinationChanged() = log("onDestinationChanged")
    override fun onError(errorCode: NaviErrorCode) = log("onError", errorCode)
    override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) = log("onCurrentLocationInfo", currentLocationInfo)
    override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = log("onRouteStateInfo", routeStateInfo)
    override fun onDestinationInfo(destinationInfo: DestinationInfo) = log("onDestinationInfo", destinationInfo)
    override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) = log("onWayPointInfo", waypointInfoList)
    override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) = log("onRecentDestinationInfo", recentDestinationInfoList)
    override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) = log("onBookmarkInfo", bookmarkInfoList)
    override fun onDrivingInfo(drivingInfo: DrivingInfo) = log("onDrivingInfo", drivingInfo)
    override fun onNavigationStatus(status: NavigationStatus) = log("onNavigationStatus", status)
    override fun onTBTInfo(tbtInfoList: List<TBTInfo>) = log("onTBTInfo", tbtInfoList)
    override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) = log("onCautionPointInfo", cautionPointList)
    override fun onTripInfo(tripInfo: TripInfo) = log("onTripInfo", tripInfo)
}
