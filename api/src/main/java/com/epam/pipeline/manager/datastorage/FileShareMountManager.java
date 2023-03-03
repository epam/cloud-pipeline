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

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.dao.datastorage.FileShareMountDao;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.MountType;
import com.epam.pipeline.manager.datastorage.providers.nfs.NFSHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;

@Service
public class FileShareMountManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileShareMountManager.class);

    @Autowired
    private FileShareMountDao fileShareMountDao;

    public FileShareMount load(final Long id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("There is no FileShare mount with id:" + id));
    }

    public Optional<FileShareMount> find(final Long id) {
        return fileShareMountDao.loadById(id);
    }

    public List<FileShareMount> loadByRegionId(final Long regionId) {
        return fileShareMountDao.loadAllByRegionId(regionId);
    }

    public List<FileShareMount> loadAll() {
        return fileShareMountDao.loadAll();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public FileShareMount save(final FileShareMount fileShareMount) {
        LOGGER.debug("Create new FileShareMount: " + fileShareMount);
        Assert.notNull(fileShareMount.getRegionId(), "Region id cannot be null for File share mount!");
        Assert.notNull(fileShareMount.getMountType(), "Mount type cannot be null for File share mount!");
        if (MountType.LUSTRE == fileShareMount.getMountType()) {
            Assert.isTrue(NFSHelper.isValidLustrePath(fileShareMount.getMountRoot()),
                          "Given path for the Lustre file share is invalid!");
        }
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
