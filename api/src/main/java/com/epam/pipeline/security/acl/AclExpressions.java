/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

    public static final String OR = " OR ";
    public static final String AND = " AND ";

    public static final String ADMIN_ONLY = "hasRole('ADMIN')";

    public static final String OR_USER_READER = OR + "hasRole('USER_READER')";

    public static final String USER_READ_FILTER =
            ADMIN_ONLY + OR_USER_READER + OR + "hasPermission(filterObject, 'READ')";
    public static final String USER_READ_PERMISSION =
            "hasPermission(#id, 'com.epam.pipeline.entity.user.PipelineUser', 'READ')";
    public static final String PIPELINE_CREATE = "hasRole('ADMIN') OR " +
            "@pipelinePermissionManager.hasCreatePermission(#pipeline.pipelineType, #pipeline.parentFolderId)";

    public static final String PIPELINE_ID_MANAGE =
            "hasRole('ADMIN') OR @pipelinePermissionManager.hasManagePermission(#id)";

    public static final String PIPELINE_ID_READ =
            "hasRole('ADMIN') OR hasPermission(#id, 'com.epam.pipeline.entity.pipeline.Pipeline', 'READ')";
    public static final String PIPELINE_COPY = "hasRole('ADMIN') OR " +
            "@pipelinePermissionManager.hasCopyPermission(#id, #parentFolderId)";

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
            "hasRole('ADMIN') OR @runPermissionManager.runPermission(#runId, 'READ')";

    public static final String RUN_ID_EXECUTE =
            "hasRole('ADMIN') OR @runPermissionManager.runPermission(#runId, 'EXECUTE')";

    public static final String RUN_ID_WRITE =
            "hasRole('ADMIN') OR @runPermissionManager.runPermission(#runId, 'WRITE')";

    public static final String RUN_ID_OWNER =
            "hasRole('ADMIN') OR @runPermissionManager.runPermission(#runId, 'OWNER')";

    public static final String RUN_ID_SSH =
            "hasRole('ADMIN') OR @runPermissionManager.isRunSshAllowed(#runId)";

    public static final String STORAGE_SHARED = "@grantPermissionManager.checkStorageShared(#id)";

    public static final String STORAGE_ID_READ =
            "(" + ADMIN_ONLY + OR + "@storagePermissionManager.storagePermissionById(#id, 'READ')" + ")"
            + AND + STORAGE_SHARED;

    public static final String STORAGE_ID_WRITE =
            "(" + ADMIN_ONLY + OR + "@storagePermissionManager.storagePermissionById(#id, 'WRITE')" + ")"
            + AND + STORAGE_SHARED;

    public static final String STORAGE_ID_OWNER =
            "(" + ADMIN_ONLY + OR + "@storagePermissionManager.storagePermissionById(#id, 'OWNER')" + ")"
            + AND + STORAGE_SHARED;

    public static final String STORAGE_MGMT_UPDATE =
            ADMIN_ONLY + OR + "@storagePermissionManager.storageMgmtPermission(#storage.id, 'OWNER')";

    public static final String STORAGE_ID_MGMT_DELETE =
            ADMIN_ONLY + OR + "(" + "hasRole('STORAGE_MANAGER')"
                + AND + "@storagePermissionManager.storageMgmtPermission(#id, 'OWNER')" + ")";

    public static final String STORAGE_ID_TAGS_WRITE =
            "(" + ADMIN_ONLY + OR + "@storagePermissionManager.storageTagsPermission(#id, #tags, 'WRITE')" + ")"
            + AND + STORAGE_SHARED;

    public static final String STORAGE_ID_TAGS_REQUEST_WRITE =
            "(" + ADMIN_ONLY + OR + "@storagePermissionManager.storageTagsPermission(#id, #request, 'WRITE')" + ")"
            + AND + STORAGE_SHARED;

    public static final String ARCHIVED_PERMISSIONS =
            "hasRole('ROLE_STORAGE_ARCHIVE_MANAGER') OR hasRole('ROLE_STORAGE_ARCHIVE_READER')";

    public static final String STORAGE_SHOW_ARCHIVED_PERMISSIONS = "(#showArchived == false"
            + OR + STORAGE_ID_OWNER + OR + ARCHIVED_PERMISSIONS + ")";

    public static final String STORAGE_LIFECYCLE_READER = STORAGE_ID_OWNER + OR + STORAGE_ID_READ + AND
            + ARCHIVED_PERMISSIONS;

    public static final String STORAGE_LIFECYCLE_MANAGER = STORAGE_ID_OWNER + OR + STORAGE_ID_WRITE + AND
            + "hasRole('ROLE_STORAGE_ARCHIVE_MANAGER')";

    public static final String STORAGE_ID_PERMISSIONS =
            "("
                + "hasRole('ADMIN') "
                    + "OR ("
                        + "@storagePermissionManager.storagePermissionById(#id, 'READ') "
                        + "AND @storagePermissionManager.storagePermissions(#id, #permissions) "
                    + ") "
            + ") "
            + AND + STORAGE_SHARED;

    public static final String STORAGE_PATHS_READ = ADMIN_ONLY + OR +
            "@grantPermissionManager.hasDataStoragePathsPermission(returnObject, 'READ')";

    public static final String RUN_COMMIT_EXECUTE =
        "hasRole('ADMIN') OR (@runPermissionManager.runPermission(#runId, 'EXECUTE')"
            + " AND hasPermission(#registryId, 'com.epam.pipeline.entity.pipeline.DockerRegistry', 'WRITE'))";

    public static final String METADATA_OWNER = ADMIN_ONLY + OR +
            "@metadataPermissionManager.metadataOwnerPermission(#entityVO)";

    public static final String METADATA_UPDATE = ADMIN_ONLY + OR +
            "@metadataPermissionManager.editMetadataPermission(#metadataVO)";

    public static final String METADATA_UPDATE_KEY = ADMIN_ONLY + OR +
            "@metadataPermissionManager.editMetadataPermission(#entityVO, #key)";

    public static final String METADATA_FILTER = ADMIN_ONLY + OR +
            "@metadataPermissionManager.metadataPermission(" +
            "filterObject.entity.entityId, filterObject.entity.entityClass, 'READ')" + OR +
            "filterObject.entity.entityClass.name() == 'PIPELINE_USER'" + AND + "hasRole('USER_METADATA_READER')";

    public static final String ACL_ENTITY_OWNER =
            "hasRole('ADMIN') or @grantPermissionManager.ownerPermission(#id, #aclClass)";

    public static final String ISSUE_AUTHOR = ADMIN_ONLY + OR +
            "@grantPermissionManager.modifyIssuePermission(#issueId)";

    public static final String COMMENT_AUTHOR = ADMIN_ONLY + OR +
            "@grantPermissionManager.modifyCommentPermission(#issueId, #commentId)";

    public static final String ISSUE_READ = ADMIN_ONLY + OR +
            "(@grantPermissionManager.issuePermission(#issueId, 'READ'))";

    public static final String ENTITY_READ = ADMIN_ONLY + OR +
            "(@metadataPermissionManager.metadataPermission(#entityVO.entityId, #entityVO.entityClass, 'READ'))";

    public static final String ISSUE_CREATE = ADMIN_ONLY + " OR (@metadataPermissionManager.metadataPermission(" +
            "#issueVO.entity.entityId, #issueVO.entity.entityClass, 'READ'))";

    public static final String ADMIN_OR_GENERAL_USER = "hasRole('ADMIN') OR hasRole('ROLE_USER')";

    public static final String NODE_READ = ADMIN_ONLY + OR +
            "@grantPermissionManager.nodePermission(#name, 'READ')";

    public static final String NODE_USAGE_READ = ADMIN_ONLY + OR +
            "@grantPermissionManager.nodeUsagePermission(#name, 'READ')";

    public static final String NODE_READ_FILTER = ADMIN_ONLY + OR +
            "@grantPermissionManager.nodePermission(filterObject, 'READ')";
    
    public static final String NODE_STOP = ADMIN_ONLY + OR +
            "@grantPermissionManager.nodeStopPermission(#name, 'EXECUTE')";

    public static final String TOOL_READ = ADMIN_ONLY + OR +
            "hasPermission(#id, 'com.epam.pipeline.entity.pipeline.Tool', 'READ')";

    public static final String TOOL_WRITE = ADMIN_ONLY + OR +
            "hasPermission(#id, 'com.epam.pipeline.entity.pipeline.Tool', 'WRITE')";

    public static final String OR_HAS_ASSIGNED_USER_OR_ROLE =
            " OR @grantPermissionManager.hasCloudProfilePermissions(#profileId)";

    public static final String DTS_REGISTRY_PERMISSIONS = ADMIN_ONLY + OR + "hasRole('ROLE_DTS_MANAGER')";

    public static final String ADMIN_OR_HAS_READ_ACCESS_ON_ENTITIES_FROM_LIST =
        ADMIN_ONLY + OR + "hasPermission(filterObject, 'READ')";

    public static final String ADMIN_OR_HAS_READ_ACCESS_ON_RETURN_OBJECT =
        ADMIN_ONLY + OR + "hasPermission(returnObject, 'READ')";

    public static final String ADMIN_OR_BILLING_MANAGER = ADMIN_ONLY + OR
            + "hasRole('ROLE_BILLING_MANAGER')";

    private AclExpressions() {
        // no op
    }
}
