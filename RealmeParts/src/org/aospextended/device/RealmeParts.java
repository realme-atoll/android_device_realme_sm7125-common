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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.graphics.Color;
import androidx.preference.Preference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.TwoStatePreference;

import org.aospextended.device.gestures.TouchGestures;
import org.aospextended.device.gestures.TouchGesturesActivity;
import org.aospextended.device.doze.DozeSettingsActivity;
import org.aospextended.device.gpu.GpuBoostSettings;
import org.aospextended.device.battery.ChargeLimitSeekBarPreference;
import org.aospextended.device.battery.ChargeLimitSettings;
import org.aospextended.device.battery.ChargeLimitService;
import org.aospextended.device.battery.FastChargeSettings;
import org.aospextended.device.battery.ForceFastChargeSettings;
import org.aospextended.device.display.DcDimmingSettings;
import org.aospextended.device.display.HbmSettings;
import org.aospextended.device.display.SkipPccSettings;
import org.aospextended.device.touch.GameTouchSettings;
import org.aospextended.device.touch.EdgeMistouchSettings;
import org.aospextended.device.speaker.ClearSpeakerHelper;
import org.aospextended.device.otg.OtgSettings;

import android.util.Log;
import android.os.SystemProperties;
import android.widget.Toast;

import org.aospextended.device.R;
import org.aospextended.device.util.Utils;

