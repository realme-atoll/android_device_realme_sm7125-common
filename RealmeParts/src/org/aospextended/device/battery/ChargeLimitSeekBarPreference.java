/*
 * Copyright (C) 2026 ShadowBladeX
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

package org.aospextended.device.battery;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settingslib.widget.SliderPreference;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

import org.aospextended.device.R;
import org.aospextended.device.util.Utils;

/**
 * Android 16 Material 3 Slider for battery charge limit, matching Evolver's
 * Now Playing "Icon size" layout with discrete step minus/plus circular buttons,
 * M3 slider bar, live percentage summary, and reset button.
 */
public class ChargeLimitSeekBarPreference extends SliderPreference {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final int DEFAULT_MIN = 70;
    private static final int DEFAULT_MAX = 95;
    private static final int DEFAULT_STEP = 5;
    private static final int DEFAULT_VAL = 80;

    private int mDefaultValue = DEFAULT_VAL;
    private int mStep = DEFAULT_STEP;
    private CharSequence mUserSummary;
    private boolean mInUserDrag = false;

    public ChargeLimitSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        parseAttrs(attrs);
        initDefaults();
        mUserSummary = super.getSummary();
        updateSummaryNow();
    }

    public ChargeLimitSeekBarPreference(Context context) {
        super(context, null);
        initDefaults();
        mUserSummary = super.getSummary();
        updateSummaryNow();
    }

    private void parseAttrs(@Nullable AttributeSet attrs) {
        if (attrs == null) {
            setMin(DEFAULT_MIN);
            setMax(DEFAULT_MAX);
            setSliderIncrement(DEFAULT_STEP);
            return;
        }

        int min = attrs.getAttributeIntValue(ANDROID_NS, "min", DEFAULT_MIN);
        int max = attrs.getAttributeIntValue(ANDROID_NS, "max", DEFAULT_MAX);
        int defVal = attrs.getAttributeIntValue(ANDROID_NS, "defaultValue", DEFAULT_VAL);

        setMin(min);
        setMax(max);
        mDefaultValue = defVal;
        setSliderIncrement(mStep);
    }

    private String formatValue(int v) {
        if (v == mDefaultValue) {
            return v + "% (Default)";
        }
        return v + "%";
    }

    private void initDefaults() {
        setShowSliderValue(true);
        setLabelFormater(new LabelFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return formatValue((int) value);
            }
        });
    }

    @Override
    protected int getPersistedInt(int defaultReturnValue) {
        try {
            return super.getPersistedInt(defaultReturnValue);
        } catch (ClassCastException e) {
            int val = defaultReturnValue;
            try {
                String strVal = getPersistedString(String.valueOf(defaultReturnValue));
                val = Integer.parseInt(strVal);
            } catch (Exception ignored) {}
            persistInt(val);
            return val;
        }
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        int initialValue = getPersistedInt(mDefaultValue);
        setValue(initialValue);
    }

    @Override
    public void setSummary(CharSequence summary) {
        mUserSummary = summary;
        updateSummaryNow();
    }

    @Override
    public void setValue(int sliderValue) {
        super.setValue(sliderValue);
        if (!mInUserDrag) {
            updateSummaryNow();
        }
    }

    private void updateSummaryNow() {
        super.setSummary(composeSummary(mUserSummary, getValue()));
    }

    private CharSequence composeSummary(CharSequence userSummary, int v) {
        final String valueText = formatValue(v);
        if (userSummary == null || userSummary.length() == 0) {
            return valueText;
        }
        return valueText + " \u2022 " + userSummary;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        final TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
        if (summaryView != null) {
            summaryView.setText(composeSummary(mUserSummary, getValue()));
        }

        final Context context = getContext();
        final int idIconStartFrame = context.getResources().getIdentifier(
                "icon_start_frame", "id", context.getPackageName());
        final int idIconStart = context.getResources().getIdentifier(
                "icon_start", "id", context.getPackageName());
        final int idIconEndFrame = context.getResources().getIdentifier(
                "icon_end_frame", "id", context.getPackageName());
        final int idIconEnd = context.getResources().getIdentifier(
                "icon_end", "id", context.getPackageName());
        final int idSlider = context.getResources().getIdentifier(
                "slider", "id", context.getPackageName());

        ViewGroup mf = idIconStartFrame != 0 ? (ViewGroup) holder.findViewById(idIconStartFrame) : null;
        ImageView mi = idIconStart != 0 ? (ImageView) holder.findViewById(idIconStart) : null;
        ViewGroup pf = idIconEndFrame != 0 ? (ViewGroup) holder.findViewById(idIconEndFrame) : null;
        ImageView pi = idIconEnd != 0 ? (ImageView) holder.findViewById(idIconEnd) : null;
        final Slider slider = idSlider != 0 ? (Slider) holder.findViewById(idSlider) : null;

        // Fallbacks using SettingsLib slider layout resource IDs if available
        if (mf == null) {
            final int slMinusFrame = context.getResources().getIdentifier(
                    "icon_start_frame", "id", "com.android.settingslib.widget");
            final int slMinusIcon = context.getResources().getIdentifier(
                    "icon_start", "id", "com.android.settingslib.widget");
            final int slPlusFrame = context.getResources().getIdentifier(
                    "icon_end_frame", "id", "com.android.settingslib.widget");
            final int slPlusIcon = context.getResources().getIdentifier(
                    "icon_end", "id", "com.android.settingslib.widget");
            if (slMinusFrame != 0) mf = (ViewGroup) holder.findViewById(slMinusFrame);
            if (slMinusIcon != 0) mi = (ImageView) holder.findViewById(slMinusIcon);
            if (slPlusFrame != 0) pf = (ViewGroup) holder.findViewById(slPlusFrame);
            if (slPlusIcon != 0) pi = (ImageView) holder.findViewById(slPlusIcon);
        }

        final ViewGroup minusFrame = mf;
        final ImageView minusIcon = mi;
        final ViewGroup plusFrame = pf;
        final ImageView plusIcon = pi;

        final int step = Math.max(1, getSliderIncrement());

        if (minusFrame != null && minusIcon != null) {
            minusFrame.setVisibility(View.VISIBLE);
            minusIcon.setImageResource(R.drawable.ic_custom_seekbar_minus);
            minusFrame.setOnClickListener(v -> {
                if (!isEnabled()) return;
                int current = slider != null ? Math.round(slider.getValue()) : getValue();
                int newVal = Math.max(getMin(), current - step);
                applyUserValue(newVal, slider);
                updateButtonsState(holder, minusFrame, minusIcon, plusFrame, plusIcon);
            });
        }

        if (plusFrame != null && plusIcon != null) {
            plusFrame.setVisibility(View.VISIBLE);
            plusIcon.setImageResource(R.drawable.ic_custom_seekbar_plus);
            plusFrame.setOnClickListener(v -> {
                if (!isEnabled()) return;
                int current = slider != null ? Math.round(slider.getValue()) : getValue();
                int newVal = Math.min(getMax(), current + step);
                applyUserValue(newVal, slider);
                updateButtonsState(holder, minusFrame, minusIcon, plusFrame, plusIcon);
            });
        }

        updateButtonsState(holder, minusFrame, minusIcon, plusFrame, plusIcon);

        final int idLabelFrame = context.getResources().getIdentifier(
                "label_frame", "id", context.getPackageName());
        View labelFrame = idLabelFrame != 0 ? holder.findViewById(idLabelFrame) : null;
        if (labelFrame == null) {
            final int slLabelFrame = context.getResources().getIdentifier(
                    "label_frame", "id", "com.android.settingslib.widget");
            if (slLabelFrame != 0) {
                labelFrame = holder.findViewById(slLabelFrame);
            }
        }
        if (labelFrame != null) {
            labelFrame.setVisibility(View.VISIBLE);
        }

        final TextView endText = (TextView) holder.findViewById(android.R.id.text2);
        if (endText != null) {
            attachResetIcon(endText);
        }

        if (slider != null && summaryView != null) {
            slider.addOnChangeListener((s, value, fromUser) -> {
                if (fromUser) {
                    summaryView.setText(composeSummary(mUserSummary, (int) value));
                    updateButtonsState(holder, minusFrame, minusIcon, plusFrame, plusIcon);
                }
            });
            slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                @Override
                public void onStartTrackingTouch(@NonNull Slider s) {
                    mInUserDrag = true;
                }

                @Override
                public void onStopTrackingTouch(@NonNull Slider s) {
                    mInUserDrag = false;
                    applyUserValue(Math.round(s.getValue()), s);
                    updateButtonsState(holder, minusFrame, minusIcon, plusFrame, plusIcon);
                }
            });
        }
    }

    private void updateButtonsState(PreferenceViewHolder holder,
                                    @Nullable View minusFrame, @Nullable ImageView minusIcon,
                                    @Nullable View plusFrame, @Nullable ImageView plusIcon) {
        boolean enabled = isEnabled();
        int value = getValue();

        if (minusFrame != null && minusIcon != null) {
            boolean canMinus = enabled && (value > getMin());
            minusFrame.setEnabled(canMinus);
            minusIcon.setEnabled(canMinus);
            minusIcon.setAlpha(canMinus ? 1.0f : 0.35f);
        }
        if (plusFrame != null && plusIcon != null) {
            boolean canPlus = enabled && (value < getMax());
            plusFrame.setEnabled(canPlus);
            plusIcon.setEnabled(canPlus);
            plusIcon.setAlpha(canPlus ? 1.0f : 0.35f);
        }
    }

    private void applyUserValue(int newVal, @Nullable Slider slider) {
        if (newVal == getValue()) return;
        if (!callChangeListener(newVal)) {
            if (slider != null) {
                slider.setValue(getValue());
            }
            return;
        }
        setValue(newVal);
        persistInt(newVal);
        updateSummaryNow();
        notifyChanged();
    }

    private void attachResetIcon(TextView tv) {
        final Drawable icon = ResourcesCompat.getDrawable(
                tv.getResources(), R.drawable.ic_custom_seekbar_reset, tv.getContext().getTheme());
        if (icon == null) return;

        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icon, null);
        tv.setCompoundDrawablePadding(dp(tv, 6));
        tv.setClickable(isEnabled());
        tv.setFocusable(isEnabled());

        final int tapSlop = dp(tv, 8);
        tv.setOnTouchListener((v, ev) -> {
            if (!isEnabled() || ev.getAction() != MotionEvent.ACTION_UP) return false;

            final boolean isRtl = ViewCompat.getLayoutDirection(tv) == ViewCompat.LAYOUT_DIRECTION_RTL;
            final Drawable[] drs = tv.getCompoundDrawablesRelative();
            final Drawable end = drs[2];
            if (end == null) return false;

            final int iconW = end.getIntrinsicWidth();
            final int x = (int) ev.getX();

            if (!isRtl) {
                final int left = tv.getWidth() - ViewCompat.getPaddingEnd(tv) - iconW - tapSlop;
                if (x >= left) {
                    performReset();
                    return true;
                }
            } else {
                final int right = ViewCompat.getPaddingStart(tv) + iconW + tapSlop;
                if (x <= right) {
                    performReset();
                    return true;
                }
            }
            return false;
        });
    }

    private void performReset() {
        applyUserValue(mDefaultValue, null);
    }

    private static int dp(TextView v, int dp) {
        return Math.round(dp * v.getResources().getDisplayMetrics().density);
    }
}
