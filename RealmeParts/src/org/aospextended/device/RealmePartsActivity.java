/*
 * Copyright (c) 2020 The AospExtended Project
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

import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.collapsingtoolbar.R;

import org.aospextended.device.battery.ChargeLimitSettings;
import org.aospextended.device.display.DcDimmingSettings;
import org.aospextended.device.display.HbmSettings;
import org.aospextended.device.gestures.TouchGesturesActivity;
import org.aospextended.device.otg.OtgSettings;

/**
 * Hosts the main RealmeParts screen inside a collapsing Material toolbar, the
 * same chrome the system Settings app uses on Android 12+. The toolbar, title
 * (from the activity label) and Up/back navigation are all handled by
 * {@link CollapsingToolbarBaseActivity}, so no ActionBar wiring is needed here.
 */
public class RealmePartsActivity extends CollapsingToolbarBaseActivity {

    public static final String EXTRA_SCROLL_TO_PREF = "scroll_to_pref";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (handleSpecialTile(getIntent())) {
            return;
        }

        String targetPref = getTargetPreferenceKey(getIntent());

        if (getSupportFragmentManager().findFragmentById(R.id.content_frame) == null) {
            RealmeParts fragment = new RealmeParts();
            if (targetPref != null) {
                Bundle args = new Bundle();
                args.putString(":settings:fragment_args_key", targetPref);
                args.putString(EXTRA_SCROLL_TO_PREF, targetPref);
                fragment.setArguments(args);
            }
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, fragment)
                    .commit();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (handleSpecialTile(intent)) {
            return;
        }

        String targetPref = getTargetPreferenceKey(intent);
        if (targetPref != null) {
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.content_frame);
            if (fragment instanceof RealmeParts) {
                ((RealmeParts) fragment).scrollToPref(targetPref);
            }
        }
    }

    private boolean handleSpecialTile(Intent intent) {
        ComponentName component = getTileComponent(intent);
        if (component != null) {
            String className = component.getClassName();
            if (className.contains("TouchGesturesTileService")) {
                startActivity(new Intent(this, TouchGesturesActivity.class));
                finish();
                return true;
            }
        }
        return false;
    }

    public static ComponentName getTileComponent(Intent intent) {
        if (intent == null) {
            return null;
        }
        ComponentName component = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            component = intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName.class);
        } else {
            component = intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME);
        }
        if (component == null) {
            String compStr = intent.getStringExtra(Intent.EXTRA_COMPONENT_NAME);
            if (compStr != null) {
                component = ComponentName.unflattenFromString(compStr);
            }
        }
        if (component == null) {
            component = intent.getComponent();
        }
        return component;
    }

    public static String getTargetPreferenceKey(Intent intent) {
        ComponentName component = getTileComponent(intent);
        if (component != null) {
            String className = component.getClassName();
            if (className.contains("OtgTileService")) {
                return OtgSettings.KEY;
            } else if (className.contains("HbmTileService")) {
                return HbmSettings.KEY;
            } else if (className.contains("DcDimmingTileService")) {
                return DcDimmingSettings.KEY;
            } else if (className.contains("ChargeLimitTileService")) {
                return ChargeLimitSettings.KEY_ENABLE;
            }
        }
        return null;
    }
}
