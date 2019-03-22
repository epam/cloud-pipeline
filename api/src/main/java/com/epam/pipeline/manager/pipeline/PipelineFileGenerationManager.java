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

import com.epam.pipeline.controller.vo.GenerateFileVO;
import com.epam.pipeline.manager.pipeline.documents.templates.PipelineDocumentTemplate;
import com.epam.pipeline.manager.pipeline.documents.templates.PipelineDocumentTemplateManager;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.git.GitManager;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class PipelineFileGenerationManager {

    @Autowired
    private PipelineDocumentTemplateManager pipelineDocumentTemplateManager;

    @Autowired
    private GitManager gitManager;

    public byte[] fillTemplateForPipelineVersion(Long pipelineId,
                                                 String pipelineVersion,
                                                 String templatePath,
                                                 GenerateFileVO generateFileVO) {
        try {
            PipelineDocumentTemplate documentTemplate = pipelineDocumentTemplateManager
                    .loadPipelineDocumentTemplateWithSpecificVersion(pipelineId, pipelineVersion);
            documentTemplate.applyLuigiWorkflowGraph(generateFileVO.getLuigiWorkflowGraphVO());
            return this.generateFile(templatePath, documentTemplate);
        } catch (GitClientException e) {
            return null;
        }
    }

    public byte[] fillTemplateForPipelineCurrentVersion(Long pipelineId,
                                                        String templatePath,
                                                        GenerateFileVO generateFileVO) {
        PipelineDocumentTemplate documentTemplate = pipelineDocumentTemplateManager
                .loadPipelineDocumentTemplateWithCurrentVersion(pipelineId);
        documentTemplate.applyLuigiWorkflowGraph(generateFileVO.getLuigiWorkflowGraphVO());
        return this.generateFile(templatePath, documentTemplate);
    }

    private byte[] generateFile(String templatePath, PipelineDocumentTemplate documentTemplate) {
        try {
            byte[] docxTemplateData = gitManager.getPipelineFileContents(documentTemplate.getPipeline(),
                    documentTemplate.getVersion().getName(),
                    templatePath);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(docxTemplateData);
            XWPFDocument document = new XWPFDocument(inputStream);
            documentTemplate.fillTemplate(document);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.write(outputStream);
            return outputStream.toByteArray();
        } catch (GitClientException | IOException e) {
            return null;
        }
    }

}
