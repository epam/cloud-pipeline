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

package com.epam.pipeline.manager.dts;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.dts.DtsRegistryVO;
import com.epam.pipeline.dao.dts.DtsRegistryDao;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.mapper.DtsRegistryMapper;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;

/**
 * {@link DtsRegistryManager} provides CRUD methods for Data Transfer Service registry.
 */
@Service
@AllArgsConstructor
public class DtsRegistryManager {
    private DtsRegistryDao dtsRegistryDao;
    private DtsRegistryMapper dtsRegistryMapper;
    private MessageHelper messageHelper;

    /**
     * Loads all existing {@link DtsRegistry}s.
     * @return list of existing {@link DtsRegistry}s
     */
    public List<DtsRegistry> loadAll() {
        return dtsRegistryDao.loadAll();
    }

    /**
     * Loads {@link DtsRegistry} specified by ID.
     * @param registryId a {@link DtsRegistry} ID
     * @return existing {@link DtsRegistry} or error if required registry does not exist.
     */
    public DtsRegistry load(Long registryId) {
        validateDtsRegistryId(registryId);
        return loadOrThrow(registryId);
    }

    /**
     * Creates a new {@link DtsRegistry}.
     * @param dtsRegistryVO a {@link DtsRegistryVO} to create
     * @return created {@link DtsRegistry}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public DtsRegistry create(DtsRegistryVO dtsRegistryVO) {
        validateDtsRegistryVO(dtsRegistryVO);
        DtsRegistry dtsRegistry = dtsRegistryMapper.toDtsRegistry(dtsRegistryVO);
        return dtsRegistryDao.create(dtsRegistry);
    }

    /**
     * Updates a {@link DtsRegistry} specified by ID. If required {@link DtsRegistry} does not exist an error will be
     * thrown.
     * @param registryId a {@link DtsRegistry} ID to update
     * @param dtsRegistryVO a {@link DtsRegistryVO} to update
     * @return updated {@link DtsRegistry}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public DtsRegistry update(Long registryId, DtsRegistryVO dtsRegistryVO) {
        validateDtsRegistryId(registryId);
        validateDtsRegistryVO(dtsRegistryVO);
        loadOrThrow(registryId);
        DtsRegistry dtsRegistry = dtsRegistryMapper.toDtsRegistry(dtsRegistryVO);
        dtsRegistry.setId(registryId);
        dtsRegistryDao.update(dtsRegistry);
        return dtsRegistry;
    }

    /**
     * Deletes a {@link DtsRegistry} specified by ID. If required {@link DtsRegistry} does not exist an error will be
     * thrown.
     * @param registryId a {@link DtsRegistry} ID to delete
     * @return deleted {@link DtsRegistry}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public DtsRegistry delete(Long registryId) {
        validateDtsRegistryId(registryId);
        DtsRegistry dtsRegistry = loadOrThrow(registryId);
        dtsRegistryDao.delete(registryId);
        return dtsRegistry;
    }

    private DtsRegistry loadOrThrow(Long registryId) {
        return dtsRegistryDao.loadById(registryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_DTS_REGISTRY_DOES_NOT_EXIST, registryId)));
    }

    private void validateDtsRegistryVO(DtsRegistryVO dtsRegistryVO) {
        Assert.notNull(dtsRegistryVO, messageHelper.getMessage(MessageConstants.ERROR_DTS_REGISTRY_IS_EMPTY));
        Assert.state(StringUtils.isNotBlank(dtsRegistryVO.getUrl()),
                messageHelper.getMessage(MessageConstants.ERROR_DTS_REGISTRY_URL_IS_EMPTY));
        Assert.state(CollectionUtils.isNotEmpty(dtsRegistryVO.getPrefixes()),
                messageHelper.getMessage(MessageConstants.ERROR_DTS_REGISTRY_PREFIXES_ARE_EMPTY));
        Assert.state(StringUtils.isNotBlank(dtsRegistryVO.getName()),
                messageHelper.getMessage(MessageConstants.ERROR_DTS_REGISTRY_NAME_IS_EMPTY));
    }

    private void validateDtsRegistryId(Long registryId) {
        Assert.notNull(registryId, messageHelper.getMessage(MessageConstants.ERROR_DTS_REGISTRY_ID_IS_EMPTY));
    }
}
