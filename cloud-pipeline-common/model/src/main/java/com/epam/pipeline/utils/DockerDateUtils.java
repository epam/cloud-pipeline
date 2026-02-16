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

package com.epam.pipeline.utils;

import com.epam.pipeline.entity.docker.HistoryEntryV2;
import com.epam.pipeline.entity.docker.RawImageDescriptionV2;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Comparator;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class DockerDateUtils {
    private static final int MAX_MICRO_SECONDS_LENGTH = 9;
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String PATTERN = "([\\d-]+)T([\\d:.]+)";
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern(DATE_TIME_FORMAT)
            .appendFraction(ChronoField.MICRO_OF_SECOND, 0, MAX_MICRO_SECONDS_LENGTH, true)
            .toFormatter();

    public static Date getEarliestDate(final RawImageDescriptionV2 rawImage) {
        return getMinElement(getDateStream(rawImage), Comparator.naturalOrder());
    }

    public static Date getLatestDate(final RawImageDescriptionV2 rawImage) {
        return getMinElement(getDateStream(rawImage), Comparator.reverseOrder());
    }

    private static Stream<Date> getDateStream(final RawImageDescriptionV2 rawImage) {
        return getHistoryEntryStream(rawImage)
                .map(HistoryEntryV2::getCreated)
                .map(DockerDateUtils::parseDate);
    }

    private static <T> T getMinElement(Stream<T> stream, Comparator<T> comparator) {
        return stream.min(comparator).orElseThrow(RuntimeException::new);
    }

    private static Stream<HistoryEntryV2> getHistoryEntryStream(final RawImageDescriptionV2 rawImage) {
        return rawImage.getHistory().stream();
    }

    private static Date parseDate(String v1Compatibility) {
        Matcher m = Pattern.compile(PATTERN).matcher(v1Compatibility);
        if (m.find()) {
            String date = m.group(1);
            String time =  m.group(2);
            return Date.from(extractDateTime(date, time).toInstant(ZoneOffset.UTC));
        }
        throw new IllegalArgumentException(
                String.format("v1Compatibility String %n%s%n has no matches with regex %s", v1Compatibility, PATTERN)
        );
    }

    private static LocalDateTime extractDateTime(String date, String time) {
        return LocalDateTime.parse(date + " " + time, FORMATTER);
    }

    private DockerDateUtils() {
        //no-op
    }
}
