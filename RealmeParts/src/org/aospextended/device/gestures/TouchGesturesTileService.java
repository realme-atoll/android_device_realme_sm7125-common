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

package org.aospextended.device.gestures;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.aospextended.device.util.Utils;

public class TouchGesturesTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!TouchGestures.isSupported(TouchGestures.GESTURE_PATH)) {
            return;
        }
        boolean enabled = !isGesturesEnabled();
        Utils.getSharedPreferences(this).edit()
                .putBoolean(TouchGestures.PREF_GESTURE_ENABLE, enabled)
                .apply();
        TouchGestures.enableGestures(enabled);
        updateTileState();
    }

    private boolean isGesturesEnabled() {
        return Utils.getSharedPreferences(this)
                .getBoolean(TouchGestures.PREF_GESTURE_ENABLE, true);
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        if (!TouchGestures.isSupported(TouchGestures.GESTURE_PATH)) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else {
            tile.setState(isGesturesEnabled() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
        tile.updateTile();
    }
}
