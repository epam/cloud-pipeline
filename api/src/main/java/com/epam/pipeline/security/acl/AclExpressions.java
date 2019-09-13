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

public final class AclExpressions {

    private static final String OR = " OR ";

    public static final String ADMIN_ONLY = "hasRole('ADMIN')";

    public static final String PIPELINE_ID_READ =
            "hasRole('ADMIN') OR hasPermission(#id, 'com.epam.pipeline.entity.pipeline.Pipeline', 'READ')";

    public static final String PIPELINE_ID_WRITE =
            "hasRole('ADMIN') OR hasPermission(#id, 'com.epam.pipeline.entity.pipeline.Pipeline', 'WRITE')";

    public static final String FOLDER_ID_CREATE = "hasRole('ADMIN') OR "
            + "(#folder.parentId != null AND hasRole('FOLDER_MANAGER') AND "
            + "hasPermission(#folder.parentId, 'com.epam.pipeline.entity.pipeline.Folder','WRITE'))";

    public static final String PIPELINE_OBJECT_WRITE =
            "hasRole('ADMIN') OR hasPermission(#pipeline, 'WRITE')";

    public static final String PIPELINE_VO_WRITE =
            "hasRole('ADMIN') OR hasPermission(#pipeline.id, 'com.epam.pipeline.entity.pipeline.Pipeline', 'WRITE')";

    public static final String RUN_ID_READ =
            "hasRole('ADMIN') OR @grantPermissionManager.runPermission(#runId, 'READ')";

    public static final String RUN_ID_EXECUTE =
            "hasRole('ADMIN') OR @grantPermissionManager.runPermission(#runId, 'EXECUTE')";

    public static final String RUN_ID_WRITE =
            "hasRole('ADMIN') OR @grantPermissionManager.runPermission(#runId, 'WRITE')";

    public static final String RUN_ID_OWNER =
            "hasRole('ADMIN') OR @grantPermissionManager.runPermission(#runId, 'OWNER')";

    public static final String STORAGE_ID_READ =
            "(hasRole('ADMIN') OR @grantPermissionManager.storagePermission(#id, 'READ')) "
            + "AND @grantPermissionManager.checkStorageShared(#id)";

    public static final String STORAGE_ID_WRITE =
            "(hasRole('ADMIN') OR @grantPermissionManager.storagePermission(#id, 'WRITE')) "
            + "AND @grantPermissionManager.checkStorageShared(#id)";

    public static final String STORAGE_ID_OWNER =
            "(hasRole('ADMIN') OR @grantPermissionManager.storagePermission(#id, 'OWNER')) "
            + "AND @grantPermissionManager.checkStorageShared(#id)";

    public static final String STORAGE_PATHS_READ = ADMIN_ONLY + OR +
            "@grantPermissionManager.hasDataStoragePathsPermission(returnObject, 'READ')";

    public static final String RUN_COMMIT_EXECUTE =
        "hasRole('ADMIN') OR (@grantPermissionManager.runPermission(#runId, 'EXECUTE')"
            + " AND hasPermission(#registryId, 'com.epam.pipeline.entity.pipeline.DockerRegistry', 'WRITE'))";

    public static final String METADATA_OWNER =
            "hasRole('ADMIN') OR @grantPermissionManager.hasMetadataOwnerPermission(#metadataVO.entity.entityId, "
                    + "#metadataVO.entity.entityClass)";

    public static final String METADATA_ENTRY_OWNER = "hasRole('ADMIN') OR "
            + "@grantPermissionManager.hasMetadataOwnerPermission(#entityVO.entityId, #entityVO.entityClass)";

    public static final String ACL_ENTITY_OWNER =
            "hasRole('ADMIN') or @grantPermissionManager.ownerPermission(#id, #aclClass)";

    public static final String ISSUE_AUTHOR = ADMIN_ONLY + OR +
            "@grantPermissionManager.modifyIssuePermission(#issueId)";

    public static final String COMMENT_AUTHOR = ADMIN_ONLY + OR +
            "@grantPermissionManager.modifyCommentPermission(#issueId, #commentId)";

    public static final String ISSUE_READ = ADMIN_ONLY + OR +
            "(@grantPermissionManager.issuePermission(#issueId, 'READ'))";

    public static final String ENTITY_READ = ADMIN_ONLY + OR +
            "(@grantPermissionManager.metadataPermission(#entityVO.entityId, #entityVO.entityClass, 'READ'))";

    public static final String ISSUE_CREATE = ADMIN_ONLY + " OR (@grantPermissionManager.metadataPermission(" +
            "#issueVO.entity.entityId, #issueVO.entity.entityClass, 'READ'))";

    public static final String DTS_REGISTRY_PERMISSIONS = "hasRole('ADMIN') OR hasRole('ROLE_USER')";

    private AclExpressions() {
        // no op
    }
}
