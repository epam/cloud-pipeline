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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.dao.datastorage.rules.DataStorageRuleDao;
import com.epam.pipeline.dao.pipeline.PipelineDao;
import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class PipelineCRUDManager {

    @Autowired
    private PipelineDao pipelineDao;
    @Autowired
    private DataStorageRuleDao dataStorageRuleDao;

    @Transactional(propagation = Propagation.REQUIRED)
    public Pipeline save(final Pipeline pipeline) {
        savePipeline(pipeline);
        final DataStorageRule rule = createDefaultDataStorageRule(pipeline, DateUtils.now());
        dataStorageRuleDao.createDataStorageRule(rule);
        return pipeline;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Pipeline savePipeline(final Pipeline pipeline) {
        pipeline.setCreatedDate(DateUtils.now());
        pipelineDao.createPipeline(pipeline);
        return pipeline;
    }

    private DataStorageRule createDefaultDataStorageRule(Pipeline pipeline, Date now) {
        DataStorageRule rule = new DataStorageRule();
        rule.setPipelineId(pipeline.getId());
        rule.setCreatedDate(now);
        rule.setMoveToSts(DataStorageRule.DEFAULT_MOVE_TO_STS);
        rule.setFileMask(DataStorageRule.DEFAULT_FILE_MASK);
        return rule;
    }
}
