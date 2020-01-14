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

package com.parrot.drone.groundsdk.arsdkengine.peripheral.bebop.media;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.internal.device.peripheral.media.MediaItemCore;
import com.parrot.drone.groundsdk.internal.device.peripheral.media.MediaRequest;
import com.parrot.drone.groundsdk.internal.device.peripheral.media.MediaResourceCore;
import com.parrot.drone.groundsdk.internal.device.peripheral.media.MediaStoreCore;
import com.parrot.drone.groundsdk.internal.ftp.FtpSession;
import com.parrot.drone.groundsdk.internal.ftp.apachecommons.FTPFile;
import com.parrot.drone.sdkcore.arsdk.ArsdkFeatureCommon;
import com.parrot.drone.sdkcore.arsdk.command.ArsdkCommand;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** MediaStore peripheral controller for Anafi family drones. */
public final class BebopMediaStore extends DronePeripheralController {

//    @NonNull
//    private final DroneController mDroneController;

    /** The MediaStore peripheral for which this object is the backend. */
    @NonNull
    private final MediaStoreCore mMediaStore;

    /** Caches last media list browse result, when content changes are being watched; {@code null} otherwise. */
    @Nullable
    private List<MediaItemImpl> mCachedMediaList = null;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public BebopMediaStore(@NonNull DroneController droneController) {
        super(droneController);
//        mDroneController = droneController;
        mMediaStore = new MediaStoreCore(mComponentStore, mBackend);
    }

    @Override
    protected void onConnected() {
        // start monitoring changes
        new Handler().postDelayed(() -> {
            final FtpSession session = mDeviceController.getMediaStoreFtpSession();
            if (session != null) {
                session.getFileList(this::listingReady);
            }
        }, 1000);

        mMediaStore.publish();
    }

    private void listingReady(final boolean successful, final SortedMap<String, FTPFile> files) {

        if (successful) {
            StringBuilder sb = new StringBuilder(65565);

            if (mCachedMediaList != null) mCachedMediaList.clear();
            mCachedMediaList = new ArrayList<>();

            for (String filename : files.keySet()) {
                sb.append(filename);
                sb.append("\r\n");
            }

//            {
//                final Pattern pattern = Pattern.compile("^\\/[a-zA-Z0-9_\\-\\+\\.]*\\/[a-zA-Z0-9_\\-\\+\\.]*\\/academy\\/([a-zA-Z0-9_\\-\\+\\.]*)", Pattern.MULTILINE);
//                final Matcher matcher = pattern.matcher(sb);
//
//                while (matcher.find()) {
//                    ULog.i(TAG_MEDIA, "ACADEMY " + matcher.group(0) + " - " + matcher.group(1));
//                }
//            }


            {
                final Pattern pattern = Pattern.compile("^\\/[a-zA-Z0-9_\\-\\+\\.]*\\/[a-zA-Z0-9_\\-\\+\\.]*\\/media\\/([a-zA-Z0-9_\\-\\+\\.]*)", Pattern.MULTILINE);
                final Matcher matcher = pattern.matcher(sb);

                while (matcher.find()) {
//                    ULog.d(TAG_MEDIA, "MEDIA " + matcher.group(0) + " - " + matcher.group(1));
                    final MediaItemImpl item = new MediaItemImpl(files.get(matcher.group(0)), matcher.group(0));
                    mCachedMediaList.add(item);
                }
            }

//            {
//                final Pattern pattern = Pattern.compile("^\\/[a-zA-Z0-9_\\-\\+\\.]*\\/[a-zA-Z0-9_\\-\\+\\.]*\\/thumb\\/([a-zA-Z0-9_\\-\\+\\.]*)", Pattern.MULTILINE);
//                final Matcher matcher = pattern.matcher(sb);
//
//                while (matcher.find()) {
//                    ULog.i(TAG_MEDIA, "THUMB " + matcher.group(0) + " - " + matcher.group(1));
//                }
//            }

            int nbphotos = 0, nbvideos = 0;

            for (MediaItemImpl item : mCachedMediaList) {
                switch (item.getType()) {
                    case PHOTO:
                        nbphotos++;
                        break;
                    case VIDEO:
                        nbvideos++;
                        break;
                }
            }

            mMediaStore.updatePhotoMediaCount(nbphotos)
                    .updateVideoMediaCount(nbvideos)
                    .updatePhotoResourceCount(nbphotos)
                    .updateVideoResourceCount(nbvideos)
                    .notifyUpdated();
        }
    }

    @Override
    protected void onDisconnecting() {
        mMediaStore.unpublish();
        mCachedMediaList = null;
    }

