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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Slf4j
public class PipelineBillingDetailsLoader implements EntityBillingDetailsLoader {

    @Value("${billing.empty.report.value:unknown}")
    private String emptyValue;

    @Autowired
    private final PipelineManager pipelineManager;

    @Autowired
    private final MessageHelper messageHelper;

    @Override
    public BillingGrouping getGrouping() {
        return BillingGrouping.PIPELINE;
    }

    @Override
    public Map<String, String> loadInformation(final String entityIdentifier, final boolean loadDetails) {
        final Map<String, String> details = new HashMap<>();
        try {
            final Pipeline pipeline = pipelineManager.loadByNameOrId(entityIdentifier);
            details.put(NAME, pipeline.getName());
            if (loadDetails) {
                details.put(OWNER, pipeline.getOwner());
            }
        } catch (RuntimeException e) {
            log.info(messageHelper.getMessage(MessageConstants.INFO_BILLING_ENTITY_FOR_DETAILS_NOT_FOUND,
                                              entityIdentifier, getGrouping()));
            details.put(NAME, entityIdentifier);
            if (loadDetails) {
                details.putAll(getEmptyDetails());
            }
        }
        return details;
    }

    @Override
    public Map<String, String> getEmptyDetails() {
        return Collections.singletonMap(OWNER, emptyValue);
    }
}
