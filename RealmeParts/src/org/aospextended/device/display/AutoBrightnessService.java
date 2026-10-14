/*
 * Copyright (C) 2026 The LineageOS Project
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

package org.aospextended.device.display;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.hardware.display.BrightnessConfiguration;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import org.aospextended.device.util.Utils;

import java.lang.reflect.Method;

public class AutoBrightnessService extends Service {

    private static final String TAG = "AutoBrightnessService";
    private static final boolean DEBUG = Utils.DEBUG;

    private ContentObserver mBrightnessModeObserver;
    private boolean mRegistered = false;

    public static void start(Context context) {
        try {
            context.startServiceAsUser(new Intent(context, AutoBrightnessService.class),
                    UserHandle.CURRENT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AutoBrightnessService", e);
        }
    }

    public static void stop(Context context) {
        try {
            context.stopServiceAsUser(new Intent(context, AutoBrightnessService.class),
                    UserHandle.CURRENT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop AutoBrightnessService", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "Creating service");
        registerBrightnessModeObserver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        if (!mRegistered) {
            registerBrightnessModeObserver();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        super.onDestroy();
        unregisterBrightnessModeObserver();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerBrightnessModeObserver() {
        if (mRegistered) {
            return;
        }

        mBrightnessModeObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            private int mLastMode = -1;

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                int mode = Settings.System.getIntForUser(
                        getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                        UserHandle.USER_CURRENT);

                if (DEBUG) Log.d(TAG, "Brightness mode changed: " + mode + " (previous: " + mLastMode + ")");

                if (mLastMode != -1 && mLastMode != mode) {
                    if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                        // User toggled auto-brightness ON: reset manual override and short-term model
                        resetAutoBrightness();
                    } else {
                        // Auto-brightness turned OFF: reset adjustment for future toggle
                        resetAutoBrightnessAdjustment();
                    }
                }
                mLastMode = mode;
            }
        };

        try {
            getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
                    false,
                    mBrightnessModeObserver,
                    UserHandle.USER_ALL);
            mRegistered = true;
            if (DEBUG) Log.d(TAG, "Brightness mode observer registered successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register brightness mode observer", e);
        }
    }

    private void unregisterBrightnessModeObserver() {
        if (mRegistered && mBrightnessModeObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(mBrightnessModeObserver);
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister brightness mode observer", e);
            }
            mRegistered = false;
        }
    }

    private void resetAutoBrightness() {
        if (DEBUG) Log.d(TAG, "Resetting auto brightness configuration and adjustment");

        // 1. Reset SCREEN_AUTO_BRIGHTNESS_ADJ
        resetAutoBrightnessAdjustment();

        // 2. Clear ShortTermModel and custom user brightness configuration
        DisplayManager dm = getSystemService(DisplayManager.class);
        if (dm != null) {
            boolean resetSuccess = false;
            try {
                dm.setBrightnessConfiguration(null);
                resetSuccess = true;
                if (DEBUG) Log.d(TAG, "Called dm.setBrightnessConfiguration(null)");
            } catch (Throwable t) {
                if (DEBUG) Log.d(TAG, "dm.setBrightnessConfiguration(null) direct call failed, trying reflection", t);
            }

            if (!resetSuccess) {
                try {
                    Method method = dm.getClass().getMethod("setBrightnessConfigurationForUser",
                            BrightnessConfiguration.class, int.class, String.class);
                    method.invoke(dm, null, UserHandle.myUserId(), getPackageName());
                    resetSuccess = true;
                    if (DEBUG) Log.d(TAG, "Called setBrightnessConfigurationForUser via reflection");
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to reset brightness configuration via reflection", t);
                }
            }

            if (!resetSuccess) {
                try {
                    Runtime.getRuntime().exec(new String[]{"cmd", "display", "reset-brightness-configuration"});
                    if (DEBUG) Log.d(TAG, "Executed cmd display reset-brightness-configuration");
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to execute cmd display reset-brightness-configuration", t);
                }
            }
        }
    }

    private void resetAutoBrightnessAdjustment() {
        try {
            Settings.System.putFloatForUser(
                    getContentResolver(),
                    Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ,
                    0.0f,
                    UserHandle.USER_CURRENT);
            if (DEBUG) Log.d(TAG, "Reset SCREEN_AUTO_BRIGHTNESS_ADJ to 0.0");
        } catch (Exception e) {
            Log.e(TAG, "Failed to reset SCREEN_AUTO_BRIGHTNESS_ADJ", e);
        }
    }
}
