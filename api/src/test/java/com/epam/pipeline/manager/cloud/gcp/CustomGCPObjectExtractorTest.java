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
    private static final int CPU = 1;
    private static final double RAM = 2.0;
    private static final double EXTENDED_RAM = 2.0;
    private static final double EXTENDED_RAM_FACTOR = 6.5;
    private static final int GPU = 3;
    private static final String K_80 = "K80";
    public static final String CUSTOM_FAMILY = "custom";

    private final GCPObjectExtractor extractor = new GCPCustomMachineExtractor();

    @Test
    public void testCustomCpuMachineExtraction() {
        final GCPRegion region = new GCPRegion();
        region.setCustomInstanceTypes(Collections.singletonList(GCPCustomInstanceType.withCpu(CPU, RAM)));
        final GCPMachine expectedMachine = GCPMachine.withCpu("custom-1-2048", CUSTOM_FAMILY, CPU, RAM, 0);

        final List<AbstractGCPObject> actualMachines = extractor.extract(region);

        assertThat(actualMachines.size(), is(1));
        final AbstractGCPObject actualObject = actualMachines.get(0);
        assertThat(actualObject, instanceOf(GCPMachine.class));
        assertMachineEquals(expectedMachine, (GCPMachine) actualObject);
    }

    @Test
    public void testCustomGpuMachineExtraction() {
        final GCPRegion region = new GCPRegion();
        region.setCustomInstanceTypes(Collections.singletonList(GCPCustomInstanceType.withGpu(CPU, RAM, GPU, K_80)));
        final GCPMachine expectedMachine = GCPMachine.withGpu("gpu-custom-1-2048-k80-3", CUSTOM_FAMILY, CPU, RAM, 0,
                GPU, K_80);

        final List<AbstractGCPObject> actualMachines = extractor.extract(region);

        assertThat(actualMachines.size(), is(1));
        final AbstractGCPObject actualObject = actualMachines.get(0);
        assertThat(actualObject, instanceOf(GCPMachine.class));
        assertMachineEquals(expectedMachine, (GCPMachine) actualObject);
    }

    @Test
    public void testExtendedCustomMachineExtraction() {
        final GCPRegion region = new GCPRegion();
        region.setCustomInstanceTypes(Collections.singletonList(GCPCustomInstanceType.withCpu(2 * CPU,
                EXTENDED_RAM_FACTOR * 2 + EXTENDED_RAM)));
        final GCPMachine expectedMachine = GCPMachine.withCpu("gpu-custom-1-2048-k80-3", CUSTOM_FAMILY, 2 * CPU,
                EXTENDED_RAM_FACTOR * 2, EXTENDED_RAM);

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
