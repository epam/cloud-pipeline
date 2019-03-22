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

import com.epam.pipeline.controller.vo.LuigiWorkflowGraphVO;
import com.epam.pipeline.controller.vo.TaskGraphVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.manager.pipeline.DocumentGenerationPropertyManager;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.BulletListTemplateProcessor;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.ImageTemplateProcessor;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.MultiLineTemplateProcessor;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.TableTemplateProcessor;
import com.epam.pipeline.manager.pipeline.documents.templates.processors.base.Placeholder;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.Table;
import com.epam.pipeline.manager.pipeline.documents.templates.structure.TableRow;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
public class PipelineDocumentTemplate extends GeneralTemplate {

    @Placeholder(regex = "pipeline", methodName = "getName")
    @Placeholder(regex = "pipeline.id", methodName = "getId")
    @Placeholder(regex = "pipeline description", methodName = "getDescription")
    private Pipeline pipeline;
    @Placeholder(regex = "version", methodName = "getName")
    @Placeholder(regex = "version.id", methodName = "getId")
    private Revision version;
    @Placeholder(regex = "run.start", methodName = "getStartDate")
    @Placeholder(regex = "run.finish", methodName = "getEndDate")
    @Placeholder(regex = "run.status", methodName = "getStatus")
    private PipelineRun pipelineRun;

    @Placeholder(regex = "change summary", processor = BulletListTemplateProcessor.class)
    private List<GitCommitEntry> commits;
    @Placeholder(regex = "Title - Example pipeline script for a single sample", methodName = "getMainFile")
    private PipelineConfiguration defaultConfiguration;
    @Placeholder(regex = "Example pipeline script for a single sample", processor = MultiLineTemplateProcessor.class)
    private String[] pipelineMainScriptCodeLines;
    private TaskGraphVO workflowGraph;
    private List<GitRepositoryEntry> customScripts;
    private List<Tool> openSourceSoftware;
    private List<Pair<String, String>> filesGeneratedDuringPipelineProcessing;
    @Placeholder(regex = "Workflow schematic", processor = ImageTemplateProcessor.class)
    private byte[] workflowGraphImage;
    @Placeholder(regex = "introduction", templateProperty = DocumentGenerationPropertyManager.INTRODUCTION_PROPERTY)
    private String introduction;
    @Placeholder(
            regex = "data hierarchy photo",
            processor = ImageTemplateProcessor.class,
            templateProperty = DocumentGenerationPropertyManager.DATA_HIERARCHY_PHOTO_PROPERTY,
            templatePropertyIsBinary = true)
    private byte[] dataHierarchyPhoto;

    private PipelineDocumentTemplate() {
        super();
    }

    PipelineDocumentTemplate(Pipeline pipeline) {
        this();
        this.setPipeline(pipeline);
        this.setCustomScripts(new ArrayList<>());
        this.setOpenSourceSoftware(new ArrayList<>());
        this.setFilesGeneratedDuringPipelineProcessing(new ArrayList<>());
    }

    public PipelineDocumentTemplate(Pipeline pipeline, Revision version) {
        this(pipeline);
        this.setVersion(version);
    }

    PipelineDocumentTemplate(Pipeline pipeline, PipelineRun run) {
        this(pipeline);
        this.setPipelineRun(run);
    }

    PipelineDocumentTemplate(Pipeline pipeline, Revision version, PipelineRun run) {
        this(pipeline, version);
        this.setPipelineRun(run);
    }

    @Placeholder(regex = "Open Source Algorithms and Software", processor = TableTemplateProcessor.class)
    public Table getOpenSourceAlgorithmsAndSoftwareTable() {
        Table table = new Table();
        table.setContainsHeaderRow(true);
        table.addColumn("Algorithm or Software");
        table.addColumn("Version");
        Map<String, List<String>> toolsVersions = new HashMap<>();
        for (Tool tool : this.openSourceSoftware) {
            String[] parts = tool.getImage().split("-");
            String toolName = tool.getImage();
            String toolVersion = null;
            if (parts.length >= 2) {
                toolName = parts[0].trim();
                toolVersion = parts[1].trim();
            }
            if (!toolsVersions.containsKey(toolName)) {
                toolsVersions.put(toolName, new ArrayList<>());
            }
            List<String> versions = toolsVersions.get(toolName);
            if (toolVersion != null && !toolVersion.equals("") && versions.indexOf(toolVersion) == -1) {
                versions.add(toolVersion);
                toolsVersions.put(toolName, versions);
            }
        }
        for (String tool : toolsVersions.keySet()) {
            List<String> versions = toolsVersions.get(tool);
            String versionsStr = String.join(", ", versions);
            TableRow row = table.addRow(tool);
            table.setData(row.getIndex(), 0, tool);
            table.setData(row.getIndex(), 1, versionsStr);
        }
        return table;
    }

    @Placeholder(regex = "Analytical Pipeline Custom Scripts", processor = TableTemplateProcessor.class)
    public Table getCustomScriptsTable() {
        Table table = new Table();
        table.setContainsHeaderRow(true);
        table.addColumn("Custom script");
        table.addColumn("Version");
        for (GitRepositoryEntry customScript : this.customScripts) {
            TableRow row = table.addRow(customScript.getName());
            table.setData(row.getIndex(), 0, customScript.getName());
            table.setData(row.getIndex(), 1, "");
        }
        return table;
    }

    @Placeholder(regex = "Files generated during pipeline processing", processor = TableTemplateProcessor.class)
    public Table getFilesGeneratedDuringPipelineProcessingTable() {
        Table table = new Table();
        table.setContainsHeaderRow(true);
        table.addColumn("File Name");
        table.addColumn("Storage Fate");
        for (Pair<String, String> generatedFile : this.filesGeneratedDuringPipelineProcessing) {
            TableRow row = table.addRow(generatedFile.getKey());
            table.setData(row.getIndex(), 0, generatedFile.getKey());
            table.setData(row.getIndex(), 1, generatedFile.getValue());
        }
        return table;
    }

    public void applyLuigiWorkflowGraph(LuigiWorkflowGraphVO luigiWorkflowGraphVO) {
        if (luigiWorkflowGraphVO != null) {
            byte[] workflowGraphImage = org.apache.commons.codec.binary.
                    Base64.decodeBase64(luigiWorkflowGraphVO.getImageData());
            this.setWorkflowGraphImage(workflowGraphImage);
        }
    }

}
