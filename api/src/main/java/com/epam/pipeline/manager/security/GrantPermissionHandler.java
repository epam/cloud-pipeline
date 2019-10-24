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

package com.epam.pipeline.manager.security;

import com.epam.pipeline.security.acl.JdbcMutableAclServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GrantPermissionHandler} shall handle all operations permission assignment:
 * grant, remove permissions, etc.
 * TODO: Now all this logic is implemented in {@link GrantPermissionManager}, shall be moved to this class.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GrantPermissionHandler {

    private final JdbcMutableAclServiceImpl aclService;

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteGrantedAuthority(String name) {
        Long sidId = aclService.getSidId(name, false);
        if (sidId == null) {
            log.debug("Granted authority with name {} was not found in ACL", name);
            return;
        }
        aclService.deleteSidById(sidId);
    }
}
