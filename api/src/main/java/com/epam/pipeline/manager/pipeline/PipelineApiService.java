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

import com.epam.pipeline.controller.vo.CheckRepositoryVO;
import com.epam.pipeline.controller.vo.GenerateFileVO;
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
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.security.acl.AclMask;
import com.epam.pipeline.manager.security.acl.AclMaskList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;
import static com.epam.pipeline.security.acl.AclExpressions.PIPELINE_ID_READ;
import static com.epam.pipeline.security.acl.AclExpressions.PIPELINE_ID_WRITE;
import static com.epam.pipeline.security.acl.AclExpressions.PIPELINE_VO_WRITE;

@Service
public class PipelineApiService {

    @Autowired
    private PipelineManager pipelineManager;

    @Autowired
    private PipelineVersionManager versionManager;

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private InstanceOfferManager instanceOfferManager;

    @Autowired
    private GitManager gitManager;

    @Autowired
    private PipelineFileGenerationManager fileGenerationManager;

    @Autowired
    private DocumentGenerationPropertyManager documentGenerationPropertyManager;

    @Autowired
    private GrantPermissionManager permissionManager;

    @PreAuthorize("hasRole('ADMIN') OR "
            + "(#pipeline.parentFolderId != null AND hasRole('PIPELINE_MANAGER') AND "
            + "hasPermission(#pipeline.parentFolderId, 'com.epam.pipeline.entity.pipeline.Folder', 'WRITE'))")
    public Pipeline create(final PipelineVO pipeline) throws GitClientException {
        return pipelineManager.create(pipeline);
    }

    public CheckRepositoryVO check(CheckRepositoryVO checkRepositoryVO) throws GitClientException {
        return pipelineManager.check(checkRepositoryVO);
    }

    @PreAuthorize(PIPELINE_VO_WRITE)
    @AclMask
    public Pipeline update(final PipelineVO pipeline) {
        return pipelineManager.update(pipeline);
    }

    @PreAuthorize(PIPELINE_VO_WRITE)
    @AclMask
    public Pipeline updateToken(final PipelineVO pipeline) {
        return pipelineManager.updateToken(pipeline);
    }

    @PostFilter("hasRole('ADMIN') OR hasPermission(filterObject, 'READ')")
    @AclMaskList
    public List<Pipeline> loadAllPipelines(boolean loadVersions) {
        return pipelineManager.loadAllPipelines(loadVersions);
    }

