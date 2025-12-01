package com.roamz.esim;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

public class HandlerForEsimDelete {

    public interface EsimDeleteCallback {
        void onComplete(boolean success, String message);
    }

    private final Context context;
    private final Activity activity;
    private final EuiccManager mgr;
    private EsimDeleteCallback callback;
    private static final String ACTION_DELETE_SUBSCRIPTION = "delete_subscription";

    public HandlerForEsimDelete(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
        this.mgr = (EuiccManager) context.getSystemService(Context.EUICC_SERVICE);

        context.registerReceiver(receiver, new IntentFilter(ACTION_DELETE_SUBSCRIPTION), Context.RECEIVER_EXPORTED);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int resultCode = getResultCode();
            if (callback != null) {
                if (resultCode == EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK) {
                    callback.onComplete(true, "eSIM deleted successfully");
                } else {
                    callback.onComplete(false, "eSIM deletion failed with code: " + resultCode);
                }
                callback = null;
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void deleteEsim(int subscriptionId, EsimDeleteCallback callback) {
        this.callback = callback;

        Intent intent = new Intent(ACTION_DELETE_SUBSCRIPTION).setPackage(context.getPackageName());
        PendingIntent callbackIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        try {
            mgr.deleteSubscription(subscriptionId, callbackIntent);
        } catch (Exception e) {
            if (callback != null) {
                callback.onComplete(false, "Exception: " + e.getMessage());
                this.callback = null;
            }
        }
    }
}
