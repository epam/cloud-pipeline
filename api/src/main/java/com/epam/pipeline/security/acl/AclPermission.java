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

package com.epam.pipeline.security.acl;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.acls.domain.AbstractPermission;
import org.springframework.security.acls.model.Permission;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AclPermission} represents a set of supported permissions for ACL security layer.
 * {@link AclPermission} is used to check whether user has a required permission to access some
 * {@link com.epam.pipeline.entity.AbstractSecuredEntity} and is also used to calculate and return
 * a permission mask to the client.
 * Basically we support three basic permissions: READ, WRITE, EXECUTE. For each basic permission
 * there is also a corresponding denying permission: NO_READ, NO_WRITE, NO_EXECUTE. Since permissions
 * maybe inherited from {@link com.epam.pipeline.entity.AbstractSecuredEntity} parent, denying
 * permissions are used to prohibit permission inheriting for a basic permission, e.g. if NO_READ
 * permission is set for a entity, user won't have READ access to it, even if he has READ access to
 * it's parent.
 * Supplementary OWNER permission gives full access to object with all basic permissions granted.
 * We support two permissions mask notations:
 * -    full 7-bit mask containing basic and denying permissions bits is stored in DB
 *      and used for granting/denying access to objects
 * -    simple 4-bit mask containing only READ/WRITE/EXECUTE/OWNER bits is returned to client.
 */
@Getter
@Setter
public class AclPermission extends AbstractPermission {

    public static final String WRITE_PERMISSION = "WRITE";

    public static final Permission READ = new AclPermission(1, 'R', true); //1
    public static final Permission NO_READ = new AclPermission(1 << 1, 'Q', false); //2

    public static final Permission WRITE = new AclPermission(1 << 2, 'W', true); //4
    public static final Permission NO_WRITE = new AclPermission(1 << 3, 'S', false); //8

    public static final Permission EXECUTE = new AclPermission(1 << 4, 'E', true); //16
    public static final Permission NO_EXECUTE = new AclPermission(1 << 5, 'X', false); //32

    public static final Permission OWNER = new AclPermission(1 << 6, 'O', true); //16

    public static final Permission ALL_DENYING_PERMISSIONS = new AclPermission(NO_READ.getMask() |
                                                                           NO_WRITE.getMask() | NO_EXECUTE.getMask());

    protected static final Map<Permission, Permission> DENYING_PERMISSIONS = new HashMap<>();

    public static final String NO_READ_NAME = "NO_READ";
    public static final String NO_WRITE_NAME = "NO_WRITE";
    public static final String NO_EXECUTE_NAME = "NO_EXECUTE";
    public static final String READ_NAME = "READ";
    public static final String WRITE_NAME = "WRITE";
    public static final String EXECUTE_NAME = "EXECUTE";
    public static final String OWNER_NAME = "OWNER";
    public static final String COMMA = ",";
    public static final String EMPTY_STRING = "";

    static {
        DENYING_PERMISSIONS.put(READ, NO_READ);
        DENYING_PERMISSIONS.put(WRITE, NO_WRITE);
        DENYING_PERMISSIONS.put(EXECUTE, NO_EXECUTE);
    }

    protected static final Map<AclPermission, Integer> SIMPLE_MASKS = new HashMap<>();
    static {
        SIMPLE_MASKS.put((AclPermission)READ, 1);
        SIMPLE_MASKS.put((AclPermission)WRITE, 1 << 1);
        SIMPLE_MASKS.put((AclPermission)EXECUTE, 1 << 2);
        SIMPLE_MASKS.put((AclPermission)OWNER, 1 << 3);
    }

    protected static final Map<String, AclPermission> NAME_PERMISSION_MAP = new HashMap<>();

    static {
        NAME_PERMISSION_MAP.put(READ_NAME, (AclPermission)READ);
        NAME_PERMISSION_MAP.put(WRITE_NAME, (AclPermission)WRITE);
        NAME_PERMISSION_MAP.put(EXECUTE_NAME, (AclPermission)EXECUTE);
        NAME_PERMISSION_MAP.put(OWNER_NAME, (AclPermission)OWNER);
    }

    private boolean granting = true;

    public AclPermission(int mask) {
        super(mask);
    }

    public AclPermission(int mask, char code) {
        super(mask, code);
    }

    public AclPermission(int mask, char code, boolean granting) {
        super(mask, code);
        this.granting = granting;
    }

    public AclPermission getDenyPermission() {
        Permission permission = DENYING_PERMISSIONS.get(this);
        return permission == null ? null : (AclPermission)permission;
    }

    public int getSimpleMask() {
        return SIMPLE_MASKS.get(this);
    }

    public static List<AclPermission> getBasicPermissions() {
        return Arrays.asList((AclPermission)READ, (AclPermission)WRITE, (AclPermission)EXECUTE);
    }

    public static AclPermission getAclPermissionByName(String permissionName) {
        return NAME_PERMISSION_MAP.get(permissionName);
    }

    public static String getReadableView(int mask) {
        if (mask == 0) {
            return EMPTY_STRING;
        }
        final StringBuilder viewBuilder = new StringBuilder();
        if ((mask & AclPermission.READ.getMask()) != 0) {
            viewBuilder.append(COMMA + READ_NAME);
        }
        if ((mask & AclPermission.NO_READ.getMask()) != 0) {
            viewBuilder.append(COMMA + NO_READ_NAME);
        }
        if ((mask & AclPermission.WRITE.getMask()) != 0) {
            viewBuilder.append(COMMA + WRITE_NAME);
        }
        if ((mask & AclPermission.NO_WRITE.getMask()) != 0) {
            viewBuilder.append(COMMA + NO_WRITE_NAME);
        }
        if ((mask & AclPermission.EXECUTE.getMask()) != 0) {
            viewBuilder.append(COMMA + EXECUTE_NAME);
        }
        if ((mask & AclPermission.NO_EXECUTE.getMask()) != 0) {
            viewBuilder.append(COMMA + NO_EXECUTE_NAME);
        }
        // remove first comma
        return viewBuilder.length() > 0 ? viewBuilder.deleteCharAt(0).toString() : viewBuilder.toString();
    }
}
