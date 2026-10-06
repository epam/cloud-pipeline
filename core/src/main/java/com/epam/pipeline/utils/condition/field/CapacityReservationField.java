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

@Getter
public enum CapacityReservationField implements SubjectEntityField<CapacityReservation> {

    INSTANCE_TYPE(STRING,
        CapacityReservation::getInstanceType,
        "instance.type"),

    INSTANCE_COUNT(NUMERIC,
        reservation -> str(reservation.getInstanceCount()),
        "instance.count"),

    DURATION_HOURS(NUMERIC,
        reservation -> str(reservation.getDurationHours()),
        "duration.hours"),

    RESERVATION_TYPE(ENUM,
        reservation -> str(reservation.getReservationType()),
        "reservation.type"),

    CLOUD_PROVIDER(ENUM,
        reservation -> str(reservation.getCloudProvider()),
        "cloud.provider"),

    REGION_ID(NUMERIC,
        reservation -> str(reservation.getRegionId()),
        "region.id"),

    OWNER(STRING,
        CapacityReservation::getOwner,
        "owner"),

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
