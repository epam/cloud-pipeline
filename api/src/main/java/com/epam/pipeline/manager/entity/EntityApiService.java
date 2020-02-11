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

package com.epam.pipeline.manager.entity;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclSid;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.HierarchicalEntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EntityApiService {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private HierarchicalEntityManager hierarchicalEntityManager;

    @PostAuthorize("hasRole('ADMIN') OR @grantPermissionManager.entityPermission(returnObject, 'READ')")
    public AbstractSecuredEntity loadByNameOrId(AclClass entityClass, String identifier) {
        return entityManager.loadByNameOrId(entityClass, identifier);
    }

    @PostFilter("hasRole('ADMIN')")
    public List<AbstractSecuredEntity> loadAvailable(final AclSid aclSid) {
        return hierarchicalEntityManager.loadAvailable(aclSid);
    }
}
