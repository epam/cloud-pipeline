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
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.PipelineVersionManager;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.AwsRegionManager;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
    private EC2Helper ec2Helper;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private AwsRegionManager awsRegionManager;

    @Autowired
    private ContextualPreferenceManager contextualPreferenceManager;

    private final AtomicReference<Set<String>> offeredInstanceTypes = new AtomicReference<>(Collections.emptySet());

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final Subject<List<InstanceType>> updatedInstanceTypesSubject = BehaviorSubject.create();

    private static final String AWS_EC2_PRICING_URL_TEMPLATE =
            "https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonEC2/current/%s/index.csv";

    private static final int COLUMNS_LINE_INDEX = 5;

    private static final String ON_DEMAND_TERM_TYPE = "OnDemand";
    private static final String LINUX_OPERATING_SYSTEM = "Linux";
    private static final String SHARED_TENANCY = "Shared";
    private static final String HOURS_UNIT = "Hrs";
    private static final String INSTANCE_PRODUCT_FAMILY = "Compute Instance";
    private static final String STORAGE_PRODUCT_FAMILY = "Storage";
    private static final String GENERAL_PURPOSE_VOLUME_TYPE = "General Purpose";

    private static final double ONE_SECOND = 1000;
    private static final double ONE_MINUTE = 60 * ONE_SECOND;
    private static final double ONE_HOUR = 60 * ONE_MINUTE;
    private static final double HOURS_IN_DAY = 24;
    private static final double DAYS_IN_MONTH = 30;

    private static final String DELIMITER = ",";

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
     * Updates offered instance types.
     */
    public void updateOfferedInstanceTypes(final List<InstanceType> instanceTypes) {
        offeredInstanceTypes.set(instanceTypes.stream()
                .map(InstanceType::getName)
                .collect(Collectors.toSet()));
    }

    public void updateOfferedInstanceTypes() {
        offeredInstanceTypes.set(getAllInstanceTypes().stream()
                .map(InstanceType::getName)
                .collect(Collectors.toSet()));
    }

    /**
     * Updates offered instance types according to the given instance offers.
     */
    public void updateOfferedInstanceTypesAccordingToInstanceOffers(final List<InstanceOffer> instanceOffers) {
        offeredInstanceTypes.set(instanceOffers.stream()
                .map(InstanceOffer::getInstanceType)
                .collect(Collectors.toSet()));
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

        Assert.isTrue(isInstanceAllowed(instanceType),
                messageHelper.getMessage(MessageConstants.ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED,
                        instanceType));
        AwsRegion region = awsRegionManager.loadRegionOrGetDefault(regionId);
        double pricePerHourForInstance =
                getPricePerHourForInstance(instanceType, isSpotRequest(useSpot), region.getAwsRegionName());
        double pricePerDisk = getPriceForDisk(instanceDisk, region.getAwsRegionName());
        double pricePerHour = pricePerDisk + pricePerHourForInstance;

        InstancePrice instancePrice = new InstancePrice(instanceType, instanceDisk, pricePerHour);

        List<PipelineRun> runs = pipelineRunManager.loadAllRunsByPipeline(id, version)
                .stream().filter(run -> run.getStatus().isFinal()).collect(Collectors.toList());
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
            instancePrice.setAverageTimePrice(pricePerHour * averageDuration / ONE_HOUR);
            instancePrice.setMinimumTimePrice(pricePerHour * minimumDuration / ONE_HOUR);
            instancePrice.setMaximumTimePrice(pricePerHour * maximumDuration / ONE_HOUR);
        }
        return instancePrice;
    }

    public InstancePrice getInstanceEstimatedPrice(
            String instanceType, int instanceDisk, Boolean spot, Long regionId) {
        Assert.isTrue(isInstanceAllowed(instanceType),
                messageHelper.getMessage(MessageConstants.ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED,
                        instanceType));
        AwsRegion region = awsRegionManager.loadRegionOrGetDefault(regionId);
        return getInstanceEstimatedPrice(instanceType, instanceDisk, spot, region.getAwsRegionName());
    }

    public InstancePrice getInstanceEstimatedPrice(
            String instanceType, int instanceDisk, Boolean spot, String awsRegionName) {
        Assert.isTrue(isInstanceAllowed(instanceType),
                messageHelper.getMessage(MessageConstants.ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED,
                        instanceType));
        double pricePerHourForInstance =
                getPricePerHourForInstance(instanceType, isSpotRequest(spot), awsRegionName);
        double pricePerDisk = getPriceForDisk(instanceDisk, awsRegionName);
        double pricePerHour = pricePerDisk + pricePerHourForInstance;
        return new InstancePrice(instanceType, instanceDisk, pricePerHour);
    }

    public PipelineRunPrice getPipelineRunEstimatedPrice(Long runId, Long regionId) {
        PipelineRun pipelineRun = pipelineRunManager.loadPipelineRun(runId);
        RunInstance runInstance = pipelineRun.getInstance();
        AwsRegion region = awsRegionManager.loadRegionOrGetDefault(regionId);
        double pricePerHourForInstance = getPricePerHourForInstance(runInstance.getNodeType(),
                isSpotRequest(runInstance.getSpot()), region.getAwsRegionName());
        double pricePerDisk = getPriceForDisk(runInstance.getNodeDisk(), region.getAwsRegionName());
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

    public List<InstanceType> getAllowedInstanceTypes() {
        return getAllInstanceTypes().stream()
            .filter(offer -> isInstanceTypeAllowed(offer.getName()))
            .collect(Collectors.toList());
    }

    public List<InstanceType> getAllowedToolInstanceTypes() {
        return getAllInstanceTypes().stream()
            .filter(offer -> isToolInstanceTypeAllowed(offer.getName()))
            .collect(Collectors.toList());
    }

    public double getPricePerHourForInstance(String instanceType, String regionId) {
        InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setInstanceType(instanceType);
        requestVO.setTermType(ON_DEMAND_TERM_TYPE);
        requestVO.setOperatingSystem(LINUX_OPERATING_SYSTEM);
        requestVO.setTenancy(SHARED_TENANCY);
        requestVO.setUnit(HOURS_UNIT);
        requestVO.setProductFamily(INSTANCE_PRODUCT_FAMILY);
        requestVO.setRegion(regionId);
        return ListUtils.emptyIfNull(instanceOfferDao.loadInstanceOffers(requestVO))
                .stream()
                .map(InstanceOffer::getPricePerUnit)
                .filter(price -> Double.compare(price, 0.0) > 0)
                .min(Double::compareTo)
                .orElse(0.0);
    }

    /**
     * Checks if the given instance type is allowed for the authorized user.
     *
     * Also checks if the instance type is one of the offered instance types.
     *
     * @param instanceType To be checked.
     */
    public boolean isInstanceAllowed(final String instanceType) {
        return isInstanceTypeAllowed(instanceType, null,
                Collections.singletonList(SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES.getKey()));
    }

    /**
     * Checks if the given tool instance type is allowed for the authorized user and the requested tool.
     *
     * Also checks if the instance type is one of the offered instance types.
     *
     * @param instanceType To be checked.
     * @param toolResource Optional tool resource.
     */
    public boolean isToolInstanceAllowed(final String instanceType,
                                         final ContextualPreferenceExternalResource toolResource) {
        return isInstanceTypeAllowed(instanceType, toolResource,
                Arrays.asList(SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER.getKey(),
                        SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES.getKey()));
    }

    private boolean isInstanceTypeAllowed(final String instanceType,
                                          final ContextualPreferenceExternalResource resource,
                                          final List<String> instanceTypesPreferences) {
        return !StringUtils.isEmpty(instanceType)
                && isInstanceTypeMatchesAllowedPatterns(instanceType, resource, instanceTypesPreferences)
                && isInstanceTypeOffered(instanceType);
    }

    private boolean isInstanceTypeMatchesAllowedPatterns(final String instanceType,
                                                         final ContextualPreferenceExternalResource resource,
                                                         final List<String> instanceTypesPreferences) {
        return getContextualPreferenceValueAsList(resource, instanceTypesPreferences).stream()
                .anyMatch(pattern -> matcher.match(pattern, instanceType));
    }

    private boolean isInstanceTypeOffered(final String instanceType) {
        return offeredInstanceTypes.get().contains(instanceType);
    }

    /**
     * Checks if the given price type is allowed for the authorized user and tool.
     *
     * @param priceType To be checked.
     * @param toolResource Optional tool resource.
     */
    public boolean isPriceTypeAllowed(final String priceType,
                                      final ContextualPreferenceExternalResource toolResource) {
        final List<String> preferences =
                Collections.singletonList(SystemPreferences.CLUSTER_ALLOWED_PRICE_TYPES.getKey());
        return getContextualPreferenceValueAsList(toolResource, preferences).stream()
                .anyMatch(priceType::equals);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void refreshPriceList() {
        LOGGER.debug(messageHelper.getMessage(MessageConstants.DEBUG_INSTANCE_OFFERS_UPDATE_STARTED));
        instanceOfferDao.removeInstanceOffers();
        List<InstanceOffer> instanceOffers = awsRegionManager.loadAll()
                .stream()
                .map(region -> updatePriceListForRegion(region.getAwsRegionName()))
                .flatMap(List::stream)
                .collect(Collectors.toList());

        updatedInstanceTypesSubject.onNext(getAllInstanceTypes());

        LOGGER.debug(messageHelper.getMessage(MessageConstants.DEBUG_INSTANCE_OFFERS_UPDATE_FINISHED));
        LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_INSTANCE_OFFERS_UPDATED, instanceOffers.size()));
        updateOfferedInstanceTypesAccordingToInstanceOffers(instanceOffers);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<InstanceOffer> updatePriceListForRegion(String awsRegion) {
        String url = String.format(AWS_EC2_PRICING_URL_TEMPLATE, awsRegion);
        try (InputStream input = new URL(url).openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            //skip first lines
            int skipLines = COLUMNS_LINE_INDEX;
            while (skipLines > 0) {
                String line = reader.readLine();
                if (line == null) {
                    LOGGER.debug("AWS Price list for region {} is empty", awsRegion);
                    return Collections.emptyList();
                }
                skipLines--;
            }
            instanceOfferDao.removeInstanceOffersForRegion(awsRegion);
            List<InstanceOffer> instanceOffers = new AwsPriceListReader(awsRegion).readPriceCsv(reader);
            instanceOfferDao.insertInstanceOffers(instanceOffers);
            return instanceOffers;
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private boolean isSpotRequest(Boolean spot) {
        return spot == null ? preferenceManager.getPreference(SystemPreferences.CLUSTER_SPOT) : spot;
    }

    private double getPricePerHourForInstance(String instanceType, boolean isSpot, String awsRegion) {
        return isSpot ? getSpotPricePerHour(instanceType, awsRegion) :
                getPricePerHourForInstance(instanceType, awsRegion);
    }

    private double getSpotPricePerHour(String instanceType, String awsRegion) {
        return ec2Helper.getSpotPrice(instanceType, awsRegion);
    }

    public List<InstanceType> getAllInstanceTypes() {
        InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setTermType(ON_DEMAND_TERM_TYPE);
        requestVO.setOperatingSystem(LINUX_OPERATING_SYSTEM);
        requestVO.setTenancy(SHARED_TENANCY);
        requestVO.setUnit(HOURS_UNIT);
        requestVO.setProductFamily(INSTANCE_PRODUCT_FAMILY);
        return instanceOfferDao.loadInstanceTypes(requestVO);
    }

    public Observable<List<InstanceType>> getAllInstanceTypesObservable() {
        return updatedInstanceTypesSubject;
    }

    private double getPriceForDisk(int instanceDisk, String regionId) {
        InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setProductFamily(STORAGE_PRODUCT_FAMILY);
        requestVO.setVolumeType(GENERAL_PURPOSE_VOLUME_TYPE);
        requestVO.setRegion(regionId);
        List<InstanceOffer> offers = instanceOfferDao.loadInstanceOffers(requestVO);
        if (offers.size() == 1) {
            return offers.get(0).getPricePerUnit() / (DAYS_IN_MONTH * HOURS_IN_DAY) * instanceDisk;
        }
        return 0;
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
     * Returns allowed instance and price types for a current user.
     *
     * @param toolId Optional tool id. If specified than allowed instance and price types will be bounded for a
     *               specific tool.
     */
    public AllowedInstanceAndPriceTypes getAllowedInstanceAndPriceTypes(final String toolId) {
        final ContextualPreferenceExternalResource resource =
                toolId != null
                        ? new ContextualPreferenceExternalResource(ContextualPreferenceLevel.TOOL, toolId)
                        : null;
        final List<InstanceType> instanceTypes = getAllInstanceTypes();
        final List<InstanceType> allowedInstanceTypes = getAllowedInstanceTypes(instanceTypes, resource,
                SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES);
        final List<InstanceType> allowedInstanceDockerTypes = getAllowedInstanceTypes(instanceTypes, resource,
                SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER,
                SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES);
        final List<String> allowedPriceTypes = getContextualPreferenceValueAsList(resource,
                SystemPreferences.CLUSTER_ALLOWED_PRICE_TYPES);
        return new AllowedInstanceAndPriceTypes(allowedInstanceTypes, allowedInstanceDockerTypes, allowedPriceTypes);
    }

    private List<InstanceType> getAllowedInstanceTypes(
            final List<InstanceType> instanceTypes,
            final ContextualPreferenceExternalResource resource,
            final AbstractSystemPreference.StringPreference... preferences) {
        final List<String> allowedInstanceTypePatterns = getContextualPreferenceValueAsList(resource, preferences);
        return instanceTypes.stream()
                .filter(instanceType -> allowedInstanceTypePatterns.stream()
                        .anyMatch(pattern -> matcher.match(pattern, instanceType.getName())))
                .collect(Collectors.toList());
    }

    private List<String> getContextualPreferenceValueAsList(
            final ContextualPreferenceExternalResource resource,
            final AbstractSystemPreference.StringPreference... preferences) {
        final List<String> preferenceNames = Arrays.stream(preferences)
                .map(AbstractSystemPreference::getKey)
                .collect(Collectors.toList());
        return getContextualPreferenceValueAsList(resource, preferenceNames);
    }

    private List<String> getContextualPreferenceValueAsList(final ContextualPreferenceExternalResource resource,
                                                            final List<String> preferences) {
        return Arrays.asList(contextualPreferenceManager.search(preferences, resource).getValue().split(DELIMITER));
    }
}
