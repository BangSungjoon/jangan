//package com.ssafy.jangan_mobile.service
//
//import android.app.Notification
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.content.pm.ServiceInfo
//import android.os.Build
//import android.os.IBinder
//import android.util.Log
//import androidx.lifecycle.findViewTreeLifecycleOwner
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.cancel
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//
//// 백그라운드에서 앱이 실행되도록 하는 서비스
//class PersistentService : Service() {
//
//    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
//
//    override fun onBind(intent: Intent?): IBinder? {
//        return null
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
//        }
//
//        // 여기에 백그라운드 작업 넣기
//        //startSomeBackgroundWork()
//
//        return START_STICKY
//    }
//
//    private fun createNotification(): Notification {
//        val channelId = "persistent_service_channel"
//        val channelName = "Persistent Service"
//
//        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW).apply{
//            setShowBadge(false)
//        }
//        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        manager.createNotificationChannel(chan)
//
//        return Notification.Builder(this, channelId)
//            .setContentTitle("앱이 실행 중입니다")
//            .setContentText("백그라운드에서 동작 중")
//            .build()
//    }
//
//    private fun startSomeBackgroundWork() {
//        // TODO: 백그라운드에서 돌아갈 작업들 넣기
//        serviceScope.launch {
//            while(true){
//                // 무한반복으로 처리할 작업
//                Log.d("", "background running...")
//                delay(1000)
//            }
//        }
//    }
//
//    override fun onDestroy(){
//        super.onDestroy()
//        serviceScope.cancel()
//    }
//}
package com.ssafy.jangan_mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import com.ssafy.jangan_mobile.MainActivity
import com.ssafy.jangan_mobile.R
import com.ssafy.jangan_mobile.store.FireNotificationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Region

// 백그라운드에서 앱이 실행되도록 하는 서비스
class PersistentService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var region: Region
    private var beaconManager: BeaconManager? = null

    private var nearestBeaconCode = -1
    private var nearestBeaconDistance = Double.MAX_VALUE
    private var nearestStationId = -1

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "beacon_service_channel"

    private val beaconObserver = Observer<Collection<Beacon>> { beacons ->
        // 조건에 맞는 비콘만 필터링
        val filtered = beacons.filter {
            it.id1.toString().startsWith("AAAAA204", true)
        }

        if (filtered.isNotEmpty()) {
            // RSSI 기준으로 가장 강한(=가장 가까운) 비콘 하나만 선택
            val nearest = filtered.minByOrNull { it.rssi * -1 }  // RSSI 값 클수록 가깝다
            nearest?.let { beacon ->
                val code = beacon.id3.toInt()
                val distance = beacon.distance
                val stationId = beacon.id2.toInt()

                nearestBeaconCode = code
                nearestBeaconDistance = distance
                nearestStationId = stationId

                Log.d("BackgroundBeacon", "즉시 갱신 - station:$stationId, code:$code, distance:$distance")

                // 현재 위치 업데이트
                FireNotificationStore.setCurrentLocationBeaconCode(nearestBeaconCode, this)
                FireNotificationStore.setCurrentLocationStationId(nearestStationId, this)

                // 알림 업데이트 (현재 위치 정보 포함)
                updateNotification()
            }
        }
    }


