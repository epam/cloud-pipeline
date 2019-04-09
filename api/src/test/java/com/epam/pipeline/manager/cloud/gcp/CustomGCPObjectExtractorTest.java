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
import com.epam.pipeline.manager.cloud.gcp.resource.GCPObject;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class CustomGCPObjectExtractorTest {

    private GCPObjectExtractor extractor = new GCPCustomMachineExtractor();

    @Test
    public void testCustomCpuMachineExtraction() {
        final GCPRegion region = new GCPRegion();
        region.setCustomInstanceTypes(Collections.singletonList(GCPCustomInstanceType.withCpu(1, 2)));
        final List<GCPMachine> expectedMachines = Collections.singletonList(
                new GCPMachine("custom-1-2048", "custom", 1, 2, 0, null));

        final List<GCPObject> actualMachines = extractor.extract(region);

        assertThat(actualMachines.size(), is(1));
        assertThat(actualMachines, is(expectedMachines));
    }

    @Test
    public void testCustomGpuMachineExtraction() {
        final GCPRegion region = new GCPRegion();
        region.setCustomInstanceTypes(Collections.singletonList(GCPCustomInstanceType.withGpu(1, 2, 3, "K80")));
        final List<GCPMachine> expectedMachines = Collections.singletonList(
                new GCPMachine("gpu-custom-1-2048-k80-3", "custom", 1, 2, 3, "K80"));

        final List<GCPObject> actualMachines = extractor.extract(region);

        assertThat(actualMachines.size(), is(1));
        assertThat(actualMachines, is(expectedMachines));
    }
}
