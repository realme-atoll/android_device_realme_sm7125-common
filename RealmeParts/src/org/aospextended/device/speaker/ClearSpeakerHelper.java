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

package org.aospextended.device.speaker;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

public class ClearSpeakerHelper {

    private static final String TAG = "ClearSpeakerHelper";
    private static final int DURATION_SECONDS = 30;
    private static final int SAMPLE_RATE = 44100;

    public interface Listener {
        void onProgress(int secondsRemaining);
        void onFinished();
    }

    private static ClearSpeakerHelper sInstance;
    private AudioTrack mAudioTrack;
    private Thread mPlayThread;
    private boolean mIsRunning = false;
    private int mOriginalVolume = -1;
    private Handler mMainHandler = new Handler(Looper.getMainLooper());
    private AudioFocusRequest mAudioFocusRequest;
    private PowerManager.WakeLock mWakeLock;

    public static synchronized ClearSpeakerHelper getInstance() {
        if (sInstance == null) {
            sInstance = new ClearSpeakerHelper();
        }
        return sInstance;
    }

    public static boolean isSupported(Context context) {
        if (context == null) return false;
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return am != null && am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) > 0;
    }

    public synchronized boolean isRunning() {
        return mIsRunning;
    }

    public synchronized void start(Context context, Listener listener) {
        if (mIsRunning) {
            return;
        }
        mIsRunning = true;

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        if (powerManager != null) {
            mWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "RealmeParts:ClearSpeakerWakeLock");
            if (mWakeLock != null) {
                mWakeLock.acquire((DURATION_SECONDS + 5) * 1000L);
            }
        }

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        if (audioManager != null) {
            mOriginalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0);

            mAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(audioAttributes)
                    .build();
            audioManager.requestAudioFocus(mAudioFocusRequest);
        }

        mPlayThread = new Thread(() -> {
            try {
                int minBufferSize = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);

                int bufferSize = Math.max(minBufferSize, SAMPLE_RATE);
                short[] audioBuffer = new short[bufferSize];

                mAudioTrack = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(bufferSize * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();

                mAudioTrack.play();

                long startTime = System.currentTimeMillis();
                long totalTimeMs = DURATION_SECONDS * 1000L;
                double phase = 0.0;

                while (mIsRunning) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed >= totalTimeMs) {
                        break;
                    }

                    int secondsRemaining = (int) Math.ceil((totalTimeMs - elapsed) / 1000.0);
                    if (listener != null) {
                        mMainHandler.post(() -> {
                            if (mIsRunning && listener != null) {
                                listener.onProgress(secondsRemaining);
                            }
                        });
                    }

                    // Generate a sweeping frequency waveform (500 Hz to 2200 Hz sweep)
                    double cycleProgress = ((elapsed % 2000L) / 2000.0); // 2-second cycle
                    double currentFreq = 500.0 + 1700.0 * (cycleProgress < 0.5 ? (cycleProgress * 2.0) : ((1.0 - cycleProgress) * 2.0));

                    double phaseIncrement = 2.0 * Math.PI * currentFreq / SAMPLE_RATE;
                    for (int i = 0; i < audioBuffer.length; i++) {
                        audioBuffer[i] = (short) (Math.sin(phase) * Short.MAX_VALUE * 0.95);
                        phase += phaseIncrement;
                        if (phase > 2.0 * Math.PI) {
                            phase -= 2.0 * Math.PI;
                        }
                    }

                    mAudioTrack.write(audioBuffer, 0, audioBuffer.length);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error playing clear speaker tone", e);
            } finally {
                stopInternal(audioManager, listener);
            }
        });

        mPlayThread.start();
    }

    public synchronized void stop() {
        mIsRunning = false;
        if (mPlayThread != null) {
            mPlayThread.interrupt();
            mPlayThread = null;
        }
    }

    private void stopInternal(AudioManager audioManager, Listener listener) {
        mIsRunning = false;
        try {
            if (mAudioTrack != null) {
                if (mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    mAudioTrack.stop();
                }
                mAudioTrack.release();
                mAudioTrack = null;
            }
        } catch (Exception ignored) {}

        if (audioManager != null) {
            if (mAudioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(mAudioFocusRequest);
                mAudioFocusRequest = null;
            }
            if (mOriginalVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mOriginalVolume, 0);
                mOriginalVolume = -1;
            }
        }

        if (mWakeLock != null && mWakeLock.isHeld()) {
            try {
                mWakeLock.release();
            } catch (Exception ignored) {}
            mWakeLock = null;
        }

        if (listener != null) {
            mMainHandler.post(listener::onFinished);
        }
    }
}
