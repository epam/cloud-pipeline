package com.epam.pipeline.manager.cloud.offer;

import com.epam.pipeline.entity.cluster.InstanceOffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class InstanceOfferTermTypeFilter implements InstanceOfferFilter {

    private final Set<String> termTypes;

    @Override
    public List<InstanceOffer> filter(final List<InstanceOffer> offers) {
        log.debug("Filtering term instance offers...");
        final List<InstanceOffer> filteredOffers = offers.stream()
                .filter(it -> termTypes.contains(it.getTermType()))
                .collect(Collectors.toList());
        log.debug("Filtered out {} instance offers.", offers.size() - filteredOffers.size());
        return filteredOffers;
    }
}
