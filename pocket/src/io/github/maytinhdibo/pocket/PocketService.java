/*
 * MIT License
 *
 * Copyright (c) 2021 Trần Mạnh Cường <maytinhdibo>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.maytinhdibo.pocket;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;

import io.github.maytinhdibo.pocket.receiver.PhoneStateReceiver;

public class PocketService extends Service {
    private static final String TAG = "PocketMode";
    private static final boolean DEBUG = true;

    private static final int EVENT_UNLOCK = 2;
    private static final int EVENT_TURN_ON_SCREEN = 1;
    private static final int EVENT_TURN_OFF_SCREEN = 0;

    private int lastAction = -1;
    private static long nextAlarm = -1;
    private boolean isSensorRunning = false;
    private boolean isScreenReceiverRegistered = false;
    private KeyguardManager keyguardManager;

    SensorManager sensorManager;
    Sensor proximitySensor;

    private long lastBlock = -1;
    private boolean isFirstChange = false;

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            Log.e(TAG, "Sensor service unavailable; stopping Pocket Mode");
            stopSelf();
            return;
        }

        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proximitySensor == null) {
            Log.w(TAG, "No proximity sensor; stopping Pocket Mode");
            stopSelf();
            return;
        }

        syncCallState();
        updateNextAlarm();

        // Grab the keyguard manager before registering the receiver so the
        // receiver never observes a null reference.
        keyguardManager = getSystemService(KeyguardManager.class);

        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenStateFilter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mScreenStateReceiver, screenStateFilter, Context.RECEIVER_NOT_EXPORTED);
        isScreenReceiverRegistered = true;

        // A sticky service restart may happen while the display is already on.
        PowerManager powerManager = getSystemService(PowerManager.class);
        if (powerManager != null && powerManager.isInteractive()
                && keyguardManager != null && keyguardManager.isKeyguardLocked()) {
            enableSensor();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        if (proximitySensor == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        boolean enabled = getSharedPreferences(
                PocketPreferenceFragment.BATTERY_POCKET_MODE, MODE_PRIVATE)
                .getBoolean("enable", false);
        if (!enabled) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        disableSensor();
        if (isScreenReceiverRegistered) {
            unregisterReceiver(mScreenStateReceiver);
            isScreenReceiverRegistered = false;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateNextAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        try {
            AlarmManager.AlarmClockInfo alarmClockInfo =
                    alarmManager != null ? alarmManager.getNextAlarmClock() : null;
            nextAlarm = alarmClockInfo != null ? alarmClockInfo.getTriggerTime() : -1;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to read next alarm", e);
            nextAlarm = -1;
        }
    }

    private void syncCallState() {
        TelephonyManager telephonyManager = getSystemService(TelephonyManager.class);
        if (telephonyManager == null) return;

        try {
            PhoneStateReceiver.CUR_STATE =
                    telephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE
                            ? PhoneStateReceiver.IDLE : PhoneStateReceiver.IN_CALL;
        } catch (SecurityException e) {
            Log.w(TAG, "Unable to read current call state", e);
        }
    }

    private void disableSensor() {
        if (!isSensorRunning) return;
        if (DEBUG) Log.d(TAG, "Disable proximity sensor");
        if (sensorManager != null && proximitySensor != null) {
            sensorManager.unregisterListener(proximitySensorEventListener, proximitySensor);
        }
        //mark first sensor update after disable
        isFirstChange = true;
        isSensorRunning = false;
    }

    private void enableSensor() {
        if (isSensorRunning || sensorManager == null || proximitySensor == null) return;
        if (DEBUG) Log.d(TAG, "Enable proximity sensor");
        isSensorRunning = sensorManager.registerListener(proximitySensorEventListener,
                proximitySensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        if (!isSensorRunning) {
            Log.w(TAG, "Failed to register proximity sensor listener");
        }
    }

    private BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent != null ? intent.getAction() : null;
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (lastAction != EVENT_UNLOCK
                        && (keyguardManager == null || keyguardManager.isKeyguardLocked())) {
                    enableSensor();
                }
                lastAction = EVENT_TURN_ON_SCREEN;
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                disableSensor();

                // Refresh the next alarm time before the next screen-on event.
                updateNextAlarm();
                lastAction = EVENT_TURN_OFF_SCREEN;
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                //disable when unlocked
                disableSensor();
                lastAction = EVENT_UNLOCK;
            }
        }
    };

    SensorEventListener proximitySensorEventListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            //method to check accuracy changed in sensor.
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            //check if the sensor type is proximity sensor.
            if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                // Only a fully covered sensor means "near". Using
                // "< getMaximumRange()" also matches a hand passing close to the
                // sensor and turns the screen off spuriously while handling the
                // device.
                if (event.values != null && event.values.length > 0
                        && event.values[0] == 0) {
                    if (keyguardManager != null && !keyguardManager.isKeyguardLocked()) {
                        // USER_PRESENT can be missed after a sticky service
                        // restart; never turn the screen off while unlocked.
                        if (DEBUG) Log.d(TAG, "NEAR while unlocked, disabling sensor");
                        disableSensor();
                        lastAction = EVENT_UNLOCK;
                    } else {
                        long timestamp = System.currentTimeMillis();
                        if (PhoneStateReceiver.CUR_STATE == PhoneStateReceiver.IDLE
                                && (nextAlarm == -1 || Math.abs(timestamp - nextAlarm) > 60000)) {
                            //stop block turn on after 15 seconds
                            if (!(isFirstChange && (System.currentTimeMillis() - lastBlock < 15000 && lastBlock != -1))) {
                                if (DEBUG) Log.d(TAG, "NEAR, disable sensor and turn screen off");
                                disableSensor();
                                PowerManager pm = getSystemService(PowerManager.class);
                                if (pm != null) {
                                    pm.goToSleep(SystemClock.uptimeMillis());
                                    lastBlock = System.currentTimeMillis();
                                }
                            }
                        }
                    }
                } else {
                    if (DEBUG) Log.d(TAG, "FAR");
                }
            }
            isFirstChange = false;
        }
    };
}
