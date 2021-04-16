/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.docker.ImageHistoryLayer;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.stream.Collectors;

public final class DockerUtils {

    private static final String CMD_DOCKER_COMMAND = "CMD";
    private static final String ENTRYPOINT_DOCKER_COMMAND = "ENTRYPOINT";
    private static final String SHELL_FORM_PREFIX = "\"/bin/sh\" \"-c\"";

    private DockerUtils() {}

    public static String extractDefaultCommandFromHistory(final List<ImageHistoryLayer> history) {
        final List<String> commands = history.stream()
            .map(ImageHistoryLayer::getCommand)
            .collect(Collectors.toList());
        final Pair<Integer, Integer> defaultsPositions = getDefaultsPositions(commands);
        final int entrypointPos = defaultsPositions.getLeft();
        final int cmdPos = defaultsPositions.getRight();

        if (entrypointPos > -1) {
            final String entrypointInstruction =
                getTrimmedInstruction(commands.get(entrypointPos));
            if (cmdPos > entrypointPos) {
                final String cmdInstruction =
                    getTrimmedInstruction(commands.get(cmdPos));
                if (!(entrypointInstruction.startsWith(SHELL_FORM_PREFIX)
                      && cmdInstruction.startsWith(SHELL_FORM_PREFIX))) {
                    return String.join(" ", entrypointInstruction, cmdInstruction);
                }
            }
            return entrypointInstruction;
        } else if (cmdPos > -1) {
            return getTrimmedInstruction(commands.get(cmdPos));
        }
        return "";
    }

    private static String getTrimmedInstruction(final String command) {
        return command.substring(command.indexOf('[') + 1, command.lastIndexOf(']')).trim();
    }

    /**
     * Searches for ENTRYPOINT and CMD instructions in tool's image history.
     *
     * @param commands list, containing image's commands history
     * @return 2 long values, describing last positions of ENTRYPOINT and CMD instruction (-1 if none found)
     */
    private static Pair<Integer, Integer> getDefaultsPositions(final List<String> commands) {
        int entrypointPos = -1;
        int cmdPos = -1;
        for (int i = 0; i < commands.size(); i++) {
            final String command = commands.get(i);
            if (command.toUpperCase().trim().startsWith(ENTRYPOINT_DOCKER_COMMAND)) {
                entrypointPos = i;
            } else if (command.toUpperCase().trim().startsWith(CMD_DOCKER_COMMAND)) {
                cmdPos = i;
            }
        }
        return Pair.of(entrypointPos, cmdPos);
    }
}
