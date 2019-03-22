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

package com.epam.pipeline.entity.pipeline;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PipelineTask {
    private static final String TASK_ID_FORMAT = "%s(%s)";

    private String name;
    private String parameters;
    private Long id;
    private TaskStatus status;
    private String instance;
    private Date created;
    private Date started;
    private Date finished;

    public PipelineTask() {
        // no op
    }

    public PipelineTask(String name) {
        String trimmedName = name.trim();
        int openIndex = trimmedName.indexOf('(');
        if (openIndex != -1 && trimmedName.endsWith(")")) {
            this.name = trimmedName.substring(0, openIndex);
            this.parameters = trimmedName.substring(openIndex + 1, trimmedName.length() - 1);
        } else {
            this.name = trimmedName;
        }
    }

    public static String buildTaskId(String taskName, String parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return taskName.trim();
        } else {
            return String.format(TASK_ID_FORMAT, taskName.trim(), parameters.trim());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PipelineTask task = (PipelineTask) o;

        if (!name.equals(task.name)) {
            return false;
        }
        return parameters != null ? parameters.equals(task.parameters) : task.parameters == null;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (parameters != null ? parameters.hashCode() : 0);
        return result;
    }
}
