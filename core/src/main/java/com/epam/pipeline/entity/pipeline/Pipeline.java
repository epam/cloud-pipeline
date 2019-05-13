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

package com.epam.pipeline.entity.pipeline;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Pipeline extends AbstractSecuredEntity {

    private String description;
    private String repository;
    private String repositorySsh;
    private Revision currentVersion;
    private Long parentFolderId;
    private String templateId;
    private Folder parent;
    private AclClass aclClass = AclClass.PIPELINE;
    @JsonIgnore
    private String repositoryToken;
    private RepositoryType repositoryType;
    private String repositoryError;
    private boolean hasMetadata;

    public Pipeline(Long id) {
        super(id);
    }

    @Override
    public void clearParent() {
        this.parent = null;
    }
}
