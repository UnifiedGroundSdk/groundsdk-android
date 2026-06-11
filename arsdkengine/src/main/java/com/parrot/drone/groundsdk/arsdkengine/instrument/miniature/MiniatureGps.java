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

    /**
     * Horizontal accuracy (metres) reported for dead-reckoned positions.
     * <p>
     * The Mambo estimates its position by integrating optical-flow and barometer readings from
     * the takeoff point. Typical drift without a real GPS receiver is several metres; 3 m is a
     * conservative lower-bound that prevents callers from treating these positions as GPS-quality
     * fixes.
     */
    private static final int DEAD_RECKONED_HORIZONTAL_ACCURACY_M = 3;

    /**
     * Vertical accuracy (metres) reported for dead-reckoned positions.
     * <p>
     * Barometer-based altitude is typically accurate to within 1–2 m in calm conditions;
     * 2 m is a reasonable conservative bound.
     */
    private static final int DEAD_RECKONED_VERTICAL_ACCURACY_M = 2;

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
        deviceDict.clear().commit();
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

        @Override
        public void onDronePosition(float x, float y, int z, int psi, int ts) {
            // ── Step 1: gate on reference ────────────────────────────────────────
            // We need a phone GPS fix to anchor the dead-reckoned offset.
            // Once the first drone-position arrives we stop listening for updates
            // (the takeoff fix is the one that matters).
            if (systemLocation != null && referenceLocation != null) {
                systemLocation.disposeMonitor(miniatureGps);
                systemLocation = null;
            } else if (referenceLocation == null) {
                return;
            }

            // ── Step 2: altitude ─────────────────────────────────────────────────
            // Protocol: z is in cm, up-positive; reference altitude is in metres.
            final double altitude = referenceLocation.getAltitude() + (z / 100.0);

            // ── Step 3: horizontal offset in body frame ──────────────────────────
            // Protocol (minidrone.xml, NavigationDataState.DronePosition):
            //   x  – forward (rear → front of drone), cm
            //   y  – left    (right → left of drone), cm
            //   psi – current yaw relative to takeoff orientation, degrees [-180, 180]
            //
            // We treat the takeoff heading as geographic north (H = 0°).  This is
            // an approximation: without a compass fix at takeoff the absolute north
            // alignment is unknown, but relative motion tracking is still correct.
            //
            // Body → geographic (NED) rotation for H = 0°:
            //   north_m =  x_m   (drone forward aligns with north at H=0)
            //   east_m  = -y_m   (drone left is geographic west, so east = −y)
            //
            // If a takeoff heading H (degrees CW from north) were available:
            //   north_m =  x_m * cos(H) + y_m * sin(H)
            //   east_m  =  x_m * sin(H) - y_m * cos(H)
            final double x_m = x / 100.0;   // cm → metres
            final double y_m = y / 100.0;

            // North/east displacement (metres) of drone from takeoff point.
            final double north_m =  x_m;    // forward = north at H=0
            final double east_m  = -y_m;    // left    = −east

            // ── Step 4: distance and bearing ────────────────────────────────────
            // Great-circle bearing FROM takeoff TO drone, clockwise from north.
            //   atan2(east, north) gives the standard azimuth.
            final double distance_m = Math.hypot(north_m, east_m);
            final double bearingDeg = Math.toDegrees(Math.atan2(east_m, north_m));

            // ── Step 5: project onto sphere ──────────────────────────────────────
            final double[] result = computeOffsetPoint(
                    referenceLocation.getLatitude(),
                    referenceLocation.getLongitude(),
                    distance_m, bearingDeg);

            if (result == null) {
                return;
            }

            gpsCore.updateLocation(result[0], result[1])
                    .updateAltitude(altitude)
                    .updateHorizontalAccuracy(DEAD_RECKONED_HORIZONTAL_ACCURACY_M)
                    .updateVerticalAccuracy(DEAD_RECKONED_VERTICAL_ACCURACY_M)
                    .notifyUpdated();
            saveLocation(result[0], result[1]);
            saveAltitude(altitude);
            HORIZONTAL_ACCURACY_SETTING.save(deviceDict, DEAD_RECKONED_HORIZONTAL_ACCURACY_M);
            VERTICAL_ACCURACY_SETTING.save(deviceDict, DEAD_RECKONED_VERTICAL_ACCURACY_M);
        }

        /**
         * Computes the geographic point that is {@code distanceM} metres away from
         * {@code (latDeg, lonDeg)} along the initial bearing {@code bearingDeg}
         * (clockwise from north), using the spherical-Earth approximation.
         *
         * @param latDeg     origin latitude in degrees
         * @param lonDeg     origin longitude in degrees
         * @param distanceM  distance in metres (must be non-negative)
         * @param bearingDeg initial bearing in degrees, clockwise from north
         * @return {@code double[]{latitude, longitude}} in degrees, or {@code null} if
         *         the computation is geometrically degenerate
         */
        private double[] computeOffsetPoint(double latDeg, double lonDeg,
                double distanceM, double bearingDeg) {
            // Convert to radians; normalise angular distance to the unit sphere.
            final double bearingRad = Math.toRadians(bearingDeg);
            final double latRad = Math.toRadians(latDeg);
            final double lonRad = Math.toRadians(lonDeg);
            final double angDist = distanceM / 6_371_009.0; // Earth mean radius, metres

            // Standard spherical forward geodesic:
            //   sin(φ₂) = sin(φ₁)·cos(d) + cos(φ₁)·sin(d)·cos(θ)
            //   λ₂ = λ₁ + atan2(sin(θ)·sin(d)·cos(φ₁), cos(d)−sin(φ₁)·sin(φ₂))
            final double sinLat1 = Math.sin(latRad);
            final double cosLat1 = Math.cos(latRad);
            final double sinDist = Math.sin(angDist);
            final double cosDist = Math.cos(angDist);

            final double sinLat2 = sinLat1 * cosDist + cosLat1 * sinDist * Math.cos(bearingRad);
            // Clamp for floating-point safety before asin.
            final double sinLat2c = Math.max(-1.0, Math.min(1.0, sinLat2));
            final double lat2Rad = Math.asin(sinLat2c);

            final double lon2Rad = lonRad + Math.atan2(
                    Math.sin(bearingRad) * sinDist * cosLat1,
                    cosDist - sinLat1 * sinLat2c);

            final double lat2 = Math.toDegrees(lat2Rad);
            final double lon2 = Math.toDegrees(lon2Rad);

            // Basic sanity: latitude must be within [-90, 90].
            if (lat2 < -90.0 || lat2 > 90.0) {
                return null;
            }
            return new double[]{lat2, lon2};
        }
    };
}