public class RealmeParts extends SettingsBasePreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final boolean DEBUG = Utils.DEBUG;
    private static final String TAG = "RealmeParts";

    private Context mContext;
    private SharedPreferences mPreferences;

    private PrimarySwitchPreference mDozePref;
    private Preference mGesturesPref;
    private ListPreference mGpuBoost;
    private SwitchPreferenceCompat mChargeLimitEnable;
    private ChargeLimitSeekBarPreference mChargeLimitLevel;
    private SwitchPreferenceCompat mDcDimming;
    private SwitchPreferenceCompat mHbm;
    private SwitchPreferenceCompat mGameTouch;
    private SwitchPreferenceCompat mEdgeMistouch;
    private SwitchPreferenceCompat mClearSpeaker;
    private ListPreference mOtgMode;
    private SwitchPreferenceCompat mSkipPcc;
    private SwitchPreferenceCompat mFastCharge;
    private SwitchPreferenceCompat mForceFastCharge;

    @Override

    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context context = getContext();
        if (context != null) {
            for (SharedPreferences prefs : new SharedPreferences[]{
                    Utils.getSharedPreferences(context),
                    PreferenceManager.getDefaultSharedPreferences(context)}) {
                if (prefs == null) continue;
                try {
                    prefs.getInt(ChargeLimitSettings.KEY_LEVEL, ChargeLimitSettings.DEFAULT_LEVEL_INT);
                } catch (ClassCastException e) {
                    int level = ChargeLimitSettings.DEFAULT_LEVEL_INT;
                    try {
                        String s = prefs.getString(ChargeLimitSettings.KEY_LEVEL, ChargeLimitSettings.DEFAULT_LEVEL);
                        level = Integer.parseInt(s);
                    } catch (Exception ignored) {}
                    prefs.edit().remove(ChargeLimitSettings.KEY_LEVEL)
                            .putInt(ChargeLimitSettings.KEY_LEVEL, level).commit();
                }
            }
        }

        setPreferencesFromResource(R.xml.RealmeParts, rootKey);

        PreferenceCategory display = (PreferenceCategory) getPreferenceScreen()
                 .findPreference("display_category");
        mDcDimming = (SwitchPreferenceCompat) findPreference(DcDimmingSettings.KEY);
        if (DcDimmingSettings.isSupported()) {
            mDcDimming.setChecked(DcDimmingSettings.isEnabled(getContext()));
            mDcDimming.setOnPreferenceChangeListener(this);
        } else if (display != null && mDcDimming != null) {
            display.removePreference(mDcDimming);
        }

        mHbm = (SwitchPreferenceCompat) findPreference(HbmSettings.KEY);
        if (HbmSettings.isSupported()) {
            mHbm.setChecked(HbmSettings.isEnabled(getContext()));
            mHbm.setOnPreferenceChangeListener(this);
        } else if (display != null && mHbm != null) {
            display.removePreference(mHbm);
        }

        mSkipPcc = (SwitchPreferenceCompat) findPreference(SkipPccSettings.KEY);
        if (SkipPccSettings.isSupported()) {
            mSkipPcc.setChecked(SkipPccSettings.isEnabled(getContext()));
            mSkipPcc.setOnPreferenceChangeListener(this);
            mSkipPcc.setSummary(R.string.skip_pcc_summary);
        } else if (display != null && mSkipPcc != null) {
            display.removePreference(mSkipPcc);
        }

        if (display != null && display.getPreferenceCount() == 0) {
            getPreferenceScreen().removePreference(display);
        }

        PreferenceCategory gestures = (PreferenceCategory) getPreferenceScreen()
                 .findPreference("gestures_category");
        mGesturesPref = findPreference("screen_gestures");
        mGesturesPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getContext(), TouchGesturesActivity.class);
                startActivity(intent);
                return true;
            }
        });
        if (!TouchGestures.isSupported()) {
            getPreferenceScreen().removePreference(gestures);
        }

        PreferenceCategory dozeCategory = (PreferenceCategory) getPreferenceScreen()
                 .findPreference("doze_category");
        mDozePref = (PrimarySwitchPreference) findPreference("doze");
        if (mDozePref != null) {
            mDozePref.setChecked(org.aospextended.device.doze.DozeUtils.isDozeEnabled(getContext()));
            mDozePref.setOnPreferenceChangeListener(this);
            mDozePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(getContext(), DozeSettingsActivity.class);
                    startActivity(intent);
                    return true;
                }
            });
        }
        if (!org.aospextended.device.doze.DozeUtils.isDozeAvailable(getContext())) {
            getPreferenceScreen().removePreference(dozeCategory);
        }

        PreferenceCategory touch = (PreferenceCategory) getPreferenceScreen()
                 .findPreference("touch_category");
        mGameTouch = (SwitchPreferenceCompat) findPreference(GameTouchSettings.KEY);
        if (GameTouchSettings.isSupported()) {
            mGameTouch.setChecked(GameTouchSettings.isEnabled(getContext()));
            mGameTouch.setOnPreferenceChangeListener(this);
        } else if (touch != null && mGameTouch != null) {
            touch.removePreference(mGameTouch);
        }

        mEdgeMistouch = (SwitchPreferenceCompat) findPreference(EdgeMistouchSettings.KEY);
        if (EdgeMistouchSettings.isSupported()) {
            mEdgeMistouch.setChecked(EdgeMistouchSettings.isEnabled(getContext()));
            mEdgeMistouch.setOnPreferenceChangeListener(this);
        } else if (touch != null && mEdgeMistouch != null) {
            touch.removePreference(mEdgeMistouch);
        }

        if (touch != null && touch.getPreferenceCount() == 0) {
            getPreferenceScreen().removePreference(touch);
        }

        PreferenceCategory performance = (PreferenceCategory) getPreferenceScreen()
                 .findPreference("performance_category");
        mGpuBoost = (ListPreference) findPreference(GpuBoostSettings.KEY);
        if (GpuBoostSettings.isSupported()) {
            mGpuBoost.setValue(GpuBoostSettings.getValue(getContext()));
            // SimpleSummaryProvider shows the current entry as the summary and
            // updates itself on change. It also avoids the String.format crash
            // that the manual setSummary path hit on entries containing '%'.
            mGpuBoost.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            mGpuBoost.setOnPreferenceChangeListener(this);
        } else if (performance != null) {
            getPreferenceScreen().removePreference(performance);
        }

        PreferenceCategory battery = (PreferenceCategory) getPreferenceScreen()
                 .findPreference("battery_category");
        mChargeLimitEnable = (SwitchPreferenceCompat) findPreference(ChargeLimitSettings.KEY_ENABLE);
        mChargeLimitLevel = (ChargeLimitSeekBarPreference) findPreference(ChargeLimitSettings.KEY_LEVEL);
        if (ChargeLimitSettings.isSupported()) {
            mChargeLimitEnable.setOnPreferenceChangeListener(this);
            if (mChargeLimitLevel != null) {
                mChargeLimitLevel.setValue(ChargeLimitSettings.getLevel(getContext()));
                mChargeLimitLevel.setOnPreferenceChangeListener(this);
            }
        } else if (battery != null) {
            if (mChargeLimitEnable != null) battery.removePreference(mChargeLimitEnable);
            if (mChargeLimitLevel != null) battery.removePreference(mChargeLimitLevel);
        }

        mFastCharge = (SwitchPreferenceCompat) findPreference(FastChargeSettings.KEY);
        if (FastChargeSettings.isSupported()) {
            mFastCharge.setChecked(FastChargeSettings.isEnabled(getContext()));
            mFastCharge.setOnPreferenceChangeListener(this);
        } else if (battery != null && mFastCharge != null) {
            battery.removePreference(mFastCharge);
        }

        if (battery != null && battery.getPreferenceCount() == 0) {
            getPreferenceScreen().removePreference(battery);
        }

        PreferenceCategory audio = (PreferenceCategory) getPreferenceScreen()
                 .findPreference("audio_category");
        mClearSpeaker = (SwitchPreferenceCompat) findPreference("clear_speaker");
        if (ClearSpeakerHelper.isSupported(getContext())) {
            if (mClearSpeaker != null) {
                mClearSpeaker.setChecked(ClearSpeakerHelper.getInstance().isRunning());
                mClearSpeaker.setOnPreferenceChangeListener(this);
            }
        } else if (audio != null) {
            getPreferenceScreen().removePreference(audio);
        }

        PreferenceCategory usb = (PreferenceCategory) getPreferenceScreen()
                 .findPreference("usb_category");
        mOtgMode = (ListPreference) findPreference(OtgSettings.KEY);
        if (OtgSettings.isSupported()) {
            mOtgMode.setValue(OtgSettings.getMode(getContext()));
            mOtgMode.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            mOtgMode.setOnPreferenceChangeListener(this);
        } else if (usb != null && mOtgMode != null) {
            usb.removePreference(mOtgMode);
        }

        mForceFastCharge = (SwitchPreferenceCompat) findPreference(ForceFastChargeSettings.KEY);
        if (ForceFastChargeSettings.isSupported()) {
            mForceFastCharge.setChecked(ForceFastChargeSettings.isEnabled(getContext()));
            mForceFastCharge.setOnPreferenceChangeListener(this);
        } else if (usb != null && mForceFastCharge != null) {
            usb.removePreference(mForceFastCharge);
        }

        if (usb != null && usb.getPreferenceCount() == 0) {
            getPreferenceScreen().removePreference(usb);
        }

    }

    @Override
    public void onViewCreated(android.view.View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String targetPref = null;
        if (getArguments() != null) {
            targetPref = getArguments().getString(RealmePartsActivity.EXTRA_SCROLL_TO_PREF);
            if (targetPref == null) {
                targetPref = getArguments().getString(":settings:fragment_args_key");
            }
        }
        if (targetPref == null && getActivity() != null) {
            targetPref = RealmePartsActivity.getTargetPreferenceKey(getActivity().getIntent());
        }
        if (targetPref != null) {
            scrollToPref(targetPref);
        }
    }

    public void scrollToPref(final String key) {
        if (key == null) return;
        if (getView() != null) {
            getView().post(() -> {
                Preference pref = findPreference(key);
                if (pref != null) {
                    scrollToPreference(pref);
                }
            });
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();
        // ListPreference summaries refresh automatically via SimpleSummaryProvider
        // once the new value is applied, so no manual setSummary is needed here.
        if (GpuBoostSettings.KEY.equals(key)) {
            GpuBoostSettings.setValue((String) newValue);
        } else if (ChargeLimitSettings.KEY_ENABLE.equals(key)) {
            ChargeLimitSettings.onEnableChanged(getContext(), (Boolean) newValue);
        } else if (ChargeLimitSettings.KEY_LEVEL.equals(key)) {
            int value;
            if (newValue instanceof Integer) {
                value = (Integer) newValue;
            } else {
                try {
                    value = Integer.parseInt(newValue.toString());
                } catch (NumberFormatException e) {
                    value = ChargeLimitSettings.DEFAULT_LEVEL_INT;
                }
            }
            // Persist now (the framework persists only after we return) so the
            // monitor re-evaluates against the new cap immediately.
            Utils.getSharedPreferences(getContext()).edit()
                    .putInt(ChargeLimitSettings.KEY_LEVEL, value).apply();
            ChargeLimitService.start(getContext());
        } else if (DcDimmingSettings.KEY.equals(key)) {
            boolean enabled = (Boolean) newValue;
            DcDimmingSettings.setEnabled(getContext(), enabled);
        } else if (HbmSettings.KEY.equals(key)) {
            boolean enabled = (Boolean) newValue;
            HbmSettings.setEnabled(getContext(), enabled);
        } else if (GameTouchSettings.KEY.equals(key)) {
            boolean enabled = (Boolean) newValue;
            GameTouchSettings.setEnabled(enabled);
            Utils.getSharedPreferences(getContext()).edit()
                    .putBoolean(GameTouchSettings.KEY, enabled).apply();
        } else if (EdgeMistouchSettings.KEY.equals(key)) {
            boolean enabled = (Boolean) newValue;
            EdgeMistouchSettings.setEnabled(enabled);
            Utils.getSharedPreferences(getContext()).edit()
                    .putBoolean(EdgeMistouchSettings.KEY, enabled).apply();
        } else if (SkipPccSettings.KEY.equals(key)) {
            boolean enabled = (Boolean) newValue;
            SkipPccSettings.setEnabled(enabled);
            Utils.getSharedPreferences(getContext()).edit()
                    .putBoolean(SkipPccSettings.KEY, enabled).apply();
        } else if (FastChargeSettings.KEY.equals(key)) {
            boolean enabled = (Boolean) newValue;
            FastChargeSettings.setEnabled(enabled);
            Utils.getSharedPreferences(getContext()).edit()
                    .putBoolean(FastChargeSettings.KEY, enabled).apply();
        } else if (ForceFastChargeSettings.KEY.equals(key)) {
            boolean enabled = (Boolean) newValue;
            ForceFastChargeSettings.setEnabled(enabled);
            Utils.getSharedPreferences(getContext()).edit()
                    .putBoolean(ForceFastChargeSettings.KEY, enabled).apply();
        } else if (OtgSettings.KEY.equals(key)) {
            String mode = (String) newValue;
            OtgSettings.setMode(getContext(), mode);
        } else if ("clear_speaker".equals(key)) {

            boolean enable = (Boolean) newValue;
            if (enable) {
                ClearSpeakerHelper.getInstance().start(getContext(), new ClearSpeakerHelper.Listener() {
                    @Override
                    public void onProgress(int secondsRemaining) {
                        if (mClearSpeaker != null && isAdded()) {
                            mClearSpeaker.setSummary(getString(R.string.clear_speaker_running, secondsRemaining));
                        }
                    }

                    @Override
                    public void onFinished() {
                        if (mClearSpeaker != null && isAdded()) {
                            mClearSpeaker.setChecked(false);
                            mClearSpeaker.setSummary(R.string.clear_speaker_summary);
                        }
                    }
                });
            } else {
                ClearSpeakerHelper.getInstance().stop();
                mClearSpeaker.setSummary(R.string.clear_speaker_summary);
            }
        } else if ("doze".equals(key)) {
            boolean enabled = (Boolean) newValue;
            org.aospextended.device.doze.DozeUtils.enableDoze(getContext(), enabled);
            org.aospextended.device.doze.DozeUtils.checkDozeService(getContext());
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mDozePref != null) {
            mDozePref.setChecked(org.aospextended.device.doze.DozeUtils.isDozeEnabled(getContext()));
        }
        if (mHbm != null) {
            mHbm.setChecked(HbmSettings.isEnabled(getContext()));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ClearSpeakerHelper.getInstance().isRunning()) {
            ClearSpeakerHelper.getInstance().stop();
        }
    }
}
