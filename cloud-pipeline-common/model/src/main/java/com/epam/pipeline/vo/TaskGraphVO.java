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

package com.epam.pipeline.vo;

import com.epam.pipeline.entity.graph.TaskNode;
import java.util.ArrayList;
import java.util.List;

public class TaskGraphVO {

    private TaskNode mainTask;
    private List<TaskNode> tasks;

    public TaskGraphVO() {
        this.tasks = new ArrayList<>();
    }

    public TaskGraphVO(TaskNode mainTask) {
        this();
        this.mainTask = mainTask;
    }

    public TaskNode getMainTask() {
        return mainTask;
    }

    public void setMainTask(TaskNode mainTask) {
        this.mainTask = mainTask;
    }

    public List<TaskNode> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskNode> tasks) {
        this.tasks = tasks;
    }
}
