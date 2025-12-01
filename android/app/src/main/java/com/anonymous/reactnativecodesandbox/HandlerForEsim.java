package com.roamz.esim;

import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.Context.RECEIVER_NOT_EXPORTED;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.os.Build;
import android.telephony.euicc.DownloadableSubscription;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

public class HandlerForEsim {

    public interface EsimCallback {
        void onComplete(boolean success, String message);
    }

    // Register receiver.
    static final String ACTION_DOWNLOAD_SUBSCRIPTION = "download_subscription";
    static final String ACTION_SWITCH_TO_SUBSCRIPTION = "switch_to_subscription";


    private final Context context;
    private EuiccManager mgr;
    private final Activity activity;
    private String action = "download_subscription";
    private  Intent resultIntent;
    private EsimCallback callback; // <-- Stores JS promise callback

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public HandlerForEsim(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;

        this.context.registerReceiver(receiver, new IntentFilter(ACTION_DOWNLOAD_SUBSCRIPTION), RECEIVER_EXPORTED );
        this.context.registerReceiver(receiverSwitch, new IntentFilter(ACTION_SWITCH_TO_SUBSCRIPTION), RECEIVER_EXPORTED );


        this.mgr =  (EuiccManager) context.getSystemService(Context.EUICC_SERVICE);
    }

    BroadcastReceiver receiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!action.equals(intent.getAction())) {
                        return;
                    }

                    int resultCode = getResultCode();
                    // int resultCode = intent.getIntExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_RESULT, EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR);

                    int detailedCode = intent.getIntExtra(
                            EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE,
                            0 /* defaultValue*/);

                    Log.i("ESIM_TAGG", "resultCode: " +resultCode );
                    Log.i("ESIM_TAGG", "detailedCode: " +detailedCode );




                    // If the result code is a resolvable error, call startResolutionActivity
                    if (resultCode == EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR) {
                        Log.e("ESIM_ERROR", "resoveble error");

                        PendingIntent callbackIntent = PendingIntent.getBroadcast(
                                context, 0 , intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                        try {
                            mgr.startResolutionActivity(
                                    activity,
                                    0 /* requestCode */,
                                    intent,
                                    callbackIntent);
                        } catch (IntentSender.SendIntentException e) {
                            Log.e("ESIM_ERROR_resoveble", e.toString());
                            throw new RuntimeException(e);
                        }
                    }
                    Log.i("ESIM_Sucess", "sucess");
                     resultIntent = intent;


                    // Switch to a subscription asynchronously.
                     intent = new Intent(ACTION_SWITCH_TO_SUBSCRIPTION).setPackage(context.getPackageName());
                    PendingIntent callbackIntent = PendingIntent.getBroadcast(
                            context, 0 /* requestCode */, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                    mgr.switchToSubscription(detailedCode, callbackIntent);
                }
            };

    BroadcastReceiver receiverSwitch =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.i("Called_receiverSwitch","receiverSwitch");

//                    if (!action.equals(intent.getAction())) {
//                        return;
//                    }
                    int resultCode = getResultCode();
                    // int resultCode = intent.getIntExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_RESULT, EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR);
                    int detailedCode = intent.getIntExtra(
                            EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE,
                            0 /* defaultValue*/);

                    Log.i("Called_receiverSwitch",resultCode +"receiverSwitch"+detailedCode);

                    resultIntent = intent;

                    if (callback != null) {
                        if (resultCode == EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK) {
                            callback.onComplete(true, "eSIM installed successfully");
                        } 
                        else {
                            callback.onComplete(false, "Failed to switch to eSIM" + resultCode);
                            // callback.onComplete(false, "eSIM installed with result code: " + resultCode);
                        }
                        callback = null; // cleanup
                    }
                }
            };


    // Switch to a subscription asynchronously.


    // Download subscription asynchronously.
    public void dowloadEsim (String code, EsimCallback callback){

        this.callback = callback;
        boolean isEnabled = mgr.isEnabled();

        Log.e("ESIM_Status: ", isEnabled + "");


        if (!isEnabled) {
            Log.e("ESIM_DISABLED", "Disabled");
            if (callback != null) {
                callback.onComplete(false, "eSIM not supported on device");
            }
            return;
        }

        DownloadableSubscription sub = DownloadableSubscription
                .forActivationCode(code /* encodedActivationCode*/);
        Intent intent = new Intent(action).setPackage(context.getPackageName());
        PendingIntent callbackIntent = PendingIntent.getBroadcast(
                context, 0 /* requestCode */, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        mgr.downloadSubscription(sub, true /* switchAfterDownload */,
               callbackIntent);
    }


}
