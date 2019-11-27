/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cluster;


import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.InstanceOfferRequestVO;
import com.epam.pipeline.dao.cluster.InstanceOfferDao;
import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.InstancePrice;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.PipelineRunPrice;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.PipelineVersionManager;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Service
@AllArgsConstructor
@NoArgsConstructor
public class InstanceOfferManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceOfferManager.class);

    @Autowired
    private InstanceOfferDao instanceOfferDao;

    @Autowired
    private PipelineVersionManager versionManager;

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private CloudRegionManager cloudRegionManager;

    @Autowired
    private ContextualPreferenceManager contextualPreferenceManager;

    @Autowired
    private CloudFacade cloudFacade;

    /**
     * Map under the reference collects instance types grouped by the region ids.
     */
    private final AtomicReference<Map<Long, Set<String>>> offeredInstanceTypesMap =
            new AtomicReference<>(Collections.emptyMap());

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final Subject<List<InstanceType>> updatedInstanceTypesSubject = BehaviorSubject.create();

    private static final double ONE_SECOND = 1000;
    private static final double ONE_MINUTE = 60 * ONE_SECOND;
    private static final double ONE_HOUR = 60 * ONE_MINUTE;

    private static final String DELIMITER = ",";

    private static final List<String> INSTANCE_TYPES_PREFERENCES = Collections.singletonList(
            SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES.getKey());
    private static final List<String> TOOL_INSTANCE_TYPES_PREFERENCES = Arrays.asList(
            SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER.getKey(),
            SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES.getKey());
    private static final List<String> PRICE_TYPES_PREFERENCES = Collections.singletonList(
            SystemPreferences.CLUSTER_ALLOWED_PRICE_TYPES.getKey());

    @PostConstruct
    public void init() {
        updateOfferedInstanceTypes(instanceOfferDao.loadInstanceTypes());
        updateOfferedInstanceTypesOnPreferenceChange(SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES);
        updateOfferedInstanceTypesOnPreferenceChange(SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER);

        updatedInstanceTypesSubject.onNext(getAllInstanceTypes());
    }

    private void updateOfferedInstanceTypesOnPreferenceChange(final AbstractSystemPreference.StringPreference pref) {
        preferenceManager.getObservablePreference(pref)
            .subscribe(newInstanceTypes -> { // Will run on same thread, that updates preference
                LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_PREFERENCE_UPDATED_WITH_ADDITIONAL_TASKS,
                    pref.getKey(), newInstanceTypes, String.format("Update InstanceOfferManager::%s", pref.getKey())));
                updateOfferedInstanceTypes(instanceOfferDao.loadInstanceTypes());
            });
    }

    /**
     * Updates offered instance types for all regions.
     */
    public void updateOfferedInstanceTypes(final List<InstanceType> instanceTypes) {
        final Map<Long, Set<String>> offeredInstanceTypes = instanceTypes.stream()
                .collect(groupingBy(InstanceType::getRegionId, mapping(InstanceType::getName, toSet())));
        offeredInstanceTypesMap.set(offeredInstanceTypes);
    }

    public void updateOfferedInstanceTypes() {
        updateOfferedInstanceTypes(getAllInstanceTypes());
    }

    /**
     * Updates offered instance types for all regions.
     */
    public void updateOfferedInstanceTypesAccordingToInstanceOffers(final List<InstanceOffer> instanceOffers) {
        final Map<Long, Set<String>> offeredInstanceTypes = instanceOffers.stream()
                .collect(groupingBy(InstanceOffer::getRegionId, mapping(InstanceOffer::getInstanceType, toSet())));
        offeredInstanceTypesMap.set(offeredInstanceTypes);
    }

    public Date getPriceListPublishDate() {
        return instanceOfferDao.getPriceListPublishDate();
    }

    public InstancePrice getInstanceEstimatedPrice(Long id, String version, String configName,
            String instanceType, int instanceDisk, Boolean spot, Long regionId) throws GitClientException {

        Boolean useSpot = spot;

        if (StringUtils.isEmpty(instanceType) || instanceDisk <= 0 || useSpot == null) {
            PipelineConfiguration pipelineConfiguration =
                    versionManager.loadParametersFromScript(id, version, configName);
            if (StringUtils.isEmpty(instanceType)) {
                instanceType = pipelineConfiguration.getInstanceType();
            }
            if (instanceDisk <= 0) {
                instanceDisk = Integer.parseInt(pipelineConfiguration.getInstanceDisk());
            }
            if (useSpot == null) {
                useSpot = pipelineConfiguration.getIsSpot();
            }
        }

        InstancePrice instancePrice = getInstanceEstimatedPrice(instanceType, instanceDisk, useSpot, regionId);

        List<PipelineRun> runs = pipelineRunManager.loadAllRunsByPipeline(id, version)
                .stream().filter(run -> run.getStatus().isFinal()).collect(toList());
        if (!runs.isEmpty()) {
            long minimumDuration = -1;
            long maximumDuration = -1;
            long totalDurations = 0;
            for (PipelineRun run : runs) {
                long duration = run.getEndDate().getTime() - run.getStartDate().getTime();
                if (minimumDuration == -1 || minimumDuration > duration) {
                    minimumDuration = duration;
                }
                if (maximumDuration == -1 || maximumDuration < duration) {
                    maximumDuration = duration;
                }
                totalDurations += duration;
            }
            double averageDuration = (double) totalDurations / runs.size();
            instancePrice.setAverageTimePrice(instancePrice.getPricePerHour() * averageDuration / ONE_HOUR);
            instancePrice.setMinimumTimePrice(instancePrice.getPricePerHour() * minimumDuration / ONE_HOUR);
            instancePrice.setMaximumTimePrice(instancePrice.getPricePerHour() * maximumDuration / ONE_HOUR);
        }
        return instancePrice;
    }

    public InstancePrice getInstanceEstimatedPrice(
            String instanceType, int instanceDisk, Boolean spot, Long regionId) {
        final Long actualRegionId = defaultRegionIfNull(regionId);
        Assert.isTrue(isInstanceAllowed(instanceType, actualRegionId) ||
                        isToolInstanceAllowed(instanceType, null, actualRegionId),
                messageHelper.getMessage(MessageConstants.ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED,
                        instanceType));
        double pricePerHourForInstance =
                getPricePerHourForInstance(instanceType, isSpotRequest(spot), actualRegionId);
        double pricePerDisk = getPriceForDisk(instanceDisk, actualRegionId, instanceType);
        double pricePerHour = pricePerDisk + pricePerHourForInstance;
        return new InstancePrice(instanceType, instanceDisk, pricePerHour);
    }

    public PipelineRunPrice getPipelineRunEstimatedPrice(Long runId, Long regionId) {
        final Long actualRegionId = defaultRegionIfNull(regionId);
        PipelineRun pipelineRun = pipelineRunManager.loadPipelineRun(runId);
        RunInstance runInstance = pipelineRun.getInstance();
        double pricePerHourForInstance = getPricePerHourForInstance(runInstance.getNodeType(),
                isSpotRequest(runInstance.getSpot()), actualRegionId);
        double pricePerDisk = getPriceForDisk(runInstance.getNodeDisk(), actualRegionId, runInstance.getNodeType());
        double pricePerHour = pricePerDisk + pricePerHourForInstance;

        PipelineRunPrice price = new PipelineRunPrice();
        price.setInstanceDisk(runInstance.getNodeDisk());
        price.setInstanceType(runInstance.getNodeType());
        price.setPricePerHour(pricePerHour);

        if (pipelineRun.getStatus().isFinal()) {
            long duration = pipelineRun.getEndDate().getTime() - pipelineRun.getStartDate().getTime();
            price.setTotalPrice(duration / ONE_HOUR * pricePerHour);
        } else {
            price.setTotalPrice(0);
        }
        return price;
    }

    /**
     * Returns all instance types that are allowed on a system-wide level for all regions.
     */
    public List<InstanceType> getAllowedInstanceTypes() {
        return getAllInstanceTypes().stream()
                .filter(offer -> isInstanceTypeAllowed(offer.getName()))
                .collect(toList());
    }

    /**
     * Returns all instance types that are allowed on a system-wide level for the specified or default region.
     *
     * @param regionId If specified then instance types will be loaded only for the specified region.
     */
    public List<InstanceType> getAllowedInstanceTypes(final Long regionId) {
        return getAllInstanceTypes(defaultRegionIfNull(regionId)).stream()
                .filter(offer -> isInstanceTypeAllowed(offer.getName()))
                .collect(toList());
    }

    /**
     * Returns tool instance types that are allowed on a system-wide level for the specified or default region.
     *
     * @param regionId If specified then instance types will be loaded for the specified region.
     */
    public List<InstanceType> getAllowedToolInstanceTypes(final Long regionId) {
        return getAllInstanceTypes(defaultRegionIfNull(regionId)).stream()
                .filter(offer -> isToolInstanceTypeAllowed(offer.getName()))
                .collect(toList());
    }

    public double getPricePerHourForInstance(final String instanceType, final Long regionId) {
        final InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setInstanceType(instanceType);
        requestVO.setTermType(CloudInstancePriceService.ON_DEMAND_TERM_TYPE);
        requestVO.setOperatingSystem(CloudInstancePriceService.LINUX_OPERATING_SYSTEM);
        requestVO.setTenancy(CloudInstancePriceService.SHARED_TENANCY);
        requestVO.setUnit(CloudInstancePriceService.HOURS_UNIT);
        requestVO.setProductFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY);
        requestVO.setRegionId(regionId);
        return ListUtils.emptyIfNull(instanceOfferDao.loadInstanceOffers(requestVO))
                .stream()
                .map(InstanceOffer::getPricePerUnit)
                .filter(price -> Double.compare(price, 0.0) > 0)
                .min(Double::compareTo)
                .orElse(0.0);
    }

    /**
     * Checks if the given instance type is allowed for the authorized user in the specified or default region.
     *
     * Also checks if the instance type is one of the offered instance types.
     *
     * @param instanceType To be checked.
     * @param regionId If specified then instance types for the specified region will be used.
     */
    public boolean isInstanceAllowed(final String instanceType, final Long regionId) {
        return isInstanceTypeAllowed(instanceType, null, defaultRegionIfNull(regionId),
                INSTANCE_TYPES_PREFERENCES);
    }

    /**
     * Checks if the given tool instance type is allowed for the authorized user and the requested tool
     * in the specified or default region.
     *
     * Also checks if the instance type is one of the offered instance types.
     *
     * @param instanceType To be checked.
     * @param toolResource Optional tool resource.
     * @param regionId If specified then instance types for the specified region will be used.
     */
    public boolean isToolInstanceAllowed(final String instanceType,
                                         final ContextualPreferenceExternalResource toolResource,
                                         final Long regionId) {
        return isInstanceTypeAllowed(instanceType, toolResource, defaultRegionIfNull(regionId),
                TOOL_INSTANCE_TYPES_PREFERENCES);
    }

    /**
     * Checks if the given tool instance type is allowed for the authorized user and the requested tool
     * in any of the existing regions.
     *
     * Also checks if the instance type is one of the offered instance types.
     *
     * @param instanceType To be checked.
     * @param toolResource Optional tool resource.
     */
    public boolean isToolInstanceAllowedInAnyRegion(final String instanceType,
                                                    final ContextualPreferenceExternalResource toolResource) {
        return isInstanceTypeAllowed(instanceType, toolResource, null, TOOL_INSTANCE_TYPES_PREFERENCES);
    }

    private boolean isInstanceTypeAllowed(final String instanceType,
                                          final ContextualPreferenceExternalResource resource,
                                          final Long regionId,
                                          final List<String> instanceTypesPreferences) {
        return !StringUtils.isEmpty(instanceType)
                && isInstanceTypeMatchesAllowedPatterns(instanceType, resource, instanceTypesPreferences)
                && isInstanceTypeOffered(instanceType, regionId);
    }

    private boolean isInstanceTypeMatchesAllowedPatterns(final String instanceType,
                                                         final ContextualPreferenceExternalResource resource,
                                                         final List<String> instanceTypesPreferences) {
        return getContextualPreferenceValueAsList(resource, instanceTypesPreferences).stream()
                .anyMatch(pattern -> matcher.match(pattern, instanceType));
    }

    private boolean isInstanceTypeOffered(final String instanceType, final Long regionId) {
        return regionId != null
                ? isInstanceTypeOfferedInRegion(instanceType, regionId)
                : isInstanceTypeOfferedInAnyRegion(instanceType);
    }

    private boolean isInstanceTypeOfferedInRegion(final String instanceType, final Long regionId) {
        return offeredInstanceTypesMap.get()
                .getOrDefault(regionId, Collections.emptySet())
                .contains(instanceType);
    }

    private boolean isInstanceTypeOfferedInAnyRegion(final String instanceType) {
        return offeredInstanceTypesMap.get().values().stream()
                .flatMap(Set::stream)
                .anyMatch(instanceType::equals);
    }

    /**
     * Checks if the given price type is allowed for the authorized user and tool.
     *
     * @param priceType To be checked.
     * @param toolResource Optional tool resource.
     */
    public boolean isPriceTypeAllowed(final String priceType,
                                      final ContextualPreferenceExternalResource toolResource) {
        return getContextualPreferenceValueAsList(toolResource, PRICE_TYPES_PREFERENCES).stream()
                .anyMatch(priceType::equals);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void refreshPriceList() {
        LOGGER.debug(messageHelper.getMessage(MessageConstants.DEBUG_INSTANCE_OFFERS_UPDATE_STARTED));
        instanceOfferDao.removeInstanceOffers();
        List<InstanceOffer> instanceOffers = cloudRegionManager.loadAll()
                .stream()
                .map(this::updatePriceListForRegion)
                .flatMap(List::stream)
                .collect(toList());

        updatedInstanceTypesSubject.onNext(getAllInstanceTypes());

        LOGGER.debug(messageHelper.getMessage(MessageConstants.DEBUG_INSTANCE_OFFERS_UPDATE_FINISHED));
        LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_INSTANCE_OFFERS_UPDATED, instanceOffers.size()));
        updateOfferedInstanceTypesAccordingToInstanceOffers(instanceOffers);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<InstanceOffer> updatePriceListForRegion(AbstractCloudRegion cloudRegion) {
        instanceOfferDao.removeInstanceOffersForRegion(cloudRegion.getId());
        List<InstanceOffer> instanceOffers = cloudFacade.refreshPriceListForRegion(cloudRegion.getId());
        instanceOfferDao.insertInstanceOffers(instanceOffers);
        return instanceOffers;
    }

    private boolean isSpotRequest(Boolean spot) {
        return spot == null ? preferenceManager.getPreference(SystemPreferences.CLUSTER_SPOT) : spot;
    }

    private double getPricePerHourForInstance(String instanceType, boolean isSpot, Long regionId) {
        return isSpot ? getSpotPricePerHour(instanceType, regionId) :
                getPricePerHourForInstance(instanceType, regionId);
    }

    private double getSpotPricePerHour(String instanceType, Long regionId) {
        return cloudFacade.getSpotPrice(regionId, instanceType);
    }

    /**
     * Returns all instance types for all regions.
     */
    public List<InstanceType> getAllInstanceTypes() {
        return getAllInstanceTypes(null);
    }

    /**
     * Returns all instance types for the specified region.
     *
     * @param regionId If specified then instance types will be loaded only for the specified region.
     */
    public List<InstanceType> getAllInstanceTypes(final Long regionId) {
        InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setTermType(CloudInstancePriceService.ON_DEMAND_TERM_TYPE);
        requestVO.setOperatingSystem(CloudInstancePriceService.LINUX_OPERATING_SYSTEM);
        requestVO.setTenancy(CloudInstancePriceService.SHARED_TENANCY);
        requestVO.setUnit(CloudInstancePriceService.HOURS_UNIT);
        requestVO.setProductFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY);
        requestVO.setRegionId(regionId);
        return instanceOfferDao.loadInstanceTypes(requestVO);
    }

    public Observable<List<InstanceType>> getAllInstanceTypesObservable() {
        return updatedInstanceTypesSubject;
    }

    private double getPriceForDisk(int instanceDisk, Long regionId, String instanceType) {
        InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setProductFamily(CloudInstancePriceService.STORAGE_PRODUCT_FAMILY);
        requestVO.setVolumeType(CloudInstancePriceService.GENERAL_PURPOSE_VOLUME_TYPE);
        requestVO.setRegionId(regionId);
        List<InstanceOffer> offers = instanceOfferDao.loadInstanceOffers(requestVO);
        return cloudFacade.getPriceForDisk(regionId, offers, instanceDisk, instanceType);
    }

    private boolean isInstanceTypeAllowed(final String instanceType) {
        return isInstanceTypeAllowed(instanceType, SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES);
    }

    private boolean isToolInstanceTypeAllowed(final String instanceType) {
        return isInstanceTypeAllowed(instanceType, SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER);
    }

    private boolean isInstanceTypeAllowed(final String instanceType,
                                          final AbstractSystemPreference.StringPreference patternPreference) {
        return isInstanceTypeAllowed(instanceType, preferenceManager.getPreference(patternPreference));
    }

    private boolean isInstanceTypeAllowed(final String instanceType, final String pattern) {
        if (StringUtils.isBlank(pattern)) {
            return true;
        }
        List<String> allowedInstancePatterns = Arrays.asList(pattern.split(","));
        return allowedInstancePatterns.stream().anyMatch(type -> matcher.match(type, instanceType));
    }

    /**
     * Returns allowed instance and price types for a current user in the specified region.
     *
     * @param toolId If specified then allowed types will be bounded for the specified tool.
     * @param regionId If specified then allowed types will be loaded only for the specified region.
     */
    public AllowedInstanceAndPriceTypes getAllowedInstanceAndPriceTypes(final Long toolId, final Long regionId) {
        final ContextualPreferenceExternalResource resource = toolResource(toolId);
        final List<InstanceType> instanceTypes = getAllInstanceTypes(defaultRegionIfNull(regionId));
        final List<InstanceType> allowedInstanceTypes = getAllowedInstanceTypes(instanceTypes, resource,
                SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES);
        final List<InstanceType> allowedInstanceDockerTypes = getAllowedInstanceTypes(instanceTypes, resource,
                SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER,
                SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES);
        final List<String> allowedPriceTypes = getContextualPreferenceValueAsList(resource,
                SystemPreferences.CLUSTER_ALLOWED_PRICE_TYPES);
        return new AllowedInstanceAndPriceTypes(allowedInstanceTypes, allowedInstanceDockerTypes, allowedPriceTypes);
    }

    private ContextualPreferenceExternalResource toolResource(final Long toolId) {
        return Optional.ofNullable(toolId)
                .map(Object::toString)
                .map(id -> new ContextualPreferenceExternalResource(ContextualPreferenceLevel.TOOL, id))
                .orElse(null);
    }

    private Long defaultRegionIfNull(final Long regionId) {
        return Optional.ofNullable(regionId)
                .orElseGet(() -> cloudRegionManager.loadDefaultRegion().getId());
    }

    private List<InstanceType> getAllowedInstanceTypes(
            final List<InstanceType> instanceTypes,
            final ContextualPreferenceExternalResource resource,
            final AbstractSystemPreference.StringPreference... preferences) {
        final List<String> allowedInstanceTypePatterns = getContextualPreferenceValueAsList(resource, preferences);
        return instanceTypes.stream()
                .filter(instanceType -> allowedInstanceTypePatterns.stream()
                        .anyMatch(pattern -> matcher.match(pattern, instanceType.getName())))
                .collect(toList());
    }

    private List<String> getContextualPreferenceValueAsList(
            final ContextualPreferenceExternalResource resource,
            final AbstractSystemPreference.StringPreference... preferences) {
        final List<String> preferenceNames = Arrays.stream(preferences)
                .map(AbstractSystemPreference::getKey)
                .collect(toList());
        return getContextualPreferenceValueAsList(resource, preferenceNames);
    }

    private List<String> getContextualPreferenceValueAsList(final ContextualPreferenceExternalResource resource,
                                                            final List<String> preferences) {
        return Arrays.asList(contextualPreferenceManager.search(preferences, resource).getValue().split(DELIMITER));
    }
}
