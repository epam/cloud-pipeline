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

import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.user.UserManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class EntityDetailsBillingInfoLoader {

    private static final String OWNER = "owner";
    private static final String NAME = "name";
    private static final String PROVIDER = "provider";
    private static final String BILLING_CENTER = "billing center";
    private final PipelineManager pipelineManager;
    private final ToolManager toolManager;
    private final DataStorageManager dataStorageManager;
    private final UserManager userManager;

    @Autowired
    public EntityDetailsBillingInfoLoader(final PipelineManager pipelineManager,
                                          final ToolManager toolManager,
                                          final DataStorageManager dataStorageManager,
                                          final UserManager userManager) {
        this.pipelineManager = pipelineManager;
        this.toolManager = toolManager;
        this.dataStorageManager = dataStorageManager;
        this.userManager = userManager;
    }

    public Map<String, String> loadDetails(final BillingGrouping groupingType, final String entityIdentifier) {
        final Map<String, String> details = new HashMap<>();
        switch (groupingType) {
            case RUN_COMPUTE_TYPE:
                break;
            case PIPELINE:
                final Pipeline pipeline = pipelineManager.loadByNameOrId(entityIdentifier);
                details.put(NAME, pipeline.getName());
                details.put(OWNER, pipeline.getOwner());
                break;
            case TOOL:
                final Tool tool = toolManager.loadByNameOrId(entityIdentifier);
                details.put(OWNER, tool.getOwner());
                details.put(NAME, tool.getName());
                break;
            case STORAGE:
                final AbstractDataStorage storage = dataStorageManager.loadByNameOrId(entityIdentifier);
                details.put(NAME, storage.getPath());
                details.put(PROVIDER, storage.getType().toString());
                break;
            case USER:
                final PipelineUser user = userManager.loadUserByName(entityIdentifier);
                details.put(BILLING_CENTER, user.getAttributes().getOrDefault("billingCenterKey", "unknown"));
                break;
            default:
                break;
        }
        return details;
    }
}
