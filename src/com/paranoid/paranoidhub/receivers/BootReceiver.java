package com.cypher.cota.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.cypher.cota.utils.AlarmUtils;
import com.cypher.cota.utils.DeviceInfoUtils;
import com.cypher.cota.utils.PreferenceUtils;

import java.util.HashMap;

import ly.count.android.sdk.Countly;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmUtils.setAlarm(context, true);

        if (!PreferenceUtils.getPreference(context, PreferenceUtils.PROPERTY_FIRST_BOOT, false)) {
            HashMap<String, String> segmentation = new HashMap<>();
            segmentation.put("device", DeviceInfoUtils.getDevice());
            segmentation.put("version", DeviceInfoUtils.getVersionString());
            Countly.sharedInstance().recordEvent("firstBoot", segmentation, 1);
            PreferenceUtils.setPreference(context, PreferenceUtils.PROPERTY_FIRST_BOOT, true);
        }
    }
}
