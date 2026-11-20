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

package com.epam.pipeline.entity.scan;

<<<<<<< ours
import lombok.AllArgsConstructor;
import lombok.Getter;

||||||| base
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

=======
>>>>>>> theirs
import java.util.Map;

<<<<<<< ours
@Getter
@AllArgsConstructor
public class ToolScanResultView {

    private final long toolId;
    private final Map<String, ToolVersionScanResultView> toolVersionScanResults;


}
||||||| base
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class ToolScanResultView {

    private final long toolId;
    private final Map<String, ToolVersionScanResultView> toolVersionScanResults;


}
=======
public record ToolScanResultView(
        long toolId, Map<String,
        ToolVersionScanResultView> toolVersionScanResults) {}
>>>>>>> theirs
