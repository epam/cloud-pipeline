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

package com.epam.pipeline.manager.pipeline.documents.templates;

import com.epam.pipeline.controller.vo.TaskGraphVO;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.graph.TaskNode;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.DataStorageRuleManager;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.pipeline.DocumentGenerationPropertyManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineVersionManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PipelineDocumentTemplateManager {

    @Autowired
    private PipelineManager pipelineManager;

    @Autowired
    private PipelineVersionManager pipelineVersionManager;

    @Autowired
    private GitManager gitManager;

    @Autowired
    private DataStorageManager dataStorageManager;

    @Autowired
    private DataStorageRuleManager dataStorageRuleManager;

    @Autowired
    private DocumentGenerationPropertyManager documentGenerationPropertyManager;

    public PipelineDocumentTemplate loadPipelineDocumentTemplateWithCurrentVersion(Long pipelineId) {
        Pipeline pipeline = pipelineManager.load(pipelineId, true);
        PipelineDocumentTemplate template = new PipelineDocumentTemplate(pipeline, pipeline.getCurrentVersion());
        this.fillTemplate(template);
        return template;
    }

    public PipelineDocumentTemplate loadPipelineDocumentTemplateWithSpecificVersion(Long pipelineId, String version)
            throws GitClientException {
        Pipeline pipeline = pipelineManager.load(pipelineId);
        List<Revision> revisions = pipelineVersionManager.loadAllVersionFromGit(pipelineId, null);
        Optional<Revision> oRevision = revisions.stream().filter(r -> r.getName().equals(version)).findAny();
        PipelineDocumentTemplate template = oRevision.map(revision -> new PipelineDocumentTemplate(pipeline, revision))
                .orElseGet(() -> new PipelineDocumentTemplate(pipeline));
        this.fillTemplate(template);
        return template;
    }

    private void fillTemplate(PipelineDocumentTemplate template) {
        this.applyChangeSummary(template);
        this.applyDefaultConfiguration(template);
        this.applyCustomScripts(template);
        this.applyTools(template);
        this.applyFilesGeneratedByPipeline(template);
        this.applyExamplePipelineScript(template);
        this.applyDocumentTemplateProperties(template);
    }

    private void applyChangeSummary(PipelineDocumentTemplate template) {
        try {
            List<Revision> revisions = pipelineVersionManager
                    .loadAllVersionFromGit(template.getPipeline().getId(), null);
            Revision previousRevision = null;
            for (int i = 0; i < revisions.size(); i++) {
                if (revisions.get(i).getName().equals(template.getVersion().getName())) {
                    if (i < revisions.size() - 1) {
                        previousRevision = revisions.get(i + 1);
                    }
                    break;
                }
            }
            if (previousRevision != null) {
                final Long oneSecond = 1000L;
                Date since = new Date(previousRevision.getCreatedDate().getTime() + oneSecond);
                template.setCommits(gitManager.getCommits(template.getPipeline(),
                        template.getVersion().getName(), since));
            } else {
                template.setCommits(gitManager.getCommits(template.getPipeline(), template.getVersion().getName()));
            }

        } catch (GitClientException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void applyDefaultConfiguration(PipelineDocumentTemplate template) {
        try {
            template.setDefaultConfiguration(pipelineVersionManager
                    .loadParametersFromScript(template.getPipeline().getId(),
                            template.getVersion().getName()));
        } catch (GitClientException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void applyCustomScripts(PipelineDocumentTemplate template) {
        try {
            template.setCustomScripts(gitManager.getPipelineSources(template.getPipeline().getId(),
                    template.getVersion().getName()));

        } catch (GitClientException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void applyTools(PipelineDocumentTemplate template) {
        TaskGraphVO taskGraph = pipelineVersionManager.getWorkflowGraph(template.getPipeline().getId(),
                template.getVersion().getName());
        template.setWorkflowGraph(taskGraph);
        List<Tool> tools = taskGraph.getTasks()
                .stream()
                .filter(t -> t.getTool() != null)
                .map(TaskNode::getTool)
                .collect(Collectors.toList());
        template.setOpenSourceSoftware(tools);
    }

    private void applyFilesGeneratedByPipeline(PipelineDocumentTemplate template) {
        Optional<AbstractDataStorage> dataStorageOptional = dataStorageManager.getDataStorages()
                .stream()
                .filter(ds -> ds.getName().toLowerCase().contains("analysis"))
                .findAny();
        if (dataStorageOptional.isPresent()) {
            AbstractDataStorage abstractDataStorage = dataStorageOptional.get();
            StoragePolicy policy = abstractDataStorage.getStoragePolicy();
            Integer totalDays =  policy == null ? 0 : policy.getLongTermStorageDuration();
            final Integer daysInYear = 365;
            final Integer daysInMonth = 30;
            Integer years = Math.floorDiv(totalDays, daysInYear);
            Integer months = Math.floorDiv(totalDays - years * daysInYear, daysInMonth);
            Integer days = totalDays - years * daysInYear - months * daysInMonth;

            List<String> fates = new ArrayList<>();
            if (years > 1) {
                fates.add(String.format("%d years", years));
            } else if (years == 1) {
                fates.add(String.format("%d year", years));
            }

            if (months > 1) {
                fates.add(String.format("%d months", months));
            } else if (months == 1) {
                fates.add(String.format("%d month", months));
            }

            if (days > 1 || (days == 0 && fates.size() == 0)) {
                fates.add(String.format("%d days", days));
            } else if (days == 1) {
                fates.add(String.format("%d day", days));
            }

            String fateDescription = String.join(", ", fates);

            List<DataStorageRule> rules = dataStorageRuleManager
                    .loadAllRulesForPipeline(template.getPipeline().getId());
            for (DataStorageRule rule : rules) {
                String name = rule.getFileMask();
                String fate = rule.getMoveToSts() ? fateDescription : "Removed at completion";
                template.getFilesGeneratedDuringPipelineProcessing().add(new ImmutablePair<>(name, fate));
            }
        }
    }

    private void applyExamplePipelineScript(PipelineDocumentTemplate template) {
        if (template.getDefaultConfiguration() != null) {
            String mainFile = template.getDefaultConfiguration().getMainFile();
            Optional<GitRepositoryEntry> optionalMainScript = template.getCustomScripts().stream()
                    .filter(s -> s.getName().equals(mainFile))
                    .findAny();
            if (optionalMainScript.isPresent()) {
                String path = optionalMainScript.get().getPath();
                try {
                    byte[] data = gitManager.getPipelineFileContents(
                            template.getPipeline(),
                            template.getVersion().getName(),
                            path);
                    if (data != null) {
                        template.setPipelineMainScriptCodeLines((new String(data)).split("\n"));
                    }
                } catch (GitClientException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    private void applyDocumentTemplateProperties(PipelineDocumentTemplate template) {
        template.setDocumentGenerationProperties(documentGenerationPropertyManager
                .loadAllPropertiesByPipelineId(template.getPipeline().getId()));
        template.applyDocumentTemplateProperties();
    }

}
