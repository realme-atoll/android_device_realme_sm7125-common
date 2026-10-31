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

    // Legacy string constants kept for backwards compatibility
    public static final String MODE_AUTO = "auto";
    public static final String MODE_ON = "on";
    public static final String MODE_OFF = "off";

    public static boolean isSupported() {
        return Utils.fileWritable(NODE_OTG_SWITCH);
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Utils.getSharedPreferences(context);
        try {
            return prefs.getBoolean(KEY, true);
        } catch (ClassCastException e) {
            // Migration from legacy String mode ("auto", "on", "off")
            String mode = prefs.getString(KEY, MODE_ON);
            boolean enabled = !MODE_OFF.equals(mode);
            prefs.edit().remove(KEY).putBoolean(KEY, enabled).apply();
            return enabled;
        }
    }

    public static void setEnabled(Context context, boolean enabled) {
        Utils.getSharedPreferences(context).edit().putBoolean(KEY, enabled).apply();
        apply(context, enabled);
    }

    public static boolean isOtgOnline() {
        String val = Utils.readLine(NODE_OTG_ONLINE);
        return val != null && "1".equals(val.trim());
    }

    public static boolean isOtgSwitchEnabled() {
        String val = Utils.readLine(NODE_OTG_SWITCH);
        return val != null && "1".equals(val.trim());
    }

    public static void apply(Context context, boolean enabled) {
        if (!isSupported()) {
            return;
        }

        Utils.writeLine(NODE_OTG_SWITCH, enabled ? "1" : "0");
        try {
            SystemProperties.set(PROP_OTG, enabled ? "1" : "0");
            if (enabled) {
                SystemProperties.set(PROP_USB_AUDIO, "1");
            }
        } catch (Exception ignored) {}

        if (context != null) {
            notifyTileUpdate(context);
        }
    }

    public static void restore(Context context) {
        if (!isSupported()) {
            return;
        }
        apply(context, isEnabled(context));
    }

    @Deprecated
    public static String getMode(Context context) {
        return isEnabled(context) ? MODE_ON : MODE_OFF;
    }

    @Deprecated
    public static void setMode(Context context, String mode) {
        setEnabled(context, !MODE_OFF.equals(mode));
    }

    public static void notifyTileUpdate(Context context) {
        try {
            TileService.requestListeningState(
                    context, new ComponentName(context, OtgTileService.class));
        } catch (Exception ignored) {}
    }
}
