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

package com.epam.pipeline.test.creator.security;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.EntityPermissionVO;
import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.controller.vo.PermissionVO;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclPermissionEntry;
import com.epam.pipeline.entity.security.acl.AclSecuredEntry;
import com.epam.pipeline.entity.security.acl.AclSid;
import com.epam.pipeline.security.acl.AclPermission;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class PermissionCreatorUtils {

    public static final TypeReference<Result<AclSecuredEntry>> ACL_SECURED_ENTRY_TYPE =
            new TypeReference<Result<AclSecuredEntry>>() {};
    public static final TypeReference<Result<EntityPermissionVO>> ENTITY_WITH_PERMISSION_VO_TYPE =
            new TypeReference<Result<EntityPermissionVO>>() {};

    private PermissionCreatorUtils() {
    }

    public static PermissionVO getPermissionVO(String userName) {
        PermissionVO permissionVO = new PermissionVO();
        permissionVO.setMask(AclPermission.READ.getMask());
        permissionVO.setUserName(userName);
        permissionVO.setPrincipal(false);
        return permissionVO;
    }

    public static AclSecuredEntry getAclSecuredEntry() {
        final AclSecuredEntry aclSecuredEntry = new AclSecuredEntry();
        aclSecuredEntry.setPermissions(Collections.singletonList(new AclPermissionEntry()));
        return aclSecuredEntry;
    }

    public static PermissionGrantVO getPermissionGrantVO() {
        final PermissionGrantVO permissionGrantVO = new PermissionGrantVO();
        permissionGrantVO.setUserName(TEST_STRING);
        permissionGrantVO.setId(ID);
        permissionGrantVO.setMask(TEST_INT);
        permissionGrantVO.setPrincipal(true);
        permissionGrantVO.setAclClass(AclClass.DATA_STORAGE);
        return permissionGrantVO;
    }

    public static PermissionGrantVO getPermissionGrantVOFrom(PermissionVO permissionVO, AclClass aclClass, Long id) {
        final PermissionGrantVO permissionGrantVO = new PermissionGrantVO();
        permissionGrantVO.setUserName(permissionVO.getUserName());
        permissionGrantVO.setMask(permissionVO.getMask());
        permissionGrantVO.setPrincipal(permissionVO.getPrincipal());
        permissionGrantVO.setAclClass(aclClass);
        permissionGrantVO.setId(id);
        return permissionGrantVO;
    }

    public static EntityPermissionVO getEntityPermissionVO() {
        final EntityPermissionVO entityPermissionVO = new EntityPermissionVO();
        entityPermissionVO.setEntityId(ID);
        entityPermissionVO.setOwner(TEST_STRING);
        entityPermissionVO.setEntityClass(AclClass.DATA_STORAGE);
        entityPermissionVO.setPermissions(Collections.singleton(new AclPermissionEntry(new AclSid(), TEST_INT)));
        return entityPermissionVO;
    }
}
