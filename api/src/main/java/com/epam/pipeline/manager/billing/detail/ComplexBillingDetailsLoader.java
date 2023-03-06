package com.epam.pipeline.manager.billing.detail;

import com.epam.pipeline.entity.billing.BillingGrouping;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class ComplexBillingDetailsLoader implements EntityBillingDetailsLoader {

    @Getter
    private final BillingGrouping grouping;
    private final List<EntityBillingDetailsLoader> loaders;

    @Override
    public Map<String, String> loadInformation(final String entityIdentifier, final boolean loadDetails,
                                               final Map<String, String> defaults) {
        return loaders.stream()
                .reduce(defaults,
                    (details, loader) -> loader.loadInformation(entityIdentifier, loadDetails, details),
                    (left, right) -> left);
    }
}
