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

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.shell.Shell;
import org.springframework.shell.jline.InteractiveShellApplicationRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Order(InteractiveShellApplicationRunner.PRECEDENCE - 2)
public class CLIApplicationRunner implements CommandLineRunner {

    private static final String HELP = "help";

    private final Shell shell;
    private final ConfigurableEnvironment environment;

    public CLIApplicationRunner(final Shell shell, final ConfigurableEnvironment environment) {
        this.shell = shell;
        this.environment = environment;
    }

    @Override
    public void run(String... args) throws Exception {
        List<String> commandsToRun = Arrays.stream(args)
                .filter(w -> !w.startsWith("@")).collect(Collectors.toList());
        InteractiveShellApplicationRunner.disable(environment);
        if (!commandsToRun.isEmpty()) {
            shell.run(new CLIInputProvider(commandsToRun));
        } else {
            shell.run(new CLIInputProvider(Collections.singletonList(HELP)));
        }
    }

}