    @Override
    protected void onCommandReceived(@NonNull ArsdkCommand command) {
        int featureId = command.getFeatureId();
        if (featureId == ArsdkFeatureCommon.CommonState.UID) {
            ArsdkFeatureCommon.CommonState.decode(command, mMediaStoreCallback);
        }
    }

    /**
     * Callbacks called when a command of the feature ArsdkFeatureMediastore is decoded.
     */
    private final ArsdkFeatureCommon.CommonState.Callback mMediaStoreCallback = new ArsdkFeatureCommon.CommonState.Callback() {
        @Override
        public void onMassStorageContent(int massStorageId, int nbphotos, int nbvideos, int nbpuds, int nbcrashlogs, int nbrawphotos) {
            if (nbvideos < 0 || nbphotos < 0 || nbrawphotos < 0) {
                throw new ArsdkCommand.RejectedEventException(
                        "Invalid media counts [nbphotos: " + nbphotos + ", nbvideos: "
                                + nbvideos + ", nbrawphotos: " + nbrawphotos + "]");
            }

            mMediaStore.updatePhotoMediaCount(nbphotos + nbrawphotos)
                    .updateVideoMediaCount(nbvideos)
                    .updatePhotoResourceCount(nbphotos + nbrawphotos)
                    .updateVideoResourceCount(nbvideos)
                    .notifyUpdated();
        }
    };


