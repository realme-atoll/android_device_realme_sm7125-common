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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import androidx.fragment.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import androidx.preference.SwitchPreferenceCompat;

import org.aospextended.device.R;
import org.aospextended.device.util.Utils;

public class DozeSettingsFragment extends SettingsBasePreferenceFragment implements OnPreferenceChangeListener,
        CompoundButton.OnCheckedChangeListener {

    private MainSwitchPreference mMainSwitch;

    private SwitchPreferenceCompat mAlwaysOnDisplayPreference;

    private SwitchPreferenceCompat mPickUpPreference;
    private SwitchPreferenceCompat mApproachPreference;
    private SwitchPreferenceCompat mHandwavePreference;
    private SwitchPreferenceCompat mPocketPreference;
    private SwitchPreferenceCompat mRaiseToWakePreference;
    private SwitchPreferenceCompat mDoubleTapPreference;

    private Handler mHandler = new Handler();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.doze_panel);

        SharedPreferences prefs = Utils.getSharedPreferences(getActivity());

        if (savedInstanceState == null && !prefs.getBoolean("first_help_shown", false)) {
            showHelp();
        }

        boolean dozeEnabled = DozeUtils.isDozeEnabled(getActivity());

        mMainSwitch = (MainSwitchPreference) findPreference("doze_enabled");
        if (mMainSwitch != null) {
            mMainSwitch.setChecked(dozeEnabled);
            mMainSwitch.addOnSwitchChangeListener(this);
        }

        mAlwaysOnDisplayPreference = (SwitchPreferenceCompat) findPreference(DozeUtils.ALWAYS_ON_DISPLAY);
        mAlwaysOnDisplayPreference.setEnabled(dozeEnabled);
        mAlwaysOnDisplayPreference.setChecked(DozeUtils.isAlwaysOnEnabled(getActivity()));
        mAlwaysOnDisplayPreference.setOnPreferenceChangeListener(this);

        PreferenceCategory pickupSensorCategory = (PreferenceCategory) getPreferenceScreen().
                findPreference(DozeUtils.CATEG_PICKUP_SENSOR);
        PreferenceCategory proximitySensorCategory = (PreferenceCategory) getPreferenceScreen().
                findPreference(DozeUtils.CATEG_PROX_SENSOR);

        SensorManager sm = getActivity().getSystemService(SensorManager.class);

        mPickUpPreference = (SwitchPreferenceCompat) findPreference(DozeUtils.GESTURE_PICK_UP_KEY);
        boolean hasPickup = sm != null && (sm.getDefaultSensor(Sensor.TYPE_PICK_UP_GESTURE, true) != null
                || sm.getDefaultSensor(Sensor.TYPE_TILT_DETECTOR, true) != null
                || DozeUtils.getSensor(sm, "android.sensor.tilt_detector") != null);
        if (hasPickup) {
            mPickUpPreference.setEnabled(dozeEnabled);
            mPickUpPreference.setOnPreferenceChangeListener(this);
        } else if (pickupSensorCategory != null && mPickUpPreference != null) {
            pickupSensorCategory.removePreference(mPickUpPreference);
        }

        mApproachPreference = (SwitchPreferenceCompat) findPreference(DozeUtils.GESTURE_APPROACH_KEY);
        if (mApproachPreference == null) {
            mApproachPreference = (SwitchPreferenceCompat) findPreference(DozeUtils.GESTURE_AMBIENT_MOTION_KEY);
        }
        boolean hasApproach = sm != null && (sm.getDefaultSensor(Sensor.TYPE_MOTION_DETECT, true) != null
                || sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION, true) != null
                || DozeUtils.getSensor(sm, "qti.sensor.amd") != null
                || sm.getDefaultSensor(Sensor.TYPE_PROXIMITY, true) != null
                || sm.getDefaultSensor(Sensor.TYPE_PROXIMITY, false) != null);
        if (hasApproach && mApproachPreference != null) {
            mApproachPreference.setEnabled(dozeEnabled);
            mApproachPreference.setOnPreferenceChangeListener(this);
        } else if (pickupSensorCategory != null && mApproachPreference != null) {
            pickupSensorCategory.removePreference(mApproachPreference);
        }

        mRaiseToWakePreference = (SwitchPreferenceCompat) findPreference(DozeUtils.GESTURE_RAISE_TO_WAKE_KEY);
        boolean hasTilt = sm != null && sm.getDefaultSensor(Sensor.TYPE_TILT_DETECTOR, true) != null;
        if (hasTilt && mRaiseToWakePreference != null) {
            mRaiseToWakePreference.setEnabled(dozeEnabled);
            mRaiseToWakePreference.setOnPreferenceChangeListener(this);
        } else if (pickupSensorCategory != null && mRaiseToWakePreference != null) {
            pickupSensorCategory.removePreference(mRaiseToWakePreference);
        }

        mDoubleTapPreference = (SwitchPreferenceCompat) findPreference(DozeUtils.GESTURE_DOUBLE_TAP);
        if (mDoubleTapPreference != null) {
            mDoubleTapPreference.setEnabled(dozeEnabled);
            mDoubleTapPreference.setOnPreferenceChangeListener(this);
        }

        if (pickupSensorCategory != null && pickupSensorCategory.getPreferenceCount() == 0) {
            getPreferenceScreen().removePreference(pickupSensorCategory);
        }

        boolean hasProx = sm != null && sm.getDefaultSensor(Sensor.TYPE_PROXIMITY, true) != null;
        mHandwavePreference = (SwitchPreferenceCompat) findPreference(DozeUtils.GESTURE_HAND_WAVE_KEY);
        if (hasProx && mHandwavePreference != null) {
            mHandwavePreference.setEnabled(dozeEnabled);
            mHandwavePreference.setOnPreferenceChangeListener(this);
        } else if (proximitySensorCategory != null && mHandwavePreference != null) {
            proximitySensorCategory.removePreference(mHandwavePreference);
        }

        mPocketPreference = (SwitchPreferenceCompat) findPreference(DozeUtils.GESTURE_POCKET_KEY);
        if (hasProx && mPocketPreference != null) {
            mPocketPreference.setEnabled(dozeEnabled);
            mPocketPreference.setOnPreferenceChangeListener(this);
        } else if (proximitySensorCategory != null && mPocketPreference != null) {
            proximitySensorCategory.removePreference(mPocketPreference);
        }

        // Hide proximity sensor category if proximity is not supported or empty
        if (!DozeUtils.getProxCheckBeforePulse(getActivity()) || !hasProx
                || (proximitySensorCategory != null && proximitySensorCategory.getPreferenceCount() == 0)) {
            if (proximitySensorCategory != null) {
                getPreferenceScreen().removePreference(proximitySensorCategory);
            }
        }

        // Hide AOD if not supported and set all its dependents otherwise
        if (!DozeUtils.alwaysOnDisplayAvailable(getActivity())) {
            getPreferenceScreen().removePreference(mAlwaysOnDisplayPreference);
        } else {
            if (pickupSensorCategory != null && getPreferenceScreen().findPreference(DozeUtils.CATEG_PICKUP_SENSOR) != null) {
                pickupSensorCategory.setDependency(DozeUtils.ALWAYS_ON_DISPLAY);
            }
            if (proximitySensorCategory != null && getPreferenceScreen().findPreference(DozeUtils.CATEG_PROX_SENSOR) != null) {
                proximitySensorCategory.setDependency(DozeUtils.ALWAYS_ON_DISPLAY);
            }
            if (mPickUpPreference != null && hasTilt && mRaiseToWakePreference != null) {
                mPickUpPreference.setDependency(DozeUtils.GESTURE_RAISE_TO_WAKE_KEY);
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (DozeUtils.ALWAYS_ON_DISPLAY.equals(preference.getKey())) {
            DozeUtils.enableAlwaysOn(getActivity(), (Boolean) newValue);
        }

        mHandler.post(() -> DozeUtils.checkDozeService(getActivity()));

        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        DozeUtils.enableDoze(getActivity(), isChecked);
        DozeUtils.checkDozeService(getActivity());

        if (mAlwaysOnDisplayPreference != null) {
            mAlwaysOnDisplayPreference.setEnabled(isChecked);
            if (!isChecked) {
                DozeUtils.enableAlwaysOn(getActivity(), false);
                mAlwaysOnDisplayPreference.setChecked(false);
            }
        }

        mPickUpPreference.setEnabled(isChecked);
        if (mApproachPreference != null) {
            mApproachPreference.setEnabled(isChecked);
        }
        mRaiseToWakePreference.setEnabled(isChecked);
        mHandwavePreference.setEnabled(isChecked);
        mPocketPreference.setEnabled(isChecked);
        mDoubleTapPreference.setEnabled(isChecked);
    }

    public static class HelpDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.doze_settings_help_title)
                    .setMessage(R.string.doze_settings_help_text)
                    .setNegativeButton(R.string.dialog_ok, (dialog, which) -> dialog.cancel())
                    .create();
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            Utils.getSharedPreferences(getActivity())
                    .edit()
                    .putBoolean("first_help_shown", true)
                    .commit();
        }
    }

    private void showHelp() {
        HelpDialogFragment fragment = new HelpDialogFragment();
        fragment.show(getParentFragmentManager(), "help_dialog");
    }
}
