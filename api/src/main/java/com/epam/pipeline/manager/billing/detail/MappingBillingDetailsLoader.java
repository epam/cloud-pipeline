package com.epam.pipeline.manager.billing.detail;

import com.epam.pipeline.entity.billing.BillingGrouping;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class MappingBillingDetailsLoader implements EntityBillingDetailsLoader {

    @Getter
    private final BillingGrouping grouping;
    private final Map<String, String> mappings;

    @Override
    public Map<String, String> loadInformation(final String entityIdentifier, final boolean loadDetails,
                                               final Map<String, String> defaults) {
        return mappings.entrySet().stream()
                .map(e -> Pair.of(e.getKey(), defaults.get(e.getValue())))
                .filter(p -> p.getValue() != null)
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }
}
