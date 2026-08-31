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

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.entity.docker.HistoryEntryV2;
import com.epam.pipeline.entity.docker.RawImageDescriptionV2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final String SPACE = " ";
    private static final String ADD_TO_FROM_COMMAND_PATTERN = "(ADD|COPY) (file|multi|dir):[a-zA-Z0-9]* in /";
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

    public static Date getEarliestDate(final RawImageDescriptionV2 rawImage) {
        return getMinElement(getDateStream(rawImage), Comparator.naturalOrder());
    }

    public static Date getLatestDate(final RawImageDescriptionV2 rawImage) {
        return getMinElement(getDateStream(rawImage), Comparator.reverseOrder());
    }

    public static Optional<String> getPlatform(final RawImageDescriptionV2 rawImage) {
        return Optional.of(rawImage.getOs().toLowerCase());
    }

    public static String getBuildHistory(final HistoryEntryV2 historyEntry) {
        return cropNopPrefix(historyEntry.getCreatedBy()).trim().replaceAll("\\s+", SPACE);
    }

    public static Map<String, String> getLabels(final RawImageDescriptionV2 rawImage) {
        return rawImage.getContainerConfig().getLabels();
    }

    public static List<String> processCommands(final String from, final List<String> commands,
                                               final List<String> commandPatternsToSkip) {
        final List<String> result = new ArrayList<>();

        result.add(String.format(FROM_TEMPLATE, from));
        // ONLY THE FIRST "ADD file:... / " or "COPY file:... / " line in the file has to be changed to "FROM <from>"
        final int startIndex = prettifyCommand(commands.get(0)).matches(ADD_TO_FROM_COMMAND_PATTERN) ? 1 : 0;

        if (CollectionUtils.isEmpty(commands)) {
            return result;
        }

        String lastCmd = StringUtils.EMPTY;
        String lastEntrypoint = StringUtils.EMPTY;
        final List<String> args = new ArrayList<>();

        for (int i = startIndex; i < commands.size(); i++) {
            String command = prettifyCommand(commands.get(i));

            if (commandPatternsToSkip.stream().anyMatch(command::matches)) {
                continue;
            }

            if (command.startsWith(ARG)) {
                args.add(command.replace(ARG, StringUtils.EMPTY).split("=")[0]);
            } else if (args.stream().anyMatch(command::contains)) {
                for (String arg: args) {
                    command = command.replaceAll(String.format(" %s=[^ ]* ", arg), SPACE);
                }
            } else if (command.startsWith(CMD)) {
                lastCmd = command;
            } else if (command.startsWith(ENTRYPOINT)) {
                lastEntrypoint = command;
            }

            if (command.startsWith(ADD) || command.startsWith(COPY)) {
                command = command.replaceAll("(file|multi|dir):[a-zA-Z0-9]* in", "<source-location>");
            } else if (command.startsWith(CMD) || command.startsWith(ENTRYPOINT)) {
                continue;
            } else if (COMMANDS.stream().noneMatch(command::startsWith)) {
                command = String.format(RUN_TEMPLATE, command.trim());
            }
            result.add(prettifyCommand(command.replaceAll(" \\|[0-9]+ ", SPACE)));
        }
        if (StringUtils.isNotBlank(lastCmd)) {
            result.add(lastCmd);
        }
        if (StringUtils.isNotBlank(lastEntrypoint)) {
            result.add(lastEntrypoint);
        }
        return result;
    }

    private static String prettifyCommand(final String command) {
        return command.replaceAll("\\s+", SPACE).trim();
    }

    public static String getLaunchPodPattern(final String command) {
        final String result = escapeSpecialCharacters(command).replaceAll("\\$[a-zA-Z0-9_]*", ".+");
        return result.replaceAll("\\$", "\\\\$");
    }

    private static Stream<HistoryEntryV2> getHistoryEntryStream(final RawImageDescriptionV2 rawImage) {
        return rawImage.getHistory().stream();
    }

    private static String cropNopPrefix(final String command) {
        final int nopIndex = command.lastIndexOf(NOP_PREFIX);
        return nopIndex == -1
               ? command
               : command.substring(nopIndex + NOP_PREFIX.length() + 1);
    }

    private static Stream<Date> getDateStream(final RawImageDescriptionV2 rawImage) {
        return getHistoryEntryStream(rawImage)
                .map(HistoryEntryV2::getCreated)
                .map(DockerParsingUtils::parseDate);
    }

    public static Date parseDate(String v1Compatibility) {
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
        return LocalDateTime.parse(date + SPACE + time, FORMATTER);
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

    private DockerParsingUtils() {
        //no-op
    }
}
