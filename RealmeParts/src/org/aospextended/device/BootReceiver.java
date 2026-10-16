/*
 * Copyright (C) 2020 The AospExtended Project
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

package org.aospextended.device;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.preference.PreferenceManager;

import org.aospextended.device.gestures.TouchGestures;
import org.aospextended.device.util.Utils;
import org.aospextended.device.doze.DozeUtils;
import org.aospextended.device.gpu.GpuBoostSettings;
import org.aospextended.device.battery.ChargeLimitSettings;
import org.aospextended.device.battery.FastChargeSettings;
import org.aospextended.device.battery.ForceFastChargeSettings;
import org.aospextended.device.display.AutoBrightnessService;
import org.aospextended.device.display.DcDimmingSettings;
import org.aospextended.device.display.HbmSettings;
import org.aospextended.device.display.SkipPccSettings;
import org.aospextended.device.touch.GameTouchSettings;
import org.aospextended.device.touch.EdgeMistouchSettings;
import org.aospextended.device.otg.OtgSettings;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            enableComponent(context, org.aospextended.device.gestures.TouchGesturesActivity.class.getName());
            SharedPreferences prefs = Utils.getSharedPreferences(context);
            TouchGestures.enableGestures(prefs.getBoolean(
                TouchGestures.PREF_GESTURE_ENABLE, false));
            TouchGestures.enableDt2w(prefs.getBoolean(
                TouchGestures.PREF_DT2W_ENABLE, true));
            GpuBoostSettings.restore(context);
            ChargeLimitSettings.restore(context);
            FastChargeSettings.restore(context);
            ForceFastChargeSettings.restore(context);
            DcDimmingSettings.restore(context);
            HbmSettings.restore(context);
            SkipPccSettings.restore(context);
            GameTouchSettings.restore(context);
            EdgeMistouchSettings.restore(context);
            OtgSettings.restore(context);
            AutoBrightnessService.start(context);
        }

        DozeUtils.checkDozeService(context);
    }

    private void enableComponent(Context context, String component) {
        ComponentName name = new ComponentName(context, component);
        PackageManager pm = context.getPackageManager();
        if (pm.getComponentEnabledSetting(name)
                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(name,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }
}
