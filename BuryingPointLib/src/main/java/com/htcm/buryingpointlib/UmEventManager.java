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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Author DJH
 * Create Date 2024/3/11
 */
public class UmEventManager {
    public static final String PAGE_CREATE = "PageCreate";
    public static final String PAGE_RESUME = "PageResume";
    public static final String PAGE_PAUSE = "PagePause";
    public static final String PAGE_DESTROY = "PageDestroy";
    private final Map<String, String> map = new HashMap<>();
    private String mChildId;
    private boolean showLog;
    private final Map<String, String> commonPageSuffixMap = new HashMap<>();
    private final Map<String, Object> params = new HashMap<>();
    private final List<String> reusableParams = new ArrayList<>();
    private List<Class<? extends Activity>> specialPageList = new ArrayList<>();
    private final JSONObject jsonObject = new JSONObject();

    public static UmEventManager getInstance() {
        return BuryingPointManagerHolder.INSTANCE;
    }

    public void init(Application application, String appKey) {
        UMConfigure.preInit(application.getApplicationContext(), appKey, Build.MODEL);
        UMConfigure.init(application.getApplicationContext(), appKey, Build.MODEL, UMConfigure.DEVICE_TYPE_PHONE, "");
        MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.AUTO);

        String jsonInfo = Settings.Global.getString(application.getContentResolver(), "htcm_launcher_user_info");
        String childId;
        String sn;
        String token;
        String parentId;
        try {
            JSONObject jsonObject = new JSONObject(jsonInfo);
            childId = jsonObject.getString("childId");
            sn = jsonObject.getString("deviceToken");
            token = jsonObject.getString("dolphinToken");
            parentId = jsonObject.getString("parentId");
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        if (TextUtils.isEmpty(childId)) {
            return;
        }
        String serverEnv = Settings.Global.getString(application.getContentResolver(), "dolphin_env");
        boolean debugEnv;
        debugEnv = !TextUtils.isEmpty(serverEnv) && serverEnv.equals("test");

        HttpClient.getInstance().init(sn, parentId, token, debugEnv);
        initUmEvent(application, childId);
    }

    public void setLogEnable(boolean showLog) {
        this.showLog = showLog;
        UMConfigure.setLogEnabled(showLog);
    }

    /**
     * 设置应用内公共复用的Activity后缀
     *
     * @param activityClass 页面
     * @param suffix        后缀
     */
    public void setCommonPageSuffix(Class<? extends Activity> activityClass, String suffix) {
        commonPageSuffixMap.put(activityClass.getName(), suffix);
    }

    /**
     * 设置需要自行管理埋点事件的Activity集合
     *
     * @param classList Activity集合
     */
    public void setExternalMultiEntrancePageList(List<Class<? extends Activity>> classList) {
        specialPageList = classList;
    }

    private void initUmEvent(Application application, String childId) {
        mChildId = childId;
        Context context = application.getApplicationContext();
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                if (!specialPageList.contains(activity.getClass())) {
                    String pageName = getPageName(activity);
                    enterEventQueue(context, PAGE_CREATE, pageName);
                }
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {

            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (!specialPageList.contains(activity.getClass())) {
                    String pageName = getPageName(activity);
                    enterEventQueue(context, PAGE_RESUME, pageName);
                }
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                if (!specialPageList.contains(activity.getClass())) {
                    String pageName = getPageName(activity);
                    quitEventQueue(context, PAGE_PAUSE, pageName);
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
                if (!specialPageList.contains(activity.getClass())) {
                    String pageName = getPageName(activity);
                    quitEventQueue(context, PAGE_DESTROY, pageName);
                }
            }
        });
    }

    /**
     * 进入事件集合
     *
     * @param context  上下文
     * @param eventId  事件ID onCreate onResume
     * @param pageName 页面名称
     */
    private void enterEventQueue(Context context, String eventId, String pageName) {
        String uuid = UUID.randomUUID().toString();
        map.put(eventId + pageName, uuid);
        postUmEvent(context, eventId, pageName, uuid);
    }

    /**
     * 移除事件
     *
     * @param context  上下文
     * @param eventId  事件ID onPause onDestroy
     * @param pageName 页面名称
     */
    private void quitEventQueue(Context context, String eventId, String pageName) {
        if (eventId.equals(PAGE_PAUSE)) {
            String uuid = map.get(PAGE_RESUME + pageName);
            if (!TextUtils.isEmpty(uuid)) {
                map.remove(PAGE_RESUME + pageName);
                postUmEvent(context, PAGE_PAUSE, pageName, uuid);
            }
        } else if (eventId.equals(PAGE_DESTROY)) {
            String uuid = map.get(PAGE_CREATE + pageName);
            if (!TextUtils.isEmpty(uuid)) {
                map.remove(PAGE_CREATE + pageName);
                postUmEvent(context, PAGE_DESTROY, pageName, uuid);
            }
        }
    }

    private String getPageName(Activity activity) {
        String pageName = activity.getComponentName().getClassName();
        // 跳转activity属于公共复用页面，则另外加上后缀
        String suffix = commonPageSuffixMap.get(pageName);
        if (suffix != null) {
            pageName += suffix;
        }
        return pageName;
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
        String timestamp = String.valueOf(System.currentTimeMillis());
        params.clear();
        reusableParams.clear();
        reusableParams.add(pageName);
        reusableParams.add(uuid);
        reusableParams.add(mChildId);
        params.put("pageName", TextUtils.join("_", reusableParams));
        reusableParams.clear();
        reusableParams.add(uuid);
        reusableParams.add(mChildId);
        reusableParams.add(timestamp);
        params.put("timeMillis", TextUtils.join("_", reusableParams));
        MobclickAgent.onEventObject(context, eventId, params);
        if (showLog) {
            Log.d("UmEventManager", "EventId = " + eventId + " UmParams = " + params);
        }
        if (eventId.equals(PAGE_RESUME) || eventId.equals(PAGE_PAUSE)) {
            try {
                jsonObject.put("activity", pageName);
                jsonObject.put("childId", Integer.parseInt(mChildId));
                jsonObject.put("eventName", eventId);
                jsonObject.put("timestamp", timestamp);
                jsonObject.put("uuid", uuid);
                HttpClient.getInstance().post(HttpClient.URL_EVENT_LOG, jsonObject.toString());
            } catch (JSONException | NumberFormatException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 公共复用页面，且参数是由外部传入，需要自行在对应生命周期调用事件上报
     *
     * @param activity 页面
     * @param eventId  事件ID
     * @param suffix   后缀
     */
    public void setEventSelf(Activity activity, String eventId, String suffix) {
        String pageName = activity.getComponentName().getClassName() + suffix;
        switch (eventId) {
            case PAGE_CREATE:
            case PAGE_RESUME:
                enterEventQueue(activity, eventId, pageName);
                break;
            case PAGE_PAUSE:
            case PAGE_DESTROY:
                quitEventQueue(activity, eventId, pageName);
                break;
            default:
                break;
        }
    }

    /**
     * 主动调用杀死进程或应用退出时，在方法之前调用该方法
     *
     * @param activity 主页
     */
    public void homePageExit(Activity activity) {
        MobclickAgent.onKillProcess(activity);
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
