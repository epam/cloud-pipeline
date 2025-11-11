package com.epam.pipeline.manager.cloud.aws;

import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.test.creator.region.RegionCreatorUtils;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@SuppressWarnings("checkstyle:magicnumber")
public class AWSPriceListReaderTest {

    private static final double COMPARISON_ERROR = 0.1;
    private final AwsRegion region = RegionCreatorUtils.getDefaultAwsRegion();

    @Test
    public void readShouldLoadAndParsePriceList() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("prices/aws.price.list.csv");
             InputStreamReader isr = new InputStreamReader(Objects.requireNonNull(is));
             BufferedReader br = new BufferedReader(isr);
             AWSPriceListReader plr = new AWSPriceListReader(br, region, Collections.emptySet())) {
            final List<InstanceOffer> offers = plr.read();
            assertThat(offers.size(), is(2));
            assertInstance(offers.get(0), getCPUInstanceOffer(region));
            assertInstance(offers.get(1), getGPUInstanceOffer(region));
        }
    }

    private static InstanceOffer getCPUInstanceOffer(final AwsRegion region) {
        final InstanceOffer offer = new InstanceOffer();
        offer.setSku("5G4TA8Z4MUKE6MJB");
        offer.setTermType("OnDemand");
        offer.setUnit("Hrs");
        offer.setPricePerUnit(0.192);
        offer.setCurrency("USD");
        offer.setInstanceType("m5.xlarge");
        offer.setTenancy("Shared");
        offer.setOperatingSystem("Linux");
        offer.setProductFamily("Compute Instance");
        offer.setVCPU(4);
        offer.setMemory(16.0);
        offer.setMemoryUnit("GiB");
        offer.setInstanceFamily("General purpose");
        offer.setGpu(0);
        offer.setGpuDevice(null);
        offer.setRegionId(region.getId());
        offer.setCloudProvider(region.getProvider());
        return offer;
    }

    private static InstanceOffer getGPUInstanceOffer(final AwsRegion region) {
        final InstanceOffer offer = new InstanceOffer();
        offer.setSku("3P84N5G49RGRCA2G");
        offer.setTermType("OnDemand");
        offer.setUnit("Hrs");
        offer.setPricePerUnit(6.2392);
        offer.setCurrency("USD");
        offer.setInstanceType("g5.12xlarge");
        offer.setTenancy("Dedicated");
        offer.setOperatingSystem("Linux");
        offer.setProductFamily("Compute Instance");
        offer.setVCPU(48);
        offer.setMemory(192.0);
        offer.setMemoryUnit("GiB");
        offer.setInstanceFamily("GPU instance");
        offer.setGpu(4);
        offer.setGpuDevice(null);
        offer.setRegionId(region.getId());
        offer.setCloudProvider(region.getProvider());
        return offer;
    }

    private static void assertInstance(final InstanceOffer actual, final InstanceOffer expected) {
        assertThat(actual.getSku(), is(expected.getSku()));
        assertThat(actual.getTermType(), is(expected.getTermType()));
        assertThat(actual.getUnit(), is(expected.getUnit()));
        assertThat(actual.getPricePerUnit(), is(closeTo(expected.getPricePerUnit(), COMPARISON_ERROR)));
        assertThat(actual.getCurrency(), is(expected.getCurrency()));
        assertThat(actual.getInstanceType(), is(expected.getInstanceType()));
        assertThat(actual.getTenancy(), is(expected.getTenancy()));
        assertThat(actual.getOperatingSystem(), is(expected.getOperatingSystem()));
        assertThat(actual.getProductFamily(), is(expected.getProductFamily()));
        assertThat(actual.getVCPU(), is(expected.getVCPU()));
        assertThat(actual.getMemory(), is(closeTo(expected.getMemory(), COMPARISON_ERROR)));
        assertThat(actual.getMemoryUnit(), is(expected.getMemoryUnit()));
        assertThat(actual.getInstanceFamily(), is(expected.getInstanceFamily()));
        assertThat(actual.getGpu(), is(expected.getGpu()));
        assertThat(actual.getGpuDevice(), is(expected.getGpuDevice()));
        assertThat(actual.getRegionId(), is(expected.getRegionId()));
        assertThat(actual.getCloudProvider(), is(expected.getCloudProvider()));
    }
}
