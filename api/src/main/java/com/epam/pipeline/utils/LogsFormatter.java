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

package com.epam.pipeline.utils;

import java.text.SimpleDateFormat;

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.PipelineTask;

public class LogsFormatter {
    private final SimpleDateFormat dateFormat = new SimpleDateFormat(Constants.FMT_ISO_LOCAL_DATE);
    private static final String TAB_DELIMITER = "\t";
    private static final String NEW_LINE_DELIMITER = "\n";

    public String formatLog(RunLog log) {
        StringBuilder builder = new StringBuilder();
        PipelineTask task = log.getTask();
        builder.append('[')
                .append(dateFormat.format(log.getDate()).trim()).append(TAB_DELIMITER)
                .append(']')
                .append(log.getStatus()).append(TAB_DELIMITER).append(task.getName())
                .append(TAB_DELIMITER);
        if (task.getParameters() != null) {
            builder.append(task.getParameters()).append(NEW_LINE_DELIMITER);
        }
        builder.append(log.getLogText());
        if (!log.getLogText().endsWith(NEW_LINE_DELIMITER)) {
            builder.append(NEW_LINE_DELIMITER);
        }
        return builder.toString();
    }
}
