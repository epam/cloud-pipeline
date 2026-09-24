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

package com.epam.pipeline.billingreportagent.service.impl.mapper;

import static com.epam.pipeline.billingreportagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.EntityWithMetadata;
import com.epam.pipeline.billingreportagent.model.billing.PipelineRunBillingInfo;
import com.epam.pipeline.billingreportagent.service.AbstractEntityMapper;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.user.PipelineUser;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Getter
public class RunBillingMapper extends AbstractEntityMapper<PipelineRunBillingInfo> {

    private static final int PRICE_SCALE = 5;

    private final String billingCenterKey;

    public RunBillingMapper(@Value("${sync.billing.center.key}") final String billingCenterKey) {
        this.billingCenterKey = billingCenterKey;
    }

    @Override
    public Map<String, ?> map(final EntityContainer<PipelineRunBillingInfo> container) {
        final PipelineRunBillingInfo billingInfo = container.getEntity();
        final PipelineRun run = billingInfo.getEntity().getPipelineRun();

        final Optional<AbstractCloudRegion> region = Optional.ofNullable(container.getRegion());

        final Optional<EntityContainer<Pipeline>> pipelineEntity = Optional.ofNullable(
                billingInfo.getEntity().getPipeline());
        final Optional<Pipeline> pipeline = pipelineEntity.map(EntityContainer::getEntity);
        final Optional<PipelineUser> pipelineOwner = pipelineEntity.map(EntityContainer::getOwner)
                .map(EntityWithMetadata::getEntity);

        final Optional<EntityContainer<Tool>> toolEntity = Optional.ofNullable(
                billingInfo.getEntity().getTool());
        final Optional<Tool> tool = toolEntity.map(EntityContainer::getEntity);
        final Optional<PipelineUser> toolOwner = toolEntity.map(EntityContainer::getOwner)
                .map(EntityWithMetadata::getEntity);

        final Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put(DOC_TYPE_FIELD, SearchDocumentType.PIPELINE_RUN.name());
        jsonMap.put("created_date", asString(billingInfo.getDate())); // Document creation date: 2022-07-22
        jsonMap.put("resource_type", billingInfo.getResourceType()); // Document resource type: COMPUTE / STORAGE
        jsonMap.put("cloudRegionId", region.map(AbstractCloudRegion::getId).orElse(null));
        jsonMap.put("cloud_region_name", region.map(AbstractCloudRegion::getName).orElse(null));
        jsonMap.put("cloud_region_provider", region.map(AbstractCloudRegion::getProvider).orElse(null));

        jsonMap.put("run_id", run.getId());
        jsonMap.put("compute_type", billingInfo.getEntity().getRunType());
        jsonMap.put("instance_type", run.getInstance().getNodeType());

        jsonMap.put("pipeline", run.getPipelineId()); // Pipeline id: 12345
        jsonMap.put("pipeline_name", run.getPipelineName());
        jsonMap.put("pipeline_version", run.getVersion());
        jsonMap.put("pipeline_owner_id", pipelineOwner.map(PipelineUser::getId).orElse(null));
        jsonMap.put("pipeline_owner_name", pipelineOwner.map(PipelineUser::getUserName).orElse(null));
        jsonMap.put("pipeline_created_date", pipeline.map(BaseEntity::getCreatedDate)
                        .map(this::asString)
                        .orElse(null));

        jsonMap.put("tool", run.getDockerImage()); // Docker image full path: registry/group/tool:version
        jsonMap.put("tool_registry_id", tool.map(Tool::getRegistryId).orElse(null));
        jsonMap.put("tool_registry_name", billingInfo.getEntity().getToolAddress().getRegistry());
        jsonMap.put("tool_group_id", tool.map(Tool::getToolGroupId).orElse(null));
        jsonMap.put("tool_group_name", billingInfo.getEntity().getToolAddress().getGroup());
        jsonMap.put("tool_id", tool.map(Tool::getId).orElse(null));
        jsonMap.put("tool_name", billingInfo.getEntity().getToolAddress().getTool());
        jsonMap.put("tool_version", billingInfo.getEntity().getToolAddress().getVersion());
        jsonMap.put("tool_owner_id", toolOwner.map(PipelineUser::getId).orElse(null));
        jsonMap.put("tool_owner_name", toolOwner.map(PipelineUser::getUserName).orElse(null));
        jsonMap.put("tool_created_date", tool.map(BaseEntity::getCreatedDate)
                        .map(this::asString)
                        .orElse(null));

        jsonMap.put("usage_minutes", billingInfo.getUsageMinutes());
        jsonMap.put("paused_minutes", billingInfo.getPausedMinutes());
        jsonMap.put("run_price", run.getPricePerHour().unscaledValue().longValue());
        jsonMap.put("compute_price", scaled(run.getComputePricePerHour()));
        jsonMap.put("disk_price", scaled(run.getDiskPricePerHour()));
        jsonMap.put("cost", billingInfo.getCost());
        jsonMap.put("disk_cost", billingInfo.getDiskCost());
        jsonMap.put("compute_cost", billingInfo.getComputeCost());

        jsonMap.put("started_date", asString(run.getStartDate()));
        jsonMap.put("finished_date", asString(run.getEndDate()));

        buildUserContent(container.getOwner(), jsonMap);
        return jsonMap;
    }

    private long scaled(final BigDecimal price) {
        return Optional.ofNullable(price)
                .map(it -> it.setScale(PRICE_SCALE, RoundingMode.CEILING))
                .map(BigDecimal::unscaledValue)
                .map(BigInteger::longValue)
                .orElse(0L);
    }
}
