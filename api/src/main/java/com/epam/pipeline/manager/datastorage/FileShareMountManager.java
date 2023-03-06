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
import com.epam.pipeline.manager.kube.KubernetesNetworkingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.manager.datastorage.providers.nfs.NFSHelper.findIpAddresses;

@Service
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class FileShareMountManager {
    private final FileShareMountDao fileShareMountDao;
    private final KubernetesNetworkingService kubernetesNetworkingService;

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
        log.debug("Create new FileShareMount: " + fileShareMount);
        Assert.notNull(fileShareMount.getRegionId(), "Region id cannot be null for File share mount!");
        Assert.notNull(fileShareMount.getMountType(), "Mount type cannot be null for File share mount!");
        if (MountType.LUSTRE == fileShareMount.getMountType()) {
            Assert.isTrue(NFSHelper.isValidLustrePath(fileShareMount.getMountRoot()),
                          "Given path for the Lustre file share is invalid!");
        }
        if (fileShareMount.getId() != null) {
            final Optional<FileShareMount> loaded = fileShareMountDao.loadById(fileShareMount.getId());
            if (loaded.isPresent()) {
                update(fileShareMount, loaded.get());
                return fileShareMount;
            }
        }
        fileShareMountDao.create(fileShareMount);
        addIpsToNetworkPolicy(fileShareMount);
        return fileShareMount;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(final Long id) {
        log.debug("Delete FileShareMount with id: " + id);
        fileShareMountDao.loadById(id)
                .ifPresent(fileShare -> {
                    fileShareMountDao.deleteById(id);
                    removeIpsFromNetwork(fileShare);
                });
    }

    private void update(final FileShareMount fileShareMount, final FileShareMount loaded) {
        final boolean needToUpdateIps = !StringUtils.equalsIgnoreCase(loaded.getMountRoot(),
                fileShareMount.getMountRoot());
        if (needToUpdateIps) {
            removeIpsFromNetwork(loaded);
        }

        fileShareMountDao.update(fileShareMount);

        if (needToUpdateIps) {
            addIpsToNetworkPolicy(fileShareMount);
        }
    }

    private void addIpsToNetworkPolicy(final FileShareMount fileShareMount) {
        final List<String> ips = findIpAddresses(fileShareMount);
        if (CollectionUtils.isEmpty(ips)) {
            return;
        }
        try {
            kubernetesNetworkingService.updateEgressIpBlocks(ips);
        } catch (Exception e) {
            log.error("Failed to update network policy", e);
        }
    }

    private void removeIpsFromNetwork(final FileShareMount fileShareMount) {
        final List<String> ips = findIpAddresses(fileShareMount);
        if (CollectionUtils.isEmpty(ips)) {
            return;
        }
        try {
            kubernetesNetworkingService.deleteEgressIpBlocks(ips);
        } catch (Exception e) {
            log.error("Failed to update network policy", e);
        }
    }
}
