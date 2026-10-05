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

import com.epam.pipeline.manager.cluster.KubernetesConstants;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A pool node's launch is told only which pool it belongs to: the launch script reads the pool's own launch
 * configuration - a capacity reservation's target, zone and subnet included - from the pool itself.
 */
public class NodeUpCommandTest {

    private static final String POOL_ID = "42";

    @Test
    public void shouldTellTheLaunchWhichPoolTheNodeBelongsTo() {
        final String command = NodeUpCommand.builder()
                .executable(AbstractClusterCommand.EXECUTABLE)
                .script("nodeup.py")
                .runId("p-1")
                .instanceType("m5.large")
                .additionalLabels(Collections.singletonMap(KubernetesConstants.NODE_POOL_ID_LABEL, POOL_ID))
                .build()
                .getCommand();

        assertThat(command).contains("--label " + KubernetesConstants.NODE_POOL_ID_LABEL + "=" + POOL_ID);
        assertThat(command).doesNotContain("--capacity_reservation_id").doesNotContain("--availability_zone");
    }
}
