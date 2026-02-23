/*
 * Copyright (C) 2022 Paranoid Android
 *           (C) 2023 ArrowOS
 *           (C) 2023 The LibreMobileOS Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util;

import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Binder;
import android.os.Process;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * @hide
 */
public class PropImitationHooks {

    private static final String TAG = "PropImitationHooks";
    private static final boolean DEBUG = true;

    private static final Boolean sDisableGmsProps = SystemProperties.getBoolean(
            "persist.sys.pihooks.disable.gms_props", false);

    private static final Boolean sDisableKeyAttestationBlock = SystemProperties.getBoolean(
            "persist.sys.pihooks.disable.gms_key_attestation_block", false);

    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";
    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";
    private static final String PROP_HOOKS = "persist.sys.pihooks_";

    private static final String[] GMS_SPOOF_KEYS = {
        "BRAND", "DEVICE", "DEVICE_INITIAL_SDK_INT", "FINGERPRINT", "ID",
        "MANUFACTURER", "MODEL", "PRODUCT", "RELEASE", "SECURITY_PATCH",
        "TAGS", "TYPE", "SDK_INT"
    };

    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");


    private static final Map<String, String> sPixelOneProps = Map.of(
        "PRODUCT", "sailfish",
        "DEVICE", "sailfish",
        "MANUFACTURER", "Google",
        "BRAND", "google",
        "MODEL", "Pixel",
        "FINGERPRINT", "google/sailfish/sailfish:10/QP1A.191005.007.A3/5972272:user/release-keys"
    );

    private static final Set<String> sNexusFeatures = Set.of(
            "NEXUS_PRELOAD",
            "nexus_preload",
            "GOOGLE_BUILD",
            "GOOGLE_EXPERIENCE",
            "PIXEL_EXPERIENCE"
    );

    private static final Set<String> sPixelFeatures = Set.of(
            "PIXEL_2017_EXPERIENCE",
            "PIXEL_2017_PRELOAD",
            "PIXEL_2018_EXPERIENCE",
            "PIXEL_2018_PRELOAD",
            "PIXEL_2019_EXPERIENCE",
            "PIXEL_2019_MIDYEAR_EXPERIENCE",
            "PIXEL_2019_MIDYEAR_PRELOAD",
            "PIXEL_2019_PRELOAD",
            "PIXEL_2020_EXPERIENCE",
            "PIXEL_2020_MIDYEAR_EXPERIENCE",
            "PIXEL_2021_MIDYEAR_EXPERIENCE"
    );

    private static final Set<String> sTensorFeatures = Set.of(
            "PIXEL_2021_EXPERIENCE",
            "PIXEL_2022_EXPERIENCE",
            "PIXEL_2022_MIDYEAR_EXPERIENCE",
            "PIXEL_2023_EXPERIENCE",
            "PIXEL_2023_MIDYEAR_EXPERIENCE",
            "PIXEL_2024_EXPERIENCE",
            "PIXEL_2024_MIDYEAR_EXPERIENCE"
    );

