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
    private static final String MOTION_DETECT_SENSOR = "android.sensor.motion_detect";
    private static final int MIN_PULSE_INTERVAL_MS = 2500;
    private static final int WAKELOCK_TIMEOUT_MS = 3000;

    private SensorManager mSensorManager;
    private Sensor mMotionSensor;
    private Sensor mProxSensor;
    private Context mContext;
    private ExecutorService mExecutorService;
    private PowerManager mPowerManager;
    private WakeLock mWakeLock;
    private long mEntryTimestamp;
    private boolean mSawNear = false;

    public ApproachSensor(Context context) {
        mContext = context;
        mSensorManager = mContext.getSystemService(SensorManager.class);

        // Motion detection: AnyMotion Detect / Motion Detect / Significant Motion
        // Detects entering the room, footsteps, table/bed vibrations
        mMotionSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MOTION_DETECT, true);
        if (mMotionSensor == null) {
            mMotionSensor = DozeUtils.getSensor(mSensorManager, MOTION_DETECT_SENSOR);
        }
        if (mMotionSensor == null) {
            mMotionSensor = DozeUtils.getSensor(mSensorManager, AMD_SENSOR);
        }
        if (mMotionSensor == null) {
            mMotionSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION, true);
        }

        // Proximity sensor: detects hand reaching/hovering over the screen
        mProxSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true);
        if (mProxSensor == null) {
            mProxSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, false);
        }

        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mExecutorService = Executors.newSingleThreadExecutor();
    }

    private Future<?> submit(Runnable runnable) {
        return mExecutorService.submit(runnable);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == mProxSensor) {
            boolean isNear = event.values[0] < mProxSensor.getMaximumRange();
            if (DEBUG) Log.d(TAG, "Got proximity approach event: " + event.values[0] + " isNear: " + isNear);

            if (!mSawNear && isNear) {
                triggerPulse();
            }
            mSawNear = isNear;
        } else if (event.sensor == mMotionSensor) {
            if (DEBUG) Log.d(TAG, "Got motion approach event: " + event.values[0]);
            triggerPulse();
        }
    }

    private void triggerPulse() {
        long delta = SystemClock.elapsedRealtime() - mEntryTimestamp;
        if (delta >= MIN_PULSE_INTERVAL_MS) {
            mEntryTimestamp = SystemClock.elapsedRealtime();
            mWakeLock.acquire(WAKELOCK_TIMEOUT_MS);
            DozeUtils.launchDozePulse(mContext);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        /* Empty */
    }

    protected void enable() {
        if (mMotionSensor == null && mProxSensor == null) return;
        if (DEBUG) Log.d(TAG, "Enabling ApproachSensor");
        mSawNear = false;
        submit(() -> {
            if (mMotionSensor != null) {
                mSensorManager.registerListener(this, mMotionSensor,
                        SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (mProxSensor != null) {
                mSensorManager.registerListener(this, mProxSensor,
                        SensorManager.SENSOR_DELAY_NORMAL);
            }
            mEntryTimestamp = SystemClock.elapsedRealtime();
        });
    }

    protected void disable() {
        if (DEBUG) Log.d(TAG, "Disabling ApproachSensor");
        submit(() -> {
            mSensorManager.unregisterListener(this);
        });
    }
}

