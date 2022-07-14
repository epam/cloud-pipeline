package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.GeneralBillingMetrics;
import com.epam.pipeline.entity.billing.UserGeneralBilling;
import com.epam.pipeline.utils.CommonUtils;

import java.io.Writer;
import java.util.Collections;
import java.util.stream.Stream;

public class UserBillingWriterTest extends AbstractBillingWriterTest<UserGeneralBilling> {

    public UserBillingWriterTest() {
        super("/billings/billing.user.csv");
    }

    @Override
    public BillingWriter<UserGeneralBilling> getWriter(final Writer writer) {
        return new UserBillingWriter(writer, START_DATE, FINISH_DATE);
    }

    @Override
    public Stream<UserGeneralBilling> billings() {
        return Stream.of(
                UserGeneralBilling.builder()
                        .name(OWNER)
                        .billingCenter(BILLING_CENTER)
                        .periodMetrics(CommonUtils.mergeMaps(
                                Collections.singletonMap(START_YEAR_MONTH,
                                        GeneralBillingMetrics.builder()
                                                .runsNumber(ONE)
                                                .runsDuration(ONE_HOUR)
                                                .runsCost(ONE_DOLLAR)
                                                .storagesCost(ONE_DOLLAR)
                                                .build()),
                                Collections.singletonMap(FINISH_YEAR_MONTH,
                                        GeneralBillingMetrics.builder()
                                                .runsNumber(ONE)
                                                .runsDuration(ONE_HOUR)
                                                .runsCost(ONE_DOLLAR)
                                                .storagesCost(ONE_DOLLAR)
                                                .build())))
                        .totalMetrics(GeneralBillingMetrics.builder()
                                .runsNumber(ONE + ONE)
                                .runsDuration(ONE_HOUR + ONE_HOUR)
                                .runsCost(ONE_DOLLAR + ONE_DOLLAR)
                                .storagesCost(ONE_DOLLAR + ONE_DOLLAR)
                                .build())
                        .build(),
                UserGeneralBilling.builder()
                        .name(OTHER_OWNER)
                        .billingCenter(BILLING_CENTER)
                        .periodMetrics(CommonUtils.mergeMaps(
                                Collections.singletonMap(START_YEAR_MONTH,
                                        GeneralBillingMetrics.builder()
                                                .runsNumber(TEN)
                                                .runsDuration(TEN_HOURS)
                                                .runsCost(TEN_DOLLARS)
                                                .storagesCost(TEN_DOLLARS)
                                                .build()),
                                Collections.singletonMap(FINISH_YEAR_MONTH,
                                        GeneralBillingMetrics.builder()
                                                .runsNumber(TEN)
                                                .runsDuration(TEN_HOURS)
                                                .runsCost(TEN_DOLLARS)
                                                .storagesCost(TEN_DOLLARS)
                                                .build())))
                        .totalMetrics(GeneralBillingMetrics.builder()
                                .runsNumber(TEN + TEN)
                                .runsDuration(TEN_HOURS + TEN_HOURS)
                                .runsCost(TEN_DOLLARS + TEN_DOLLARS)
                                .storagesCost(TEN_DOLLARS + TEN_DOLLARS)
                                .build())
                        .build(),
                UserGeneralBilling.builder()
                        .name(GRAND_TOTAL)
                        .periodMetrics(CommonUtils.mergeMaps(
                                Collections.singletonMap(START_YEAR_MONTH,
                                        GeneralBillingMetrics.builder()
                                                .runsNumber(ONE + TEN)
                                                .runsDuration(ONE_HOUR + TEN_HOURS)
                                                .runsCost(ONE_DOLLAR + TEN_DOLLARS)
                                                .storagesCost(ONE_DOLLAR + TEN_DOLLARS)
                                                .build()),
                                Collections.singletonMap(FINISH_YEAR_MONTH,
                                        GeneralBillingMetrics.builder()
                                                .runsNumber(ONE + TEN)
                                                .runsDuration(ONE_HOUR + TEN_HOURS)
                                                .runsCost(ONE_DOLLAR + TEN_DOLLARS)
                                                .storagesCost(ONE_DOLLAR + TEN_DOLLARS)
                                                .build())))
                        .totalMetrics(GeneralBillingMetrics.builder()
                                .runsNumber(ONE + ONE + TEN + TEN)
                                .runsDuration(ONE_HOUR + ONE_HOUR + TEN_HOURS + TEN_HOURS)
                                .runsCost(ONE_DOLLAR + ONE_DOLLAR + TEN_DOLLARS + TEN_DOLLARS)
                                .storagesCost(ONE_DOLLAR + ONE_DOLLAR + TEN_DOLLARS + TEN_DOLLARS)
                                .build())
                        .build());
    }
}
