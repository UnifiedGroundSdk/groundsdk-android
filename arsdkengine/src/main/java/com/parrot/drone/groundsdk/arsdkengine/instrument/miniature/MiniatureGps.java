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

import android.location.Location;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.instrument.DroneInstrumentController;
import com.parrot.drone.groundsdk.arsdkengine.persistence.PersistentStore;
import com.parrot.drone.groundsdk.arsdkengine.persistence.StorageEntry;
import com.parrot.drone.groundsdk.internal.device.instrument.GpsCore;
import com.parrot.drone.groundsdk.internal.utility.SystemLocation;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureMinidrone;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import androidx.annotation.NonNull;

/** GPS instrument controller for Anafi family drones. */
public class MiniatureGps extends DroneInstrumentController implements SystemLocation.Monitor {

    private final MiniatureGps miniatureGps;
    private final DroneController droneController;

    /** Key used to access device specific dictionary for this component's settings. */
    private static final String SETTINGS_KEY = "gps";

    /** Location latitude setting. */
    private static final StorageEntry<Double> LATITUDE_SETTING = StorageEntry.ofDouble("latitude");

    /** Location longitude setting. */
    private static final StorageEntry<Double> LONGITUDE_SETTING = StorageEntry.ofDouble("longitude");

    /** Location altitude setting. */
    private static final StorageEntry<Double> ALTITUDE_SETTING = StorageEntry.ofDouble("altitude");

    /** Location horizontal accuracy setting. */
    private static final StorageEntry<Integer> HORIZONTAL_ACCURACY_SETTING =
            StorageEntry.ofInteger("horizontalAccuracy");

    /** Location vertical accuracy setting. */
    private static final StorageEntry<Integer> VERTICAL_ACCURACY_SETTING = StorageEntry.ofInteger("verticalAccuracy");

    /** Location timestamp setting. */
    private static final StorageEntry<Long> TIMESTAMP_SETTING = StorageEntry.ofLong("timestamp");


    /** The GPS from which this object is the backend. */
    @NonNull
    private final GpsCore gpsCore;

    /** Dictionary containing device specific values for this component. */
    @NonNull
    private final PersistentStore.Dictionary deviceDict;

    private SystemLocation systemLocation = null;
    
    private Location referenceLocation;

    /**
     * Constructor.
     *
     * @param droneController The drone controller that owns this component controller.
     */
    public MiniatureGps(@NonNull DroneController droneController) {
        super(droneController);
        
        miniatureGps = this;
        this.droneController = droneController;
        deviceDict = mDeviceController.getDeviceDict().getDictionary(SETTINGS_KEY);
        gpsCore = new GpsCore(mComponentStore);

        if (!deviceDict.isNew()) {
            loadLastKnownLocation();
            gpsCore.publish();
        }
    }

    @Override
    public void onConnected() {
        systemLocation = droneController.getEngine().getUtilityOrThrow(SystemLocation.class);
        systemLocation.monitorWith(this);

        gpsCore.publish();
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        referenceLocation = location;
    }

    @Override
    public void onDisconnected() {
        if (systemLocation != null) {
            systemLocation.disposeMonitor(this);
        }

        if (deviceDict.isNew()) {
            gpsCore.unpublish();
        }
        gpsCore.reset().notifyUpdated();
    }

    @Override
    protected void onForgetting() {
        deviceDict.clear();
        gpsCore.unpublish();
    }

    @Override
    public void onCommandReceived(@NonNull ArsdkCommand command) {
        if (command.getFeatureId() == ArsdkFeatureMinidrone.NavigationDataState.UID) {
            ArsdkFeatureMinidrone.NavigationDataState.decode(command, mNavigationDataStateCallback);
        }
    }

    /**
     * Loads the last known location, from persistent storage.
     * <p>
     * This updates the gps instrument accordingly.
     * <p>
     * <strong>NOTE:</strong> Caller is still responsible to call {@code ComponentCore.notifyUpdated()} to publish
     * the change up to the user.
     */
    private void loadLastKnownLocation() {
        Double latitude = LATITUDE_SETTING.load(deviceDict);
        Double longitude = LONGITUDE_SETTING.load(deviceDict);
        if (latitude != null && longitude != null) {
            gpsCore.updateLocation(latitude, longitude);
        }
        Double altitude = ALTITUDE_SETTING.load(deviceDict);
        if (altitude != null) {
            gpsCore.updateAltitude(altitude);
        }
        Integer horizontalAccuracy = HORIZONTAL_ACCURACY_SETTING.load(deviceDict);
        if (horizontalAccuracy != null) {
            gpsCore.updateHorizontalAccuracy(horizontalAccuracy);
        }
        Integer verticalAccuracy = VERTICAL_ACCURACY_SETTING.load(deviceDict);
        if (verticalAccuracy != null) {
            gpsCore.updateVerticalAccuracy(verticalAccuracy);
        }
        Long locationTime = TIMESTAMP_SETTING.load(deviceDict);
        if (locationTime != null) {
            gpsCore.updateLocationTime(locationTime);
        }
    }