    /** Backend of MediaStoreCore implementation. */
    @SuppressWarnings("FieldCanBeLocal")
    private final MediaStoreCore.Backend mBackend = new MediaStoreCore.Backend() {

        /** {@code true} when content changes are being watched; media browse results are cached in this case. */
        private boolean mWatching;

        @Override
        public void startWatchingContentChange() {
//            if (mMediaClient != null) {
//                mMediaClient.setListener(mListener);
            mWatching = true;
//            }
        }

        @Override
        public void stopWatchingContentChange() {
//            if (mMediaClient != null) {
//                mMediaClient.setListener(null);
                mWatching = false;
                mCachedMediaList = null;
//            }
        }

        @Nullable
        @Override
        public MediaRequest browse(@NonNull MediaRequest.ResultCallback<List<? extends MediaItemCore>> callback) {
            if (mCachedMediaList != null && mCachedMediaList.size() > 0) {
                callback.onRequestComplete(MediaRequest.Status.SUCCESS, mCachedMediaList);
            } else {
                final FtpSession session = mDeviceController.getMediaStoreFtpSession();
                if (session != null) {
                    session.getFileList((successful, files) -> {
                        if (successful) {
                            listingReady(true, files);
                            callback.onRequestComplete(MediaRequest.Status.SUCCESS, mCachedMediaList);
                        } else {
                            callback.onRequestComplete(MediaRequest.Status.FAILED, null);
                        }

                    });
                } else {
                    callback.onRequestComplete(MediaRequest.Status.FAILED, null);
                }
            }
            return null;
        }

        @Nullable
        @Override
        public MediaRequest download(@NonNull MediaResourceCore resource, @NonNull String destDir,
                                     @NonNull MediaRequest.ProgressResultCallback<File> callback) {

            final FtpSession session = mDeviceController.getMediaStoreFtpSession();

            if (session == null) {
                callback.onRequestComplete(MediaRequest.Status.FAILED, null);
                return null;
            }

            final MediaRequest mediaRequest = () -> { /* too bad - can't cancel */ };
            File dest = new File(destDir, resource.getMedia().getName());

            session.retrieveFile(MediaResourceImpl.unwrap(resource).getDownloadUrl(), dest.getAbsolutePath(), false, new FtpSession.FtpTransferListener() {
                @Override
                public void onTransferCompleted(boolean successful, @Nullable Object data) {
                    if (successful) {
                        callback.onRequestComplete(MediaRequest.Status.SUCCESS, dest);
                    } else {
                        callback.onRequestComplete(MediaRequest.Status.FAILED, null);
                    }
                }

                @Override
                public void onTransferProgress(int percent) {
                    callback.onRequestProgress(percent);
                }
            });

            return mediaRequest;
        }

        @Nullable
        @Override
        public MediaRequest fetchThumbnail(@NonNull MediaItemCore media,
                                           @NonNull MediaRequest.ResultCallback<Bitmap> callback) {
            String url;
            if ((url = MediaItemImpl.unwrap(media).getThumbnailUrl()) == null) {
                callback.onRequestComplete(MediaRequest.Status.SUCCESS, null);
                return null;
            }
            return fetchThumbnail(url, media.getUid(), callback);
        }

        @Nullable
        @Override
        public MediaRequest fetchThumbnail(@NonNull MediaResourceCore resource,
                                           @NonNull MediaRequest.ResultCallback<Bitmap> callback) {
            String url;
            if ((url = MediaResourceImpl.unwrap(resource).getThumbnailUrl()) == null) {
                callback.onRequestComplete(MediaRequest.Status.SUCCESS, null);
                return null;
            }
            return fetchThumbnail(url, resource.getUid(), callback);
        }

        @Nullable
        @Override
        public MediaRequest delete(@NonNull MediaItemCore media, @NonNull MediaRequest.StatusCallback callback) {
            final MediaRequest mediaRequest = () -> { /* too bad - can't cancel */ };
            final FtpSession ftpSession = mDeviceController.getMediaStoreFtpSession();

            if (ftpSession != null) {
                ftpSession.deleteFile(media.getUid(), new FtpSession.FtpTransferListener() {
                    @Override
                    public void onTransferCompleted(boolean successful, @Nullable Object data) {
                        new Handler(Looper.getMainLooper()).post(() -> callback.onRequestComplete(successful ? MediaRequest.Status.SUCCESS : MediaRequest.Status.FAILED));
                    }
                    @Override
                    public void onTransferProgress(int percent) {
                    }
                });

                return mediaRequest;
            }

            new Handler(Looper.getMainLooper()).postDelayed(() -> callback.onRequestComplete(MediaRequest.Status.FAILED), 500);
            return mediaRequest;
        }

        @Nullable
        @Override
        public MediaRequest delete(@NonNull MediaResourceCore resource, @NonNull MediaRequest.StatusCallback callback) {
            return delete(resource.getMedia(), callback);
        }

        @Nullable
        @Override
        public MediaRequest wipe(@NonNull MediaRequest.StatusCallback callback) {
            final MediaRequest mediaRequest = () -> { /* too bad - can't cancel */ };
            final FtpSession ftpSession = mDeviceController.getMediaStoreFtpSession();

            if (ftpSession != null) {
                ftpSession.fullWipe(new FtpSession.FtpTransferListener() {
                    @Override
                    public void onTransferCompleted(boolean successful, @Nullable Object data) {
                        new Handler(Looper.getMainLooper()).post(() -> callback.onRequestComplete(successful ? MediaRequest.Status.SUCCESS : MediaRequest.Status.FAILED));
                    }
                    @Override
                    public void onTransferProgress(int percent) {
                    }
                });

                return mediaRequest;
            }

            new Handler(Looper.getMainLooper()).postDelayed(() -> callback.onRequestComplete(MediaRequest.Status.FAILED), 500);
            return mediaRequest;
        }

        /**
         * Fetches the thumbnail at the given url.
         * <p>
         * {@code callback} is always called, either after success or failure. <br/>
         * This method returns a {@code MediaRequest} object, which can be used to cancel the request.
         * <p>
         *
         * @param url      url of the thumbnail to download
         * @param itemUid  identifier of the thumbnail provider, used for failure logs
         * @param callback callback notified of request result
         *
         * @return a request that can be canceled
         */
        @NonNull
        private MediaRequest fetchThumbnail(@NonNull String url, @NonNull String itemUid,
                                            @NonNull MediaRequest.ResultCallback<Bitmap> callback) {


            final MediaRequest mediaRequest = () -> { /* too bad - can't cancel */ };
            final FtpSession ftpSession = mDeviceController.getMediaStoreFtpSession();

            if (ftpSession != null) {
                ftpSession.retrieveFileContents(url, new FtpSession.FtpTransferListener() {
                    @Override
                    public void onTransferCompleted(boolean successful, @Nullable Object data) {
                        if (successful) {
                            final byte[] thumb = (byte[]) data;
                            Log.i("bla", "thumb size=" + thumb.length);
                            final Bitmap thumbnail = BitmapFactory.decodeByteArray(thumb, 0, thumb.length);
                            if (thumbnail == null) {
                                callback.onRequestComplete(MediaRequest.Status.FAILED, null);
                            } else {
                                callback.onRequestComplete(MediaRequest.Status.SUCCESS, thumbnail);
                            }
                        } else {
                            callback.onRequestComplete(MediaRequest.Status.FAILED, null);
                        }
                    }

                    @Override
                    public void onTransferProgress(int percent) {
                    }
                });
            } else {
                new Handler(Looper.getMainLooper()).postDelayed(() -> callback.onRequestComplete(MediaRequest.Status.FAILED, null), 500);
            }

            return mediaRequest;
        }
    };
}
