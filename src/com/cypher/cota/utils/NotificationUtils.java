package com.cypher.cota.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationCompat;

import com.cypher.cota.R;
import com.cypher.cota.activities.SystemActivity;
import com.cypher.cota.updater.Updater;

import java.io.Serializable;

public class NotificationUtils {
    public static final String FILES_INFO = "com.cypher.cota.Utils.FILES_INFO";
    public static final int NOTIFICATION_ID = 122303235;
    private static Updater.PackageInfo[] sPackageInfosRom = new Updater.PackageInfo[0];

    public static void showNotification(Context context, Updater.PackageInfo[] infosRom) {
        Resources resources = context.getResources();

        if (infosRom != null) {
            sPackageInfosRom = infosRom;
        } else {
            infosRom = sPackageInfosRom;
        }

        Intent intent = new Intent(context, SystemActivity.class);
        NotificationInfo fileInfo = new NotificationInfo();
        fileInfo.mNotificationId = NOTIFICATION_ID;
        fileInfo.mPackageInfosRom = infosRom;
        intent.putExtra(FILES_INFO, fileInfo);
        PendingIntent pIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setContentTitle(resources.getString(R.string.update_found_title))
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_launcher_mono)
                .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_launcher))
                .setContentIntent(pIntent)
                .setOngoing(true)
				.setAutoCancel(true)
                .addAction(R.drawable.ic_download, "Download", pIntent);

        builder.setContentText(resources.getString(R.string.update_label) + " "
                + infosRom[0].getVersion().toString());

        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    public static class NotificationInfo implements Serializable {
        public int mNotificationId;
        public Updater.PackageInfo[] mPackageInfosRom;
    }
}
