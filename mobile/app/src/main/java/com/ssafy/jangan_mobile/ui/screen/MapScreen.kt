package com.ssafy.jangan_mobile.ui.screen

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraBoundsOptions
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.CoordinateBounds
import com.mapbox.maps.MapView
import com.mapbox.maps.extension.style.image.image
import com.mapbox.maps.extension.style.layers.generated.backgroundLayer
import com.mapbox.maps.extension.style.layers.generated.rasterLayer
import com.mapbox.maps.extension.style.sources.generated.imageSource
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.getGesturesManager
import com.mapbox.maps.plugin.scalebar.scalebar
import com.ssafy.jangan_mobile.R
import com.ssafy.jangan_mobile.service.dto.BeaconNotificationDto
import com.ssafy.jangan_mobile.service.dto.FireNotificationDto
import com.ssafy.jangan_mobile.store.CompassSensorManager
import com.ssafy.jangan_mobile.store.FireNotificationStore
import com.ssafy.jangan_mobile.ui.component.ArrivalCard
import com.ssafy.jangan_mobile.ui.component.EvacuationButton
import com.ssafy.jangan_mobile.ui.component.FireNotificationCard
import com.ssafy.jangan_mobile.ui.component.FireStation
import com.ssafy.jangan_mobile.ui.component.FloorSelector
import com.ssafy.jangan_mobile.ui.component.ToMyLocation
import com.ssafy.jangan_mobile.ui.viewmodel.MapViewModel
import com.ssafy.jangan_mobile.viewmodel.EscapeRouteViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EscapeRouteMapScreen(
    navController: NavController,
    viewModel: EscapeRouteViewModel = viewModel(),
    mapViewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    val zoomLevel = 6.5

    val fireNotificationDto: FireNotificationDto? by FireNotificationStore.fireNotificationDto.observeAsState()
    val currentLocationCode by FireNotificationStore.currentLocationBeaconCode.observeAsState()
    val routePoints by viewModel.route.observeAsState(emptyList())
    val imageUrl by mapViewModel.mapImageUrl.collectAsState()
    val myLocation by viewModel.myLocation.observeAsState()
    val isTracking by viewModel.isTracking.observeAsState()
    val fireFloors by viewModel.fireFloors.collectAsState()



    val showRoute = remember { mutableStateOf(false) }
    val selectedFloor = remember { mutableStateOf("B1") }
    val pointAnnotationManager = remember { mutableStateOf<PointAnnotationManager?>(null) }
    val polylineManager = remember { mutableStateOf<PolylineAnnotationManager?>(null) }
    val polylineList = remember { mutableListOf<PolylineAnnotation>() }

    val imageWidth = 5000
    val imageHeight = 7800
    val aspectRatio = imageWidth.toDouble() / imageHeight.toDouble()
    val coordinateHeight = 60.0
    val coordinateWidth = coordinateHeight * aspectRatio
    val top = coordinateHeight / 2
    val bottom = -coordinateHeight / 2
    val left = -coordinateWidth / 2
    val right = coordinateWidth / 2

    val colors = listOf(
        android.graphics.Color.rgb(138, 234, 82),
        android.graphics.Color.argb(128, 138, 234, 82)
    ) // 초록 ↔ 투명
    var colorIndex = 0

    val fireMarkerIcons = listOf("fire-weak", "fire-strong")
    var fireIconIndex = 0

    val handler = Handler(Looper.getMainLooper())
    var blinkRunnable: Runnable? = null

    fun convertPixelToLngLat(x: Int, y: Int): List<Double> {
        val lng = left + (x.toDouble() / imageWidth) * (right - left)
        val lat = top - (y.toDouble() / imageHeight) * (top - bottom)
        return listOf(lng, lat)
    }


    // 상태 변수
    val redLighting = remember { mutableStateOf(false) }
    val hasArrived = remember { mutableStateOf(false) }
    val showArrivalCard = remember { mutableStateOf(false) }
    val isCardVisible = remember { mutableStateOf(true) } // 도착카드
    val isGuiding = remember { mutableStateOf(false) }
    val isFireNotificationCardVisible = remember { mutableStateOf(true) } // 마커 클릭용
    val fireNotification = fireNotificationDto
    val isFireStationShown = remember { mutableStateOf(false) } // 최초 알림용
    val cctvImageUrl = remember { mutableStateOf<String?>(null) }
    val selectedFireBeaconDto = remember { mutableStateOf<BeaconNotificationDto?>(null) }
    val lineState = remember { mutableStateOf(0) }
    val lastShownFireId = remember { mutableStateOf<String?>(null) }
    val previousFireCodes = remember { mutableStateListOf<Int>() }
    val selectedImageUrl = remember { mutableStateOf("") }
    val mapConfigTrigger = remember { mutableStateOf(0) }
    val isFireIconClicked = remember { mutableStateOf(false) }
    val firebeaconSave = remember { mutableMapOf<PointAnnotation, BeaconNotificationDto>() }
    val firefloor = remember { mutableStateListOf<String>() }


    // 마커들
    val myLocationAnnotation = remember { mutableStateOf<PointAnnotation?>(null) }
    val goalMarker = remember { mutableStateOf<PointAnnotation?>(null) }
    val fireMarkers = remember { mutableStateListOf<PointAnnotation>() }
    val destinationMarker = remember { mutableStateOf<PointAnnotation?>(null) }
    val routeMarkers = remember { mutableListOf<PointAnnotation>() }

    // 이미지 로드
    LaunchedEffect(Unit) {
        Log.d("EscapeRouteScreen", "✅ EscapeRouteMapScreen 진입")
        mapViewModel.fetchMapImage(222, "1001")
    }

    // 내 위치 요청
    LaunchedEffect(currentLocationCode) {
        while (true) {
            currentLocationCode?.toInt()?.let {
                viewModel.fetchMyLocation(222, it)
            }
            kotlinx.coroutines.delay(2000) // 2초마다 업데이트
        }
    }

    // 문자열 층을 API 코드로 변환
    fun floorStringToCode(floor: String): Int? {
        return when (floor) {
            "B1" -> 1001
            "B2" -> 1002
            "B3" -> 1003
            else -> null
        }
    }

    // 층 변경 시 이미지 요청
    LaunchedEffect(selectedFloor.value) {
        val floorCode = floorStringToCode(selectedFloor.value)?.toString() ?: return@LaunchedEffect
        mapViewModel.fetchMapImage(222, floorCode)
    }

    // 🔥 실제 화재 발생한 비콘 중 첫 번째 찾기
    val targetBeaconDto = fireNotificationDto
        ?.beaconNotificationDtos
        ?.firstOrNull { it.isNewFire == 1 }

    // 지도 초기 스타일 설정 (최초 한 번만 실행)
    LaunchedEffect(imageUrl) {
        if (imageUrl != null) {
            mapView.mapboxMap.loadStyle(
                style {
                    +backgroundLayer("background") {
                        backgroundColor("#EFF0F1")
                    }
                    +image(
                        "marker-icon",
                        BitmapFactory.decodeResource(context.resources, R.drawable.maker_icon)
                    ) {}
                    +image(
                        "fire-icon",
                        BitmapFactory.decodeResource(context.resources, R.drawable.fire)
                    ) {}

                    +image("fire-weak", BitmapFactory.decodeResource(context.resources, R.drawable.fire_weak)) {}
                    +image("fire-strong", BitmapFactory.decodeResource(context.resources, R.drawable.fire_strong)) {}

                    +image(
                        "destination-icon",
                        BitmapFactory.decodeResource(context.resources, R.drawable.goal)
                    ) {}
                    +imageSource("custom-map") {
                        url(imageUrl!!)
                        coordinates(
                            listOf(
                                convertPixelToLngLat(0, 0),
                                convertPixelToLngLat(imageWidth, 0),
                                convertPixelToLngLat(imageWidth, imageHeight),
                                convertPixelToLngLat(0, imageHeight)
                            )
                        )
                    }
                    +rasterLayer("custom-map-layer", "custom-map") {
                        rasterOpacity(1.0)
                    }
                }
            ) {
                var center = convertPixelToLngLat(imageWidth / 2, imageHeight / 2)
                // 화재알람 존재 시 지도 처음 위치를 화재 위치로
                if (!(fireNotificationDto?.beaconNotificationDtos?.isEmpty() ?: true)) {
                    fireNotificationDto!!.beaconNotificationDtos.forEach { dto ->
                        if (dto.isNewFire == 1) {
                            center = listOf(dto.coordX, dto.coordY)
                        }
                    }
                }
                mapView.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(center[0], center[1]))
                        .zoom(zoomLevel)
                        .build()
                )
                mapView.mapboxMap.setBounds(
                    CameraBoundsOptions.Builder()
                        .bounds(
                            CoordinateBounds(
                                Point.fromLngLat(left, bottom),
                                Point.fromLngLat(right, top)
                            )
                        )
                        .build()
                )
                mapView.mapboxMap.getGesturesManager()!!.rotateGestureDetector.isEnabled = false
                mapView.mapboxMap.getGesturesManager()!!.shoveGestureDetector.isEnabled = false
                val annotationApi = mapView.annotations
                pointAnnotationManager.value = annotationApi.createPointAnnotationManager()
                polylineManager.value = annotationApi.createPolylineAnnotationManager()
            }
        }
        mapView.compass.enabled = false; //나침반 비활성화
        mapView.scalebar.enabled = false; //스케일바 비활성화
        mapConfigTrigger.value = mapConfigTrigger.value + 1
    }

    //방향 센서 설정
    val azimuthState = remember { mutableStateOf(0) }
    val compassSensorManager = remember {
        CompassSensorManager(context) { azimuth ->
            val azimuthInt = azimuth.toInt() - (azimuth.toInt() % 5)
            if(azimuthInt != azimuthState.value){
                azimuthState.value = azimuthInt
            }
        }
    }
    DisposableEffect(Unit) {
        compassSensorManager.startListening()
        onDispose {
            compassSensorManager.stopListening()
        }
    }

    // 마커 각도 수정
    LaunchedEffect(azimuthState.value) {
        var beforePointAnnotation: PointAnnotation? = null
        myLocationAnnotation.value?.let {
            beforePointAnnotation = it
        }
        val selectedFloorCode = floorStringToCode(selectedFloor.value)
        myLocation?.let { beacon ->
            if (beacon.floor == selectedFloorCode) {
                val marker = PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(beacon.coordX, beacon.coordY))
                    .withIconImage("marker-icon")
                    .withIconSize(0.15)
                    .withIconRotate(azimuthState.value.toDouble())
                myLocationAnnotation.value = pointAnnotationManager.value?.create(marker)
            }
        }
        myLocationAnnotation.value?.let {
            if(beforePointAnnotation != null)
                pointAnnotationManager.value?.delete(beforePointAnnotation!!)
        }
    }

    // 🔁 내 위치 마커만 따로 관리
    LaunchedEffect(myLocation, selectedFloor.value, isTracking) {
        myLocationAnnotation.value?.let {
            pointAnnotationManager.value?.delete(it)
            myLocationAnnotation.value = null
        }
        val selectedFloorCode = floorStringToCode(selectedFloor.value)
        myLocation?.let { beacon ->
            if (beacon.floor == selectedFloorCode) {
                val marker = PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(beacon.coordX, beacon.coordY))
                    .withIconImage("marker-icon")
                    .withIconSize(0.15)
                    .withIconRotate(azimuthState.value.toDouble())
                myLocationAnnotation.value = pointAnnotationManager.value?.create(marker)
            }
        }
        // 현재 위치 카메라 추적
        if (isTracking ?: false && myLocation != null) {
            mapView.mapboxMap.flyTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(myLocation!!.coordX, myLocation!!.coordY))
                    .zoom(zoomLevel)
                    .build(),
                mapAnimationOptions {
                    duration(1500L)
                }
            )
        }
    }

    LaunchedEffect(selectedImageUrl.value) {
        Log.d("🔥 ImageURL", "🔄 이미지 URL 변경됨: ${selectedImageUrl.value}")
    }

    // 화재 마커 고르기