    @PreAuthorize(ADMIN_ONLY)
    public PipelinesWithPermissionsVO loadAllPipelinesWithPermissions(Integer pageNum, Integer pageSize) {
        return permissionManager.loadAllPipelinesWithPermissions(pageNum, pageSize);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    @AclMask
    public Pipeline load(Long id) {
        return pipelineManager.load(id, true);
    }

    @PostAuthorize("hasRole('ADMIN') OR hasPermission(returnObject, 'READ')")
    @AclMask
    public Pipeline loadPipelineByIdOrName(String identifier) {
        return pipelineManager.loadByNameOrId(identifier);
    }

    @PostFilter("hasRole('ADMIN') OR hasPermission(filterObject, 'READ')")
    @AclMaskList
    public List<Pipeline> loadAllPipelinesWithoutVersion() {
        return pipelineManager.loadAllPipelines(false);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('PIPELINE_MANAGER') "
            + "AND hasPermission(#id, 'com.epam.pipeline.entity.pipeline.Pipeline', 'WRITE'))")
    public Pipeline delete(Long id, boolean keepRepository) {
        return pipelineManager.delete(id, keepRepository);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    @AclMaskList
    public List<PipelineRun> loadAllRunsByPipeline(Long id) {
        return pipelineRunManager.loadAllRunsByPipeline(id);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    public List<Revision> loadAllVersionFromGit(Long id, Long pageSize) throws GitClientException {
        return versionManager.loadAllVersionFromGit(id, pageSize);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    public GitTagEntry loadRevision(Long id, String version) throws GitClientException {
        return gitManager.loadRevision(pipelineManager.load(id), version);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    public InstancePrice getInstanceEstimatedPrice(Long id, String version, String configName,
            String instanceType, int instanceDisk, Boolean spot, Long regionId)
            throws GitClientException {
        return instanceOfferManager.getInstanceEstimatedPrice(id, version, configName,
                instanceType, instanceDisk, spot, regionId);
    }

    public InstancePrice getInstanceEstimatedPrice(String instanceType, int instanceDisk,
                                                   Boolean spot, Long regionId) {
        return instanceOfferManager.getInstanceEstimatedPrice(instanceType, instanceDisk, spot, regionId);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    public TaskGraphVO getWorkflowGraph(Long id, String version) {
        return versionManager.getWorkflowGraph(id, version);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    public List<GitRepositoryEntry> getPipelineSources(Long id, String version, String path,
            boolean appendConfigurationFileIfNeeded, boolean recursive) throws GitClientException {
        return gitManager.getPipelineSources(id, version, path, appendConfigurationFileIfNeeded, recursive);
    }

    @PreAuthorize(PIPELINE_ID_WRITE)
    public GitCommitEntry createOrRenameFolder(Long id, PipelineSourceItemVO folderVO)
            throws GitClientException {
        return gitManager.createOrRenameFolder(id, folderVO);
    }

    @PreAuthorize(PIPELINE_ID_WRITE)
    public GitCommitEntry removeFolder(Long id, String folder, String lastCommitId, String commitMessage)
            throws GitClientException {
        return gitManager.removeFolder(pipelineManager.load(id, true), folder, lastCommitId, commitMessage);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    public List<GitRepositoryEntry> getPipelineDocs(Long id, String version)
            throws GitClientException {
        return gitManager.getPipelineDocs(id, version);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    public byte[] getPipelineFileContents(Long id, String version, String path)
            throws GitClientException {
        return gitManager.getPipelineFileContents(pipelineManager.load(id), version, path);
    }

    @PreAuthorize(PIPELINE_ID_WRITE)
    public GitCommitEntry modifyFile(Long id, PipelineSourceItemVO sourceItemVO) throws GitClientException {
        return gitManager.modifyFile(pipelineManager.load(id, true), sourceItemVO);
    }

    @PreAuthorize(PIPELINE_ID_WRITE)
    public GitCommitEntry modifyFiles(Long id, PipelineSourceItemsVO sourceItemsVO) throws GitClientException {
        return gitManager.updateFiles(pipelineManager.load(id, true), sourceItemsVO);
    }

    @PreAuthorize(PIPELINE_ID_WRITE)
    public GitCommitEntry uploadFiles(Long id, String folder, List<UploadFileMetadata> files)
            throws GitClientException {
        Pipeline pipeline = pipelineManager.load(id, true);
        return gitManager.uploadFiles(pipeline, folder, files,
                pipeline.getCurrentVersion().getCommitId(), null);
    }

    @PreAuthorize(PIPELINE_ID_WRITE)
    public GitCommitEntry deleteFile(Long id, String filePath, String lastCommitId, String commitMessage)
            throws GitClientException {
        return gitManager.deleteFile(pipelineManager.load(id, true), filePath, lastCommitId, commitMessage);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    public byte[] fillTemplateForPipelineVersion(Long id,
            String pipelineVersion,
            String templatePath,
            GenerateFileVO generateFileVO) {
        return fileGenerationManager
                .fillTemplateForPipelineVersion(id, pipelineVersion, templatePath, generateFileVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#registerPipelineVersionVO.pipelineId, "
            + "'com.epam.pipeline.entity.pipeline.Pipeline', 'WRITE')")
    public Revision registerPipelineVersion(final RegisterPipelineVersionVO registerPipelineVersionVO)
            throws GitClientException {
        return versionManager.registerPipelineVersion(registerPipelineVersionVO);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    public String getPipelineCloneUrl(Long id) {
        return pipelineManager.getPipelineCloneUrl(id);
    }

    public GitCredentials getPipelineCredentials(Long duration) {
        return gitManager.getGitlabCredentials(duration);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    public List<DocumentGenerationProperty> loadAllPropertiesByPipelineId(Long id) {
        return documentGenerationPropertyManager.loadAllPropertiesByPipelineId(id);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    public DocumentGenerationProperty loadProperty(String name, Long id) {
        return documentGenerationPropertyManager.loadProperty(name, id);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "hasPermission(#property.pipelineId, 'com.epam.pipeline.entity.pipeline.Pipeline', 'WRITE')")
    public DocumentGenerationProperty saveProperty(DocumentGenerationProperty property) {
        return documentGenerationPropertyManager.saveProperty(property);
    }

    @PreAuthorize(PIPELINE_ID_WRITE)
    public DocumentGenerationProperty deleteProperty(String name, Long id) {
        return documentGenerationPropertyManager.deleteProperty(name, id);
    }

    @PostAuthorize("hasRole('ADMIN') OR hasPermission(returnObject, 'READ')")
    @AclMask
    public Pipeline loadPipelineByRepoUrl(String url) {
        return pipelineManager.loadByRepoUrl(url);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('PIPELINE_MANAGER') AND " +
            "hasPermission(#id, 'com.epam.pipeline.entity.pipeline.Pipeline', 'WRITE'))")
    public GitRepositoryEntry addHookToPipelineRepository(Long id) throws GitClientException {
        return gitManager.addHookToPipelineRepository(id);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    public List<GitRepositoryEntry> getPipelineRepositoryContents(Long id, String version, String path)
            throws GitClientException {
        return gitManager.getRepositoryContents(id, version, path);
    }
}
