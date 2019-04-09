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

import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.TooManyStaticImports")
public class GCPInstancePriceServiceTest {

    private static final double DELTA = 0.01;
    private static final long CUSTOM_CPU_COST = 500_000_000L;
    private static final long CUSTOM_RAM_COST = 50_000_000L;
    private static final long STANDARD_CPU_COST = 1_000_000_000L;
    private static final long STANDARD_RAM_COST = 100_000_000L;
    private static final long STANDARD_CPU_PREEMTIBLE_COST = 100_000L;
    private static final long STANDARD_RAM_PREEMTIBLE_COST = 1_000L;
    private static final long K80_GPU_COST = 50_000_000L;
    private static final long GIGABYTE = 1_000_000_000;
    private static final String STANDARD_FAMILY = "standard";
    private static final String CUSTOM_FAMILY = "custom";
    private static final String K_80_GPU = "K80";

    private final GCPRegion region = defaultRegion();
    private final GCPMachine cpuMachine = GCPMachine.withCpu("n1-standard-1", STANDARD_FAMILY, 1, 4);
    private final GCPMachine gpuMachine = GCPMachine.withGpu("gpu-custom-1-3840-k80-1", CUSTOM_FAMILY, 2, 8, 3, "K80");
    private final GCPMachine machineWithoutAssociatedPrices =
            GCPMachine.withCpu("n1-familywithoutprices-1", "familywithoutprices", 10, 20);
    private final List<GCPMachine> extractor1Machines = Arrays.asList(cpuMachine, machineWithoutAssociatedPrices);
    private final List<GCPMachine> extractor2Machines = Collections.singletonList(gpuMachine);

    private final GCPMachineExtractor extractor1 = mock(GCPMachineExtractor.class);
    private final GCPMachineExtractor extractor2 = mock(GCPMachineExtractor.class);
    private final List<GCPMachineExtractor> extractors = Arrays.asList(extractor1, extractor2);
    private final GCPResourcePriceLoader priceLoader = mock(GCPResourcePriceLoader.class);
    private final GCPInstancePriceService service = new GCPInstancePriceService(extractors, priceLoader);

    @Before
    public void setUp() {
        when(extractor1.extract(any())).thenReturn(extractor1Machines);
        when(extractor2.extract(any())).thenReturn(extractor2Machines);

        when(priceLoader.load(any(), any())).thenReturn(new HashSet<>(Arrays.asList(
                new GCPResourcePrice(STANDARD_FAMILY, GCPResourceType.CPU, GCPBilling.ON_DEMAND, STANDARD_CPU_COST),
                new GCPResourcePrice(STANDARD_FAMILY, GCPResourceType.RAM, GCPBilling.ON_DEMAND, STANDARD_RAM_COST),
                new GCPResourcePrice(STANDARD_FAMILY, GCPResourceType.CPU, GCPBilling.PREEMPTIBLE,
                        STANDARD_CPU_PREEMTIBLE_COST),
                new GCPResourcePrice(STANDARD_FAMILY, GCPResourceType.RAM, GCPBilling.PREEMPTIBLE,
                        STANDARD_RAM_PREEMTIBLE_COST),
                new GCPResourcePrice(CUSTOM_FAMILY, GCPResourceType.CPU, GCPBilling.ON_DEMAND, CUSTOM_CPU_COST),
                new GCPResourcePrice(CUSTOM_FAMILY, GCPResourceType.RAM, GCPBilling.ON_DEMAND, CUSTOM_RAM_COST),
                new GCPResourcePrice(K_80_GPU, GCPResourceType.GPU, GCPBilling.ON_DEMAND, K80_GPU_COST)
        )));
    }

    @Test
    public void refreshShouldUseMachinesFromAllExtractorsForPriceLoading() {
        service.refreshPriceListForRegion(region);

        verify(extractor1).extract(eq(region));
        verify(extractor2).extract(eq(region));
        verify(priceLoader).load(eq(region), eq(mergeLists(extractor1Machines, extractor2Machines)));
    }

