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

package com.epam.pipeline.app;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.contextual.ContextualPreferenceDao;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.dao.user.RoleDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.epam.pipeline.manager.contextual.handler.ArrayContextualPreferenceReducer;
import com.epam.pipeline.manager.contextual.handler.BooleanContextualPreferenceReducer;
import com.epam.pipeline.manager.contextual.handler.ContextualPreferenceHandler;
import com.epam.pipeline.manager.contextual.handler.ContextualPreferenceReducer;
import com.epam.pipeline.manager.contextual.handler.DefaultContextualPreferenceReducer;
import com.epam.pipeline.manager.contextual.handler.RegionContextualPreferenceHandler;
import com.epam.pipeline.manager.contextual.handler.RoleContextualPreferenceHandler;
import com.epam.pipeline.manager.contextual.handler.StorageContextualPreferenceHandler;
import com.epam.pipeline.manager.contextual.handler.SystemPreferenceHandler;
import com.epam.pipeline.manager.contextual.handler.ToolContextualPreferenceHandler;
import com.epam.pipeline.manager.contextual.handler.UserContextualPreferenceHandler;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class ContextualPreferenceConfiguration {

    @Bean
    public ContextualPreferenceHandler contextualPreferenceHandler(
            final UserDao userDao,
            final ContextualPreferenceDao contextualPreferenceDao,
            final RoleContextualPreferenceHandler roleContextualPreferenceHandler) {
        return new UserContextualPreferenceHandler(userDao, contextualPreferenceDao, roleContextualPreferenceHandler);
    }

    @Bean
    public RoleContextualPreferenceHandler roleContextualPreferenceHandler(
            final RoleDao roleDao,
            final ContextualPreferenceDao contextualPreferenceDao,
            final ToolContextualPreferenceHandler toolContextualPreferenceHandler,
            final DefaultContextualPreferenceReducer defaultContextualPreferenceReducer) {
        return new RoleContextualPreferenceHandler(roleDao, contextualPreferenceDao,
                toolContextualPreferenceHandler, defaultContextualPreferenceReducer);
    }

    @Bean
    public ToolContextualPreferenceHandler toolContextualPreferenceHandler(
            final ToolDao toolDao,
            final ContextualPreferenceDao contextualPreferenceDao,
            final StorageContextualPreferenceHandler storageContextualPreferenceHandler) {
        return new ToolContextualPreferenceHandler(toolDao, contextualPreferenceDao,
                storageContextualPreferenceHandler);
    }

    @Bean
    public StorageContextualPreferenceHandler storageContextualPreferenceHandler(
            final DataStorageDao storageDao,
            final ContextualPreferenceDao contextualPreferenceDao,
            final RegionContextualPreferenceHandler regionContextualPreferenceHandler) {
        return new StorageContextualPreferenceHandler(storageDao, contextualPreferenceDao,
                regionContextualPreferenceHandler);
    }

    @Bean
    public RegionContextualPreferenceHandler regionContextualPreferenceHandler(
            final CloudRegionDao cloudRegionDao,
            final ContextualPreferenceDao contextualPreferenceDao,
            final SystemPreferenceHandler systemPreferenceHandler) {
        return new RegionContextualPreferenceHandler(cloudRegionDao, contextualPreferenceDao, systemPreferenceHandler);
    }

    @Bean
    public SystemPreferenceHandler systemContextualPreferenceHandler(final PreferenceManager preferenceManager) {
        return new SystemPreferenceHandler(preferenceManager);
    }

    @Bean
    public DefaultContextualPreferenceReducer defaultContextualPreferenceReducer(
            final MessageHelper messageHelper,
            final ArrayContextualPreferenceReducer arrayContextualPreferenceReducer,
            final BooleanContextualPreferenceReducer booleanContextualPreferenceReducer) {
        final Map<String, ContextualPreferenceReducer> preferenceNameToReducer = new HashMap<>();
        preferenceNameToReducer.put(SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES.getKey(),
                arrayContextualPreferenceReducer);
        preferenceNameToReducer.put(SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER.getKey(),
                arrayContextualPreferenceReducer);
        final Map<PreferenceType, ContextualPreferenceReducer> preferenceTypeToReducer = new HashMap<>();
        preferenceTypeToReducer.put(PreferenceType.BOOLEAN, booleanContextualPreferenceReducer);
        return new DefaultContextualPreferenceReducer(messageHelper, preferenceNameToReducer, preferenceTypeToReducer);
    }

    @Bean
    public ArrayContextualPreferenceReducer arrayContextualPreferenceReducer() {
        return new ArrayContextualPreferenceReducer();
    }

    @Bean
    public BooleanContextualPreferenceReducer booleanContextualPreferenceReducer() {
        return new BooleanContextualPreferenceReducer();
    }
}
