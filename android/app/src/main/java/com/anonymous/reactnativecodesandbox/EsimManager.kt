package com.anonymous.reactnativecodesandbox

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.euicc.DownloadableSubscription
import android.telephony.euicc.EuiccManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi

class EsimManager(
    private val activity: ComponentActivity,
    private val callback: EsimInstallCallback? = null
) {
    private var euiccManager: EuiccManager? = null
    private val ACTION_DOWNLOAD_SUBSCRIPTION = "com.anonymous.reactnativecodesandbox.DOWNLOAD_SUBSCRIPTION"
    private var currentActivationCode: String? = null

    interface EsimInstallCallback {
        fun onInstallComplete(success: Boolean, message: String)
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_DOWNLOAD_SUBSCRIPTION) {
                return
            }

            val resultCode = resultCode
            val detailedCode = intent.getIntExtra(
                EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE,
                0
            )

            Log.i("EsimManager", "Download result - resultCode: $resultCode, detailedCode: $detailedCode")

            when (resultCode) {
                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK -> {
                    Log.i("EsimManager", "eSIM downloaded successfully")
                    Toast.makeText(activity, "eSIM Installed successfully", Toast.LENGTH_SHORT).show()
                    callback?.onInstallComplete(true, "eSIM installed successfully")
                    currentActivationCode = null
                }
                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR -> {
                    Log.w("EsimManager", "Resolvable error - attempting resolution (will show system confirmation dialog)")
                    try {
                        // Inspect extras to find a resolution Intent provided by the platform/vendor
                        var resolutionIntent: Intent? = null
                        try {
                            val extras = intent.extras
                            if (extras != null && !extras.isEmpty) {
                                for (key in extras.keySet()) {
                                    try {
                                        val value = extras.get(key)
                                        Log.i("EsimManager", "Download intent extra: $key = ${value?.javaClass?.name} -> $value")
                                        if (value is Intent && resolutionIntent == null) {
                                            resolutionIntent = value
                                            Log.i("EsimManager", "Found resolution Intent in extras under key: $key")
                                        }
                                    } catch (inner: Exception) {
                                        Log.w("EsimManager", "Failed to read extra $key", inner)
                                    }
                                }
                            } else {
                                Log.i("EsimManager", "No extras found on download intent")
                            }
                        } catch (ex: Exception) {
                            Log.w("EsimManager", "Failed enumerating download intent extras", ex)
                        }

                        // Prepare callback PendingIntent to receive the post-resolution broadcast
                        val callbackIntent = PendingIntent.getBroadcast(
                            activity,
                            0,
                            Intent(ACTION_DOWNLOAD_SUBSCRIPTION).setPackage(activity.packageName),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )

                        // If platform provided a dedicated resolution Intent, use it; otherwise fall back to the broadcast intent
                        val toResolve = resolutionIntent ?: intent

                        euiccManager?.startResolutionActivity(
                            activity,
                            REQUEST_CODE_RESOLVE_ERROR,
                            toResolve,
                            callbackIntent
                        )

                        Log.i("EsimManager", "Resolution activity started - waiting for user confirmation")
                        // Don't call callback yet - wait for resolution result in handleActivityResult and final broadcast
                    } catch (e: Exception) {
                        Log.e("EsimManager", "Failed to start resolution activity", e)
                        Toast.makeText(activity, "Failed to request eSIM permission", Toast.LENGTH_SHORT).show()
                        callback?.onInstallComplete(false, "Failed to request permission: ${e.message}")
                        currentActivationCode = null
                    }
                }
                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR -> {
                    val errorMessage = "eSIM installation failed (error code: $detailedCode)"
                    Log.e("EsimManager", errorMessage)
                    Toast.makeText(activity, "eSIM Installation Failed", Toast.LENGTH_SHORT).show()
                    callback?.onInstallComplete(false, errorMessage)
                    currentActivationCode = null
                }
                else -> {
                    Log.w("EsimManager", "Unknown result code: $resultCode")
                    Toast.makeText(activity, "eSIM installation cancelled", Toast.LENGTH_SHORT).show()
                    callback?.onInstallComplete(false, "eSIM installation cancelled")
                    currentActivationCode = null
                }
            }
        }
    }

    init {
        // Register the broadcast receiver for download subscription
        val filter = IntentFilter(ACTION_DOWNLOAD_SUBSCRIPTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(
                downloadReceiver,
                filter,
                Context.RECEIVER_EXPORTED
            )
        } else {
            activity.registerReceiver(downloadReceiver, filter)
        }

        euiccManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            activity.getSystemService(EuiccManager::class.java)
        } else {
            null
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE_RESOLVE_ERROR) {
            return
        }

        Log.i("EsimManager", "Resolution activity result - requestCode: $requestCode, resultCode: $resultCode")
        Log.i("EsimManager", "RESULT_OK = ${Activity.RESULT_OK}, RESULT_CANCELED = ${Activity.RESULT_CANCELED}")
        
        // Log any data extras
        if (data != null) {
            Log.i("EsimManager", "Result intent action: ${data.action}")
            data.extras?.let { bundle ->
                Log.i("EsimManager", "Result intent extras:")
                for (key in bundle.keySet()) {
                    try {
                        val value = bundle.get(key)
                        Log.i("EsimManager", "  Extra: $key = $value")
                    } catch (e: Exception) {
                        Log.w("EsimManager", "  Extra: $key (error reading: ${e.message})")
                    }
                }
            }
        } else {
            Log.i("EsimManager", "Result intent data is null")
        }

        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.i("EsimManager", "User accepted permission - waiting for download to complete")
                // Don't call callback - wait for downloadReceiver to get RESULT_OK or ERROR
            }
            Activity.RESULT_CANCELED -> {
                // This might be returned even when user accepts on some devices
                // Wait a moment to see if we get a broadcast result
                Log.w("EsimManager", "Got RESULT_CANCELED - checking if download continues anyway")
                
                // Don't immediately call callback - the download might still continue
                // We'll handle the final result in the broadcast receiver
                // Only if we don't get a broadcast in a few seconds, then it was truly cancelled
            }
            else -> {
                val errorMessage = "Resolution failed with result code: $resultCode"
                Log.e("EsimManager", errorMessage)
                Toast.makeText(activity, "eSIM Installation Failed", Toast.LENGTH_SHORT).show()
                callback?.onInstallComplete(false, errorMessage)
                currentActivationCode = null
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun install(code: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.e("EsimManager", "Android version ${Build.VERSION.SDK_INT} does not support eSIM installation")
            callback?.onInstallComplete(false, "Android version not supported. Requires Android 9 (API 28) or higher")
            return
        }

        if (euiccManager == null) {
            Log.e("EsimManager", "EuiccManager is not available")
            callback?.onInstallComplete(false, "eSIM not supported on this device")
            return
        }

        if (euiccManager?.isEnabled != true) {
            Log.e("EsimManager", "eSIM is not enabled on this device")
            callback?.onInstallComplete(false, "eSIM is not enabled on this device")
            return
        }

        runCatching {
            currentActivationCode = code
            
            // Create DownloadableSubscription from activation code
            val subscription = DownloadableSubscription.forActivationCode(code)
            
            Log.i("EsimManager", "Attempting automated eSIM installation with code: $code")
            Log.i("EsimManager", "Encoded activation code: ${subscription.encodedActivationCode}")
            
            // Create pending intent for the result
            val intent = Intent(ACTION_DOWNLOAD_SUBSCRIPTION).setPackage(activity.packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                activity,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            
            // Try automated download first
            // If it fails with RESOLVABLE_ERROR/ERROR, we'll fall back to native UI
            euiccManager?.downloadSubscription(subscription, true, pendingIntent)
            
        }.onFailure { error ->
            Log.e("EsimManager", "Failed to start eSIM installation", error)
            callback?.onInstallComplete(false, "Failed to start eSIM installation: ${error.message}")
            currentActivationCode = null
        }
    }

    fun cleanup() {
        try {
            activity.unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            Log.e("EsimManager", "Error unregistering receiver", e)
        }
    }

    companion object {
        const val REQUEST_CODE_RESOLVE_ERROR = 1001
    }
}