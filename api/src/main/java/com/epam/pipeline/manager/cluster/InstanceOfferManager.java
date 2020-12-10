/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.PipelineVersionManager;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.utils.CommonUtils;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
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
@NoArgsConstructor
public class InstanceOfferManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceOfferManager.class);
    private static final String EMPTY = "";

    private InstanceOfferDao instanceOfferDao;
    private PipelineVersionManager versionManager;
    private PipelineRunManager pipelineRunManager;
    private MessageHelper messageHelper;
    private PreferenceManager preferenceManager;
    private CloudRegionManager cloudRegionManager;
    private ContextualPreferenceManager contextualPreferenceManager;
    private Map<CloudProvider, CloudInstancePriceService> instancePriceServices;

    @Autowired
    public InstanceOfferManager(final InstanceOfferDao instanceOfferDao,
                                final PipelineVersionManager versionManager,
                                final PipelineRunManager pipelineRunManager,
                                final MessageHelper messageHelper,
                                final PreferenceManager preferenceManager,
                                final CloudRegionManager cloudRegionManager,
                                final ContextualPreferenceManager contextualPreferenceManager,
                                final List<CloudInstancePriceService> instancePriceServices) {
        this.instanceOfferDao = instanceOfferDao;
        this.versionManager = versionManager;
        this.pipelineRunManager = pipelineRunManager;
        this.messageHelper = messageHelper;
        this.preferenceManager = preferenceManager;
        this.cloudRegionManager = cloudRegionManager;
        this.contextualPreferenceManager = contextualPreferenceManager;
        this.instancePriceServices = CommonUtils.groupByCloudProvider(instancePriceServices);
    }

    /**
     * Map under the reference collects instance types grouped by the region ids.
     */
    private final AtomicReference<Map<Long, Map<PriceType, Set<String>>>> offeredInstanceTypesMap =
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
    private static final List<String> MASTER_PRICE_TYPES_PREFERENCES = Collections.singletonList(
            SystemPreferences.CLUSTER_ALLOWED_MASTER_PRICE_TYPES.getKey());

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
        final Map<Long, Map<PriceType, Set<String>>>offeredInstanceTypes = groupInstanceTypes(instanceTypes);
        offeredInstanceTypesMap.set(extendInstanceTypesForAws(offeredInstanceTypes));
    }

    public void updateOfferedInstanceTypes() {
        updateOfferedInstanceTypes(getAllInstanceTypes());
    }

    /**
     * Updates offered instance types for all regions.
     */
    public void updateOfferedInstanceTypesAccordingToInstanceOffers(final List<InstanceOffer> instanceOffers) {
        final Map<Long, Map<PriceType, Set<String>>> offeredInstanceTypes = groupInstanceOffers(instanceOffers);
        offeredInstanceTypesMap.set(extendInstanceTypesForAws(offeredInstanceTypes));
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
        final boolean isSpot = isSpotRequest(spot);
        final Long actualRegionId = defaultRegionIfNull(regionId);
        Assert.isTrue(isInstanceAllowed(instanceType, actualRegionId, isSpot) ||
                        isToolInstanceAllowed(instanceType, null, actualRegionId, isSpot),
                messageHelper.getMessage(MessageConstants.ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED,
                        instanceType));
        double computePricePerHour = getPricePerHourForInstance(instanceType, isSpot, actualRegionId);
        double initialDiskPricePerHour = getPriceForDisk(instanceDisk, actualRegionId, instanceType, isSpot);
        double diskPricePerHour = initialDiskPricePerHour / instanceDisk;
        double pricePerHour = initialDiskPricePerHour + computePricePerHour;
        return new InstancePrice(instanceType, instanceDisk, pricePerHour, computePricePerHour, diskPricePerHour);
    }

    public PipelineRunPrice getPipelineRunEstimatedPrice(Long runId, Long regionId) {
        final Long actualRegionId = defaultRegionIfNull(regionId);
        PipelineRun pipelineRun = pipelineRunManager.loadPipelineRun(runId);
        RunInstance runInstance = pipelineRun.getInstance();
        boolean spot = isSpotRequest(runInstance.getSpot());
        double computePricePerHour = getPricePerHourForInstance(runInstance.getNodeType(), spot, actualRegionId);
        double initialDiskPricePerHour = getPriceForDisk(runInstance.getNodeDisk(), actualRegionId,
                runInstance.getNodeType(), spot);
        double pricePerHour = initialDiskPricePerHour + computePricePerHour;

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
    public List<InstanceType> getAllowedInstanceTypes(final Long regionId, final Boolean spot) {
        final boolean isSpot = isSpotRequest(spot);
        return getAllInstanceTypes(defaultRegionIfNull(regionId), isSpot).stream()
                .filter(offer -> isInstanceTypeAllowed(offer.getName()))
                .collect(toList());
    }

    /**
     * Returns tool instance types that are allowed on a system-wide level for the specified or default region.
     *
     * @param regionId If specified then instance types will be loaded for the specified region.
     */
    public List<InstanceType> getAllowedToolInstanceTypes(final Long regionId, final Boolean spot) {
        final boolean isSpot = isSpotRequest(spot);
        return getAllInstanceTypes(defaultRegionIfNull(regionId), isSpot).stream()
                .filter(offer -> isToolInstanceTypeAllowed(offer.getName()))
                .collect(toList());
    }

    public double getPricePerHourForInstance(final String instanceType, final Long regionId) {
        final InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setInstanceType(instanceType);
        requestVO.setTermType(CloudInstancePriceService.TermType.ON_DEMAND.getName());
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
    public boolean isInstanceAllowed(final String instanceType, final Long regionId, final boolean spot) {
        return isInstanceTypeAllowed(instanceType, null, defaultRegionIfNull(regionId),
                INSTANCE_TYPES_PREFERENCES, spot);
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
                                         final Long regionId,
                                         final boolean spot) {
        return isInstanceTypeAllowed(instanceType, toolResource, defaultRegionIfNull(regionId),
                TOOL_INSTANCE_TYPES_PREFERENCES, spot);
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
        return isInstanceTypeAllowed(instanceType, toolResource, null, TOOL_INSTANCE_TYPES_PREFERENCES, false)
                || isInstanceTypeAllowed(instanceType, toolResource, null, TOOL_INSTANCE_TYPES_PREFERENCES, true);
    }

    private Map<Long, Map<PriceType, Set<String>>> groupInstanceTypes(final List<InstanceType> instanceTypes) {
        return instanceTypes.stream()
                .filter(it -> Arrays.stream(CloudInstancePriceService.TermType.values())
                        .anyMatch(priceType -> StringUtils.defaultIfEmpty(it.getTermType(), EMPTY)
                                .equalsIgnoreCase(priceType.getName())))
                .collect(groupingBy(InstanceType::getRegionId,
                        groupingBy(it -> PriceType.fromTermType(it.getTermType()),
                                mapping(InstanceType::getName, toSet()))));
    }

    private Map<Long, Map<PriceType, Set<String>>> groupInstanceOffers(final List<InstanceOffer> instanceOffers) {
        return instanceOffers.stream()
                .filter(it -> Arrays.stream(CloudInstancePriceService.TermType.values())
                        .anyMatch(priceType -> StringUtils.defaultIfEmpty(it.getTermType(), EMPTY)
                                .equalsIgnoreCase(priceType.getName())))
                .collect(groupingBy(InstanceOffer::getRegionId,
                        groupingBy(io -> PriceType.fromTermType(io.getTermType()),
                                mapping(InstanceOffer::getInstanceType, toSet()))));
    }

    private Map<Long, Map<PriceType, Set<String>>> extendInstanceTypesForAws(
            final Map<Long, Map<PriceType, Set<String>>> offeredInstanceTypes) {
        offeredInstanceTypes.forEach((regionId, prices) -> {
            final AbstractCloudRegion region = cloudRegionManager.load(regionId);
            if (region.getProvider() == CloudProvider.AWS) {
                prices.put(PriceType.SPOT, prices.get(PriceType.ON_DEMAND));
            }
        });
        return offeredInstanceTypes;
    }

    private boolean isInstanceTypeAllowed(final String instanceType,
                                          final ContextualPreferenceExternalResource resource,
                                          final Long regionId,
                                          final List<String> instanceTypesPreferences,
                                          final boolean spot) {
        return !StringUtils.isEmpty(instanceType)
                && isInstanceTypeMatchesAllowedPatterns(instanceType, resource, instanceTypesPreferences)
                && isInstanceTypeOffered(instanceType, regionId, spot);
    }

    private boolean isInstanceTypeMatchesAllowedPatterns(final String instanceType,
                                                         final ContextualPreferenceExternalResource resource,
                                                         final List<String> instanceTypesPreferences) {
        return getContextualPreferenceValueAsList(resource, instanceTypesPreferences).stream()
                .anyMatch(pattern -> matcher.match(pattern, instanceType));
    }

    private boolean isInstanceTypeOffered(final String instanceType, final Long regionId, final boolean spot) {
        return regionId != null
                ? isInstanceTypeOfferedInRegion(instanceType, regionId, spot)
                : isInstanceTypeOfferedInAnyRegion(instanceType, spot);
    }

    private boolean isInstanceTypeOfferedInRegion(final String instanceType, final Long regionId, final boolean spot) {
        final Set<String> regionInstances = offeredInstanceTypesMap.get()
                .getOrDefault(regionId, Collections.emptyMap()).get(spot ? PriceType.SPOT : PriceType.ON_DEMAND);
        return SetUtils.emptyIfNull(regionInstances).contains(instanceType);
    }

    private boolean isInstanceTypeOfferedInAnyRegion(final String instanceType, final boolean spot) {
        return offeredInstanceTypesMap.get().values().stream()
                .map(m -> m.get(spot ? PriceType.SPOT : PriceType.ON_DEMAND))
                .flatMap(s -> SetUtils.emptyIfNull(s).stream())
                .anyMatch(instanceType::equals);
    }

    /**
     * Checks if the given price type is allowed for the authorized user and tool.
     *
     * @param priceType To be checked.
     * @param toolResource Optional tool resource.
     * @param isMaster is checking node a master in cluster run
     */
    public boolean isPriceTypeAllowed(final String priceType,
                                      final ContextualPreferenceExternalResource toolResource,
                                      final boolean isMaster) {
        final List<String> priceTypesPreferences = isMaster
                                     ? MASTER_PRICE_TYPES_PREFERENCES
                                     : PRICE_TYPES_PREFERENCES;
        return getContextualPreferenceValueAsList(toolResource, priceTypesPreferences).stream()
            .anyMatch(priceType::equals);
    }

    public boolean isPriceTypeAllowed(final String priceType,
                                      final ContextualPreferenceExternalResource toolResource) {
        return isPriceTypeAllowed(priceType, toolResource, false);
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
        final AbstractCloudRegion region = cloudRegionManager.load(cloudRegion.getId());
        List<InstanceOffer> instanceOffers = getInstancePriceService(region).refreshPriceListForRegion(cloudRegion);
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
        final AbstractCloudRegion region = cloudRegionManager.loadOrDefault(regionId);
        return getInstancePriceService(region).getSpotPrice(instanceType, region);
    }

    /**
     * Returns all instance types for all regions.
     */
    public List<InstanceType> getAllInstanceTypes() {
        return ListUtils.sum(getAllInstanceTypes(null, false), getAllInstanceTypes(null, true));
    }

    /**
     * Returns all instance types for the specified region.
     *
     * @param regionId If specified then instance types will be loaded only for the specified region.
     */
    public List<InstanceType> getAllInstanceTypes(final Long regionId, final boolean spot) {
        if (regionId == null) {
            return loadInstancesForAllRegions(spot);
        } else {
            final AbstractCloudRegion region = cloudRegionManager.loadOrDefault(regionId);
            return getInstancePriceService(region).getAllInstanceTypes(region.getId(), spot);
        }
    }

    private List<InstanceType> loadInstancesForAllRegions(final Boolean spot) {
        return (List<InstanceType>) instancePriceServices.values()
            .stream()
            .map(priceService -> priceService.getAllInstanceTypes(null, spot))
            .flatMap(cloudInstanceTypes -> cloudInstanceTypes.stream())
            .collect(toList());
    }

    public Observable<List<InstanceType>> getAllInstanceTypesObservable() {
        return updatedInstanceTypesSubject;
    }

    private double getPriceForDisk(int instanceDisk, Long regionId, String instanceType, boolean spot) {
        InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setProductFamily(CloudInstancePriceService.STORAGE_PRODUCT_FAMILY);
        requestVO.setVolumeType(CloudInstancePriceService.GENERAL_PURPOSE_VOLUME_TYPE);
        requestVO.setRegionId(regionId);
        List<InstanceOffer> offers = instanceOfferDao.loadInstanceOffers(requestVO);
        final AbstractCloudRegion region = cloudRegionManager.loadOrDefault(regionId);
        return getInstancePriceService(region).getPriceForDisk(offers, instanceDisk, instanceType, spot, region);
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
     * @param spot if true allowed instances types will be filtered and only spot instance proposal will be shown
     */
    public AllowedInstanceAndPriceTypes getAllowedInstanceAndPriceTypes(final Long toolId, final Long regionId,
                                                                        final Boolean spot) {
        final boolean isSpot = isSpotRequest(spot);
        final ContextualPreferenceExternalResource resource = toolResource(toolId);
        final List<InstanceType> instanceTypes = getAllInstanceTypes(defaultRegionIfNull(regionId), isSpot);
        final List<InstanceType> allowedInstanceTypes = getAllowedInstanceTypes(instanceTypes, resource,
                SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES);
        final List<InstanceType> allowedInstanceDockerTypes = getAllowedInstanceTypes(instanceTypes, resource,
                SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER,
                SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES);
        final List<String> allowedPriceTypes = getContextualPreferenceValueAsList(resource,
                SystemPreferences.CLUSTER_ALLOWED_PRICE_TYPES);
        final List<String> allowedMasterPriceTypes = getContextualPreferenceValueAsList(resource,
                SystemPreferences.CLUSTER_ALLOWED_MASTER_PRICE_TYPES);
        return new AllowedInstanceAndPriceTypes(allowedInstanceTypes, allowedInstanceDockerTypes,
                                                allowedPriceTypes, allowedMasterPriceTypes);
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

    private CloudInstancePriceService getInstancePriceService(final AbstractCloudRegion region) {
        return CommonUtils.getServiceForRegion(instancePriceServices, messageHelper, region);
    }
}
