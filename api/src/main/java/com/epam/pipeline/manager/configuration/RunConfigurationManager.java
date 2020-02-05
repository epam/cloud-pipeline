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

package com.epam.pipeline.manager.configuration;


import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.configuration.RunConfigurationVO;
import com.epam.pipeline.dao.configuration.RunConfigurationDao;
import com.epam.pipeline.dao.pipeline.RunScheduleDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationProviderManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import com.epam.pipeline.manager.security.acl.AclSync;
import com.epam.pipeline.mapper.AbstractRunConfigurationMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AclSync
public class RunConfigurationManager implements SecuredEntityManager {

    @Autowired
    private RunConfigurationDao runConfigurationDao;

    @Autowired
    private FolderManager folderManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private AbstractRunConfigurationMapper runConfigurationMapper;

    @Autowired
    private ConfigurationProviderManager configurationProvider;

    @Autowired
    private RunScheduleDao runScheduleDao;

    @Override
    public RunConfiguration load(Long id) {
        RunConfiguration configuration = runConfigurationDao.load(id);
        Assert.notNull(configuration,
                messageHelper.getMessage(MessageConstants.ERROR_RUN_CONFIG_NOT_FOUND, id));
        return configuration;
    }

    @Override
    public AbstractSecuredEntity changeOwner(Long id, String owner) {
        RunConfiguration runConfiguration = load(id);
        runConfiguration.setOwner(owner);
        return runConfigurationDao.update(runConfiguration);
    }

    @Override public AclClass getSupportedClass() {
        return AclClass.CONFIGURATION;
    }

    @Override
    public AbstractSecuredEntity loadByNameOrId(String identifier) {
        if (NumberUtils.isDigits(identifier)) {
            RunConfiguration configuration = runConfigurationDao.load(Long.parseLong(identifier));
            if (configuration != null) {
                return configuration;
            }
        }
        //Search by name is not supported for run configuration
        throw new UnsupportedOperationException(messageHelper
                .getMessage(MessageConstants.ERROR_UNSUPPORTED_OPERATION, "run configuration"));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public RunConfiguration create(RunConfigurationVO configuration) {
        RunConfiguration newConfig = runConfigurationMapper.toRunConfiguration(configuration);
        validateConfiguration(newConfig);
        newConfig.setOwner(authManager.getAuthorizedUser());
        return runConfigurationDao.create(newConfig);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public RunConfiguration update(RunConfigurationVO configuration) {
        validateConfiguration(runConfigurationMapper.toRunConfiguration(configuration));
        RunConfiguration dbConfiguration = load(configuration.getId());

        dbConfiguration.setName(configuration.getName());
        dbConfiguration.setDescription(configuration.getDescription());
        dbConfiguration.setEntries(configuration.getEntries());
        if (configuration.getParentId() != null) {
            dbConfiguration.setParent(new Folder(configuration.getParentId()));
        } else {
            dbConfiguration.setParent(null);
        }
        return runConfigurationDao.update(dbConfiguration);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public RunConfiguration delete(Long id) {
        RunConfiguration configuration = load(id);
        runConfigurationDao.delete(id);
        runScheduleDao.deleteRunSchedules(id, ScheduleType.RUN_CONFIGURATION);
        return configuration;
    }

    public List<RunConfiguration> loadAll() {
        return  runConfigurationDao.loadAll();
    }

    public List<RunConfiguration> loadRootConfigurations() {
        return  runConfigurationDao.loadRootEntities();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateLocks(List<Long> configurationIds, boolean isLocked) {
        runConfigurationDao.updateLocks(configurationIds, isLocked);
    }

    @Override
    public Integer loadTotalCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends AbstractSecuredEntity> loadAllWithParents(Integer page, Integer pageSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RunConfiguration loadWithParents(final Long id) {
        return runConfigurationDao.loadConfigurationWithParents(id);
    }

    public void validateConfiguration(RunConfiguration configuration) {
        Assert.notNull(configuration.getName(),
                messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, "name"));
        Assert.isTrue(CollectionUtils.isNotEmpty(configuration.getEntries()),
                messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, "entries"));
        List<String> entryNames = configuration.getEntries()
                .stream()
                .map(AbstractRunConfigurationEntry::getName)
                .collect(Collectors.toList());
        Assert.isTrue(entryNames.size() == new HashSet<>(entryNames).size(),
                messageHelper.getMessage(MessageConstants.ERROR_RUN_CONFIG_DUPLICATES));
        configuration.getEntries().forEach(this::validateEntry);
        if (configuration.getParent() != null) {
            folderManager.load(configuration.getParent().getId());
        }
    }

    private void validateEntry(AbstractRunConfigurationEntry entry) {
        Assert.isTrue(entry.checkConfigComplete(), messageHelper.getMessage(MessageConstants.ERROR_CONFIG_INVALID));
        configurationProvider.validateEntry(entry);
    }
}
