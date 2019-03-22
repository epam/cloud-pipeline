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

package com.epam.pipeline.manager.preference;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PreferenceApiService {

    private final PreferenceManager preferenceManager;
    private final AuthManager authManager;
    private final MessageHelper messageHelper;

    public Collection<Preference> loadAll() {
        if (authManager.isAdmin()) {
            return preferenceManager.loadAll();
        } else {
            return preferenceManager.loadVisible();
        }
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public List<Preference> update(List<Preference> preference) {
        return preferenceManager.update(preference);
    }

    @PostAuthorize("hasRole('ADMIN') OR (returnObject != null AND returnObject.visible)")
    public Preference load(String name) {
        return preferenceManager.load(name)
                .orElseThrow(() -> new IllegalArgumentException(messageHelper
                        .getMessage(MessageConstants.ERROR_PREFERENCE_WITH_NAME_NOT_FOUND, name)));
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public void delete(String name) {
        preferenceManager.delete(name);
    }
}
