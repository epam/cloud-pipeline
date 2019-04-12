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

import com.epam.pipeline.dao.cluster.InstanceOfferDao;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.manager.cloud.gcp.extractor.GCPObjectExtractor;
import com.epam.pipeline.manager.cloud.gcp.resource.AbstractGCPObject;
import com.epam.pipeline.manager.cloud.gcp.resource.GCPDisk;
import com.epam.pipeline.manager.cloud.gcp.resource.GCPMachine;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
    private static final long DISK_COST = 1_000L;
    private static final long GIGABYTE = 1_000_000_000;
    private static final String STANDARD_FAMILY = "standard";
    private static final String CUSTOM_FAMILY = "custom";
    private static final String K_80_GPU = "K80";
    private static final String INSTANCE_PRODUCT_FAMILY = "instance";
    private static final String STORAGE_PRODUCT_FAMILY = "storage";
    private static final List<String> SKUS = Arrays.asList("sku-id-1", "sku-id-2");

    private final GCPRegion region = defaultRegion();
    private final GCPMachine cpuMachine = new GCPMachine("n1-standard-1", STANDARD_FAMILY, 1, 4, 0, null);
    private final GCPMachine gpuMachine = new GCPMachine("gpu-custom-2-8192-k80-1", CUSTOM_FAMILY, 2, 8, 3, K_80_GPU);
    private final GCPMachine machineWithoutAssociatedPrices =
            new GCPMachine("n1-familywithoutprices-1", "familywithoutprices", 10, 20, 0, null);
    private final GCPDisk disk = new GCPDisk("SSD", "SSD");
    private final List<AbstractGCPObject> predefinedMachines = Arrays.asList(cpuMachine,
            machineWithoutAssociatedPrices);
    private final List<AbstractGCPObject> customMachines = Collections.singletonList(gpuMachine);
    private final List<AbstractGCPObject> disks = Collections.singletonList(disk);

    private final GCPObjectExtractor predefinedMachinesExtractor = mock(GCPObjectExtractor.class);
    private final GCPObjectExtractor customMachinesExtractor = mock(GCPObjectExtractor.class);
    private final GCPObjectExtractor diskExtractor = mock(GCPObjectExtractor.class);
    private final List<GCPObjectExtractor> extractors = Arrays.asList(predefinedMachinesExtractor,
            customMachinesExtractor, diskExtractor);
    private final GCPResourcePriceLoader priceLoader = mock(GCPResourcePriceLoader.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final InstanceOfferDao instanceOfferDao = mock(InstanceOfferDao.class);
    private final GCPInstancePriceService service = new GCPInstancePriceService(preferenceManager,
            instanceOfferDao, extractors, priceLoader);
    private final GCPResourceRequest cpuOndemandStandardRequest = new GCPResourceRequest(GCPResourceType.CPU,
            GCPBilling.ON_DEMAND, cpuMachine, SKUS);
    private GCPResourceRequest ramOndemandStandardRequest = new GCPResourceRequest(GCPResourceType.RAM,
            GCPBilling.ON_DEMAND, cpuMachine, SKUS);
    private GCPResourceRequest cpuPreemtibleStandard = new GCPResourceRequest(GCPResourceType.CPU,
            GCPBilling.PREEMPTIBLE, cpuMachine, SKUS);
    private GCPResourceRequest ramPreemtibleStandardRequest = new GCPResourceRequest(GCPResourceType.RAM,
            GCPBilling.PREEMPTIBLE, cpuMachine, SKUS);
    private GCPResourceRequest cpuOndemandCustomRequest = new GCPResourceRequest(GCPResourceType.CPU,
            GCPBilling.ON_DEMAND, gpuMachine, SKUS);
    private GCPResourceRequest ramOndemandCustomRequest = new GCPResourceRequest(GCPResourceType.RAM,
            GCPBilling.ON_DEMAND, gpuMachine, SKUS);
    private GCPResourceRequest ramOndemandK80Request = new GCPResourceRequest(GCPResourceType.GPU,
            GCPBilling.ON_DEMAND, gpuMachine, SKUS);
    private GCPResourceRequest diskOndemandRequest = new GCPResourceRequest(GCPResourceType.DISK,
            GCPBilling.ON_DEMAND, disk, SKUS);
    private final List<GCPResourceRequest> extractor1Requests = Arrays.asList(cpuOndemandStandardRequest,
            ramOndemandStandardRequest);
    private final List<GCPResourceRequest> extractor2Requests = Arrays.asList(cpuOndemandCustomRequest,
            ramOndemandCustomRequest, ramOndemandK80Request);
    private final List<GCPResourceRequest> diskRequests = Collections.singletonList(diskOndemandRequest);

    @Before
    public void setUp() {
        when(predefinedMachinesExtractor.extract(any())).thenReturn(predefinedMachines);
        when(customMachinesExtractor.extract(any())).thenReturn(customMachines);
        when(diskExtractor.extract(any())).thenReturn(disks);
        final HashMap<String, List<String>> prefixes = new HashMap<>();
        prefixes.put("cpu_ondemand_standard", SKUS);
        prefixes.put("ram_ondemand_standard", SKUS);
        prefixes.put("cpu_preemtible_standard", SKUS);
        prefixes.put("ram_preemtible_standard", SKUS);
        prefixes.put("cpu_ondemand_custom", SKUS);
        prefixes.put("ram_ondemand_custom", SKUS);
        prefixes.put("gpu_ondemand_k80", SKUS);
        prefixes.put("disk_ondemand", SKUS);
        when(preferenceManager.getPreference(eq(SystemPreferences.GCP_SKU_MAPPING))).thenReturn(prefixes);
        when(priceLoader.load(any(), any())).thenReturn(new HashSet<>(Arrays.asList(
                new GCPResourcePrice(cpuOndemandStandardRequest, STANDARD_CPU_COST),
                new GCPResourcePrice(ramOndemandStandardRequest, STANDARD_RAM_COST),
                new GCPResourcePrice(cpuPreemtibleStandard, STANDARD_CPU_PREEMTIBLE_COST),
                new GCPResourcePrice(ramPreemtibleStandardRequest, STANDARD_RAM_PREEMTIBLE_COST),
                new GCPResourcePrice(cpuOndemandCustomRequest, CUSTOM_CPU_COST),
                new GCPResourcePrice(ramOndemandCustomRequest, CUSTOM_RAM_COST),
                new GCPResourcePrice(ramOndemandK80Request, K80_GPU_COST),
                new GCPResourcePrice(diskOndemandRequest, DISK_COST))));
    }

    @Test
    public void refreshShouldUseMachinesFromAllExtractorsForPriceLoading() {
        service.refreshPriceListForRegion(region);

        verify(predefinedMachinesExtractor).extract(eq(region));
        verify(customMachinesExtractor).extract(eq(region));
        verify(diskExtractor).extract(eq(region));
        verify(priceLoader).load(eq(region), eq(mergeLists(extractor1Requests, extractor2Requests, diskRequests)));
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
        assertTrue(offer.getProductFamily().toLowerCase().contains(INSTANCE_PRODUCT_FAMILY));
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
        assertTrue(offer.getProductFamily().toLowerCase().contains(INSTANCE_PRODUCT_FAMILY));
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
        assertTrue(offer.getProductFamily().toLowerCase().contains(INSTANCE_PRODUCT_FAMILY));
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
        assertTrue(offer.getProductFamily().toLowerCase().contains(INSTANCE_PRODUCT_FAMILY));
        final double expectedNanos = STANDARD_CPU_PREEMTIBLE_COST * cpuMachine.getCpu()
                + STANDARD_RAM_PREEMTIBLE_COST * cpuMachine.getRam();
        final double expectedPrice = expectedNanos / GIGABYTE;
        assertEquals(expectedPrice, offer.getPricePerUnit(), DELTA);
    }

    @Test
    public void refreshShouldReturnDiskInstanceOffers() {
        final List<InstanceOffer> offers = service.refreshPriceListForRegion(region);

        final Optional<InstanceOffer> optionalOffer = offers.stream()
                .filter(it -> it.getInstanceType().equals(disk.getName()))
                .findFirst();

        assertTrue(optionalOffer.isPresent());
        final InstanceOffer offer = optionalOffer.get();
        assertThat(offer.getVCPU(), is(0));
        assertThat(offer.getMemory(), is(0.0));
        assertThat(offer.getGpu(), is(0));
        assertTrue(offer.getProductFamily().toLowerCase().contains(STORAGE_PRODUCT_FAMILY));
        final double expectedPrice = (double) DISK_COST / GIGABYTE;
        assertEquals(expectedPrice, offer.getPricePerUnit(), DELTA);
    }

    private <T> ArrayList<T> mergeLists(final List<T> list1, final List<T> list2) {
        final ArrayList<T> mergedLists = new ArrayList<>(list1);
        mergedLists.addAll(list2);
        return mergedLists;
    }

    private <T> ArrayList<T> mergeLists(final List<T> list1, final List<T> list2, final List<T> list3) {
        final ArrayList<T> mergedLists = mergeLists(list1, list2);
        mergedLists.addAll(list3);
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
