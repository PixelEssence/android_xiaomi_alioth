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

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

public class PocketPreferenceFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "PocketMode";

    public static final String BATTERY_POCKET_MODE = "b_pocketmode_sw_pref";
    private SwitchPreferenceCompat modeSwitch;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pocket_setting);
        modeSwitch = findPreference(BATTERY_POCKET_MODE);
        if (modeSwitch == null) {
            Log.e(TAG, "Pocket Mode preference is missing");
            return;
        }

        // Keep the legacy preference file shared with BootCompletedReceiver.
        modeSwitch.setPersistent(false);
        modeSwitch.setChecked(requireContext()
                .getSharedPreferences(BATTERY_POCKET_MODE, Activity.MODE_PRIVATE)
                .getBoolean("enable", false));
        modeSwitch.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!BATTERY_POCKET_MODE.equals(preference.getKey()) || !(newValue instanceof Boolean)) {
            return false;
        }

        final boolean enabled = (Boolean) newValue;
        requireContext().getSharedPreferences(BATTERY_POCKET_MODE, Activity.MODE_PRIVATE)
                .edit()
                .putBoolean("enable", enabled)
                .apply();

        if (enabled) {
            PocketUtils.startService(requireContext());
        } else {
            PocketUtils.stopService(requireContext());
        }
        return true;
    }
}
