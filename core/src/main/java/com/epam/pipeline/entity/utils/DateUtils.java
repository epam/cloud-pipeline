/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.utils;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public final class DateUtils {

    private DateUtils() {
        //no op
    }

    public static Date now() {
        DateTime now = DateTime.now(DateTimeZone.UTC);
        return now.toDate();
    }

    public static LocalDateTime nowUTC() {
        return LocalDateTime.now(Clock.systemUTC());
    }

    public static LocalDateTime convertDateToLocalDateTime(final Date date) {
        return date.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    public static LocalDateTime convertEpochMillisToLocalDateTime(final long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    public static long convertLocalDateTimeToEpochMillis(final LocalDateTime dateTime) {
        return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public static long convertSecsToHours(final long secs) {
        return secs / 3600;
    }

    public static long convertSecsToMinOfHour(final long secs) {
        return secs % 3600 / 60;
    }

    public static long convertSecsToSecsOfMin(final long secs) {
        return secs % 60;
    }
}
