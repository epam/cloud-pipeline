/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
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
 * limitations under the License.
 */

package com.epam.pipeline.manager.cloud.gcp;

import com.epam.pipeline.controller.vo.InstanceOfferRequestVO;
import com.epam.pipeline.dao.cluster.InstanceOfferDao;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.manager.cloud.gcp.extractor.GCPObjectExtractor;
import com.epam.pipeline.manager.cloud.gcp.resource.AbstractGCPObject;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class GCPInstancePriceService implements CloudInstancePriceService<GCPRegion> {

    private static final long BILLION = 1_000_000_000L;
    private static final int PRICES_PRECISION = 5;

    private final PreferenceManager preferenceManager;
    private final InstanceOfferDao instanceOfferDao;
    private final List<GCPObjectExtractor> extractors;
    private final GCPResourcePriceLoader priceLoader;

    @Override
    public List<InstanceOffer> refreshPriceListForRegion(final GCPRegion region) {
        try {
            final List<AbstractGCPObject> objects = availableObjects(region);
            final Map<String, GCPResourceMapping> mappings = loadResourceMappings();
            final List<GCPResourceRequest> requests = requests(objects, mappings);
            final Set<GCPResourcePrice> prices = priceLoader.load(region, requests);
            return objects.stream()
                    .flatMap(object -> Arrays.stream(GCPBilling.values())
                            .map(billing -> object.toInstanceOffer(billing,
                                    getPrice(object, billing, prices, region.getRegionCode()), region.getId())))
                    .collect(Collectors.toList());
        } catch (GCPInstancePriceException e) {
            log.error("Failed to get instance types and prices from GCP.", e);
            return Collections.emptyList();
        }
    }

    private List<AbstractGCPObject> availableObjects(final GCPRegion region) {
        return extractors.stream()
                .flatMap(it -> it.extract(region).stream())
                .collect(Collectors.toList());
    }

    private Map<String, GCPResourceMapping> loadResourceMappings() {
        return MapUtils.emptyIfNull(preferenceManager.getPreference(SystemPreferences.GCP_SKU_MAPPING));
    }

    private List<GCPResourceRequest> requests(final List<AbstractGCPObject> objects,
                                              final Map<String, GCPResourceMapping> mappings) {
        return objects.stream()
                .flatMap(machine -> Arrays.stream(GCPResourceType.values())
                        .filter(machine::isRequired)
                        .flatMap(type -> requests(machine, type, mappings)))
                .collect(Collectors.toList());
    }

    private Stream<GCPResourceRequest> requests(final AbstractGCPObject object,
                                                final GCPResourceType type,
                                                final Map<String, GCPResourceMapping> mappings) {
        return Arrays.stream(GCPBilling.values())
                .map(billing -> Optional.of(object.billingKey(billing, type))
                        .map(mappings::get)
                        .map(mapping -> new GCPResourceRequest(type, billing, object, mapping)))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Double getPrice(final AbstractGCPObject object,
                            final GCPBilling billing,
                            final Set<GCPResourcePrice> prices,
                            final String region) {
        final List<GCPResourceType> requiredTypes = Arrays.stream(GCPResourceType.values())
                .filter(object::isRequired)
                .collect(Collectors.toList());
        final Map<GCPResourceType, GCPResourcePrice> objectPrices = prices.stream()
                .filter(price -> price.getRequest().getObject().equals(object))
                .filter(price -> price.getRequest().getBilling().equals(billing))
                .collect(Collectors.toMap(price -> price.getRequest().getType(), Function.identity()));
        final Optional<GCPResourceType> typeWithMissingPrice = requiredTypes.stream()
                .filter(type -> !objectPrices.keySet().contains(type))
                .findFirst();
        if (typeWithMissingPrice.isPresent()) {
            log.error("Price for {} with {} billing for GCP object {} wasn't found in {} region.",
                    typeWithMissingPrice.get(), billing.alias(), object.getName(), region);
            return 0.0;
        }
        final long nanos = object.totalPrice(new ArrayList<>(objectPrices.values()));
        return new BigDecimal(((double) nanos) / BILLION)
                .setScale(PRICES_PRECISION, RoundingMode.HALF_EVEN)
                .doubleValue();
    }

    @Override
    public double getSpotPrice(final String instanceType, final GCPRegion region) {
        final InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setInstanceType(instanceType);
        requestVO.setTermType(TermType.PREEMPTIBLE.getName());
        requestVO.setOperatingSystem(CloudInstancePriceService.LINUX_OPERATING_SYSTEM);
        requestVO.setTenancy(CloudInstancePriceService.SHARED_TENANCY);
        requestVO.setUnit(CloudInstancePriceService.HOURS_UNIT);
        requestVO.setProductFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY);
        requestVO.setRegionId(region.getId());
        final List<InstanceOffer> offers = ListUtils.emptyIfNull(instanceOfferDao.loadInstanceOffers(requestVO));
        return offers.stream()
                .findFirst()
                .map(InstanceOffer::getPricePerUnit)
                .orElse(0.0);
    }

    @Override
    public double getPriceForDisk(final List<InstanceOffer> offers,
                                  final int instanceDisk,
                                  final String instanceType,
                                  final boolean spot,
                                  final GCPRegion region) {
        return offers.stream()
                .filter(offer -> spot
                        ? offer.getTermType().equals(TermType.PREEMPTIBLE.getName())
                        : offer.getTermType().equals(TermType.ON_DEMAND.getName())
                )
                .findFirst()
                .map(offer -> offer.getPricePerUnit() * instanceDisk)
                .orElse(0.0);
    }

    @Override
    public List<InstanceType> getAllInstanceTypes(final Long regionId, final boolean spot) {
        final InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setTermType(spot ? TermType.PREEMPTIBLE.getName() : TermType.ON_DEMAND.getName());
        requestVO.setOperatingSystem(CloudInstancePriceService.LINUX_OPERATING_SYSTEM);
        requestVO.setTenancy(CloudInstancePriceService.SHARED_TENANCY);
        requestVO.setUnit(CloudInstancePriceService.HOURS_UNIT);
        requestVO.setProductFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY);
        requestVO.setRegionId(regionId);
        requestVO.setCloudProvider(CloudProvider.GCP.name());
        return instanceOfferDao.loadInstanceTypes(requestVO);
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.GCP;
    }

}
