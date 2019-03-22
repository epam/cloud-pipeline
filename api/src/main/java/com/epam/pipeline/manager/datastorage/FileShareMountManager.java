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

import com.epam.pipeline.dao.datastorage.FileShareMountDao;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;

@Service
public class FileShareMountManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileShareMountManager.class);

    @Autowired
    private FileShareMountDao fileShareMountDao;

    public FileShareMount load(final Long id) {
        return fileShareMountDao.loadById(id)
                .orElseThrow(() -> new IllegalArgumentException("There is no FileShare mount with id:" + id));
    }

    public List<FileShareMount> loadByRegionId(final Long regionId) {
        return fileShareMountDao.loadAllByRegionId(regionId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public FileShareMount save(final FileShareMount fileShareMount) {
        LOGGER.debug("Create new FileShareMount: " + fileShareMount);
        Assert.notNull(fileShareMount.getRegionId(), "Region id cannot be null for File share mount!");
        Assert.notNull(fileShareMount.getMountType(), "Mount type cannot be null for File share mount!");
        if (fileShareMount.getId() != null && fileShareMountDao.loadById(fileShareMount.getId()).isPresent()) {
            fileShareMountDao.update(fileShareMount);
            return fileShareMount;
        }
        return fileShareMountDao.create(fileShareMount);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(final Long id) {
        LOGGER.debug("Delete FileShareMount with id: " + id);
        fileShareMountDao.deleteById(id);
    }

}
