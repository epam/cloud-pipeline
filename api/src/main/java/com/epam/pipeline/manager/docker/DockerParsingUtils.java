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
import com.epam.pipeline.entity.execution.OSSpecificLaunchCommandTemplate;
import com.epam.pipeline.utils.StreamUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private static final String ADD_TO_FROM_COMMAND_PATTERN = "ADD (file|multi|dir):[a-zA-Z0-9]* in /";
    private static final List<String> COMMANDS = Arrays.asList("ADD", "ARG", "CMD", "COPY", "ENTRYPOINT", "ENV",
            "EXPOSE", "FROM", "HEALTHCHECK", "LABEL", "MAINTAINER", "ONBUILD", "RUN", "SHELL", "STOPSIGNAL", "USER",
            "VOLUME", "WORKDIR");
    private static final String ARG = "ARG ";
    private static final String CMD = "CMD ";
    private static final String ENTRYPOINT = "ENTRYPOINT ";
    private static final String ADD = "ADD ";
    private static final String COPY = "COPY ";
    private static final String RUN_TEMPLATE = "RUN %s";
    private static final String FROM_TEMPLATE = "FROM %s";

    public static Date getEarliestDate(final RawImageDescription rawImage) {
        return getMinElement(getDateStream(rawImage), Comparator.naturalOrder());
    }

    public static Date getLatestDate(final RawImageDescription rawImage) {
        return getMinElement(getDateStream(rawImage), Comparator.reverseOrder());
    }

    public static Optional<String> getPlatform(final RawImageDescription rawImage) {
        return getHistoryEntryStream(rawImage)
                .map(HistoryEntryV1::getOs)
                .filter(Objects::nonNull)
                .findFirst()
                .map(StringUtils::trim)
                .map(StringUtils::lowerCase);
    }

    public static List<String> getBuildHistory(final RawImageDescription rawImage) {
        final List<String> commandsHistory = getHistoryEntryStream(rawImage)
            .map(HistoryEntryV1::getContainerConfig)
            .map(ContainerConfig::getCommands)
            .filter(CollectionUtils::isNotEmpty)
            .map(commands -> String.join(StringUtils.EMPTY, commands))
            .map(DockerParsingUtils::cropNopPrefix)
            .map(command -> command.replaceAll("\\t", StringUtils.EMPTY))
            .collect(Collectors.toList());
        Collections.reverse(commandsHistory);
        return commandsHistory;
    }

    public static Map<String, String> getLabels(final RawImageDescription rawImage) {
        return getHistoryEntryStream(rawImage)
                .map(HistoryEntryV1::getContainerConfig)
                .map(ContainerConfig::getLabels)
                .filter(MapUtils::isNotEmpty)
                .flatMap(labels -> labels.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static List<String> processCommands(final String from, final List<String> commands,
                                               final List<OSSpecificLaunchCommandTemplate> podLaunchTemplatesLinux,
                                               final String podLaunchTemplatesWin) {
        final List<String> result = new ArrayList<>();

        result.add(String.format(FROM_TEMPLATE, from));
        // ONLY THE FIRST "ADD file:... / " line in the file has to be changed to "FROM <from>"
        final int startIndex = commands.get(0).matches(ADD_TO_FROM_COMMAND_PATTERN) ? 1 : 0;

        if (CollectionUtils.isEmpty(commands)) {
            return result;
        }

        final List<String> podLaunchPatterns = getPodLaunchPatterns(podLaunchTemplatesLinux, podLaunchTemplatesWin);
        String lastCmd = StringUtils.EMPTY;
        String lastEntrypoint = StringUtils.EMPTY;
        final List<String> args = new ArrayList<>();

        for (int i = startIndex; i < commands.size(); i++) {
            String command = commands.get(i);
            if (command.startsWith(ARG)) {
                args.add(command.replace(ARG, StringUtils.EMPTY).split("=")[0]);
            } else if (args.stream().anyMatch(command::contains)){
                for (String arg: args) {
                    command = command.replaceAll(String.format("%s=[^ ]* ", arg), StringUtils.EMPTY);
                }
                command = command.replaceAll("\\|[0-9]* ", StringUtils.EMPTY).trim();
            } else if (command.startsWith(CMD)) {
                lastCmd = command;
            } else if (command.startsWith(ENTRYPOINT)) {
                lastEntrypoint = command;
            }
            if (command.startsWith(ADD) || command.startsWith(COPY)) {
                command = command.replaceAll("(file|multi|dir):[a-zA-Z0-9]* in", "<source-location>");
            } else if (podLaunchPatterns.stream().anyMatch(command::matches)
                    || command.startsWith(CMD) || command.startsWith(ENTRYPOINT)) {
                continue;
            } else if (COMMANDS.stream().noneMatch(command::startsWith)) {
                command = String.format(RUN_TEMPLATE, command.trim());
            }
            result.add(command);
        }
        if (StringUtils.isNotBlank(lastCmd)) {
            result.add(lastCmd);
        }
        if (StringUtils.isNotBlank(lastEntrypoint)) {
            result.add(lastEntrypoint);
        }
        return result;
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

    private static String escapeSpecialCharacters(String input) {
        final String[] specialCharacters = { ".", "\\", "*", "?", "[", "^", "]", "+", "(", ")", "{", "}",
            "=", "!", "<", ">", "|", ":", "-" };
        for (String ch : specialCharacters) {
            input = input.replace(ch, "\\" + ch);
        }
        return input;
    }

    private static List<String> getPodLaunchPatterns(final List<OSSpecificLaunchCommandTemplate>
                                                             podLaunchTemplatesLinux,
                                                     final String podLaunchTemplatesWin) {
        return StreamUtils.appended(
                podLaunchTemplatesLinux.stream().map(r -> getLaunchPodPattern(r.getCommand())),
                getLaunchPodPattern(podLaunchTemplatesWin)
        ).collect(Collectors.toList());
    }

    private static String getLaunchPodPattern(final String command) {
        final String result = escapeSpecialCharacters(command).replaceAll("\\$[a-zA-Z0-9_]*", ".+");
        return result.replaceAll("\\$", "\\\\$");
    }

    private DockerParsingUtils() {
        //no-op
    }
}
