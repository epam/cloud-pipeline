/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.git;

import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.entity.pipeline.Revision;

import java.util.List;

public interface GitClientService {

    RepositoryType getType();

    GitProject createRepository(String description, String repositoryPath, String token);

    GitProject getRepository(String repository, String token);

    void handleHooks(GitProject project, String token);

    void createFile(GitProject project, String path, String content, String token);

    byte[] getFileContents(GitProject project, String path, String revision, String token);

    List<Revision> getTags(Pipeline pipeline);

    Revision getLastRevision(Pipeline pipeline);
}
