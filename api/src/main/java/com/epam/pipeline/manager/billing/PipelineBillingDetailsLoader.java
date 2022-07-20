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

package com.epam.pipeline.manager.billing;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.utils.CommonUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Slf4j
public class PipelineBillingDetailsLoader implements EntityBillingDetailsLoader {

    @Getter
    private final BillingGrouping grouping = BillingGrouping.PIPELINE;

    private final PipelineManager pipelineManager;
    private final UserBillingDetailsLoader userBillingDetailsLoader;
    private final MessageHelper messageHelper;
    private final String emptyValue;

    public PipelineBillingDetailsLoader(final PipelineManager pipelineManager,
                                        final UserBillingDetailsLoader userBillingDetailsLoader,
                                        final MessageHelper messageHelper,
                                        @Value("${billing.empty.report.value:unknown}")
                                        final String emptyValue) {
        this.pipelineManager = pipelineManager;
        this.userBillingDetailsLoader = userBillingDetailsLoader;
        this.messageHelper = messageHelper;
        this.emptyValue = emptyValue;
    }

    @Override
    public Map<String, String> loadInformation(final String id, final boolean loadDetails,
                                               final Map<String, String> defaults) {
        final Optional<Pipeline> pipeline = load(id);

        final Map<String, String> details = new HashMap<>();
        details.put(NAME, CommonUtils.first(defaults, "pipeline_name")
                .map(Optional::of)
                .orElseGet(() -> pipeline.map(Pipeline::getName))
                .orElse(id));
        details.put(OWNER, CommonUtils.first(defaults, "owner_user_name", "owner")
                .map(Optional::of)
                .orElseGet(() -> pipeline.map(Pipeline::getOwner))
                .orElse(emptyValue));
        details.put(BILLING_CENTER, CommonUtils.first(defaults, "owner_billing_center", "billing_center")
                .map(Optional::of)
                .orElseGet(() -> pipeline.map(Pipeline::getOwner)
                        .flatMap(userBillingDetailsLoader::getUserBillingCenter))
                .orElse(emptyValue));
        details.put(IS_DELETED, Boolean.toString(!pipeline.isPresent()));
        return details;
    }

    private Optional<Pipeline> load(final String id) {
        try {
            return Optional.of(pipelineManager.loadByNameOrIdWithoutVersion(id));
        } catch (RuntimeException e) {
            log.info(messageHelper.getMessage(MessageConstants.INFO_BILLING_ENTITY_FOR_DETAILS_NOT_FOUND,
                    id, getGrouping()));
            return Optional.empty();
        }
    }
}
