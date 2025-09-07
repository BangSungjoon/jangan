package com.ssafy.jangan_mobile.receive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ssafy.jangan_mobile.service.PersistentService

/**
 * 디바이스 부팅 완료 시 백그라운드 서비스를 자동으로 시작하는 리시버
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "부팅 완료 감지: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                startPersistentService(context)
            }
        }
    }

    private fun startPersistentService(context: Context) {
        try {
            val serviceIntent = Intent(context, PersistentService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
                Log.d("BootReceiver", "부팅 시 포그라운드 서비스 시작됨")
            } else {
                context.startService(serviceIntent)
                Log.d("BootReceiver", "부팅 시 백그라운드 서비스 시작됨")
            }
        } catch (e: Exception) {
            Log.e("BootReceiver", "부팅 시 서비스 시작 실패: ${e.message}")
        }
    }
}
