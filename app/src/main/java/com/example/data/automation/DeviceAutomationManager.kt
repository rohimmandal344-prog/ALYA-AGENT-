package com.example.data.automation

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AutomationResult(
    val success: Boolean,
    val message: String,
    val details: Map<String, Any?> = emptyMap()
)

data class AutomationCapability(
    val name: String,
    val description: String,
    val isSupported: Boolean,
    val statusText: String
)

/**
 * Honest, permission-respecting device automation manager.
 * Uses official Android APIs exclusively; never claims unsupported full device control.
 */
class DeviceAutomationManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /**
     * Toggles the device flashlight.
     */
    fun setFlashlight(enabled: Boolean): AutomationResult {
        if (cameraManager == null) {
            return AutomationResult(false, "Camera hardware not available on this device.")
        }
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, enabled)
                AutomationResult(
                    true,
                    if (enabled) "Torch/Flashlight turned ON." else "Torch/Flashlight turned OFF."
                )
            } else {
                AutomationResult(false, "No camera with flash hardware detected.")
            }
        } catch (e: CameraAccessException) {
            AutomationResult(false, "Cannot access camera flash: ${e.localizedMessage}")
        } catch (e: SecurityException) {
            AutomationResult(false, "Camera permission required for flashlight.")
        } catch (e: Exception) {
            AutomationResult(false, "Flashlight error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Reads battery percentage, charging state, and health via BatteryManager.
     */
    fun getBatteryStatus(): AutomationResult {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val batteryPct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                batteryManager?.isCharging == true
            } else {
                false
            }

            val statusMsg = if (batteryPct >= 0) {
                "Battery is at $batteryPct%${if (isCharging) " (Charging)" else " (Discharging)"}"
            } else {
                "Battery level unavailable from system."
            }

            AutomationResult(
                success = true,
                message = statusMsg,
                details = mapOf("percentage" to batteryPct, "isCharging" to isCharging)
            )
        } catch (e: Exception) {
            AutomationResult(false, "Failed to read battery status: ${e.localizedMessage}")
        }
    }

    /**
     * Adjusts system media volume.
     */
    fun adjustVolume(action: String): AutomationResult {
        if (audioManager == null) {
            return AutomationResult(false, "Audio service unavailable.")
        }
        return try {
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            when (action.uppercase(Locale.ROOT)) {
                "UP", "RAISE", "INCREASE" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    val newVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    AutomationResult(true, "Volume raised to $newVol / $maxVol")
                }
                "DOWN", "LOWER", "DECREASE" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                    val newVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    AutomationResult(true, "Volume lowered to $newVol / $maxVol")
                }
                "MUTE" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_MUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    AutomationResult(true, "Media volume muted.")
                }
                "UNMUTE" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_UNMUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    val newVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    AutomationResult(true, "Media volume unmuted ($newVol / $maxVol)")
                }
                else -> {
                    AutomationResult(false, "Unknown volume action: $action. Use UP, DOWN, MUTE, or UNMUTE.")
                }
            }
        } catch (e: Exception) {
            AutomationResult(false, "Failed to adjust volume: ${e.localizedMessage}")
        }
    }

    /**
     * Triggers a brief haptic vibration.
     */
    fun triggerVibration(durationMs: Long = 200): AutomationResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
                } else {
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
            AutomationResult(true, "Haptic vibration triggered ($durationMs ms).")
        } catch (e: Exception) {
            AutomationResult(false, "Vibration failed: ${e.localizedMessage}")
        }
    }

    /**
     * Safely opens an application via Android standard Intents.
     */
    fun openApplication(appName: String): AutomationResult {
        val query = appName.trim().lowercase(Locale.ROOT)
        val intent: Intent? = when {
            query.contains("youtube") -> {
                context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
            }
            query.contains("camera") -> {
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            }
            query.contains("calculator") -> {
                val calcIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
                if (calcIntent.resolveActivity(context.packageManager) != null) calcIntent
                else context.packageManager.getLaunchIntentForPackage("com.google.android.calculator")
            }
            query.contains("map") -> {
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
            }
            query.contains("clock") || query.contains("alarm") -> {
                Intent(AlarmClock.ACTION_SHOW_ALARMS)
            }
            query.contains("setting") -> {
                Intent(Settings.ACTION_SETTINGS)
            }
            else -> {
                // Try finding by package name search or launch intent
                val installedPackages = context.packageManager.getInstalledApplications(0)
                val match = installedPackages.firstOrNull { it.packageName.contains(query) }
                if (match != null) {
                    context.packageManager.getLaunchIntentForPackage(match.packageName)
                } else null
            }
        }

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                AutomationResult(true, "Opened $appName successfully.")
            } catch (e: Exception) {
                AutomationResult(false, "Could not open $appName: ${e.localizedMessage}")
            }
        }

        return AutomationResult(false, "Application '$appName' not found or unsupported.")
    }

    /**
     * Returns real system date, time, and timezone.
     */
    fun getDeviceTime(): AutomationResult {
        val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy, hh:mm a (z)", Locale.getDefault())
        val formatted = sdf.format(Date())
        return AutomationResult(true, "Current device time: $formatted")
    }

    /**
     * Executes accessibility navigation action if user enabled AlyaAccessibilityService.
     */
    fun performAccessibilityAction(action: String): AutomationResult {
        val service = AlyaAccessibilityService.instance
        if (service == null) {
            return AutomationResult(false, "Accessibility Service is not enabled. Please enable Alya in System Settings.")
        }
        val success = when (action.uppercase(Locale.ROOT)) {
            "BACK" -> service.performBack()
            "HOME" -> service.performHome()
            "RECENTS" -> service.performRecents()
            "NOTIFICATIONS" -> service.performNotifications()
            "QUICK_SETTINGS" -> service.performQuickSettings()
            "LOCK_SCREEN" -> service.performLockScreen()
            else -> false
        }
        return AutomationResult(success, if (success) "Executed $action navigation" else "Failed to execute $action navigation")
    }

    fun isAccessibilityActive(): Boolean = AlyaAccessibilityService.isServiceActive

    /**
     * Lists honest capabilities with accurate device status.
     */
    fun getHonestCapabilities(): List<AutomationCapability> {
        val hasFlash = try {
            cameraManager?.cameraIdList?.any { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } == true
        } catch (e: Exception) {
            false
        }

        val hasAccessibility = AlyaAccessibilityService.isServiceActive

        return listOf(
            AutomationCapability(
                name = "Accessibility Device Control",
                description = "Navigation gestures: Back, Home, Recents, Notifications via AccessibilityService",
                isSupported = hasAccessibility,
                statusText = if (hasAccessibility) "Active" else "Disabled in Settings"
            ),
            AutomationCapability(
                name = "Flashlight / Torch",
                description = "Turn device flash on and off using Camera2 API",
                isSupported = hasFlash,
                statusText = if (hasFlash) "Available" else "No Hardware Flash"
            ),
            AutomationCapability(
                name = "Battery Telemetry",
                description = "Inspect battery level and charging state via BatteryManager",
                isSupported = true,
                statusText = "Available"
            ),
            AutomationCapability(
                name = "Media Volume Control",
                description = "Adjust music and media volume via AudioManager",
                isSupported = audioManager != null,
                statusText = "Available"
            ),
            AutomationCapability(
                name = "App Launching",
                description = "Launch YouTube, Camera, Calculator, Maps, Settings via Android Intents",
                isSupported = true,
                statusText = "Available"
            ),
            AutomationCapability(
                name = "Haptic Vibration",
                description = "Trigger system vibration pulses",
                isSupported = true,
                statusText = "Available"
            ),
            AutomationCapability(
                name = "System Time & Date",
                description = "Accurate local clock and calendar reporting",
                isSupported = true,
                statusText = "Available"
            ),
            AutomationCapability(
                name = "Full System Control (Restricted)",
                description = "Root actions, touching arbitrary third-party screens without permission",
                isSupported = false,
                statusText = "Restricted (Honest OS Sandbox)"
            )
        )
    }
}
