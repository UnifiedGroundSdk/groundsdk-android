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

package com.parrot.drone.groundsdk.arsdkengine.ephemeris;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.http.HttpEphemerisClient;
import com.parrot.drone.groundsdk.internal.Cancelable;
import com.parrot.drone.groundsdk.internal.ftp.FtpSession;
import com.parrot.drone.groundsdk.internal.http.HttpRequest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Protocol specific ephemeris uploader interface.
 */
public interface EphemerisUploadProtocol {

    /** Callback used upon request completion. */
    interface Callback {

        /**
         * Called when ephemeris file upload request completes.
         *
         * @param success {@code true} if upload was successful, otherwise {@code false}
         */
        void onRequestComplete(boolean success);
    }

    /**
     * Starts a request to upload an ephemeris file to the device.
     * <p>
     * Implementations of this method shall call {@link EphemerisUploadProtocol.Callback#onRequestComplete} when request
     * completes.
     *
     * @param droneController the device controller that owns the flight plan updater peripheral controller
     * @param ephemeris       file to upload
     * @param callback        the callback used upon request completion
     *
     * @return a cancellable request
     */
    @Nullable
    Cancelable upload(@NonNull DroneController droneController, @NonNull File ephemeris, @NonNull Callback callback);

    /**
     * Starts a request to upload an ephemeris file to the device using the HTTP protocol.
     * <p>
     * Implementations of this method shall call {@link EphemerisUploadProtocol.Callback#onRequestComplete} when request
     * completes.
     *
     * @param droneController the device controller that owns the flight plan updater peripheral controller
     * @param ephemeris       file to upload
     * @param callback        the callback used upon request completion
     *
     * @return a cancellable request
     */
    @Nullable
    static Cancelable httpUpload(@NonNull DroneController droneController, @NonNull File ephemeris,
                                 @NonNull Callback callback) {
        HttpEphemerisClient client = droneController.getHttpClient(HttpEphemerisClient.class);
        if (client == null) {
            // Invalid state, drone not connected
            return null;
        }
        return client.uploadEphemeris(ephemeris, (status, code) ->
                callback.onRequestComplete(status == HttpRequest.Status.SUCCESS));
    }

    /**
     * Starts a request to upload an ephemeris file to the device using the FTP protocol.
     * <p>
     * Implementations of this method shall call {@link EphemerisUploadProtocol.Callback#onRequestComplete} when request
     * completes.
     *
     * @param droneController the device controller that owns the flight plan updater peripheral controller
     * @param ephemeris       file to upload
     * @param callback        the callback used upon request completion
     *
     * @return a cancellable request
     */
    @Nullable
    static Cancelable ftpUpload(@NonNull DroneController droneController, @NonNull File ephemeris,
                                 @NonNull Callback callback) {

        final FtpSession session = droneController.getMediaStoreFtpSession();
        if (session == null) return null;

        try {
            final String checksum = getMD5Checksum(ephemeris.getAbsolutePath());
            session.storeFile(new ByteArrayInputStream(checksum.getBytes()), "/internal_000/gps_data/ephemeris.bin.md5", null);
        } catch (Exception e) {
            return null;
        }

        final FtpSession.FtpTransferListener listener = new FtpSession.FtpTransferListener() {
            @Override
            public void onTransferCompleted(boolean successful, @Nullable Object data) {
                callback.onRequestComplete(successful);
            }

            @Override
            public void onTransferProgress(int percent) {

            }
        };
        return session.cancelableStoreFile(ephemeris, "/internal_000/gps_data/ephemeris.bin", listener);
    }

    static byte[] createChecksum(String filename) throws Exception {
        InputStream fis =  new FileInputStream(filename);

        byte[] buffer = new byte[1024];
        MessageDigest complete = MessageDigest.getInstance("MD5");
        int numRead;

        do {
            numRead = fis.read(buffer);
            if (numRead > 0) {
                complete.update(buffer, 0, numRead);
            }
        } while (numRead != -1);

        fis.close();
        return complete.digest();
    }

    static String getMD5Checksum(String filename) throws Exception {
        final byte[] checksum = createChecksum(filename);
        final StringBuilder result = new StringBuilder();

        for (byte chk : checksum) {
            result.append(Integer.toString((chk & 0xff) + 0x100, 16).substring(1));
        }

        return result.toString();
    }
}
