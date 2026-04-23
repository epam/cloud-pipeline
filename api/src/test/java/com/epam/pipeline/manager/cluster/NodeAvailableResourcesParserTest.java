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
import joptsimple.internal.Strings;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.epam.pipeline.manager.cluster.KubernetesConstants.RUN_ID_LABEL;
import static com.epam.pipeline.manager.cluster.KubernetesConstants.OWNER_LABEL;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.nullValue;

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
    private static final long ONE_GB_IN_BYTES = 1073741824L;
    private static final long TWO_GB_IN_BYTES = 2147483648L;
    private static final long FOUR_GB_IN_BYTES = 4294967296L;
    private static final String RUN_ID_LABEL_VALUE = "123";
    private static final String RUN_ID_LABEL_VALUE_2 = "321";
    private static final String POOL_ID_LABEL_VALUE = "p-123";
    private static final long RUN_ID = 123L;
    private static final long RUN_ID_2 = 321L;
    private static final String OWNER = "USER";

    @Test
    public void shouldCollectAllocatedPods() {
        final NodeInstance node = node();

        final ContainerInstance wholeContainer = container(TWO_CPUS, ONE_GB, ONE_GPU);
        final ContainerInstance noGpuContainer = container(TWO_CPUS_IN_MILLICORES, ONE_GB, null);

        final PodInstance pod1 = pod(Collections.singletonList(wholeContainer));
        final PodInstance pod2 = pod(Collections.singletonList(noGpuContainer));
        final List<PodInstance> pods = Arrays.asList(pod1, pod2, new PodInstance());

        final NodeResources actual = NodeAvailableResourcesParser.parse(node, pods, false);
        Assert.assertThat(actual.getNodeName(), is(NODE_NAME));
        Assert.assertThat(actual.getUsed().getCpu(), is(4L));
        Assert.assertThat(actual.getUsed().getGpu(), is(1L));
        Assert.assertThat(actual.getUsed().getMemory(), is(TWO_GB_IN_BYTES));
        Assert.assertThat(actual.getTotal().getCpu(), is(7L));
        Assert.assertThat(actual.getTotal().getGpu(), is(1L));
        Assert.assertThat(actual.getTotal().getMemory(), is(FOUR_GB_IN_BYTES));
        Assert.assertThat(actual.getDetails(), is(nullValue()));
    }

    @Test
    public void shouldProceedIfNoPodsAllocated() {
        final NodeResources actual = NodeAvailableResourcesParser.parse(node(), null, false);

        Assert.assertThat(actual.getNodeName(), is(NODE_NAME));
        Assert.assertThat(actual.getUsed().getCpu(), is(0L));
        Assert.assertThat(actual.getUsed().getGpu(), is(0L));
        Assert.assertThat(actual.getUsed().getMemory(), is(0L));
        Assert.assertThat(actual.getTotal().getCpu(), is(7L));
        Assert.assertThat(actual.getTotal().getGpu(), is(1L));
        Assert.assertThat(actual.getTotal().getMemory(), is(FOUR_GB_IN_BYTES));
        Assert.assertThat(actual.getDetails(), is(nullValue()));
    }

    @Test
    public void shouldPopulateRunDetailsWhenSingleRunIdLabelProvided() {
        final NodeInstance node = node();

        final ContainerInstance container = container(TWO_CPUS, ONE_GB, ONE_GPU);
        final Map<String, String> labels = new HashMap<>();
        labels.put(RUN_ID_LABEL, RUN_ID_LABEL_VALUE);
        final PodInstance runPod = pod(Collections.singletonList(container), labels);
        final PodInstance noRunPod = pod(Collections.singletonList(container));
        final List<PodInstance> pods = Arrays.asList(runPod, noRunPod, new PodInstance());

        final NodeResources actual = NodeAvailableResourcesParser.parse(node, pods, true);

        Assert.assertThat(actual.getDetails().size(), is(1));
        final NodeResources.RunDetails runDetails = actual.getDetails().get(0);
        Assert.assertThat(runDetails.getRunId(), is(RUN_ID));
        Assert.assertThat(runDetails.getOwner(), is(nullValue()));
        Assert.assertThat(runDetails.getQuantities().getCpu(), is(2L));
        Assert.assertThat(runDetails.getQuantities().getGpu(), is(1L));
        Assert.assertThat(runDetails.getQuantities().getMemory(), is(ONE_GB_IN_BYTES));
    }

    @Test
    public void shouldPopulateRunDetailsWhenRunIdAndOwnerLabelsProvided() {
        final NodeInstance node = node();

        final ContainerInstance container = container(TWO_CPUS, ONE_GB, ONE_GPU);
        final Map<String, String> labels = new HashMap<>();
        labels.put(RUN_ID_LABEL, RUN_ID_LABEL_VALUE);
        labels.put(OWNER_LABEL, OWNER);
        final PodInstance runPod = pod(Collections.singletonList(container), labels);
        final PodInstance noRunPod = pod(Collections.singletonList(container));
        final List<PodInstance> pods = Arrays.asList(runPod, noRunPod, new PodInstance());

        final NodeResources actual = NodeAvailableResourcesParser.parse(node, pods, true);

        Assert.assertThat(actual.getDetails().size(), is(1));
        final NodeResources.RunDetails runDetails = actual.getDetails().get(0);
        Assert.assertThat(runDetails.getRunId(), is(RUN_ID));
        Assert.assertThat(runDetails.getOwner(), is(OWNER));
        Assert.assertThat(runDetails.getQuantities().getCpu(), is(2L));
        Assert.assertThat(runDetails.getQuantities().getGpu(), is(1L));
        Assert.assertThat(runDetails.getQuantities().getMemory(), is(ONE_GB_IN_BYTES));
    }

    @Test
    public void shouldPopulateRunDetailsWhenMultipleRunIdLabelProvided() {
        final NodeInstance node = node();

        final ContainerInstance container = container(TWO_CPUS, ONE_GB, ONE_GPU);

        final Map<String, String> labels1 = new HashMap<>();
        labels1.put(RUN_ID_LABEL, RUN_ID_LABEL_VALUE);
        final PodInstance runPod1 = pod(Collections.singletonList(container), labels1);

        final Map<String, String> labels2 = new HashMap<>();
        labels2.put(RUN_ID_LABEL, RUN_ID_LABEL_VALUE_2);
        final PodInstance runPod2 = pod(Collections.singletonList(container), labels2);

        final PodInstance noRunPod = pod(Collections.singletonList(container));

        final List<PodInstance> pods = Arrays.asList(runPod1, runPod2, noRunPod, new PodInstance());

        final NodeResources actual = NodeAvailableResourcesParser.parse(node, pods, true);

        Assert.assertThat(actual.getDetails().size(), is(2));
        actual.getDetails().forEach(runDetails -> {
            Assert.assertThat(runDetails.getRunId(), isIn(Arrays.asList(RUN_ID, RUN_ID_2).toArray()));
            Assert.assertThat(runDetails.getOwner(), is(nullValue()));
            Assert.assertThat(runDetails.getQuantities().getCpu(), is(2L));
            Assert.assertThat(runDetails.getQuantities().getGpu(), is(1L));
            Assert.assertThat(runDetails.getQuantities().getMemory(), is(ONE_GB_IN_BYTES));
        });
    }

    @Test
    public void shouldOmitRunDetailsWhenRunIdLabelNotProvided() {
        final NodeInstance node = node();

        final ContainerInstance container = container(TWO_CPUS, ONE_GB, ONE_GPU);
        final PodInstance noRunPod = pod(Collections.singletonList(container));
        final List<PodInstance> pods = Arrays.asList(noRunPod, new PodInstance());

        final NodeResources actual = NodeAvailableResourcesParser.parse(node, pods, true);

        Assert.assertThat(actual.getDetails(), is(empty()));
    }

    @Test
    public void shouldOmitRunDetailsWhenRunIdLabelIsNotNumeric() {
        final NodeInstance node = node();

        final ContainerInstance container = container(TWO_CPUS, ONE_GB, ONE_GPU);
        final Map<String, String> labels = new HashMap<>();
        labels.put(RUN_ID_LABEL, POOL_ID_LABEL_VALUE);
        final PodInstance noRunPod = pod(Collections.singletonList(container), labels);
        final List<PodInstance> pods = Arrays.asList(noRunPod, new PodInstance());

        final NodeResources actual = NodeAvailableResourcesParser.parse(node, pods, true);

        Assert.assertThat(actual.getDetails(), is(empty()));
    }

    @Test
    public void shouldOmitRunDetailsWhenRunIdLabelIsBlank() {
        final NodeInstance node = node();

        final ContainerInstance container = container(TWO_CPUS, ONE_GB, ONE_GPU);
        final Map<String, String> labels = new HashMap<>();
        labels.put(RUN_ID_LABEL, Strings.EMPTY);
        final PodInstance noRunPod = pod(Collections.singletonList(container), labels);
        final List<PodInstance> pods = Arrays.asList(noRunPod, new PodInstance());

        final NodeResources actual = NodeAvailableResourcesParser.parse(node, pods, true);

        Assert.assertThat(actual.getDetails(), is(empty()));
    }

    private static PodInstance pod(final List<ContainerInstance> containers) {
        return pod(containers, Collections.emptyMap());
    }

    private static PodInstance pod(final List<ContainerInstance> containers,
                                   final Map<String, String> labels) {
        final PodInstance pod = new PodInstance();
        pod.setLabels(labels);
        pod.setContainers(containers);
        return pod;
    }

    private static ContainerInstance container(final String cpuValue,
                                               final String memoryValue,
                                               final String gpuValue) {
        final Map<String, String> requests = new HashMap<>();
        if (Objects.nonNull(cpuValue)) {
            requests.put(CPU, cpuValue);
        }
        if (Objects.nonNull(memoryValue)) {
            requests.put(MEMORY, memoryValue);
        }
        if (Objects.nonNull(gpuValue)) {
            requests.put(GPU, ONE_GPU);
        }

        final ContainerInstance container = new ContainerInstance();
        container.setRequests(requests);

        return container;
    }

    private static NodeInstance node() {
        final Map<String, String> allocatable = new HashMap<>();
        allocatable.put(CPU, TEST_CPU);
        allocatable.put(MEMORY, FOUR_GB);
        allocatable.put(GPU, ONE_GPU);

        final NodeInstance node = new NodeInstance();
        node.setName(NODE_NAME);
        node.setAllocatable(allocatable);

        return node;
    }
}
