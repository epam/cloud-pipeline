/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.controller.vo.PipelineSourceItemRevertVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemsVO;
import com.epam.pipeline.controller.vo.PipelineVO;
import com.epam.pipeline.controller.vo.PipelinesWithPermissionsVO;
import com.epam.pipeline.controller.vo.RegisterPipelineVersionVO;
import com.epam.pipeline.controller.vo.TaskGraphVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.controller.vo.EntityFilterVO;
import com.epam.pipeline.entity.cluster.InstancePrice;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCommitsFilter;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.git.report.GitDiffReportFilter;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiff;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiffEntry;
import com.epam.pipeline.entity.git.gitreader.GitReaderEntryIteratorListing;
import com.epam.pipeline.entity.git.gitreader.GitReaderEntryListing;
import com.epam.pipeline.entity.git.gitreader.GitReaderLogsPathFilter;
import com.epam.pipeline.entity.git.gitreader.GitReaderObject;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommit;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryLogEntry;
import com.epam.pipeline.entity.git.report.VersionStorageReportFile;
import com.epam.pipeline.entity.pipeline.DocumentGenerationProperty;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineWithMetadata;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.acl.pipeline.PipelineApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

@Controller
@Tag(name = "pipeline-controller", description = "Pipelines")
public class PipelineController extends AbstractRestController {

