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

package com.parrot.drone.groundsdk.arsdkengine.peripheral.miniature.media;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.parrot.drone.groundsdk.arsdkengine.devicecontroller.DroneController;
import com.parrot.drone.groundsdk.arsdkengine.http.HttpMiniatureMediaClient;
import com.parrot.drone.groundsdk.arsdkengine.peripheral.DronePeripheralController;
import com.parrot.drone.groundsdk.internal.device.peripheral.media.MediaItemCore;
import com.parrot.drone.groundsdk.internal.device.peripheral.media.MediaRequest;
import com.parrot.drone.groundsdk.internal.device.peripheral.media.MediaResourceCore;
import com.parrot.drone.groundsdk.internal.device.peripheral.media.MediaStoreCore;
import com.parrot.drone.groundsdk.internal.http.HttpRequest;
import com.parrot.drone.sdkcore.ulog.ULog;

import java.io.File;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.parrot.drone.groundsdk.arsdkengine.Logging.TAG_MEDIA;

/** MediaStore peripheral controller for Anafi family drones. */
public final class MiniatureMediaStore extends DronePeripheralController {

    /** The MediaStore peripheral for which this object is the backend. */
    @NonNull
    private final MediaStoreCore mMediaStore;

    /** HTTP media client. */
    @Nullable
    private HttpMiniatureMediaClient mMediaClient;

    /** Caches last media list browse result, when content changes are being watched; {@code null} otherwise. */
    @Nullable
    private List<MediaItemImpl> mCachedMediaList;

    /**
     * Constructor.
     *
     * @param droneController the drone controller that owns this peripheral controller.
     */
    public MiniatureMediaStore(@NonNull DroneController droneController) {
        super(droneController);
        mMediaStore = new MediaStoreCore(mComponentStore, mBackend);
    }

    @Override
    protected void onConnected() {
        // start monitoring changes
        mMediaClient = mDeviceController.getHttpClient(HttpMiniatureMediaClient.class);
        mBackend.browse(this::onUpdateSummaryCounts);

        mMediaStore.publish();
    }

    private void onUpdateSummaryCounts(MediaRequest.Status status, List<? extends MediaItemCore> result) {
        if (status == MediaRequest.Status.SUCCESS && result != null) {
            int photos = 0, videos = 0, photoResources = 0, videoResources = 0;

            for (MediaItemCore item : result) {
                switch (item.getType()) {
                    case PHOTO:
                        photos++;
                        photoResources += item.getResources().size();
                        break;
                    case VIDEO:
                        videos++;
                        videoResources += item.getResources().size();
                        break;
                }
            }

            mMediaStore.updatePhotoMediaCount(photos)
                    .updateVideoMediaCount(videos)
                    .updatePhotoResourceCount(photoResources)
                    .updateVideoResourceCount(videoResources)
                    .notifyUpdated();
        }
    }

    @Override
    protected void onDisconnecting() {
        mMediaStore.unpublish();
        mCachedMediaList = null;
        if (mMediaClient != null) {
            mMediaClient.dispose();
        }
    }

