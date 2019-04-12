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

package com.epam.pipeline.manager.cloud.gcp;

import com.epam.pipeline.entity.region.GCPCustomInstanceType;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.gcp.extractor.GCPCustomMachineExtractor;
import com.epam.pipeline.manager.cloud.gcp.extractor.GCPObjectExtractor;
import com.epam.pipeline.manager.cloud.gcp.resource.GCPMachine;
import com.epam.pipeline.manager.cloud.gcp.resource.AbstractGCPObject;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class CustomGCPObjectExtractorTest {

    private static final double DELTA = 0.01;

    private final GCPObjectExtractor extractor = new GCPCustomMachineExtractor();

    @Test
    public void testCustomCpuMachineExtraction() {
        final GCPRegion region = new GCPRegion();
        region.setCustomInstanceTypes(Collections.singletonList(GCPCustomInstanceType.withCpu(1, 2)));
        final GCPMachine expectedMachine = GCPMachine.withCpu("custom-1-2048", "custom", 1, 2.0, 0);

        final List<AbstractGCPObject> actualMachines = extractor.extract(region);

        assertThat(actualMachines.size(), is(1));
        final AbstractGCPObject actualObject = actualMachines.get(0);
        assertThat(actualObject, instanceOf(GCPMachine.class));
        assertMachineEquals(expectedMachine, (GCPMachine) actualObject);
    }

    @Test
    public void testCustomGpuMachineExtraction() {
        final GCPRegion region = new GCPRegion();
        region.setCustomInstanceTypes(Collections.singletonList(GCPCustomInstanceType.withGpu(1, 2, 3, "K80")));
        final GCPMachine expectedMachine = GCPMachine.withGpu("gpu-custom-1-2048-k80-3", "custom", 1, 2, 0, 3, "K80");

        final List<AbstractGCPObject> actualMachines = extractor.extract(region);

        assertThat(actualMachines.size(), is(1));
        final AbstractGCPObject actualObject = actualMachines.get(0);
        assertThat(actualObject, instanceOf(GCPMachine.class));
        assertMachineEquals(expectedMachine, (GCPMachine) actualObject);
    }

    @Test
    public void testExtendedCustomMachineExtraction() {
        final GCPRegion region = new GCPRegion();
        region.setCustomInstanceTypes(Collections.singletonList(GCPCustomInstanceType.withCpu(2, 15)));
        final GCPMachine expectedMachine = GCPMachine.withCpu("gpu-custom-1-2048-k80-3", "custom", 2, 13, 2);

        final List<AbstractGCPObject> actualMachines = extractor.extract(region);

        assertThat(actualMachines.size(), is(1));
        final AbstractGCPObject actualObject = actualMachines.get(0);
        assertThat(actualObject, instanceOf(GCPMachine.class));
        assertMachineEquals(expectedMachine, (GCPMachine) actualObject);
    }

    private void assertMachineEquals(final GCPMachine expectedMachine, final GCPMachine actualMachine) {
        assertEquals(actualMachine.getCpu(), expectedMachine.getCpu());
        assertEquals(actualMachine.getRam(), expectedMachine.getRam(), DELTA);
        assertEquals(actualMachine.getExtendedRam(), expectedMachine.getExtendedRam(), DELTA);
        assertEquals(actualMachine.getGpu(), expectedMachine.getGpu());
    }
}
