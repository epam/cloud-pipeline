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

package com.epam.pipeline.manager.cluster.cleaner;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.exception.datastorage.exception.LustreFSException;
import com.epam.pipeline.manager.datastorage.lustre.LustreFSManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LustreRunCleaner implements RunCleaner {

    private static final String SHARED_FS_ENV_VAR = "CP_CAP_SHARE_FS_TYPE";
    private static final String LUSTRE_TYPE = "lustre";

    private final LustreFSManager lustreFSManager;

    @Override
    public void cleanResources(final PipelineRun run) {
        if (!isLustreRequested(run)) {
            return;
        }
        log.debug("Clearing lustre fs for run {}.", run.getId());
        try {
            lustreFSManager.deleteLustreFs(run.getId());
        } catch (LustreFSException e) {
            log.error("Failed to clean up lustre for run {}.", run.getId());
            log.error(e.getMessage(), e);
        }
    }

    public boolean isLustreRequested(PipelineRun run) {
        return ListUtils.emptyIfNull(run.getPipelineRunParameters()).stream()
                .anyMatch(param -> SHARED_FS_ENV_VAR.equals(param.getName()) && LUSTRE_TYPE.equals(param.getValue()));
    }
}
