/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.entity.docker.ContainerConfig;
import com.epam.pipeline.entity.docker.HistoryEntry;
import com.epam.pipeline.entity.docker.HistoryEntryV1;
import com.epam.pipeline.entity.docker.RawImageDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DockerParsingUtils {
    private static final int MAX_MICRO_SECONDS_LENGTH = 9;
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String PATTERN = "([\\d-]+)T([\\d:.]+)";
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern(DATE_TIME_FORMAT)
            .appendFraction(ChronoField.MICRO_OF_SECOND, 0, MAX_MICRO_SECONDS_LENGTH, true)
            .toFormatter();
    private static final String NOP_PREFIX = "#(nop)";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Date getEarliestDate(final RawImageDescription rawImage) {
        return getMinElement(getDateStream(rawImage), Comparator.naturalOrder());
    }

    public static Date getLatestDate(final RawImageDescription rawImage) {
        return getMinElement(getDateStream(rawImage), Comparator.reverseOrder());
    }

    public static List<String> getBuildHistory(final RawImageDescription rawImage) {
        final List<String> commandsHistory = getHistoryEntryStream(rawImage)
            .map(HistoryEntryV1::getContainerConfig)
            .map(ContainerConfig::getCommands)
            .map(commands -> String.join(StringUtils.EMPTY, commands))
            .map(DockerParsingUtils::cropNopPrefix)
            .map(command -> command.replaceAll("\\t", StringUtils.EMPTY))
            .collect(Collectors.toList());
        Collections.reverse(commandsHistory);
        return commandsHistory;
    }

    private static Stream<HistoryEntryV1> getHistoryEntryStream(final RawImageDescription rawImage) {
        return rawImage.getHistory().stream()
            .map(HistoryEntry::getV1Compatibility)
            .map(DockerParsingUtils::parseHistoryEntry);
    }

    private static HistoryEntryV1 parseHistoryEntry(final String jsonString) {
        try {
            return MAPPER.readValue(jsonString, HistoryEntryV1.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                "History entry received has illegal format and can't be parsed correctly.");
        }
    }

    private static String cropNopPrefix(final String command) {
        final int nopIndex = command.lastIndexOf(NOP_PREFIX);
        return nopIndex == -1
               ? command
               : command.substring(nopIndex + NOP_PREFIX.length() + 1);
    }

    private static Stream<Date> getDateStream(final RawImageDescription rawImage) {
        return getHistoryEntryStream(rawImage)
            .map(HistoryEntryV1::getCreated)
            .map(DockerParsingUtils::parseDate);
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

    private static <T> T getMinElement(Stream<T> stream, Comparator<T> comparator) {
        return stream.min(comparator).orElseThrow(RuntimeException::new);
    }

    private DockerParsingUtils() {
        //no-op
    }
}
