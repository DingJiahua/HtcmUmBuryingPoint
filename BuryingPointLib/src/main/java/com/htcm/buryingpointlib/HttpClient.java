package com.htcm.buryingpointlib;

import android.os.Build;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Author DJH
 * Create Date 2024/5/22
 */
public class HttpClient {
    public static final String URL_EVENT_LOG = "/tbapi/sta/saveChildEventLog";
    private static final String BASE_URL = "https://tabletapi.dolphinmedia.cn";
    private static final String BASE_URL_TEST = "https://tabletapi-test.dolphinmedia.cn";
    private static final int DEFAULT_TIMEOUT = 10;
    private static final MediaType JSON = MediaType.get("application/json");
    private static final OkHttpClient.Builder client = new OkHttpClient.Builder();
    private static HttpClient instance;
    private static String baseUrl;

    public static HttpClient getInstance() {
        if (null == instance) {
            instance = new HttpClient();
        }
        return instance;
    }

    private HttpClient() {

    }

    public void init(String sn, String parentId, String token, boolean debugEnv) {
        baseUrl = debugEnv ? BASE_URL_TEST : BASE_URL;
        client.connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
        client.readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
        client.writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
        client.retryOnConnectionFailure(true);//错误重连
        client.addInterceptor(chain -> {
            Request original = chain.request();

            Request.Builder request = original.newBuilder();
            request.addHeader("ua", getUa(token));
            request.addHeader("sn", sn);
            request.addHeader("ota", Build.DISPLAY);
            request.addHeader("userId", parentId);
            return chain.proceed(request.build());
        });
    }

    private String getUa(String token) {
        return "app" + "/" + // 类型(app/小程序:client html:h5)
                "1.0/" + // App版本(1.0.0)
                "1/" + // 系统平台(安卓:1 小程序:2)
                "BuryingPointSDK/" + // 设备唯一标识(app包名/小程序包名)
                token; // 签名(绑定接口返回的token)
    }

    public void post(String url, String json) {
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder().post(body).url(baseUrl + url).build();
        client.build().newCall(request).enqueue(callback);
    }

    private final Callback callback = new Callback() {
        @Override
        public void onFailure(@NonNull Call call, @NonNull IOException e) {

        }

        @Override
        public void onResponse(@NonNull Call call, @NonNull Response response) {
            response.close();
        }
    };
}
