package com.vbnet.gewerbenotification;

import android.app.Application;
import com.google.firebase.messaging.FirebaseMessaging;

public class GewerbeApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Enable FCM delivery metrics export to BigQuery
        // This is required for the MESSAGE_DELIVERED event to appear in BigQuery
        FirebaseMessaging.getInstance().setDeliveryMetricsExportToBigQuery(true);
    }
}