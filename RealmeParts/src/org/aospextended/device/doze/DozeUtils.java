/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2019 The LineageOS Project
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

package org.aospextended.device.doze;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import androidx.preference.PreferenceManager;

import org.aospextended.device.util.Utils;

import android.hardware.display.AmbientDisplayConfiguration;

import static android.provider.Settings.Secure.DOZE_ALWAYS_ON;
import static android.provider.Settings.Secure.DOZE_ENABLED;

public final class DozeUtils {

    private static final String TAG = "DozeUtils";
    private static final boolean DEBUG = Utils.DEBUG;

    private static final String DOZE_INTENT = "com.android.systemui.doze.pulse";

    protected static final String ALWAYS_ON_DISPLAY = "always_on_display";

    protected static final String CATEG_PICKUP_SENSOR = "pickup_sensor";
    protected static final String CATEG_PROX_SENSOR = "proximity_sensor";

    protected static final String GESTURE_PICK_UP_KEY = "gesture_pick_up";
    protected static final String GESTURE_APPROACH_KEY = "gesture_approach";
    protected static final String GESTURE_AMBIENT_MOTION_KEY = "gesture_ambient_motion";
    protected static final String GESTURE_HAND_WAVE_KEY = "gesture_hand_wave";
    protected static final String GESTURE_POCKET_KEY = "gesture_pocket";
    protected static final String GESTURE_RAISE_TO_WAKE_KEY = "gesture_raise_to_wake";
    public static final String GESTURE_DOUBLE_TAP = "gesture_double_tap_to_check_phone";

    public static void startService(Context context) {
        if (DEBUG) Log.d(TAG, "Starting service");
        context.startServiceAsUser(new Intent(context, DozeService.class),
                UserHandle.CURRENT);
    }

    protected static void stopService(Context context) {
        if (DEBUG) Log.d(TAG, "Stopping service");
        context.stopServiceAsUser(new Intent(context, DozeService.class),
                UserHandle.CURRENT);
    }

    public static void checkDozeService(Context context) {
        if (isDozeEnabled(context) && !isAlwaysOnEnabled(context) && sensorsEnabled(context)) {
            startService(context);
        } else {
            stopService(context);
        }
    }

    protected static boolean getProxCheckBeforePulse(Context context) {
        try {
            Context con = context.createPackageContext("com.android.systemui", 0);
            int id = con.getResources().getIdentifier("doze_proximity_check_before_pulse",
                    "bool", "com.android.systemui");
            if (id != 0) {
                return con.getResources().getBoolean(id);
            }
        } catch (Exception e) {
            // Fallback to sensor availability check
        }
        SensorManager sm = context.getSystemService(SensorManager.class);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null;
    }

    public static boolean enableDoze(Context context, boolean enable) {
        return Settings.Secure.putInt(context.getContentResolver(),
                DOZE_ENABLED, enable ? 1 : 0);
    }

    public static boolean isDozeEnabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(),
                DOZE_ENABLED, 1) != 0;
    }

    public static void launchDozePulse(Context context) {
        if (DEBUG) Log.d(TAG, "Launch doze pulse");
        Intent intent = new Intent(DOZE_INTENT);
        intent.setPackage("com.android.systemui");
        context.sendBroadcastAsUser(intent, UserHandle.CURRENT);
    }

    public static boolean enableAlwaysOn(Context context, boolean enable) {
        return Settings.Secure.putIntForUser(context.getContentResolver(),
                DOZE_ALWAYS_ON, enable ? 1 : 0, UserHandle.USER_CURRENT);
    }

    public static boolean isAlwaysOnEnabled(Context context) {
        final boolean enabledByDefault = context.getResources()
                .getBoolean(com.android.internal.R.bool.config_dozeAlwaysOnEnabled);

        return Settings.Secure.getIntForUser(context.getContentResolver(),
                DOZE_ALWAYS_ON, alwaysOnDisplayAvailable(context) && enabledByDefault ? 1 : 0,
                UserHandle.USER_CURRENT) != 0;
    }

    public static boolean alwaysOnDisplayAvailable(Context context) {
        return new AmbientDisplayConfiguration(context).alwaysOnAvailable();
    }

    public static boolean isDozeAvailable(Context context) {
        if (alwaysOnDisplayAvailable(context)) {
            return true;
        }
        SensorManager sm = context.getSystemService(SensorManager.class);
        if (sm == null) {
            return false;
        }
        return sm.getDefaultSensor(Sensor.TYPE_PROXIMITY, true) != null
                || sm.getDefaultSensor(Sensor.TYPE_PROXIMITY, false) != null
                || sm.getDefaultSensor(Sensor.TYPE_PICK_UP_GESTURE, true) != null
                || sm.getDefaultSensor(Sensor.TYPE_TILT_DETECTOR, true) != null
                || getSensor(sm, "android.sensor.tilt_detector") != null;
    }

    protected static boolean isGestureEnabled(Context context, String gesture) {
        return Utils.getSharedPreferences(context)
                .getBoolean(gesture, false);
    }

    protected static boolean isPickUpEnabled(Context context) {
        return isGestureEnabled(context, GESTURE_PICK_UP_KEY);
    }

    protected static boolean isApproachEnabled(Context context) {
        return isGestureEnabled(context, GESTURE_APPROACH_KEY)
                || isGestureEnabled(context, GESTURE_AMBIENT_MOTION_KEY);
    }

    protected static boolean isAmbientMotionEnabled(Context context) {
        return isApproachEnabled(context);
    }

    protected static boolean isRaiseToWakeEnabled(Context context) {
        return isGestureEnabled(context, GESTURE_RAISE_TO_WAKE_KEY);
    }

    protected static boolean isHandwaveGestureEnabled(Context context) {
        return isGestureEnabled(context, GESTURE_HAND_WAVE_KEY);
    }

    protected static boolean isPocketGestureEnabled(Context context) {
        return isGestureEnabled(context, GESTURE_POCKET_KEY);
    }

    protected static boolean isDoubleTapEnabled(Context context) {
        return isGestureEnabled(context, GESTURE_DOUBLE_TAP);
    }
    public static boolean sensorsEnabled(Context context) {
        return isPickUpEnabled(context) || isApproachEnabled(context) || isHandwaveGestureEnabled(context)
                || isPocketGestureEnabled(context) ||  isRaiseToWakeEnabled(context) || isDoubleTapEnabled(context);
    }

    protected static Sensor getSensor(SensorManager sm, String type) {
        for (Sensor sensor : sm.getSensorList(Sensor.TYPE_ALL)) {
            if (type.equals(sensor.getStringType())) {
                return sensor;
            }
        }
        return null;
    }
}
