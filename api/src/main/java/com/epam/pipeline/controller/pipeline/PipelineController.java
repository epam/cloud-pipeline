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

package com.epam.pipeline.controller.pipeline;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.CheckRepositoryVO;
import com.epam.pipeline.controller.vo.GenerateFileVO;
import com.epam.pipeline.controller.vo.InstanceOfferParametersVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemsVO;
import com.epam.pipeline.controller.vo.PipelineVO;
import com.epam.pipeline.controller.vo.PipelinesWithPermissionsVO;
import com.epam.pipeline.controller.vo.RegisterPipelineVersionVO;
import com.epam.pipeline.controller.vo.TaskGraphVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.entity.cluster.InstancePrice;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.pipeline.DocumentGenerationProperty;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.pipeline.PipelineApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

@Controller
@Api(value = "Pipelines")
public class PipelineController extends AbstractRestController {

    private static final int BYTES_IN_KB = 1024;
    @Autowired
    private PipelineApiService pipelineApiService;


    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String VERSION = "version";
    private static final String PATH = "path";
    private static final String KEEP_REPOSITORY = "keep_repository";
    private static final String RECURSIVE = "recursive";

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineController.class);

    @RequestMapping(value = "/pipeline/register", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Registers a new pipeline.",
            notes = "Registers a new pipeline.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Pipeline> registerPipeline(@RequestBody PipelineVO pipeline)
            throws GitClientException {
        return Result.success(pipelineApiService.create(pipeline));
    }

    @RequestMapping(value = "/pipeline/check", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Checks repository existence.",
            notes = "Checks repository existence.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<CheckRepositoryVO> checkPipelineRepository(@RequestBody CheckRepositoryVO checkRepositoryVO)
            throws GitClientException {
        return Result.success(pipelineApiService.check(checkRepositoryVO));
    }

    @RequestMapping(value = "/pipeline/update", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Updates a pipeline.",
            notes = "Updates a pipeline.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Pipeline> updatePipeline(@RequestBody PipelineVO pipeline) {
        return Result.success(pipelineApiService.update(pipeline));
    }

    @RequestMapping(value = "/pipeline/updateToken", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Updates pipeline token.",
            notes = "Updates pipeline token.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Pipeline> updatePipelineToken(@RequestBody PipelineVO pipeline) {
        return Result.success(pipelineApiService.updateToken(pipeline));
    }

    @RequestMapping(value = "/pipeline/loadAll", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Lists all registered pipelines.",
            notes = "Lists all registered pipelines.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<Pipeline>> loadAllPipelines(
            @RequestParam(defaultValue = "false") Boolean loadVersion) {
        return Result.success(pipelineApiService.loadAllPipelines(loadVersion));
    }

    @GetMapping(value = "/pipeline/permissions")
    @ResponseBody
    @ApiOperation(
            value = "Lists all registered pipelines with permissions.",
            notes = "Lists all registered pipelines with permissions.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<PipelinesWithPermissionsVO> loadAllPipelinesWithPermissions(
            @RequestParam(required = false) final Integer pageNum,
            @RequestParam(required = false) final Integer pageSize) {
        return Result.success(pipelineApiService.loadAllPipelinesWithPermissions(pageNum, pageSize));
    }

    @RequestMapping(value = "/pipeline/{id}/load", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns a pipeline, specified by ID.",
            notes = "Returns a pipeline, specified by ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Pipeline> loadPipeline(@PathVariable(value = ID) final Long id) {
        return Result.success(pipelineApiService.load(id));
    }

    @RequestMapping(value = "/pipeline/find", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns a pipeline, specified by ID or name.",
            notes = "Returns a pipeline, specified by ID or name.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Pipeline> findPipeline(@RequestParam(value = ID) final String identifier) {
        return Result.success(pipelineApiService.loadPipelineByIdOrName(identifier));
    }

    @RequestMapping(value = "/pipeline/{id}/delete", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes a pipeline, specified by ID.",
            notes = "Deletes a pipeline, specified by ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Pipeline> deletePipeline(@PathVariable(value = ID) final Long id,
                                           @RequestParam(value = KEEP_REPOSITORY, required = false)
                                           final boolean keepRepository) {
        return Result.success(pipelineApiService.delete(id, keepRepository));
    }

    @RequestMapping(value = "/pipeline/{id}/runs", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Loads all pipeline runs for a specified pipeline.",
            notes = "Loads all pipeline runs for a specified pipeline.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<PipelineRun>> loadRunsByPipeline(@PathVariable(value = ID) final Long id) {
        return Result.success(pipelineApiService.loadAllRunsByPipeline(id));
    }

    @RequestMapping(value = "/pipeline/{id}/versions", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Loads all pipeline versions for a specified pipeline.",
            notes = "Loads all pipeline versions for a specified pipeline.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<Revision>> loadVersionsByPipeline(@PathVariable(value = ID) final Long id)
            throws GitClientException {
        return Result.success(pipelineApiService.loadAllVersionFromGit(id, null));
    }


    @RequestMapping(value = "/pipeline/{id}/version", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns a pipeline version, specified by ID.",
            notes = "Returns a pipeline version, specified by ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<GitTagEntry> loadPipelineVersion(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = VERSION) final String version) throws GitClientException {
        return Result.success(pipelineApiService.loadRevision(id, version));
    }


    @RequestMapping(value = "/pipeline/{id}/clone", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns pipeline clone URL.",
            notes = "Returns pipeline clone URL.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<String> getPipelineCloneURL(
            @PathVariable(value = ID) final Long id) {
        return Result.success(pipelineApiService.getPipelineCloneUrl(id));
    }

    @RequestMapping(value = "/pipeline/git/credentials", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
        value = "Returns user's git credentials for internal Gitlab.",
        notes = "Returns user's git credentials for internal Gitlab.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<GitCredentials> getPipelineCredentials(@RequestParam(required = false) Long duration) {
        return Result.success(pipelineApiService.getPipelineCredentials(duration));
    }


    @RequestMapping(value = "/pipeline/{id}/price", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Gets estimated price for pipeline run.",
            notes = "Gets estimated price for pipeline run.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<InstancePrice> getPipelineEstimatedPrice(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = VERSION) final String version,
            @RequestParam(required = false) final String config,
            @RequestBody InstanceOfferParametersVO instanceOfferParametersVO) throws GitClientException {
        return Result.success(pipelineApiService.getInstanceEstimatedPrice(id, version, config,
                        instanceOfferParametersVO.getInstanceType(),
                        instanceOfferParametersVO.getInstanceDisk(),
                        instanceOfferParametersVO.getSpot(),
                        instanceOfferParametersVO.getRegionId()));
    }

    @RequestMapping(value = "/pipeline/price", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Gets estimated price for run.",
            notes = "Gets estimated price for run.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<InstancePrice> getEstimatedPrice(
            @RequestBody InstanceOfferParametersVO instanceOfferParametersVO) {
        return Result.success(
                        pipelineApiService.getInstanceEstimatedPrice(
                                instanceOfferParametersVO.getInstanceType(),
                                instanceOfferParametersVO.getInstanceDisk(),
                                instanceOfferParametersVO.getSpot(),
                                instanceOfferParametersVO.getRegionId()));
    }

    @RequestMapping(value = "/pipeline/{id}/graph", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns a workflow graph for a specified version.",
            notes = "Returns a workflow graph for a specified version.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<TaskGraphVO> getWorkflowGraph(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = VERSION) final String version) {
        return Result.success(pipelineApiService.getWorkflowGraph(id, version));
    }

    @RequestMapping(value = "/pipeline/{id}/sources", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Gets list of source files of pipeline version.",
            notes = "Gets list of source files of pipeline version, specified by ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<GitRepositoryEntry>> getPipelineSources(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = VERSION) final String version,
            @RequestParam(value = PATH, required = false) final String path,
            @RequestParam(value = RECURSIVE, required = false) final boolean recursive) throws
            GitClientException {
        return Result.success(pipelineApiService.getPipelineSources(
                id,
                version,
                path,
                true,
                recursive));
    }

    @RequestMapping(value = "/pipeline/{id}/folder", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Creates or renames pipeline update.",
            notes = "Creates or renames pipeline update.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<GitCommitEntry> createOrRenamePipelineFolder(
            @PathVariable(value = ID) Long id,
            @RequestBody PipelineSourceItemVO folderVO) throws
            GitClientException {
        return Result.success(pipelineApiService.createOrRenameFolder(id, folderVO));
    }

    @RequestMapping(value = "/pipeline/{id}/folder", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Removes pipeline update.",
            notes = "Removes pipeline update.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<GitCommitEntry> removeFolder(
            @PathVariable(value = ID) Long id,
            @RequestBody PipelineSourceItemVO folderVO) throws
            GitClientException {
        return Result.success(pipelineApiService.removeFolder(id, folderVO.getPath(),
                folderVO.getLastCommitId(), folderVO.getComment()));
    }

    @RequestMapping(value = "/pipeline/{id}/docs", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Gets list of docs files of pipeline version.",
            notes = "Gets list of docs files of pipeline version, specified by ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<GitRepositoryEntry>> getPipelineDocs(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = VERSION) final String version) throws GitClientException {
        return Result.success(pipelineApiService.getPipelineDocs(id, version));
    }

    @RequestMapping(value = "/pipeline/{id}/file", method= RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Gets file content",
            notes = "Gets content of the file, specified by path in the repository and pipeline version ID. " +
                    "The file content is returned Base64 encoded",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public ResponseEntity<byte[]> getPipelineFile(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = VERSION) final String version,
            @RequestParam String path) throws GitClientException {
        return new ResponseEntity<>(pipelineApiService.getPipelineFileContents(id, version, path), HttpStatus.OK);
    }

    @GetMapping(value = "/pipeline/{id}/file/truncate")
    @ResponseBody
    @ApiOperation(
        value = "Truncate first bytes of a file content",
        notes = "Gets first bytes of content of the file, specified by path in the repository and pipeline "
                + "version ID. The file content is returned Base64 encoded",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public ResponseEntity<byte[]> getTruncatedPipelineFile(
        @PathVariable(value = ID) Long id,
        @RequestParam(value = VERSION) final String version,
        @RequestParam String path,
        @RequestParam Integer byteLimit) throws GitClientException {
        return new ResponseEntity<>(pipelineApiService.getTruncatedPipelineFileContent(id, version, path, byteLimit),
                                    HttpStatus.OK);
    }

    @RequestMapping(value = "/pipeline/{id}/file", method= RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Creates, updates or moves a file",
            notes = "Creates, updates or moves a  file",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<GitCommitEntry> modifyPipelineFile(
            @PathVariable(value = ID) Long id,
            @RequestBody PipelineSourceItemVO sourceItemVO) throws GitClientException {
        return Result.success(pipelineApiService.modifyFile(id, sourceItemVO));
    }

    @RequestMapping(value = "/pipeline/{id}/files", method= RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Creates, updates or moves files",
            notes = "Creates, updates or moves files",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<GitCommitEntry> modifyPipelineFiles(
            @PathVariable(value = ID) Long id,
            @RequestBody PipelineSourceItemsVO sourceItemsVO) throws GitClientException {
        return Result.success(pipelineApiService.modifyFiles(id, sourceItemsVO));
    }

    @RequestMapping(value = "/pipeline/{id}/file/upload", method= RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Uploads a file.",
            notes = "Uploads a file.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public List<UploadFileMetadata> uploadFile(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = PATH) final String folder,
            HttpServletRequest request) throws GitClientException, FileUploadException {
        MultipartFile file = consumeMultipartFile(request);

        List<UploadFileMetadata> uploadedFiles = new LinkedList<>();
        UploadFileMetadata fileMeta = new UploadFileMetadata();
        fileMeta.setFileName(FilenameUtils.getName(file.getOriginalFilename()).replaceAll("[ ]", "_"));
        fileMeta.setFileSize(file.getSize() / BYTES_IN_KB + " Kb");
        fileMeta.setFileType(file.getContentType());

        try {
            fileMeta.setBytes(file.getBytes());
            uploadedFiles.add(fileMeta);
        } catch (IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }

        pipelineApiService.uploadFiles(id, folder, uploadedFiles);
        uploadedFiles.forEach(f -> f.setBytes(null));
        return uploadedFiles;
    }

    @RequestMapping(value = "/pipeline/{id}/file", method= RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes a file",
            notes = "Deletes a file",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<GitCommitEntry> deletePipelineFile(
            @PathVariable(value = ID) Long id,
            @RequestBody PipelineSourceItemVO sourceItemVO) throws GitClientException {
        return Result.success(
                pipelineApiService.deleteFile(id, sourceItemVO.getPath(),
                        sourceItemVO.getLastCommitId(), sourceItemVO.getComment()));
    }

    @RequestMapping(value = "/pipeline/{id}/file/download", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Gets file content",
            notes = "Gets the file, specified by path in the repository and pipeline version ID. The file",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public void downloadPipelineFile(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = VERSION) final String version,
            @RequestParam String path, HttpServletResponse response) throws GitClientException, IOException {
        byte[] bytes = pipelineApiService.getPipelineFileContents(id, version, path);
        String name = FilenameUtils.getName(path);
        writeFileToResponse(response, bytes, name);
    }

    @RequestMapping(value = "/pipeline/{id}/file/generate", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Gets file content",
            notes = "Gets content of the file, specified by path in the repository and pipeline version ID. The file " +
                    "content is returned Base64 encoded",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public void generateFileByTemplate(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = VERSION) final String version,
            @RequestParam String path, @RequestBody GenerateFileVO generateFileVO, HttpServletResponse response)
                throws IOException {
        byte[] bytes = pipelineApiService.fillTemplateForPipelineVersion(id, version, path, generateFileVO);
        String name = FilenameUtils.getName(path);
        writeFileToResponse(response, bytes, name);
    }

    @RequestMapping(value = "/pipeline/version/register", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Registers a new pipeline version.",
            notes = "Registers a new pipeline version.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Revision> registerPipelineVersion(@RequestBody RegisterPipelineVersionVO registerPipelineVersionVO)
            throws GitClientException {
        return Result.success(pipelineApiService.registerPipelineVersion(registerPipelineVersionVO));
    }

    @RequestMapping(value = "/pipeline/{id}/template/properties", method= RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Gets pipeline document generation properties",
            notes = "Gets pipeline document generation properties, specified by pipeline ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<DocumentGenerationProperty>> getPipelineDocumentGenerationProperties(
            @PathVariable(value = ID) Long id) {
        return Result.success(pipelineApiService.loadAllPropertiesByPipelineId(id));
    }

    @RequestMapping(value = "/pipeline/{id}/template/properties/{name}", method= RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Gets pipeline document generation property",
            notes = "Gets pipeline document generation property, specified by name and pipeline ID.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DocumentGenerationProperty> getPipelineDocumentGenerationProperty(
            @PathVariable(value = ID) Long id, @PathVariable(value = NAME) String name) {
        return Result.success(pipelineApiService.loadProperty(name, id));
    }

    @RequestMapping(value = "/pipeline/template/properties", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Creates or updates pipeline document generation property.",
            notes = "Creates or updates pipeline document generation property.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DocumentGenerationProperty> savePipelineDocumentGenerationProperty(
            @RequestBody DocumentGenerationProperty property) {
        return Result.success(pipelineApiService.saveProperty(property));
    }

    @RequestMapping(value = "/pipeline/template/properties", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Deletes pipeline document generation property.",
            notes = "Deletes pipeline document generation property.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<DocumentGenerationProperty> deletePipelineDocumentGenerationProperty(
            @RequestBody DocumentGenerationProperty property) {
        return Result.success(pipelineApiService.deleteProperty(
                property.getPropertyName(), property.getPipelineId()));
    }

    @RequestMapping(value = "/pipeline/findByUrl", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns a pipeline, specified by repository URL.",
            notes = "Returns a pipeline, specified by repository URL.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Pipeline> findPipelineByRepoUrl(@RequestParam String url) {
        return Result.success(pipelineApiService.loadPipelineByRepoUrl(url));
    }

    @PostMapping("/pipeline/{id}/addHook")
    @ResponseBody
    @ApiOperation(
            value = "Add webhook to pipeline repository.",
            notes = "Add webhook to pipeline repository.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<GitRepositoryEntry> addHookToPipelineRepository(@PathVariable(value = ID) Long id)
            throws GitClientException {
        return Result.success(pipelineApiService.addHookToPipelineRepository(id));
    }

    @RequestMapping(value = "/pipeline/{id}/repository", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Loads all pipeline repository content.",
            notes = "Loads all pipeline repository content.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<GitRepositoryEntry>> loadRepositoryContent(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = VERSION) final String version,
            @RequestParam(value = PATH) final String path) throws GitClientException {
        return Result.success(pipelineApiService.getPipelineRepositoryContents(id, version, path));
    }
}
