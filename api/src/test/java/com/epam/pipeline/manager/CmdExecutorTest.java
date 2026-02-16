/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager;

import com.epam.pipeline.exception.CmdExecutionException;
import org.junit.Test;

public class CmdExecutorTest {

    public static final String COMMAND_WITH_SLEEP = "sleep %f";
    public static final int TIMEOUT = 100;
    public static final double MILLS_100 = 0.1;
    public static final double MILLS_300 = 0.3;
    public static final int MILLS_IN_SEC = 1000;

    @Test
    public void executeCommandWithOutTimeoutFinishes() {
        CmdExecutor cmdExecutor = new CmdExecutor();

        double timeToSleep = MILLS_100;
        cmdExecutor.executeCommand(String.format(COMMAND_WITH_SLEEP, timeToSleep));

        timeToSleep = MILLS_300;
        cmdExecutor.executeCommand(String.format(COMMAND_WITH_SLEEP, timeToSleep));
    }

    @Test
    public void executeCommandWithTimeoutShouldFinishIfNotReachedTimeout() {
        CmdExecutor cmdExecutor = new CmdExecutor();

        // bash sleep measured in sec but timeout in mills
        double timeToSleep = TIMEOUT / 2 / MILLS_IN_SEC;
        cmdExecutor.executeCommand(String.format(COMMAND_WITH_SLEEP, timeToSleep), TIMEOUT);

    }

    @Test(expected = CmdExecutionException.class)
    public void executeCommandWithTimeoutShouldBeInterruptedIfReachedTimeout() {
        CmdExecutor cmdExecutor = new CmdExecutor();

        // bash sleep measured in sec but timeout in mills
        double timeToSleep = (TIMEOUT + TIMEOUT) / MILLS_IN_SEC;
        cmdExecutor.executeCommand(String.format(COMMAND_WITH_SLEEP, timeToSleep), TIMEOUT);
    }
}
