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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.controller.vo.CheckRepositoryVO;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.PipelineVO;
import com.epam.pipeline.dao.datastorage.rules.DataStorageRuleDao;
import com.epam.pipeline.dao.pipeline.PipelineDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineType;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.git.PipelineRepositoryService;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import com.epam.pipeline.manager.security.acl.AclSync;
import com.epam.pipeline.utils.GitUtils;
import com.epam.pipeline.utils.PasswordGenerator;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@AclSync
public class PipelineManager implements SecuredEntityManager {

    @Value("${templates.default.template}")
    private String defaultTemplate;

    @Autowired
    private PipelineDao pipelineDao;

    @Autowired
    private PipelineCRUDManager crudManager;

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private DataStorageRuleDao dataStorageRuleDao;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private GitManager gitManager;

    @Autowired
    private AuthManager securityManager;

    @Autowired
    private FolderManager folderManager;

    @Autowired
    private MetadataManager metadataManager;

    @Autowired
    private RunScheduleManager runScheduleManager;

    @Autowired
    private PipelineRepositoryService pipelineRepositoryService;

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineManager.class);

    public Pipeline create(final PipelineVO pipelineVO) throws GitClientException {
        Assert.isTrue(GitUtils.checkGitNaming(pipelineVO.getName()),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_PIPELINE_NAME, pipelineVO.getName()));
        if (pipelineVO.getPipelineType() == null) {
            pipelineVO.setPipelineType(PipelineType.PIPELINE);
        }
        if (pipelineVO.getRepositoryType() == null) {
            pipelineVO.setRepositoryType(RepositoryType.GITLAB);
        }
        if (StringUtils.isEmpty(pipelineVO.getRepository())) {
            Assert.isTrue(RepositoryType.BITBUCKET != pipelineVO.getRepositoryType(),
                    "Bitbucket repository creation supported from urls only");
            Assert.isTrue(RepositoryType.BITBUCKET_CLOUD != pipelineVO.getRepositoryType(),
                    "Bitbucket Cloud repository creation supported from urls only");
            Assert.isTrue(!gitManager.checkProjectExists(pipelineVO.getName()),
                    messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_REPO_EXISTS, pipelineVO.getName()));
            final GitProject project = createGitRepository(pipelineVO);
            pipelineVO.setRepository(project.getRepoUrl());
            pipelineVO.setRepositorySsh(project.getRepoSsh());
        } else {
            CheckRepositoryVO checkRepositoryVO = new CheckRepositoryVO();
            checkRepositoryVO.setRepository(pipelineVO.getRepository());
            checkRepositoryVO.setToken(pipelineVO.getRepositoryToken());
            checkRepositoryVO.setType(pipelineVO.getRepositoryType());
            checkRepositoryVO.setBranch(pipelineVO.getBranch());
            checkRepositoryVO = check(checkRepositoryVO);
            if (!checkRepositoryVO.isRepositoryExists()) {
                GitProject project = pipelineRepositoryService.createGitRepositoryWithRepoUrl(pipelineVO);
                pipelineVO.setRepositorySsh(project.getRepoSsh());
            } else if (StringUtils.isEmpty(pipelineVO.getRepositorySsh())) {
                final GitProject project = pipelineRepositoryService.getRepository(pipelineVO.getRepositoryType(),
                        pipelineVO.getRepository(), pipelineVO.getRepositoryToken());
                checkBranchExists(pipelineVO);
                pipelineVO.setRepositorySsh(project.getRepoSsh());
            }
        }
        pipelineVO.setConfigurationPath(StringUtils.strip(pipelineVO.getConfigurationPath(), Constants.PATH_DELIMITER));
        Pipeline pipeline = pipelineVO.toPipeline();
        setFolderIfPresent(pipeline);
        pipeline.setOwner(securityManager.getAuthorizedUser());
        return crudManager.save(pipeline);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Pipeline createEmpty(final PipelineVO pipelineVO) throws GitClientException {
        Assert.isTrue(GitUtils.checkGitNaming(pipelineVO.getName()),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_PIPELINE_NAME, pipelineVO.getName()));
        Assert.isTrue(!gitManager.checkProjectExists(pipelineVO.getName()),
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_REPO_EXISTS, pipelineVO.getName()));
        final GitProject project = gitManager.createEmptyRepository(pipelineVO.getName(), pipelineVO.getDescription());
        pipelineVO.setRepository(project.getRepoUrl());
        pipelineVO.setRepositorySsh(project.getRepoSsh());
        final Pipeline pipeline = pipelineVO.toPipeline();
        setFolderIfPresent(pipeline);
        pipeline.setOwner(securityManager.getAuthorizedUser());
        return crudManager.save(pipeline);
    }

    public CheckRepositoryVO check(CheckRepositoryVO checkRepositoryVO) {
        if (StringUtils.isEmpty(checkRepositoryVO.getRepository())) {
            checkRepositoryVO.setRepositoryExists(false);
        } else {
            Pipeline checkPipeline = new Pipeline();
            checkPipeline.setRepository(checkRepositoryVO.getRepository());
            checkPipeline.setRepositoryToken(checkRepositoryVO.getToken());
            checkPipeline.setRepositoryType(checkRepositoryVO.getType());
            checkPipeline.setBranch(checkRepositoryVO.getBranch());
            setCurrentVersion(checkPipeline);
            if (StringUtils.isEmpty(checkPipeline.getRepositoryError())) {
                checkRepositoryVO.setRepositoryExists(true);
            } else {
                checkRepositoryVO.setRepositoryExists(false);
            }
        }
        return checkRepositoryVO;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Pipeline update(final PipelineVO pipelineVO) {
        String pipelineVOName = pipelineVO.getName();
        Assert.notNull(pipelineVOName, messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_NAME_IS_EMPTY));
        Assert.isTrue(GitUtils.checkGitNaming(pipelineVOName),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_PIPELINE_NAME, pipelineVOName));
        Pipeline dbPipeline = load(pipelineVO.getId());
        String previousName = dbPipeline.getName();
        final String currentProjectPath = dbPipeline.getRepository();
        final String currentProjectName = GitUtils.convertPipeNameToProject(dbPipeline.getName());
        final String newProjectName = GitUtils.convertPipeNameToProject(pipelineVOName);
        final boolean projectNameUpdated = !newProjectName.equals(currentProjectName);
        if (projectNameUpdated) {
            final String newRepository =
                GitUtils.replaceGitProjectNameInUrl(dbPipeline.getRepository(), newProjectName);
            final String newRepositorySsh =
                GitUtils.replaceGitProjectNameInUrl(dbPipeline.getRepositorySsh(), newProjectName);
            dbPipeline.setRepository(newRepository);
            dbPipeline.setRepositorySsh(newRepositorySsh);
        }
        dbPipeline.setName(pipelineVOName);
        dbPipeline.setDescription(pipelineVO.getDescription());
        dbPipeline.setParentFolderId(pipelineVO.getParentFolderId());
        setFolderIfPresent(dbPipeline);
        dbPipeline.setBranch(pipelineVO.getBranch());
        dbPipeline.setVisibility(pipelineVO.getVisibility());
        dbPipeline.setCodePath(pipelineVO.getCodePath());
        dbPipeline.setDocsPath(pipelineVO.getDocsPath());
        dbPipeline.setConfigurationPath(StringUtils.strip(pipelineVO.getConfigurationPath(), Constants.PATH_DELIMITER));
        pipelineDao.updatePipeline(dbPipeline);
        if (!previousName.equals(pipelineVOName)) {
            updatePipelineNameForRuns(pipelineVO.getId(), pipelineVOName);
        }

        if (projectNameUpdated) {
            pipelineRepositoryService.updateRepositoryName(dbPipeline, currentProjectPath, newProjectName);
        }
        return dbPipeline;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Pipeline updateToken(final PipelineVO pipelineVO) {
        Pipeline dbPipeline = load(pipelineVO.getId());
        dbPipeline.setRepositoryToken(pipelineVO.getRepositoryToken());
        setCurrentVersion(dbPipeline);
        if (!StringUtils.isEmpty(dbPipeline.getRepositoryError())) {
            throw new IllegalArgumentException(messageHelper.getMessage(MessageConstants.ERROR_REPO_TOKEN_INVALID,
                    dbPipeline.getRepository(), dbPipeline.getRepositoryError()));
        }
        pipelineDao.updatePipeline(dbPipeline);
        return dbPipeline;
    }

    @Override
    public Pipeline load(Long id) {
        return load(id, false);
    }

    @Override
    public AbstractSecuredEntity changeOwner(Long id, String owner) {
        final Pipeline pipeline = pipelineDao.loadPipeline(id);
        pipeline.setOwner(owner);
        pipelineDao.updatePipeline(pipeline);
        return pipeline;
    }

    @Override public AclClass getSupportedClass() {
        return AclClass.PIPELINE;
    }

    public Pipeline load(Long id, boolean loadVersion) {
        Pipeline pipeline = pipelineDao.loadPipeline(id);
        Assert.notNull(pipeline, messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_NOT_FOUND, id));
        if (loadVersion) {
            setCurrentVersion(pipeline);
        }
        pipeline.setHasMetadata(this.metadataManager.hasMetadata(new EntityVO(id, AclClass.PIPELINE)));
        return pipeline;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Pipeline delete(Long id, boolean keepRepository) {
        Pipeline pipeline = load(id);
        if (!keepRepository) {
            try {
                pipelineRepositoryService.deletePipelineRepository(pipeline);
            } catch (GitClientException | HttpClientErrorException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        runScheduleManager.deleteSchedulesForRunByPipeline(id);
        resetPipelineIdForRuns(id);
        dataStorageRuleDao.deleteRulesByPipeline(id);
        pipelineDao.deletePipeline(id);
        return pipeline;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateLocks(List<Long> pipelineIds, boolean isLocked) {
        pipelineDao.updateLocks(pipelineIds, isLocked);
    }

    public List<Pipeline> loadAllPipelines(boolean loadVersions) {
        List<Pipeline> result = pipelineDao.loadAllPipelines();
        if (loadVersions) {
            result.forEach(this::setCurrentVersion);
        }
        return result;
    }

    @Override
    public Set<Pipeline> loadAllWithParents(Integer pageNum, Integer pageSize) {
        Assert.isTrue((pageNum == null) == (pageSize == null),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_PAGE_INDEX_OR_SIZE, pageNum, pageSize));
        Assert.isTrue(pageNum == null || pageNum > 0, messageHelper.getMessage(MessageConstants.ERROR_PAGE_INDEX));
        Assert.isTrue(pageSize == null || pageSize > 0, messageHelper.getMessage(MessageConstants.ERROR_PAGE_SIZE));
        return pipelineDao.loadAllPipelinesWithParents(pageNum, pageSize);
    }

    @Override
    public Pipeline loadWithParents(final Long id) {
        return pipelineDao.loadPipelineWithParents(id);
    }

    @Override
    public Integer loadTotalCount() {
        return pipelineDao.loadPipelinesCount();
    }

    public Pipeline loadByNameOrId(String identifier) {
        return loadByNameOrId(identifier, true);
    }

    public Pipeline loadByNameOrIdWithoutVersion(String identifier) {
        return loadByNameOrId(identifier, false);
    }

    private Pipeline loadByNameOrId(final String identifier, final boolean loadVersion) {
        Pipeline pipeline = null;
        try {
            pipeline = pipelineDao.loadPipeline(Long.parseLong(identifier));
        } catch (NumberFormatException e) {
            LOGGER.trace(e.getMessage(), e);
        }
        if (pipeline == null) {
            pipeline = pipelineDao.loadPipelineByName(identifier);
        }
        Assert.notNull(pipeline, messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_NOT_FOUND, identifier));
        if (loadVersion) {
            setCurrentVersion(pipeline);
        }
        pipeline.setHasMetadata(this.metadataManager.hasMetadata(new EntityVO(pipeline.getId(), AclClass.PIPELINE)));
        return pipeline;
    }

    public Pipeline loadByRepoUrl(String url) {
        Optional<Pipeline> loadedPipeline = pipelineDao.loadPipelineByRepoUrl(url);
        Pipeline pipeline = loadedPipeline.orElseThrow(() -> new IllegalArgumentException(
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_WITH_URL_NOT_FOUND, url)));
        setCurrentVersion(pipeline);
        pipeline.setHasMetadata(this.metadataManager.hasMetadata(new EntityVO(pipeline.getId(), AclClass.PIPELINE)));
        return pipeline;
    }

    public List<Pipeline> loadRootPipelines() {
        return pipelineDao.loadRootPipelines();
    }

    public void setGitManager(GitManager gitManager) {
        this.gitManager = gitManager;
    }

    public String getPipelineCloneUrl(Long pipelineId) {
        return gitManager.getGitCredentials(pipelineId, false, true).getUrl();
    }

    private GitProject createGitRepository(final PipelineVO pipelineVO) throws GitClientException {
        GitProject project;
        if (pipelineVO.getPipelineType() == PipelineType.PIPELINE) {
            project = gitManager.createRepository(
                    pipelineVO.getTemplateId() == null ? defaultTemplate : pipelineVO.getTemplateId(),
                    pipelineVO.getName(),
                    pipelineVO.getDescription());
        } else  {
            project = gitManager.createRepository(pipelineVO.getName(), pipelineVO.getDescription());
        }
        return project;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Pipeline copyPipeline(final Long id, final Long parentFolderId, final String newName) {
        final Pipeline loadedPipeline = load(id);

        final String sourceProjectName = GitUtils.convertPipeNameToProject(loadedPipeline.getName());
        final String uuid = PasswordGenerator.generateRandomString(20);
        final String newPipelineName = buildCopyProjectName(sourceProjectName, uuid, newName);
        final String newProjectName = GitUtils.convertPipeNameToProject(newPipelineName);
        final String newRepository =
                GitUtils.replaceGitProjectNameInUrl(loadedPipeline.getRepository(), newProjectName);
        final String newRepositorySsh =
                GitUtils.replaceGitProjectNameInUrl(loadedPipeline.getRepositorySsh(), newProjectName);
        final Long sourcePipelineId = loadedPipeline.getId();

        loadedPipeline.setRepository(newRepository);
        loadedPipeline.setRepositorySsh(newRepositorySsh);
        loadedPipeline.setName(newPipelineName);
        loadedPipeline.setParentFolderId(parentFolderId);
        setFolderIfPresent(loadedPipeline);
        loadedPipeline.setOwner(securityManager.getAuthorizedUser());
        loadedPipeline.setRepositoryType(null);
        loadedPipeline.setLocked(false);
        final Pipeline newPipeline = crudManager.savePipeline(loadedPipeline);
        copyStorageRules(sourcePipelineId, newPipeline.getId());
        gitManager.copyRepository(sourceProjectName, newProjectName, uuid);
        return newPipeline;
    }

    private void setCurrentVersion(Pipeline pipeline) {
        try {
            pipeline.setRepositoryError(null);
            List<Revision> revisions = pipelineRepositoryService
                    .getPipelineRevisions(pipeline.getRepositoryType(), pipeline);
            if (revisions != null && !revisions.isEmpty()) {
                pipeline.setCurrentVersion(revisions.get(0));
            }
        } catch (GitClientException | HttpClientErrorException | IllegalArgumentException e) {
            LOGGER.error(e.getMessage(), e);
            pipeline.setRepositoryError(e.getMessage());
        }
    }

    private void setFolderIfPresent(Pipeline dbPipeline) {
        if (dbPipeline.getParentFolderId() != null) {
            Folder parent = folderManager.load(dbPipeline.getParentFolderId());
            dbPipeline.setParent(parent);
        }
    }

    private String buildCopyProjectName(final String sourceProjectName, final String uuid,
                                        final String newProjectName) {
        if (StringUtils.isNotBlank(newProjectName)) {
            Assert.isTrue(!gitManager.checkProjectExists(newProjectName),
                    messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_REPO_EXISTS, newProjectName));
            return newProjectName;
        }
        return String.format("%s_copy%s", sourceProjectName, uuid);
    }

    private void copyStorageRules(final Long sourcePipelineId, final Long newPipelineId) {
        final List<DataStorageRule> sourceStorageRules = ListUtils.emptyIfNull(dataStorageRuleDao
                .loadDataStorageRulesForPipeline(sourcePipelineId));
        sourceStorageRules.forEach(rule -> {
            rule.setPipelineId(newPipelineId);
            dataStorageRuleDao.createDataStorageRule(rule);
        });
    }

    private void updatePipelineNameForRuns(final Long pipelineId, final String pipelineVOName) {
        pipelineRunDao.updatePipelineNameForRuns(pipelineVOName, pipelineId);
    }

    private void resetPipelineIdForRuns(final Long id) {
        pipelineRunDao.clearPipelineIdForRuns(id);
    }

    private void checkBranchExists(final PipelineVO pipelineVO) {
        final String branchName = pipelineVO.getBranch();
        if (StringUtils.isBlank(branchName)) {
            return;
        }
        final List<String> branches = pipelineRepositoryService
                .getBranches(pipelineVO.getRepositoryType(), pipelineVO.getRepository(),
                        pipelineVO.getRepositoryToken());
        Assert.isTrue(ListUtils.emptyIfNull(branches).stream().anyMatch(branchName::equals),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_BRANCH_NOT_FOUND, branchName));
    }
}