    /** Backend of MediaStoreCore implementation. */
    @SuppressWarnings("FieldCanBeLocal")
    private final MediaStoreCore.Backend mBackend = new MediaStoreCore.Backend() {

        /** {@code true} when content changes are being watched; media browse results are cached in this case. */
        private boolean mWatching;

        @Override
        public void startWatchingContentChange() {
            if (mMediaClient != null) {
                mWatching = true;
            }
        }

        @Override
        public void stopWatchingContentChange() {
            if (mMediaClient != null) {
                mWatching = false;
                mCachedMediaList = null;
            }
        }

        @Nullable
        @Override
        public MediaRequest browse(@NonNull MediaRequest.ResultCallback<List<? extends MediaItemCore>> callback) {
            MediaRequest request = null;
            if (mCachedMediaList != null) {
                callback.onRequestComplete(MediaRequest.Status.SUCCESS, mCachedMediaList);
            } else if (mMediaClient == null) {
                callback.onRequestComplete(MediaRequest.Status.FAILED, null);
            } else {
                request = mMediaClient.browse((status, code, result) -> {
                    switch (status) {
                        case SUCCESS:
                            assert result != null;

                            List<MediaItemImpl> list = MediaItemImpl.from(result);

                            if (mWatching) {
                                mCachedMediaList = list;
                            }

                            int photos = 0, videos = 0, photoResources = 0, videoResources = 0;

                            for (MediaItemCore item : list) {
                                switch (item.getType()) {
                                    case PHOTO:
                                        photos++;
                                        photoResources += item.getResources().size();
                                        break;
                                    case VIDEO:
                                        videos++;
                                        videoResources += item.getResources().size();
                                        break;
                                }
                            }

                            mMediaStore.updatePhotoMediaCount(photos)
                                    .updateVideoMediaCount(videos)
                                    .updatePhotoResourceCount(photoResources)
                                    .updateVideoResourceCount(videoResources)
                                    .notifyUpdated();

                            callback.onRequestComplete(MediaRequest.Status.SUCCESS, list);
                            break;
                        case FAILED:
                            callback.onRequestComplete(MediaRequest.Status.FAILED, null);
                            break;
                        case CANCELED:
                            callback.onRequestComplete(MediaRequest.Status.CANCELED, null);
                            break;
                    }
                })::cancel;
            }
            return request;
        }

        @Nullable
        @Override
        public MediaRequest download(@NonNull MediaResourceCore resource, @NonNull String destDir,
                                     @NonNull MediaRequest.ProgressResultCallback<File> callback) {
            if (mMediaClient == null) {
                callback.onRequestComplete(MediaRequest.Status.FAILED, null);
                return null;
            }

            File dest = new File(destDir, resource.getUid());
            return mMediaClient.download(MediaResourceImpl.unwrap(resource).getDownloadUrl(), dest,
                    new HttpRequest.ProgressStatusCallback() {

                        @Override
                        public void onRequestProgress(int progress) {
                            callback.onRequestProgress(progress);
                        }

                        @Override
                        public void onRequestComplete(@NonNull HttpRequest.Status status, int code) {
                            switch (status) {
                                case SUCCESS:
                                    callback.onRequestComplete(MediaRequest.Status.SUCCESS, dest);
                                    break;
                                case FAILED:
                                    callback.onRequestComplete(code == HttpRequest.STATUS_CODE_SERVER_ERROR ?
                                            MediaRequest.Status.ABORTED : MediaRequest.Status.FAILED, null);
                                    break;
                                case CANCELED:
                                    callback.onRequestComplete(MediaRequest.Status.CANCELED, null);
                                    break;
                            }
                        }
                    })::cancel;
        }

        @Nullable
        @Override
        public MediaRequest fetchThumbnail(@NonNull MediaItemCore media,
                                           @NonNull MediaRequest.ResultCallback<Bitmap> callback) {
            String url;
            if (mMediaClient == null || (url = MediaItemImpl.unwrap(media).getThumbnailUrl()) == null) {
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
            if (mMediaClient == null || (url = MediaResourceImpl.unwrap(resource).getThumbnailUrl()) == null) {
                callback.onRequestComplete(MediaRequest.Status.SUCCESS, null);
                return null;
            }
            return fetchThumbnail(url, resource.getUid(), callback);
        }

        @Nullable
        @Override
        public MediaRequest delete(@NonNull MediaItemCore media, @NonNull MediaRequest.StatusCallback callback) {
            if (mMediaClient == null) {
                callback.onRequestComplete(MediaRequest.Status.FAILED);
                return null;
            }
            return mMediaClient.deleteMedia(media.getUid(), (status, code) -> {
                mCachedMediaList = null;
                mMediaStore.notifyObservers();
                callback.onRequestComplete(MediaRequest.Status.SUCCESS);
            })::cancel;
        }

        @Nullable
        @Override
        public MediaRequest delete(@NonNull MediaResourceCore resource, @NonNull MediaRequest.StatusCallback callback) {
            if (mMediaClient == null) {
                callback.onRequestComplete(MediaRequest.Status.FAILED);
                return null;
            }
            return mMediaClient.deleteResource(resource.getUid(), (status, code) -> {
                switch (status) {
                    case SUCCESS:
                        callback.onRequestComplete(MediaRequest.Status.SUCCESS);
                        break;
                    case FAILED:
                        callback.onRequestComplete(code == HttpRequest.STATUS_CODE_SERVER_ERROR ?
                                MediaRequest.Status.ABORTED : MediaRequest.Status.FAILED);
                        break;
                    case CANCELED:
                        callback.onRequestComplete(MediaRequest.Status.CANCELED);
                        break;
                }
            })::cancel;
        }

        @Nullable
        @Override
        public MediaRequest wipe(@NonNull MediaRequest.StatusCallback callback) {
            if (mMediaClient == null) {
                callback.onRequestComplete(MediaRequest.Status.FAILED);
                return null;
            }
            return mMediaClient.deleteAll((status, code) -> {
                switch (status) {
                    case SUCCESS:
                        callback.onRequestComplete(MediaRequest.Status.SUCCESS);
                        break;
                    case FAILED:
                        callback.onRequestComplete(code == HttpRequest.STATUS_CODE_SERVER_ERROR ?
                                MediaRequest.Status.ABORTED : MediaRequest.Status.FAILED);
                        break;
                    case CANCELED:
                        callback.onRequestComplete(MediaRequest.Status.CANCELED);
                        break;
                }
            })::cancel;
        }

        /**
         * Fetches the thumbnail at the given url.
         * <p>
         * {@code callback} is always called, either after success or failure. <br/>
         * This method returns a {@code MediaRequest} object, which can be used to cancel the request.
         * <p>
         * <strong>Note:</strong> caller should ensure that {@link MiniatureMediaStore#mMediaClient} is not null before
         * calling this method.
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
            assert mMediaClient != null;
            return mMediaClient.fetch(url, (data) -> {
                Bitmap thumbnail = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (thumbnail == null && ULog.w(TAG_MEDIA)) {
                    ULog.w(TAG_MEDIA, "Failed to decode thumbnail [item:" + itemUid + "]");
                }
                return thumbnail;
            }, (status, code, thumbnail) -> {
                switch (status) {
                    case SUCCESS:
                        callback.onRequestComplete(MediaRequest.Status.SUCCESS, thumbnail);
                        break;
                    case FAILED:
                        callback.onRequestComplete(code < HttpRequest.STATUS_CODE_SERVER_ERROR ?
                                MediaRequest.Status.SUCCESS : MediaRequest.Status.FAILED, null);
                        break;
                    case CANCELED:
                        callback.onRequestComplete(MediaRequest.Status.CANCELED, null);
                        break;
                }
            })::cancel;
        }
    };
}
