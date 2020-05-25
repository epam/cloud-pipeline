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
import com.epam.pipeline.billingreportagent.model.billing.PipelineRunBillingInfo;
import com.epam.pipeline.billingreportagent.service.AbstractEntityMapper;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.search.SearchDocumentType;
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
            jsonBuilder
                .startObject()
                .field(DOC_TYPE_FIELD, SearchDocumentType.PIPELINE_RUN.name())
                .field("run_id", run.getId())
                .field("resource_type", billingInfo.getResourceType())
                .field("pipeline", run.getPipelineId())
                .field("tool", run.getDockerImage())
                .field("instance_type", run.getInstance().getNodeType())
                .field("compute_type", billingInfo.getEntity().getRunType())
                .field("cost", billingInfo.getCost())
                .field("usage_minutes", billingInfo.getUsageMinutes())
                .field("paused_minutes", billingInfo.getPausedMinutes())
                .field("run_price", run.getPricePerHour().unscaledValue().longValue())
                .field("compute_price", scaled(run.getComputePricePerHour()))
                .field("disk_price", scaled(run.getDiskPricePerHour()))
                .field("cloudRegionId", run.getInstance().getCloudRegionId())
                .field("created_date", billingInfo.getDate());
            buildUserContent(container.getOwner(), jsonBuilder);
            jsonBuilder.endObject();
            return jsonBuilder;
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
