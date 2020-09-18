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

package com.epam.pipeline.dts.transfer.service;

import com.epam.pipeline.cmd.CmdExecutor;
import com.epam.pipeline.dts.transfer.service.impl.PipelineCliProviderImpl;
import org.apache.commons.collections4.MapUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PipelineCliProviderTest {

    private static final String QSUB_TEMPLATE = "qsub template";
    private static final String API = "api";
    private static final String API_TOKEN = "apiToken";

    private final CmdExecutorsProvider cmdExecutorsProvider = mock(CmdExecutorsProvider.class);

    private PipelineCliProvider getPipelineCliProvider(final boolean isGridUploadEnabled) {
        return new PipelineCliProviderImpl(cmdExecutorsProvider, "pipe", "", QSUB_TEMPLATE,
            isGridUploadEnabled, false, 5);
    }

    @BeforeEach
    void setUp() {
        final CmdExecutor cmdExecutor = mock(CmdExecutor.class);
        when(cmdExecutorsProvider.getCmdExecutor()).thenReturn(cmdExecutor);
        when(cmdExecutorsProvider.getQsubCmdExecutor(notNull(), any())).thenReturn(cmdExecutor);
        when(cmdExecutorsProvider.getImpersonatingCmdExecutor(any())).thenReturn(cmdExecutor);
        when(cmdExecutorsProvider.getEnvironmentCmdExecutor(notNull(), any())).thenReturn(cmdExecutor);
    }

    @Test
    void getPipelineCliShouldReturnCliWithAuthenticatedCmdExecutor() {
        final PipelineCliProvider pipelineCliProvider = getPipelineCliProvider(true);

        pipelineCliProvider.getPipelineCLI(API, API_TOKEN);

        verify(cmdExecutorsProvider).getEnvironmentCmdExecutor(notNull(), eq(pipelineCredentials()));
    }

    @Test
    void getPipelineCliShouldReturnCliWithQsubExecutorIfItIsSpecified() {
        final PipelineCliProvider pipelineCliProvider = getPipelineCliProvider(true);

        pipelineCliProvider.getPipelineCLI(API, API_TOKEN);

        verify(cmdExecutorsProvider).getQsubCmdExecutor(notNull(), eq(QSUB_TEMPLATE));
    }

    @Test
    void getPipelineCliShouldReturnCliWithoutQsubExecutorIfItIsNotSpecified() {
        final PipelineCliProvider pipelineCliProvider = getPipelineCliProvider(false);

        pipelineCliProvider.getPipelineCLI(API, API_TOKEN);

        verify(cmdExecutorsProvider, times(0)).getQsubCmdExecutor(notNull(), any());
    }

    private Map<String, String> pipelineCredentials() {
        return MapUtils.putAll(new HashMap<>(), new Object[][] {
            {"API", API},
            {"API_TOKEN", API_TOKEN}
        });
    }
}
