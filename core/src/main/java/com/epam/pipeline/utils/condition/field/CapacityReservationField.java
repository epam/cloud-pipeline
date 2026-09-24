/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.utils.condition.field;

import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.utils.condition.FieldType;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.epam.pipeline.utils.condition.FieldType.ENUM;
import static com.epam.pipeline.utils.condition.FieldType.NUMERIC;
import static com.epam.pipeline.utils.condition.FieldType.STRING;

/**
 * The fields of a {@link CapacityReservation} that a capacity reservation approval policy may test.
 *
 * <p>Same shape as {@link PipelineRunField}: the display names are what a policy author writes in a
 * condition expression's {@code field}.
 */
@Getter
public enum CapacityReservationField implements SubjectEntityField<CapacityReservation> {

    /** Instance type being reserved, e.g. {@code p5.48xlarge}. Supports wildcards. */
    INSTANCE_TYPE(STRING,
        CapacityReservation::getInstanceType,
        "instance.type"),

    /** How many instances are being reserved. */
    INSTANCE_COUNT(NUMERIC,
        reservation -> str(reservation.getInstanceCount()),
        "instance.count"),

    /** Requested reservation duration in hours - the usual proxy for how much a request costs. */
    DURATION_HOURS(NUMERIC,
        reservation -> str(reservation.getDurationHours()),
        "duration.hours"),

    /** Which reservation product is being requested. */
    RESERVATION_TYPE(ENUM,
        reservation -> str(reservation.getReservationType()),
        "reservation.type"),

    /** Cloud provider the reservation targets. Scopes a policy to one provider. */
    CLOUD_PROVIDER(ENUM,
        reservation -> str(reservation.getCloudProvider()),
        "cloud.provider"),

    /** Identifier of the region the reservation targets. */
    REGION_ID(NUMERIC,
        reservation -> str(reservation.getRegionId()),
        "region.id"),

    /** Name of the user who opened the request. */
    OWNER(STRING,
        CapacityReservation::getOwner,
        "owner"),

    /**
     * Roles and groups of the requester, resolved at evaluation time from the owner name. This is how a
     * policy auto-approves for a particular group without hardcoding a role check.
     */
    OWNER_AUTHORITIES(FieldType.USER_AUTHORITIES,
        CapacityReservation::getOwner,
        "owner.authorities");

    private static final Map<String, CapacityReservationField> BY_DISPLAY_NAME;

    static {
        final Map<String, CapacityReservationField> map = new HashMap<>();
        for (final CapacityReservationField field : values()) {
            for (final String name : field.displayNames) {
                map.put(name, field);
            }
        }
        BY_DISPLAY_NAME = Collections.unmodifiableMap(map);
    }

    private final FieldType type;
    private final Function<CapacityReservation, String> extractor;
    private final String[] displayNames;

    CapacityReservationField(final FieldType type,
                             final Function<CapacityReservation, String> extractor,
                             final String... displayNames) {
        this.type = type;
        this.extractor = extractor;
        this.displayNames = displayNames;
    }

    @Override
    public String extract(final CapacityReservation reservation) {
        return extractor != null ? extractor.apply(reservation) : null;
    }

    /**
     * No capacity reservation field is duration-based: a reservation's own duration is a plain number here,
     * not an elapsed time being compared against a threshold.
     */
    @Override
    public boolean isSupportsDuration() {
        return false;
    }

    @Override
    public List<String> getDisplayNames() {
        return Arrays.asList(displayNames);
    }

    public static Optional<CapacityReservationField> findByDisplayName(final String name) {
        return Optional.ofNullable(BY_DISPLAY_NAME.get(name));
    }

    private static String str(final Object value) {
        return value != null ? value.toString() : null;
    }
}
