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
import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.utils.DoubleUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NodePoolValidator {

    private static final String MAX_SIZE_FIELD = "max size";
    private static final String MIN_SIZE_FIELD = "min size";
    private static final String SCALE_STEP_FIELD = "scale step";
    private static final String UP_THRESHOLD_FIELD = "scale up threshold";
    private static final String DOWN_THRESHOLD_FIELD = "scale down threshold";
    private static final double HUNDRED_PERCENT = 100.0;

    private final MessageHelper messageHelper;
    private final CloudRegionManager regionManager;
    private final InstanceOfferManager instanceOfferManager;
    private final NodeScheduleManager scheduleManager;
    private final ToolManager toolManager;

    public void validate(final NodePoolVO vo) {
        Assert.notNull(vo.getRegionId(),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_MISSING_REGION));
        regionManager.load(vo.getRegionId());

        Assert.notNull(vo.getPriceType(),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_MISSING_PRICE_TYPE));
        Assert.isTrue(instanceOfferManager.isPriceTypeAllowed(vo.getPriceType().getLiteral(), null),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_PRICE_TYPE_NOT_ALLOWED,
                        vo.getPriceType()));

        Assert.isTrue(StringUtils.isNotBlank(vo.getInstanceType()),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_MISSING_INSTANCE_TYPE));
        Assert.isTrue(instanceOfferManager.isToolInstanceAllowed(vo.getInstanceType(), null, vo.getRegionId(),
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
                .ifPresent(images -> images.forEach(toolManager::loadByNameOrId));

        if (vo.isAutoscaled()) {
            validateAutoscalingParams(vo);
        }
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
}
