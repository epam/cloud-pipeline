/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.controller.vo.search;

import lombok.Data;

@Data
public class FacetedSearchExportVO {
    private boolean includeName;
    private boolean includeChanged;
    private boolean includeSize;
    private boolean includeOwner;
    private boolean includePath;
    private boolean includeCloudPath;
    private boolean includeMountPath;
    private String delimiter;
}