package com.htcm.buryingpointlib;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.umeng.analytics.MobclickAgent;
import com.umeng.commonsdk.UMConfigure;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Author DJH
 * Create Date 2024/3/11
 */
public class UmEventManager {
    private static final String PAGE_CREATE = "PageCreate";
    private static final String PAGE_RESUME = "PageResume";
    private static final String PAGE_PAUSE = "PagePause";
    private static final String PAGE_DESTROY = "PageDestroy";
    private final Map<String, String> map = new HashMap<>();
    private String mChildId;
    private boolean eventInit;

    public static UmEventManager getInstance() {
        return BuryingPointManagerHolder.INSTANCE;
    }

    public void init(Application application, String appKey) {
        UMConfigure.preInit(application.getApplicationContext(), appKey, Build.MODEL);
        UMConfigure.init(application.getApplicationContext(), appKey, Build.MODEL, UMConfigure.DEVICE_TYPE_PHONE, "");
        MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.AUTO);

        String serverEnv = Settings.Global.getString(application.getContentResolver(), "dolphin_env");
        if (!TextUtils.isEmpty(serverEnv) && serverEnv.equals("test")) {
            return;
        }
        String jsonInfo = Settings.Global.getString(application.getContentResolver(), "htcm_launcher_user_info");
        String childId;
        try {
            JSONObject jsonObject = new JSONObject(jsonInfo);
            childId = jsonObject.getString("childId");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        if (TextUtils.isEmpty(childId)) {
            return;
        }
        initUmEvent(application, childId);
    }

    private void initUmEvent(Application application, String childId) {
        eventInit = true;
        mChildId = childId;
        Context context = application.getApplicationContext();
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                String pageName = activity.getComponentName().getClassName();
                String uuid = UUID.randomUUID().toString();
                map.put(PAGE_CREATE + pageName, uuid);
                postUmEvent(context, PAGE_CREATE, pageName, uuid);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {

            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                String pageName = activity.getComponentName().getClassName();
                String uuid = UUID.randomUUID().toString();
                map.put(PAGE_RESUME + pageName, uuid);
                postUmEvent(context, PAGE_RESUME, pageName, uuid);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                String pageName = activity.getComponentName().getClassName();
                String uuid = map.get(PAGE_RESUME + pageName);
                if (!TextUtils.isEmpty(uuid)) {
                    map.remove(PAGE_RESUME + pageName);
                    postUmEvent(context, PAGE_PAUSE, pageName, uuid);
                }
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {

            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                String pageName = activity.getComponentName().getClassName();
                String uuid = map.get(PAGE_CREATE + pageName);
                if (!TextUtils.isEmpty(uuid)) {
                    map.remove(PAGE_CREATE + pageName);
                    postUmEvent(context, PAGE_DESTROY, pageName, uuid);
                }
            }
        });
    }

    public void setChildId(String childId) {
        mChildId = childId;
    }

    /**
     * 友盟自定义事件
     *
     * @param context  上下文
     * @param eventId  事件ID
     * @param pageName 页面名称
     * @param uuid     一组对应事件的uuid
     */
    private void postUmEvent(Context context, String eventId, String pageName, String uuid) {
        Map<String, Object> params = new HashMap<>();
        params.put("pageName", pageName + "_" + uuid + "_" + mChildId);
        params.put("timeMillis", uuid + "_" + mChildId + "_" + System.currentTimeMillis());
        Log.e("UmEventManager", "UmEvent = " + params);
        MobclickAgent.onEventObject(context, eventId, params);
    }

    /**
     * 主动调用杀死进程或应用退出时，在方法之前调用该方法
     *
     * @param activity 主页
     */
    public void homePageExit(Activity activity) {
        MobclickAgent.onKillProcess(activity);
        if (!eventInit) {
            return;
        }
        String pageName = activity.getComponentName().getClassName();
        String uuid = map.get(PAGE_RESUME + pageName);
        if (!TextUtils.isEmpty(uuid)) {
            map.remove(PAGE_RESUME + pageName);
            postUmEvent(activity, PAGE_PAUSE, pageName, uuid);
        }

        uuid = map.get(PAGE_CREATE + pageName);
        if (!TextUtils.isEmpty(uuid)) {
            map.remove(PAGE_CREATE + pageName);
            postUmEvent(activity, PAGE_DESTROY, pageName, uuid);
        }
    }

    private static class BuryingPointManagerHolder {
        private static final UmEventManager INSTANCE = new UmEventManager();
    }
}
