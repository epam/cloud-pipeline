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

package com.epam.pipeline.manager.template;

import java.util.Collection;

import com.epam.pipeline.entity.template.Template;
import com.epam.pipeline.manager.git.TemplatesScanner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TemplateManager {


    @Value("${templates.directory}")
    private String pipelineTemplatesDirectoryPath;

    @Value("${templates.folder.directory}")
    private String folderTemplatesDirectoryPath;

    @Value("${templates.default.template}")
    private String pipelineDefaultTemplate;

    public Collection<Template> getPipelineTemplates() {
        TemplatesScanner templatesScanner = new TemplatesScanner(pipelineTemplatesDirectoryPath);
        Collection<Template> templates = templatesScanner.listTemplates().values();
        templates.forEach(template -> {
            if (template.getId().equals(pipelineDefaultTemplate)) {
                template.setDefaultTemplate(true);
            }
        });
        return templates;
    }

    public Collection<Template> getFolderTemplates() {
        TemplatesScanner templatesScanner = new TemplatesScanner(folderTemplatesDirectoryPath);
        return templatesScanner.listTemplates().values();
    }

}
