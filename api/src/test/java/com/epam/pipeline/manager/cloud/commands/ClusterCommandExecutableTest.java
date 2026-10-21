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

package com.epam.pipeline.manager.cloud.commands;

import org.junit.Assert;
import org.junit.Test;

public class ClusterCommandExecutableTest {

    private static final String CUSTOM_EXECUTABLE = "/usr/local/python3.12/bin/python3.12";
    private static final String SCRIPT = "nodeup.py";
    private static final String CLOUD = "aws";

    @Test
    public void nodeUpCommandUsesGivenExecutable() {
        final String command = NodeUpCommand.builder()
                .executable(CUSTOM_EXECUTABLE)
                .script(SCRIPT)
                .cloud(CLOUD)
                .build()
                .getCommand();
        Assert.assertTrue(command.startsWith(CUSTOM_EXECUTABLE + " " + SCRIPT));
    }

    @Test
    public void runIdArgCommandUsesGivenExecutable() {
        final String command = RunIdArgCommand.builder()
                .executable(CUSTOM_EXECUTABLE)
                .script(SCRIPT)
                .cloud(CLOUD)
                .build()
                .getCommand();
        Assert.assertTrue(command.startsWith(CUSTOM_EXECUTABLE + " " + SCRIPT));
    }

    @Test
    public void reassignCommandUsesGivenExecutable() {
        final String command = ReassignCommand.builder()
                .executable(CUSTOM_EXECUTABLE)
                .script(SCRIPT)
                .cloud(CLOUD)
                .build()
                .getCommand();
        Assert.assertTrue(command.startsWith(CUSTOM_EXECUTABLE + " " + SCRIPT));
    }

    @Test
    public void terminateNodeCommandUsesGivenExecutable() {
        final String command = TerminateNodeCommand.builder()
                .executable(CUSTOM_EXECUTABLE)
                .script(SCRIPT)
                .cloud(CLOUD)
                .build()
                .getCommand();
        Assert.assertTrue(command.startsWith(CUSTOM_EXECUTABLE + " " + SCRIPT));
    }
}
