/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.git.bitbucket.BitbucketCloneEntry;
import com.epam.pipeline.entity.git.bitbucket.BitbucketCloneHrefType;
import com.epam.pipeline.entity.git.bitbucket.BitbucketLinks;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudCommit;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudRef;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudRepository;
import com.epam.pipeline.entity.pipeline.Revision;
import lombok.SneakyThrows;
import org.apache.commons.collections4.ListUtils;
import org.apache.http.util.TextUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public interface BitbucketCloudMapper {
    DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "repoUrl", ignore = true)
    @Mapping(target = "repoSsh", ignore = true)
    @Mapping(target = "path", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    GitProject toGitRepository(BitbucketCloudRepository bitbucket);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "draft", ignore = true)
    @Mapping(target = "message", source = "target.message")
    @Mapping(target = "commitId", source = "target.hash")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "author", expression = "java(fillAuthor(bitbucketTag.getTarget()))")
    @Mapping(target = "authorEmail", expression = "java(fillEmail(bitbucketTag.getTarget()))")
    @Mapping(target = "createdDate", expression = "java(fillDate(bitbucketTag.getTarget()))")
    Revision tagToRevision(BitbucketCloudRef bitbucketTag);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "draft", ignore = true)
    @Mapping(target = "createdDate", expression = "java(fillDate(bitbucketCommit))")
    @Mapping(target = "commitId", source = "hash")
    @Mapping(target = "message", source = "message")
    @Mapping(target = "author", expression = "java(fillAuthor(bitbucketCommit))")
    @Mapping(target = "authorEmail", expression = "java(fillEmail(bitbucketCommit))")
    Revision commitToRevision(BitbucketCloudCommit bitbucketCommit);

    @Mapping(target = "id", source = "hash")
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "parentIds", expression = "java(fillCommitParents(bitbucketCommit))")
    @Mapping(target = "committedDate", expression = "java(fillCommitDate(bitbucketCommit))")
    @Mapping(target = "committerName", ignore = true)
    @Mapping(target = "committerEmail", ignore = true)
    @Mapping(target = "shortId", ignore = true)
    @Mapping(target = "authoredDate", ignore = true)
    @Mapping(target = "authorName", expression = "java(fillAuthor(bitbucketCommit))")
    @Mapping(target = "authorEmail", expression = "java(fillEmail(bitbucketCommit))")
    GitCommitEntry bitbucketCommitToCommitEntry(BitbucketCloudCommit bitbucketCommit);

    @Mapping(target = "release", ignore = true)
    @Mapping(target = "message", ignore = true)
    GitTagEntry bitbucketTagToTagEntry(BitbucketCloudRef tag);

    @AfterMapping
    default void fillRepositoryUrls(final BitbucketCloudRepository bitbucket,
                                    final @MappingTarget GitProject gitProject) {
        final BitbucketLinks repositoryLinks = bitbucket.getLinks();
        if (Objects.isNull(repositoryLinks)) {
            return;
        }
        final Map<BitbucketCloneHrefType, String> repoUrls = ListUtils.emptyIfNull(repositoryLinks.getClone()).stream()
                .collect(Collectors.toMap(BitbucketCloneEntry::getName, BitbucketCloneEntry::getHref));
        gitProject.setRepoUrl(repoUrls.getOrDefault(BitbucketCloneHrefType.https,
                repoUrls.get(BitbucketCloneHrefType.http)));
        gitProject.setRepoSsh(repoUrls.get(BitbucketCloneHrefType.ssh));
        gitProject.setPath(repositoryLinks.getHtml().getHref());
    }

    @SneakyThrows
    default Date fillDate(final BitbucketCloudCommit commit) {
        return TextUtils.isBlank(commit.getDate()) ? null : DATE_FORMAT.parse(commit.getDate());
    }

    default String fillCommitDate(final BitbucketCloudCommit commit) {
        return Objects.nonNull(commit) && Objects.nonNull(commit.getDate())
                ? DATE_FORMAT.format(fillDate(commit))
                : null;
    }

    default String fillAuthor(final BitbucketCloudCommit commit) {
        if (commit == null || commit.getAuthor() == null || TextUtils.isBlank(commit.getAuthor().getRaw())) {
            return null;
        }
        return commit.getAuthor().getRaw().split("<")[0].trim();
    }

    default String fillEmail(final BitbucketCloudCommit commit) {
        if (commit == null || commit.getAuthor() == null || TextUtils.isBlank(commit.getAuthor().getRaw())) {
            return null;
        }
        Pattern pattern = Pattern.compile("<(.*?)>");
        Matcher matcher = pattern.matcher(commit.getAuthor().getRaw());
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    default List<String> fillCommitParents(final BitbucketCloudCommit commit) {
        if (Objects.isNull(commit)) {
            return null;
        }
        return ListUtils.emptyIfNull(commit.getParents()).stream()
                .map(BitbucketCloudCommit::getHash)
                .collect(Collectors.toList());
    }
}
