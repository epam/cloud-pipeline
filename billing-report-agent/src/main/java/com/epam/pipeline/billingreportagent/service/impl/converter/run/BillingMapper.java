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
package com.epam.pipeline.billingreportagent.service.impl.converter.run;

import static com.epam.pipeline.billingreportagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.PipelineRunBillingInfo;
import com.epam.pipeline.billingreportagent.service.EntityMapper;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.search.SearchDocumentType;
import lombok.NoArgsConstructor;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@NoArgsConstructor
public class BillingMapper implements EntityMapper<PipelineRunBillingInfo> {

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);

    @Override
    public XContentBuilder map(final EntityContainer<PipelineRunBillingInfo> container) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            final PipelineRunBillingInfo billingInfo = container.getEntity();
            final PipelineRun run = billingInfo.getPipelineRun();
            jsonBuilder
                .startObject()
                .field("id", run.getId())
                .field(DOC_TYPE_FIELD, SearchDocumentType.PIPELINE_RUN.name())
                .field("cost", billingInfo.getCost())
                .field("date", billingInfo.getDate())
                .field("endDate", parseDataToString(run.getEndDate()))
                .field("pipelineName", run.getPipelineName())
                .field("pipelineVersion", run.getVersion())
                .field("status", run.getStatus())
                .field("dockerImage", run.getDockerImage())
                .field("actualCmd", run.getActualCmd())
                .field("configurationName", run.getConfigName())
                .field("configurationId", run.getConfigurationId())
                .field("pricePerHour", run.getPricePerHour().doubleValue())
                .field("parentRunId", run.getParentRunId())
                .field("nodeCount", run.getNodeCount())
                .field("podId", run.getPodId());

            buildRunInstance(billingInfo.getPipelineRun().getInstance(), jsonBuilder);
            buildPermissions(container.getPermissions(), jsonBuilder);

            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document for pipeline run: ", e);
        }
    }

    private void buildRunInstance(RunInstance instance, XContentBuilder jsonBuilder) throws IOException {
        if (instance == null) {
            return;
        }
        jsonBuilder
                .field("instance")
                .startObject()
                .field("nodeType", instance.getNodeType())
                .field("nodeDisk", instance.getNodeDisk())
                .field("nodeIP", instance.getNodeIP())
                .field("nodeId", instance.getNodeId())
                .field("nodeImage", instance.getNodeImage())
                .field("nodeName", instance.getNodeName())
                .field("priceType", instance.getSpot())
                .field("cloudRegionId", instance.getCloudRegionId())
                .endObject();
    }

    private String parseLocalDataToString(final LocalDate date) {
        if (date == null) {
            return null;
        }
        return dateFormatter.format(date);
    }
}
