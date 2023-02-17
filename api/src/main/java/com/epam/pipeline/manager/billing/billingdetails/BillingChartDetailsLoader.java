package com.epam.pipeline.manager.billing.billingdetails;

import com.epam.pipeline.controller.vo.billing.CostDetailsRequest;
import com.epam.pipeline.entity.billing.BillingChartDetails;
import com.epam.pipeline.entity.billing.BillingGrouping;
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BillingChartDetailsLoader {

    public static List<AggregationBuilder> buildQuery(final CostDetailsRequest request) {
        if (isStorageBillingDetailsShouldBeLoaded(request)) {
            return StorageBillingDetailsHelper.buildQuery();
        }
        return Collections.emptyList();
    }

    private static boolean isStorageBillingDetailsShouldBeLoaded(final CostDetailsRequest request) {
        if (!request.isEnabled()) {
            return false;
        }
        final BillingGrouping grouping = request.getGrouping();
        if (BillingGrouping.STORAGE.equals(grouping) || BillingGrouping.STORAGE_TYPE.equals(grouping)) {
            return true;
        } else {
            final Map<String, List<String>> filters = MapUtils.emptyIfNull(request.getFilters());
            Boolean onlyStorageRTypeRequestedOrNothing = Optional.ofNullable(filters.get("resource_type"))
                    .map(values -> (values.contains("STORAGE") && values.size() == 1) || values.isEmpty())
                    .orElse(false);
            Boolean onlyObjectStorageSTypeRequested = Optional.ofNullable(filters.get("storage_type"))
                    .map(values -> values.contains("OBJECT_STORAGE") && values.size() == 1).orElse(false);
            return onlyObjectStorageSTypeRequested && onlyStorageRTypeRequestedOrNothing;
        }
    }


    public static BillingChartDetails parseResponse(final CostDetailsRequest request,
                                                    final Aggregations aggregations) {
        if (isStorageBillingDetailsShouldBeLoaded(request)) {
            return StorageBillingDetailsHelper.parseResponse(aggregations);
        }
        return null;
    }

}
