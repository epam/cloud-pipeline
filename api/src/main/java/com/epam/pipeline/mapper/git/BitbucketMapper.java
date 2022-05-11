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

package com.epam.pipeline.mapper.git;

import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.bitbucket.BitbucketCloneEntry;
import com.epam.pipeline.entity.git.bitbucket.BitbucketCloneHrefType;
import com.epam.pipeline.entity.git.bitbucket.BitbucketCommit;
import com.epam.pipeline.entity.git.bitbucket.BitbucketLinks;
import com.epam.pipeline.entity.git.bitbucket.BitbucketProject;
import com.epam.pipeline.entity.git.bitbucket.BitbucketRepository;
import com.epam.pipeline.entity.git.bitbucket.BitbucketTag;
import com.epam.pipeline.entity.pipeline.Revision;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface BitbucketMapper {

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "repoUrl", ignore = true)
    @Mapping(target = "repoSsh", ignore = true)
    @Mapping(target = "path", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    GitProject toGitRepository(BitbucketRepository bitbucket);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "draft", ignore = true)
    @Mapping(target = "commitId", source = "latestCommit")
    @Mapping(target = "name", source = "displayId")
    @Mapping(target = "author", source = "commit.author.displayName")
    @Mapping(target = "authorEmail", source = "commit.author.emailAddress")
    @Mapping(target = "createdDate", expression = "java(fillDate(bitbucketTag.getCommit()))")
    Revision tagToRevision(BitbucketTag bitbucketTag);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "draft", ignore = true)
    @Mapping(target = "createdDate", expression = "java(fillDate(bitbucketCommit))")
    @Mapping(target = "commitId", source = "id")
    @Mapping(target = "name", source = "displayId")
    @Mapping(target = "author", source = "author.displayName")
    @Mapping(target = "authorEmail", source = "author.emailAddress")
    Revision commitToRevision(BitbucketCommit bitbucketCommit);

    @AfterMapping
    default void fillRepositoryUrls(final BitbucketRepository bitbucket, final @MappingTarget GitProject gitProject) {
        final BitbucketLinks repositoryLinks = bitbucket.getLinks();
        if (Objects.isNull(repositoryLinks)) {
            return;
        }
        final Map<BitbucketCloneHrefType, String> repoUrls = ListUtils.emptyIfNull(repositoryLinks.getClone()).stream()
                .collect(Collectors.toMap(BitbucketCloneEntry::getName, BitbucketCloneEntry::getHref));
        gitProject.setRepoUrl(repoUrls.getOrDefault(BitbucketCloneHrefType.https,
                repoUrls.get(BitbucketCloneHrefType.http)));
        gitProject.setRepoSsh(repoUrls.get(BitbucketCloneHrefType.ssh));
    }

    @AfterMapping
    default void fillRepositoryPath(final BitbucketRepository repository, final @MappingTarget GitProject gitProject) {
        final BitbucketProject project = repository.getProject();
        if (StringUtils.isNotBlank(repository.getName())) {
            gitProject.setName(repository.getName());
        }
        if (Objects.isNull(project) || StringUtils.isBlank(project.getKey())
                || StringUtils.isBlank(project.getName())) {
            return;
        }
        gitProject.setPath(String.format("%s/%s", project.getKey(), repository.getName()));
    }

    default Date fillDate(final BitbucketCommit commit) {
        return Objects.nonNull(commit) && Objects.nonNull(commit.getAuthorTimestamp())
                ? new Date(commit.getAuthorTimestamp())
                : null;
    }
}
