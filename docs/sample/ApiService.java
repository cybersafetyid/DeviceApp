package com.net2software.busvalidator.biskita.data.remote;

import static com.net2software.busvalidator.biskita.App.BARCODE;
import static com.net2software.busvalidator.biskita.App.LAST_QR;
import static com.net2software.busvalidator.biskita.App.localService;
import static com.net2software.busvalidator.biskita.App.utility;
import static com.net2software.busvalidator.biskita.module.BaseActivity.locationTrack;
import static com.net2software.busvalidator.biskita.tools.AppUtils.getTime;
import static com.net2software.busvalidator.biskita.tools.AppUtils.writeLog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.google.gson.Gson;
import com.net2software.biskita.module.sdk.SDK;
import com.net2software.busvalidator.biskita.App;
import com.net2software.busvalidator.biskita.config.Config;
import com.net2software.busvalidator.biskita.config.Data;
import com.net2software.busvalidator.biskita.data.local.LocalService;
import com.net2software.busvalidator.biskita.data.local.controller.DataTapOutController;
import com.net2software.busvalidator.biskita.data.local.controller.LocationController;
import com.net2software.busvalidator.biskita.data.local.controller.QueryPaymentController;
import com.net2software.busvalidator.biskita.data.local.controller.TransactionController;
import com.net2software.busvalidator.biskita.data.local.controller.TransactionFailController;
import com.net2software.busvalidator.biskita.data.local.controller.TransactionQrisController;
import com.net2software.busvalidator.biskita.model.BaseDataModel;
import com.net2software.busvalidator.biskita.model.DataTapOutModel;
import com.net2software.busvalidator.biskita.model.LocationModel;
import com.net2software.busvalidator.biskita.model.LocationModelRequest;
import com.net2software.busvalidator.biskita.model.PaymentModel;
import com.net2software.busvalidator.biskita.model.PromoModel;
import com.net2software.busvalidator.biskita.model.QueryPaymentModel;
import com.net2software.busvalidator.biskita.model.SaveDataLogModel;
import com.net2software.busvalidator.biskita.model.TransactionFailModel;
import com.net2software.busvalidator.biskita.model.TransactionModel;
import com.net2software.busvalidator.biskita.model.TransactionQrisModel;
import com.net2software.busvalidator.biskita.module.BaseActivity;
import com.net2software.busvalidator.biskita.module.MainActivity;
import com.net2software.busvalidator.biskita.module.initialize.SplashScreen;
import com.net2software.busvalidator.biskita.services.LocationTrack;
import com.net2software.busvalidator.biskita.services.MqttHelper;
import com.net2software.busvalidator.biskita.tools.AppUtils;
import com.net2software.busvalidator.biskita.tools.Constant;
import com.net2software.busvalidator.biskita.tools.ReaderUtils;
import com.net2software.busvalidator.biskita.tools.SignalStrengthHelper;
import com.net2software.busvalidator.biskita.tools.Utils;
import com.net2software.filelog.FileLog;
import com.net2software.mobile.netlibs.config.database.Controllers.ControllerConfigBNI;
import com.net2software.mobile.netlibs.config.models.config.ConfigBNIModel;
import com.net2software.mobile.netlibs.core.chipbase.card.BNI.TapCashModel;
import com.net2software.mobile.netlibs.utils.Convert;
import com.net2software.mobile.netlibs.utils.Utility;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiService extends Handler {
    private final static String TAG = ApiService.class.getSimpleName();
    private final Context context;
    //private LocalService localService;
    private final Data data;
    private final Gson gson;
    //private final Network network;
    private final Network network;
    private final NetworkRetrofit networkRetrofit;
    private final LocationController locationController;
    private final ArrayList<LocationModelRequest> allLocationDataAPI = new ArrayList<>();
    private final TransactionFailController transactionFailController;
    private final TransactionController transactionController;
    private final DataTapOutController dataTapOutController;
    public int i_cam1in;
    public int i_cam1on;
    public int i_cam1out;
    public boolean isCanChangetrip = false;
    public Location targetLocation;
    protected App mMyApp;
    SDK sdkInstance = App.sdk;
    ApiService apiServiceInstance = App.apiService;
    LocalService localServiceInstance = App.localService;
    private boolean isTaskDone;
    private String tempFileName;
    private StringBuilder stringBuilder;
    private BaseDataModel result;
    private String message;
    private boolean isReadyToSendLog = true;
    private boolean isReadyToSendTrxFailed = true;
    private ApiService.OnStateChangedListener mOnStateChangedListener;

    public ApiService(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.network = new Network(ctx);
        this.mMyApp = (App) ctx.getApplicationContext();
        this.networkRetrofit = new NetworkRetrofit();
        this.data = App.data;
        this.gson = new Gson();
        this.stringBuilder = new StringBuilder();

        this.locationController = new LocationController(context);
        this.transactionFailController = new TransactionFailController(context);
        this.transactionController = new TransactionController(context);
        this.dataTapOutController = new DataTapOutController(context);
        this.data.saveBoolean(Data.Key.SENDING_PROCESS, false);
    }

    @NonNull
    private static Parameter getParameterTrxFailed(TransactionFailModel transactionFailModel) {
        Parameter parameter = new Parameter();
        parameter.Add("payment_method", transactionFailModel.getPayment_method());
        parameter.Add("tid", transactionFailModel.getTid());
        parameter.Add("reff_no", transactionFailModel.getReff_no());
        parameter.Add("error_code", transactionFailModel.getError_code());
        parameter.Add("date_trx", transactionFailModel.getDate_trx());
        parameter.Add("amount", transactionFailModel.getAmount());
        parameter.Add("route", transactionFailModel.getRoute());
        parameter.Add("bus_code", transactionFailModel.getBus_code());
        parameter.Add("operational_code", transactionFailModel.getOps_code());
        parameter.Add("trip", transactionFailModel.getTrip());
        parameter.Add("hwid", transactionFailModel.getHwid());
        parameter.Add("trx_code", transactionFailModel.getTrx_code());
        parameter.Add("trx_id", transactionFailModel.getTrx_id());
        parameter.Add("account_number", transactionFailModel.getAccount_number());
        parameter.Add("long", transactionFailModel.getLongg());
        parameter.Add("latt", transactionFailModel.getLatt());
        parameter.Add("additional", transactionFailModel.getAdditional());

        return parameter;
    }

    @NonNull
    private static Parameter getParameterTrxSuccess(TransactionModel transactionModel) {
        Parameter parameter = new Parameter();
        parameter.Add("hwid", transactionModel.getHwid());
        parameter.Add("operational", transactionModel.getOperationalCode());
        parameter.Add("route", transactionModel.getRouteName());
        parameter.Add("trip", transactionModel.getTrip());
        parameter.Add("mid_partner", transactionModel.getMid());
        parameter.Add("tid_partner", transactionModel.getTid());
        parameter.Add("payment_method", transactionModel.getPaymentMethod());
        parameter.Add("trx_code", transactionModel.getTrxCode());
        parameter.Add("trx_id", transactionModel.getTrxId());
        parameter.Add("trx_date", transactionModel.getTrxDate());
        parameter.Add("sn", transactionModel.getSerialNumber());
        parameter.Add("uid", transactionModel.getUid());
        parameter.Add("balance_before", transactionModel.getBalanceBefore());
        parameter.Add("amount", transactionModel.getAmount());
        parameter.Add("balance_after", transactionModel.getBalanceAfter());
        parameter.Add("lat", transactionModel.getLatitude());
        parameter.Add("long", transactionModel.getLongitude());
        parameter.Add("bank_name", transactionModel.getBankName());
        parameter.Add("tapin", transactionModel.getTapin());
        parameter.Add("card_type", transactionModel.getCardType());
        return parameter;
    }

    @NonNull
    private static HashMap<String, String> getStringHashMap(ArrayList<LocationModelRequest> locationModel) {
        HashMap<String, String> params = new HashMap<>();
        int listSize = locationModel.size();

        for (int i = 0; i < listSize; i++) {
            params.put("data[" + i + "][hwid]", locationModel.get(i).getHwid());
            params.put("data[" + i + "][operational]", locationModel.get(i).getOperational());
            params.put("data[" + i + "][route]", locationModel.get(i).getRoute());
            params.put("data[" + i + "][trip]", locationModel.get(i).getTrip());
            params.put("data[" + i + "][in]", locationModel.get(i).getIn());
            params.put("data[" + i + "][out]", locationModel.get(i).getOut());
            params.put("data[" + i + "][on]", locationModel.get(i).getOn());
            params.put("data[" + i + "][lat]", locationModel.get(i).getLatitude());
            params.put("data[" + i + "][long]", locationModel.get(i).getLongitude());
            params.put("data[" + i + "][date]", locationModel.get(i).getDate());
            params.put("data[" + i + "][tid]", locationModel.get(i).getTid());
            params.put("data[" + i + "][bus]", locationModel.get(i).getBus());
        }
        return params;
    }

    public void setOnStateChangedListener(ApiService.OnStateChangedListener onStateChangedListener) {
        mOnStateChangedListener = onStateChangedListener;
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case 0:
                if (mOnStateChangedListener != null)
                    mOnStateChangedListener.onSuccess(result);
                break;

            case 1:
                if (mOnStateChangedListener != null)
                    mOnStateChangedListener.onFailed(result);
                break;

            case 2:
                if (mOnStateChangedListener != null)
                    mOnStateChangedListener.onLoading(message);
                break;
            case 3:
                if (mOnStateChangedListener != null)
                    mOnStateChangedListener.onRequest(result.getMessage());
                break;
        }
    }

    private void setResultAPI(int callback, int code, String msg, Object data) {
        result = new BaseDataModel();
        result.setCode(code);
        result.setMessage(msg);
        result.setData(data);

        sendEmptyMessage(callback);
    }

    private void setMsgLoading(String input) {
        message = input;
        sendEmptyMessage(2);
    }

    public void sendMCBNI() {
        try {
            ControllerConfigBNI configBNI = new ControllerConfigBNI(context);
            String samID = Convert.fromBytes.toHexString(TapCashModel.samId);

            ArrayList<ConfigBNIModel> config = configBNI.getAllData();
            if (config == null || config.isEmpty()) {
                Log.d("CONFIG_BNI", "Tidak ada");
            } else {
                for (ConfigBNIModel configBNIModel : configBNI.getAllData()) {
                    if (samID.equals(configBNIModel.getId())) {
                        TapCashModel.marriageCode = Convert.fromString.toByteArray(configBNIModel.getCode());
                        Log.d("BNI", "Marriage Code Found");
                        Log.d("BNI", "SAM ID : " + Convert.fromBytes.toHexString(TapCashModel.samId));
                        Log.d("BNI", "MARRIAGE CODE : " + Convert.fromBytes.toHexString(TapCashModel.marriageCode));
                        break;
                    }
                }

                /*Parameter parameter = new Parameter();
                parameter.Add("hwid", getHwid());
                parameter.Add("marriage_code", Convert.fromBytes.toHexString(TapCashModel.marriageCode));
                parameter.Add("sam_id", Convert.fromBytes.toHexString(TapCashModel.samId));

                this.network.PostDirect(parameter, "https://", new Network.ResponseCallback() {

                    @Override
                    public void onSuccess(String str) {
                        try {
                            writeLog("SEND_MC", str);
                            if (new JSONObject(str).getBoolean("status")) {
                                writeLog("STATUS_MC", String.valueOf(new JSONObject(str).getBoolean("status")));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            if (e.getMessage() != null)
                                writeLog("Transaction", e.getMessage());
                        }
                    }

                    @Override
                    public void onFailed(String str) {
                        writeLog("SEND_MC", str);
                    }
                });*/
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Initialization
     */
    public void initBus(AppCompatActivity cActivity) {
        Parameter parameter = new Parameter();
        parameter.Add("hwid", this.data.get(Data.Key.BUS_HWID));
        parameter.Add("bus", this.data.get(Data.Key.BUS_CODE));
        parameter.Add("date", Utility.getDateTimeWithFormat(System.currentTimeMillis(), "yyyy-MM-dd HH:mm"));

        setMsgLoading("Initialize");

        this.network.Post(parameter, "init_terminal", new Network.ResponseCallback() {

            @Override
            public void onSuccess(String str) {
                try {
                    JSONObject jSONObject = new JSONObject(str);
                    if (jSONObject.getBoolean("status")) {
                        Config config = new Config(context);
                        int writeCfg = config.writeConfig(Constant.TERMINAL_NAME_FILE, jSONObject.toString());
                        if (writeCfg == 0) {
                            Log.d("TERMINAL", "Sukses tulis file terminal");
                        } else {
                            Log.d("TERMINAL", "Gagal tulis file terminal");
                        }

                        if (!App.utility.setTerminal(cActivity, jSONObject)) {
                            setResultAPI(1, 9011, "Failed setTerminal", null);
                            return;
                        }

                        /*data.save(Data.Key.BUS_TID, jSONObject.getString(TransactionFailTable.TID));
                        if (jSONObject.getBoolean("statusQris")) {
                            data.save(Data.Key.IS_QRIS, "true");
                            data.save(Data.Key.RAW_QRIS, jSONObject.getString("rawQRIS"));
                        } else {
                            data.save(Data.Key.IS_QRIS, "false");
                            data.save(Data.Key.RAW_QRIS, "");
                        }*/
                        if (App.isCitraRaya()) {
                            getPromo();
                        }

                        getDataLog();

                        getFareNew(cActivity, locationTrack.getLocation());

                        return;
                    }

                    stringBuilder = new StringBuilder();
                    StringBuilder sb = stringBuilder;
                    sb.append("[").append(jSONObject.getString("code")).append("] ");
                    stringBuilder.append(jSONObject.getString(NotificationCompat.CATEGORY_MESSAGE));
                    stringBuilder.append(StringUtils.LF);
                    stringBuilder.append("Hidupkan ulang perangkat atau ");
                    stringBuilder.append("hubungi kantor pusat.");

                    setResultAPI(1, -1, "Inisialisasi bus gagal !", stringBuilder);
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        initBus(cActivity);
                    } catch (Exception e2) {
                        e2.printStackTrace();
                        setResultAPI(1, -1, "", null);
                    }
                } catch (Throwable e) {
                    setResultAPI(1, -1, "", null);
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onFailed(String str) {
                try {
                    if (str.contains("Response Timeout")) {
                        initBus(cActivity);
                    } else {
                        stringBuilder = new StringBuilder();
                        stringBuilder.append("[Network] ");
                        stringBuilder.append(str);
                        stringBuilder.append(StringUtils.LF);
                        stringBuilder.append("Hidupkan ulang perangkat atau ");
                        stringBuilder.append("hubungi kantor pusat.");
                        //App.utility.showDialog(-1, "Inisialisasi bus gagal !", stringBuilder.toString());
                        setResultAPI(1, -1, "Inisialisasi bus gagal !", stringBuilder);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    setResultAPI(1, -1, "", null);
                }
            }
        });
    }

    public void readOnlineConfig(AppCompatActivity cActivity) {
        try {
//            if (sdkInstance.checkUpdateFirmware(context)) {
            if (this.data.get(Data.Key.BUS_HWID) != null)
                writeLog("HWID", this.data.get(Data.Key.BUS_HWID));

            Parameter parameter = new Parameter();
            parameter.Add("hwid", this.data.get(Data.Key.BUS_HWID));

            setMsgLoading("Read File Config");

            this.network.Post(parameter, "get_config", new Network.ResponseCallback() {

                @Override
                public void onSuccess(String str) {
                    try {
                        JSONObject jSONObject = new JSONObject(str);
                        if (jSONObject.getString("code").equals("00")) {
                            if (!jSONObject.isNull("servertime")) {
                                jSONObject.getString("servertime");
                                writeLog("Waktu Server", jSONObject.getString("servertime"));
                                ApiService.this.data.save(Data.Key.TIME_SERVER, jSONObject.getString("servertime"));
                                try {
                                    App.utility.getTimeServer();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    Log.d("NETLibs|Shell", "Gagal exec command shell!");
                                    ApiService.this.stringBuilder = new StringBuilder();
                                    ApiService.this.stringBuilder.append("[Time] ");
                                    ApiService.this.stringBuilder.append(str);
                                    ApiService.this.stringBuilder.append(StringUtils.LF);
                                    ApiService.this.stringBuilder.append("Gagal merubah waktu, ");
                                    ApiService.this.stringBuilder.append("silahkan lakukan pengaturan waktu ");
                                    ApiService.this.stringBuilder.append("secara manual di pengaturan perangkat.");

                                    setResultAPI(1, -1, "Inisialisasi bus gagal !", stringBuilder);
                                }
                            }

                            JSONObject jSONObject2 = jSONObject.getJSONObject("config");

                            Config config = new Config(context);
                            int writeCfg = config.writeConfig(Constant.CONFIG_NAME_FILE, jSONObject2.toString());
                            if (writeCfg == 0) {
                                Log.d("CONFIG", "Sukses tulis file config");
                            } else {
                                Log.d("CONFIG", "Gagal tulis file config");
                            }

                            if (!App.utility.setConfig(cActivity, jSONObject2)) {
                                //setResultAPI(1, -1, "Gagal setConfig", null);
                                return;
                            }

                            locationTrack.getLocation();

                            initBus(cActivity);

                            return;
                        }
                        String sb = "File Config Tidak Ditemukan Pada Server." +
                                StringUtils.LF +
                                "HWID: " + data.get(Data.Key.BUS_HWID);

                        setResultAPI(1, -1, "File Config Tidak Ditemukan", sb);
                    } catch (Exception e) {
                        e.printStackTrace();
                        String sb2 = "Gagal Parsing File Config." +
                                StringUtils.LF +
                                "Msg: " + e.getMessage();

                        setResultAPI(1, -1, "Gagal Parsing File Config", sb2);
                    } catch (Throwable e) {
                        setResultAPI(1, -1, e.toString(), null);
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onFailed(String str) {
                    try {
                        String string = new JSONObject(str).getString("desc");
                        String sb = "Gagal Menghubungi Server" +
                                StringUtils.LF +
                                "Msg: " + string +
                                StringUtils.LF +
                                "HWID: " + ApiService.this.data.get(Data.Key.BUS_HWID);

                        setResultAPI(1, -1, "Error Getting File Config", sb);
                    } catch (Exception e) {
                        writeLog("FailedGetResponse", str);
                        String sb2 = "File Config Tidak Ditemukan Pada Server." +
                                StringUtils.LF +
                                "Msg: " + str +
                                StringUtils.LF +
                                "HWID: " + ApiService.this.data.get(Data.Key.BUS_HWID);

                        setResultAPI(1, -1, "Error Getting File Config", sb2);
                    }
                }
            });
//            }
//            else {
//                writeLog("FailedKernelUpdate", "Failed Update kernel");
//                String sb = "Firmware Update" +
//                        StringUtils.LF +
//                        "Msg: " + "Gagal update firmware" +
//                        StringUtils.LF +
//                        "HWID: " + data.get(Data.Key.BUS_HWID);
//
//                setResultAPI(1, -1, "Error SDK: Gagal update firmware", sb);
//            }
        } catch (Exception e) {
            Log.e("Exception", e.toString());
            String sb = "Gagal Mengambil File Config" +
                    StringUtils.LF +
                    "HWID: " + ApiService.this.data.get(Data.Key.BUS_HWID);

            setResultAPI(1, -1, "Gagal Mengambil File Config", sb);
        }
    }

    @SuppressLint({"SimpleDateFormat", "MissingPermission"})
    public void getFareNew(AppCompatActivity cActivity, Location location) {
        Parameter params = new Parameter();
        params.Add("hwid", this.data.get(Data.Key.BUS_HWID));
        params.Add("date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis())));
        if (!App.fakeLatLong) {
            params.Add("lat", location.getLatitude());
            params.Add("long", location.getLongitude());
        } else {
            params.Add("lat", 0.0);
            params.Add("long", 0.0);
        }

        setMsgLoading("Get Fare");

        this.network.Post(params, "get_fare", new Network.ResponseCallback() {
            @Override
            public void onSuccess(final String response) {
                try {
                    JSONObject jsonObject = new JSONObject(response);

                    if (jsonObject.getBoolean("status")) {
                        Config config = new Config(context);
                        int writeCfg = config.writeConfig(Constant.FARE_NAME_FILE, jsonObject.toString());
                        if (writeCfg == 0) {
                            Log.d("CONFIG", "Sukses tulis file fare");
                        } else {
                            Log.d("CONFIG", "Gagal tulis file fare");
                        }

                        if (!App.utility.setFare(jsonObject)) {
                            setResultAPI(1, -1, "Gagal setFare", null);
                            return;
                        }

                        if (!jsonObject.isNull("filename") && !jsonObject.isNull("filecard")) {
                            tempFileName = jsonObject.getString("filename");
                            App.urlJson = jsonObject.getString("filecard");

                            if (App.urlJson.isEmpty() && tempFileName.isEmpty()) {
                                writeLog("TARIF_KHUSUS", "URL & File Name is empty");
                            }

                            writeLog("TARIF_KHUSUS", "URL : " + App.urlJson + "File Name : " + tempFileName);
                            getTarifData(App.urlJson, tempFileName);
                        }

                        int doInitSAM = App.utility.doInitSAM(cActivity);
                        Log.d(TAG, "RET ==> " + doInitSAM);
                        if (doInitSAM == 0) {
                            setResultAPI(0, 0, "Sukses", null);

                            App.isBusInitialized = true;

                            return;
                        }

                        return;
                    }

                    stringBuilder = new StringBuilder();
                    StringBuilder sb = stringBuilder;
                    sb.append("[").append(jsonObject.getString("code")).append("] ");
                    stringBuilder.append(jsonObject.getString(NotificationCompat.CATEGORY_MESSAGE));
                    stringBuilder.append(StringUtils.LF);
                    stringBuilder.append("Silahkan hubungi kantor pusat.");

                    setResultAPI(1, -1, "Gagal Mengambil Tarif Bus", stringBuilder);
                } catch (Exception e) {
                    e.printStackTrace();
                    writeLog("GetFareFailed", e.getMessage());
                    App.fileNameJson = "";
                    getFareNew(cActivity, location);
                } catch (Throwable e) {
                    setResultAPI(1, -1, "", null);
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onFailed(final String response) {
                try {
                    getFareNew(cActivity, location);
                    App.fileNameJson = "";
                } catch (Exception e) {
                    setResultAPI(1, -1, e.toString(), null);
                    e.printStackTrace();
                }
            }
        });
    }

    @SuppressLint({"SimpleDateFormat", "MissingPermission"})
    public void getFareTest(Location location) {
        Parameter params = new Parameter();
        params.Add("hwid", this.data.get(Data.Key.BUS_HWID));
        params.Add("date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis())));
        if (!App.fakeLatLong) {
            params.Add("lat", location.getLatitude());
            params.Add("long", location.getLongitude());
        } else {
            params.Add("lat", 0.0);
            params.Add("long", 0.0);
        }

        this.network.Post(params, "get_fare", new Network.ResponseCallback() {
            @Override
            public void onSuccess(final String response) {
                try {
                    JSONObject jsonObject = new JSONObject(response);

                    if (jsonObject.getBoolean("status")) {
                        Config config = new Config(context);
                        int writeCfg = config.writeConfig(Constant.FARE_NAME_FILE, jsonObject.toString());
                        if (writeCfg == 0) {
                            Log.d("CONFIG", "Sukses tulis file fare");
                        } else {
                            Log.d("CONFIG", "Gagal tulis file fare");
                        }

                        if (!App.utility.setFare(jsonObject)) {
                            setResultAPI(1, -1, "Gagal setFare", null);
                            return;
                        }

                        if (!jsonObject.isNull("filename") && !jsonObject.isNull("filecard")) {
                            tempFileName = jsonObject.getString("filename");
                            App.urlJson = jsonObject.getString("filecard");

                            if (App.urlJson.isEmpty() && tempFileName.isEmpty()) {
                                writeLog("TARIF_KHUSUS", "URL & File Name is empty");
                            }

                            getTarifData(App.urlJson, tempFileName);
                        }

                        return;
                    }

                    stringBuilder = new StringBuilder();
                    StringBuilder sb = stringBuilder;
                    sb.append("[").append(jsonObject.getString("code")).append("] ");
                    stringBuilder.append(jsonObject.getString(NotificationCompat.CATEGORY_MESSAGE));
                    stringBuilder.append(StringUtils.LF);
                    stringBuilder.append("Silahkan hubungi kantor pusat.");

                    setResultAPI(1, -1, "Gagal Mengambil Tarif Bus", stringBuilder);
                } catch (Exception e) {
                    e.printStackTrace();
                    writeLog("GetFareFailed", e.getMessage());
                    App.fileNameJson = "";
                } catch (Throwable e) {
                    setResultAPI(1, -1, "", null);
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onFailed(final String response) {
                try {
                    App.fileNameJson = "";
                } catch (Exception e) {
                    setResultAPI(1, -1, e.toString(), null);
                    e.printStackTrace();
                }
            }
        });
    }

    private void getPromo() {
        Parameter parameter = new Parameter();

        setMsgLoading("Get Promo");

        this.network.Post(parameter, "get_promo", new Network.ResponseCallback() {
            @Override
            public void onSuccess(String str) {
                try {
                    JSONObject jsonObject = new JSONObject(str);
                    if (jsonObject.getBoolean("status")) {
                        JSONArray dataJson = jsonObject.getJSONArray("data");
                        ArrayList<PromoModel> promoList = new ArrayList<>();
                        for (int i = 0; i < dataJson.length(); i++) {
                            JSONObject object = dataJson.getJSONObject(i);
                            PromoModel promoModel = new PromoModel();
                            promoModel.setPromocode(object.getString("promocode"));
                            promoModel.setPromoname(object.getString("promoname"));
                            promoModel.setPromotype(object.getString("promotype"));
                            promoModel.setPromotypename(object.getString("promotypename"));
                            promoModel.setPromofare(object.getString("promofare"));
                            promoModel.setPromostarttime(object.getString("promostarttime"));
                            promoModel.setPromoendtime(object.getString("promoendtime"));
                            promoModel.setPromodesc(object.getString("promodesc"));
                            promoList.add(promoModel);
                        }

                        data.savePromoList(promoList);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    writeLog("GetPromo", "Exception : " + e.getMessage());
                    setResultAPI(1, -1, e.toString(), null);
                }
            }

            @Override
            public void onFailed(String str) {
                setResultAPI(1, -1, str, null);
            }
        });
    }

    public void getTarifData(String url, String filename) {
        setMsgLoading("Get Data Price");

        this.network.Get(url, new Network.ResponseCallback() {
            @Override
            public void onSuccess(String str) {
                try {
                    JSONObject data = new JSONObject(str);
                    App.utility.saveFileTarif(getTime(), String.valueOf(data), filename);
                } catch (Exception e) {
                    e.printStackTrace();
                    setResultAPI(1, -1, e.toString(), null);
                }
            }

            @Override
            public void onFailed(String str) {
                setResultAPI(1, -1, str, null);
            }
        });
    }

    /**
     * Trx Failed
     */
    public void checkDataLocalTrxFailed() {
        List<TransactionFailModel> allData = this.transactionFailController.getAllData();
        if (allData != null) {
            if (!allData.isEmpty() && isReadyToSendTrxFailed) {
                for (int i = 0; i < allData.size(); i++) {
                    TransactionFailModel transactionFailModel = allData.get(i);
                    if (transactionFailModel.getStatus().equals("false")) {
                        sendTrxFailLog(transactionFailModel);
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void sendTrxFailLog(final TransactionFailModel transactionFailModel) {
        try {
            if (ReaderUtils.isNetworkAvailable(context)) {
                isReadyToSendTrxFailed = false;
                Parameter parameter = getParameterTrxFailed(transactionFailModel);

                this.network.Post(parameter, "trx_fail", new Network.ResponseCallback() {
                    @Override
                    public void onSuccess(String str) {
                        try {
                            if (new JSONObject(str).getBoolean("status")) {
                                transactionFailController.delete(transactionFailModel);
                                isReadyToSendTrxFailed = true;
                                return;
                            }
                            isReadyToSendTrxFailed = true;
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.d("SendLogFail", e.toString());
                            isReadyToSendTrxFailed = true;
                        }
                    }

                    @Override
                    public void onFailed(String str) {
                        isReadyToSendTrxFailed = true;
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            isReadyToSendTrxFailed = true;
        }

    }

    /**
     * Trx Success
     */
    public void checkDataLocalTrxSuccess() {
        int dataSize = this.transactionController.getDataSize();
        Log.d("checkDataSize", String.valueOf(dataSize));
        this.data.save(Data.Key.PASSENGER_TAP_PENDING, String.valueOf(dataSize));

        TransactionModel data = this.transactionController.getData();
        DataTapOutModel dataTapOut = this.dataTapOutController.getData();
        if (data != null) {
            boolean sendingProcess = this.data.getBoolean(Data.Key.SENDING_PROCESS);
            if (!sendingProcess) {
                sendTrxPrepaid(data);
            }
        }
        if (dataTapOut != null) {
            sendDataTapOut(dataTapOut);
        }

        App.utility.saveLocation();
    }

    private void sendTrxPrepaid(final TransactionModel transactionModel) {
        try {
            if (ReaderUtils.isNetworkAvailable(context)) {
                this.data.saveBoolean(Data.Key.SENDING_PROCESS, true);
                Parameter parameter = getParameterTrxSuccess(transactionModel);

                this.network.PostTrx(parameter, "trx_card", new Network.ResponseTrxCallback() {
                    @Override
                    public void onSuccess(String str) {
                        try {
                            JSONObject dataJson = new JSONObject(str);
                            if (dataJson.getBoolean("status")) {
                                writeLog("CurrentCounter", data.get(Data.Key.PASSENGER_TAP));
                                writeLog("CurrentCounterQRIS", data.get(Data.Key.PASSENGER_TAP_QRIS));
                                writeLog("CurrentPending", data.get(Data.Key.PASSENGER_TAP_PENDING));
                                if (App.isBalikPapanUngu()){
                                    writeLog("CurrentPelajar", data.get(Data.Key.PASSENGER_TAP_PELAJAR));
                                    writeLog("CurrentLansia", data.get(Data.Key.PASSENGER_TAP_LANSIA));
                                    writeLog("CurrentDifable", data.get(Data.Key.PASSENGER_TAP_DIFABEL));
                                    writeLog("CurrentReqular", data.get(Data.Key.PASSENGER_TAP_REGULAR));
                                }

                                if (App.isBekasi() || App.isKabupatenBekasi() || App.isBekasiUngu() || App.isBalikPapanUngu()) {
                                    if (transactionModel.getTapin().equals("true")) {
                                        writeLog("TAPIN", "COUNTER TAPIN");
                                        counterResolver(transactionModel, dataJson);
                                    } else {
                                        writeLog("TAPOUT", "MASUK KONDISI TAPOUT");

                                    }
                                } else {
                                    counterResolver(transactionModel, dataJson);
                                }

                                transactionController.delete(transactionModel);
                                new Thread(() -> {
                                    List<TransactionModel> allData = transactionController.getAllData();
                                    if (allData != null) {
                                        new Handler(Looper.getMainLooper()).post(() -> {
                                            data.save(Data.Key.PASSENGER_TAP_PENDING, String.valueOf(allData.size()));
                                        });
                                    }
                                }).start();
                            } else {
                                if (Objects.equals(transactionModel.getStatus(), "false")) {
                                    transactionModel.setStatus("true");
                                    transactionController.update(transactionModel);
                                }
                            }
                            saveDataLog();
                            data.saveBoolean(Data.Key.SENDING_PROCESS, false);
                        } catch (Exception e) {
                            writeLog("RES_TRX", "Exception : " + e.getMessage());
                            if (Objects.equals(transactionModel.getStatus(), "false")) {
                                transactionModel.setStatus("true");
                                transactionController.update(transactionModel);
                            }
                            saveDataLog();
                            data.saveBoolean(Data.Key.SENDING_PROCESS, false);
                        }
                    }

                    @Override
                    public void onFailed(String str, boolean isNoConnection) {
                        if (!isNoConnection) {
                            if (Objects.equals(transactionModel.getStatus(), "false")) {
                                transactionModel.setStatus("true");
                                transactionController.update(transactionModel);
                            }
                        }
                        saveDataLog();
                        data.saveBoolean(Data.Key.SENDING_PROCESS, false);
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            writeLog("RES_TRX", "Exception : " + e.getMessage());
            if (Objects.equals(transactionModel.getStatus(), "false")) {
                transactionModel.setStatus("true");
                transactionController.update(transactionModel);
            }
            saveDataLog();
            data.saveBoolean(Data.Key.SENDING_PROCESS, false);
        }
    }

    private void counterResolver(TransactionModel model, JSONObject dataJson){
        try {
            if (model.getTapin().equals("true") && dataJson.getString("code").equals("1000")) {
                writeLog("TAPIN", "COUNTER TAPIN RESOLVER");

                String counter = utility.getOrZero(Data.Key.PASSENGER_TAP);
                String counterKartu = utility.getOrZero(Data.Key.PASSENGER_TAP_KARTU);

                if (App.isBalikPapanUngu()){
                    String counterPelajar = utility.getOrZero(Data.Key.PASSENGER_TAP_PELAJAR);
                    String counterLansia = utility.getOrZero(Data.Key.PASSENGER_TAP_LANSIA);
                    String counterDifabel= utility.getOrZero(Data.Key.PASSENGER_TAP_DIFABEL);
                    String counterRegular= utility.getOrZero(Data.Key.PASSENGER_TAP_REGULAR);

                    switch (model.getCardType()){
                        case Constant.CARD_TYPE.KARTU_PELAJAR:{
                            data.save(Data.Key.PASSENGER_TAP_PELAJAR, String.valueOf(Integer.parseInt(counterPelajar) + 1));
                            writeLog("IncPelajar", data.get(Data.Key.PASSENGER_TAP_PELAJAR));
                            break;
                        }
                        case Constant.CARD_TYPE.KARTU_LANSIA:{
                            data.save(Data.Key.PASSENGER_TAP_LANSIA, String.valueOf(Integer.parseInt(counterLansia) + 1));
                            writeLog("IncLansia", data.get(Data.Key.PASSENGER_TAP_LANSIA));
                            break;
                        }
                        case Constant.CARD_TYPE.KARTU_DIFABEL:{
                            data.save(Data.Key.PASSENGER_TAP_DIFABEL, String.valueOf(Integer.parseInt(counterDifabel) + 1));
                            writeLog("IncDifabel", data.get(Data.Key.PASSENGER_TAP_DIFABEL));
                            break;
                        }
                        default:{
                            data.save(Data.Key.PASSENGER_TAP_REGULAR, String.valueOf(Integer.parseInt(counterRegular) + 1));
                        }
                    }
                }

                data.save(Data.Key.PASSENGER_TAP_KARTU, String.valueOf(Integer.parseInt(counterKartu) + 1));
                data.save(Data.Key.PASSENGER_TAP, String.valueOf(Integer.parseInt(counter) + 1));
            }
        }catch (Exception e){
            writeLog("RES_TRX", "Exception : " + e.getMessage());
            if (Objects.equals(model.getStatus(), "false")) {
                model.setStatus("true");
                transactionController.update(model);
            }
            saveDataLog();
            data.saveBoolean(Data.Key.SENDING_PROCESS, false);
        }
    }

    private void sendDataTapOut(DataTapOutModel model) {
        try {
            String timeTO = model.getTimestamp();
            String issuerData = model.getIssuer();
            String cardNumberData = model.getSn();
            String tidReader = model.getTid();
            String bankName = "";
            String paymentMethod = "";

            switch (issuerData) {
                case "MDR":
                    issuerData = "E-MONEY";
                    bankName = "MANDIRI";
                    paymentMethod = "PM-0006";
                    break;
                case "BCA":
                    issuerData = "FLAZZ";
                    bankName = "BCA";
                    paymentMethod = "PM-0001";
                    break;
                case "BNI":
                    issuerData = "TAPCASH";
                    bankName = "BNI";
                    paymentMethod = "PM-0002";
                    break;
                case "BRI":
                    issuerData = "BRIZZI";
                    bankName = "BRI";
                    paymentMethod = "PM-0007";
                    break;
            }

            new DataTapOutController(context).delete(model);

            this.data.save(Data.Key.ISSUER_SERIAL, issuerData);
            this.data.save(Data.Key.CARDNUMBER_SERIAL, cardNumberData);
            this.data.save(Data.Key.TAP_IN, "false");

            writeLog("TAPOUT", "Sukses Tap out [" + issuerData + "]" + " \nNo. Kartu : " + cardNumberData);

            if (cardNumberData.isEmpty()) {
                writeLog("TAPOUT", "SN empty");
                sdkInstance.sendResponseSerialFalse();
            } else if (tidReader.isEmpty()) {
                writeLog("TAPOUT", "TID empty");
                sdkInstance.sendResponseSerialFalse();
            } else if (!Utils.isValidDate(timeTO)) {
                writeLog("TAPOUT", "Invalid date");
                sdkInstance.sendResponseSerialFalse();
            } else {
                String str = this.data.get(Data.Key.PASSENGER_TAP_OUT);
                if (str == null || str.isEmpty()) {
                    str = "0";
                }
                this.data.save(Data.Key.PASSENGER_TAP_OUT, String.valueOf(Integer.parseInt(str) + 1));

                localService.insertTrx(model, paymentMethod, bankName);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("SendDataSNSerial", "Error : " + e.getMessage());
        }
    }

    public void sendQrisTap(TransactionQrisModel transactionQrisModel, PaymentModel modelPayment, boolean doubleTap) {
        String endPoint;
        final boolean tapInOut = modelPayment.getProductIndicator().equals(Constant.PRODUCT_INDICATOR_TAP_QRIS.TAP_OUT);
        final boolean singleTap = modelPayment.getProductIndicator().equals(Constant.PRODUCT_INDICATOR_TAP_QRIS.SINGLE_TAP);
        final String qrType = modelPayment.getQrType();
        writeLog("QRIS_TYPE", qrType);

        Parameter parameter = new Parameter();
        if (doubleTap) {
            writeLog("QRIS_TAP", "Double Tap");
            String timestamp = Utility.getDateTimeWithFormat(getTime(), "yyyy-MM-dd HH:mm:ss");
            if (tapInOut) {
                writeLog("QRIS_TAP", "Tap Out");
                parameter.AddJson("trx_id", modelPayment.getTrxId());
                parameter.AddJson("trx_capture_id", data.get(Data.Key.BUS_HWID) + getTime());
                parameter.AddJson("qrcontent", modelPayment.getQrcontent());
                parameter.AddJson("amount", modelPayment.getAmount());
                parameter.AddJson("product_indicator", modelPayment.getProductIndicator());

                setMsgLoading("Sedang proses kirim\nMohon tunggu...");

//                if (MainActivity.instance != null) {
//                    MainActivity.instance.printLogUI("REQUEST", String.valueOf(parameter.getJson()));
                setResultAPI(3, -1, String.valueOf(parameter.getJson()), null);

//                }

                endPoint = "capture";
            } else {
                writeLog("QRIS_TAP", "Tap In");
                parameter.AddJson("hwid", modelPayment.getHwid());
                parameter.AddJson("operational", modelPayment.getOperational());
                parameter.AddJson("trip", modelPayment.getTrip());
                parameter.AddJson("route", modelPayment.getRoute());
                parameter.AddJson("trx_id", modelPayment.getTrxId());
                /*parameter.AddJson("mid", modelPayment.getMid());
                parameter.AddJson("tid", modelPayment.getTid());*/
                parameter.AddJson("qrcontent", modelPayment.getQrcontent());
                parameter.AddJson("amount", modelPayment.getAmount());
                /*parameter.AddJson("fee", modelPayment.getFee());
                parameter.AddJson("qr_type", modelPayment.getQrType());*/
                parameter.AddJson("trx_date", timestamp);
                parameter.AddJson("lat", String.valueOf(locationTrack.getLatitude()));
                parameter.AddJson("long", String.valueOf(locationTrack.getLongitude()));
                /*if (modelPayment.getProductIndicator().equals(Constant.PRODUCT_INDICATOR_TAP_QRIS.TAP_OUT)) {
                    parameter.AddJson("approval_code", "0001QQ");
                } else {
                    parameter.AddJson("approval_code", modelPayment.getPan());
                }*/
                parameter.AddJson("product_indicator", modelPayment.getProductIndicator());

                setMsgLoading("Sedang proses kirim\nMohon tunggu...");

//                if (MainActivity.instance != null) {
//                    MainActivity.instance.printLogUI("REQUEST", String.valueOf(parameter.getJson()));
//                }
                setResultAPI(3, -1, String.valueOf(parameter.getJson()), null);


                endPoint = "payment";

            }
        } else {
            writeLog("QRIS_TAP", "Single Tap");
            String timestamp = Utility.getDateTimeWithFormat(getTime(), "yyyy-MM-dd HH:mm:ss");
            parameter.AddJson("hwid", modelPayment.getHwid());
            parameter.AddJson("operational", modelPayment.getOperational());
            parameter.AddJson("trip", modelPayment.getTrip());
            parameter.AddJson("route", modelPayment.getRoute());
            parameter.AddJson("trx_id", modelPayment.getTrxId());
            parameter.AddJson("qrcontent", modelPayment.getQrcontent());
            parameter.AddJson("amount", modelPayment.getAmount());
            parameter.AddJson("trx_date", timestamp);
            parameter.AddJson("lat", String.valueOf(locationTrack.getLatitude()));
            parameter.AddJson("long", String.valueOf(locationTrack.getLongitude()));

            /*parameter.AddJson("approval_code", modelPayment.getPan());*/

            if (App.isDamriQ6() || App.isDamri()) {
                parameter.AddJson("mid", modelPayment.getMid());
                parameter.AddJson("tid", modelPayment.getTid());
                parameter.AddJson("fee", modelPayment.getFee());
                parameter.AddJson("qr_type", modelPayment.getQrType());
            } else if (App.isDemo()) {
                parameter.AddJson("product_indicator", modelPayment.getProductIndicator());
            }

            setMsgLoading("Sedang proses kirim\nMohon tunggu...");

//            if (MainActivity.instance != null) {
//                MainActivity.instance.printLogUI("REQUEST", String.valueOf(parameter.getJson()));
//            }
            setResultAPI(3, -1, String.valueOf(parameter.getJson()), null);


            endPoint = "payment";
        }

        this.network.PostJsonQris(parameter,
                App.auth,
                App.xPartner,
                Utils.createSignature(modelPayment), endPoint, new Network.ResponseQrisTrxCallback() {
                    @Override
                    public void onSuccess(JSONObject jsonObject) {
                        App.lastDetectTime = getTime();
                        App.isCanReset = true;

//                        if (MainActivity.instance != null) {
//                            MainActivity.instance.printLogUI("RESPONSE", String.valueOf(jsonObject));
//                        }
                        setResultAPI(3, -1, String.valueOf(jsonObject), null);


                        try {
                            String refNo = "";
                            String invoiceNumber = "";
                            if (jsonObject.has("data")) {
                                JSONObject dataJson = jsonObject.optJSONObject("data");
                                if (dataJson != null) {
                                    refNo = dataJson.getString("ref_no");
                                    invoiceNumber = dataJson.getString("invoice_number");
                                }
                            }

                            if (jsonObject.getString("code").equals("00")) {
                                transactionQrisModel.setStatus("true");
                                new TransactionQrisController(context).update(transactionQrisModel);
                                // Notif pembayaran sukses N004, N005, N007
                                if (jsonObject.getString("message").contains("N004") || jsonObject.getString("message").contains("N005") || jsonObject.getString("message").contains("N007")) {
                                    String counter = data.get(Data.Key.PASSENGER_TAP);
                                    if (counter == null || counter.isEmpty()) {
                                        counter = "0";
                                    }

                                    String counterQris = data.get(Data.Key.PASSENGER_TAP_QRIS);
                                    if (counterQris == null || counterQris.isEmpty()) {
                                        counterQris = "0";
                                    }

                                    data.save(Data.Key.PASSENGER_TAP_QRIS, String.valueOf(Integer.parseInt(counterQris) + 1));
                                    data.save(Data.Key.PASSENGER_TAP, String.valueOf(Integer.parseInt(counter) + 1));

                                    stringBuilder = new StringBuilder();
                                    stringBuilder.append("Saldo Terpotong : Rp ").append(Convert.Currency.IDR(Integer.valueOf(data.get(Data.Key.BUS_FARE_AMOUNT)))).append("\n\nSelamat menikmati perjalanan anda, terimakasih.");

                                    setResultAPI(0, 0, "Transaksi berhasil \n[" + "QRISTAP" + "]", stringBuilder);
                                } else if (jsonObject.getString("message").contains("N003") || jsonObject.getString("message").contains("N006")) { // Notif PreAuth sukses N003, N006
                                    stringBuilder = new StringBuilder();
                                    stringBuilder.append("Selamat menikmati perjalanan anda, terimakasih.");

                                    setResultAPI(0, 0, "Transaksi berhasil \n[" + "QRISTAP PREAUTH" + "]", stringBuilder);
                                } else { // Notif pembayaran sukses N001 Single tap
                                    if (tapInOut || singleTap) {
                                        String counter = data.get(Data.Key.PASSENGER_TAP);
                                        if (counter == null || counter.isEmpty()) {
                                            counter = "0";
                                        }

                                        String counterQris = data.get(Data.Key.PASSENGER_TAP_QRIS);
                                        if (counterQris == null || counterQris.isEmpty()) {
                                            counterQris = "0";
                                        }

                                        data.save(Data.Key.PASSENGER_TAP_QRIS, String.valueOf(Integer.parseInt(counterQris) + 1));
                                        data.save(Data.Key.PASSENGER_TAP, String.valueOf(Integer.parseInt(counter) + 1));

                                        MainActivity.instance.lastOptionalData = modelPayment.getQrcontent();

                                        new TransactionQrisController(context).delete(transactionQrisModel);

                                        writeLog("CurrentCounter", data.get(Data.Key.PASSENGER_TAP));
                                        writeLog("CurrentCounterQRIS", data.get(Data.Key.PASSENGER_TAP_QRIS));
                                        writeLog("CurrentPending", data.get(Data.Key.PASSENGER_TAP_PENDING));

                                        stringBuilder = new StringBuilder();
                                        stringBuilder.append("Saldo Terpotong : Rp ").append(Convert.Currency.IDR(Integer.valueOf(data.get(Data.Key.BUS_FARE_AMOUNT)))).append("\n\nSelamat menikmati perjalanan anda, terimakasih.");

                                        setResultAPI(0, 0, "Transaksi berhasil \n[" + "QRIS" + "]", stringBuilder);
                                    } else {
                                        stringBuilder = new StringBuilder();
                                        stringBuilder.append("\nSelamat menikmati perjalanan anda, terimakasih.");

                                        setResultAPI(0, 0, "PreAuth Success \n[" + "QRIS" + "]", stringBuilder);
                                    }
                                }

                                App.lastTrx = Utility.getTimeWithTimeStart();
                            } else if (jsonObject.getString("code").equals("10")) {
                                /*String cPending = data.get(Data.Key.PASSENGER_TAP_PENDING);
                                if (cPending == null || cPending.isEmpty()) {
                                    cPending = "0";
                                }
                                data.save(Data.Key.PASSENGER_TAP_PENDING, String.valueOf(Integer.parseInt(cPending) + 1));*/
                                String finalRefNo = refNo;
                                String finalInvoiceNumber = invoiceNumber;

                                new Thread(new Runnable() {
                                    public void run() {
                                        QueryPaymentModel model = new QueryPaymentModel();
                                        model.setId(String.valueOf(getTime()));
                                        model.setTrxId(modelPayment.getTrxId());
                                        model.setRef_no(finalRefNo);
                                        model.setInvoice_number(finalInvoiceNumber);

                                        new QueryPaymentController(MainActivity.instance).insert(model);
                                    }
                                }).start();

                                setResultAPI(0, 2, "Transaksi QRIS pending...", null);
                            } else {
                                        /*String cGagal = data.get(Data.Key.COUNTER_GAGAL);
                                        if (cGagal == null || cGagal.equals("")) {
                                            cGagal = "0";
                                        }
                                        data.save(Data.Key.COUNTER_GAGAL, String.valueOf(Integer.parseInt(cGagal) + 1));*/

                                setResultAPI(1, -1, "Transaksi Gagal", jsonObject.getString("code") + " : " + jsonObject.getString("message"));
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                                    /*String cGagal = data.get(Data.Key.COUNTER_GAGAL);
                                    if (cGagal == null || cGagal.equals("")) {
                                        cGagal = "0";
                                    }
                                    data.save(Data.Key.COUNTER_GAGAL, String.valueOf(Integer.parseInt(cGagal) + 1));*/

                            setResultAPI(1, -1, "Transaksi Gagal", e.getMessage());
                        } catch (Exception e) {
                            setResultAPI(1, -1, "Transaksi Gagal|Exeption[cr_09]", e.toString());

                            writeLog("exeption[cr_09]", e.getLocalizedMessage());
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void onFailed(String str, boolean connection) {
                        App.lastDetectTime = getTime();
                        App.isCanReset = true;

//                        if (MainActivity.instance != null) {
//                            MainActivity.instance.printLogUI("RESPONSE", str);
//                        }

                        setResultAPI(3, -1, str, null);


                        if (Utils.isValidJSON(str)) {
                            try {
                                JSONObject error = new JSONObject(str);
                                String errorMessage = error.getString("message");

                                if (error.getString("code").equals("10")) {
                                    JSONObject dataJson = error.getJSONObject("data");
                                    String refNo = dataJson.getString("ref_no");
                                    String invoiceNumber = dataJson.getString("invoice_number");

                                    String cPending = data.get(Data.Key.PASSENGER_TAP_PENDING);
                                    if (cPending == null || cPending.isEmpty()) {
                                        cPending = "0";
                                    }
                                    data.save(Data.Key.PASSENGER_TAP_PENDING, String.valueOf(Integer.parseInt(cPending) + 1));
                                    new Thread(new Runnable() {

                                        public void run() {
                                            QueryPaymentModel model = new QueryPaymentModel();
                                            model.setId(String.valueOf(getTime()));
                                            model.setTrxId(modelPayment.getTrxId());
                                            model.setRef_no(refNo);
                                            model.setInvoice_number(invoiceNumber);

                                            new QueryPaymentController(MainActivity.instance).insert(model);
                                        }
                                    }).start();

                                    setResultAPI(0, 2, "Informasi", "Transaksi Pending \n" + errorMessage);
                                } else {
                                    /*String cGagal = data.get(Data.Key.COUNTER_GAGAL);
                                    if (cGagal == null || cGagal.equals("")) {
                                        cGagal = "0";
                                    }
                                    data.save(Data.Key.COUNTER_GAGAL, String.valueOf(Integer.parseInt(cGagal) + 1));*/

                                    setResultAPI(1, -1, "Transaksi Gagal", errorMessage);
                                }
                            } catch (JSONException e) {
                                setResultAPI(1, -1, "Transaksi Gagal", e.toString());

                                throw new RuntimeException(e);
                            }
                        } else {
                            /*String cGagal = data.get(Data.Key.COUNTER_GAGAL);
                            if (cGagal == null || cGagal.equals("")) {
                                cGagal = "0";
                            }
                            data.save(Data.Key.COUNTER_GAGAL, String.valueOf(Integer.parseInt(cGagal) + 1));*/

                            setResultAPI(1, -1, "Transaksi Gagal", str);
                        }

                        BARCODE = "";
                        LAST_QR = "";

                        writeLog("failedSendPayment", str);
                    }
                });
    }

    /**
     * Change Trip & Location Log
     *
     * @throws Exception
     */
    public void postLocation(LocationTrack locationTrack, MqttHelper mqttHelper) {
        try {
//            if (locationTrack.getLocation() != null) {
            Double latitude = locationTrack.getLatitude();
            Double longitude = locationTrack.getLongitude();
            i_cam1in = Integer.parseInt(this.data.get(Data.Key.PASSENGER_TAP));

            String strRadius = data.get(Data.Key.RADIUS);
            float radius;
            if (strRadius != null) {
                radius = Float.parseFloat(data.get(Data.Key.RADIUS));
            } else {
                radius = 500.0f;
            }

            String provider = "locationEnd";
            this.targetLocation = new Location(provider);
            this.targetLocation.setLatitude(Double.parseDouble(this.data.get(Data.Key.LAT_END)));
            this.targetLocation.setLongitude(Double.parseDouble(this.data.get(Data.Key.LONG_END)));

            List<LocationModel> allLocationData = locationController.getLimitData(utility.getLocationLimit());
            Log.d("LocationData", "isReadyToSend : " + isReadyToSendLog);

            if (!allLocationData.isEmpty() && isReadyToSendLog) {
                isReadyToSendLog = false;
                    /*final String resultAddress = GeocodingHelper.getAddressFromCoordinates(
                            mMyApp.getCurrentActivity(), latitude, longitude
                    );*/

                int signalLevel = SignalStrengthHelper.getSignalStrengthLevel(mMyApp.getCurrentActivity());

                for (int i = 0; i < allLocationData.size(); i++) {
                    LocationModel locationModel = allLocationData.get(i);
                    if (locationTrack == null) {
                        allLocationDataAPI.add(new LocationModelRequest(
                                this.data.get(Data.Key.BUS_HWID),
                                this.data.get(Data.Key.OPERATIONAL_CODE),
                                this.data.get(Data.Key.BUS_ROUTE_NAME),
                                this.data.get(Data.Key.BUS_TRIP_COUNTER),
                                String.valueOf(this.i_cam1in),
                                String.valueOf(this.i_cam1out),
                                String.valueOf(this.i_cam1on),
                                "0.0",
                                "0.0",
                                locationModel.getTimestamp(),
                                this.data.get(Data.Key.BUS_TID),
                                this.data.get(Data.Key.BUS_CODE),
                                String.valueOf(locationTrack.getSpeed()),
                                String.valueOf(locationTrack.getAltitude()),
                                String.valueOf(locationTrack.getBearing()),
                                ReaderUtils.checkGPS(context) ? "1" : "0",
                                String.valueOf(ReaderUtils.getBatteryLevel(context)),
                                "1",
                                "-",
                                String.valueOf(signalLevel),
                                this.data.get(Data.Key.BUS_HWID),
                                this.data.get(Data.Key.PASSENGER_TAP),
                                this.data.get(Data.Key.PASSENGER_TAP_KARTU),
                                this.data.get(Data.Key.PASSENGER_TAP_QRIS),
                                this.data.get(Data.Key.PASSENGER_TAP_LAST)
                        ));
                    } else {
                        allLocationDataAPI.add(new LocationModelRequest(
                                this.data.get(Data.Key.BUS_HWID),
                                this.data.get(Data.Key.OPERATIONAL_CODE),
                                this.data.get(Data.Key.BUS_ROUTE_NAME),
                                this.data.get(Data.Key.BUS_TRIP_COUNTER),
                                String.valueOf(this.i_cam1in),
                                String.valueOf(this.i_cam1out),
                                String.valueOf(this.i_cam1on),
                                locationModel.getLatitude(),
                                locationModel.getLongitude(),
                                locationModel.getTimestamp(),
                                this.data.get(Data.Key.BUS_TID),
                                this.data.get(Data.Key.BUS_CODE),
                                String.valueOf(locationTrack.getSpeed()),
                                String.valueOf(locationTrack.getAltitude()),
                                String.valueOf(locationTrack.getBearing()),
                                ReaderUtils.checkGPS(context) ? "1" : "0",
                                String.valueOf(ReaderUtils.getBatteryLevel(context)),
                                "1",
                                "-",

                                String.valueOf(signalLevel),
                                this.data.get(Data.Key.BUS_HWID),
                                this.data.get(Data.Key.PASSENGER_TAP),
                                this.data.get(Data.Key.PASSENGER_TAP_KARTU),
                                this.data.get(Data.Key.PASSENGER_TAP_QRIS),
                                this.data.get(Data.Key.PASSENGER_TAP_LAST)
                        ));
                    }

                }

                Log.d("LocationData", "ReadyToSendAPI : " + allLocationDataAPI.size());

                if (App.isUsingMqtt && !App.isDamri() && !App.isDamriQ6() && !App.isSbyQ6() && !App.isWaraWiri()) {
                    ArrayList<LocationModel> localLocationModels = new ArrayList<>(allLocationData);
                    String data = new Gson().toJson(allLocationDataAPI);
                    JSONObject dataJsonFinal = new JSONObject();
                    try {
                        JSONArray jArr = new JSONArray(data);
                        dataJsonFinal.put("data", jArr);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }

                    if (mqttHelper != null && mqttHelper.isConnected()) {
                        boolean publishMsg = mqttHelper.publishMessage(dataJsonFinal.toString());
                        if (publishMsg) {
                            locationController.deleteAll();
                        }
                        isReadyToSendLog = true;
                    } else {
                        if (App.isDamri() || App.isDamriQ6() || App.isSbyQ6() || App.isWaraWiri()) {
                            sendLog(this.i_cam1in, this.i_cam1out, this.i_cam1on, latitude.doubleValue(), longitude.doubleValue());
                        } else {
                            sendLogLocationRaw(allLocationDataAPI, allLocationData);
                        }
                    }
                } else {
                    if (App.isDamri() || App.isDamriQ6() || App.isSbyQ6() || App.isWaraWiri()) {
                        sendLog(this.i_cam1in, this.i_cam1out, this.i_cam1on, latitude.doubleValue(), longitude.doubleValue());
                    } else {
                        sendLogLocationRaw(allLocationDataAPI, allLocationData);
                    }
                }

                allLocationData.clear();
                allLocationDataAPI.clear();
            }

            if (utility.isOnRadius(locationTrack.getLocation(), this.targetLocation, radius) && !isCanChangetrip) {
                changeTrip(this.i_cam1in, this.i_cam1out, this.i_cam1on, latitude, longitude);
                isCanChangetrip = true;
            }
//            }
//            else {
//                if (App.isUsingMqtt && !App.isDamri() && !App.isDamriQ6() && !App.isSbyQ6() && !App.isWaraWiri()) {
//                    if (mqttHelper != null && mqttHelper.isConnected()) {
//                        mqttHelper.publishMessage("Blank Spot");
//                    }
//                }
//            }
        } catch (Exception e) {
            isReadyToSendLog = true;
            e.printStackTrace();
        }
    }

    public void onTriggerChangeTrip() {
        try {
            if (this.isCanChangetrip) {
                this.i_cam1in = Integer.parseInt(this.data.get(Data.Key.PASSENGER_TAP));
                doChangeTrip(this.i_cam1in, this.i_cam1out, this.i_cam1on, locationTrack.getLatitude().doubleValue(), locationTrack.getLongitude().doubleValue());
            } else {
                setResultAPI(1, -1, "Gagal Change Trip", null);
                /*runOnUiThread(new Runnable() {
                    public void run() {
                        App.utility.showDialog(-1, "Gagal Change Trip", "Anda Belum Bisa Melakukan Changetrip", 2000);
                    }
                });*/
            }
        } catch (Exception e) {
            e.printStackTrace();
            setResultAPI(1, -1, e.toString(), null);
        }
    }

    private void doChangeTrip(int i, int i2, int i3, double d, double d2) throws Exception {
        Parameter parameter = new Parameter();
        parameter.Add("hwid", this.data.get(Data.Key.BUS_HWID));
        parameter.Add("route_code", this.data.get(Data.Key.BUS_ROUTE_CODE));
        parameter.Add("operational", this.data.get(Data.Key.OPERATIONAL_CODE));
        parameter.Add("trip", this.data.get(Data.Key.BUS_TRIP_COUNTER));
        parameter.Add("datetime", Utility.getDateTimeWithFormat("yyyy-MM-dd HH:mm:ss"));
        parameter.Add("in", Integer.valueOf(i));
        parameter.Add("out", Integer.valueOf(i2));
        parameter.Add("on", Integer.valueOf(i3));
        parameter.Add("lat", Double.valueOf(d));
        parameter.Add("long", Double.valueOf(d2));

        setMsgLoading("Change Trip");

        try {
            this.network.Post(parameter, "change_trip", new Network.ResponseCallback() {

                @Override
                public void onSuccess(String str) {
                    try {
                        JSONObject jSONObject = new JSONObject(str);
                        if (jSONObject.getBoolean("status")) {
                            writeLog("ChangeTrip", "Success : " + str);
                            data.save(Data.Key.BUS_FARE_AMOUNT, jSONObject.getString("fare"));
                            data.save(Data.Key.BUS_TRIP_COUNTER, jSONObject.getString("trip"));
                            data.save(Data.Key.BUS_ROUTE_CODE, jSONObject.getString("route_code"));
                            data.save(Data.Key.BUS_ROUTE_NAME, jSONObject.getString("route"));
                            data.save(Data.Key.OPERATIONAL_CODE, jSONObject.getString("operational"));
                            data.save(Data.Key.OPERATIONAL_START, jSONObject.getString("start"));
                            data.save(Data.Key.OPERATIONAL_END, jSONObject.getString("end"));
                            data.save(Data.Key.BUS_CURRENT_HALTE, jSONObject.getString("n_halte_1"));
                            Location location = new Location("locationStart");
                            location.setLatitude(jSONObject.getDouble("start_lat"));
                            location.setLongitude(jSONObject.getDouble("start_lng"));
                            data.save(Data.Key.LOCATION_START, gson.toJson(location));
                            Location location2 = new Location("locationEnd");
                            location2.setLatitude(jSONObject.getDouble("end_lat"));
                            location2.setLongitude(jSONObject.getDouble("end_lng"));
                            data.save(Data.Key.LOCATION_END, gson.toJson(location2));
                            targetLocation = location2;
                            isCanChangetrip = false;
                            // resetCounter();
                            /*runOnUiThread(new Runnable() {

                                public void run() {
                                    App.utility.showDialog(0, "Sukses Change Trip", "Anda Barusaja Melakukan Changetrip", 2000);
                                }
                            });*/
                            /*setResultAPI(0, "Sukses Change Trip", null);
                            sendEmptyMessage(0);*/
                            return;
                        }

                        /*setResultAPI(-1, "Gagal Melakukan Changetrip [False]", null);
                        sendEmptyMessage(1);*/
                        /*runOnUiThread(new Runnable() {

                            public void run() {
                                App.utility.showDialog(-1, "Error Change Trip", "Gagal Melakukan Changetrip [False]", 2000);
                            }
                        });*/
                    } catch (Exception e) {
                        e.printStackTrace();
                        /*setResultAPI(-1, "Gagal Melakukan Changetrip [Error Parsing]", null);
                        sendEmptyMessage(1);*/
                        /*runOnUiThread(new Runnable() {

                            public void run() {
                                App.utility.showDialog(-1, "Error Change Trip", "Gagal Melakukan Changetrip [Error Parsing]", 2000);
                            }
                        });*/
                    }
                }

                @Override
                public void onFailed(String str) {
                    /*setResultAPI(-1, "Gagal Melakukan Changetrip [Volley Fail]", null);
                    sendEmptyMessage(1);*/
                    /*runOnUiThread(new Runnable() {

                        public void run() {
                            App.utility.showDialog(-1, "Error Change Trip", "Gagal Melakukan Changetrip [Volley Fail]", 2000);
                        }
                    });*/
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            /*setResultAPI(-1, "Gagal Melakukan Changetrip [Volley]", null);
            sendEmptyMessage(1);*/
            /*runOnUiThread(new Runnable() {

                public void run() {
                    App.utility.showDialog(-1, "Error Change Trip", "Gagal Melakukan Changetrip [Volley]", 2000);
                }
            });*/
        }
    }

    public void changeTrip(int i, int i2, int i3, double d, double d2) throws Exception {
        if (ReaderUtils.isNetworkAvailable(context)) {
            Parameter parameter = new Parameter();
            parameter.Add("hwid", this.data.get(Data.Key.BUS_HWID));
            parameter.Add("route_code", this.data.get(Data.Key.BUS_ROUTE_CODE));
            parameter.Add("operational", this.data.get(Data.Key.OPERATIONAL_CODE));
            parameter.Add("trip", this.data.get(Data.Key.BUS_TRIP_COUNTER));
            parameter.Add("datetime", Utility.getDateTimeWithFormat("yyyy-MM-dd HH:mm:ss"));
            parameter.Add("in", Integer.valueOf(i));
            parameter.Add("out", Integer.valueOf(i2));
            parameter.Add("on", Integer.valueOf(i3));
            parameter.Add("lat", Double.valueOf(d));
            parameter.Add("long", Double.valueOf(d2));

            try {
                this.network.Post(parameter, "change_trip", new Network.ResponseCallback() {

                    @Override
                    public void onSuccess(String str) {
                        try {
                            JSONObject jSONObject = new JSONObject(str);
                            if (jSONObject.getBoolean("status")) {
                                /*isCanChangetrip = false;
                                PassengerAndLocationLogRunnable passengerAndLocationLogRunnable = PassengerAndLocationLogRunnable.this;*/
                                writeLog("SensorLog", "Success : " + str);
                                data.save(Data.Key.BUS_FARE_AMOUNT, jSONObject.getString("fare"));
                                data.save(Data.Key.BUS_TRIP_COUNTER, jSONObject.getString("trip"));
                                data.save(Data.Key.BUS_ROUTE_CODE, jSONObject.getString("route_code"));
                                data.save(Data.Key.BUS_ROUTE_NAME, jSONObject.getString("route"));
                                data.save(Data.Key.OPERATIONAL_CODE, jSONObject.getString("operational"));
                                data.save(Data.Key.OPERATIONAL_START, jSONObject.getString("start"));
                                data.save(Data.Key.OPERATIONAL_END, jSONObject.getString("end"));
                                Location location = new Location("locationStart");
                                location.setLatitude(jSONObject.getDouble("start_lat"));
                                location.setLongitude(jSONObject.getDouble("start_lng"));
                                data.save(Data.Key.LAT_START, jSONObject.getString("start_lat"));
                                data.save(Data.Key.LONG_START, jSONObject.getString("start_lng"));
                                data.save(Data.Key.LOCATION_START, gson.toJson(location));
                                Location location2 = new Location("locationEnd");
                                location2.setLatitude(jSONObject.getDouble("end_lat"));
                                location2.setLongitude(jSONObject.getDouble("end_lng"));
                                data.save(Data.Key.LAT_END, jSONObject.getString("end_lat"));
                                data.save(Data.Key.LONG_END, jSONObject.getString("end_lng"));
                                data.save(Data.Key.LOCATION_END, gson.toJson(location2));
                                targetLocation = location2;
                                // resetCounter();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailed(String str) {
                        if (str.contains("500")) {
                            Toast.makeText(MainActivity.instance, "Unexpected response code 500 change trip", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.instance, str, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                writeLog("errorChangeTrip", e.getMessage());
            }
        }
    }

    public void sendLog(int i, int i2, int i3, double d, double d2) throws Exception {
        if (!ReaderUtils.isNetworkAvailable(context)) {
            isReadyToSendLog = true;
            return;
        }

        Parameter parameter = new Parameter();
        parameter.Add("hwid", this.data.get(Data.Key.BUS_HWID));
        parameter.Add("operational", this.data.get(Data.Key.OPERATIONAL_CODE));
        parameter.Add("route", this.data.get(Data.Key.BUS_ROUTE_NAME));
        parameter.Add("trip", this.data.get(Data.Key.BUS_TRIP_COUNTER));
        parameter.Add("in", Integer.valueOf(i));
        parameter.Add("out", Integer.valueOf(i2));
        parameter.Add("on", Integer.valueOf(i3));
        parameter.Add("lat", Double.valueOf(d));
        parameter.Add("long", Double.valueOf(d2));
        parameter.Add("date", Utility.getDateTimeWithFormat("yyyy-MM-dd HH:mm:ss"));
        parameter.Add("passenger_counter", this.data.get(Data.Key.PASSENGER_TAP));
        parameter.Add("passenger_counter_kartu", this.data.get(Data.Key.PASSENGER_TAP_KARTU));
        parameter.Add("passenger_counter_qris", this.data.get(Data.Key.PASSENGER_TAP_QRIS));
        parameter.Add("passenger_counter_last", this.data.get(Data.Key.PASSENGER_TAP_LAST));

        this.network.PostLog(parameter, "sensor_log", new Network.ResponseCallback() {
            /* class com.net2software.mobile.busvalidator.http.API.AnonymousClass3 */

            @Override // com.net2software.mobile.busvalidator.http.Network.ResponseCallback
            public void onFailed(String str) {
                isReadyToSendLog = true;
            }

            @Override // com.net2software.mobile.busvalidator.http.Network.ResponseCallback
            public void onSuccess(String str) {
                try {
                    if (new JSONObject(str).getBoolean("status")) {

                    }
                    isReadyToSendLog = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    writeLog("RES_LATLONG", e.getMessage());
                    isReadyToSendLog = true;
                }
            }
        });
    }

    public void sendLogLocation(final ArrayList<LocationModelRequest> locationModel) {
        String data = new Gson().toJson(locationModel);
        JSONObject dataJsonFinal = new JSONObject();
        try {
            JSONArray jArr = new JSONArray(data);
            dataJsonFinal.put("data", jArr);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        HashMap<String, String> params = getStringHashMap(locationModel);

        try {
            this.network.PostArray(params, "/sensor_log", new Network.ResponseCallback() {
                @Override
                public void onSuccess(String str) {
                    try {
                        if (new JSONObject(str).getBoolean("status")) {
                            allLocationDataAPI.clear();
                            locationController.deleteAll();
                            isReadyToSendLog = true;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.d("RES_LATLONG", e.getMessage());
                        isReadyToSendLog = true;
                    }
                }

                @Override
                public void onFailed(String str) {
                    isReadyToSendLog = true;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            writeLog("Error Log Location", e.getMessage());
        }
    }

    public void sendLogLocationRaw(ArrayList<LocationModelRequest> locationModelRequests, List<LocationModel> locationModels) {
        if (!ReaderUtils.isNetworkAvailable(context)) {
            isReadyToSendLog = true;
            return;
        }
        ArrayList<LocationModel> localLocationModels = new ArrayList<>(locationModels);
        String data = new Gson().toJson(locationModelRequests);
        JSONObject dataJsonFinal = new JSONObject();
        try {
            JSONArray jArr = new JSONArray(data);
            dataJsonFinal.put("data", jArr);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        try {
            this.network.PostArrayRaw(dataJsonFinal, "sensor_log_raw", new Network.ResponseCallback() {
                @Override
                public void onSuccess(String str) {
                    try {
                        if (new JSONObject(str).getBoolean("status")) {
                            Log.d("LocationData", "Send data successfully");
                            Log.d("LocationData", "LocationModels : " + localLocationModels.size());
                            //backupAndRemoveDataToJson(localLocationModels, false);
                            allLocationDataAPI.clear();
                            locationController.deleteAll();
                        }
                        isReadyToSendLog = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        writeLog("RES LATLONG", "Exception : " + e.getMessage());
                        Log.d("LocationData", "Exception: " + e.getMessage());
                        //backupAndRemoveDataToJson(localLocationModels, true);
                        isReadyToSendLog = true;
                    }
                }

                @Override
                public void onFailed(String str) {
                    Log.d("LocationData", "Failed: " + str);
                    Log.d("LocationData", "LocationModels : " + localLocationModels.size());
                    //backupAndRemoveDataToJson(localLocationModels, true);
                    isReadyToSendLog = true;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            writeLog("RES LATLONG", "Error : " + e.getMessage());
            Log.d("LocationData", "Error: " + e.getMessage());
            //backupAndRemoveDataToJson(localLocationModels, true);
            isReadyToSendLog = true;
        }
    }

    public void sendAttendance(String name, String code, boolean status, String id) {
        Parameter parameter = new Parameter();
        parameter.Add("hwid", this.data.get(Data.Key.BUS_HWID));
        parameter.Add("operational", this.data.get(Data.Key.OPERATIONAL_CODE));
        parameter.Add("route", this.data.get(Data.Key.BUS_ROUTE_CODE));
        parameter.Add("c_crew", code);
        parameter.Add("n_crew", name);
        parameter.Add("timestamp", Utility.getDateTimeWithFormat("yyyy-MM-dd HH:mm:ss"));
        parameter.Add("latitude", locationTrack.getLatitude());
        parameter.Add("longitude", locationTrack.getLongitude());
        //parameter.Add("status", status);
        parameter.Add("unique_id", id);

        String statusAbsen;
        if (status) {
            statusAbsen = "Masuk";
        } else {
            statusAbsen = "Keluar";
        }

        setMsgLoading("Send Attendance");

        this.network.PostJson(parameter, "/attendance", new Network.ResponseCallback() {
            @Override
            public void onSuccess(String str) {
                App.lastDetectTime = getTime();
                App.isCanReset = true;

                try {
                    if (!Objects.equals(code, "TIKET")) {
                        if (new JSONObject(str).getBoolean("status")) {
                            stringBuilder = new StringBuilder();
                            stringBuilder.append("Sukses Absen ").append(statusAbsen).append("\nNama : ").append(name).append("\n\nSilahkan lanjutkan perjalanan. Utamakan keselamatan berkendara.");

                            setResultAPI(0, 0, "Berhasil \n[" + "ABSENSI" + "]", stringBuilder);
                        } else {
                            stringBuilder = new StringBuilder();
                            stringBuilder.append("\nCode : ").append(new JSONObject(str).getString("code")).append("\nCaused : ").append(new JSONObject(str).getString("message"));

                            setResultAPI(1, -1, "Gagal \n[" + "ABSENSI" + "]", stringBuilder);
                        }
                    } else {
                        if (new JSONObject(str).getBoolean("status")) {
                            stringBuilder = new StringBuilder();
                            stringBuilder.append("Sukses ").append(statusAbsen).append("\nRute : ").append(code).append("\n\nSelamat menikmati perjalanan anda, terimakasih.");

                            setResultAPI(0, 0, "Berhasil \n[" + "QRIS" + "]", stringBuilder);
                        } else {
                            stringBuilder = new StringBuilder();
                            stringBuilder.append("\nCode : ").append(new JSONObject(str).getString("code")).append("\nCaused : ").append(new JSONObject(str).getString("message"));

                            setResultAPI(1, -1, "Gagal \n[" + "QRIS" + "]", stringBuilder);
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    writeLog("SendAttendance", "Exception : " + e.getMessage());
                    setResultAPI(1, -1, e.toString(), null);
                }
            }

            @Override
            public void onFailed(String str) {
                App.lastDetectTime = getTime();
                App.isCanReset = true;

                BARCODE = "";
                LAST_QR = "";

                setResultAPI(1, -1, str, null);
            }
        });
    }

    public void saveQRTicket() {
        TransactionModel transactionModel = new TransactionModel();
        transactionModel.setId(String.valueOf(getTime()));
        transactionModel.setTrxCode("");
        transactionModel.setTrxId("");
        transactionModel.setTrxDate("");
        transactionModel.setHwid(data.get(Data.Key.BUS_HWID));
        transactionModel.setOperationalCode(data.get(Data.Key.OPERATIONAL_CODE));
        transactionModel.setRouteName(data.get(Data.Key.BUS_ROUTE_NAME));
        transactionModel.setTrip(data.get(Data.Key.BUS_TRIP_COUNTER));
        transactionModel.setMid("");
        transactionModel.setTid("");
        transactionModel.setPaymentMethod("");
        transactionModel.setSerialNumber("");
        transactionModel.setUid("");
        transactionModel.setAmount("");
        transactionModel.setBalanceAfter("");
        transactionModel.setBalanceBefore("");
        transactionModel.setLatitude(String.valueOf(BaseActivity.locationTrack.getLatitude()));
        transactionModel.setLongitude(String.valueOf(BaseActivity.locationTrack.getLongitude()));
        transactionModel.setBankName("");
        transactionModel.setStatus("false");
        transactionModel.setCurrentCounter(data.get(Data.Key.PASSENGER_TAP));
        transactionModel.setTapin("true");

        //localService.sendTrx(transactionModel);
    }

    public void sendQRTicket(String bookingCode) {
        Parameter parameter = new Parameter();
        parameter.Add("hwid", this.data.get(Data.Key.BUS_HWID));
        parameter.Add("operational", this.data.get(Data.Key.OPERATIONAL_CODE));
        parameter.Add("route", this.data.get(Data.Key.BUS_ROUTE_CODE));
        parameter.Add("trip", this.data.get(Data.Key.BUS_TRIP_COUNTER));
        parameter.Add("mid_partner", "");
        parameter.Add("tid_partner", "");
        parameter.Add("trx_code", bookingCode);
        parameter.Add("trx_id", data.get(Data.Key.BUS_HWID) + getTime());
        parameter.Add("trx_date", Utility.getDateTimeWithFormat("yyyy-MM-dd HH:mm:ss"));
        parameter.Add("uid", "");
        parameter.Add("balance_before", "");
        parameter.Add("amount", data.get(Data.Key.BUS_FARE_AMOUNT));
        parameter.Add("balance_after", "");
        parameter.Add("lat", locationTrack.getLatitude());
        parameter.Add("long", locationTrack.getLongitude());

        String amount = data.get(Data.Key.BUS_FARE_AMOUNT);

        setMsgLoading("Send Ticket");

        this.network.PostJson(parameter, "trx_ticket", new Network.ResponseCallback() {
            @Override
            public void onSuccess(String str) {
                App.lastDetectTime = getTime();
                App.isCanReset = true;

                try {
                    if (new JSONObject(str).getBoolean("status")) {
                        if (new JSONObject(str).getString("code").equals("1010")) {
                            stringBuilder = new StringBuilder();
                            stringBuilder.append("\nCode : ").append(new JSONObject(str).getString("code")).append("\nCaused : ").append(new JSONObject(str).getString("message"));

                            setResultAPI(1, -1, "Transaksi Gagal \n[" + "TIKET" + "]", stringBuilder);
                            return;
                        }

                        String counter = data.get(Data.Key.PASSENGER_TAP);
                        if (counter == null || counter.isEmpty()) {
                            counter = "0";
                        }

                        String counterQris = data.get(Data.Key.PASSENGER_TAP_QRIS);
                        if (counterQris == null || counterQris.isEmpty()) {
                            counterQris = "0";
                        }

                        data.save(Data.Key.PASSENGER_TAP_QRIS, String.valueOf(Integer.parseInt(counterQris) + 1));
                        data.save(Data.Key.PASSENGER_TAP, String.valueOf(Integer.parseInt(counter) + 1));

                        stringBuilder = new StringBuilder();
                        stringBuilder.append("\nTarif: Rp ").append(amount).append("\nSelamat menikmati perjalanan Anda.");

                        LAST_QR = bookingCode;

                        setResultAPI(0, 0, "Transaksi Berhasil \n[" + "TIKET" + "]", stringBuilder);
                    } else {
                        stringBuilder = new StringBuilder();
                        stringBuilder.append("Code : ").append(new JSONObject(str).getString("code")).append("\nCaused : ").append(new JSONObject(str).getString("message"));

                        setResultAPI(1, -1, "Transaksi Gagal \n[" + "TIKET" + "]", stringBuilder);
                    }
                } catch (Exception e) {
                    App.lastDetectTime = getTime();
                    App.isCanReset = true;

                    e.printStackTrace();
                    writeLog("SendTicket", "Exception : " + e.getMessage());
                    setResultAPI(1, -1, e.toString(), null);
                }
            }

            @Override
            public void onFailed(String str) {
                App.lastDetectTime = getTime();
                App.isCanReset = true;

                writeLog("isQRTimeout", App.isQRTimeout.toString());

                if (App.isQRTimeout) {
                    setResultAPI(1, -1, "Response Time Out. Silakan scan kembali tiket anda", null);
                } else {
                    setResultAPI(1, -1, str, null);
                }
            }
        });
    }

    public void sendLogFile(int maxRetry, int chunkSize) {
        File snapshot = FileLog.createLogSnapshot();
        if (snapshot == null || !snapshot.exists()) {
            Log.e(TAG, "sendLogFile: snapshot tidak ditemukan / gagal dibuat");
            return;
        }

        try {
            new Thread(() -> {
                networkRetrofit.PostMultipartFormChunked(
                        snapshot,
                        "Damri",
                        "store-chunk",
                        maxRetry,
                        chunkSize,
                        new NetworkRetrofit.ResponseCallback() {
                            @Override
                            public void onFailed(String str) {
                                Log.e(TAG, "UPLOAD_FAIL: " + str);
                                AppUtils.deleteSnapshot(snapshot);

                            }

                            @Override
                            public void onSuccess(String str) {
                                Log.d(TAG, "UPLOAD_SUCCESS: " + str);
                                AppUtils.deleteSnapshot(snapshot);

                            }
                        }
                );
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
            writeLog("SendLogFile", "Exception : " + e.getMessage());
            AppUtils.deleteSnapshot(snapshot);
        }
    }

    public interface OnStateChangedListener {

        void onRequest(String message);

        void onSuccess(BaseDataModel result);

        void onFailed(BaseDataModel result);

        void onLoading(String message);
    }

    private void getDataLog() {
        try {
            if (!(App.isDamri() || App.isDamriQ6())) return;

            data.save(Data.Key.TAP_REMOTE, null);
            data.save(Data.Key.TAP_QRIS_REMOTE, null);
            data.save(Data.Key.TAP_KARTU_REMOTE, null);
            data.save(Data.Key.TAP_QRIS_REMOTE, null);
            data.save(Data.Key.TAP_OUT_REMOTE, null);
            data.save(Data.Key.TAP_LAST_REMOTE, null);
            //data.save(Data.Key.TAP_SUCCESS_REMOTE, null);
            //data.save(Data.Key.TAP_FAILED_REMOTE, null);

            Parameter parameter = new Parameter();
            parameter.AddJson("hwid", this.data.get(Data.Key.BUS_HWID));
            parameter.AddJson("date", Utility.getDateTimeWithFormat(getTime(), "yyyy-MM-dd"));

            this.network.PostArrayRaw(parameter.getJson(), "get_data_log", new Network.ResponseCallback(){
                @Override
                public void onFailed(String str) {
                    writeLog("GetDataLog[2]", str);
                }

                @Override
                public void onSuccess(String str) {
                    try {
                        JSONObject res = new JSONObject(str);
                        if (res.getBoolean("status")) {
                            if (!res.isNull("data")) {
                                JSONObject data = res.getJSONObject("data");
                                String tap = data.getString("tap_remote");
                                String tapQris = data.getString("tap_qris_remote");
                                String tapKartu = data.getString("tap_kartu_remote");
                                String tapOut = data.getString("tap_out_remote");
                                String tapLast = data.getString("tap_last_remote");
                                //String tapSuccess = data.getString("tap_success");
                                //String tapFail = data.getString("tap_fail");

                                ApiService.this.data.save(Data.Key.TAP_REMOTE, tap);
                                ApiService.this.data.save(Data.Key.TAP_QRIS_REMOTE, tapQris);
                                ApiService.this.data.save(Data.Key.TAP_KARTU_REMOTE, tapKartu);
                                ApiService.this.data.save(Data.Key.TAP_OUT_REMOTE, tapOut);
                                ApiService.this.data.save(Data.Key.TAP_LAST_REMOTE, tapLast);
                                //data.save(Data.Key.TAP_SUCCESS_REMOTE, tapSuccess);
                                //data.save(Data.Key.TAP_FAILED_REMOTE, tapFail);
                                writeLog("GetDataLog[1]", "Success");
                            }

                        }
                    }catch (JSONException e) {
                        e.printStackTrace();
                        writeLog("GetDataLog[3]", e.getMessage());
                    }

                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            writeLog("GetDataLog[4]", e.getMessage());
        }
    }

    private void saveDataLog() {
        try {
            if (!(App.isDamri() || App.isDamriQ6())) return;
           /* int failedData;
            List<TransactionModel> allData = transactionController.getAllData();
            if (allData != null) {
                failedData = allData.size();
                data.save(Data.Key.DATA_FAILED, String.valueOf(failedData));
            } else {
                String buff = "0";
                if(data.get(Data.Key.DATA_FAILED) != null){
                    buff = data.get(Data.Key.DATA_FAILED);
                }
                failedData = Integer.parseInt(buff);
            }*/

            data.save(Data.Key.PASSENGER_TAP_PENDING, String.valueOf(transactionController.getDataSize()));

            SaveDataLogModel dataLog = new SaveDataLogModel();
            dataLog.setHwid(data.get(Data.Key.BUS_HWID));
            dataLog.setDate(Utility.getDateTimeWithFormat(getTime(), "yyyy-MM-dd"));
            dataLog.setTerminalCode(data.get(Data.Key.TERMINAL_CODE));
            //dataLog.setTerminalId(data.get(Data.Key.TERMINAL_ID));
            dataLog.setcBus(data.get(Data.Key.BUS_CODE));
            dataLog.setPlat(data.get(Data.Key.NOPOL));
            //dataLog.setNoBody(data.get(Data.Key.NO_BODY));
            //dataLog.setBusType(data.get(Data.Key.BUS_TYPE));
            //dataLog.setTerminalType(data.get(Data.Key.TERMINAL_TYPE));
            dataLog.setTransportationType(Integer.parseInt(data.get(Data.Key.TRANSPORTATION_TYPE)));
            dataLog.setTap(Integer.parseInt(data.get(Data.Key.PASSENGER_TAP)));
            dataLog.setTapQris(Integer.parseInt(data.get(Data.Key.PASSENGER_TAP_QRIS)));
            dataLog.setTapKartu(Integer.parseInt(data.get(Data.Key.PASSENGER_TAP_KARTU)));
            dataLog.setTapPending(Integer.parseInt(data.get(Data.Key.PASSENGER_TAP_PENDING)));
            dataLog.setTapOut(Integer.parseInt(data.get(Data.Key.PASSENGER_TAP_OUT)));
            dataLog.setTapLast(Integer.parseInt(data.get(Data.Key.PASSENGER_TAP_LAST)));
            //dataLog.setTapSuccess(Integer.parseInt(data.get(Data.Key.DATA_SUCCESS)));
            //dataLog.setTapFail(failedData);
            writeLog("SaveDataLog", dataLog.toString());

            network.PostArrayRaw(dataLog.toJson(), "save_data_log", new Network.ResponseCallback() {
                @Override
                public void onFailed(String str) {
                    writeLog("SaveDataLog[4]", "Failed save data log, " + str);
                }

                @Override
                public void onSuccess(String str) {
                    try {
                        JSONObject res = new JSONObject(str);
                        if (res.getBoolean("status")) {
                            writeLog("SaveDataLog[1]", "Success save: " + dataLog);
                        } else {
                            writeLog("SaveDataLog[2]", "Failed save data log, " + res.getString("msg"));
                        }
                    } catch (JSONException e) {
                            writeLog("SaveDataLog[3]", "Failed save data log, " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            writeLog("SaveDataLogErr", e.getMessage());
        }
    }
}
