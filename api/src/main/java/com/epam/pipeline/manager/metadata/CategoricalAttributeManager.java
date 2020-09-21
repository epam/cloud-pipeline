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

package com.epam.pipeline.manager.metadata;

import com.epam.pipeline.dao.metadata.CategoricalAttributeDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoricalAttributeManager {

    private final CategoricalAttributeDao categoricalAttributesDao;
    private final MetadataManager metadataManager;

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean insertAttributesValues(final Map<String, List<String>> dict) {
        return categoricalAttributesDao.insertAttributesValues(dict);
    }

    public Map<String, List<String>> loadAll() {
        return categoricalAttributesDao.loadAll();
    }

    public Map<String, List<String>> loadAllValuesForKeys(final List<String> keys) {
        return categoricalAttributesDao.loadAllValuesForKeys(keys);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean deleteAttributeValues(final String key) {
        return categoricalAttributesDao.deleteAttributeValues(key);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean deleteAttributeValue(final String key, final String value) {
        return categoricalAttributesDao.deleteAttributeValue(key, value);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void syncWithMetadata() {
        final Map<String, List<String>> fullMetadataDict = metadataManager.buildFullMetadataDict();
        insertAttributesValues(fullMetadataDict);
    }
}
