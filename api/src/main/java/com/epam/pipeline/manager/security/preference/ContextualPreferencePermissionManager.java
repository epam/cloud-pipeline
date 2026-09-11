/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.security.preference;

import com.epam.pipeline.controller.vo.ContextualPreferenceVO;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContextualPreferencePermissionManager {

    private final CheckPermissionHelper permissionHelper;

    public boolean hasScopedAdminPermission(final ContextualPreferenceExternalResource resource) {
        final ContextualPreferenceLevel resourceLevel = resource.level();
        if (resourceLevel == null) {
            return false;
        }
        switch (resourceLevel) {
            case ROLE:
            case USER:
                return permissionHelper.isScopedAdmin(AclClass.PIPELINE_USER);
            case TOOL:
                return permissionHelper.isScopedAdmin(AclClass.TOOL);
            case STORAGE:
                return permissionHelper.isScopedAdmin(AclClass.DATA_STORAGE);
            default:
                return false;
        }
    }

    public boolean hasScopedAdminPermission(final ContextualPreferenceVO contextualPreference) {
        if (contextualPreference.resource() == null) {
            return false;
        }
        return hasScopedAdminPermission(contextualPreference.resource());
    }
}
