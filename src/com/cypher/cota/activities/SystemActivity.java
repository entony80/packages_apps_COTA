package com.cypher.cota.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.format.Formatter;
import android.view.View;
import android.widget.TextView;
import android.util.Log;

import com.cypher.cota.R;
import com.cypher.cota.helpers.DownloadHelper;
import com.cypher.cota.helpers.RebootHelper;
import com.cypher.cota.helpers.RecoveryHelper;
import com.cypher.cota.receivers.DownloadReceiver;
import com.cypher.cota.updater.RomUpdater;
import com.cypher.cota.updater.Updater.PackageInfo;
import com.cypher.cota.updater.Updater.UpdaterListener;
import com.cypher.cota.utils.DeviceInfoUtils;
import com.cypher.cota.utils.FileUtils;
import com.cypher.cota.utils.NotificationUtils;
import com.cypher.cota.utils.PreferenceUtils;

import org.piwik.sdk.DownloadTracker;
import org.piwik.sdk.PiwikApplication;
import org.piwik.sdk.TrackHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SystemActivity extends AppCompatActivity implements FloatingActionButton.OnClickListener,
        UpdaterListener, DownloadHelper.DownloadCallback {
    private static final String TAG = "COTA:SystemActivity";

    private int mState;
    private static final int STATE_CHECK = 0;
    private static final int STATE_FOUND = 1;
    private static final int STATE_DOWNLOADING = 2;
    private static final int STATE_INSTALL = 3;
    private static final int STATE_ERROR = 4;

    private RomUpdater mRomUpdater;
    private RebootHelper mRebootHelper;

    private PackageInfo mRom;
    private List<File> mFiles = new ArrayList<>();

    private NotificationUtils.NotificationInfo mNotificationInfo;

    private CoordinatorLayout mCoordinatorLayout;
    private TextView mMessage;
    private FloatingActionButton mButton;
    private TextView mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system);

        mToolbar = (TextView) findViewById(R.id.toolbar);
        mCoordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinatorLayout);
        mMessage = (TextView) findViewById(R.id.message);
        mButton = (FloatingActionButton) findViewById(R.id.fab);

        mRom = null;
        DownloadHelper.init(this, this);
        mRomUpdater = new RomUpdater(this, true);
        mRebootHelper = new RebootHelper(new RecoveryHelper(SystemActivity.this));

        mButton.setOnClickListener(this);
        mRomUpdater.addUpdaterListener(this);

        // Check for M permission to write on external
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }

        if (mNotificationInfo != null) {
            if (mNotificationInfo.mNotificationId == NotificationUtils.NOTIFICATION_ID) {
                mRomUpdater.setLastUpdates(mNotificationInfo.mPackageInfosRom);
            } else {
                mRomUpdater.check(true);
            }
        } else if (DownloadHelper.isDownloading()) {
            mState = STATE_DOWNLOADING;
            updateMessages((PackageInfo) null);
        } else {
            mRomUpdater.check(true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        mNotificationInfo = null;
        if (intent != null && intent.getExtras() != null) {
            mNotificationInfo = (NotificationUtils.NotificationInfo) intent.getSerializableExtra(NotificationUtils.FILES_INFO);
            if (intent.getBooleanExtra(DownloadReceiver.CHECK_DOWNLOADS_FINISHED, false)) {
                DownloadHelper.checkDownloadFinished(this,
                        intent.getLongExtra(DownloadReceiver.CHECK_DOWNLOADS_ID, -1L));
            }
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onResume() {
        super.onResume();
        DownloadHelper.registerCallback(this);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onPause() {
        super.onPause();
        DownloadHelper.unregisterCallback();
    }

    @Override
    public void startChecking() {
        mState = STATE_CHECK;
        updateMessages(mRom);
    }

    @Override
    public void versionFound(PackageInfo[] info) {
		//An update has been found
        mState = STATE_FOUND;
		if (info != null && info.length > 0) {
            if(FileUtils.isOnDownloadList(this, info[0].getFilename())) {
                //Now that the package is download, lets queue the install
                mState = STATE_INSTALL;
                addFile(FileUtils.getFile(this, info[0].getFilename()), info[0].getMd5());
            }
        }
        updateMessages(info);
    }

    private void updateMessages(PackageInfo[] info) {
        if (info != null && info.length > 0) {
            updateMessages(info.length > 0 ? info[0] : null);
        }
    }

    private void updateMessages(PackageInfo info) {
        mRom = info;
        switch (mState) {
            default:
            case STATE_CHECK:
                if (mRom == null) {
                    mToolbar.setText(R.string.no_updates_title);
                    mMessage.setText(R.string.no_updates_text);
                    mButton.setImageResource(R.drawable.ic_check_update);
                    Log.v(TAG, "updateMessages:STATE_CHECK = mRom != null");
                }
                Log.v(TAG, "updateMessages:STATE_CHECK = mRom == null");
                break;
            case STATE_FOUND:
                if (!mRomUpdater.isScanning() && mRom != null) {
                    mToolbar.setText(R.string.update_found_title);
                    String ShortFileSize = getResources().getString(R.string.update_found_text,
                        new Object[]{
                              Formatter.formatShortFileSize(this, Long.decode(mRom.getSize()))
                        });
                    mMessage.setText(ShortFileSize);
                    mButton.setImageResource(R.drawable.ic_download_update);
                    Log.v(TAG, "updateMessages:STATE_FOUND = " + ShortFileSize);
                }
                Log.v(TAG, "updateMessages:STATE_FOUND = mRomUpdater.isScanning || mRom == null");
                break;
            case STATE_DOWNLOADING:
                mToolbar.setText(R.string.downloading_title);
                mMessage.setText(String.format(getString(R.string.downloading_text), "0%"));
                mButton.setImageResource(R.drawable.ic_cancel_download);
                Log.v(TAG, "updateMessages:STATE_DOWNLOADING = " + String.format(getString(R.string.downloading_text), "0%"));
                break;
            case STATE_ERROR:
                mToolbar.setText(R.string.download_failed_title);
                mMessage.setText(R.string.download_failed_text);
                mButton.setImageResource(R.drawable.ic_check_update);
                Log.v(TAG, "updateMessages:STATE_ERROR");
                break;
            case STATE_INSTALL:
                mToolbar.setText(R.string.install_title);
                mMessage.setText(R.string.install_text);
                mButton.setImageResource(R.drawable.ic_install_update);
                Log.v(TAG, "updateMessages:STATE_INSTALL");
                break;
        }
    }

    @Override
    public void onClick(View v) {
        switch (mState) {
            default:
            case STATE_CHECK:
                mState = STATE_CHECK;
                mRomUpdater.check(true);
                Log.v(TAG, "onClick:STATE_CHECK");
                break;
            case STATE_FOUND:
                if (!mRomUpdater.isScanning() && mRom != null) {
                    mState = STATE_DOWNLOADING;
                    DownloadHelper.registerCallback(SystemActivity.this);
                    DownloadHelper.downloadFile(mRom.getPath(),
                            mRom.getFilename(), mRom.getMd5());
                    updateMessages(mRom);
                    TrackHelper.track().download().version(mRom.getFilename()).with(((PiwikApplication) getApplication()).getTracker());
                    Log.v(TAG, "onClick:STATE_FOUND = " + DeviceInfoUtils.getDevice() + ":" + mRom.getFilename());
                }
                Log.v(TAG, "onClick:STATE_FOUND = mRomUpdater.isScanning || mRom == null");
                break;
            case STATE_DOWNLOADING:
                mState = STATE_CHECK;
                DownloadHelper.clearDownloads();
                updateMessages((PackageInfo) null);
                Log.v(TAG, "onClick:STATE_DOWNLOADING");
                break;
            case STATE_ERROR:
                mState = STATE_CHECK;
                mRomUpdater.check(true);
                Log.v(TAG, "onClick:STATE_ERROR");
                break;
            case STATE_INSTALL:
                String[] items = new String[mFiles.size()];
                for (int i = 0; i < mFiles.size(); i++) {
                    File file = mFiles.get(i);
                    items[i] = file.getAbsolutePath();
                }
                mRebootHelper.showRebootDialog(SystemActivity.this, items);
                Log.v(TAG, "onClick:STATE_INSTALL = " + android.text.TextUtils.join(", ", items));
                break;
        }
    }

    @Override
    public void onDownloadStarted() {
        mState = STATE_DOWNLOADING;
        onDownloadProgress(-1);
    }

    @Override
    public void onDownloadError(String reason) {
        mState = STATE_ERROR;
        updateMessages((PackageInfo) null);
    }

    @Override
    public void onDownloadProgress(int progress) {
        if (progress >= 0 && progress <= 100) {
            mMessage.setText(String.format(getString(R.string.downloading_text), progress + "%"));
        }
    }

    @Override
    public void onDownloadFinished(Uri uri, final String md5) {
        if (uri != null) {
            mState = STATE_INSTALL;
            updateMessages((PackageInfo) null);
            addFile(uri, md5);
        } else {
            mState = STATE_CHECK;
            mRomUpdater.check(true);
        }
    }

    public void addFile(Uri uri, final String md5) {
        String filePath = uri.toString().replace("file://", "");
        File file = new File(filePath);
        addFile(file, md5);
    }

    private void addFile(final File file, final String md5) {
        if (md5 != null && !"".equals(md5)) {
            final Snackbar md5Snackbar = Snackbar.make(mCoordinatorLayout, R.string.calculating_md5, Snackbar.LENGTH_INDEFINITE);
            md5Snackbar.show();
			mButton.hide();
            new Thread() {
                public void run() {
                    final String calculatedMd5 = FileUtils.md5(file);
                    md5Snackbar.dismiss();
                    runOnUiThread(new Runnable() {

                        public void run() {
                            if (md5.equals(calculatedMd5)) {
                                reallyAddFile(file);
                            }
                            else {
                                showMd5Mismatch(file);
                            }
                        }
                    });
                }
            }.start();

        } else {
            reallyAddFile(file);
        }
    }

    private void reallyAddFile(final File file) {
		mButton.show();
        mFiles.add(file);
    }

    private void showMd5Mismatch(final File file) {
        Snackbar.make(mCoordinatorLayout, R.string.md5_mismatch, Snackbar.LENGTH_LONG)
                .setAction(R.string.md5_install_anyway, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        reallyAddFile(file);
                    }
                })
                .show();
    }

    @Override
    public void checkError(String cause) {
    }
}