    private static volatile String sProcessName;
    private static volatile boolean sIsGms, sIsFinsky, sIsPhotos;

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();

        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(processName)) {
            Log.e(TAG, "Null package or process name");
            return;
        }

        final Resources res = context.getResources();
        if (res == null) {
            Log.e(TAG, "Null resources");
            return;
        }

        sProcessName = processName;
        sIsGms = packageName.equals(PACKAGE_GMS) && processName.equals(PROCESS_GMS_UNSTABLE);
        sIsFinsky = packageName.equals(PACKAGE_FINSKY);
        sIsPhotos = packageName.equals(PACKAGE_GPHOTOS);

        /* Set Certified Properties for GMSCore
         * Set Pixel XL for Google Photos
         */
        if (sIsGms) {
            setCertifiedPropsForGms();
        } else if (sIsPhotos) {
            dlog("Spoofing Pixel 1 for Google Photos");
            sPixelOneProps.forEach((PropImitationHooks::setPropValue));
        }
    }

    private static void setPropValue(String key, Object value) {
        try {
            dlog("Setting prop " + key + " to " + value);
            Field field = getBuildClassField(key);
	    if (field != null) {
                field.setAccessible(true);
		if (field.getType() == int.class) {
                    if (value instanceof String valueAsString) {
                        field.set(null, Integer.parseInt(valueAsString));
                    } else if (value instanceof Integer valueAsInteger) {
                        field.set(null, valueAsInteger);
                    }
                } else if (field.getType() == long.class) {
                    if (value instanceof String valueAsString) {
                        field.set(null, Long.parseLong(valueAsString));
                    } else if (value instanceof Long valueAsLong) {
                        field.set(null, valueAsLong);
                    }
                } else {
                    field.set(null, value.toString());
                }
                field.setAccessible(false);
	    } else {
                Log.e(TAG, "Field " + key + " not found in Build or Build.VERSION classes");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static Field getBuildClassField(String key) throws NoSuchFieldException {
        try {
            Field field = Build.class.getDeclaredField(key);
            dlog("Field " + key + " found in Build.class");
            return field;
        } catch (NoSuchFieldException e) {
            Field field = Build.VERSION.class.getDeclaredField(key);
            dlog("Field " + key + " found in Build.VERSION.class");
            return field;
        }
    }

    private static void setCertifiedPropsForGms() {
        if (sDisableGmsProps) {
            dlog("GMS prop imitation is disabled by user");
            return;
        }

        final boolean was = isGmsAddAccountActivityOnTop();
        final TaskStackListener taskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                final boolean is = isGmsAddAccountActivityOnTop();
                if (is ^ was) {
                    dlog("GmsAddAccountActivityOnTop is:" + is + " was:" + was +
                            ", killing myself!"); // process will restart automatically later
                    Process.killProcess(Process.myPid());
                }
            }
        };
        if (!was) {
            dlog("Spoofing build for GMS");
            setCertifiedProps();
        } else {
            dlog("Skip spoofing build for GMS, because GmsAddAccountActivityOnTop");
        }
        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener!", e);
        }
    }

    private static void setCertifiedProps() {
        for (String key : GMS_SPOOF_KEYS) {
            setPropValue(key, SystemProperties.get(PROP_HOOKS + key));
        }
    }

    private static boolean isGmsAddAccountActivityOnTop() {
        try {
            final ActivityTaskManager.RootTaskInfo focusedTask =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            return focusedTask != null && focusedTask.topActivity != null
                    && focusedTask.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }

    public static boolean shouldBypassTaskPermission(Context context) {
        if (sDisableGmsProps) {
            return false;
        }

        // GMS doesn't have MANAGE_ACTIVITY_TASKS permission
        final int callingUid = Binder.getCallingUid();
        final int gmsUid;
        try {
            gmsUid = context.getPackageManager().getApplicationInfo(PACKAGE_GMS, 0).uid;
            dlog("shouldBypassTaskPermission: gmsUid:" + gmsUid + " callingUid:" + callingUid);
        } catch (Exception e) {
            Log.e(TAG, "shouldBypassTaskPermission: unable to get gms uid", e);
            return false;
        }
        return gmsUid == callingUid;
    }

    private static boolean isCallerSafetyNet() {
        return sIsGms && Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static boolean hasSystemFeature(String name, boolean has) {
        if (sIsPhotos) {
            if (has && (sPixelFeatures.stream().anyMatch(name::contains)
                    || sTensorFeatures.stream().anyMatch(name::contains))) {
                dlog("Blocked system feature " + name + " for Google Photos");
                has = false;
            } else if (!has && sNexusFeatures.stream().anyMatch(name::contains)) {
                dlog("Enabled system feature " + name + " for Google Photos");
                has = true;
            }
        }
        return has;
    }

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, "[" + sProcessName + "] " + msg);
    }
}
