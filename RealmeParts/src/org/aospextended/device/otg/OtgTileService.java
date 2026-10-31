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
        boolean next = !OtgSettings.isEnabled(this);
        OtgSettings.setEnabled(this, next);
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

        boolean enabled = OtgSettings.isEnabled(this);
        tile.setLabel(getString(R.string.otg_mode_title));
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_otg));
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(enabled ? getString(R.string.otg_mode_tile_on) : getString(R.string.otg_mode_tile_off));
        tile.setContentDescription(getString(R.string.otg_mode_title) + ", "
                + (enabled ? getString(R.string.otg_mode_tile_on) : getString(R.string.otg_mode_tile_off)));
        tile.updateTile();
    }
}
