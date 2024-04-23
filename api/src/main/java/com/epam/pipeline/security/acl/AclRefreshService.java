/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.epam.pipeline.security.acl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.acls.model.Acl;
import org.springframework.security.acls.model.AclCache;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.ObjectIdentity;

import java.util.Map;

@Slf4j
@EnableScheduling
public class AclRefreshService {

    private final LookupStrategyImpl lookupStrategy;
    private final AclCache aclCache;

    public AclRefreshService(final LookupStrategyImpl lookupStrategy,
                             final AclCache aclCache) {
        this.lookupStrategy = lookupStrategy;
        this.aclCache = aclCache;
    }

    @Scheduled(fixedDelayString = "${security.acl.cache.ttl:60000}")
    public void refresh() {
        log.info("Receiving ACLs...");
        final Map<ObjectIdentity, Acl> acls = lookupStrategy.lookupObjectIdentities();
        log.info("Received {} ACLs", acls.size());
        log.info("Persisting ACLs...");
        acls.forEach((key, value) -> aclCache.putInCache((MutableAcl) value));
        log.info("Persisted {} ACLs", acls.size());
    }
}
