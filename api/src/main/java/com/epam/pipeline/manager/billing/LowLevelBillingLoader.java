package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.BillingDiscount;
import org.elasticsearch.client.RestHighLevelClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public interface LowLevelBillingLoader<B> {

    Stream<B> billings(RestHighLevelClient client,
                       String[] indices,
                       LocalDate from,
                       LocalDate to,
                       Map<String, List<String>> filters,
                       BillingDiscount discount,
                       int pageSize);
}
