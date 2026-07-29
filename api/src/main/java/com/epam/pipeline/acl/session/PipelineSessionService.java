/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.acl.session;

import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.SidImpl;
import com.epam.pipeline.manager.user.UserManager;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Service;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;
import static com.epam.pipeline.security.acl.AclExpressions.OR;
import static com.epam.pipeline.security.acl.AclExpressions.USER_ADMIN_ONLY;

@Service
public class PipelineSessionService {
    @Autowired
    private JdbcIndexedSessionRepository jdbcIndexedSessionRepository;

    @Autowired
    private UserManager userManager;

    @PreAuthorize(ADMIN_ONLY + OR + USER_ADMIN_ONLY)
    public void invalidateSession(final SidImpl sid) {
        if (sid == null || StringUtils.isBlank(sid.getName())) {
            return;
        }
        if (sid.isPrincipal()) {
            deleteSessions(sid.getName().trim().toUpperCase());
            return;
        }
        userManager.loadUsersByGroupOrRole(sid.getName()).stream()
                .map(PipelineUser::getUserName)
                .filter(StringUtils::isNotBlank)
                .map(name -> name.trim().toUpperCase())
                .distinct()
                .forEach(this::deleteSessions);
    }

    private void deleteSessions(final String principalName) {
        jdbcIndexedSessionRepository.findByPrincipalName(principalName)
                .keySet()
                .forEach(jdbcIndexedSessionRepository::deleteById);
    }
}
