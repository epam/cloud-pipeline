/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.capacityreservation;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.cluster.capacityreservation.CapacityBlockOfferingRequest;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockInstanceTypeInfo;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockOffering;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockResourceType;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

/**
 * Lets a user search what AWS currently has for sale as capacity blocks, before any node pool or reservation
 * request exists to search on behalf of.
 *
 * <p>{@link #listOfferings} answers a concrete search - instance type, count, duration and the date to start
 * looking from - with the exact offerings, zones and up-front prices AWS would sell today. {@link #listInstanceTypes}
 * answers the question a search needs answered first: which instance types a region even sells as capacity blocks,
 * since most instance types never are.
 *
 * <p>Only {@link CapacityBlockResourceType#INSTANCE} is accepted; {@link CapacityBlockResourceType#ULTRASERVER} is
 * rejected here, before either reaches a provider.
 */
@Service
@RequiredArgsConstructor
public class CapacityBlockOfferingService {

    private final CloudRegionManager regionManager;
    private final CapacityReservationCloudFacade cloudFacade;
    private final MessageHelper messageHelper;

    public List<CapacityBlockOffering> listOfferings(final CapacityBlockOfferingRequest request) {
        validate(request);
        final AbstractCloudRegion region = regionManager.load(request.getRegionId());
        return cloudFacade.listCapacityBlockOfferings(region, request);
    }

    public List<CapacityBlockInstanceTypeInfo> listInstanceTypes(final Long regionId) {
        Assert.notNull(regionId,
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_BLOCK_OFFERING_REGION_REQUIRED));
        final AbstractCloudRegion region = regionManager.load(regionId);
        return cloudFacade.listCapacityBlockInstanceTypes(region);
    }

    private void validate(final CapacityBlockOfferingRequest request) {
        Assert.notNull(request.getRegionId(),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_BLOCK_OFFERING_REGION_REQUIRED));
        Assert.isTrue(StringUtils.isNotBlank(request.getInstanceType()),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_BLOCK_OFFERING_INSTANCE_TYPE_REQUIRED));
        Assert.isTrue(request.getInstanceCount() != null && request.getInstanceCount() >= 1,
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_BLOCK_OFFERING_INSTANCE_COUNT_REQUIRED));
        Assert.notNull(request.getCapacityDurationHours(),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_BLOCK_OFFERING_DURATION_REQUIRED));
        Assert.notNull(request.getStartDateRange(),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_BLOCK_OFFERING_START_DATE_REQUIRED));

        final CapacityBlockResourceType resourceType = request.getResourceType();
        Assert.isTrue(resourceType == null || CapacityBlockResourceType.INSTANCE == resourceType,
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_BLOCK_RESOURCE_TYPE_NOT_SUPPORTED,
                        resourceType));
    }
}
