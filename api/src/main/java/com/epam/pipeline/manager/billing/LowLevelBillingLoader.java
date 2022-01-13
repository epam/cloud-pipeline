package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.BillingDiscount;
import org.elasticsearch.client.RestHighLevelClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public interface LowLevelBillingLoader<B> {

    Stream<B> billings(final RestHighLevelClient client,
                       final String[] indices,
                       final LocalDate from,
                       final LocalDate to,
                       final Map<String, List<String>> filters,
                       final BillingDiscount discount,
                       final int pageSize);
}
