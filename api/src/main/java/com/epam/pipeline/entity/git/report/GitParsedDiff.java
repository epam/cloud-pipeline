/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.git.report;

import com.epam.pipeline.entity.git.GitCommitsFilter;
import lombok.Builder;
import lombok.Data;

import java.util.List;


/**
 * Represents Git diff object that is split by commits, each entry in entries is related to separate file
 */
@Data
@Builder
public class GitParsedDiff {
    private List<GitParsedDiffEntry> entries;
    private GitCommitsFilter filters;
}
