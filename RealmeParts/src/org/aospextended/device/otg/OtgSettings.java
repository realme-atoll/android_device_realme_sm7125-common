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

package org.aospextended.device.otg;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.service.quicksettings.TileService;
import android.util.Log;

import org.aospextended.device.util.Utils;

public class OtgSettings {

    private static final String TAG = "OtgSettings";
    private static final boolean DEBUG = Utils.DEBUG;

    public static final String KEY = "otg_mode";
    private static final String LEGACY_KEY = "usb_mode";

    public static final String NODE_OTG_SWITCH = "/sys/class/power_supply/usb/otg_switch";
    public static final String NODE_OTG_ONLINE = "/sys/class/power_supply/usb/otg_online";

    public static final String PROP_OTG = "persist.sys.oplus.otg_support";
    public static final String PROP_USB_AUDIO = "persist.vendor.usb.audio";

    public static final String MODE_AUTO = "auto";
    public static final String MODE_ON = "on";
    public static final String MODE_OFF = "off";

    /** Timeout duration for AUTO mode inactivity: 10 minutes */
    private static final long AUTO_TIMEOUT_MS = 10 * 60 * 1000L;
    /** Poll interval while waiting to check for connected OTG devices: 30 seconds */
    private static final long POLL_INTERVAL_MS = 30 * 1000L;

    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static Context sContext;
    private static long sInactivityDurationMs = 0;
    private static boolean sWatchdogRunning = false;

    private static final Runnable sWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (sContext == null) {
                return;
            }

            String currentMode = getMode(sContext);
            if (!MODE_AUTO.equals(currentMode)) {
                stopAutoTimeoutWatchdog();
                return;
            }

            boolean isDeviceConnected = isOtgOnline();
            if (isDeviceConnected) {
                // Device connected: reset inactivity duration
                sInactivityDurationMs = 0;
                if (DEBUG) Log.d(TAG, "OTG device active, resetting inactivity timer");
                sHandler.postDelayed(this, POLL_INTERVAL_MS);
            } else {
                sInactivityDurationMs += POLL_INTERVAL_MS;
                if (DEBUG) Log.d(TAG, "OTG idle for " + (sInactivityDurationMs / 1000) + "s");

                if (sInactivityDurationMs >= AUTO_TIMEOUT_MS) {
                    Log.i(TAG, "OTG inactive for 10 minutes in Auto mode; powering down VBUS");
                    stopAutoTimeoutWatchdog();
                    // Power off OTG switch in kernel
                    Utils.writeLine(NODE_OTG_SWITCH, "0");
                    try {
                        SystemProperties.set(PROP_OTG, "0");
                    } catch (Exception ignored) {}
                    // Notify QS Tile if visible
                    notifyTileUpdate(sContext);
                } else {
                    sHandler.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        }
    };

    public static boolean isSupported() {
        return Utils.fileWritable(NODE_OTG_SWITCH);
    }

    public static String getMode(Context context) {
        SharedPreferences prefs = Utils.getSharedPreferences(context);
        if (prefs.contains(KEY)) {
            return prefs.getString(KEY, MODE_AUTO);
        }
        // Fallback for legacy preference key
        return prefs.getString(LEGACY_KEY, MODE_AUTO);
    }

    public static void setMode(Context context, String mode) {
        Utils.getSharedPreferences(context).edit().putString(KEY, mode).apply();
        applyMode(context, mode);
    }

    public static boolean isOtgOnline() {
        String val = Utils.readLine(NODE_OTG_ONLINE);
        return val != null && "1".equals(val.trim());
    }

    public static boolean isOtgSwitchEnabled() {
        String val = Utils.readLine(NODE_OTG_SWITCH);
        return val != null && "1".equals(val.trim());
    }

    public static void applyMode(Context context, String mode) {
        if (!isSupported()) {
            return;
        }

        sContext = context != null ? context.getApplicationContext() : null;

        switch (mode) {
            case MODE_AUTO:
                // Enable OTG VBUS
                Utils.writeLine(NODE_OTG_SWITCH, "1");
                try {
                    SystemProperties.set(PROP_OTG, "1");
                    SystemProperties.set(PROP_USB_AUDIO, "1");
                } catch (Exception ignored) {}
                startAutoTimeoutWatchdog();
                break;

            case MODE_ON:
                // Always on: cancel timeout watchdog, keep VBUS energized continuously
                stopAutoTimeoutWatchdog();
                Utils.writeLine(NODE_OTG_SWITCH, "1");
                try {
                    SystemProperties.set(PROP_OTG, "1");
                    SystemProperties.set(PROP_USB_AUDIO, "1");
                } catch (Exception ignored) {}
                break;

            case MODE_OFF:
                // Off: cancel timeout watchdog and de-energize VBUS
                stopAutoTimeoutWatchdog();
                Utils.writeLine(NODE_OTG_SWITCH, "0");
                try {
                    SystemProperties.set(PROP_OTG, "0");
                } catch (Exception ignored) {}
                break;

            default:
                break;
        }

        if (context != null) {
            notifyTileUpdate(context);
        }
    }

    public static void restore(Context context) {
        if (!isSupported()) {
            return;
        }
        applyMode(context, getMode(context));
    }

    private static synchronized void startAutoTimeoutWatchdog() {
        sInactivityDurationMs = 0;
        sHandler.removeCallbacks(sWatchdogRunnable);
        sHandler.postDelayed(sWatchdogRunnable, POLL_INTERVAL_MS);
        sWatchdogRunning = true;
        if (DEBUG) Log.d(TAG, "Started OTG Auto timeout watchdog");
    }

    private static synchronized void stopAutoTimeoutWatchdog() {
        sHandler.removeCallbacks(sWatchdogRunnable);
        sInactivityDurationMs = 0;
        sWatchdogRunning = false;
        if (DEBUG) Log.d(TAG, "Stopped OTG Auto timeout watchdog");
    }

    private static void notifyTileUpdate(Context context) {
        try {
            TileService.requestListeningState(
                    context, new ComponentName(context, OtgTileService.class));
        } catch (Exception ignored) {}
    }
}
