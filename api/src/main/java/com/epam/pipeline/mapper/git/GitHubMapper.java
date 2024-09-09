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
import com.epam.pipeline.entity.git.github.GitHubAuthor;
import com.epam.pipeline.entity.git.github.GitHubCommit;
import com.epam.pipeline.entity.git.github.GitHubCommitNode;
import com.epam.pipeline.entity.git.github.GitHubRelease;
import com.epam.pipeline.entity.git.github.GitHubRepository;
import com.epam.pipeline.entity.git.github.GitHubTag;
import com.epam.pipeline.entity.pipeline.Revision;
import lombok.SneakyThrows;
import org.apache.commons.collections4.ListUtils;
import org.apache.http.util.TextUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public interface GitHubMapper {
    DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

    @Mapping(target = "projectId", source = "id")
    @Mapping(target = "repoUrl", source = "cloneUrl")
    @Mapping(target = "repoSsh", source = "sshUrl")
    @Mapping(target = "path", source = "htmlUrl")
    @Mapping(target = "createdDate", ignore = true)
    GitProject toGitRepository(GitHubRepository bitbucket);

    @Mapping(target = "message", source = "message")
    @Mapping(target = "commitId", source = "object.sha")
    @Mapping(target = "name", source = "tag")
    @Mapping(target = "author", source = "tagger.name")
    @Mapping(target = "authorEmail", source = "tagger.email")
    @Mapping(target = "createdDate", expression = "java(fillDate(tag.getTagger()))")
    Revision tagToRevision(GitHubTag tag);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "message", source = "body")
    @Mapping(target = "draft", ignore = true)
    @Mapping(target = "author", source = "commit.commit.author.name")
    @Mapping(target = "authorEmail", source = "commit.commit.author.email")
    @Mapping(target = "createdDate", expression = "java(fillDate(gitHubRef.getCreatedAt()))")
    Revision refToRevision(GitHubRelease gitHubRef);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "draft", ignore = true)
    @Mapping(target = "createdDate", expression = "java(fillDate(commit.getCommit()))")
    @Mapping(target = "commitId", source = "sha")
    @Mapping(target = "message", source = "commit.message")
    @Mapping(target = "author", source = "commit.author.name")
    @Mapping(target = "authorEmail", source = "commit.author.email")
    Revision commitToRevision(GitHubCommitNode commit);

    @Mapping(target = "id", source = "sha")
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "parentIds", expression = "java(fillCommitParents(commit))")
    @Mapping(target = "committedDate", expression = "java(fillCommitDate(commit.getCommit()))")
    @Mapping(target = "committerName", ignore = true)
    @Mapping(target = "committerEmail", ignore = true)
    @Mapping(target = "shortId", ignore = true)
    @Mapping(target = "message", source = "commit.message")
    @Mapping(target = "authoredDate", ignore = true)
    @Mapping(target = "authorName", source = "commit.author.name")
    @Mapping(target = "authorEmail", source = "commit.author.email")
    GitCommitEntry commitToCommitEntry(GitHubCommitNode commit);

    @Mapping(target = "message", ignore = true)
    GitTagEntry tagToTagEntry(GitHubRelease tag);

    @SneakyThrows
    default Date fillDate(final String date) {
        return TextUtils.isBlank(date) ? null : DATE_FORMAT.parse(date);
    }

    @SneakyThrows
    default Date fillDate(final GitHubAuthor author) {
        if (Objects.isNull(author)) {
            return null;
        }
        return fillDate(author.getDate());
    }

    @SneakyThrows
    default Date fillDate(final GitHubCommit commit) {
        if (Objects.isNull(commit)) {
            return null;
        }
        return fillDate(commit.getAuthor());
    }

    default String fillCommitDate(final GitHubCommit commit) {
        return Objects.nonNull(commit) &&
                Objects.nonNull(commit.getAuthor()) &&
                Objects.nonNull(commit.getAuthor().getDate())
                ? DATE_FORMAT.format(fillDate(commit))
                : null;
    }

    default List<String> fillCommitParents(final GitHubCommitNode commit) {
        if (Objects.isNull(commit)) {
            return null;
        }
        return ListUtils.emptyIfNull(commit.getParents()).stream()
                .map(GitHubCommitNode::getSha)
                .collect(Collectors.toList());
    }
}
