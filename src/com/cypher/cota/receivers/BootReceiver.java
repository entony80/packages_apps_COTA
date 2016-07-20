package com.cypher.cota.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.cypher.cota.App;
import com.cypher.cota.utils.AlarmUtils;
import com.cypher.cota.utils.DeviceInfoUtils;
import com.cypher.cota.utils.PreferenceUtils;

import org.piwik.sdk.PiwikApplication;
import org.piwik.sdk.Tracker;
import org.piwik.sdk.TrackHelper;

import java.util.HashMap;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmUtils.setAlarm(context, true);

        if (!PreferenceUtils.getPreference(context, PreferenceUtils.PROPERTY_FIRST_BOOT, false)) {
            //App app = (App)context.getApplicationContext();
            App app = App.getApplication();

            HashMap<String, String> segmentation = new HashMap<>();
            segmentation.put("device", DeviceInfoUtils.getDevice());
            segmentation.put("version", DeviceInfoUtils.getVersionString());
            TrackHelper.track().screen("First Boot").variable(0, "Device", DeviceInfoUtils.getDevice()).variable(1, "Version", DeviceInfoUtils.getVersionString()).with(app.getTracker());
            PreferenceUtils.setPreference(context, PreferenceUtils.PROPERTY_FIRST_BOOT, true);
        }
    }
}
