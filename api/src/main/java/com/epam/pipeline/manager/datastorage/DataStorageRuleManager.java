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

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.datastorage.rules.DataStorageRuleDao;
import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;

@Service
public class DataStorageRuleManager {

    @Autowired
    private DataStorageRuleDao dataStorageRuleDao;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private PipelineManager pipelineManager;

    @Transactional(propagation = Propagation.REQUIRED)
    public DataStorageRule createRule(DataStorageRule rule) {
        Assert.notNull(rule.getFileMask(), messageHelper
                .getMessage(MessageConstants.ERROR_PARAMETER_REQUIRED, "FileMask",
                        DataStorageRuleManager.class.getSimpleName()));
        Assert.notNull(rule.getPipelineId(), messageHelper
                .getMessage(MessageConstants.ERROR_PARAMETER_REQUIRED, "PipelineId",
                        DataStorageRuleManager.class.getSimpleName()));
        pipelineManager.load(rule.getPipelineId());
        rule.setCreatedDate(DateUtils.now());
        dataStorageRuleDao.createDataStorageRule(rule);
        return rule;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DataStorageRule loadRule(Long pipelineId, String fileMask) {
        DataStorageRule rule = dataStorageRuleDao.loadDataStorageRule(pipelineId, fileMask);
        Assert.notNull(rule, messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_RULE_NOT_FOUND,
                pipelineId, fileMask));
        return rule;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<DataStorageRule> loadAllRules() {
        return dataStorageRuleDao.loadAllDataStorageRules();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<DataStorageRule> loadAllRulesForPipeline(Long pipelineId) {
        pipelineManager.load(pipelineId);
        return dataStorageRuleDao.loadDataStorageRulesForPipeline(pipelineId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<DataStorageRule> loadRules(Long pipelineId, String fileMask) {
        if (pipelineId == null) {
            return loadAllRules();
        } else if (fileMask == null) {
            return loadAllRulesForPipeline(pipelineId);
        } else {
            return Collections.singletonList(loadRule(pipelineId, fileMask));
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DataStorageRule deleteRule(Long pipelineId, String fileMask) {
        pipelineManager.load(pipelineId);
        DataStorageRule rule = loadRule(pipelineId, fileMask);
        dataStorageRuleDao.deleteDataStorageRule(pipelineId, fileMask);
        return rule;
    }
}
