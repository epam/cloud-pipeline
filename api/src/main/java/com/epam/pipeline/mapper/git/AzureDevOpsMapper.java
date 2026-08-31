/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.mapper.git;

import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.azure.AzureDevOpsCommit;
import com.epam.pipeline.entity.git.azure.AzureDevOpsItem;
import com.epam.pipeline.entity.git.azure.AzureDevOpsRepository;
import com.epam.pipeline.entity.git.azure.AzureDevOpsTag;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Objects;

@Mapper(componentModel = "spring")
public interface AzureDevOpsMapper {
    String COMMIT_ID = "commitId";
    DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "repoUrl", source = "remoteUrl")
    @Mapping(target = "repoSsh", source = "sshUrl")
    @Mapping(target = "path", source = "webUrl")
    @Mapping(target = "createdDate", ignore = true)
    GitProject toGitRepository(AzureDevOpsRepository devOpsRepository);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "draft", ignore = true)
    @Mapping(target = COMMIT_ID, source = "taggedObject.objectId")
    @Mapping(target = "author", source = "taggedBy.name")
    @Mapping(target = "authorEmail", source = "taggedBy.email")
    @Mapping(target = "createdDate", source = "taggedBy.date")
    Revision tagToRevision(AzureDevOpsTag tag);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "draft", ignore = true)
    @Mapping(target = "name", source = COMMIT_ID)
    @Mapping(target = "createdDate", source = "author.date")
    @Mapping(target = "message", source = "comment")
    @Mapping(target = "author", source = "author.name")
    @Mapping(target = "authorEmail", source = "author.email")
    Revision commitToRevision(AzureDevOpsCommit commit);

    @Mapping(target = "id", source = COMMIT_ID)
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "parentIds", source = "parents")
    @Mapping(target = "committedDate", expression = "java(fillCommitDate(commit))")
    @Mapping(target = "committerName", source = "committer.name")
    @Mapping(target = "committerEmail", source = "committer.email")
    @Mapping(target = "shortId", ignore = true)
    @Mapping(target = "authoredDate", ignore = true)
    @Mapping(target = "authorName", source = "author.name")
    @Mapping(target = "authorEmail", source = "author.email")
    @Mapping(target = "message", source = "comment")
    GitCommitEntry commitToCommitEntry(AzureDevOpsCommit commit);

    @Mapping(target = "id", source = COMMIT_ID)
    @Mapping(target = "type", source = "gitObjectType")
    @Mapping(target = "path", expression = "java(fillItemPath(item))")
    @Mapping(target = "name", expression = "java(fillItemName(item))")
    GitRepositoryEntry itemToRepositoryEntry(AzureDevOpsItem item);

    default String fillItemPath(final AzureDevOpsItem item) {
        return ProviderUtils.withoutLeadingDelimiter(item.getPath());
    }

    default String fillItemName(final AzureDevOpsItem item) {
        return Paths.get(item.getPath()).getFileName().toString();
    }

    default String fillCommitDate(final AzureDevOpsCommit commit) {
        return Objects.nonNull(commit) && Objects.nonNull(commit.getCommitter().getDate())
                ? DATE_FORMAT.format(commit.getCommitter().getDate())
                : null;
    }
}
