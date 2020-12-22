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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
@Api(value = "Folders")
public class FolderController  extends AbstractRestController {

    @Autowired
    private FolderApiService folderApiService;

    @Autowired
    private MetadataEntityApiService metadataEntityApiService;

    @RequestMapping(value = "/folder/register", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Registers a new update.",
            notes = "Registers a new update.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
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
    @ApiOperation(
            value = "Updates a update.",
            notes = "Updates a update.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> updateFolder(@RequestBody Folder folder) {
        return Result.success(folderApiService.update(folder));
    }

    @RequestMapping(value = "/folder/project", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns a project for an input entity id and class.",
            notes = "Returns a project for an input entity id and class.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> loadProject(@RequestParam Long id, @RequestParam AclClass aclClass) {
        return Result.success(folderApiService.getProject(id, aclClass));
    }

    @RequestMapping(value = "/folder/projects", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Lists all folders with project indicator.",
            notes = "Lists all folders with project indicator.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> loadProjects() {
        return Result.success(folderApiService.loadProjects());
    }

    @RequestMapping(value = "/folder/loadTree", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Lists folders tree.",
            notes = "Lists folders tree.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> loadFolderTree() {
        return Result.success(folderApiService.loadTree());
    }



    @RequestMapping(value = "/folder/{id}/load", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns a update subtree, specified by ID.",
            notes = "Returns a update subtree, specified by ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> loadFolder(@PathVariable(value = "id") final Long id) {
        return Result.success(folderApiService.load(id));
    }

    @RequestMapping(value = "/folder/find", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns a folder, specified by ID or name.",
            notes = "Returns a folder, specified by ID or name.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> findFolder(@RequestParam(value = "id") final String identifier) {
        return Result.success(folderApiService.loadByIdOrPath(identifier));
    }

    @RequestMapping(value = "/folder/{id}/metadata", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns a list of entities, specified by class.",
            notes = "Returns a list of entities, specified by class.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<MetadataEntity>> loadFolderMetadataEntitiesByClass(
            @PathVariable(value = "id") final Long id,
            @RequestParam(value = "class") final String className) {
        return Result.success(metadataEntityApiService.loadMetadataEntityByClass(id, className));
    }

    @RequestMapping(value = "/folder/{id}/delete", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes a folder, specified by ID.",
            notes = "Deletes a folder, specified by ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
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
    @ApiOperation(
            value = "Clones a folder, specified by ID.",
            notes = "Clones a folder, specified by ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> cloneFolder(@PathVariable final Long id,
                                      @RequestParam(required = false) final Long parentId,
                                      @RequestParam final String name) {
        Long destinationFolderId = parentId != null ? parentId : id;
        return Result.success(folderApiService.cloneFolder(id, destinationFolderId, name));
    }

    @PostMapping(value = "/folder/{id}/lock")
    @ResponseBody
    @ApiOperation(
            value = "Locks a project and all its children from any changes from non-admin users.",
            notes = "Locks a project and all its children from any changes from non-admin users.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> lockFolder(@PathVariable final Long id) {
        return Result.success(folderApiService.lockFolder(id));
    }

    @PostMapping(value = "/folder/{id}/unlock")
    @ResponseBody
    @ApiOperation(
            value = "Unlocks a project and all its children.",
            notes = "Unlocks a project and all its children.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Folder> unlockFolder(@PathVariable final Long id) {
        return Result.success(folderApiService.unlockFolder(id));
    }
}
