/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.contextual.handler;

import com.epam.pipeline.dao.contextual.ContextualPreferenceDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.region.AbstractCloudRegion;

/**
 * Region contextual preference handler.
 * It handles preferences with {@link ContextualPreferenceLevel#REGION} level.
 * Handler associates preference with a single {@link AbstractCloudRegion} entity.
 */
public class RegionContextualPreferenceHandler extends AbstractDaoContextualPreferenceHandler{

    private final CloudRegionDao cloudRegionDao;

    public RegionContextualPreferenceHandler(final CloudRegionDao cloudRegionDao,
                                             final ContextualPreferenceDao contextualPreferenceDao,
                                             final ContextualPreferenceHandler nextHandler) {
        super(ContextualPreferenceLevel.REGION, nextHandler, contextualPreferenceDao);
        this.cloudRegionDao = cloudRegionDao;
    }

    @Override
    boolean externalEntityExists(final ContextualPreference preference) {
        return cloudRegionDao.loadById(Long.parseLong(preference.getResource().getResourceId())).isPresent();
    }
}
