/*
 * Copyright 2014 ParanoidAndroid Project
 *
 * This file is part of CypherOS OTA.
 *
 * CypherOS OTA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CypherOS OTA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CypherOS OTA.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.cypher.cota.helpers;

import android.Manifest;
import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.net.Uri;
import android.os.Handler;
import android.support.v4.content.ContextCompat;

import com.cypher.cota.R;
import com.cypher.cota.utils.FileUtils;
import com.cypher.cota.utils.PreferenceUtils;

import java.io.File;

public class DownloadHelper {

    private static Context sContext;
    private static Handler sUpdateHandler = new Handler();

    private static DownloadManager sDownloadManager;
    private static DownloadCallback sCallback;

    private static boolean sDownloadingRom = false;
    private static Runnable sUpdateProgress = new Runnable() {

        public void run() {
            if (!sDownloadingRom) {
                return;
            }

            long idRom = Long.parseLong(PreferenceUtils.getPreference(sContext, PreferenceUtils.DOWNLOAD_ROM_ID, "-1"));

            long[] statusRom = getDownloadProgress(idRom);

            int status = DownloadManager.STATUS_SUCCESSFUL;
            if (statusRom[0] == DownloadManager.STATUS_FAILED) {
                status = DownloadManager.STATUS_FAILED;
            } else if (statusRom[0] == DownloadManager.STATUS_PENDING) {
                status = DownloadManager.STATUS_PENDING;
            }

            switch (status) {
                case DownloadManager.STATUS_PENDING:
                    sCallback.onDownloadProgress(-1);
                    break;
                case DownloadManager.STATUS_FAILED:
                    int error = (int) statusRom[3];
                    sCallback.onDownloadError(error == -1 ? null : sContext.getResources()
                            .getString(error));
                    break;
                default:
                    long totalBytes = statusRom[1];
                    long downloadedBytes = statusRom[2];
                    long percent = totalBytes == -1 && downloadedBytes == -1 ? -1 : downloadedBytes
                            * 100 / totalBytes;
                    if (totalBytes != -1 && downloadedBytes != -1 && percent != -1) {
                        sCallback.onDownloadProgress((int) percent);
                    }
                    break;
            }

            if (status != DownloadManager.STATUS_FAILED) {
                sUpdateHandler.postDelayed(this, 1000);
            }
        }
    };

    public static void init(Context context, DownloadCallback callback) {
        sContext = context;
        if (sDownloadManager == null) {
            sDownloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        }
        registerCallback(callback);
        checkIfDownloading();
    }

    public static void registerCallback(DownloadCallback callback) {
        sCallback = callback;
        sUpdateHandler.post(sUpdateProgress);
    }

    private static void readdCallback() {
        sUpdateHandler.post(sUpdateProgress);
    }

    public static void unregisterCallback() {
        sUpdateHandler.removeCallbacks(sUpdateProgress);
    }

    public static void checkDownloadFinished(Context context, long downloadId) {
        sContext = context;
        if (sDownloadManager == null) {
            sDownloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        }
        checkDownloadFinished(downloadId, true);
    }

    public static void clearDownloads() {
        long id = Long.parseLong(PreferenceUtils.getPreference(sContext, PreferenceUtils.DOWNLOAD_ROM_ID, "-1"));
        checkDownloadFinished(id, false);
    }

    private static void checkDownloadFinished(long downloadId, boolean installIfFinished) {
        long id = Long.parseLong(PreferenceUtils.getPreference(sContext, PreferenceUtils.DOWNLOAD_ROM_ID, "-1"));
        if (id == -1L || (downloadId != 0 && downloadId != id)) {
            return;
        }
        String md5 = PreferenceUtils.getPreference(sContext, PreferenceUtils.DOWNLOAD_ROM_MD5, null);
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);
        Cursor cursor = sDownloadManager.query(query);
        if (cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = cursor.getInt(columnIndex);
            switch (status) {
                case DownloadManager.STATUS_FAILED:
                    removeDownload(id, true);
                    int reasonText = getDownloadError(cursor);
                    sCallback.onDownloadError(sContext.getResources().getString(reasonText));
                    break;
                case DownloadManager.STATUS_SUCCESSFUL:
                    if (installIfFinished) {
                        String uriString = cursor.getString(cursor
                                .getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                        sCallback.onDownloadFinished(Uri.parse(uriString), md5);
                    }
                    downloadSuccesful();
                    break;
                default:
                    cancelDownload(id);
                    break;
            }
        } else {
            removeDownload(id, true);
        }
        cursor.close();
    }

    public static boolean isDownloading() {
        return sDownloadingRom;
    }

    public static boolean isDownloading(String fileName) {
        if (sDownloadingRom) {
            String downloadName = PreferenceUtils.getPreference(sContext, PreferenceUtils.DOWNLOAD_ROM_FILENAME, null);
            return fileName.equals(downloadName);
        }
        return false;
    }

    public static void downloadFile(final String url, final String fileName, final String md5) {
        sUpdateHandler.post(sUpdateProgress);

        File romFile = File(fileName);

        if (romFile.exists() && FileUtils.md5(fileName) == md5) {
            downloadSuccesful();

            return;
        }

        sCallback.onDownloadStarted();
        Request request = new Request(Uri.parse(url));
        request.setNotificationVisibility(Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setVisibleInDownloadsUi(false);
        request.setTitle(fileName);

        File file = new File(FileUtils.DOWNLOAD_PATH);

        if (!file.exists()) {
            file.mkdirs();
        }

        request.setDestinationUri(Uri.fromFile(new File(FileUtils.DOWNLOAD_PATH, fileName)));

        if (ContextCompat.checkSelfPermission(sContext, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            long id = sDownloadManager.enqueue(request);
            sDownloadingRom = true;
            PreferenceUtils.setDownloadRomId(sContext, id, md5, fileName);
        }
    }

    private static void removeDownload(long id, boolean removeDownload) {
        sDownloadingRom = false;
        PreferenceUtils.setDownloadRomId(sContext, null, null, null);
        if (removeDownload) {
            sDownloadManager.remove(id);
        }
        sUpdateHandler.removeCallbacks(sUpdateProgress);
        sCallback.onDownloadFinished(null, null);
    }

    private static void downloadSuccesful() {
        sDownloadingRom = false;
        PreferenceUtils.setDownloadRomId(sContext, null, null, null);
        sUpdateHandler.removeCallbacks(sUpdateProgress);
    }

    private static void cancelDownload(final long id) {
        removeDownload(id, true);
    }

    private static long[] getDownloadProgress(long id) {
        DownloadManager.Query q = new DownloadManager.Query();
        q.setFilterById(id);

        Cursor cursor = sDownloadManager.query(q);
        int status;

        if (cursor == null || !cursor.moveToFirst()) {
            status = DownloadManager.STATUS_FAILED;
        } else {
            status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
        }

        long error = -1;
        long totalBytes = -1;
        long downloadedBytes = -1;

        switch (status) {
            case DownloadManager.STATUS_PAUSED:
            case DownloadManager.STATUS_RUNNING:
                downloadedBytes = cursor.getLong(cursor
                        .getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                totalBytes = cursor.getLong(cursor
                        .getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                break;
            case DownloadManager.STATUS_FAILED:
                sDownloadingRom = false;
                error = getDownloadError(cursor);
                break;
        }

        if (cursor != null) {
            cursor.close();
        }

        return new long[]{
                status, totalBytes, downloadedBytes, error
        };
    }

    private static void checkIfDownloading() {
        long romId = Long.parseLong(PreferenceUtils.getPreference(sContext, PreferenceUtils.DOWNLOAD_ROM_ID, "-1"));
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(romId);
        Cursor cursor = sDownloadManager.query(query);
        sDownloadingRom = cursor.moveToFirst();
        cursor.close();
        if (romId >= 0L && !sDownloadingRom) {
            removeDownload(romId, false);
        }
    }

    private static int getDownloadError(Cursor cursor) {
        int columnReason = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
        int reasonText;
        try {
            int reason = cursor.getInt(columnReason);
            switch (reason) {
                case DownloadManager.ERROR_CANNOT_RESUME:
                    reasonText = R.string.error_cannot_resume;
                    break;
                case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                    reasonText = R.string.error_device_not_found;
                    break;
                case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                    reasonText = R.string.error_file_already_exists;
                    break;
                case DownloadManager.ERROR_FILE_ERROR:
                    reasonText = R.string.error_file_error;
                    break;
                case DownloadManager.ERROR_HTTP_DATA_ERROR:
                    reasonText = R.string.error_http_data_error;
                    break;
                case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                    reasonText = R.string.error_insufficient_space;
                    break;
                case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                    reasonText = R.string.error_too_many_redirects;
                    break;
                case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                    reasonText = R.string.error_unhandled_http_code;
                    break;
                case DownloadManager.ERROR_UNKNOWN:
                default:
                    reasonText = R.string.error_unknown;
                    break;
            }
        } catch (CursorIndexOutOfBoundsException ex) {
            // don't crash, just report it
            reasonText = R.string.error_unknown;
        }
        return reasonText;
    }

    public interface DownloadCallback {

        void onDownloadStarted();

        void onDownloadProgress(int progress);

        void onDownloadFinished(Uri uri, String md5);

        void onDownloadError(String reason);
    }
}
