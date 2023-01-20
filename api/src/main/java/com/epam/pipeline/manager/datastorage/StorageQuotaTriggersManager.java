/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.dao.datastorage.StorageQuotaTriggersDao;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StorageQuotaTriggersManager {

    private final StorageQuotaTriggersDao storageQuotaTriggersDao;

    @Transactional(propagation = Propagation.REQUIRED)
    public void insert(final NFSQuotaTrigger triggerEntry) {
        if (storageQuotaTriggersDao.find(triggerEntry.getStorageId()).isPresent()) {
            storageQuotaTriggersDao.update(triggerEntry);
        } else {
            storageQuotaTriggersDao.create(triggerEntry);
        }
    }

    public List<NFSQuotaTrigger> loadAll() {
        return storageQuotaTriggersDao.loadAll();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(final NFSQuotaTrigger triggerEntry) {
        storageQuotaTriggersDao.delete(triggerEntry.getStorageId());
    }
}
