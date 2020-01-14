/*
 *     Copyright (C) 2019 Parrot Drones SAS
 *
 *     Redistribution and use in source and binary forms, with or without
 *     modification, are permitted provided that the following conditions
 *     are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of the Parrot Company nor the names
 *       of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written
 *       permission.
 *
 *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *     "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *     LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *     FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *     PARROT COMPANY BE LIABLE FOR ANY DIRECT, INDIRECT,
 *     INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *     BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 *     OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 *     AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *     OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 *     OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 *     SUCH DAMAGE.
 *
 */

package com.parrot.drone.groundsdk.arsdkengine.instrument.miniature;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.instrument.DroneInstrumentController;
import com.parrot.drone.groundsdk.internal.device.instrument.RadioCore;
import com.parrot.drone.groundsdk.value.DoubleRange;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureCommon;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import androidx.annotation.NonNull;

/** Radio instrument controller for Miniature (Mambo) family drones. */
public class MiniatureRadio extends DroneInstrumentController {

    private final Context ctx;
    /** The radio instrument from which this object is the backend. */
    @NonNull
    private final RadioCore mRadio;

    private MamboSignalStrengthThread mamboSignalStrengthThread = null;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public MiniatureRadio(@NonNull Context ctx, @NonNull DroneController droneController) {
        super(droneController);
        this.ctx = ctx;
        mRadio = new RadioCore(mComponentStore);
    }

    @Override
    protected void onConnected() {
        mRadio.publish();

        mamboSignalStrengthThread = new MamboSignalStrengthThread();
        mamboSignalStrengthThread.start();
    }

    @Override
    protected void onDisconnected() {
        if (mamboSignalStrengthThread != null && mamboSignalStrengthThread.isAlive()) {
            mamboSignalStrengthThread.interrupt();
            try {
                mamboSignalStrengthThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mamboSignalStrengthThread = null;
        }

        mRadio.unpublish();
    }

    @Override
    protected void onCommandReceived(@NonNull ArsdkCommand command) {
        int featureId = command.getFeatureId();
        if (featureId == ArsdkFeatureCommon.CommonState.UID) {
            ArsdkFeatureCommon.CommonState.decode(command, mCommonStateCallback);
        }
    }

    /**
     * Callbacks called when a command of the feature ArsdkFeatureCommon.CommonState is decoded.
     */
    private final ArsdkFeatureCommon.CommonState.Callback mCommonStateCallback = new ArsdkFeatureCommon.CommonState.Callback() {
        @Override
        public void onWifiSignalChanged(int rssi) {
            throw new RuntimeException("onWifiSignalChanged not implemented");
        }

        @Override
        public void onLinkSignalQuality(int linkSignalQuality) {
            throw new RuntimeException("onLinkSignalQuality not implemented");
        }
    };

    private class MamboSignalStrengthThread extends Thread {
        int lastRssi = 0;

        final Handler mainThreadHandler;

        final DoubleRange sourceRange = DoubleRange.of(0, 100);
        final DoubleRange targetRange = DoubleRange.of(0, 4);

        MamboSignalStrengthThread() {
            mainThreadHandler = new Handler(Looper.getMainLooper());
        }

        @Override
        public void run() {
            final WifiManager wifiManager = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

            while (!isInterrupted()) {
                try {
                    Thread.sleep(333);
                } catch (InterruptedException e) {
                    break;
                }

                final WifiInfo wifiInfo = wifiManager != null ? wifiManager.getConnectionInfo() : null;
                final int rssi = wifiInfo != null ? wifiInfo.getRssi() : 0;

                if (rssi != lastRssi) {
                    lastRssi = rssi;

                    int quality = 2 * (rssi + 100);

                    if (quality < 0) quality = 0;
                    if (quality > 100) quality = 100;

                    final int q = Math.round((float) targetRange.scaleFrom(quality, sourceRange));

                    mainThreadHandler.post(() -> {
                        mRadio.updateRssi(rssi)
                                .updateLinkSignalQuality(q)
                                .notifyUpdated();
                    });
                }
            }
        }
    }
}
