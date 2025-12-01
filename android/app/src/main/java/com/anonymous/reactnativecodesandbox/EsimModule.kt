package com.anonymous.reactnativecodesandbox

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

import android.telephony.euicc.DownloadableSubscription
import android.telephony.euicc.EuiccManager
import com.facebook.react.bridge.*
import android.telephony.TelephonyManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.Manifest
import android.util.Log
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.CellInfoCdma
import android.telephony.CellInfoNr
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.bridge.WritableNativeArray


@ReactModule(name = EsimModule.NAME)
class EsimModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "EsimModule"
        // Static reference so native entry points (Activity) can reliably notify this module
        @JvmStatic
        var instance: EsimModule? = null
    }

    init {
        instance = this
    }

    private fun hasPhoneStatePermission(): Boolean {
        val ctx = reactApplicationContext
        return ActivityCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }

    @ReactMethod
    fun getActiveSubscriptions(promise: Promise) {
        if (!hasPhoneStatePermission()) {
            promise.reject("E_PERMISSION", "READ_PHONE_STATE not granted")
            return
        }
        try {
            val sm = reactApplicationContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            // ... query subscriptions safely ...
            val result = Arguments.createArray()
            // populate result
            promise.resolve(result)
        } catch (t: Throwable) {
            Log.e("EsimModule", "getActiveSubscriptions failed", t)
            promise.reject("E_NATIVE", t.message, t)
        }
    }

    private var pendingPromise: Promise? = null

    override fun getName(): String {
        return "EsimModule"
    }

    @ReactMethod
    fun downloadEsimProfile(smdpAddress: String, promise: Promise) {
        try {
            val currentActivity = reactApplicationContext.currentActivity

            Log.e("smdpAddress", smdpAddress)
            
            // val subscriptionManager = currentActivity?.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            // val euiccManager = currentActivity?.getSystemService(Context.EUICC_SERVICE) as EuiccManager
            // Log.e("smdpAddress", smdpAddress)
            // Log.e("activationCode",activationCode)
            
            // if (ActivityCompat.checkSelfPermission(currentActivity, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            //     val subscriptionId = SubscriptionManager.getDefaultSubscriptionId()  // Get the first available subscription
            //     val telephonyManager = (currentActivity?.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
            //         .createForSubscriptionId(subscriptionId)
            //         val hasPrivileges = telephonyManager.hasCarrierPrivileges()

            //         Log.e("CarrierPrivileges", "Has Carrier Privileges: $hasPrivileges")
            // }
            // else
            // {
            //     Log.e("No Permit", "AndroidManifest missing configuration READ_PHONE_STATE")
            // }
            // // Create a DownloadableSubscription.
            // val subscription = DownloadableSubscription.forActivationCode(activationCode)

            // val encodedActivationCode = subscription.getEncodedActivationCode()
            // if (encodedActivationCode != null) {
            //     Log.e("encodedActivationCode", encodedActivationCode)
            // } else {
            //     Log.e("encodedActivationCode", "Activation code is null")
            // }

            // // Create an Intent for the BroadcastReceiver.
            // val intent = Intent(currentActivity, EsimDownloadReceiver::class.java)

            // // Create a PendingIntent.
            // val pendingIntent: PendingIntent = PendingIntent.getBroadcast(
            //     currentActivity,
            //     0,
            //     intent,
            //     PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            // )

            // // Call downloadSubscription with the correct parameters.
            // euiccManager.downloadSubscription(subscription, false, pendingIntent) // Add the isAlwaysAllow parameter
            
            // euiccManager.startResolutionActivity(currentActivity, 42, intent, null)
            
            val mainActivity = currentActivity
            if (mainActivity is MainActivityCallback) {
                this.pendingPromise = promise
                mainActivity.executeInstallFunction(smdpAddress)
            } else {
                promise.reject("E_MAIN_ACTIVITY", "MainActivity not available")
            }

            // promise.resolve("eSIM profile download initiated") // Resolve promise after initiating download

        } catch (e: Exception) {
            promise.reject("E_SIM_ERROR", e.message)
        }
    }

    private fun requestPermissionsIfNecessary(currentActivity: Activity) {
        val requiredPermissions = arrayOf(
            Manifest.permission.READ_PHONE_STATE
        )

        val missingPermissions = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(currentActivity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                currentActivity,
                missingPermissions.toTypedArray(),
                1001 // Request code
            )
        }
    }
    
    
    fun onEsimDownloadComplete(success: Boolean, message: String?) {
        pendingPromise?.let {
            if (success) {
                it.resolve(message ?: "eSIM installed successfully MD")
            } else {
                it.reject("E_SIM_FAIL", message ?: "eSIM installation failed")
            }
            pendingPromise = null
        }
    }

    @ReactMethod
    fun getCellInfo(promise: Promise) {
        try {
            val telephonyManager =
                    reactApplicationContext.getSystemService(Context.TELEPHONY_SERVICE) as
                            TelephonyManager
            val cellInfoList = telephonyManager.allCellInfo

            if (cellInfoList.isNotEmpty()) {
                val cellInfo = cellInfoList[0]
                val result: WritableMap = Arguments.createMap()

                when (cellInfo) {
                    is CellInfoGsm -> {
                        result.putString("type", "GSM")
                        result.putInt("cellId", cellInfo.cellIdentity.cid)
                        result.putInt("lac", cellInfo.cellIdentity.lac)
                    }
                    is CellInfoLte -> {
                        result.putString("type", "LTE")
                        result.putInt("cellId", cellInfo.cellIdentity.ci)
                        result.putInt("tac", cellInfo.cellIdentity.tac)
                    }
                    is CellInfoWcdma -> {
                        result.putString("type", "WCDMA")
                        result.putInt("cellId", cellInfo.cellIdentity.cid)
                        result.putInt("lac", cellInfo.cellIdentity.lac)
                    }
                    is CellInfoNr -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            result.putString("type", "5G_NR")
                            result.putDouble("cellId", 0.00) // Convert long to double
                            result.putInt("tac", 0)
                        } else {
                            promise.reject(
                                    "E_LOW_API",
                                    "5G cell info requires Android 10 (API level 29) or higher"
                            )
                            return
                        }
                    }
                    else -> {
                        promise.reject(
                                "E_UNSUPPORTED_CELL_TYPE",
                                "Cell type not supported on this device"
                        )
                        return
                    }
                }

                promise.resolve(result)
            } else {
                promise.reject("E_NO_CELL_INFO", "No cell information available")
            }
        } catch (e: Exception) {
            promise.reject("E_SIM_ERROR", e.message)
        }
    }

    @ReactMethod
    fun listEsimProfiles(promise: Promise) {
        try {
            val subscriptionManager =
                    reactApplicationContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as
                            SubscriptionManager

            // Ensure permission is granted and list is not null
            val infoList: List<SubscriptionInfo>? = subscriptionManager.getActiveSubscriptionInfoList()
            //getAccessibleSubscriptionInfoList()

            val esimList = WritableNativeArray()

            infoList?.forEach { subscriptionInfo: SubscriptionInfo ->
                if (subscriptionInfo.isEmbedded) {
                    val map = WritableNativeMap()
                    map.putInt("subscriptionId", subscriptionInfo.getSubscriptionId())
                    map.putString("carrierName", subscriptionInfo.getCarrierName()?.toString())
                    map.putString("displayName", subscriptionInfo.getDisplayName()?.toString())
                    map.putInt("simSlotIndex", subscriptionInfo.getSubscriptionId())
                    map.putInt("portIndex", subscriptionInfo.getPortIndex())

                    esimList.pushMap(map)
                }
            }

            promise.resolve(esimList)
        } catch (e: Exception) {
            promise.reject("ESIM_ERROR", "Error fetching eSIMs", e)
        }
    }

    @ReactMethod
    fun deleteEsimProfile(subscriptionId: Int, promise: Promise) {
        try {
            val currentActivity = reactApplicationContext.currentActivity
            if (currentActivity is MainActivityCallback) {
                pendingPromise = promise
                currentActivity.executeDeleteFunction(subscriptionId)
            } else {
                promise.reject("E_MAIN_ACTIVITY", "MainActivity not available")
            }
        } catch (e: Exception) {
            promise.reject("E_ESIM_DELETE", e.message)
        }
    }

    fun onEsimDeleteComplete(success: Boolean, message: String?) {
        pendingPromise?.let {
            if (success) {
                it.resolve(message ?: "eSIM deleted successfully")
            } else {
                it.reject("E_DELETE_FAIL", message ?: "eSIM deletion failed")
            }
            pendingPromise = null
        }
    }

    @ReactMethod
    fun toggleEsimProfile(enable: Boolean, subscriptionId: Int, portIndex: Int, promise: Promise) {
        try {
            val currentActivity = reactApplicationContext.currentActivity
            if (currentActivity is MainActivityCallback) {
                pendingPromise = promise
                currentActivity.executeEsimToggle(enable, subscriptionId, portIndex)
            } else {
                promise.reject("E_MAIN_ACTIVITY", "MainActivity not available")
            }
        } catch (e: Exception) {
            promise.reject("E_TOGGLE_FAIL", e.message)
        }
    }

    fun onEsimToggleComplete(success: Boolean, message: String?) {
        pendingPromise?.let {
            if (success) {
                it.resolve(message ?: "eSIM toggled successfully")
            } else {
                it.reject("E_TOGGLE_FAIL", message ?: "eSIM toggle failed")
            }
            pendingPromise = null
        }
    }


    // fun deleteEsimProfile(id: Int, promise: Promise) {
    //     try {
    //         val currentActivity: Activity? = currentActivity
            
    //         val mainActivity = currentActivity
    //         if (mainActivity is MainActivityCallback) {
    //             this.pendingPromise = promise
    //             mainActivity.executeDeleteFunction(id)
    //         } else {
    //             promise.reject("E_MAIN_ACTIVITY", "MainActivity not available")
    //         }

    //         // promise.resolve("eSIM profile download initiated") // Resolve promise after initiating
    //         // download

    //     } catch (e: Exception) {
    //         promise.reject("E_DSIM_ERROR", e.message)
    //     }

    // }

    // fun onEsimDeleteComplete(success: Boolean, message: String?) {
    //     pendingPromise?.let {
    //         if (success) {
    //             it.resolve(message ?: "eSIM installed successfully MD")
    //         } else {
    //             it.reject("E_SIM_FAIL", message ?: "eSIM installation failed")
    //         }
    //         pendingPromise = null
    //     }
    // }


    // @ReactMethod
    // fun setEsimEnabled(subscriptionId: Int, enabled: Boolean, promise: Promise) {
    //     try {
    //         val subscriptionManager =
    //                 reactApplicationContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as
    //                         SubscriptionManager

    //         // Check if your app has permission to make this call
    //         val telephonyManager =
    //                 reactApplicationContext.getSystemService(Context.TELEPHONY_SERVICE) as
    //                         TelephonyManager
    //         val tmForSub = telephonyManager.createForSubscriptionId(subscriptionId)

    //         val hasPrivileges = tmForSub.hasCarrierPrivileges()
    //         if (!hasPrivileges) {
    //             promise.reject(
    //                     "NO_PRIVILEGES",
    //                     "App does not have carrier privileges for this subscription."
    //             )
    //             return
    //         }

    //         if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
    //             subscriptionManager.setSubscriptionEnabled(subscriptionId, enabled)
    //             promise.resolve(
    //                     "Subscription $subscriptionId ${if (enabled) "enabled" else "disabled"} successfully."
    //             )
    //         } else {
    //             promise.reject("API_TOO_LOW", "Requires Android 10 (API 29) or higher.")
    //         }
    //     } catch (e: SecurityException) {
    //         promise.reject("SECURITY_EXCEPTION", "Security exception: ${e.message}", e)
    //     } catch (e: Exception) {
    //         promise.reject(
    //                 "ESIM_ACTIVATION_ERROR",
    //                 "Failed to change subscription state: ${e.message}",
    //                 e
    //         )
    //     }
    // }


}
