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
import com.epam.pipeline.entity.cluster.GpuDevice;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.manager.cloud.gcp.extractor.GCPObjectExtractor;
import com.epam.pipeline.manager.cloud.gcp.resource.AbstractGCPObject;
import com.epam.pipeline.manager.cloud.gcp.resource.GCPDisk;
import com.epam.pipeline.manager.cloud.gcp.resource.GCPMachine;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.CommonUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"PMD.TooManyStaticImports", "unchecked"})
public class GCPInstancePriceServiceTest {

    private static final double DELTA = 0.01;
    private static final long CUSTOM_CPU_COST = 500_000_000L;
    private static final long CUSTOM_RAM_COST = 50_000_000L;
    private static final long CUSTOM_EXTENDED_RAM_COST = 100_000_000L;
    private static final long STANDARD_CPU_COST = 1_000_000_000L;
    private static final long STANDARD_RAM_COST = 100_000_000L;
    private static final long HIGHGPU_CPU_COST = 1_000_000_000L;
    private static final long HIGHGPU_RAM_COST = 100_000_000L;
    private static final long STANDARD_CPU_PREEMTIBLE_COST = 100_000L;
    private static final long STANDARD_RAM_PREEMTIBLE_COST = 1_000L;
    private static final long K80_GPU_COST = 50_000_000L;
    private static final long A100_GPU_COST = 50_000_000L;
    private static final long DISK_COST = 1_000L;
    private static final long GIGABYTE = 1_000_000_000;
    private static final String STANDARD_FAMILY = "standard";
    private static final String HIGHGPU_FAMILY = "highgpu";
    private static final String CUSTOM_FAMILY = "custom";
    private static final String INSTANCE_PRODUCT_FAMILY = "instance";
    private static final String STORAGE_PRODUCT_FAMILY = "storage";
    private static final String NVIDIA = "NVIDIA";
    private static final GpuDevice NVIDIA_A100 = GpuDevice.of("A100", NVIDIA);
    private static final int NVIDIA_A100_CORES = 6912;
    private static final Map<String, Integer> GPU_CORES_MAPPING = CommonUtils.toMap(
            Pair.of(NVIDIA_A100.getManufacturerAndName(), NVIDIA_A100_CORES));
    private static final GpuDevice NVIDIA_K80 = GpuDevice.of("K80", NVIDIA);
    private static final GCPResourceMapping MAPPING = new GCPResourceMapping("prefix", "group");

    private final GCPRegion region = defaultRegion();
    private final GCPMachine cpuMachine = GCPMachine.withCpu("n1-standard-1", STANDARD_FAMILY, 1, 4, 0);
    private final GCPMachine gpuMachine = GCPMachine.withGpu("a2-highgpu-2g", HIGHGPU_FAMILY, 24, 170, 0, 2,
            NVIDIA_A100);
    private final GCPMachine customCpuMachine = GCPMachine.withCpu("custom-2-15360", CUSTOM_FAMILY, 2, 13, 2);
    private final GCPMachine customGpuMachine = GCPMachine.withGpu("gpu-custom-2-8192-k80-1", CUSTOM_FAMILY, 2, 8, 0, 3,
            NVIDIA_K80);
    private final GCPMachine cpuMachineWithNoPrices = GCPMachine.withCpu("n1-familywithoutprices-1",
            "familywithoutprices", 10, 20, 0);
    private final GCPDisk disk = new GCPDisk("SSD", "SSD");
    private final List<AbstractGCPObject> predefinedMachines = Arrays.asList(cpuMachine, gpuMachine,
            cpuMachineWithNoPrices);
    private final List<AbstractGCPObject> customMachines = Arrays.asList(customGpuMachine, customCpuMachine);
    private final List<AbstractGCPObject> disks = Collections.singletonList(disk);