    @Test
    public void refreshShouldSetEmptyPriceForMachinesWithoutAssociatedPrices() {
        final List<InstanceOffer> offers = service.refreshPriceListForRegion(region);

        final Optional<InstanceOffer> optionalOffer = offers.stream()
                .filter(it -> it.getInstanceType().equals(machineWithoutAssociatedPrices.getName()))
                .findFirst();

        assertTrue(optionalOffer.isPresent());
        final InstanceOffer offer = optionalOffer.get();
        assertThat(offer.getVCPU(), is(machineWithoutAssociatedPrices.getCpu()));
        assertThat(offer.getMemory(), is(machineWithoutAssociatedPrices.getRam()));
        assertThat(offer.getGpu(), is(machineWithoutAssociatedPrices.getGpu()));
        assertEquals(0.0, offer.getPricePerUnit(), 0.0);
    }

    @Test
    public void refreshShouldReturnCpuMachineInstanceOffers() {
        final List<InstanceOffer> offers = service.refreshPriceListForRegion(region);

        final Optional<InstanceOffer> optionalOffer = offers.stream()
                .filter(it -> it.getInstanceType().equals(cpuMachine.getName()))
                .filter(it -> it.getTermType().equals(CloudInstancePriceService.ON_DEMAND_TERM_TYPE))
                .findFirst();

        assertTrue(optionalOffer.isPresent());
        final InstanceOffer offer = optionalOffer.get();
        assertThat(offer.getVCPU(), is(cpuMachine.getCpu()));
        assertThat(offer.getMemory(), is(cpuMachine.getRam()));
        assertThat(offer.getGpu(), is(cpuMachine.getGpu()));
        final double expectedNanos = STANDARD_CPU_COST * cpuMachine.getCpu()
                + STANDARD_RAM_COST * cpuMachine.getRam();
        final double expectedPrice = expectedNanos / GIGABYTE;
        assertEquals(expectedPrice, offer.getPricePerUnit(), DELTA);
    }

    @Test
    public void refreshShouldReturnGpuMachineInstanceOffers() {
        final List<InstanceOffer> offers = service.refreshPriceListForRegion(region);

        final Optional<InstanceOffer> optionalOffer = offers.stream()
                .filter(it -> it.getInstanceType().equals(gpuMachine.getName()))
                .findFirst();

        assertTrue(optionalOffer.isPresent());
        final InstanceOffer offer = optionalOffer.get();
        assertThat(offer.getVCPU(), is(gpuMachine.getCpu()));
        assertThat(offer.getMemory(), is(gpuMachine.getRam()));
        assertThat(offer.getGpu(), is(gpuMachine.getGpu()));
        final double expectedNanos = CUSTOM_CPU_COST * gpuMachine.getCpu()
                + CUSTOM_RAM_COST * gpuMachine.getRam()
                + K80_GPU_COST * gpuMachine.getGpu();
        final double expectedPrice = expectedNanos / GIGABYTE;
        assertEquals(expectedPrice, offer.getPricePerUnit(), DELTA);
    }

    @Test
    public void refreshShouldReturnPreemtibleMachineInstanceOffers() {
        final List<InstanceOffer> offers = service.refreshPriceListForRegion(region);

        final Optional<InstanceOffer> optionalOffer = offers.stream()
                .filter(it -> it.getInstanceType().equals(cpuMachine.getName()))
                .filter(it -> it.getTermType().equals(GCPInstancePriceService.PREEMPTIBLE_TERM_TYPE))
                .findFirst();

        assertTrue(optionalOffer.isPresent());
        final InstanceOffer offer = optionalOffer.get();
        assertThat(offer.getVCPU(), is(cpuMachine.getCpu()));
        assertThat(offer.getMemory(), is(cpuMachine.getRam()));
        assertThat(offer.getGpu(), is(cpuMachine.getGpu()));
        final double expectedNanos = STANDARD_CPU_PREEMTIBLE_COST * cpuMachine.getCpu()
                + STANDARD_RAM_PREEMTIBLE_COST * cpuMachine.getRam();
        final double expectedPrice = expectedNanos / GIGABYTE;
        assertEquals(expectedPrice, offer.getPricePerUnit(), DELTA);
    }

    private <T> ArrayList<T> mergeLists(final List<T> list1, final List<T> list2) {
        final ArrayList<T> mergedLists = new ArrayList<>(list1);
        mergedLists.addAll(list2);
        return mergedLists;
    }

    private static GCPRegion defaultRegion() {
        final GCPRegion region = new GCPRegion();
        region.setId(1L);
        region.setProject("project");
        region.setRegionCode("us-east1-b");
        return region;
    }
}