    /**
     * Saves the location received from the drone to persistent storage, so that it can be available offline.
     *
     * @param latitude  the received latitude
     * @param longitude the received longitude
     */
    private void saveLocation(double latitude, double longitude) {
        LATITUDE_SETTING.save(deviceDict, latitude);
        LONGITUDE_SETTING.save(deviceDict, longitude);
        TIMESTAMP_SETTING.save(deviceDict, System.currentTimeMillis());
    }

    /**
     * Saves the altitude received from the drone to persistent storage, so that it can be available offline.
     *
     * @param altitude the received altitude
     */
    private void saveAltitude(double altitude) {
        ALTITUDE_SETTING.save(deviceDict, altitude);
    }

    /** Callbacks called when a command of the feature ArsdkFeatureArdrone3.PilotingState is decoded. */
    private final ArsdkFeatureMinidrone.NavigationDataState.Callback mNavigationDataStateCallback =
            new ArsdkFeatureMinidrone.NavigationDataState.Callback() {

        /** Value sent by drone when latitude/longitude or altitude are not available. */
        private static final double VALUE_UNAVAILABLE = 500;

        @Override
        public void onDronePosition(float x, float y, int z, int psi, int ts) {
            if (systemLocation != null && referenceLocation != null) {
                systemLocation.disposeMonitor(miniatureGps);
                systemLocation = null;
            } else if (referenceLocation == null) return;

            final double altitude = referenceLocation.getAltitude() + (z / 100d);
            final double distance = Math.hypot(x, y) / 100d;
            final double heading = calculateAngle(x, y, 0, 0);

            final double[] results = computeOffsetOrigin(referenceLocation.getLatitude(), referenceLocation.getLongitude(), distance, heading);
            assert results != null;

            if (Double.compare(results[0], VALUE_UNAVAILABLE) != 0
                && Double.compare(results[1], VALUE_UNAVAILABLE) != 0) {
                int horizontalAccuracy = 1;
                gpsCore.updateLocation(results[0], results[1])
                        .updateAltitude(altitude)
                        .updateHorizontalAccuracy(horizontalAccuracy)
                        .updateVerticalAccuracy(1)
                        .notifyUpdated();
                saveLocation(results[0], results[1]);
                saveAltitude(altitude);
                HORIZONTAL_ACCURACY_SETTING.save(deviceDict, 1);
                VERTICAL_ACCURACY_SETTING.save(deviceDict, 1);
            }
        }

        private double calculateAngle(double x1, double y1, double x2, double y2) {
            double angle = Math.toDegrees(Math.atan2(x2 - x1, y2 - y1));
            angle = angle + Math.ceil( -angle / 360 ) * 360;

            return angle;
        }

        private double[] computeOffsetOrigin(double latitude, double longitude, double distance, double heading) {
            heading = Math.toRadians(heading);
            distance /= 6371009.0D;
            double n1 = Math.cos(distance);
            double n2 = Math.sin(distance) * Math.cos(heading);
            double n3 = Math.sin(distance) * Math.sin(heading);
            double n4 = Math.sin(Math.toRadians(latitude));
            double n12 = n1 * n1;
            double discriminant = n2 * n2 * n12 + n12 * n12 - n12 * n4 * n4;
            if (discriminant < 0.0D) {
                return null;
            } else {
                double b = n2 * n4 + Math.sqrt(discriminant);
                b /= n1 * n1 + n2 * n2;
                double a = (n4 - n2 * b) / n1;
                double fromLatRadians = Math.atan2(a, b);
                if (fromLatRadians < -1.5707963267948966D || fromLatRadians > 1.5707963267948966D) {
                    b = n2 * n4 - Math.sqrt(discriminant);
                    b /= n1 * n1 + n2 * n2;
                    fromLatRadians = Math.atan2(a, b);
                }

                if (fromLatRadians >= -1.5707963267948966D && fromLatRadians <= 1.5707963267948966D) {
                    double fromLngRadians = Math.toRadians(longitude) - Math.atan2(n3, n1 * Math.cos(fromLatRadians) - n2 * Math.sin(fromLatRadians));
                    return new double[] { Math.toDegrees(fromLatRadians), Math.toDegrees(fromLngRadians) };
                } else {
                    return null;
                }
            }
        }
    };
}