    private static final int BYTES_IN_KB = 1024;
    private static final String INCLUDE_DIFF = "include_diff";
    private static final String COMMIT = "commit";
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String VERSION = "version";
    private static final String PATH = "path";
    private static final String PAGE = "page";
    private static final String PAGE_SIZE = "page_size";
    private static final String KEEP_REPOSITORY = "keep_repository";
    private static final String RECURSIVE = "recursive";
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineController.class);
    @Autowired
    private PipelineApiService pipelineApiService;

    @RequestMapping(value = "/pipeline/register", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Registers a new pipeline.",
            description = "Registers a new pipeline.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Pipeline> registerPipeline(@RequestBody PipelineVO pipeline)
            throws GitClientException {
        return Result.success(pipelineApiService.create(pipeline));
    }

    @RequestMapping(value = "/pipeline/check", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Checks repository existence.",
            description = "Checks repository existence.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<CheckRepositoryVO> checkPipelineRepository(@RequestBody CheckRepositoryVO checkRepositoryVO)
            throws GitClientException {
        return Result.success(pipelineApiService.check(checkRepositoryVO));
    }

    @RequestMapping(value = "/pipeline/update", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Updates a pipeline.",
            description = "Updates a pipeline.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Pipeline> updatePipeline(@RequestBody PipelineVO pipeline) {
        return Result.success(pipelineApiService.update(pipeline));
    }

    @RequestMapping(value = "/pipeline/updateToken", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Updates pipeline token.",
            description = "Updates pipeline token.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Pipeline> updatePipelineToken(@RequestBody PipelineVO pipeline) {
        return Result.success(pipelineApiService.updateToken(pipeline));
    }

    @RequestMapping(value = "/pipeline/loadAll", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Lists all registered pipelines.",
            description = "Lists all registered pipelines.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<Pipeline>> loadAllPipelines(
            @RequestParam(defaultValue = "false") Boolean loadVersion) {
        return Result.success(pipelineApiService.loadAllPipelines(loadVersion));
    }

    @PostMapping("/pipeline/filter")
    @ResponseBody
    @Operation(
            summary = "Loads all registered pipelines with specified filters.",
            description = "Loads all registered pipelines with specified filters.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<PipelineWithMetadata>> filterPipelines(
            @RequestBody final EntityFilterVO filter,
            @RequestParam(defaultValue = "false") final Boolean loadVersion,
            @RequestParam(defaultValue = "false") final boolean loadMetadata) {
        return Result.success(pipelineApiService.filterPipelines(loadVersion, loadMetadata, filter));
    }

    @GetMapping(value = "/pipeline/permissions")
    @ResponseBody
    @Operation(
            summary = "Lists all registered pipelines with permissions.",
            description = "Lists all registered pipelines with permissions.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PipelinesWithPermissionsVO> loadAllPipelinesWithPermissions(
            @RequestParam(required = false) final Integer pageNum,
            @RequestParam(required = false) final Integer pageSize) {
        return Result.success(pipelineApiService.loadAllPipelinesWithPermissions(pageNum, pageSize));
    }

    @RequestMapping(value = "/pipeline/{id}/load", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns a pipeline, specified by ID.",
            description = "Returns a pipeline, specified by ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Pipeline> loadPipeline(@PathVariable(value = ID) final Long id) {
        return Result.success(pipelineApiService.load(id));
    }

    @RequestMapping(value = "/pipeline/find", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns a pipeline, specified by ID or name.",
            description = "Returns a pipeline, specified by ID or name.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Pipeline> findPipeline(@RequestParam(value = ID) final String identifier) {
        return Result.success(pipelineApiService.loadPipelineByIdOrName(identifier));
    }

    @RequestMapping(value = "/pipeline/{id}/delete", method = RequestMethod.DELETE)
    @ResponseBody
    @Operation(
            summary = "Deletes a pipeline, specified by ID.",
            description = "Deletes a pipeline, specified by ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Pipeline> deletePipeline(@PathVariable(value = ID) final Long id,
                                           @RequestParam(value = KEEP_REPOSITORY, required = false)
                                           final boolean keepRepository) {
        return Result.success(pipelineApiService.delete(id, keepRepository));
    }

    @RequestMapping(value = "/pipeline/{id}/runs", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Loads all pipeline runs for a specified pipeline.",
            description = "Loads all pipeline runs for a specified pipeline.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<PipelineRun>> loadRunsByPipeline(@PathVariable(value = ID) final Long id) {
        return Result.success(pipelineApiService.loadAllRunsByPipeline(id));
    }

    @RequestMapping(value = "/pipeline/{id}/versions", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Loads all pipeline versions for a specified pipeline.",
            description = "Loads all pipeline versions for a specified pipeline.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<Revision>> loadVersionsByPipeline(@PathVariable(value = ID) final Long id)
            throws GitClientException {
        return Result.success(pipelineApiService.loadAllVersionFromGit(id));
    }


    @RequestMapping(value = "/pipeline/{id}/version", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns a pipeline version, specified by ID.",
            description = "Returns a pipeline version, specified by ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitTagEntry> loadPipelineVersion(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = VERSION) final String version) throws GitClientException {
        return Result.success(pipelineApiService.loadRevision(id, version));
    }


    @RequestMapping(value = "/pipeline/{id}/clone", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns pipeline clone URL.",
            description = "Returns pipeline clone URL.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<String> getPipelineCloneURL(
            @PathVariable(value = ID) final Long id) {
        return Result.success(pipelineApiService.getPipelineCloneUrl(id));
    }

    @RequestMapping(value = "/pipeline/git/credentials", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
        summary = "Returns user's git credentials for internal Gitlab.", 
        description = "Returns user's git credentials for internal Gitlab.")
    @ApiResponses(
        value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
        })
    public Result<GitCredentials> getPipelineCredentials(@RequestParam(required = false) Long duration) {
        return Result.success(pipelineApiService.getPipelineCredentials(duration));
    }


    @RequestMapping(value = "/pipeline/{id}/price", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Gets estimated price for pipeline run.",
            description = "Gets estimated price for pipeline run.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
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
    @Operation(
            summary = "Gets estimated price for run.",
            description = "Gets estimated price for run.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
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
    @Operation(
            summary = "Returns a workflow graph for a specified version.",
            description = "Returns a workflow graph for a specified version.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<TaskGraphVO> getWorkflowGraph(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = VERSION) final String version) {
        return Result.success(pipelineApiService.getWorkflowGraph(id, version));
    }

    @RequestMapping(value = "/pipeline/{id}/sources", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Gets list of source files of pipeline version.",
            description = "Gets list of source files of pipeline version, specified by ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
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
    @Operation(
            summary = "Creates or renames pipeline folder.",
            description = "Creates or renames pipeline folder.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitCommitEntry> createOrRenamePipelineFolder(
            @PathVariable(value = ID) Long id,
            @RequestBody PipelineSourceItemVO folderVO) throws
            GitClientException {
        return Result.success(pipelineApiService.createOrRenameFolder(id, folderVO));
    }

    @RequestMapping(value = "/pipeline/{id}/folder", method = RequestMethod.DELETE)
    @ResponseBody
    @Operation(
            summary = "Removes pipeline update.",
            description = "Removes pipeline update.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
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
    @Operation(
            summary = "Gets list of docs files of pipeline version.",
            description = "Gets list of docs files of pipeline version, specified by ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<GitRepositoryEntry>> getPipelineDocs(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = VERSION) final String version) throws GitClientException {
        return Result.success(pipelineApiService.getPipelineDocs(id, version));
    }

    @RequestMapping(value = "/pipeline/{id}/file", method= RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Gets file content",
            description = "Gets content of the file, specified by path in the repository and pipeline version ID. " +
                    "The file content is returned Base64 encoded")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public ResponseEntity<byte[]> getPipelineFile(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = VERSION) final String version,
            @RequestParam String path) throws GitClientException {
        final byte[] bytes = pipelineApiService.getPipelineFileContents(id, version, path);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(toBase64Json(bytes).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @GetMapping(value = "/pipeline/{id}/file/truncate")
    @ResponseBody
    @Operation(
        summary = "Truncate first bytes of a file content",
        description = "Gets first bytes of content of the file, specified by path in the repository and pipeline "
                + "version ID. The file content is returned Base64 encoded")
    @ApiResponses(
        value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
        })
    public ResponseEntity<byte[]> getTruncatedPipelineFile(
        @PathVariable(value = ID) Long id,
        @RequestParam(value = VERSION) final String version,
        @RequestParam String path,
        @RequestParam Integer byteLimit) throws GitClientException {
        final byte[] bytes = pipelineApiService.getTruncatedPipelineFileContent(id, version, path, byteLimit);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(toBase64Json(bytes).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String toBase64Json(final byte[] bytes) {
        // Jackson's byte[] JSON representation is a base64 JSON string, e.g. "AQEB".
        // We return the same shape explicitly to keep legacy tests stable.
        return "\"" + Base64.getEncoder().encodeToString(bytes == null ? new byte[0] : bytes) + "\"";
    }

    @RequestMapping(value = "/pipeline/{id}/file", method= RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Creates, updates or moves a file",
            description = "Creates, updates or moves a  file")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitCommitEntry> modifyPipelineFile(
            @PathVariable(value = ID) Long id,
            @RequestBody PipelineSourceItemVO sourceItemVO) throws GitClientException {
        return Result.success(pipelineApiService.modifyFile(id, sourceItemVO));
    }

    @RequestMapping(value = "/pipeline/{id}/file/revert", method= RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Revert a given file to specific commit",
            description = "Revert a given file to specific commit")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitCommitEntry> revertPipelineFile(
            @PathVariable(value = ID) Long id,
            @RequestBody PipelineSourceItemRevertVO sourceItemRevertVO) throws GitClientException {
        return Result.success(pipelineApiService.revertFile(id, sourceItemRevertVO));
    }

    @RequestMapping(value = "/pipeline/{id}/files", method= RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Creates, updates or moves files",
            description = "Creates, updates or moves files")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitCommitEntry> modifyPipelineFiles(
            @PathVariable(value = ID) Long id,
            @RequestBody PipelineSourceItemsVO sourceItemsVO) throws GitClientException {
        return Result.success(pipelineApiService.modifyFiles(id, sourceItemsVO));
    }

    @RequestMapping(value = "/pipeline/{id}/file/upload", method= RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Uploads a file.",
            description = "Uploads a file.")
    public List<UploadFileMetadata> uploadFile(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = PATH) final String folder,
            HttpServletRequest request) throws GitClientException, IOException {
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
    @Operation(
            summary = "Deletes a file",
            description = "Deletes a file")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
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
    @Operation(
            summary = "Gets file content",
            description = "Gets the file, specified by path in the repository and pipeline version ID. The file")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
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
    @Operation(
            summary = "Gets file content",
            description = "Gets content of the file, specified by path in the repository and pipeline version ID. " +
                    "The file content is returned Base64 encoded")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
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
    @Operation(
            summary = "Registers a new pipeline version.",
            description = "Registers a new pipeline version.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Revision> registerPipelineVersion(@RequestBody RegisterPipelineVersionVO registerPipelineVersionVO)
            throws GitClientException {
        return Result.success(pipelineApiService.registerPipelineVersion(registerPipelineVersionVO));
    }

    @RequestMapping(value = "/pipeline/{id}/template/properties", method= RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Gets pipeline document generation properties",
            description = "Gets pipeline document generation properties, specified by pipeline ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<DocumentGenerationProperty>> getPipelineDocumentGenerationProperties(
            @PathVariable(value = ID) Long id) {
        return Result.success(pipelineApiService.loadAllPropertiesByPipelineId(id));
    }

    @RequestMapping(value = "/pipeline/{id}/template/properties/{name}", method= RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Gets pipeline document generation property",
            description = "Gets pipeline document generation property, specified by name and pipeline ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<DocumentGenerationProperty> getPipelineDocumentGenerationProperty(
            @PathVariable(value = ID) Long id, @PathVariable(value = NAME) String name) {
        return Result.success(pipelineApiService.loadProperty(name, id));
    }

    @RequestMapping(value = "/pipeline/template/properties", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Creates or updates pipeline document generation property.",
            description = "Creates or updates pipeline document generation property.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<DocumentGenerationProperty> savePipelineDocumentGenerationProperty(
            @RequestBody DocumentGenerationProperty property) {
        return Result.success(pipelineApiService.saveProperty(property));
    }

    @RequestMapping(value = "/pipeline/template/properties", method = RequestMethod.DELETE)
    @ResponseBody
    @Operation(
            summary = "Deletes pipeline document generation property.",
            description = "Deletes pipeline document generation property.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<DocumentGenerationProperty> deletePipelineDocumentGenerationProperty(
            @RequestBody DocumentGenerationProperty property) {
        return Result.success(pipelineApiService.deleteProperty(
                property.getPropertyName(), property.getPipelineId()));
    }

    @RequestMapping(value = "/pipeline/findByUrl", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns a pipeline, specified by repository URL.",
            description = "Returns a pipeline, specified by repository URL.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Pipeline> findPipelineByRepoUrl(@RequestParam String url) {
        return Result.success(pipelineApiService.loadPipelineByRepoUrl(url));
    }

    @PostMapping("/pipeline/{id}/addHook")
    @ResponseBody
    @Operation(
            summary = "Add webhook to pipeline repository.",
            description = "Add webhook to pipeline repository.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitRepositoryEntry> addHookToPipelineRepository(@PathVariable(value = ID) Long id)
            throws GitClientException {
        return Result.success(pipelineApiService.addHookToPipelineRepository(id));
    }

    @RequestMapping(value = "/pipeline/{id}/repository", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Loads all pipeline repository content.",
            description = "Loads all pipeline repository content.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<GitRepositoryEntry>> loadRepositoryContent(
            @PathVariable(value = ID) Long id,
            @RequestParam(value = VERSION) final String version,
            @RequestParam(value = PATH) final String path) throws GitClientException {
        return Result.success(pipelineApiService.getPipelineRepositoryContents(id, version, path));
    }

    @PostMapping(value = "/pipeline/{id}/copy")
    @ResponseBody
    @Operation(
            summary = "Copies specified pipeline.",
            description = "Copies specified pipeline.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Pipeline> copyPipeline(@PathVariable(ID) final Long id,
                                         @RequestParam(value = "parentId", required = false) final Long parentId,
                                         @RequestParam(value = "name", required = false) final String name) {
        return Result.success(pipelineApiService.copyPipeline(id, parentId, name));
    }

    @RequestMapping(value = "/pipeline/{id}/ls_tree", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "List pipeline repository content.",
            description = "List pipeline repository content.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitReaderEntryListing<GitReaderObject>> lsTreeRepositoryContent(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = VERSION, required = false) final String version,
            @RequestParam(value = PATH, required = false) final String path,
            @RequestParam(value = PAGE, required = false) final Long page,
            @RequestParam(value = PAGE_SIZE, required = false) final Integer pageSize) throws GitClientException {
        return Result.success(pipelineApiService.lsTreeRepositoryContent(id, version, path, page, pageSize));
    }

    @RequestMapping(value = "/pipeline/{id}/path", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns pipeline repository object.",
            description = "Returns pipeline repository object or throws exception of such path doesn't exists.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitReaderObject> lsTreeRepositoryObject(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = VERSION, required = false) final String version,
            @RequestParam(value = PATH, required = false) final String path) throws GitClientException {
        return Result.success(pipelineApiService.lsTreeRepositoryObject(id, version, path));
    }

    @RequestMapping(value = "/pipeline/{id}/logs_tree", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Lists pipeline repository content with last commit information.",
            description = "Lists pipeline repository content with last commit information.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitReaderEntryListing<GitReaderRepositoryLogEntry>> logsTreeRepositoryContent(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = VERSION, required = false) final String version,
            @RequestParam(value = PATH, required = false) final String path,
            @RequestParam(value = PAGE, required = false) final Long page,
            @RequestParam(value = PAGE_SIZE, required = false) final Integer pageSize) throws GitClientException {
        return Result.success(pipelineApiService.logsTreeRepositoryContent(id, version, path, page, pageSize));
    }

    @RequestMapping(value = "/pipeline/{id}/logs_tree", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Lists pipeline repository content with last commit information by specific paths.",
            description = "Lists pipeline repository content with last commit information by specific paths.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitReaderEntryListing<GitReaderRepositoryLogEntry>> logsTreeRepositoryContent(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = VERSION, required = false) final String version,
            @RequestBody final GitReaderLogsPathFilter paths) throws GitClientException {
        return Result.success(pipelineApiService.logsTreeRepositoryContent(id, version, paths));
    }

    @RequestMapping(value = "/pipeline/{id}/commits", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Loads commit information regarding specified filters.",
            description = "Loads commit information regarding specified filters.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitReaderEntryIteratorListing<GitReaderRepositoryCommit>> getRepositoryCommits(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = PAGE, required = false) final Long page,
            @RequestParam(value = PAGE_SIZE, required = false) final Integer pageSize,
            @RequestBody GitCommitsFilter filter) throws GitClientException {
        return Result.success(pipelineApiService.logRepositoryCommits(id, page, pageSize, filter));
    }

    @RequestMapping(value = "/pipeline/{id}/diff", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Loads commits and its diffs regarding to specified filters.",
            description = "Loads commits and its diffs regarding to specified filters.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitReaderDiff> getRepositoryCommitDiffs(
            @PathVariable(value = ID) final Long id,
            @RequestParam(value = INCLUDE_DIFF, required = false)  final Boolean includeDiff,
            @RequestBody final GitCommitsFilter filter) throws GitClientException {
        return Result.success(pipelineApiService.logRepositoryCommitDiffs(id, includeDiff, filter));
    }

    @RequestMapping(value = "/pipeline/{id}/diff/{commit}", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Loads commit diff regarding to specified sha and path.",
            description = "Loads commit diff regarding to specified sha and path.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitReaderDiffEntry> getRepositoryCommitDiff(
            @PathVariable(value = ID) final Long id,
            @PathVariable(value = COMMIT) final String commit,
            @RequestParam(value = PATH, required = false) final String path) throws GitClientException {
        return Result.success(pipelineApiService.getRepositoryCommitDiff(id, commit, path));
    }

    @RequestMapping(value = "/pipeline/{id}/report", method = RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Generate Version Storage Report",
            description = "Generate Version Storage Report, based on provided filters")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public void generateFileByTemplate(
            @PathVariable(value = ID) final Long id,
            @RequestBody final GitDiffReportFilter filter,
            final HttpServletResponse response) throws IOException {
        final VersionStorageReportFile report = pipelineApiService.generateReportForVersionedStorage(id, filter);
        writeFileToResponse(response, report.getContent(), report.getName());
    }
}
