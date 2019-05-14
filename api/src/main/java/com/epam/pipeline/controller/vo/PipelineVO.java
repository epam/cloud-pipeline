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

package com.epam.pipeline.controller.vo;

import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PipelineVO {
    private Long id;
    private String name;
    private String description;
    private String repository;
    private String repositorySsh;
    private String repositoryToken;
    private Long parentFolderId;
    private String templateId;
    private RepositoryType repositoryType;

    public Pipeline toPipeline() {
        Pipeline pipeline = new Pipeline();
        pipeline.setId(getId());
        pipeline.setName(getName());
        pipeline.setDescription(getDescription());
        pipeline.setParentFolderId(getParentFolderId());
        pipeline.setRepository(getRepository());
        pipeline.setRepositorySsh(getRepositorySsh());
        pipeline.setRepositoryToken(getRepositoryToken());
        pipeline.setTemplateId(getTemplateId());
        pipeline.setRepositoryType(getRepositoryType());
        return pipeline;
    }
}
