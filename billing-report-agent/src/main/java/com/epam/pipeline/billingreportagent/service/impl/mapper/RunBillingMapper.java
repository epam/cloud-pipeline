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
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
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
    public XContentBuilder map(final EntityContainer<PipelineRunBillingInfo> container) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
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

            jsonBuilder.startObject()
                .field(DOC_TYPE_FIELD, SearchDocumentType.PIPELINE_RUN.name())
                .field("created_date", billingInfo.getDate()) // Document creation date: 2022-07-22
                .field("resource_type", billingInfo.getResourceType()) // Document resource type: COMPUTE / STORAGE
                .field("cloudRegionId", region.map(AbstractCloudRegion::getId).orElse(null))
                .field("cloud_region_name", region.map(AbstractCloudRegion::getName).orElse(null))
                .field("cloud_region_provider", region.map(AbstractCloudRegion::getProvider).orElse(null))

                .field("run_id", run.getId())
                .field("compute_type", billingInfo.getEntity().getRunType())
                .field("instance_type", run.getInstance().getNodeType())

                .field("pipeline", run.getPipelineId()) // Pipeline id: 12345
                .field("pipeline_name", run.getPipelineName())
                .field("pipeline_version", run.getVersion())
                .field("pipeline_owner_id", pipelineOwner.map(PipelineUser::getId).orElse(null))
                .field("pipeline_owner_name", pipelineOwner.map(PipelineUser::getUserName).orElse(null))
                .field("pipeline_created_date", pipeline.map(BaseEntity::getCreatedDate)
                        .map(this::asString)
                        .orElse(null))

                .field("tool", run.getDockerImage()) // Docker image full path: registry/group/tool:version
                .field("tool_registry_id", tool.map(Tool::getRegistryId).orElse(null))
                .field("tool_registry_name", billingInfo.getEntity().getToolAddress().getRegistry())
                .field("tool_group_id", tool.map(Tool::getToolGroupId).orElse(null))
                .field("tool_group_name", billingInfo.getEntity().getToolAddress().getGroup())
                .field("tool_id", tool.map(Tool::getId).orElse(null))
                .field("tool_name", billingInfo.getEntity().getToolAddress().getTool())
                .field("tool_version", billingInfo.getEntity().getToolAddress().getVersion())
                .field("tool_owner_id", toolOwner.map(PipelineUser::getId).orElse(null))
                .field("tool_owner_name", toolOwner.map(PipelineUser::getUserName).orElse(null))
                .field("tool_created_date", tool.map(BaseEntity::getCreatedDate)
                        .map(this::asString)
                        .orElse(null))

                .field("usage_minutes", billingInfo.getUsageMinutes())
                .field("paused_minutes", billingInfo.getPausedMinutes())
                .field("run_price", run.getPricePerHour().unscaledValue().longValue())
                .field("compute_price", scaled(run.getComputePricePerHour()))
                .field("disk_price", scaled(run.getDiskPricePerHour()))
                .field("cost", billingInfo.getCost())
                .field("disk_cost", billingInfo.getDiskCost())
                .field("compute_cost", billingInfo.getComputeCost())

                .field("started_date", asString(run.getStartDate()))
                .field("finished_date", asString(run.getEndDate()));

            return buildUserContent(container.getOwner(), jsonBuilder)
                .endObject();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document for pipeline run: ", e);
        }
    }

    private long scaled(final BigDecimal price) {
        return Optional.ofNullable(price)
                .map(it -> it.setScale(PRICE_SCALE, RoundingMode.CEILING))
                .map(BigDecimal::unscaledValue)
                .map(BigInteger::longValue)
                .orElse(0L);
    }
}