//    private val beaconObserver = Observer<Collection<Beacon>> { beacons ->
//        val found = beacons.any {
//            it.id1.toString().startsWith("AAAAA204", true)
//        }
//        if (found) {
//            nearestBeaconCode = -1
//            nearestBeaconDistance = Double.MAX_VALUE
//            nearestStationId = -1
//
//            beacons.forEach { beacon ->
//                if (beacon.id1.toString().startsWith("AAAAA204", true)) {
//                    val code = beacon.id3.toInt()
//                    val distance = beacon.distance
//                    val stationId = beacon.id2.toInt()
//
//                    if (nearestBeaconDistance > distance) {
//                        nearestBeaconCode = code
//                        nearestBeaconDistance = distance
//                        nearestStationId = stationId
//                    }
//                    Log.d("BackgroundBeacon", "감지된 비콘 - code:${code} distance:${distance}")
//                }
//            }
//
//            Log.d("BackgroundBeacon", "가장 가까운 비콘 업데이트 - station: ${nearestStationId}, code:${nearestBeaconCode}, distance:${nearestBeaconDistance}")
//
//            // 현재 위치 업데이트
//            FireNotificationStore.setCurrentLocationBeaconCode(nearestBeaconCode, this)
//            FireNotificationStore.setCurrentLocationStationId(nearestStationId, this)
//
//            // 알림 업데이트 (현재 위치 정보 포함)
//            updateNotification()
//        }
//    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("PersistentService", "서비스 시작됨")

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        // 비콘 매니저 초기화 및 스캔 시작
        initializeBeaconManager()
        startBeaconScanning()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channelName = "비콘 탐지 서비스"
        val channelDescription = "백그라운드에서 비콘을 탐지하고 위치를 추적합니다"

        val channel = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW).apply {
            description = channelDescription
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        // 앱을 열기 위한 PendingIntent
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("불이야 앱 실행 중")
            .setContentText("비콘 탐지를 통해 현재 위치를 추적하고 있습니다")
            .setSmallIcon(R.drawable.icon_big)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 사용자가 스와이프로 제거할 수 없도록
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val locationText = if (nearestStationId != -1 && nearestBeaconCode != -1) {
            "현재 위치를 파악하고 있습니다."
        } else {
            "비콘 탐지 중..."
        }

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val updatedNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("불이야 앱 실행 중")
            .setContentText(locationText)
            .setSmallIcon(R.drawable.icon_big) // 아이콘이 없다면 android.R.drawable.ic_dialog_info 사용
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, updatedNotification)
    }

    private fun initializeBeaconManager() {
        region = Region("background-beacon-scan", null, null, null)
        beaconManager = BeaconManager.getInstanceForApplication(this)

        // 백그라운드 스캔 주기 설정 (더 자주 스캔하도록)
        beaconManager?.apply {
            setEnableScheduledScanJobs(false)
            foregroundScanPeriod = 300L
            foregroundBetweenScanPeriod = 0L
            backgroundScanPeriod = 500L
            backgroundBetweenScanPeriod = 0L  // 백그라운드에서 2초 간격
//            setEnableScheduledScanJobs(false)  // JobScheduler 비활성화
            setBackgroundMode(false)           // 백그라운드 최적화 끄기
            updateScanPeriods()


            beaconParsers?.clear()
            beaconParsers?.add(
                BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")
            )
        }

        Log.d("PersistentService", "비콘 매니저 초기화 완료")
    }

    private fun startBeaconScanning() {
        beaconManager?.let { manager ->
            try {
                manager.startRangingBeacons(region)
                manager.getRegionViewModel(region).rangedBeacons.observeForever(beaconObserver)
                Log.d("PersistentService", "백그라운드 비콘 스캔 시작")

                // 주기적으로 비콘 스캔 상태 확인
                serviceScope.launch {
                    while (true) {
                        val isRanging = manager.rangedRegions.contains(region)
                        if (!isRanging) {
                            Log.w("PersistentService", "비콘 스캔이 중단됨, 재시작 시도")
                            manager.startRangingBeacons(region)
                        }
                        delay(30000) // 30초마다 확인
                    }
                }

            } catch (e: Exception) {
                Log.e("PersistentService", "비콘 스캔 시작 실패: ${e.message}")
            }
        }
    }

    private fun stopBeaconScanning() {
        beaconManager?.let { manager ->
            try {
                manager.getRegionViewModel(region).rangedBeacons.removeObserver(beaconObserver)
                manager.stopRangingBeacons(region)
                Log.d("PersistentService", "백그라운드 비콘 스캔 중단")
            } catch (e: Exception) {
                Log.e("PersistentService", "비콘 스캔 중단 실패: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        Log.d("PersistentService", "서비스 종료됨")
        stopBeaconScanning()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("PersistentService", "앱이 태스크에서 제거됨, 서비스 재시작")
        // 앱이 태스크에서 제거되어도 서비스가 계속 실행되도록
        val restartServiceIntent = Intent(applicationContext, PersistentService::class.java)
        startForegroundService(restartServiceIntent)
        super.onTaskRemoved(rootIntent)
    }
}