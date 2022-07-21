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
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.search.SearchDocumentType;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Getter
public class RunBillingMapper extends AbstractEntityMapper<PipelineRunBillingInfo> {

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat(Constants.FMT_ISO_LOCAL_DATE);
    private static final Pattern TOOL_PATTERN = Pattern.compile("^(.*)\\/(.*)\\/(.*):(.*)$");
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
            final Optional<Matcher> tool = Optional.ofNullable(run.getDockerImage())
                    .filter(StringUtils::isNotBlank)
                    .map(TOOL_PATTERN::matcher)
                    .filter(Matcher::find);

            jsonBuilder.startObject()
                .field(DOC_TYPE_FIELD, SearchDocumentType.PIPELINE_RUN.name())
                .field("resource_type", billingInfo.getResourceType())
                .field("cloudRegionId", container.getRegion().getId())
                .field("cloud_region_name", container.getRegion().getName())

                .field("run_id", run.getId())
                .field("compute_type", billingInfo.getEntity().getRunType())
                .field("instance_type", run.getInstance().getNodeType())

                .field("pipeline", run.getPipelineId())
                .field("pipeline_name", run.getPipelineName())
                .field("pipeline_version", run.getVersion())

                .field("tool", run.getDockerImage())
                .field("tool_registry", tool.map(toGroup(1)).orElse(null))
                .field("tool_group", tool.map(toGroup(2)).orElse(null))
                .field("tool_name", tool.map(toGroup(3)).orElse(null))
                .field("tool_version", tool.map(toGroup(4)).orElse(null))

                .field("usage_minutes", billingInfo.getUsageMinutes())
                .field("paused_minutes", billingInfo.getPausedMinutes())
                .field("run_price", run.getPricePerHour().unscaledValue().longValue())
                .field("compute_price", scaled(run.getComputePricePerHour()))
                .field("disk_price", scaled(run.getDiskPricePerHour()))
                .field("cost", billingInfo.getCost())

                .field("created_date", billingInfo.getDate())
                .field("started_date", asString(run.getStartDate()))
                .field("finished_date", asString(run.getEndDate()));

            return buildUserContent(container.getOwner(), jsonBuilder)
                .endObject();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document for pipeline run: ", e);
        }
    }

    private Function<Matcher, String> toGroup(final int group) {
        return matcher -> matcher.group(group);
    }

    private long scaled(final BigDecimal price) {
        return Optional.ofNullable(price)
                .map(it -> it.setScale(PRICE_SCALE, RoundingMode.CEILING))
                .map(BigDecimal::unscaledValue)
                .map(BigInteger::longValue)
                .orElse(0L);
    }

    private String asString(final Date date) {
        return Optional.ofNullable(date).map(SIMPLE_DATE_FORMAT::format).orElse(null);
    }
}
