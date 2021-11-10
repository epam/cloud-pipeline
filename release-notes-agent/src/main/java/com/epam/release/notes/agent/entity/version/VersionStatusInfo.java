package com.epam.release.notes.agent.entity.version;

import com.epam.release.notes.agent.entity.github.GitHubIssue;
import com.epam.release.notes.agent.entity.jira.JiraIssue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
@AllArgsConstructor
public class VersionStatusInfo {
    String oldVersion;
    String newVersion;
    List<JiraIssue> jiraIssues;
    List<GitHubIssue> gitHubIssues;
    VersionStatus versionStatus;
}
