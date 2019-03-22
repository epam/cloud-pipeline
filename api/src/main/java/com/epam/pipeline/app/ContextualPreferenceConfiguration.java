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
import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.dao.user.RoleDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.manager.contextual.handler.ArrayContextualPreferenceReducer;
import com.epam.pipeline.manager.contextual.handler.ContextualPreferenceHandler;
import com.epam.pipeline.manager.contextual.handler.ContextualPreferenceReducer;
import com.epam.pipeline.manager.contextual.handler.DefaultContextualPreferenceReducer;
import com.epam.pipeline.manager.contextual.handler.RoleContextualPreferenceHandler;
import com.epam.pipeline.manager.contextual.handler.SystemPreferenceHandler;
import com.epam.pipeline.manager.contextual.handler.ToolContextualPreferenceHandler;
import com.epam.pipeline.manager.contextual.handler.UserContextualPreferenceHandler;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        return new RoleContextualPreferenceHandler(roleDao, contextualPreferenceDao, toolContextualPreferenceHandler,
                defaultContextualPreferenceReducer);
    }

    @Bean
    public ToolContextualPreferenceHandler toolContextualPreferenceHandler(
            final ToolDao toolDao,
            final ContextualPreferenceDao contextualPreferenceDao,
            final SystemPreferenceHandler systemPreferenceHandler) {
        return new ToolContextualPreferenceHandler(toolDao, contextualPreferenceDao, systemPreferenceHandler);
    }

    @Bean
    public SystemPreferenceHandler systemContextualPreferenceHandler(final PreferenceManager preferenceManager) {
        return new SystemPreferenceHandler(preferenceManager);
    }

    @Bean
    public DefaultContextualPreferenceReducer defaultContextualPreferenceReducer(
            final MessageHelper messageHelper,
            final ArrayContextualPreferenceReducer arrayContextualPreferenceReducer) {
        final Map<String, ContextualPreferenceReducer> preferenceReducerMap = new HashMap<>();
        preferenceReducerMap.put(SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES.getKey(),
                arrayContextualPreferenceReducer);
        preferenceReducerMap.put(SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER.getKey(),
                arrayContextualPreferenceReducer);
        return new DefaultContextualPreferenceReducer(messageHelper, preferenceReducerMap);
    }

    @Bean
    public ArrayContextualPreferenceReducer arrayContextualPreferenceReducer() {
        return new ArrayContextualPreferenceReducer();
    }
}
