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

package com.epam.pipeline.manager.contextual;

import com.epam.pipeline.controller.vo.ContextualPreferenceVO;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.security.acl.AclExpressions;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContextualPreferenceApiService {

    private final ContextualPreferenceManager contextualPreferenceManager;

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public List<ContextualPreference> loadAll() {
        return contextualPreferenceManager.loadAll();
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public ContextualPreference load(final String name, final ContextualPreferenceExternalResource resource) {
        return contextualPreferenceManager.load(name, resource);
    }

    public ContextualPreference search(final List<String> preferences,
                                       final ContextualPreferenceExternalResource resource) {
        return contextualPreferenceManager.search(preferences, resource);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public ContextualPreference upsert(final ContextualPreferenceVO preference) {
        return contextualPreferenceManager.upsert(preference);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public ContextualPreference delete(final String name, final ContextualPreferenceExternalResource resource) {
        return contextualPreferenceManager.delete(name, resource);
    }
}
