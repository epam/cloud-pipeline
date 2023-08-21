package com.epam.pipeline.manager.cloud.aws;

import com.epam.pipeline.entity.cluster.GpuDevice;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.manager.cloud.InstanceOfferReader;
import com.epam.pipeline.manager.cloud.StaticInstanceOfferReader;
import com.epam.pipeline.test.creator.region.RegionCreatorUtils;
import com.epam.pipeline.utils.CommonUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class AWSInstanceTypeReaderTest {

    private static final String G5_12XLARGE = "g5.12xlarge";
    private static final String M5_XLARGE = "m5.xlarge";
    private static final String P3_2XLARGE = "p3.2xlarge";
    private static final String G4AD_XLARGE = "g4ad.xlarge";
    private static final GpuDevice NVIDIA_A10G = GpuDevice.of("A10G", "NVIDIA");
    private static final int NVIDIA_A10G_CORES = 9216;
    private static final GpuDevice AMD_RADEON_PRO_V520 = GpuDevice.of("RADEON PRO V520", "AMD");
    private static final Map<String, GpuDevice> INSTANCE_GPU_MAPPING = CommonUtils.toMap(
            Pair.of(G5_12XLARGE, NVIDIA_A10G),
            Pair.of(G4AD_XLARGE, AMD_RADEON_PRO_V520));
    private static final Map<String, Integer> GPU_CORES_MAPPING = CommonUtils.toMap(
            Pair.of(NVIDIA_A10G.getManufacturerAndName(), NVIDIA_A10G_CORES));
    private static final List<InstanceOffer> INITIAL_OFFERS = Arrays.asList(
            offer(M5_XLARGE, 0, null),
            offer(G5_12XLARGE, 4, null),
            offer(P3_2XLARGE, 1, null),
            offer(G4AD_XLARGE, 1, null));
    private static final List<InstanceOffer> EXPECTED_OFFERS = Arrays.asList(
            offer(M5_XLARGE, 0, null),
            offer(G5_12XLARGE, 4, NVIDIA_A10G.toBuilder().cores(NVIDIA_A10G_CORES).build()),
            offer(P3_2XLARGE, 1, null),
            offer(G4AD_XLARGE, 1, AMD_RADEON_PRO_V520));

    private final AwsRegion region = RegionCreatorUtils.getDefaultAwsRegion();

    @Test
    public void readShouldCollectInstanceTypeGpuDevices() throws IOException {
        try (InstanceOfferReader ior = new StaticInstanceOfferReader(INITIAL_OFFERS);
             AWSInstanceTypeReader itr = new AWSInstanceTypeReader(ior, region, (a, b) -> INSTANCE_GPU_MAPPING,
                     GPU_CORES_MAPPING)) {
            final List<InstanceOffer> actualOffers = itr.read();
            assertThat(actualOffers.size(), is(EXPECTED_OFFERS.size()));
            for (int i = 0; i < actualOffers.size(); i++) {
                assertInstance(actualOffers.get(i), EXPECTED_OFFERS.get(i));
            }
        }
    }

    private static InstanceOffer offer(final String instanceType, final int gpu, final GpuDevice gpuDevice) {
        final InstanceOffer offer = new InstanceOffer();
        offer.setInstanceType(instanceType);
        offer.setGpu(gpu);
        offer.setGpuDevice(gpuDevice);
        offer.setProductFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY);
        return offer;
    }

    private static void assertInstance(final InstanceOffer actual, final InstanceOffer expected) {
        assertThat(actual.getInstanceType(), is(expected.getInstanceType()));
        assertThat(actual.getGpu(), is(expected.getGpu()));
        assertThat(actual.getGpuDevice(), is(expected.getGpuDevice()));
    }
}
