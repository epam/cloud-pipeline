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

package com.epam.pipeline.mapper;

import com.epam.pipeline.controller.vo.EntityPermissionVO;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.EntityPermission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class AbstractEntityPermissionMapper {

    @Mapping(target = "entityId", expression = "java(fillEntityId(entityPermission))")
    @Mapping(target = "entityClass", expression = "java(fillEntityClass(entityPermission))")
    @Mapping(target = "owner", expression = "java(fillOwner(entityPermission))")
    public abstract EntityPermissionVO toEntityPermissionVO(EntityPermission entityPermission);

    Long fillEntityId(EntityPermission entityPermission) {
        return entityPermission.getEntity() == null ? null : entityPermission.getEntity().getId();
    }

    AclClass fillEntityClass(EntityPermission entityPermission) {
        return entityPermission.getEntity() == null ? null : entityPermission.getEntity().getAclClass();
    }

    String fillOwner(EntityPermission entityPermission) {
        return entityPermission.getEntity() == null ? null : entityPermission.getEntity().getOwner();
    }
}
