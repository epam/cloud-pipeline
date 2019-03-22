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

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.security.acl.AclClass;

import java.util.Collection;

/**
 * Interface for all managers that support work with secured entities
 */
public interface SecuredEntityManager {

    AbstractSecuredEntity load(Long id);

    AbstractSecuredEntity changeOwner(Long id, String owner);

    AclClass getSupportedClass();

    AbstractSecuredEntity loadByNameOrId(String identifier);

    Integer loadTotalCount();

    Collection<? extends AbstractSecuredEntity> loadAllWithParents(Integer page, Integer pageSize);

    AbstractSecuredEntity loadWithParents(Long id);
}
