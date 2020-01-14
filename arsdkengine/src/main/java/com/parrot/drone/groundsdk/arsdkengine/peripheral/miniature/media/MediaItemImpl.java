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

import android.annotation.SuppressLint;
import android.os.Parcel;

import com.parrot.drone.groundsdk.arsdkengine.http.HttpMiniatureMediaItem;
import com.parrot.drone.groundsdk.device.peripheral.media.MediaItem;
import com.parrot.drone.groundsdk.internal.device.peripheral.media.MediaItemCore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Implementation of {@code MediaItemCore} over {@link HttpMiniatureMediaItem}.
 */
final class MediaItemImpl implements MediaItemCore {

    /**
     * Builds a list of {@code MediaItemImpl} instances from a collection of {@code HttpMiniatureMediaItem}.
     * <p>
     * HTTP items are validated by this method. Items that do not pass validation are excluded from the returned list.
     *
     * @param httpMedias http media items to convert
     *
     * @return a list of corresponding {@code MediaItemImpl} instances
     */
    @NonNull
    static List<MediaItemImpl> from(@NonNull Collection<HttpMiniatureMediaItem> httpMedias) {
        return httpMedias.stream()
                         .filter(HttpMiniatureMediaItem::isValid)
                         .map(MediaItemImpl::new)
                         .collect(Collectors.toList());
    }

    /**
     * Unwraps a media item to its internal {@code MediaItemImpl} representation.
     * <p>
     * This method assumes that the specified media item is based upon {@code MediaItemImpl}.
     *
     * @param media media item to unwrap
     *
     * @return internal {@code MediaItemImpl} representation of the specified media item
     */
    @NonNull
    static MediaItemImpl unwrap(@NonNull MediaItemCore media) {
        return (MediaItemImpl) media;
    }

    /** HTTP media item that backs this media. */
    @NonNull
    private final HttpMiniatureMediaItem mHttpMedia;

    /** Available resources in this media. */
    @NonNull
    private final List<MediaResourceImpl> mResources;

    /** Available metadata types for this media. */
    @NonNull
    private final EnumSet<MetadataType> mMetadataTypes;

    /**
     * Constructor.
     *
     * @param httpMedia HTTP media item backend
     */
    private MediaItemImpl(@NonNull HttpMiniatureMediaItem httpMedia) {
        if (!httpMedia.isValid()) {
            throw new IllegalArgumentException("Invalid HttpMiniatureMediaItem: " + httpMedia);
        }

        mHttpMedia = httpMedia;

        mResources = new ArrayList<>();
        for (HttpMiniatureMediaItem.Resource resource : mHttpMedia) {
            mResources.add(new MediaResourceImpl(this, resource));
        }

        mMetadataTypes = EnumSet.noneOf(MediaItem.MetadataType.class);
    }

    @NonNull
    @Override
    public String getUid() {
        assert mHttpMedia.getUid() != null;
        return mHttpMedia.getUid();
    }

    @NonNull
    @Override
    public String getName() {
        assert mHttpMedia.getUid() != null;
        return mHttpMedia.getUid();
    }

    @NonNull
    @Override
    public Type getType() {
        assert mHttpMedia.getType() != null;
        return MediaTypeAdapter.from(mHttpMedia.getType());
    }

    @Nullable
    @Override
    public String getRunUid() {
        return mHttpMedia.getUid();
    }

    @NonNull
    @Override
    @SuppressLint("SimpleDateFormat")
    public Date getCreationDate() {
        assert mHttpMedia.getDate() != null;

        final TimeZone myTimeZone = TimeZone.getDefault();
        final long myOffset;

        if (myTimeZone.inDaylightTime(mHttpMedia.getDate())) {
            myOffset = myTimeZone.getRawOffset() + myTimeZone.getDSTSavings();
        } else {
            myOffset = myTimeZone.getRawOffset();
        }

//        Log.i("date", "original date=" + mHttpMedia.getDate());
//        Log.i("date", " updated date=" + new Date(mHttpMedia.getDate().getTime() + myOffset));

        return new Date(mHttpMedia.getDate().getTime() + myOffset);
    }

    @Override
    public int getExpectedResourceCount() {
        return mResources.size();
    }

//    @Nullable
    @Override
    public PhotoMode getPhotoMode() {
        return MediaItem.PhotoMode.SINGLE;
    }

    @Nullable
    @Override
    public PanoramaType getPanoramaType() {
        return null;
    }

    @NonNull
    @Override
    public EnumSet<MetadataType> getAvailableMetadata() {
        return EnumSet.copyOf(mMetadataTypes);
    }

    @NonNull
    @Override
    public List<MediaResourceImpl> getResources() {
        return Collections.unmodifiableList(mResources);
    }

    /**
     * Retrieves the URL to use to fetch the thumbnail for this media.
     *
     * @return thumbnail URL, if available, otherwise {@code null}
     */
    @Nullable
    String getThumbnailUrl() {
        for (MediaResourceImpl resource : mResources) {
            if (resource.getThumbnailUrl() != null && getType() == Type.VIDEO) {
                return resource.getThumbnailUrl();
            } else if (resource.getThumbnailUrl() == null && getType() == Type.PHOTO) {
                return resource.getDownloadUrl();
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaItemImpl media = (MediaItemImpl) o;
        return getUid().equals(media.getUid());
    }

    @Override
    public int hashCode() {
        return getUid().hashCode();
    }

    //region Parcelable

    /**
     * Parcel deserializer.
     */
    public static final Creator<MediaItemImpl> CREATOR = new Creator<MediaItemImpl>() {

        @Override
        public MediaItemImpl createFromParcel(@NonNull Parcel src) {
            return new MediaItemImpl(src);
        }

        @Override
        public MediaItemImpl[] newArray(int size) {
            return new MediaItemImpl[size];
        }
    };

    /**
     * Constructor for parcel deserialization.
     *
     * @param src parcel to deserialize from
     */
    private MediaItemImpl(@NonNull Parcel src) {
        this(new HttpMiniatureMediaItem(src));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dst, int flags) {
        mHttpMedia.writeToParcel(dst);
    }

    //endregion
}
