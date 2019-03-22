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

package com.epam.pipeline.entity.template;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

@Getter
@Setter
@AllArgsConstructor
public class Template {
    private String id;
    private String description;
    private boolean defaultTemplate = false;

    @JsonIgnore
    private String dirPath;

    public Template(String id, String description, String dirPath) {
        this.id = id;
        this.description = description;
        this.dirPath = dirPath;
    }

    /**
     * @return collection of paths of files in the src template directory
     */
    public Collection<File> srcFiles() {
        File templateSrcDir = new File(dirPath + "/src/");
        File[] srcFiles = templateSrcDir.listFiles();

        Collection<File> srcFilePathsCollection = new ArrayList<>();
        srcFilePathsCollection.addAll(Arrays.asList(srcFiles));

        return srcFilePathsCollection;
    }
}
