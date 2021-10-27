/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.release.notes.agent.cli;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;

@Slf4j
@ShellComponent
public class ReleaseNotesSender {

    @Value("${release.notes.agent.subsribers:}")
    private List<String> subscribers;

    @ShellMethod(key = "send-release-notes", value = "Grab and send release notes information to specified users")
    public void sendReleaseNotes(@ShellOption(defaultValue = ShellOption.NULL) String from,
                                 @ShellOption(defaultValue = ShellOption.NULL) String to,
                                 @ShellOption(defaultValue = ShellOption.NULL) List<String> emails) {
        if (from == null) {
            from = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";
        }
        if (emails == null) {
            emails = subscribers;
        }
        log.info(String.format(
                "Creating release notes report. Old version: %s, new version: %s. Report will be sent to: %s",
                from, to, emails));
    }

}
