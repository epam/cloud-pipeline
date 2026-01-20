/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.entity.cluster.ContainerInstance;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.NodeResources;
import com.epam.pipeline.entity.cluster.PodInstance;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;

public class NodeAvailableResourcesParserTest {
    private static final String NODE_NAME = "testNode";
    private static final String CPU = "cpu";
    private static final String MEMORY = "memory";
    private static final String GPU = "nvidia.com/gpu";
    private static final String TWO_CPUS = "2";
    private static final String TWO_CPUS_IN_MILLICORES = "2000m";
    private static final String ONE_GB = "1024Mi";
    private static final String FOUR_GB = "4096Mi";
    private static final String ONE_GPU = "1";
    private static final String TEST_CPU = "7400m";
    private static final long TWO_GB_IN_BYTES = 2147483648L;
    private static final long FOUR_GB_IN_BYTES = 4294967296L;

    @Test
    public void shouldCollectAllocatedPods() {
        final Map<String, String> wholeContainer = new HashMap<>();
        wholeContainer.put(CPU, TWO_CPUS);
        wholeContainer.put(MEMORY, ONE_GB);
        wholeContainer.put(GPU, ONE_GPU);

        final Map<String, String> noGpuContainer = new HashMap<>();
        noGpuContainer.put(CPU, TWO_CPUS_IN_MILLICORES);
        noGpuContainer.put(MEMORY, ONE_GB);

        final Map<String, String> allocatable = new HashMap<>();
        allocatable.put(CPU, TEST_CPU);
        allocatable.put(MEMORY, FOUR_GB);
        allocatable.put(GPU, ONE_GPU);

        final NodeInstance node = new NodeInstance();
        node.setName(NODE_NAME);
        node.setAllocatable(allocatable);

        final ContainerInstance container1 = new ContainerInstance();
        container1.setRequests(wholeContainer);
        final ContainerInstance container2 = new ContainerInstance();
        container2.setRequests(noGpuContainer);

        final PodInstance pod1 = new PodInstance();
        pod1.setContainers(Collections.singletonList(container1));
        final PodInstance pod2 = new PodInstance();
        pod2.setContainers(Collections.singletonList(container2));
        final List<PodInstance> pods = Arrays.asList(pod1, pod2, new PodInstance());

        final NodeResources actual = NodeAvailableResourcesParser.parse(node, pods);
        Assert.assertThat(actual.getNodeName(), is(NODE_NAME));
        Assert.assertThat(actual.getUsed().getCpu(), is(4L));
        Assert.assertThat(actual.getUsed().getGpu(), is(1L));
        Assert.assertThat(actual.getUsed().getMemory(), is(TWO_GB_IN_BYTES));
        Assert.assertThat(actual.getTotal().getCpu(), is(7L));
        Assert.assertThat(actual.getTotal().getGpu(), is(1L));
        Assert.assertThat(actual.getTotal().getMemory(), is(FOUR_GB_IN_BYTES));
    }

    @Test
    public void shouldProceedIfNoPodsAllocated() {
        final Map<String, String> allocatable = new HashMap<>();
        allocatable.put(CPU, TEST_CPU);
        allocatable.put(MEMORY, FOUR_GB);
        allocatable.put(GPU, ONE_GPU);

        final NodeInstance node = new NodeInstance();
        node.setName(NODE_NAME);
        node.setAllocatable(allocatable);

        final NodeResources actual = NodeAvailableResourcesParser.parse(node, null);
        Assert.assertThat(actual.getNodeName(), is(NODE_NAME));
        Assert.assertThat(actual.getUsed().getCpu(), is(0L));
        Assert.assertThat(actual.getUsed().getGpu(), is(0L));
        Assert.assertThat(actual.getUsed().getMemory(), is(0L));
        Assert.assertThat(actual.getTotal().getCpu(), is(7L));
        Assert.assertThat(actual.getTotal().getGpu(), is(1L));
        Assert.assertThat(actual.getTotal().getMemory(), is(FOUR_GB_IN_BYTES));
    }
}
