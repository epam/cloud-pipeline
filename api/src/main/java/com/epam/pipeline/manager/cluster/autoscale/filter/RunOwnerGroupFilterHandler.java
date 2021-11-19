/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cluster.autoscale.filter;

import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilterType;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.RunOwnerGroupPoolInstanceFilter;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.user.UserManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.SetUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RunOwnerGroupFilterHandler implements PoolFilterHandler<String, RunOwnerGroupPoolInstanceFilter> {

    private final UserManager userManager;

    @Override
    public boolean matches(final RunOwnerGroupPoolInstanceFilter filter, final PipelineRun run) {
        log.debug("Matching run {} owner groups {} to filter {}.", run.getId(), run.getOwner(), filter);
        return Optional.ofNullable(userManager.loadUserByName(run.getOwner()))
                .map(user -> filter.evaluate(SetUtils.emptyIfNull(user.getAuthorities())))
                .orElse(false);
    }

    @Override
    public PoolInstanceFilterType type() {
        return PoolInstanceFilterType.RUN_OWNER_GROUP;
    }
}
