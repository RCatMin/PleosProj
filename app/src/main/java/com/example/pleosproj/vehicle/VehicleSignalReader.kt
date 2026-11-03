package com.example.pleosproj.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import com.example.pleosproj.risk.BodyType
import com.example.pleosproj.risk.VehicleSignals
import com.example.pleosproj.risk.WiperLevel

/** 위험도 보정과 출발 전 브리핑에 쓰는 차량 신호 묶음. 모르는 값은 null이다. */
data class VehicleSnapshot(
    val wiper: WiperLevel? = null,
    val outsideTempC: Double? = null,
    val frontFogOn: Boolean? = null,
    val speedKph: Double? = null,
    val gear: VehicleGear? = null,
    val bodyType: BodyType? = null,
) {
    /** 기어가 P이거나 속도가 거의 0이면 정차로 본다 */
    val parked: Boolean? get() = when {
        gear == VehicleGear.GEAR_PARK -> true
        speedKph != null -> speedKph < 1.0
        gear != null -> false
        else -> null
    }

    fun toRiskSignals() = VehicleSignals(wiper = wiper, outsideTempC = outsideTempC, bodyType = bodyType ?: BodyType.SEDAN)
}

/**
 * Pleos Vehicle SDK(와이퍼·안개등·기어)와 안드로이드 차량 정보(외기온도·차속·연료 종류)를 읽어
 * 하나의 [VehicleSnapshot]으로 모은다. 값이 바뀔 때마다 [onChange]를 부른다.
 */
class VehicleSignalReader(private val context: Context, private val onChange: (VehicleSnapshot) -> Unit) {

    private val tag = "VehicleSignals"
    private val front = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var snapshot = VehicleSnapshot()
    private var wiperState: WiperState? = null

    // 신호마다 다른 스레드에서 콜백이 와서, 한 번에 하나씩 고치지 않으면 값이 서로 덮어써진다
    private val lock = Any()

    private fun update(change: VehicleSnapshot.() -> VehicleSnapshot) {
        val next = synchronized(lock) { snapshot.change().also { snapshot = it } }
        onChange(next)
    }

    fun start() {
        startPleos()
        startAndroidCar()
    }

    fun stop() {
        runCatching { vehicle?.release() }
        runCatching { car?.disconnect() }
        vehicle = null
        car = null
    }

    private fun startPleos() {
        val v = runCatching { Vehicle(context.applicationContext).also { it.initialize() } }
            .onFailure { Log.e(tag, "Vehicle SDK 초기화 실패", it) }.getOrNull() ?: return
        vehicle = v

        // 와이퍼: 상태가 바뀔 때마다 스위치(단계)를 다시 읽는다
        val wiper = v.getWiper()
        wiper.registerWiperState(front, object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) {
                wiperState = state
                readWiperSwitch()
            }
            override fun onFailed(exception: Exception) = Log.w(tag, "와이퍼 상태 구독 실패: ${exception.message}").let { }
        })

        // 구독은 바뀔 때만 알려 주므로 처음 값은 따로 읽는다
        v.getLight().getCurrentFrontFogLightState(
            { state -> update { copy(frontFogOn = state == LightState.ON) } },
            { Log.i(tag, "안개등 상태 없음: ${it.message}") },
        )
        v.getLight().registerFrontFogLightState(object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) = update { copy(frontFogOn = state == LightState.ON) }
            override fun onFailed(exception: Exception) = Log.w(tag, "안개등 구독 실패: ${exception.message}").let { }
        })

        v.getGear().getCurrentGearSelectionState(
            { gear -> update { copy(gear = gear) } },
            { Log.i(tag, "기어 상태 없음: ${it.message}") },
        )
        v.getGear().registerGearSelection(object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) = update { copy(gear = gear) }
            override fun onFailed(exception: Exception) = Log.w(tag, "기어 구독 실패: ${exception.message}").let { }
        })

        // 전기차인지: 배터리 잔량을 읽을 수 있으면 전기차로 본다
        v.getEvBattery().getEvBatteryLevel(
            { level -> if ((level ?: 0f) > 0f) update { copy(bodyType = BodyType.EV) } },
            { Log.i(tag, "EV 배터리 정보 없음: ${it.message}") },
        )
    }

    fun readWiperSwitch() {
        vehicle?.getWiper()?.getCurrentWiperSwitch(
            front,
            { sw -> update { copy(wiper = WiperMapping.level(wiperState, sw)) } },
            { update { copy(wiper = WiperMapping.level(wiperState, null)) } },
        )
    }

    private fun startAndroidCar() {
        val c = runCatching { Car.createCar(context.applicationContext) }
            .onFailure { Log.e(tag, "android.car 연결 실패", it) }.getOrNull() ?: return
        car = c
        val props = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return

        subscribe(props, VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE) { v ->
            update { copy(outsideTempC = (v.value as? Float)?.toDouble()) }
        }
        subscribe(props, VehiclePropertyIds.PERF_VEHICLE_SPEED) { v ->
            // m/s → km/h
            update { copy(speedKph = (v.value as? Float)?.let { it * 3.6 }) }
        }
        runCatching {
            props.getProperty<Float>(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)?.value?.let { speed ->
                update { copy(speedKph = speed * 3.6) }
            }
        }.onFailure { Log.i(tag, "차속을 읽지 못함: ${it.message}") }
        runCatching {
            // 연료 종류는 Integer 배열로 온다. FuelType.FUEL_TYPE_ELECTRIC = 10
            val fuel = props.getProperty<Array<Int>>(VehiclePropertyIds.INFO_FUEL_TYPE, 0)?.value
            if (fuel?.contains(10) == true) update { copy(bodyType = BodyType.EV) }
        }.onFailure { Log.i(tag, "연료 종류를 읽지 못함: ${it.message}") }
    }

    private fun subscribe(props: CarPropertyManager, id: Int, onValue: (CarPropertyValue<*>) -> Unit) {
        runCatching {
            props.registerCallback(object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) = onValue(value)
                override fun onErrorEvent(propId: Int, zone: Int) = Log.w(tag, "속성 $propId 오류").let { }
            }, id, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        }.onFailure { Log.w(tag, "속성 ${VehiclePropertyIds.toString(id)} 구독 실패: ${it.message}") }
    }
}
