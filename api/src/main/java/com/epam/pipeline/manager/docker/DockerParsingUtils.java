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

import com.epam.pipeline.entity.docker.HistoryEntry;
import com.epam.pipeline.entity.docker.RawImageDescription;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class DockerParsingUtils {
    private static final int MAX_MICRO_SECONDS_LENGTH = 9;
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String PATTERN = "created\\W*([\\d-]+)T([\\d:.]+)";
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern(DATE_TIME_FORMAT)
            .appendFraction(ChronoField.MICRO_OF_SECOND, 0, MAX_MICRO_SECONDS_LENGTH, true)
            .toFormatter();
    private static final String NOP_PREFIX = "#(nop)";

    public static Date getEarliestDate(RawImageDescription rawImage) {
        return rawImage.getHistory()
                .stream()
                .map(historyEntry -> parseDate(historyEntry.getV1Compatibility()))
                .sorted()
                .findFirst()
                .orElseThrow(RuntimeException::new);
    }

    public static Date getLatestDate(RawImageDescription rawImage) {
        return rawImage.getHistory()
                .stream()
                .map(historyEntry -> parseDate(historyEntry.getV1Compatibility()))
                .sorted(Comparator.reverseOrder())
                .findFirst()
                .orElseThrow(RuntimeException::new);
    }

    public static List<String> getBuildHistory(final RawImageDescription rawImage) {
        final List<String> commands = rawImage.getHistory().stream()
            .map(DockerParsingUtils::extractCommand)
            .map(command -> command.replaceAll("\\t", StringUtils.EMPTY))
            .map(String::trim)
            .collect(Collectors.toList());
        Collections.reverse(commands);
        return commands;
    }

    private static String extractCommand(final HistoryEntry v1Compatibility) {
        final ObjectMapper mapper = new ObjectMapper();
        try {
            final JsonNode jsonNode = mapper.readValue(v1Compatibility.getV1Compatibility(), JsonNode.class);
            final String fullCommand = Optional.ofNullable(jsonNode.get("container_config"))
                .map(config -> config.get("Cmd"))
                .map(Iterable::spliterator)
                .map(spliterator -> StreamSupport.stream(spliterator, false))
                .orElse(Stream.empty())
                .map(JsonNode::asText)
                .collect(Collectors.joining());
            final int nopIndex = fullCommand.lastIndexOf(NOP_PREFIX);
            return nopIndex == -1
                   ? fullCommand
                   : fullCommand.substring(nopIndex + NOP_PREFIX.length() + 1);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                "History entry received has illegal format and can't be parsed correctly.");
        }
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

    private DockerParsingUtils() {
        //no-op
    }
}
