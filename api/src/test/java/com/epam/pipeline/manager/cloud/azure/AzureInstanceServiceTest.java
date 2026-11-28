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

package com.epam.pipeline.manager.cloud.azure;

import com.epam.pipeline.entity.cloud.CloudInstanceState;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.exception.cloud.azure.AzureException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Covers only {@link AzureInstanceService#getInstanceState}: a VM that is not found is reported as
 * {@link CloudInstanceState#TERMINATED}, while a failed API call must propagate.
 */
public class AzureInstanceServiceTest {

    private static final String NODE_LABEL = "1";

    private final AzureVMService vmService = mock(AzureVMService.class);
    private final AzureRegion region = new AzureRegion();
    private final AzureInstanceService instanceService = new AzureInstanceService(null, null, null, vmService,
            null, null, null, null, null, null, null, null);

    @Test
    public void shouldReturnTerminatedWhenNoVmMatchesTheTag() {
        doThrow(new AzureException("Instance not found")).when(vmService).getVMStatsByTag(region, NODE_LABEL);

        assertThat(instanceService.getInstanceState(region, NODE_LABEL)).isEqualTo(CloudInstanceState.TERMINATED);
    }

    @Test
    public void shouldPropagateAFailedApiCallInsteadOfReportingTerminated() {
        doThrow(new AzureException(new IOException("authentication failed")))
                .when(vmService).getVMStatsByTag(region, NODE_LABEL);

        assertThrows(AzureException.class, () -> instanceService.getInstanceState(region, NODE_LABEL));
    }
}