//     🔥 1. 마커 생성 및 클릭 이벤트 등록
    LaunchedEffect(
        fireNotificationDto?.beaconNotificationDtos,
        selectedFloor.value,
        showArrivalCard.value,
        mapConfigTrigger.value,
        selectedImageUrl.value
    ) {

        // 화재좌표 먼저 나오게끔 null값 확인
        if (fireNotificationDto == null) {
            Log.w("FireMarker", "🔥 아직 fireNotificationDto가 도착하지 않았음")
            return@LaunchedEffect
        }

        Log.d("🔥fireCheck", "불이야불이야 fireNotificationDto: $fireNotificationDto")
        val selectedFloorCode = floorStringToCode(selectedFloor.value)
        Log.d("FireMarker", "🔥 LaunchedEffect 호출됨. 현재 층: $selectedFloorCode")
        if (pointAnnotationManager.value == null) {
            Log.d("FireMarker", "pointAnnotationManager가 null")
        }

        pointAnnotationManager.value?.let { manager ->
            val fireBeacons = fireNotificationDto?.beaconNotificationDtos
                ?: run {
                    Log.w("FireMarker", "⚠️ fireNotificationDto가 null이거나 해당 층의 화재 없음")
                    return@let
                }
            manager.delete(fireMarkers)
            fireMarkers.clear()
            Log.d("Firewhere", "불이야불불")
            fireBeacons.forEachIndexed { index, beacon ->
                if(floorStringToCode(selectedFloor.value) == beacon.floor){
                    Log.d(
                        "FireMarker",
                        "🔥 [$index] 화재 마커 생성 → coord=(${beacon.coordX}, ${beacon.coordY}), floor=${beacon.floor}, beaconCode=${beacon.beaconCode}"
                    )
                    val marker = PointAnnotationOptions()
                        .withPoint(Point.fromLngLat(beacon.coordX, beacon.coordY))
                        .withIconImage("fire-icon")
                        .withIconSize(0.25)
                    val fireMarker = manager.create(marker)
                    fireMarkers.add(fireMarker)

                    // 화재 아이콘에 비콘 코드 저장
                    firebeaconSave[fireMarker] = beacon

                    // ✅ 마커 클릭 이벤트 등록
                    manager.addClickListener { clicked ->
                        val clickedBeacon = firebeaconSave[clicked]
                        if (clickedBeacon != null) {
                            Log.d("FireMarker", "🔥 화재 마커 클릭됨! → 모달 다시 열기")

                            // beacon.beaconCode와 fireNotification.stationId를 함께 사용
                            val stationId = fireNotification?.stationId ?: return@addClickListener false
                            val beaconCode = clickedBeacon.beaconCode

                            Log.d(
                                "FireMarker",
                                "➡️ 마커 클릭됨 요청할 stationId=$stationId, beaconCode=$beaconCode"
                            )
                            Log.d("🔥 마커 클릭", "➡️ 선택된 마커의 beaconCode=$beaconCode, stationId=$stationId")
                            Log.d(
                                "🔥 마커 클릭",
                                "➡️ 좌표=(${clickedBeacon.coordX}, ${clickedBeacon.coordY}), 층=${clickedBeacon.floor}"
                            )

                            viewModel.fetchCctvImage(stationId, beaconCode) { url ->
                                Log.d("FireMarker", "📸 fetchCctvImage → 받아온 imageUrl=$url")

    //                            cctv 이미지로 받아올 예정
                                selectedImageUrl.value = "$url"
    //                            selectedImageUrl.value =
    //                                "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a9/Example.jpg/320px-Example.jpg"
                                selectedFireBeaconDto.value = null
                                selectedFireBeaconDto.value = clickedBeacon.copy()
                                isFireIconClicked.value = true
                                isFireNotificationCardVisible.value = false
                                isFireNotificationCardVisible.value = true
                            }
                            true
                        } else false
                    }
                }
            }
        }
    }
    LaunchedEffect(fireNotificationDto,mapConfigTrigger.value) {
        while (true) {
            // 🔥 fire 마커 이미지 교체
            val nextIcon = fireMarkerIcons[fireIconIndex]
            val manager = pointAnnotationManager.value ?: return@LaunchedEffect
            val fireBeacons = fireNotificationDto?.beaconNotificationDtos ?: return@LaunchedEffect

            fireMarkers.toList().forEachIndexed { index, marker ->
                val point = marker.point
                val beacon = fireBeacons[index]
                // 새로운 마커 생성
                val newMarkerOptions = PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage(nextIcon)
                    .withIconSize(0.25)
                val newMarker = manager.create(newMarkerOptions)

                // 1. 먼저 새 마커를 fireMarkers 리스트에 넣고
                fireMarkers[index] = newMarker
                firebeaconSave[newMarker] = beacon
                // 2. 기존 마커 삭제 (덮어씌운 뒤 제거)
                manager.delete(marker)
            }
            fireIconIndex = (fireIconIndex + 1) % fireMarkerIcons.size
            delay(500)
        }
    }

    LaunchedEffect(imageUrl) {
        Log.d("🔥 AsyncImage Trigger", "🖼️ imageUrl 변경 감지됨 → $imageUrl")
    }

    // 🔁 경로 표시도 분리해서 관리
    LaunchedEffect(routePoints, showRoute.value, selectedFloor.value) {
        val selectedFloorCode = floorStringToCode(selectedFloor.value)


        if (hasArrived.value) return@LaunchedEffect


        // 경로 숨기기거나 포인트 부족할 경우 라인 & 마커 모두 삭제
        if (!showRoute.value || routePoints.isEmpty()) {

            polylineManager.value?.deleteAll()
            routeMarkers.forEach { marker -> pointAnnotationManager.value?.delete(marker) }
            routeMarkers.clear()


            return@LaunchedEffect
        }
        // 라인만 지우기 (routePoints가 1개인 경우)
        if (!showRoute.value || routePoints.size == 1) {

            polylineManager.value?.deleteAll()


            // ✅ 무조건 routePoints[0]에 목적지 마커 표시
            val destination = routePoints.first()
            Log.d("EscapeRouteMap", "📍 목적지 마커 추가: (${destination.x}, ${destination.y})")


            if (destination.floor == selectedFloorCode) {
                val endMarker = PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(destination.x, destination.y))
                    .withIconImage("destination-icon")
                    .withIconSize(0.15)
                pointAnnotationManager.value?.create(endMarker)
            }
            // ✅ 내 위치 마커 (routePoints.last())
            val currentPosition = routePoints.last()


            // ✅ 도착 여부: 객체 값 일치로만 판단
            if (destination.floor == currentPosition.floor &&
                destination.x == currentPosition.x &&
                destination.y == currentPosition.y
            ) {
                Log.d("EscapeRouteMap", "📍 도착")
                if (!showArrivalCard.value) {
                    Log.d("EscapeRouteMap", "🎉 목적지 도착 (좌표 동일) → 안내 카드 표시")
                    showArrivalCard.value = true
                    hasArrived.value = true

                }
            } else {
                Log.w(
                    "EscapeRouteMap",
                    "❌ 도착 아님 → destination=(${destination.x}, ${destination.y}, floor=${destination.floor}) | " +
                            "current=(${currentPosition.x}, ${currentPosition.y}, floor=${currentPosition.floor})"
                )
            }
            return@LaunchedEffect
        }


        polylineManager.value?.deleteAll()
        routeMarkers.clear()
        polylineList.clear()

        if (showRoute.value && routePoints.size >= 2) {
            Log.d("EscapeRouteMap", "🟩 전체 경로 좌표:")
            routePoints.forEachIndexed { index, point ->
                Log.d("EscapeRouteMap", "[$index] (${point.x}, ${point.y}) on floor ${point.floor}")
            }
            polylineManager.value?.deleteAll()
            routeMarkers.forEach { marker ->
                pointAnnotationManager.value?.delete(marker)
            }
            routeMarkers.clear()
            polylineList.clear()
            for (i in 0 until routePoints.size - 1) {
                val start = routePoints[i]
                val end = routePoints[i + 1]

                if (start.floor != selectedFloorCode || end.floor != selectedFloorCode) continue

                val polyline = PolylineAnnotationOptions()
                    .withPoints(
                        listOf(
                            Point.fromLngLat(start.x, start.y),
                            Point.fromLngLat(end.x, end.y)
                        )
                    )
                    .withLineColor("#8AEA52")
                    .withLineWidth(6.0)
                val line = polylineManager.value?.create(polyline)
                polylineList.add(line!!)
            }


            // ✅ 무조건 routePoints[0]에 목적지 마커 표시
            val destination = routePoints.first()
            Log.d("EscapeRouteMap", "📍 목적지 마커 추가: (${destination.x}, ${destination.y})")

            // 기존 목적지 마커 지우기
            destinationMarker.value?.let { existingMarker ->
                pointAnnotationManager.value?.delete(existingMarker)
                destinationMarker.value = null
            }

            if (destination.floor == selectedFloorCode) {
                val marker = PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(destination.x, destination.y))
                    .withIconImage("destination-icon")
                    .withIconSize(0.2)
                destinationMarker.value = pointAnnotationManager.value?.create(marker)
            }
            // ✅ 내 위치 마커 (routePoints.last())
            val currentPosition = routePoints.last()

            // ✅ 도착 여부: 객체 값 일치로만 판단
            if (destination.floor == currentPosition.floor &&
                destination.x == currentPosition.x &&
                destination.y == currentPosition.y
            ) {
                Log.d("EscapeRouteMap", "📍 도착")
                if (!showArrivalCard.value) {
                    Log.d("EscapeRouteMap", "🎉 목적지 도착 (좌표 동일) → 안내 카드 표시")
                    showArrivalCard.value = true

                    // 목적지 마커만 제거
                    destinationMarker.value?.let {
                        pointAnnotationManager.value?.delete(it)
                        destinationMarker.value = null
                    }
                }
            } else {
                Log.w(
                    "EscapeRouteMap",
                    "❌ 도착 아님 → destination=(${destination.x}, ${destination.y}, floor=${destination.floor}) | " +
                            "current=(${currentPosition.x}, ${currentPosition.y}, floor=${currentPosition.floor})"
                )
            }

        }
        lineState.value = lineState.value + 1
    }
    LaunchedEffect(lineState, showRoute.value) {
        while (showRoute.value) {
            polylineList.forEach { polyline ->
                polyline.lineColorInt = colors[colorIndex]
                polylineManager.value?.update(polyline)
            }
            colorIndex = (colorIndex + 1) % colors.size
            delay(500)
        }
    }


    // 안내 종료 모달
    LaunchedEffect(showArrivalCard.value) {
        if (showArrivalCard.value) {
            kotlinx.coroutines.delay(5000)
            showArrivalCard.value = false
            isGuiding.value = false // ✅ 안내 종료 버튼도 함께 사라지게

            Log.d("DEST_MARKER", "🧪 삭제 시도 전 상태: ${destinationMarker.value}")
            // 도착지 마커 제거
            destinationMarker.value?.let {
                pointAnnotationManager.value?.delete(it)
                Log.d("DEST_MARKER", "🗑️ 도착 마커 삭제 시도: $destinationMarker")
            }
            destinationMarker.value = null
            Log.d("DEST_MARKER", "🗑️ 도착 마커 삭제 시도 (null 여부 상관없이 초기화)")

            // 경로 마커 제거
            routeMarkers.forEach {
                pointAnnotationManager.value?.delete(it)
            }
            routeMarkers.clear()

            // 경로 선 제거
            polylineManager.value?.deleteAll()
        }
    }


    //화재 났을 때 화면 붉게 하기
    LaunchedEffect(fireNotificationDto) {
        val isFireActive =
            fireNotificationDto?.beaconNotificationDtos?.any { it.isNewFire == 1 } == true

        if (isFireActive && !redLighting.value) {
            launch {
                redLighting.value = true
                Log.d("Debug", "Red lighting started")
                delay(10000) // 5초 대기
                redLighting.value = false
                Log.d("Debug", "Red lighting stopped after 5 seconds")
            }
        }


        // 새로운 화재 나타날 떄 실시간 모달 자동 표시
        val currentFires = fireNotificationDto?.beaconNotificationDtos
            ?.filter { it.isNewFire == 1 }
            ?.map { it.beaconCode } ?: emptyList()


        // 🔍 이전에 없던 새 화재 탐색
        val newFireCode = currentFires.firstOrNull { it !in previousFireCodes }

        if (newFireCode != null) {
            val newFire =
                fireNotificationDto?.beaconNotificationDtos?.firstOrNull { it.beaconCode == newFireCode }

            if (newFire != null) {
                Log.d("🔥 Fire", "🚨 새롭게 추가된 화재 감지 → 모달 표시")
                isFireStationShown.value = true

                mapView.mapboxMap.flyTo(
                    CameraOptions.Builder()
                        .zoom(zoomLevel)
                        .center(Point.fromLngLat(newFire.coordX, newFire.coordY))
                        .build()
                )

                // 🔥 3. 잠시 대기 후 경로 재요청 (시각적으로 순서를 보장)
                delay(1000)

                if (isGuiding.value) {
                    Log.d("🔥 Fire", "📍 새 화재 후 경로 재탐색 실행")
                    currentLocationCode?.let { code ->
                        viewModel.fetchEscapeRoute(222, code)
                    }
                }

            }
        }

        // 🔄 현재 화재 상태 저장 (다음 변경 대비)
        previousFireCodes.clear()
        previousFireCodes.addAll(currentFires)


        // 층수 바뀌는 것 적용하기
        viewModel.updateFireFloors(fireNotificationDto)

    }

    //🔥빨간색 깜빡임 애니메이션
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    //🔥 화재 상황일 때 빨간색 깜빡임 효과 추가
    Box(modifier = Modifier.fillMaxSize()) {

        if (redLighting.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = alpha)) // 투명도가 변하는 빨간색 배경
            )
        }
    }


