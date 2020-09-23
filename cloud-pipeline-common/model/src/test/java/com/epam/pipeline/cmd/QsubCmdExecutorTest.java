/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.cmd;

import org.junit.Test;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class QsubCmdExecutorTest {

    private static final String COMMAND = "command";
    private static final String QSUB_TEMPLATE = "qsub -sync y -o %s -e %s %s";
    private static final String FILE_NAME_ELEMENT_PATTERN = "(\\w*\\d*\\.*\\/*\\-*)+";
    private static final String QSUB_TEMPLATE_PATTERN =
        String.format(QSUB_TEMPLATE, FILE_NAME_ELEMENT_PATTERN, FILE_NAME_ELEMENT_PATTERN, FILE_NAME_ELEMENT_PATTERN);
    private final CmdExecutor innerExecutor = mock(CmdExecutor.class);
    private final CmdExecutor qsubCmdExecutor = new QsubCmdExecutor(innerExecutor, QSUB_TEMPLATE);

    @Test
    public void executorShouldDelegateCallToInnerExecutor() {
        qsubCmdExecutor.executeCommand(COMMAND);

        verify(innerExecutor).executeCommand(any(), 
                eq(Collections.emptyMap()), eq(null), eq(null));
    }

    @Test
    public void executorShouldCallQsubTemplateCommandInsteadOfTheOriginalOne() {
        qsubCmdExecutor.executeCommand(COMMAND);

        verify(innerExecutor).executeCommand(matches(QSUB_TEMPLATE_PATTERN), 
                eq(Collections.emptyMap()), eq(null), eq(null));
    }
}
