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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.security.acl.AclMask;
import com.epam.pipeline.manager.security.acl.AclTree;
import com.epam.pipeline.security.acl.AclExpressions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class FolderApiService {

    @Autowired
    private FolderManager folderManager;

    @PreAuthorize(AclExpressions.FOLDER_ID_CREATE)
    public Folder create(final Folder folder) {
        return folderManager.create(folder);
    }

    @PreAuthorize(AclExpressions.FOLDER_ID_CREATE)
    public Folder createFromTemplate(final Folder folder, final String templateName) {
        return folderManager.createFromTemplate(folder, templateName);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#folder, 'WRITE')")
    public Folder update(final Folder folder) {
        return folderManager.update(folder);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.metadataPermission(#id, #aclClass, 'READ')")
    @AclTree
    public FolderWithMetadata getProject(final Long id, final AclClass aclClass) {
        return folderManager.getProject(id, aclClass);
    }

    @AclTree
    public Folder loadTree() {
        return folderManager.loadTree();
    }

    @AclTree
    public Folder loadProjects() {
        return folderManager.loadAllProjects();
    }

    @AclTree
    public Folder load(Long id) {
        return folderManager.load(id);
    }

    @PostAuthorize("hasRole('ADMIN') OR hasPermission(returnObject, 'READ')")
    @AclMask
    public Folder loadByIdOrPath(String pathOrIdentifier) {
        return folderManager.loadByNameOrId(pathOrIdentifier);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('FOLDER_MANAGER') AND "
            + "hasPermission(#id, 'com.epam.pipeline.entity.pipeline.Folder', 'WRITE'))")
    public Folder delete(final Long id) {
        return folderManager.delete(id);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('FOLDER_MANAGER') AND "
            +"@grantPermissionManager.childrenFolderPermission(#id, 'WRITE'))")
    public Folder deleteForce(final Long id) {
        return folderManager.deleteForce(id);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('FOLDER_MANAGER') AND "
            + "hasPermission(#id, 'com.epam.pipeline.entity.pipeline.Folder', 'READ') AND "
            + "hasPermission(#destinationFolderId, 'com.epam.pipeline.entity.pipeline.Folder', 'WRITE'))")
    public Folder cloneFolder(final Long id, final Long destinationFolderId, String name) {
        return folderManager.cloneFolder(id, destinationFolderId, name);
    }

    @AclMask
    @PreAuthorize("hasRole('ADMIN') or @grantPermissionManager.ownerPermission(#id, {'FOLDER'})")
    public Folder lockFolder(Long id) {
        return folderManager.lockFolder(id);
    }

    @AclMask
    @PreAuthorize("hasRole('ADMIN') or @grantPermissionManager.ownerPermission(#id, {'FOLDER'})")
    public Folder unlockFolder(Long id) {
        return folderManager.unlockFolder(id);
    }
}