// UI 구성
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                if (isCardVisible.value) {
                    Log.d("EscapeRouteUI", "🛑 화면 클릭 → 화재 모달 닫기")
                    isCardVisible.value = false
                }
            }
    ) {
        // 1. 지도 배경
        AndroidView(factory = { mapView })

        // 화재 역정보 모달
        if (targetBeaconDto?.imageUrl?.isNotEmpty() == true && fireNotification != null) {

            // 🚀 최초 진입 시 fadeIn 트리거
            LaunchedEffect(isCardVisible.value) {
                if (isCardVisible.value) {
                    isFireStationShown.value = true
                }
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ){
                // 🔹 배경 클릭 감지를 위한 반응 없는 투명 레이어
                if (isFireStationShown.value) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                Log.d("FireModal", "📴 배경 클릭 → 모달 닫기")
                                isFireStationShown.value = false
                            }
                    )
                }

                // 🔸 FireStation 모달 (화면 상단에 고정)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 36.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    AnimatedVisibility(
                        visible = isFireStationShown.value,
                        enter = slideInVertically(initialOffsetY = { -100 }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -100 }) + fadeOut()
                    ) {
                        FireStation(
                            stationName = fireNotification?.stationName ?: "",
                            beaconName = targetBeaconDto?.beaconName ?: "알 수 없음",
                            imageUrl = targetBeaconDto?.imageUrl ?: "",
                            isVisible = isCardVisible.value,
                            onDismiss = { isFireStationShown.value = false },
                            onGuideClick = { Log.d("FireModal", "➡️ 대피 경로 클릭") }
                        )
                    }
                }
            }
        }


        // 빨간색 화면 생성
        if (redLighting.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = alpha)) // 투명도가 변하는 빨간색 배경
            )
        }

        // ✅ 도착 알림 카드
        if (showArrivalCard.value) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp)
            ) {
                ArrivalCard(
                    onDismiss = {
                        showArrivalCard.value = false
                        isGuiding.value = false
                    },
                    // 경로 재안내 눌렀을 때
                    onRetry = {
                        Log.d("ArrivalCard", "🔁 경로 재안내 요청")
                        hasArrived.value = false
                        showArrivalCard.value = false
                        isGuiding.value = true
                        showRoute.value = true

                        currentLocationCode?.let {
                            viewModel.fetchEscapeRoute(it, it)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                )
            }
        }

        // 2. 오버레이 전체 (층 버튼, 안내 버튼 등)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.Start
        ) {
            ToMyLocation(
                modifier = Modifier.align(Alignment.Start),
                mapView = mapView,
                myLocation = myLocation,
                onClick = { }
            )
            Spacer(modifier = Modifier.height(15.dp))
            FloorSelector(
                selectedFloor = selectedFloor.value,
                onFloorSelected = { selectedFloor.value = it },
                firefloor = fireFloors
            )
            if (!showArrivalCard.value) {
                Spacer(modifier = Modifier.height(36.dp))
                EvacuationButton(
                    isGuiding = isGuiding.value,
                    onClick = {
                        if (isGuiding.value) {
                            // ✅ 안내 종료 처리
                            isGuiding.value = false
                            showRoute.value = false
                            polylineManager.value?.deleteAll()
                            goalMarker.value?.let { pointAnnotationManager.value?.delete(it) }
                            routeMarkers.forEach { pointAnnotationManager.value?.delete(it) }
                            routeMarkers.clear()
                            myLocationAnnotation.value?.let {
                                pointAnnotationManager.value?.delete(
                                    it
                                )
                            }
                            viewModel.setIsTracking(false)
                        } else {
                            // ✅ 안내 시작
                            hasArrived.value = false
                            currentLocationCode?.let { code ->
                                viewModel.fetchEscapeRoute(222, code)
                                showRoute.value = true
                                isGuiding.value = true
                                viewModel.setIsTracking(true)
                            }
                        }
                    }
                )
            }




        }


        // 조건 체크만 따로
        val shouldShowFireNotificationCard =
            isFireNotificationCardVisible.value &&
                    selectedFireBeaconDto.value != null &&
                    isFireIconClicked.value == true

            // ✅ 🔥 상세 모달
        AnimatedVisibility(
            visible = shouldShowFireNotificationCard,
            enter = slideInVertically(initialOffsetY = { -1000 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -1000 }) + fadeOut()
        ){
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .padding(top = 36.dp)
//                    .padding(horizontal = 32.dp, vertical = 22.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* 내부 탭 무시 */ }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                Log.d("FireModal", "🛑 배경 클릭 → 모달 닫기")
                                isFireNotificationCardVisible.value = false
                            }
                        )
                    },
                contentAlignment = Alignment.TopCenter
            )   {
                FireNotificationCard(
                    beaconName = selectedFireBeaconDto.value?.beaconName ?: "알 수 없음",
                    imageUrl = selectedImageUrl.value,
                    isVisible = isFireNotificationCardVisible.value,
                    onDismiss = {
                        Log.d("FireModal", "🛑 모달 닫기 버튼 클릭")
                        isFireNotificationCardVisible.value = false
                        isFireIconClicked.value = false
                    },
                    onGuideClick = {
                        Log.d("FireModal", "➡️ 대피 경로 찾기 클릭됨")
                        isFireNotificationCardVisible.value = false
                        currentLocationCode?.let { code ->
                            viewModel.fetchEscapeRoute(222, code)
                            showRoute.value = true
                            isGuiding.value = true
                        }
                    }
                )
            }
        }
    }
}