    private final GCPObjectExtractor predefinedMachinesExtractor = mock(GCPObjectExtractor.class);
    private final GCPObjectExtractor customMachinesExtractor = mock(GCPObjectExtractor.class);
    private final GCPObjectExtractor diskExtractor = mock(GCPObjectExtractor.class);
    private final GCPResourcePriceLoader priceLoader = mock(GCPResourcePriceLoader.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final InstanceOfferDao instanceOfferDao = mock(InstanceOfferDao.class);
    private final GCPInstancePriceService service = getService(predefinedMachinesExtractor, customMachinesExtractor,
            diskExtractor);

    private GCPInstancePriceService getService(final GCPObjectExtractor... extractors) {
        return new GCPInstancePriceService(preferenceManager, instanceOfferDao, Arrays.asList(extractors), priceLoader);
    }

    @Before
    public void setUp() {
        when(predefinedMachinesExtractor.extract(any())).thenReturn(predefinedMachines);
        when(customMachinesExtractor.extract(any())).thenReturn(customMachines);
        when(diskExtractor.extract(any())).thenReturn(disks);
        when(preferenceManager.getPreference(eq(SystemPreferences.GCP_SKU_MAPPING)))
                .thenReturn(mappings(
                        "cpu_ondemand_standard", "cpu_preemptible_standard",
                        "ram_ondemand_standard", "ram_preemptible_standard",
                        "cpu_ondemand_custom", "ram_ondemand_custom", "extendedram_ondemand_custom",
                        "gpu_ondemand_a100", "gpu_preemptible_a100",
                        "gpu_ondemand_k80", "gpu_preemptible_k80",
                        "disk_ondemand", "disk_preemptible"));
        when(preferenceManager.getPreference(eq(SystemPreferences.CLUSTER_INSTANCE_GPU_CORES_MAPPING)))
                .thenReturn(GPU_CORES_MAPPING);
    }

    @Test
    public void refreshShouldGenerateRequestsForExtractedObject() {
        final GCPObjectExtractor extractor = mock(GCPObjectExtractor.class);
        final GCPMachine machine = GCPMachine.withCpu("name", STANDARD_FAMILY, 2, 8.0, 0);
        when(extractor.extract(any())).thenReturn(Collections.singletonList(machine));
        final GCPInstancePriceService service = getService(extractor);
        final List<GCPResourceRequest> expectedRequests = Arrays.asList(
                new GCPResourceRequest(GCPResourceType.CPU, GCPBilling.ON_DEMAND, machine, MAPPING),
                new GCPResourceRequest(GCPResourceType.RAM, GCPBilling.ON_DEMAND, machine, MAPPING),
                new GCPResourceRequest(GCPResourceType.CPU, GCPBilling.PREEMPTIBLE, machine, MAPPING),
                new GCPResourceRequest(GCPResourceType.RAM, GCPBilling.PREEMPTIBLE, machine, MAPPING)
        );

        service.refreshPriceListForRegion(region);

        final ArgumentCaptor<List<GCPResourceRequest>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(priceLoader).load(eq(region), captor.capture());
        final List<GCPResourceRequest> actualRequests = captor.getValue();
        assertThat(actualRequests.size(), is(expectedRequests.size()));
        assertTrue(expectedRequests.stream()
                .map(request -> actualRequests.stream().filter(request::equals).findFirst())
                .allMatch(Optional::isPresent));
    }

    @Test
    public void refreshShouldGenerateRequestsForAllExtractedObjects() {
        final GCPObjectExtractor extractor = mock(GCPObjectExtractor.class);
        final GCPMachine machine1 = GCPMachine.withCpu("name", STANDARD_FAMILY, 2, 8.0, 0);
        final GCPMachine machine2 = GCPMachine.withCpu("another-name", STANDARD_FAMILY, 3, 10.0, 0);
        when(extractor.extract(any())).thenReturn(Arrays.asList(machine1, machine2));
        final GCPInstancePriceService service = getService(extractor);
        final List<GCPResourceRequest> expectedRequests = Arrays.asList(
                new GCPResourceRequest(GCPResourceType.CPU, GCPBilling.ON_DEMAND, machine1, MAPPING),
                new GCPResourceRequest(GCPResourceType.RAM, GCPBilling.ON_DEMAND, machine1, MAPPING),
                new GCPResourceRequest(GCPResourceType.CPU, GCPBilling.PREEMPTIBLE, machine1, MAPPING),
                new GCPResourceRequest(GCPResourceType.RAM, GCPBilling.PREEMPTIBLE, machine1, MAPPING),
                new GCPResourceRequest(GCPResourceType.CPU, GCPBilling.ON_DEMAND, machine2, MAPPING),
                new GCPResourceRequest(GCPResourceType.RAM, GCPBilling.ON_DEMAND, machine2, MAPPING),
                new GCPResourceRequest(GCPResourceType.CPU, GCPBilling.PREEMPTIBLE, machine1, MAPPING),
                new GCPResourceRequest(GCPResourceType.RAM, GCPBilling.PREEMPTIBLE, machine1, MAPPING)
        );

        service.refreshPriceListForRegion(region);

        final ArgumentCaptor<List<GCPResourceRequest>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(priceLoader).load(eq(region), captor.capture());
        final List<GCPResourceRequest> actualRequests = captor.getValue();
        assertThat(actualRequests.size(), is(expectedRequests.size()));
        assertTrue(expectedRequests.stream()
                .map(request -> actualRequests.stream().filter(request::equals).findFirst())
                .allMatch(Optional::isPresent));
    }

    @Test
    public void refreshShouldGenerateRequestsForAllExtractedDisks() {
        final GCPObjectExtractor extractor = mock(GCPObjectExtractor.class);
        final GCPDisk disk = new GCPDisk("some-disk", "SSD");
        when(extractor.extract(any())).thenReturn(Collections.singletonList(disk));
        final GCPInstancePriceService service = getService(extractor);
        final List<GCPResourceRequest> expectedRequests = Arrays.asList(
                new GCPResourceRequest(GCPResourceType.DISK, GCPBilling.ON_DEMAND, disk, MAPPING),
                new GCPResourceRequest(GCPResourceType.DISK, GCPBilling.PREEMPTIBLE, disk, MAPPING)
        );

        service.refreshPriceListForRegion(region);

        final ArgumentCaptor<List<GCPResourceRequest>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(priceLoader).load(eq(region), captor.capture());
        final List<GCPResourceRequest> actualRequests = captor.getValue();
        assertThat(actualRequests.size(), is(expectedRequests.size()));
        assertTrue(expectedRequests.stream()
                .map(request -> actualRequests.stream().filter(request::equals).findFirst())
                .allMatch(Optional::isPresent));
    }

    @Test
    public void refreshShouldExtractMachinesFromAllExtractors() {
        when(priceLoader.load(any(), any())).thenReturn(Collections.emptySet());
        service.refreshPriceListForRegion(region);

        verify(predefinedMachinesExtractor).extract(eq(region));
        verify(customMachinesExtractor).extract(eq(region));
        verify(diskExtractor).extract(eq(region));
    }

    @Test
    public void refreshShouldSetEmptyPriceForMachinesWithoutAssociatedPrices() {
        when(priceLoader.load(any(), any())).thenReturn(Collections.emptySet());
        final List<InstanceOffer> offers = service.refreshPriceListForRegion(region);

        final Optional<InstanceOffer> optionalOffer = offers.stream()
                .filter(it -> it.getInstanceType().equals(cpuMachineWithNoPrices.getName()))
                .findFirst();

        assertTrue(optionalOffer.isPresent());
        final InstanceOffer offer = optionalOffer.get();
        assertThat(offer.getVCPU(), is(cpuMachineWithNoPrices.getCpu()));
        assertThat(offer.getMemory(), is(cpuMachineWithNoPrices.getRam()));
        assertThat(offer.getGpu(), is(cpuMachineWithNoPrices.getGpu()));
        assertTrue(offer.getProductFamily().toLowerCase().contains(INSTANCE_PRODUCT_FAMILY));
        assertEquals(0.0, offer.getPricePerUnit(), 0.0);
    }

    @Test
    public void refreshShouldReturnCpuMachineInstanceOffers() {
        when(priceLoader.load(any(), any())).thenReturn(new HashSet<>(Arrays.asList(
                price(GCPResourceType.CPU, GCPBilling.ON_DEMAND, cpuMachine, STANDARD_CPU_COST),
                price(GCPResourceType.RAM, GCPBilling.ON_DEMAND, cpuMachine, STANDARD_RAM_COST)
        )));
        final List<InstanceOffer> offers = service.refreshPriceListForRegion(region);

        final Optional<InstanceOffer> optionalOffer = offers.stream()
                .filter(it -> it.getInstanceType().equals(cpuMachine.getName()))
                .filter(it -> it.getTermType().equals(CloudInstancePriceService.TermType.ON_DEMAND.getName()))
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
    public void refreshShouldReturnCompleteGpuMachineInstanceOffers() {
        when(priceLoader.load(any(), any())).thenReturn(new HashSet<>(Arrays.asList(
                price(GCPResourceType.CPU, GCPBilling.ON_DEMAND, gpuMachine, HIGHGPU_CPU_COST),
                price(GCPResourceType.RAM, GCPBilling.ON_DEMAND, gpuMachine, HIGHGPU_RAM_COST),
                price(GCPResourceType.GPU, GCPBilling.ON_DEMAND, gpuMachine, A100_GPU_COST)
        )));
        final List<InstanceOffer> offers = service.refreshPriceListForRegion(region);

        final Optional<InstanceOffer> optionalOffer = offers.stream()
                .filter(it -> it.getInstanceType().equals(gpuMachine.getName()))
                .findFirst();

        assertTrue(optionalOffer.isPresent());
        final InstanceOffer offer = optionalOffer.get();
        assertThat(offer.getVCPU(), is(gpuMachine.getCpu()));
        assertThat(offer.getMemory(), is(gpuMachine.getRam()));
        assertThat(offer.getGpu(), is(gpuMachine.getGpu()));
        assertThat(offer.getGpuDevice().getName(), is(NVIDIA_A100.getName()));
        assertThat(offer.getGpuDevice().getManufacturer(), is(NVIDIA_A100.getManufacturer()));
        assertThat(offer.getGpuDevice().getCores(), is(NVIDIA_A100_CORES));
        assertTrue(offer.getProductFamily().toLowerCase().contains(INSTANCE_PRODUCT_FAMILY));
        final double expectedNanos = HIGHGPU_CPU_COST * gpuMachine.getCpu()
                + HIGHGPU_RAM_COST * gpuMachine.getRam()
                + A100_GPU_COST * gpuMachine.getGpu();
        final double expectedPrice = expectedNanos / GIGABYTE;
        assertEquals(expectedPrice, offer.getPricePerUnit(), DELTA);
    }

    @Test
    public void refreshShouldReturnPartialGpuMachineInstanceOffers() {
        when(priceLoader.load(any(), any())).thenReturn(new HashSet<>(Arrays.asList(
                price(GCPResourceType.CPU, GCPBilling.ON_DEMAND, customGpuMachine, CUSTOM_CPU_COST),
                price(GCPResourceType.RAM, GCPBilling.ON_DEMAND, customGpuMachine, CUSTOM_RAM_COST),
                price(GCPResourceType.GPU, GCPBilling.ON_DEMAND, customGpuMachine, K80_GPU_COST)
        )));
        final List<InstanceOffer> offers = service.refreshPriceListForRegion(region);

        final Optional<InstanceOffer> optionalOffer = offers.stream()
                .filter(it -> it.getInstanceType().equals(customGpuMachine.getName()))
                .findFirst();

        assertTrue(optionalOffer.isPresent());
        final InstanceOffer offer = optionalOffer.get();
        assertThat(offer.getVCPU(), is(customGpuMachine.getCpu()));
        assertThat(offer.getMemory(), is(customGpuMachine.getRam()));
        assertThat(offer.getGpu(), is(customGpuMachine.getGpu()));
        assertThat(offer.getGpuDevice().getName(), is(NVIDIA_K80.getName()));
        assertThat(offer.getGpuDevice().getManufacturer(), is(NVIDIA_K80.getManufacturer()));
        assertThat(offer.getGpuDevice().getCores(), nullValue());
        assertTrue(offer.getProductFamily().toLowerCase().contains(INSTANCE_PRODUCT_FAMILY));
        final double expectedNanos = CUSTOM_CPU_COST * customGpuMachine.getCpu()
                + CUSTOM_RAM_COST * customGpuMachine.getRam()
                + K80_GPU_COST * customGpuMachine.getGpu();
        final double expectedPrice = expectedNanos / GIGABYTE;
        assertEquals(expectedPrice, offer.getPricePerUnit(), DELTA);
    }

    @Test
    public void refreshShouldReturnPreemptibleMachineInstanceOffers() {
        when(priceLoader.load(any(), any())).thenReturn(new HashSet<>(Arrays.asList(
                price(GCPResourceType.CPU, GCPBilling.PREEMPTIBLE, cpuMachine, STANDARD_CPU_PREEMTIBLE_COST),
                price(GCPResourceType.RAM, GCPBilling.PREEMPTIBLE, cpuMachine, STANDARD_RAM_PREEMTIBLE_COST)
        )));

        final List<InstanceOffer> offers = service.refreshPriceListForRegion(region);

        final Optional<InstanceOffer> optionalOffer = offers.stream()
                .filter(it -> it.getInstanceType().equals(cpuMachine.getName()))
                .filter(it -> it.getTermType().equals(CloudInstancePriceService.TermType.ON_DEMAND.getName()))
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
    public void refreshShouldReturnExtendedCustomMachineInstanceOffers() {
        when(priceLoader.load(any(), any())).thenReturn(new HashSet<>(Arrays.asList(
                price(GCPResourceType.CPU, GCPBilling.ON_DEMAND, customCpuMachine, CUSTOM_CPU_COST),
                price(GCPResourceType.RAM, GCPBilling.ON_DEMAND, customCpuMachine, CUSTOM_RAM_COST),
                price(GCPResourceType.EXTENDED_RAM, GCPBilling.ON_DEMAND, customCpuMachine, CUSTOM_EXTENDED_RAM_COST)
        )));
        final List<InstanceOffer> offers = service.refreshPriceListForRegion(region);

        final Optional<InstanceOffer> optionalOffer = offers.stream()
                .filter(it -> it.getInstanceType().equals(customCpuMachine.getName()))
                .filter(it -> it.getTermType().equals(CloudInstancePriceService.TermType.ON_DEMAND.getName()))
                .findFirst();

        assertTrue(optionalOffer.isPresent());
        final InstanceOffer offer = optionalOffer.get();
        assertThat(offer.getVCPU(), is(customCpuMachine.getCpu()));
        assertThat(offer.getMemory(), is(customCpuMachine.getRam()));
        assertThat(offer.getGpu(), is(customCpuMachine.getGpu()));
        assertTrue(offer.getProductFamily().toLowerCase().contains(INSTANCE_PRODUCT_FAMILY));
        final double expectedNanos = CUSTOM_CPU_COST * customCpuMachine.getCpu()
                + CUSTOM_RAM_COST * customCpuMachine.getRam()
                + CUSTOM_EXTENDED_RAM_COST * customCpuMachine.getExtendedRam();
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

    private Map<String, GCPResourceMapping> mappings(final String... keys) {
        return Arrays.stream(keys).collect(Collectors.toMap(Function.identity(), key -> MAPPING));
    }

    private GCPResourcePrice price(final GCPResourceType type,
                                   final GCPBilling billing,
                                   final AbstractGCPObject object,
                                   final long cost) {
        return new GCPResourcePrice(new GCPResourceRequest(type, billing, object, MAPPING), cost);
    }

    private static GCPRegion defaultRegion() {
        final GCPRegion region = new GCPRegion();
        region.setId(1L);
        region.setProject("project");
        region.setRegionCode("us-east1-b");
        return region;
    }
}
