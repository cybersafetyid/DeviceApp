package com.net2software.busvalidator.updater;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.net2software.busvalidator.updater.autoInstaller.AutoInstaller;
import com.net2software.busvalidator.updater.helper.Network;
import com.net2software.filelog.FileLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;

public class Updater extends Handler {
    private static final String TAG = "AutoUpdater";
    private String apkUrl;
    private Context context;
    private ProgressDialog mProgressDialog;
    private AutoInstaller autoInstaller;
    private int response;
    private int versionCode;
    private String statusFare;
    private String versionName;
    private String[] versionNotes;
    private String sVersionNotes;
    private String fileApkPath;
    private boolean isMajorVersionChanged;
    private String lastErrorMessage;
    private Updater.OnStateChangedListener mOnStateChangedListener;

    public Updater(Context context2) {
        this.context = context2;
        autoInstaller = AutoInstaller.getDefault(this.context);
        this.mProgressDialog = new ProgressDialog(context2);
        this.mProgressDialog.setCancelable(false);
        this.fileApkPath = null;
        this.isMajorVersionChanged = false;
    }

    public void setOnStateChangedListener(Updater.OnStateChangedListener onStateChangedListener) {
        mOnStateChangedListener = onStateChangedListener;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case 0:
                if (mOnStateChangedListener != null)
                    mOnStateChangedListener.onSuccess(fileApkPath);
                break;

            case 1:
                if (mOnStateChangedListener != null)
                    mOnStateChangedListener.onFailed();
                break;

            case 3:
                if (mOnStateChangedListener != null)
                    mOnStateChangedListener.onNoNewVersion();
                break;

            case 4:
                if (mOnStateChangedListener != null)
                    mOnStateChangedListener.onLoading();
                break;
        }
    }

    public void checkAppUpdate(String str, String oldVersionName, int oldVersionCode) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                check(str, oldVersionName, oldVersionCode);
            }
        }).start();
    }

    private void check(String str, String oldVersionName, int oldVersionCode) {

        new Network(this.context).get(str, new Network.ResponseCallback() {
            /* class com.net2software.mobile.updater.Updater.AnonymousClass1 */

            @Override // com.net2software.mobile.updater.helper.Network.ResponseCallback
            public void onSuccess(String str) {
                try {
                    writeLog(TAG, str);
                    JSONObject jSONObject = new JSONObject(str);
                    boolean status = jSONObject.getBoolean("status");
                    if (status) {
                        JSONObject jSONObject2 = jSONObject.getJSONObject("data");
                        Updater.this.versionCode = Integer.parseInt(jSONObject2.getString("versionCode"));
                        if (jSONObject2.has("status_fare")) {
                            Updater.this.statusFare = jSONObject2.getString("status_fare");
                        } else {
                            Updater.this.statusFare = "f";
                        }
                        if (Updater.this.versionCode > oldVersionCode || Updater.this.versionCode < oldVersionCode) {
                            sendEmptyMessage(4);
                            Updater.this.apkUrl = jSONObject2.getString("apkUrl");
                            Updater.this.versionName = jSONObject2.getString("versionName");
                            JSONArray optJSONArray = jSONObject2.optJSONArray("versionNotes");
                            Updater.this.versionNotes = new String[optJSONArray.length()];
                            for (int i = 0; i < optJSONArray.length(); i++) {
                                Updater.this.versionNotes[i] = optJSONArray.getString(i);
                            }
                            sVersionNotes = Arrays.toString(versionNotes)
                                    .replaceAll("\\[|\\]", "")
                                    .replace(", ", "\n");
                            isMajorVersionChanged = isMajorVersionChanged(oldVersionName, Updater.this.versionName);
                            writeLog(TAG, "Sukses cek versi app");
                            Updater.this.doCheck();
                        } else {
                            writeLog(TAG, "Belum ada versi terbaru");
                            sendEmptyMessage(3);
                        }
                        /*else if (jSONObject2.getInt("versionCode") == i) {
                            writeLog();(TAG, "Belum ada versi terbaru");
                            sendEmptyMessage(3);
                        }*/
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendEmptyMessage(1);
                    writeLog(TAG, e.getMessage());
                    Updater.this.mProgressDialog.dismiss();

                }
            }

            @Override // com.net2software.mobile.updater.helper.Network.ResponseCallback
            public void onFailed(String str) {
                lastErrorMessage = str;
                sendEmptyMessage(1);
                Updater.this.mProgressDialog.dismiss();
            }
        });
    }

    private void doCheck() {

        if (this.apkUrl == null || this.apkUrl.equals("null")) {
            sendEmptyMessage(1);
            return;
        }

        autoInstaller.installFromUrl(this.apkUrl);
        autoInstaller.setOnStateChangedListener(new AutoInstaller.OnStateChangedListener() {
            /* class com.net2software.mobile.updater.Updater.AnonymousClass2 */

            @SuppressLint("HandlerLeak")
            final Handler handle = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    mProgressDialog.setProgress(autoInstaller.getProgressBar());
                }
            };

            @Override
            // com.net2software.mobile.updater.autoInstaller.AutoInstaller.OnStateChangedListener
            public void onStart() {
                Updater.this.mProgressDialog.setMax(100);
                Updater.this.mProgressDialog.setTitle("Tap On Bus v" + versionName);
                Updater.this.mProgressDialog.setMessage("Changelog : \n" + sVersionNotes);
                Updater.this.mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                Updater.this.mProgressDialog.show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            while (autoInstaller.getProgressBar() <= mProgressDialog
                                    .getMax()) {
                                Thread.sleep(1000);
                                handle.sendMessage(handle.obtainMessage());
                                if (autoInstaller.getProgressBar() == mProgressDialog
                                        .getMax()) {
                                    mProgressDialog.dismiss();
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }

            @Override
            // com.net2software.mobile.updater.autoInstaller.AutoInstaller.OnStateChangedListener
            public void onStop() {
                sendEmptyMessage(1);
                Toast.makeText(Updater.this.context, "Gagal update aplikasi", Toast.LENGTH_SHORT).show();
                Updater.this.mProgressDialog.dismiss();
            }

            @Override
            // com.net2software.mobile.updater.autoInstaller.AutoInstaller.OnStateChangedListener
            public void onComplete(String fileApkPath) {
                writeLog(TAG, "onComplete");
                Updater.this.fileApkPath = fileApkPath;
                sendEmptyMessage(0);
                Updater.this.mProgressDialog.dismiss();
            }

            @Override
            // com.net2software.mobile.updater.autoInstaller.AutoInstaller.OnStateChangedListener
            public void onNeed2OpenService() {
                sendEmptyMessage(4);
                Toast.makeText(Updater.this.context, "Please turn on accessibility services", Toast.LENGTH_SHORT).show();
            }

            @Override
            // com.net2software.mobile.updater.autoInstaller.AutoInstaller.OnStateChangedListener
            public void onFailed() {
                sendEmptyMessage(1);
                Toast.makeText(Updater.this.context, "Failed install apk", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public int getResponse() {
        Log.d(TAG, "Response: " + response);
        return this.response;
    }

    public int setResponse(int i) {
        Log.d(TAG, "Response: " + response);
        return this.response = i;
    }

    public int getVersionCode() {
        Log.d(TAG, "Version: " + versionCode);
        return this.versionCode;
    }

    public String getVersionName() {
        return this.versionName;
    }

    public String[] getVersionNotes() {
        return this.versionNotes;
    }

    public String getStatusFare() {
        return this.statusFare;
    }

    public boolean isMajorVersionChanged(String oldVersion, String newVersion) {
        try {
            String[] oldVersionParts = oldVersion.split("\\.");
            String[] newVersionParts = newVersion.split("\\.");

            int oldMajorVersion = Integer.parseInt(oldVersionParts[0]);
            int newMajorVersion = Integer.parseInt(newVersionParts[0]);

            return oldMajorVersion != newMajorVersion;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void writeLog(String str, String str2) {
        if (str2 == null) {
            str2 = "NULL";
        }
        FileLog.d("NETLibs|" + str, str2);
    }

    public interface OnStateChangedListener {
        void onSuccess(String fileApkPath);

        void onFailed();

        void onNoNewVersion();

        void onLoading();
    }
}
