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

package com.epam.pipeline.entity.graph;

import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.Tool;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class TaskNode {
    private PipelineTask task;
    private Set<Long> parents;
    private Set<TaskNode> children;
    private Set<String> inputs;
    private Set<String> outputs;
    private Tool tool;

    public TaskNode() {
        this.inputs =  new HashSet<>();
        this.outputs = new HashSet<>();
        this.children = new HashSet<>();
        this.parents = new HashSet<>();
    }

    public TaskNode(PipelineTask task) {
        this();
        this.task = task;
    }

    public void addParent(Long parent) {
        this.parents.add(parent);
    }

    public void switchInputOutput() {
        Set<String> tmp = this.outputs;
        this.outputs = this.inputs;
        this.inputs = tmp;
    }
}
