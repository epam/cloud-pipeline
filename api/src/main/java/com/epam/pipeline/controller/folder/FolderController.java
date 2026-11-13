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

package com.epam.pipeline.controller.folder;

import java.util.List;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.acl.metadata.MetadataEntityApiService;
import com.epam.pipeline.acl.folder.FolderApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Tag(name = "folder-controller", description = "Folders")
public class FolderController  extends AbstractRestController {

    @Autowired
    private FolderApiService folderApiService;

    @Autowired
    private MetadataEntityApiService metadataEntityApiService;

    @RequestMapping(value = "/folder/register", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Registers a new update.",
            description = "Registers a new update.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> registerFolder(@RequestBody Folder folder,
                                         @RequestParam(required = false) final String templateName) {
        if (StringUtils.isEmpty(templateName)) {
            return Result.success(folderApiService.create(folder));
        }
        return Result.success(folderApiService.createFromTemplate(folder, templateName));
    }

    @RequestMapping(value = "/folder/update", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Updates a update.",
            description = "Updates a update.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> updateFolder(@RequestBody Folder folder) {
        return Result.success(folderApiService.update(folder));
    }

    @RequestMapping(value = "/folder/project", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns a project for an input entity id and class.",
            description = "Returns a project for an input entity id and class.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> loadProject(@RequestParam Long id, @RequestParam AclClass aclClass) {
        return Result.success(folderApiService.getProject(id, aclClass));
    }

    @RequestMapping(value = "/folder/projects", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Lists all folders with project indicator.",
            description = "Lists all folders with project indicator.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> loadProjects() {
        return Result.success(folderApiService.loadProjects());
    }

    @RequestMapping(value = "/folder/loadTree", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Lists folders tree.",
            description = "Lists folders tree.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> loadFolderTree() {
        return Result.success(folderApiService.loadTree());
    }



    @RequestMapping(value = "/folder/{id}/load", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns a update subtree, specified by ID.",
            description = "Returns a update subtree, specified by ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> loadFolder(@PathVariable(value = "id") final Long id) {
        return Result.success(folderApiService.load(id));
    }

    @RequestMapping(value = "/folder/find", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns a folder, specified by ID or name.",
            description = "Returns a folder, specified by ID or name.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> findFolder(@RequestParam(value = "id") final String identifier) {
        return Result.success(folderApiService.loadByIdOrPath(identifier));
    }

    @RequestMapping(value = "/folder/{id}/metadata", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns a list of entities, specified by class.",
            description = "Returns a list of entities, specified by class.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<MetadataEntity>> loadFolderMetadataEntitiesByClass(
            @PathVariable(value = "id") final Long id,
            @RequestParam(value = "class") final String className) {
        return Result.success(metadataEntityApiService.loadMetadataEntityByClass(id, className));
    }

    @RequestMapping(value = "/folder/{id}/delete", method = RequestMethod.DELETE)
    @ResponseBody
    @Operation(
            summary = "Deletes a folder, specified by ID.",
            description = "Deletes a folder, specified by ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> deleteFolder(
            @PathVariable(value = "id") final Long id,
            @RequestParam(value = "force", defaultValue = "false") final boolean force) {
        if (force) {
            return Result.success(folderApiService.deleteForce(id));
        } else {
            return Result.success(folderApiService.delete(id));
        }
    }

    @PostMapping(value = "/folder/{id}/clone")
    @ResponseBody
    @Operation(
            summary = "Clones a folder, specified by ID.",
            description = "Clones a folder, specified by ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> cloneFolder(@PathVariable final Long id,
                                      @RequestParam(required = false) final Long parentId,
                                      @RequestParam final String name) {
        Long destinationFolderId = parentId != null ? parentId : id;
        return Result.success(folderApiService.cloneFolder(id, destinationFolderId, name));
    }

    @PostMapping(value = "/folder/{id}/lock")
    @ResponseBody
    @Operation(
            summary = "Locks a project and all its children from any changes from non-admin users.",
            description = "Locks a project and all its children from any changes from non-admin users.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> lockFolder(@PathVariable final Long id) {
        return Result.success(folderApiService.lockFolder(id));
    }

    @PostMapping(value = "/folder/{id}/unlock")
    @ResponseBody
    @Operation(
            summary = "Unlocks a project and all its children.",
            description = "Unlocks a project and all its children.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> unlockFolder(@PathVariable final Long id) {
        return Result.success(folderApiService.unlockFolder(id));
    }
}
