package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.StorageBilling;
import com.epam.pipeline.entity.billing.StorageBillingMetrics;
import com.epam.pipeline.utils.CommonUtils;

import java.io.Writer;
import java.util.Collections;
import java.util.stream.Stream;

public class StorageBillingWriterTest extends AbstractBillingWriterTest<StorageBilling> {

    public StorageBillingWriterTest() {
        super("/billings/billing.storage.csv");
    }

    @Override
    public BillingWriter<StorageBilling> getWriter(final Writer writer) {
        return new StorageBillingWriter(writer, START_DATE, FINISH_DATE, null);
    }

    @Override
    public Stream<StorageBilling> billings() {
        return Stream.of(
                StorageBilling.builder()
                        .name(STORAGE)
                        .owner(OWNER)
                        .billingCenter(BILLING_CENTER)
                        .type(TYPE)
                        .region(REGION)
                        .provider(PROVIDER)
                        .created(START_DATE_TIME)
                        .periodMetrics(CommonUtils.mergeMaps(
                                Collections.singletonMap(START_YEAR_MONTH,
                                        StorageBillingMetrics.builder()
                                                .cost(ONE_DOLLAR)
                                                .averageVolume(ONE_GB)
                                                .currentVolume(ONE_GB)
                                                .build()),
                                Collections.singletonMap(FINISH_YEAR_MONTH,
                                        StorageBillingMetrics.builder()
                                                .cost(TEN_DOLLARS)
                                                .averageVolume(TEN_GBS)
                                                .currentVolume(TEN_GBS)
                                                .build())))
                        .totalMetrics(StorageBillingMetrics.builder()
                                .cost(ONE_DOLLAR + TEN_DOLLARS)
                                .averageVolume(ONE_GB + TEN_GBS)
                                .currentVolume(ONE_GB + TEN_GBS)
                                .build())
                        .build());
    }
}
