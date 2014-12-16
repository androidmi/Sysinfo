/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sysinfo.app.com.sysinfo.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.provider.Telephony.Sms.Intents;

/**
 * 安全收件箱工具类
 * @author dufan
 */
public class MmsUtil {

    private static final String LONOVO_MODEL = "Lenovo K900";
    /** meizu系统4.4系统不支持第三方短信设置默认，4.4及以上系统屏蔽魅族手机上的安全短信功能 */
    private static final String MEIZU_MANUFACTURER = "Meizu";
    private static final String XIAO_MANUFACTURER = "Xiaomi";
    private static final String HUAWEI_P7_MODEL = "HUAWEI P7-L00";
    private static final String HUAWEI_MODEL1 = "GT-9300";
    private static final String HUAWEI_MODEL2 = "HUAWEI C8812E";
    private static final String SAMSUNG_MODEL1 = "GT-I9502";
    private static final String SAMSUNG_MODEL2 = "Nexus S";
    private static final String SAMSUNG_MODEL3 = "GT-I9100";
    /**
     * 安全短信收件箱只在4.0及其以上版本可用,对于联想K900暂不适配,对于K900收件箱有两个问题
     * 暂时无法解决，1、绑定问题，接管后同时绑定了短信、拨号、联系人2、收到新短信后无法拦截，
     * 导致系统收件箱和我们都执行了一次短信插入逻辑，结果是收件箱新 收到的都是重复的两条新短信
     *
     * @return
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static boolean canUseMmsApp() {
        String model = Build.MODEL;
        if (LONOVO_MODEL.equalsIgnoreCase(model)
                || ((MEIZU_MANUFACTURER.equalsIgnoreCase(Build.MANUFACTURER) || HUAWEI_P7_MODEL.equals(model)) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)) {
            return false;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    /**
     * 判断当前默认短信应用是否为卫士
     * 4.4系统
     * @param context
     * @return
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static boolean isSmsEnabled(Context context) {
        if (Build.VERSION.SDK_INT >=  Build.VERSION_CODES.KITKAT) {
            String defaultSmsApplication = Telephony.Sms.getDefaultSmsPackage(context);
            if (defaultSmsApplication != null && defaultSmsApplication.equals(context.getPackageName())) {
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * 是否能处理收到的短信广播
     * @param context
     * @param action
     * @return true,能处理，反之，不能处理
     */
    public static boolean canDealSmsAction(Context context, String action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && MmsUtil.isSmsEnabled(context)) {// 4.4以上系统设为默认后只处理SMS_DELIVER_ACTION
            return Intents.SMS_DELIVER_ACTION
                    .equals(action);
        }
        return true;
    }

    /*
     * 是否为小米4.4系统
     */
    public static boolean isXiaomiKitKat() {
        if (XIAO_MANUFACTURER.equalsIgnoreCase(Build.MANUFACTURER)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {// 屏蔽小米系统的绑定系统图标功能
            return true;
        }
        return false;
    }

    /**
     * 是否需要处理方向错误的照片
     * @return true,需要处理,反之，不需要处理
     */
    public static boolean needHandleErrorPicture() {
        if (SAMSUNG_MODEL1.equalsIgnoreCase(Build.MODEL) || SAMSUNG_MODEL2.equalsIgnoreCase(Build.MODEL) ||
                SAMSUNG_MODEL3.equalsIgnoreCase(Build.MODEL) || HUAWEI_MODEL1.equalsIgnoreCase(Build.MODEL) ||
                HUAWEI_MODEL2.equalsIgnoreCase(Build.MODEL)) {
            return true;
        }
        return false;
    }

    public static void setDefaultMms(Activity activity, int requestCode) {
        Context context = activity.getApplicationContext();
        activity.startActivityForResult(getRequestDefaultSmsAppActivity(context), requestCode);
    }

    @SuppressLint("InlinedApi")
    public static Intent getRequestDefaultSmsAppActivity(Context context) {
        final Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.getPackageName());
        return intent;
    }
}
