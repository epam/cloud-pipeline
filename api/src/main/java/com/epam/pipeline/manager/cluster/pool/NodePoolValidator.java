/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.pool;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.cluster.pool.CapacityReservationRequest;
import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.entity.cluster.AMIConfiguration;
import com.epam.pipeline.entity.cluster.InstanceImage;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.docker.ToolVersionAttributes;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationType;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cloud.aws.capacityreservation.AwsCapacityReservationService;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.capacityreservation.CapacityReservationCloudFacade;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.pipeline.ToolUtils;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.utils.DoubleUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Comparator;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class NodePoolValidator {

    private static final String MAX_SIZE_FIELD = "max size";
    private static final String MIN_SIZE_FIELD = "min size";
    private static final String SCALE_STEP_FIELD = "scale step";
    private static final String UP_THRESHOLD_FIELD = "scale up threshold";
    private static final String DOWN_THRESHOLD_FIELD = "scale down threshold";
    private static final double HUNDRED_PERCENT = 100.0;
    private static final String WINDOWS = "windows";

    private static final int MIN_RESERVATION_LEAD_DAYS = AwsCapacityReservationService.MINIMUM_LEAD_DAYS;
    private static final int MAX_RESERVATION_LEAD_DAYS = 120;
    private static final int MIN_RESERVATION_COMMITMENT_HOURS = 14 * 24;
    private static final int MIN_RESERVATION_VCPUS = 32;

    private static final Set<String> SUPPORTED_INSTANCE_PLATFORMS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("Linux/UNIX", "Red Hat Enterprise Linux", "SUSE Linux")));

    private final MessageHelper messageHelper;
    private final CloudRegionManager regionManager;
    private final InstanceOfferManager instanceOfferManager;
    private final NodeScheduleManager scheduleManager;
    private final ToolManager toolManager;
    private final CloudFacade cloudFacade;
    private final PreferenceManager preferenceManager;
    private final CapacityReservationCloudFacade reservationCloudFacade;

    public void validate(final NodePoolVO vo) {
        Assert.notNull(vo.getRegionId(),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_MISSING_REGION));
        regionManager.load(vo.getRegionId());

        Assert.notNull(vo.getPriceType(),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_MISSING_PRICE_TYPE));
        Assert.isTrue(instanceOfferManager.isPriceTypeAllowed(vo.getPriceType().getLiteral()),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_PRICE_TYPE_NOT_ALLOWED,
                        vo.getPriceType()));

        Assert.isTrue(StringUtils.isNotBlank(vo.getInstanceType()),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_MISSING_INSTANCE_TYPE));
        Assert.isTrue(instanceOfferManager.isToolInstanceAllowed(vo.getInstanceType(), vo.getRegionId(),
                PriceType.SPOT.equals(vo.getPriceType())),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_INSTANCE_TYPE_NOT_ALLOWED,
                        vo.getInstanceType()));

        Assert.isTrue(vo.getInstanceDisk() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_INVALID_DISK_SIZE));

        Assert.isTrue(vo.getCount() >= 0,
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_INVALID_COUNT));

        Optional.ofNullable(vo.getScheduleId())
                .ifPresent(scheduleManager::load);

        Optional.ofNullable(vo.getDockerImages())
                .ifPresent(images -> images.forEach(this::validatePoolImage));

        validateInstanceImage(vo.getRegionId(), vo.getInstanceImage());

        if (vo.isAutoscaled()) {
            validateAutoscalingParams(vo);
        }
        Optional.ofNullable(vo.getCapacityReservationRequest())
                .ifPresent(request -> validateCapacityReservation(vo, request));
    }

    @SuppressWarnings("deprecation")
    public void validateReservationPoolUpdate(final NodePool existing, final NodePoolVO vo) {
        validateUnchanged(existing, "count", existing.getCount(), vo.getCount());
        validateUnchanged(existing, "price type", existing.getPriceType(), vo.getPriceType());
        Assert.isTrue(!vo.isAutoscaled(),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_AUTOSCALING_NOT_SUPPORTED));
        if (StringUtils.isNotBlank(vo.getInstanceImage())) {
            validateUnchanged(existing, "instance image", existing.getInstanceImage(), vo.getInstanceImage());
        }
        if (vo.getAmiConfiguration() != null) {
            validateUnchanged(existing, "launch configuration", existing.getAmiConfiguration(),
                    vo.getAmiConfiguration());
        }
    }

    private void validateUnchanged(final NodePool pool, final String field, final Object current,
                                   final Object requested) {
        Assert.isTrue(Objects.equals(current, requested),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_POOL_IMMUTABLE,
                        pool.getId(), field));
    }

    private void validateCapacityReservation(final NodePoolVO vo, final CapacityReservationRequest request) {
        final CloudProvider provider = regionManager.load(vo.getRegionId()).getProvider();
        Assert.isTrue(reservationCloudFacade.isSupported(provider),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_PROVIDER_NOT_SUPPORTED,
                        provider));
        Assert.isTrue(!PriceType.SPOT.equals(vo.getPriceType()),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_SPOT_NOT_SUPPORTED));
        Assert.isTrue(!vo.isAutoscaled(),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_AUTOSCALING_NOT_SUPPORTED));

        final CapacityReservationType type = request.getReservationType();
        Assert.notNull(type,
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_TYPE_REQUIRED));
        Assert.isTrue(CapacityReservationType.FUTURE_DATED == type,
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_TYPE_NOT_SUPPORTED, type));

        Assert.isTrue(vo.getCount() >= 1,
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_INSTANCE_COUNT_INVALID));

        validateReservationSize(vo);
        validateReservationWindow(request);
        validateReservationInstance(vo, request);
    }

    private void validateReservationSize(final NodePoolVO vo) {
        final int instances = vo.getCount();
        instanceOfferManager.findOffer(vo.getInstanceType(), vo.getRegionId())
                .map(InstanceOffer::getVCPU)
                .filter(vcpus -> vcpus > 0)
                .ifPresent(vcpus -> Assert.isTrue((long) vcpus * instances >= MIN_RESERVATION_VCPUS,
                        messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_TOO_FEW_VCPUS,
                                instances, vo.getInstanceType(), vcpus, MIN_RESERVATION_VCPUS)));
    }

    private void validateReservationInstance(final NodePoolVO vo, final CapacityReservationRequest request) {
        final String platform = StringUtils.defaultIfBlank(request.getInstancePlatform(),
                CapacityReservation.DEFAULT_INSTANCE_PLATFORM);
        Assert.isTrue(SUPPORTED_INSTANCE_PLATFORMS.contains(platform),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_PLATFORM_NOT_SUPPORTED,
                        platform));

        final String family = instanceFamily(vo.getInstanceType());
        final Set<String> supportedFamilies = supportedInstanceFamilies();
        Assert.isTrue(supportedFamilies.isEmpty() || supportedFamilies.contains(family),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_FAMILY_NOT_SUPPORTED,
                        vo.getInstanceType(), String.join(", ", supportedFamilies)));
    }

    private static String instanceFamily(final String instanceType) {
        if (StringUtils.isBlank(instanceType)) {
            return StringUtils.EMPTY;
        }
        final int firstDigit = StringUtils.indexOfAny(instanceType, "0123456789".split(StringUtils.EMPTY));
        return (firstDigit < 0 ? instanceType : instanceType.substring(0, firstDigit)).toLowerCase(Locale.ROOT);
    }

    private Set<String> supportedInstanceFamilies() {
        return Arrays.stream(StringUtils.split(StringUtils.defaultString(preferenceManager.getPreference(
                                SystemPreferences.CLUSTER_CAPACITY_RESERVATION_INSTANCE_FAMILIES)), ','))
                .map(family -> family.trim().toLowerCase(Locale.ROOT))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    private void validateReservationWindow(final CapacityReservationRequest request) {
        final LocalDateTime start = request.getRequestedStartDate();
        final LocalDateTime end = request.getRequestedEndDate();
        Assert.isTrue(Objects.nonNull(start) && Objects.nonNull(end),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_DATES_REQUIRED,
                        request.getReservationType()));
        Assert.isTrue(start.isAfter(DateUtils.nowUTC()),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_START_DATE_IN_PAST,
                        start));
        Assert.isTrue(end.isAfter(start),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_WINDOW_INVALID,
                        end, start));

        final Integer duration = request.getDurationHours();
        Assert.isTrue(Objects.nonNull(duration) && duration > 0,
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_DURATION_INVALID));
        Assert.isTrue(!start.plusHours(duration).isAfter(end),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_WINDOW_TOO_NARROW,
                        start, end, duration));

        validateProviderLimits(start, duration);
    }

    private void validateProviderLimits(final LocalDateTime start, final int duration) {
        final long leadDays = Duration.between(DateUtils.nowUTC(), start).toDays();
        Assert.isTrue(leadDays >= MIN_RESERVATION_LEAD_DAYS && leadDays <= MAX_RESERVATION_LEAD_DAYS,
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_LEAD_TIME_INVALID,
                        start, MIN_RESERVATION_LEAD_DAYS, MAX_RESERVATION_LEAD_DAYS));

        Assert.isTrue(duration >= MIN_RESERVATION_COMMITMENT_HOURS,
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_COMMITMENT_TOO_SHORT,
                        duration, MIN_RESERVATION_COMMITMENT_HOURS));
    }

    private void validateInstanceImage(final Long regionId, final String instanceImage) {
        final boolean isWindowsInstanceImage = Optional.ofNullable(instanceImage)
            .filter(StringUtils::isNotBlank)
            .map(image -> cloudFacade.getInstanceImageDescription(regionId, image))
            .map(InstanceImage::getPlatform)
            .filter(WINDOWS::equalsIgnoreCase)
            .isPresent();
        Assert.isTrue(!isWindowsInstanceImage,
                      messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_WIN_INSTANCES_ARE_NOT_ALLOWED));
    }

    /**
     * A launch configuration given for a pool. Its image has to be one a pool may launch, and on AWS its zone and
     * subnet ones its region's networks configure.
     */
    public void validateAmiConfiguration(final Long regionId, final AMIConfiguration configuration) {
        if (configuration == null) {
            return;
        }
        validateInstanceImage(regionId, configuration.getAmi());
        final AbstractCloudRegion region = regionManager.load(regionId);
        if (CloudProvider.AWS != region.getProvider()) {
            return;
        }
        final String regionCode = region.getRegionCode();
        final Map<String, String> networks = allowedNetworks(regionCode);
        if (networks.isEmpty()) {
            return;
        }
        final String zone = configuration.getAvailabilityZone();
        if (StringUtils.isNotBlank(zone)) {
            Assert.isTrue(networks.containsKey(zone),
                    messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_ZONE_NOT_CONFIGURED,
                            zone, regionCode, networks.keySet()));
        }
        final String subnet = configuration.getSubnet();
        if (StringUtils.isBlank(subnet)) {
            return;
        }
        if (StringUtils.isBlank(zone)) {
            Assert.isTrue(networks.containsValue(subnet),
                    messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_SUBNET_NOT_IN_NETWORKS,
                            subnet, regionCode, networks.values()));
            return;
        }
        Assert.isTrue(subnet.equals(networks.get(zone)),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_SUBNET_NOT_CONFIGURED,
                        subnet, regionCode, zone, networks.get(zone)));
    }

    private void validatePoolImage(final String image) {
        final boolean poolContainsWindowsImages = Optional.of(toolManager.loadByNameOrId(image))
            .map(tool -> toolManager.loadToolVersionAttributes(tool.getId(), ToolUtils.getImageTag(tool.getImage())))
            .map(ToolVersionAttributes::getAttributes)
            .map(ToolVersion::getPlatform)
            .filter(WINDOWS::equalsIgnoreCase)
            .isPresent();
        Assert.isTrue(!poolContainsWindowsImages,
                      messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_WIN_TOOLS_ARE_NOT_ALLOWED));
    }

    private void validateAutoscalingParams(final NodePoolVO vo) {
        validatePositiveInt(vo.getMaxSize(), MAX_SIZE_FIELD);
        validatePositiveInt(vo.getMinSize(), MIN_SIZE_FIELD);
        validatePositiveInt(vo.getScaleStep(), SCALE_STEP_FIELD);
        validateFieldIsLessThanOnOther(Integer::compare, vo.getMinSize(), MIN_SIZE_FIELD,
                vo.getMaxSize(), MAX_SIZE_FIELD);
        validatePercentValue(vo.getScaleDownThreshold(), DOWN_THRESHOLD_FIELD);
        validatePercentValue(vo.getScaleUpThreshold(), UP_THRESHOLD_FIELD);
        validateFieldIsLessThanOnOther(DoubleUtils::compare, vo.getScaleDownThreshold(), DOWN_THRESHOLD_FIELD,
                vo.getScaleUpThreshold(),  UP_THRESHOLD_FIELD);
    }

    private void validatePercentValue(final Double value, final String fieldName) {
        Assert.isTrue(Objects.nonNull(value) && validPercentValue(value),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_INVALID_PERCENT, fieldName, value));
    }

    private <T> void validateFieldIsLessThanOnOther(final Comparator<T> comparator,
                                                    final T low,
                                                    final String lowName,
                                                    final T up,
                                                    final String upName) {
        Assert.isTrue(comparator.compare(low, up) < 0,
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_FIELDS_COMPARE,
                        lowName, upName, low, up));
    }

    private void validatePositiveInt(final Integer value, final String fieldName) {
        Assert.isTrue(Objects.nonNull(value) && value > 0,
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_POSITIVE_INT_REQUIRED,
                        fieldName, value));
    }

    private boolean validPercentValue(double value) {
        return DoubleUtils.between(0.0, HUNDRED_PERCENT, value);
    }

    private Map<String, String> allowedNetworks(final String regionCode) {
        return Optional.ofNullable(preferenceManager.getPreference(SystemPreferences.CLUSTER_NETWORKS_CONFIG))
                .map(configuration -> configuration.allowedNetworks(regionCode))
                .orElseGet(Collections::emptyMap);
    }
}
