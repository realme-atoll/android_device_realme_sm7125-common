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

package org.aospextended.device.otg;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.aospextended.device.R;

/**
 * Quick Settings Tile for USB OTG that cycles through:
 * Auto (10 min idle) -> Always On -> Off -> Auto
 */
public class OtgTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!OtgSettings.isSupported()) {
            return;
        }
        String current = OtgSettings.getMode(this);
        String next;
        if (OtgSettings.MODE_OFF.equals(current)) {
            next = OtgSettings.MODE_AUTO;
        } else if (OtgSettings.MODE_AUTO.equals(current)) {
            next = OtgSettings.MODE_ON;
        } else {
            next = OtgSettings.MODE_OFF;
        }
        OtgSettings.setMode(this, next);
        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        if (!OtgSettings.isSupported()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setLabel(getString(R.string.otg_mode_title));
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_otg));
            tile.setSubtitle(null);
            tile.updateTile();
            return;
        }

        String mode = OtgSettings.getMode(this);
        tile.setLabel(getString(R.string.otg_mode_title));
        if (OtgSettings.MODE_AUTO.equals(mode)) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_otg_auto));
            tile.setSubtitle(getString(R.string.otg_mode_tile_auto));
            tile.setContentDescription(getString(R.string.otg_mode_title) + ", " + getString(R.string.otg_mode_tile_auto));
        } else if (OtgSettings.MODE_ON.equals(mode)) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_otg));
            tile.setSubtitle(getString(R.string.otg_mode_tile_on));
            tile.setContentDescription(getString(R.string.otg_mode_title) + ", " + getString(R.string.otg_mode_tile_on));
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_otg));
            tile.setSubtitle(getString(R.string.otg_mode_tile_off));
            tile.setContentDescription(getString(R.string.otg_mode_title) + ", " + getString(R.string.otg_mode_tile_off));
        }
        tile.updateTile();
    }
}
