/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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
