package com.anonymous.reactnativecodesandbox;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

import java.util.List;

public class EsimToggleHandler {

    public interface EsimToggleCallback {
        void onComplete(boolean success, String message);
    }

    private static final String ACTION_TOGGLE_SUBSCRIPTION = "toggle_subscription";
    private final Context context;
    private final Activity activity;
    private final EuiccManager euiccManager;
    private EsimToggleCallback callback;
    private int targetSubId = -1;
    private int portIndex = -1;

    public EsimToggleHandler(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
        this.euiccManager = (EuiccManager) context.getSystemService(Context.EUICC_SERVICE);

        // this.context.registerReceiver(toggleReceiver, new IntentFilter(ACTION_TOGGLE_SUBSCRIPTION));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(toggleReceiver, new IntentFilter(ACTION_TOGGLE_SUBSCRIPTION), Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(toggleReceiver, new IntentFilter(ACTION_TOGGLE_SUBSCRIPTION));
        }

    }

    public void toggleEsim(int subscriptionId, int portIndex, boolean enable, EsimToggleCallback callback) {
        this.callback = callback;
        this.portIndex = portIndex;

        Intent intent = new Intent(ACTION_TOGGLE_SUBSCRIPTION).setPackage(context.getPackageName());
        PendingIntent callbackIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        try {
            if (enable) {
                euiccManager.switchToSubscription(subscriptionId, portIndex, callbackIntent);
            } else {
                euiccManager.switchToSubscription(-1, portIndex, callbackIntent);
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onComplete(false, "Toggle failed: " + e.getMessage());
            }
        }
    }


    private final BroadcastReceiver toggleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int resultCode = getResultCode();
            Log.i("ESIM_TOGGLE", "Result: " + resultCode);

            if (callback != null) {
                if (resultCode == EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK) {
                    callback.onComplete(true, "eSIM toggled successfully");
                } else {
                    callback.onComplete(false, "Toggle failed with result code: " + resultCode);
                }
                callback = null;
            }
        }
    };
}
