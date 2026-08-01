package com.net2software.busvalidator.biskita.data.remote;

import static com.net2software.busvalidator.biskita.tools.AppUtils.writeLog;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.net2software.busvalidator.biskita.App;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import kotlin.text.Charsets;

public class Network {
    private final String baseUrlAPI = App.getBaseUrl();
    private final String baseUrlQrisCpm = App.getBaseUrlQrisCPM();
    private final int timeOut = 0;
    private final DefaultRetryPolicy defaultRetryPolicy = new DefaultRetryPolicy(timeOut, -1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
    private final int timeOutCustom = 60000;
    private final DefaultRetryPolicy timoutdefaultRetryPolicy = new DefaultRetryPolicy(timeOutCustom, -1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
    private Context context;
    private RequestQueue queue;
    private boolean isTimeout = false;

    public Network(Context context2) {
        this.context = context2;

    }

    public void Post(final Parameter parameter, String str, final ResponseCallback responseCallback) {
        try {
            if (!str.equals("/sensor_log_raw")) {
                writeLog("API", "Method : POST\nUrl : " + baseUrlAPI + str);
                writeLog("REQUEST", String.valueOf(parameter.getJson()));
            }

            StringRequest r11 = new StringRequest(Request.Method.POST, baseUrlAPI + str, new Response.Listener<String>() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass1 */

                public void onResponse(String str) {
                    if (!str.equals("/sensor_log_raw")) {
                        writeLog("RESPONSE", str);
                    }
                    if (!str.isEmpty()) {
                        responseCallback.onSuccess(str);
                    } else {
                        responseCallback.onSuccess("{\"status\":\"false\",\"code\":\"0099\",\"msg\":\"Error on response\"}");
                    }
                }
            }, new Response.ErrorListener() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass2 */

                @Override // com.android.volley.Response.ErrorListener
                public void onErrorResponse(VolleyError volleyError) {
                    String errorMessage;
                    try {
                        if (volleyError instanceof TimeoutError) {
                            errorMessage = "Response Timeout, periksa koneksi internet Anda.";
                        } else if (volleyError instanceof NoConnectionError) {
                            errorMessage = "Tidak ada koneksi internet, silakan periksa jaringan atau paket data Anda.";
                        } else if (volleyError instanceof AuthFailureError) {
                            errorMessage = "Authentication Failure";
                        } else if (volleyError instanceof ServerError) {
                            int statusCode = 0;
                            NetworkResponse networkResponse = volleyError.networkResponse;
                            if (networkResponse != null && networkResponse.data != null) {
                                statusCode = networkResponse.statusCode;
                            }
                            errorMessage = "Server Error, Kode " + statusCode;
                        } else if (volleyError instanceof ParseError) {
                            int statusCode = 0;
                            NetworkResponse networkResponse = volleyError.networkResponse;
                            if (networkResponse != null && networkResponse.data != null) {
                                statusCode = networkResponse.statusCode;
                            }
                            errorMessage = "Parse Error, Kode " + statusCode;
                        } else if (volleyError instanceof NetworkError) {
                            errorMessage = "Terjadi kesalahan jaringan, periksa kembali koneksi internet Anda.";
                        } else {
                            errorMessage = volleyError.getMessage();
                        }

                        writeLog("RESPONSE", errorMessage);

                        // Callback onFailed dengan pesan yang telah disesuaikan
                        responseCallback.onFailed(errorMessage);
                    } catch (Exception e) {
                        e.printStackTrace();
                        writeLog("RESPONSE", e.toString());
                        responseCallback.onFailed(e.getMessage());
                    }
                }
            }) {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass3 */

                @Override // com.android.volley.Request
                public byte[] getBody() {
                    return parameter.getParam().getBytes();
                }

                @Override // com.android.volley.Request
                public Map<String, String> getHeaders() {
                    HashMap hashMap = new HashMap();
                    hashMap.put("Content-Type", "application/x-www-form-urlencoded");
                    return hashMap;
                }
            };
            if (this.queue == null) {
                this.queue = Volley.newRequestQueue(this.context);
            }
            r11.setRetryPolicy(defaultRetryPolicy);
            this.queue.add(r11);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void PostLog(final Parameter parameter, String str, final ResponseCallback responseCallback) {
        try {
            Log.d("API", "Method : POST\nUrl : " + baseUrlAPI + str);
            Log.d("REQUEST", String.valueOf(parameter.getJson()));

            StringRequest r11 = new StringRequest(Request.Method.POST, baseUrlAPI + str, new Response.Listener<String>() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass1 */

                public void onResponse(String str) {
                    Log.d("RESPONSE", str);
                    if (!str.isEmpty()) {
                        responseCallback.onSuccess(str);
                    } else {
                        responseCallback.onSuccess("{\"status\":\"false\",\"code\":\"0099\",\"msg\":\"Error on response\"}");
                    }
                }
            }, new Response.ErrorListener() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass2 */

                @Override // com.android.volley.Response.ErrorListener
                public void onErrorResponse(VolleyError volleyError) {
                    String errorMessage;
                    try {
                        if (volleyError instanceof TimeoutError) {
                            errorMessage = "Response Timeout, periksa koneksi internet Anda.";
                        } else if (volleyError instanceof NoConnectionError) {
                            errorMessage = "Tidak ada koneksi internet, silakan periksa jaringan atau paket data Anda.";
                        } else if (volleyError instanceof AuthFailureError) {
                            errorMessage = "Authentication Failure";
                        } else if (volleyError instanceof ServerError) {
                            int statusCode = 0;
                            NetworkResponse networkResponse = volleyError.networkResponse;
                            if (networkResponse != null && networkResponse.data != null) {
                                statusCode = networkResponse.statusCode;
                            }
                            errorMessage = "Server Error, Kode " + statusCode;
                        } else if (volleyError instanceof ParseError) {
                            int statusCode = 0;
                            NetworkResponse networkResponse = volleyError.networkResponse;
                            if (networkResponse != null && networkResponse.data != null) {
                                statusCode = networkResponse.statusCode;
                            }
                            errorMessage = "Parse Error, Kode " + statusCode;
                        } else if (volleyError instanceof NetworkError) {
                            errorMessage = "Terjadi kesalahan jaringan, periksa kembali koneksi internet Anda.";
                        } else {
                            errorMessage = volleyError.getMessage();
                        }

                        writeLog("RESPONSE", errorMessage);

                        // Callback onFailed dengan pesan yang telah disesuaikan
                        responseCallback.onFailed(errorMessage);
                    } catch (Exception e) {
                        e.printStackTrace();
                        writeLog("RESPONSE", e.toString());
                        responseCallback.onFailed(e.getMessage());
                    }
                }
            }) {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass3 */

                @Override // com.android.volley.Request
                public byte[] getBody() {
                    return parameter.getParam().getBytes();
                }

                @Override // com.android.volley.Request
                public Map<String, String> getHeaders() {
                    HashMap hashMap = new HashMap();
                    hashMap.put("Content-Type", "application/x-www-form-urlencoded");
                    return hashMap;
                }
            };
            if (this.queue == null) {
                this.queue = Volley.newRequestQueue(this.context);
            }
            r11.setRetryPolicy(defaultRetryPolicy);
            this.queue.add(r11);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void PostTrx(final Parameter parameter, String str, final ResponseTrxCallback responseCallback) {
        try {
            writeLog("API", "Method : POST\nUrl : " + baseUrlAPI + str);
            writeLog("REQUEST", String.valueOf(parameter.getJson()));

            StringRequest r11 = new StringRequest(Request.Method.POST, baseUrlAPI + str, new Response.Listener<String>() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass1 */

                public void onResponse(String str) {
                    writeLog("RESPONSE", str);
                    if (!str.isEmpty()) {
                        responseCallback.onSuccess(str);
                    } else {
                        responseCallback.onSuccess("{\"status\":\"false\",\"code\":\"0099\",\"msg\":\"Error on response\"}");
                    }
                }
            }, new Response.ErrorListener() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass2 */

                @Override // com.android.volley.Response.ErrorListener
                public void onErrorResponse(VolleyError volleyError) {
                    String errorMessage;
                    boolean isNoConnection = false;
                    try {
                        if (volleyError instanceof TimeoutError) {
                            isNoConnection = true;
                            errorMessage = "Response Timeout, periksa koneksi internet Anda.";
                        } else if (volleyError instanceof NoConnectionError) {
                            isNoConnection = true;
                            errorMessage = "Tidak ada koneksi internet, silakan periksa jaringan atau paket data Anda.";
                        } else if (volleyError instanceof AuthFailureError) {
                            errorMessage = "Authentication Failure";
                        } else if (volleyError instanceof ServerError) {
                            int statusCode = 0;
                            NetworkResponse networkResponse = volleyError.networkResponse;
                            if (networkResponse != null && networkResponse.data != null) {
                                statusCode = networkResponse.statusCode;
                            }
                            errorMessage = "Server Error, Kode " + statusCode;
                        } else if (volleyError instanceof ParseError) {
                            int statusCode = 0;
                            NetworkResponse networkResponse = volleyError.networkResponse;
                            if (networkResponse != null && networkResponse.data != null) {
                                statusCode = networkResponse.statusCode;
                            }
                            errorMessage = "Parse Error, Kode " + statusCode;
                        } else if (volleyError instanceof NetworkError) {
                            errorMessage = "Terjadi kesalahan jaringan, periksa kembali koneksi internet Anda.";
                        } else {
                            errorMessage = volleyError.getMessage() != null ? volleyError.getMessage() : "Something went wrong";
                        }

                        writeLog("RESPONSE", errorMessage);

                        // Callback onFailed dengan pesan yang telah disesuaikan
                        responseCallback.onFailed(errorMessage, isNoConnection);
                    } catch (Exception e) {
                        e.printStackTrace();
                        writeLog("RESPONSE", e.toString());
                        responseCallback.onFailed(e.getMessage(), false);
                    }
                }
            }) {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass3 */

                @Override // com.android.volley.Request
                public byte[] getBody() {
                    return parameter.getParam().getBytes();
                }

                @Override // com.android.volley.Request
                public Map<String, String> getHeaders() {
                    HashMap hashMap = new HashMap();
                    hashMap.put("Content-Type", "application/x-www-form-urlencoded");
                    return hashMap;
                }
            };
            if (this.queue == null) {
                this.queue = Volley.newRequestQueue(this.context);
            }
            r11.setRetryPolicy(defaultRetryPolicy);
            this.queue.add(r11);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void PostJson(final Parameter parameter, String str, final ResponseCallback responseCallback) {
        try {

            //Log.d("RequestJson", jsonObject.toString());
            writeLog("API", "Method : POST\nUrl : " + baseUrlAPI + str);
            writeLog("REQUEST", String.valueOf(parameter.getJson()));

            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, baseUrlAPI + str, parameter.getJson(),
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            if (response != null) {
                                App.isQRTimeout = false;
                                writeLog("RESPONSE", String.valueOf(response));
                                responseCallback.onSuccess(response.toString());
                            } else {
                                App.isQRTimeout = false;

                                responseCallback.onSuccess("{\"status\":\"false\",\"code\":\"0099\",\"msg\":\"Error on response\"}");
                            }
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError volleyError) {
                    String errorMessage;
                    /*if (volleyError != null && volleyError.networkResponse.data != null) {
                        Log.d("ErrorResp", new String(volleyError.networkResponse.data, Charsets.UTF_8));
                    }*/
                    try {
                        if (volleyError instanceof TimeoutError) {
                            App.isQRTimeout = true;
                            errorMessage = "Response Timeout, periksa koneksi internet Anda.";
                        } else if (volleyError instanceof NoConnectionError) {
                            errorMessage = "Tidak ada koneksi internet, silakan periksa jaringan atau paket data Anda.";
                        } else if (volleyError instanceof AuthFailureError) {
                            errorMessage = "Authentication Failure";
                        } else if (volleyError instanceof ServerError) {
                            int statusCode = 0;
                            NetworkResponse networkResponse = volleyError.networkResponse;
                            if (networkResponse != null && networkResponse.data != null) {
                                statusCode = networkResponse.statusCode;
                            }
                            errorMessage = "Server Error, Kode " + statusCode;
                        } else if (volleyError instanceof ParseError) {
                            int statusCode = 0;
                            NetworkResponse networkResponse = volleyError.networkResponse;
                            if (networkResponse != null && networkResponse.data != null) {
                                statusCode = networkResponse.statusCode;
                            }
                            errorMessage = "Parse Error, Kode " + statusCode;
                        } else if (volleyError instanceof NetworkError) {
                            errorMessage = "Terjadi kesalahan jaringan, periksa kembali koneksi internet Anda.";
                        } else {
                            errorMessage = volleyError.getMessage();
                        }

                        // Callback onFailed dengan pesan yang telah disesuaikan
                        responseCallback.onFailed(errorMessage);

                    } catch (Exception e) {
                        e.printStackTrace();
                        responseCallback.onFailed(e.getMessage());
                    }
                }
            }) {
                @Override
                public Map<String, String> getHeaders() {
                    HashMap<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    return headers;
                }
            };

            if (this.queue == null) {
                this.queue = Volley.newRequestQueue(this.context);
            }
            jsonObjectRequest.setRetryPolicy(timoutdefaultRetryPolicy);
            this.queue.add(jsonObjectRequest);

        } catch (Exception e) {
            e.printStackTrace();
            responseCallback.onFailed(e.getMessage());
        }
    }

    public void PostJson(final Parameter parameter, String str, final ResponseCallbackJson responseCallback) {
        try {
            //Log.d("RequestJson", jsonObject.toString());
            writeLog("API", "Method : POST\nUrl : " + baseUrlAPI + str);
            writeLog("REQUEST", String.valueOf(parameter.getJson()));

            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, baseUrlAPI + str, parameter.getJson(),
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            if (response != null) {
                                writeLog("RESPONSE", String.valueOf(response));
                                responseCallback.onSuccess(response);
                            }
                            /*else {
                                responseCallback.onSuccess("{\"status\":\"false\",\"code\":\"0099\",\"msg\":\"Error on response\"}");
                            }*/
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError volleyError) {
                    String errorMessage;
                    Log.d("ErrorResp", new String(volleyError.networkResponse.data, Charsets.UTF_8));
                    try {
                        if (volleyError instanceof TimeoutError) {
                            errorMessage = "Response Timeout, periksa koneksi internet Anda.";
                        } else if (volleyError instanceof NoConnectionError) {
                            errorMessage = "Tidak ada koneksi internet, silakan periksa jaringan atau paket data Anda.";
                        } else if (volleyError instanceof AuthFailureError) {
                            errorMessage = "Authentication Failure";
                        } else if (volleyError instanceof ServerError) {
                            int statusCode = 0;
                            NetworkResponse networkResponse = volleyError.networkResponse;
                            if (networkResponse != null && networkResponse.data != null) {
                                statusCode = networkResponse.statusCode;
                            }
                            errorMessage = "Server Error, Kode " + statusCode;
                        } else if (volleyError instanceof ParseError) {
                            int statusCode = 0;
                            NetworkResponse networkResponse = volleyError.networkResponse;
                            if (networkResponse != null && networkResponse.data != null) {
                                statusCode = networkResponse.statusCode;
                            }
                            errorMessage = "Parse Error, Kode " + statusCode;
                        } else if (volleyError instanceof NetworkError) {
                            errorMessage = "Terjadi kesalahan jaringan, periksa kembali koneksi internet Anda.";
                        } else {
                            errorMessage = volleyError.getMessage();
                        }

                        // Callback onFailed dengan pesan yang telah disesuaikan
                        responseCallback.onFailed(errorMessage);

                    } catch (Exception e) {
                        e.printStackTrace();
                        responseCallback.onFailed(e.getMessage());
                    }
                }
            }) {
                @Override
                public Map<String, String> getHeaders() {
                    HashMap<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    return headers;
                }
            };

            if (this.queue == null) {
                this.queue = Volley.newRequestQueue(this.context);
            }
            jsonObjectRequest.setRetryPolicy(defaultRetryPolicy);
            this.queue.add(jsonObjectRequest);

        } catch (Exception e) {
            e.printStackTrace();
            responseCallback.onFailed(e.getMessage());
        }
    }

    public void PostJsonQris(final Parameter parameter, String authorization, String xPartner, String signature, String endpoint, final ResponseQrisTrxCallback responseCallback) {
        try {
            VolleyLog.DEBUG = true;
            writeLog("API", "Method : POST\nUrl : " + baseUrlQrisCpm + endpoint);
            writeLog("REQUEST", String.valueOf(parameter.getJson()));

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    baseUrlQrisCpm + endpoint,
                    parameter.getJson(),
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            writeLog("RESPONSE", String.valueOf(response));
                            responseCallback.onSuccess(response);

                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError volleyError) {
                    String errorMessage;
                    boolean isNoConnection = false;
                    try {
                        if (volleyError instanceof TimeoutError) {
                            isNoConnection = true;
                            errorMessage = "Response Timeout, periksa koneksi internet Anda.";
                        } else if (volleyError instanceof NoConnectionError) {
                            isNoConnection = true;
                            errorMessage = "Tidak ada koneksi internet, silakan periksa jaringan atau paket data Anda.";
                        } else if (volleyError instanceof AuthFailureError) {
                            errorMessage = "Authentication Failure";
                        } else if (volleyError instanceof ServerError) {
                            int statusCode = 0;
                            NetworkResponse networkResponse = volleyError.networkResponse;
                            if (networkResponse != null && networkResponse.data != null) {
                                statusCode = networkResponse.statusCode;
                            }
                            errorMessage = "Server Error, Kode " + statusCode;
                        } else if (volleyError instanceof ParseError) {
                            int statusCode = 0;
                            NetworkResponse networkResponse = volleyError.networkResponse;
                            if (networkResponse != null && networkResponse.data != null) {
                                statusCode = networkResponse.statusCode;
                            }
                            errorMessage = "Parse Error, Kode " + statusCode;
                        } else if (volleyError instanceof NetworkError) {
                            errorMessage = "Terjadi kesalahan jaringan, periksa kembali koneksi internet Anda.";
                        } else {
                            errorMessage = volleyError.getMessage() != null ? volleyError.getMessage() : "Something went wrong";
                        }

                        writeLog("RESPONSE", errorMessage);

                        // Callback onFailed dengan pesan yang telah disesuaikan
                        responseCallback.onFailed(errorMessage, isNoConnection);
                    } catch (Exception e) {
                        e.printStackTrace();
                        writeLog("RESPONSE", e.toString());
                        responseCallback.onFailed(e.getMessage(), false);
                    }
                }
            }) {

                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("Content-Type", "application/json");
//                    params.put("Authorization", authorization);
//                    params.put("X-PARTNER", xPartner);
//                    params.put("X-SIGNATURE", signature);

                    return params;
                }
            };

            if (this.queue == null) {
                this.queue = Volley.newRequestQueue(this.context);
            }
            request.setRetryPolicy(timoutdefaultRetryPolicy);
            this.queue.add(request);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void PostArray(final Map<String, String> params, String str, final ResponseCallback responseCallback) {
        try {
            writeLog("API", "Method : POST\nUrl : " + baseUrlAPI + str);

            StringRequest r15 = new StringRequest(Request.Method.POST, baseUrlAPI + str, new Response.Listener<String>() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass1 */

                public void onResponse(String str) {
                    if (!str.isEmpty()) {
                        responseCallback.onSuccess(str);
                    } else {
                        responseCallback.onSuccess("{\"status\":\"false\",\"code\":\"0099\",\"msg\":\"Error on response\"}");
                    }
                }
            }, new Response.ErrorListener() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass2 */

                @Override // com.android.volley.Response.ErrorListener
                public void onErrorResponse(VolleyError volleyError) {
                    String str = "";
                    try {
                        if (!(volleyError instanceof TimeoutError)) {
                            if (!(volleyError instanceof NoConnectionError)) {
                                if (volleyError instanceof AuthFailureError) {
                                    str = "Authentication Failure";
                                } else if (volleyError instanceof ServerError) {
                                    int jsonError = 0;
                                    NetworkResponse networkResponse = volleyError.networkResponse;
                                    if (networkResponse != null && networkResponse.data != null) {
                                        jsonError = networkResponse.statusCode;
                                    }
                                    str = "Server Error Code " + jsonError;
                                } else if (volleyError instanceof ParseError) {
                                    int jsonError = 0;
                                    NetworkResponse networkResponse = volleyError.networkResponse;
                                    if (networkResponse != null && networkResponse.data != null) {
                                        jsonError = networkResponse.statusCode;
                                        // Print Error!
                                    }
                                    str = "Server Error Code " + jsonError;
                                } else if (volleyError instanceof NetworkError) {
                                    str = "Network Error";
                                } else {
                                    str = volleyError instanceof ParseError ? "Parse Error" : volleyError.getMessage();
                                }
                                responseCallback.onFailed(str);
                            }
                        }
                        assert str != null;
                        if (str.equals(""))
                            str = "Response Timeout, silahkan periksa kembali jaringan internet Anda.";
                        responseCallback.onFailed(str);
                    } catch (Exception e) {
                        e.printStackTrace();
                        responseCallback.onFailed(e.getMessage());
                    }
                }
            }) {

                @Override
                protected Map<String, String> getParams() {
                    /*Map<String, String> params = new HashMap<String, String>();
                    params.put("data[0][hwid]", "2310005817015157");
                    params.put("data[0][operational]", "OR-0107");
                    params.put("data[0][route]", "Muhara - Assalam");
                    params.put("data[0][trip]", "1");
                    params.put("data[0][in]", "40");
                    params.put("data[0][out]", "0");
                    params.put("data[0][on]", "0");
                    params.put("data[0][lat]", "-6.4746231");
                    params.put("data[0][long]", "106.8927749");
                    params.put("data[0][date]", "2024-03-27 19:57:24");
                    params.put("data[0][tid]", "00000185");
                    params.put("data[0][bus]", "G-002");*/
                    return params;
                }

            };

            if (this.queue == null) {
                this.queue = Volley.newRequestQueue(this.context);
            }
            r15.setRetryPolicy(defaultRetryPolicy);
            this.queue.add(r15);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void PostArrayRaw(final JSONObject jsonParam, String str, final ResponseCallback responseCallback) {
        try {
            if (!str.equals("/sensor_log_raw")) {
                writeLog("API", "Method : POST\nUrl : " + baseUrlAPI + str);
                writeLog("REQUEST", jsonParam.toString());
            }
            JsonObjectRequest r16 = new JsonObjectRequest(Request.Method.POST, baseUrlAPI + str, jsonParam, new Response.Listener<JSONObject>() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass1 */

                public void onResponse(JSONObject jObj) {
                    String str2 = jObj.toString();
                    if (!str.equals("/sensor_log_raw")) {
                        writeLog("RESPONSE", str2);
                    }
                    if (!str2.isEmpty()) {
                        responseCallback.onSuccess(str2);
                    } else {
                        responseCallback.onSuccess("{\"status\":\"false\",\"code\":\"0099\",\"msg\":\"Error on response\"}");
                    }
                }
            }, new Response.ErrorListener() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass2 */

                @Override // com.android.volley.Response.ErrorListener
                public void onErrorResponse(VolleyError volleyError) {
                    String str = "";
                    try {
                        if (!(volleyError instanceof TimeoutError)) {
                            if (!(volleyError instanceof NoConnectionError)) {
                                if (volleyError instanceof AuthFailureError) {
                                    str = "Authentication Failure";
                                } else if (volleyError instanceof ServerError) {
//                                    str = "Server Error";
                                    int jsonError = 0;
                                    NetworkResponse networkResponse = volleyError.networkResponse;
                                    if (networkResponse != null && networkResponse.data != null) {
                                        jsonError = networkResponse.statusCode;
                                        // Print Error!
                                    }
                                    str = "Server Error Code " + jsonError;
                                } else if (volleyError instanceof ParseError) {
//                                    str = "Server Error";
                                    int jsonError = 0;
                                    NetworkResponse networkResponse = volleyError.networkResponse;
                                    if (networkResponse != null && networkResponse.data != null) {
                                        jsonError = networkResponse.statusCode;
                                        // Print Error!
                                    }
                                    str = "Server Error Code " + jsonError;
                                } else if (volleyError instanceof NetworkError) {
                                    str = "Network Error";
                                } else {
                                    str = volleyError instanceof ParseError ? "Parse Error" : volleyError.getMessage();
                                }
                                responseCallback.onFailed(str);
                            }
                        }
                        assert str != null;
                        if (str.equals(""))
                            str = "Response Timeout, silahkan periksa kembali jaringan internet Anda.";

                        writeLog("RESPONSE", str);
                        responseCallback.onFailed(str);
                    } catch (Exception e) {
                        e.printStackTrace();
                        writeLog("RESPONSE", e.toString());
                        responseCallback.onFailed(e.getMessage());
                    }
                }
            }) {
//                @Override
//                public String getBodyContentType() {
//                    return "application/json; charset=utf-8";
//                }
//                @Override
//                public byte[] getBody() throws AuthFailureError {
//                    try {
//                        return strParam == null ? null : strParam.getBytes("utf-8");
//                    } catch (UnsupportedEncodingException uee) {
//                        VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s",
//                                strParam, "utf-8");
//                        return null;
//                    }
//
//                }
//                @Override
//                public Map<String, String> getHeaders() throws AuthFailureError {
//                    HashMap<String, String> headers = new HashMap<String, String>();
//                    headers.put("Content-Type", "application/json; charset=utf-8");
//                    return headers;
//                }

            };

            if (this.queue == null) {
                this.queue = Volley.newRequestQueue(this.context);
            }
            r16.setRetryPolicy(defaultRetryPolicy);
            this.queue.add(r16);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void Get(String str, final ResponseCallback responseCallback) {
        try {
            writeLog("API", "Method : GET\nUrl : " + str);
            writeLog("REQUEST", str);

            StringRequest r11 = new StringRequest(Request.Method.GET, str, new Response.Listener<String>() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass1 */

                public void onResponse(String str) {
                    writeLog("RESPONSE", str);
                    if (!str.equals("")) {
                        responseCallback.onSuccess(str);
                    } else {
                        responseCallback.onSuccess("{\"status\":\"false\",\"code\":\"0099\",\"msg\":\"Error on response\"}");
                    }
                }
            }, new Response.ErrorListener() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass2 */

                @Override // com.android.volley.Response.ErrorListener
                public void onErrorResponse(VolleyError volleyError) {
                    String str = "";
                    try {
                        if (!(volleyError instanceof TimeoutError)) {
                            if (!(volleyError instanceof NoConnectionError)) {
                                if (volleyError instanceof AuthFailureError) {
                                    str = "Authentication Failure";
                                } else if (volleyError instanceof ServerError) {
//                                    str = "Server Error";
                                    int jsonError = 0;
                                    NetworkResponse networkResponse = volleyError.networkResponse;
                                    if (networkResponse != null && networkResponse.data != null) {
                                        jsonError = networkResponse.statusCode;
                                        // Print Error!
                                    }
                                    str = "Server Error Code " + jsonError;
                                } else if (volleyError instanceof ParseError) {
//                                    str = "Server Error";
                                    int jsonError = 0;
                                    NetworkResponse networkResponse = volleyError.networkResponse;
                                    if (networkResponse != null && networkResponse.data != null) {
                                        jsonError = networkResponse.statusCode;
                                        // Print Error!
                                    }
                                    str = "Server Error Code " + jsonError;
                                } else if (volleyError instanceof NetworkError) {
                                    str = "Network Error";
                                } else {
                                    str = volleyError instanceof ParseError ? "Parse Error" : volleyError.getMessage();
                                }
                                responseCallback.onFailed(str);
                            }
                        }
                        assert str != null;
                        if (str.isEmpty())
                            str = "Response Timeout, silahkan periksa kembali jaringan internet Anda.";

                        writeLog("RESPONSE", str);
                        responseCallback.onFailed(str);
                    } catch (Exception e) {
                        e.printStackTrace();
                        writeLog("RESPONSE", e.toString());
                        responseCallback.onFailed(e.getMessage());
                    }
                }
            }) {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass3 */

                @Override // com.android.volley.Request
                public Map<String, String> getHeaders() {
                    HashMap hashMap = new HashMap();
                    hashMap.put("Content-Type", "application/x-www-form-urlencoded");
                    return hashMap;
                }
            };
            if (this.queue == null) {
                this.queue = Volley.newRequestQueue(this.context);
            }
            r11.setRetryPolicy(defaultRetryPolicy);
            this.queue.add(r11);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void PostDirect(final Parameter parameter, String str, final ResponseCallback responseCallback) {
        try {
            StringRequest r11 = new StringRequest(1, str, new Response.Listener<String>() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass1 */

                public void onResponse(String str) {
                    if (!str.equals("")) {
                        responseCallback.onSuccess(str);
                    } else {
                        responseCallback.onSuccess("{\"status\":\"false\",\"code\":\"0099\",\"msg\":\"Error on response\"}");
                    }
                }
            }, new Response.ErrorListener() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass2 */

                @Override // com.android.volley.Response.ErrorListener
                public void onErrorResponse(VolleyError volleyError) {
                    String str = "";
                    try {
                        if (!(volleyError instanceof TimeoutError)) {
                            if (!(volleyError instanceof NoConnectionError)) {
                                if (volleyError instanceof AuthFailureError) {
                                    str = "Authentication Failure";
                                } else if (volleyError instanceof ServerError) {
//                                    str = "Server Error";
                                    int jsonError = 0;
                                    NetworkResponse networkResponse = volleyError.networkResponse;
                                    if (networkResponse != null && networkResponse.data != null) {
                                        jsonError = networkResponse.statusCode;
                                        // Print Error!
                                    }
                                    str = "Server Error Code " + jsonError;
                                } else if (volleyError instanceof ParseError) {
//                                    str = "Server Error";
                                    int jsonError = 0;
                                    NetworkResponse networkResponse = volleyError.networkResponse;
                                    if (networkResponse != null && networkResponse.data != null) {
                                        jsonError = networkResponse.statusCode;
                                        // Print Error!
                                    }
                                    str = "Server Error Code " + jsonError;
                                } else if (volleyError instanceof NetworkError) {
                                    str = "Network Error";
                                } else {
                                    str = volleyError instanceof ParseError ? "Parse Error" : volleyError.getMessage();
                                }
                                responseCallback.onFailed(str);
                            }
                        }
                        assert str != null;
                        if (str.equals(""))
                            str = "Response Timeout, silahkan periksa kembali jaringan internet Anda.";
                        responseCallback.onFailed(str);
                    } catch (Exception e) {
                        e.printStackTrace();
                        responseCallback.onFailed(e.getMessage());
                    }
                }
            }) {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass3 */

                @Override // com.android.volley.Request
                public byte[] getBody() {
                    return parameter.getParam().getBytes();
                }

                @Override // com.android.volley.Request
                public Map<String, String> getHeaders() {
                    HashMap hashMap = new HashMap();
                    hashMap.put("Content-Type", "application/x-www-form-urlencoded");
                    return hashMap;
                }
            };
            if (this.queue == null) {
                this.queue = Volley.newRequestQueue(this.context);
            }
            r11.setRetryPolicy(defaultRetryPolicy);
            this.queue.add(r11);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void ServerBasedPost(final JSONObject jSONObject, String str, final ResponseCallback responseCallback) {
        try {
            StringRequest r11 = new StringRequest(Request.Method.POST, baseUrlAPI + str, new Response.Listener<String>() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass10 */

                public void onResponse(String str) {
                    if (!str.equals("")) {
                        responseCallback.onSuccess(str);
                    } else {
                        responseCallback.onSuccess("{\"status\":\"false\",\"code\":\"0099\",\"msg\":\"Error on response\"}");
                    }
                }
            }, new Response.ErrorListener() {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass11 */

                @Override // com.android.volley.Response.ErrorListener
                public void onErrorResponse(VolleyError volleyError) {
                    String str;
                    try {
                        if (!(volleyError instanceof TimeoutError)) {
                            if (!(volleyError instanceof NoConnectionError)) {
                                if (volleyError instanceof AuthFailureError) {
                                    str = "Authentication Failure";
                                } else if (volleyError instanceof ServerError) {
                                    ServerError se = new ServerError();
                                    int statusCode = se.networkResponse.statusCode;
                                    str = "Server Error Code" + statusCode;
                                } else if (volleyError instanceof NetworkError) {
                                    str = "Network Error";
                                } else {
                                    str = volleyError instanceof ParseError ? "Parse Error" : volleyError.getMessage();
                                }
                                responseCallback.onFailed(str);
                            }
                        }
                        str = "Response Timeout, silahkan periksa kembali jaringan internet Anda.";
                        responseCallback.onFailed(str);
                    } catch (Exception e) {
                        e.printStackTrace();
                        responseCallback.onFailed(e.getMessage());
                    }
                }
            }) {
                /* class com.net2software.mobile.busvalidator.http.Network.AnonymousClass12 */

                @Override // com.android.volley.Request
                public String getBodyContentType() {
                    return "application/json";
                }

                @Override // com.android.volley.Request
                public byte[] getBody() {
                    return jSONObject.toString().getBytes();
                }

                @Override // com.android.volley.Request
                public Map<String, String> getHeaders() throws AuthFailureError {
                    HashMap hashMap = new HashMap();
                    hashMap.put("Authorization", "Basic " + Base64.encodeToString("trxserverbased:ppdbv123!".getBytes(), 2));
                    hashMap.put("Accept", "application/json");
                    return hashMap;
                }
            };
            if (this.queue == null) {
                this.queue = Volley.newRequestQueue(this.context);
            }
            r11.setRetryPolicy(defaultRetryPolicy);
            this.queue.add(r11);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface ResponseCallback {
        void onFailed(String str);

        void onSuccess(String str);
    }

    public interface ResponseTrxCallback {
        void onFailed(String str, boolean isNoConnection);

        void onSuccess(String str);
    }

    public interface ResponseQrisTrxCallback {
        void onFailed(String str, boolean isNoConnection);

        void onSuccess(JSONObject jsonObject);
    }

    /*public void PostDirect(final Parameter parameter, String str, final ResponseCallback responseCallback) {
        try {
            String str2 = baseUrlAPIDirect + str;
            Log.d("VolleyURL : ", str2);
            StringRequest r11 = new StringRequest(1, str2, new Response.Listener<String>() {
                public void onResponse(String str) {
                    if (!str.equals("")) {
                        responseCallback.onSuccess(str);
                    } else {
                        responseCallback.onSuccess("{\"status\":\"false\",\"code\":\"0099\",\"msg\":\"Error on response\"}");
                    }
                }
            }, new Response.ErrorListener() {
                @Override // com.android.volley.Response.ErrorListener
                public void onErrorResponse(VolleyError volleyError) {
                    String str;
                    try {
                        if (!(volleyError instanceof TimeoutError)) {
                            if (!(volleyError instanceof NoConnectionError)) {
                                if (volleyError instanceof AuthFailureError) {
                                    str = "Authentication Failure";
                                } else if (volleyError instanceof ServerError) {
                                    str = "Server Error";
                                } else if (volleyError instanceof NetworkError) {
                                    str = "Network Error";
                                } else {
                                    str = volleyError instanceof ParseError ? "Parse Error" : volleyError.getMessage();
                                }
                                responseCallback.onFailed(str);
                            }
                        }
                        str = "Response Timeout, please check your network";
                        responseCallback.onFailed(str);
                    } catch (Exception e) {
                        e.printStackTrace();
                        responseCallback.onFailed(e.getMessage());
                    }
                }
            }) {
                @Override // com.android.volley.Request
                public byte[] getBody() {
                    return parameter.getParam().getBytes();
                }

                @Override // com.android.volley.Request
                public Map<String, String> getHeaders() {
                    HashMap hashMap = new HashMap();
                    hashMap.put("Content-Type", "application/x-www-form-urlencoded");
                    return hashMap;
                }
            };
            if (this.queue == null) {
                this.queue = Volley.newRequestQueue(this.context);
            }
            r11.setRetryPolicy(new DefaultRetryPolicy(90000, 3, -1.0f));
            this.queue.getCache().clear();
            this.queue.add(r11);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void PostErr(final Parameter parameter, String str, final ResponseCallback responseCallback) {
        try {
            StringRequest r11 = new StringRequest(1, "http://202.53.254.38/bv_transtek/c_bus" + str, new Response.Listener<String>() {
                public void onResponse(String str) {
                    if (!str.equals("")) {
                        responseCallback.onSuccess(str);
                    } else {
                        responseCallback.onSuccess("{\"status\":\"false\",\"code\":\"0099\",\"msg\":\"Error on response\"}");
                    }
                }
            }, new Response.ErrorListener() {
                @Override // com.android.volley.Response.ErrorListener
                public void onErrorResponse(VolleyError volleyError) {
                    String str;
                    try {
                        if (!(volleyError instanceof TimeoutError)) {
                            if (!(volleyError instanceof NoConnectionError)) {
                                if (volleyError instanceof AuthFailureError) {
                                    str = "Authentication Failure";
                                } else if (volleyError instanceof ServerError) {
                                    str = "Server Error";
                                } else if (volleyError instanceof NetworkError) {
                                    str = "Network Error";
                                } else {
                                    str = volleyError instanceof ParseError ? "Parse Error" : volleyError.getMessage();
                                }
                                responseCallback.onFailed(str);
                            }
                        }
                        str = "Response Timeout, please check your network";
                        responseCallback.onFailed(str);
                    } catch (Exception e) {
                        e.printStackTrace();
                        responseCallback.onFailed(e.getMessage());
                    }
                }
            }) {
                @Override // com.android.volley.Request
                public byte[] getBody() {
                    return parameter.getParam().getBytes();
                }

                @Override // com.android.volley.Request
                public Map<String, String> getHeaders() {
                    return new HashMap();
                }
            };
            if (this.queue == null) {
                this.queue = Volley.newRequestQueue(this.context);
            }
            r11.setRetryPolicy(new DefaultRetryPolicy(90000, 3, -1.0f));
            this.queue.add(r11);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }*/

    public interface ResponseCallbackJson {
        void onFailed(String str);

        void onSuccess(JSONObject jsonObject);
    }


}
