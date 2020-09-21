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

import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoricalAttributeApiService {

    private final CategoricalAttributeManager categoricalAttributesManager;

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public boolean insertAttributesValues(final Map<String, List<String>> dict) {
        return categoricalAttributesManager.insertAttributesValues(dict);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public Map<String, List<String>> loadAll() {
        return categoricalAttributesManager.loadAll();
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public Map<String, List<String>> loadAllValuesForKeys(final List<String> keys) {
        return categoricalAttributesManager.loadAllValuesForKeys(keys);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public boolean deleteAttributeValues(final String key) {
        return categoricalAttributesManager.deleteAttributeValues(key);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public boolean deleteAttributeValue(final String key, final String value) {
        return categoricalAttributesManager.deleteAttributeValue(key, value);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public void syncWithMetadata() {
        categoricalAttributesManager.syncWithMetadata();
    }
}
