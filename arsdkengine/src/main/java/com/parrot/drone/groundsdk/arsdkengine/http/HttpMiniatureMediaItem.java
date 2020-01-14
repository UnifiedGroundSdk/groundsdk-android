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

package com.parrot.drone.groundsdk.arsdkengine.http;

import android.os.Parcel;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.parrot.drone.groundsdk.arsdkengine.Iso8601;
import com.parrot.drone.groundsdk.arsdkengine.MamboDateFormat;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * A media item, as received from the drone HTTP media service.
 * <p>
 * Field names do not respect coding rules to map exactly the field names in the received JSON from which they are
 * parsed.
 * <p>
 * Private default constructors must be kept as they are used by GSON parser.
 */
@SuppressWarnings("unused")
public final class HttpMiniatureMediaItem implements Iterable<HttpMiniatureMediaItem.Resource> {

    /** Type of the media item. */
    public enum Type {

        /** Media is a photo. */
        photo,

        /** Media is a video. */
        video
    }

    /** Media item type. */
    @Nullable
    private Type type;

    /** Media unique identifier. */
    @Nullable
    private String uid;

    /** Media creation date and time. ISO 8601 base format. */
    @JsonAdapter(DateTimeParser.class)
    @Nullable
    private Date date;

    /** A media resource. */
    public static final class Resource {

        /** Unique identifier of the media that owns this resource. */
        @Nullable
        private String path;

        /** Media resource type. */
        public enum Type {

            /** Resource is a photo. */
            video,

            /** Resource is a photo. */
            photo,

            /** Resource is a thumbnail. */
            thumbnail,

            /** Resource is streaming. */
            streaming
        }

        /** Resource type. */
        @Nullable
        private Resource.Type type;

        /** Media resource format. */
        public enum Format {

            /** Resource is a JPEG file. */
            jpg,

            /** Resource is a DNG file. */
            dng,

            /** Resource is a MP4 file. */
            mp4
        }

        /** Resource format. */
        @Nullable
        private Resource.Format format;

        /** Resource size, in bytes. */
        @IntRange(from = 0)
        private long size;

        /**
         * Private, default constructor used by GSon deserializer.
         */
        private Resource() {
        }

        /**
         * Constructor for use in tests.
         *
         * @param path         media identifier
         * @param type         resource type
         * @param format       resource format
         * @param size         resource size in bytes
         */
        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        Resource(@Nullable String path, @Nullable Type type, @Nullable Format format,
                 @IntRange(from = 0) long size) {
            this.path = path;
            this.type = type;
            this.format = format;
            this.size = size;
        }

        /**
         * Retrieves the identifier of the media that owns this resource.
         *
         * @return resource media identifier
         */
        @Nullable
        public String getPath() {
            return path;
        }

        /**
         * Retrieves the resource type.
         *
         * @return resource type
         */
        @Nullable
        public Resource.Type getType() {
            return type;
        }

        /**
         * Retrieves the resource format.
         *
         * @return resource format
         */
        @Nullable
        public Resource.Format getFormat() {
            return format;
        }

        /**
         * Retrieves the resource size.
         *
         * @return resource size, in bytes
         */
        @IntRange(from = 0)
        public long getSize() {
            return size;
        }

        /**
         * Checks that this resource is valid.
         *
         * @return {@code true} if this resource is valid, otherwise {@code false}
         */
        public boolean isValid() {
            return path != null && type != null && format != null && size >= 0;
        }

        //region Parcelable

        /**
         * Constructor for parcel deserialization.
         *
         * @param parent media item parent
         * @param src    parcel to deserialize from
         */
        Resource(@NonNull HttpMiniatureMediaItem parent, @NonNull Parcel src) {
            path = src.readString();
            type = Type.values()[src.readInt()];
            format = Format.values()[src.readInt()];
            size = src.readLong();
        }

        /**
         * Serializes this resource to a parcel.
         *
         * @param dst parcel to serialize to
         */
        void writeToParcel(@NonNull Parcel dst) {
            dst.writeString(path);
            assert type != null;
            dst.writeInt(type.ordinal());
            assert format != null;
            dst.writeInt(format.ordinal());
            dst.writeLong(size);
        }

        //endregion
    }

    /** Media resource. */
    @Nullable
    private Resource[] resource;

    /**
     * Private, default constructor used by GSON deserializer.
     */
    private HttpMiniatureMediaItem() {
    }

    /**
     * Constructor for use in tests.
     *
     * @param uid            media id
     * @param type          media type
     * @param date          media creation date
     * @param resource     media resource
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    HttpMiniatureMediaItem(@Nullable String uid, @Nullable Type type, @Nullable Date date, @NonNull List<Resource> resource) {
        this.uid = uid;
        this.type = type;
        this.date = date;
        this.resource = resource.toArray(new Resource[0]);
    }

    /**
     * Retrieves the unique identifier of this media.
     *
     * @return media identifier
     */
    @Nullable
    public String getUid() {
        return uid;
    }

    /**
     * Retrieves the media type.
     *
     * @return media type
     */
    @Nullable
    public Type getType() {
        return type;
    }

    /**
     * Retrieves the media creation date.
     *
     * @return media creation date, in ISO 8601 base format
     */
    @Nullable
    public Date getDate() {
        return date;
    }

    @NonNull
    @Override
    public Iterator<Resource> iterator() {
        return resource == null ?
                Collections.emptyIterator() : Stream.of(resource).filter(Objects::nonNull).iterator();
    }

    /**
     * Checks that this media item is valid.
     * <p>
     * An invalid item will be rejected.
     *
     * @return {@code true} if this item is valid, otherwise {@code false}
     */
    public boolean isValid() {
        boolean valid = uid != null && type != null && date != null;
        if (valid) {
            for (int i = 0; i < resource.length && valid; i++) {
                Resource resource = this.resource[i];
                valid = resource == null || resource.isValid();
            }
        }

        return valid;
    }

    /** GSON adapter to convert between ISO 8601 base format and {@link Date}. */
    private static final class DateTimeParser extends TypeAdapter<Date> {

        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        @Override
        public void write(@NonNull JsonWriter out, @NonNull Date value) throws IOException {
            out.value(Iso8601.toBaseDateAndTimeFormat(value));
        }

        @Override
        public Date read(@NonNull JsonReader in) throws IOException {
            try {
                return MamboDateFormat.fromBaseDateAndTimeFormat(in.nextString());
            } catch (ParseException e) {
                return null;
            }
        }
    }

    //region Parcelable

    /**
     * Constructor for parcel deserialization.
     *
     * @param src parcel to deserialize from
     */
    public HttpMiniatureMediaItem(@NonNull Parcel src) {
        uid = src.readString();
        type = Type.values()[src.readInt()];
        date = new Date(src.readLong());
        resource = new HttpMiniatureMediaItem.Resource[src.readInt()];
        for (int i = 0; i < resource.length; i++) {
            resource[i] = new HttpMiniatureMediaItem.Resource(this, src);
        }
    }

    /**
     * Serializes this media to a parcel.
     *
     * @param dst parcel to serialize to
     */
    public void writeToParcel(@NonNull Parcel dst) {
        dst.writeString(uid);
        assert type != null;
        dst.writeInt(type.ordinal());
        assert date != null;
        dst.writeLong(date.getTime());

        Collection<Resource> nonNullResources = new ArrayList<>();
        forEach(nonNullResources::add);
        dst.writeInt(nonNullResources.size());
        nonNullResources.forEach(it -> it.writeToParcel(dst));
    }

    //endregion
}
