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
import com.epam.pipeline.entity.git.bitbucket.BitbucketRepository;
import com.epam.pipeline.entity.git.bitbucket.BitbucketTag;
import com.epam.pipeline.entity.pipeline.Revision;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface BitbucketMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "repoUrl", ignore = true)
    @Mapping(target = "repoSsh", ignore = true)
    GitProject toGitRepository(BitbucketRepository bitbucket);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "draft", ignore = true)
    @Mapping(target = "authorEmail", ignore = true)
    @Mapping(target = "createdDate", source = "date")
    @Mapping(target = "commitId", source = "target.hash")
    @Mapping(target = "author", source = "tagger.user.name")
    Revision tagToRevision(BitbucketTag bitbucketTag);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "draft", ignore = true)
    @Mapping(target = "authorEmail", ignore = true)
    @Mapping(target = "createdDate", source = "date")
    @Mapping(target = "commitId", source = "hash")
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "author", source = "author.user.name")
    Revision commitToRevision(BitbucketCommit bitbucketCommit);

    @AfterMapping
    default void fillRepositoryUrls(final BitbucketRepository bitbucket, final @MappingTarget GitProject gitProject) {
        final BitbucketLinks repositoryLinks = bitbucket.getLinks();
        if (Objects.isNull(repositoryLinks)) {
            return;
        }
        final Map<BitbucketCloneHrefType, String> repoUrls = ListUtils.emptyIfNull(repositoryLinks.getClone()).stream()
                .collect(Collectors.toMap(BitbucketCloneEntry::getName, BitbucketCloneEntry::getHref));
        gitProject.setRepoUrl(repoUrls.get(BitbucketCloneHrefType.https));
        gitProject.setRepoSsh(repoUrls.get(BitbucketCloneHrefType.ssh));
    }

    @AfterMapping
    default void fillCommit(final BitbucketCommit commit, final @MappingTarget Revision revision) {
        if (Objects.nonNull(commit.getAuthor())) {
            revision.setAuthorEmail(parseAuthorEmail(commit.getAuthor().getRaw()));
        }
    }

    @AfterMapping
    default void fillTag(final BitbucketTag tag, final @MappingTarget Revision revision) {
        if (Objects.nonNull(tag.getTagger())) {
            revision.setAuthorEmail(parseAuthorEmail(tag.getTagger().getRaw()));
        }
    }

    static String parseAuthorEmail(final String raw) {
        return StringUtils.substringBetween(raw, "<", ">");
    }
}
