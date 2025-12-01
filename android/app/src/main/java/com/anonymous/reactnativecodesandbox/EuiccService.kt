package com.anonymous.reactnativecodesandbox

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.service.euicc.ICarrierEuiccProvisioningService
import android.service.euicc.IGetActivationCodeCallback

class EuiccService : Service() {

    override fun onBind(intent: Intent?): IBinder {
        return object : ICarrierEuiccProvisioningService.Stub() {
            override fun getActivationCode(callback: IGetActivationCodeCallback?) {
                EsimManager.code.let { callback?.onSuccess(it) }
            }

            override fun getActivationCodeForEid(
                eid: String?, callback: IGetActivationCodeCallback?
            ) {
                EsimManager.code.let { callback?.onSuccess(it) }
            }
        }
    }
}