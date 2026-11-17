/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2018 The LineageOS Project
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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.util.Log;

import org.aospextended.device.util.Utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ApproachSensor implements SensorEventListener {

    private static final boolean DEBUG = Utils.DEBUG;
    private static final String TAG = "ApproachSensor";

    private static final String AMD_SENSOR = "qti.sensor.amd";
    private static final int MIN_PULSE_INTERVAL_MS = 6500; // 6.5s delay between pulses to prevent double wake after ambient display
    private static final int SETTLING_DELAY_MS = 2000;     // 2.0s optical settling filter to ignore turn-off glitches on screen off
    private static final int STILL_REST_TIME_MS = 2000;    // 2.0s of continuous stillness required on a surface to return to rest
    private static final int WAKELOCK_TIMEOUT_MS = 3000;   // 3.0s wakelock to keep CPU awake while SystemUI turns display on
    private static final float MOTION_THRESHOLD = 1.5f;    // 1.5 m/s^2 delta to ignore footsteps (<0.5) and detect pickup (>2.0)

    private SensorManager mSensorManager;
    private Sensor mAccelSensor;
    private Sensor mTriggerSensor;
    private Sensor mProxSensor;
    private Context mContext;
    private ExecutorService mExecutorService;
    private PowerManager mPowerManager;
    private WakeLock mWakeLock;

    private long mEntryTimestamp = 0;
    private long mEnableTimestamp = 0;
    private long mLastMotionTimestamp = 0;
    private boolean mSawNear = false;
    private boolean mHasProxReading = false;
    private boolean mEnabled = false;
    private boolean mAtRest = true;

    private float mLastX, mLastY, mLastZ;
    private boolean mHasLastAccel = false;

    private final TriggerEventListener mTriggerListener = new TriggerEventListener() {
        @Override
        public void onTrigger(TriggerEvent event) {
            if (DEBUG) Log.d(TAG, "Got hardware motion trigger: " + event.sensor.getName());
            mLastMotionTimestamp = SystemClock.elapsedRealtime();
            mAtRest = false;
            triggerPulse();
        }
    };

    public ApproachSensor(Context context) {
        mContext = context;
        mSensorManager = mContext.getSystemService(SensorManager.class);
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mExecutorService = Executors.newSingleThreadExecutor();

        if (mSensorManager != null) {
            // 1. Accelerometer for shake / movement detection
            mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

            // 2. Hardware motion trigger (prioritize wakeup Significant Motion over fallback)
            mTriggerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION, true);
            if (mTriggerSensor == null) {
                mTriggerSensor = DozeUtils.getSensor(mSensorManager, AMD_SENSOR);
            }
            if (mTriggerSensor == null) {
                mTriggerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MOTION_DETECT, true);
            }

            // 3. Proximity sensor for approach / hand-wave detection
            mProxSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true);
            if (mProxSensor == null) {
                mProxSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, false);
            }
        }
    }

    private Future<?> submit(Runnable runnable) {
        return mExecutorService.submit(runnable);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long now = SystemClock.elapsedRealtime();

        if (event.sensor == mProxSensor) {
            boolean isNear = event.values[0] < mProxSensor.getMaximumRange();

            // First reading establishes baseline; ignore optical turn-off glitch
            if (!mHasProxReading || (now - mEnableTimestamp < SETTLING_DELAY_MS)) {
                mHasProxReading = true;
                mSawNear = isNear;
                return;
            }

            boolean wasNear = mSawNear;
            mSawNear = isNear;

            boolean isAtRest = mAtRest || (now - mLastMotionTimestamp >= STILL_REST_TIME_MS);

            // Approach gesture: hand arrival (FAR -> NEAR) while phone is at rest
            if (isAtRest && !wasNear && isNear) {
                triggerPulse();
            }
        } else if (event.sensor == mAccelSensor) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            if (!mHasLastAccel) {
                mLastX = x;
                mLastY = y;
                mLastZ = z;
                mHasLastAccel = true;
                return;
            }

            float deltaX = Math.abs(x - mLastX);
            float deltaY = Math.abs(y - mLastY);
            float deltaZ = Math.abs(z - mLastZ);

            mLastX = x;
            mLastY = y;
            mLastZ = z;

            float totalDelta = deltaX + deltaY + deltaZ;
            if (totalDelta > MOTION_THRESHOLD) {
                mLastMotionTimestamp = now;
                if (mAtRest) {
                    mAtRest = false;
                    if (DEBUG) Log.d(TAG, "Movement from rest detected: delta=" + totalDelta);
                    triggerPulse();
                }
            } else {
                if (!mAtRest && (now - mLastMotionTimestamp >= STILL_REST_TIME_MS)) {
                    mAtRest = true;
                    if (DEBUG) Log.d(TAG, "Device returned to sustained rest");
                    if (mEnabled && mTriggerSensor != null) {
                        mSensorManager.requestTriggerSensor(mTriggerListener, mTriggerSensor);
                    }
                }
            }
        }
    }

    private void triggerPulse() {
        long now = SystemClock.elapsedRealtime();
        if (now - mEntryTimestamp < MIN_PULSE_INTERVAL_MS) {
            return;
        }

        mEntryTimestamp = now;

        if (DEBUG) Log.d(TAG, "Launching ambient display pulse on approach/motion");
        mWakeLock.acquire(WAKELOCK_TIMEOUT_MS);
        DozeUtils.launchDozePulse(mContext);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        /* Empty */
    }

    protected void enable() {
        if (DEBUG) Log.d(TAG, "Enabling ApproachSensor");
        mEnabled = true;
        mHasProxReading = false;
        mHasLastAccel = false;
        mAtRest = true;
        mLastMotionTimestamp = 0;
        mEnableTimestamp = SystemClock.elapsedRealtime();

        submit(() -> {
            if (mSensorManager == null) return;

            if (mProxSensor != null) {
                mSensorManager.registerListener(this, mProxSensor,
                        SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (mAccelSensor != null) {
                mSensorManager.registerListener(this, mAccelSensor,
                        SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (mTriggerSensor != null) {
                mSensorManager.requestTriggerSensor(mTriggerListener, mTriggerSensor);
            }
        });
    }

    protected void disable() {
        if (DEBUG) Log.d(TAG, "Disabling ApproachSensor");
        mEnabled = false;
        submit(() -> {
            if (mSensorManager == null) return;

            mSensorManager.unregisterListener(this);
            if (mTriggerSensor != null) {
                mSensorManager.cancelTriggerSensor(mTriggerListener, mTriggerSensor);
            }
        });
    }
}
