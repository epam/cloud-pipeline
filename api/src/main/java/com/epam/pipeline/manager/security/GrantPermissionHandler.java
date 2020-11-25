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

package com.epam.pipeline.manager.security;

import com.epam.pipeline.app.CacheConfiguration;
import com.epam.pipeline.entity.security.acl.AclEntitySummary;
import com.epam.pipeline.security.acl.JdbcMutableAclServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.cache.CacheManager;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
    private final CacheManager cacheManager;

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteGrantedAuthority(final String name) {
        Long sidId = aclService.getSidId(name, false);
        if (sidId == null) {
            log.debug("Granted authority with name {} was not found in ACL", name);
            return;
        }
        final List<AclEntitySummary> entriesToEvict = aclService.loadEntriesWithAuthoritySummary(sidId);
        aclService.deleteSidById(sidId);
        invalidateAclEntriesInCache(entriesToEvict);
    }

    private void invalidateAclEntriesInCache(final List<AclEntitySummary> entriesToEvict) {
        if (CollectionUtils.isNotEmpty(entriesToEvict)) {
            Optional.ofNullable(cacheManager.getCache(CacheConfiguration.ACL_CACHE))
                .ifPresent(cache -> entriesToEvict.forEach(entry -> {
                    cache.evict(entry.getAclEntityId());
                    cache.evict(new ObjectIdentityImpl(entry.getAclClass(), entry.getObjectId()));
                }));
        }
    }
}
