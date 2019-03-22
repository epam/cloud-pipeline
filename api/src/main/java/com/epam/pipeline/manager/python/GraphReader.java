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

package com.epam.pipeline.manager.python;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.epam.pipeline.controller.vo.TaskGraphVO;
import com.epam.pipeline.entity.graph.TaskNode;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.manager.CmdExecutor;
import org.springframework.util.StringUtils;

public class GraphReader {

    private static final String INPUT_START = "IN:";
    private static final String OUTPUT_START = "OUT:";
    private static final String TOOL_START = "TOOL:";
    private static final String REGISTRY_PREFIX = "registry=";
    private static final String CPU_PREFIX = "cpu=";
    private static final String RAM_PREFIX = "ram=";
    private static final String VALUES_DELIMITER = ";";
    private static final String DEPENDENCY_DELIMITER = "=>";

    private CmdExecutor cmdExecutor = new CmdExecutor();

    public TaskGraphVO readGraph(String graphScript, String pathToScript, String configFile) {
        String command = Stream.of("python", graphScript, pathToScript, configFile)
                .collect(Collectors.joining(" "));
        String output = cmdExecutor.executeCommand(command);
        return createGraphFromScriptOutput(output);
    }

    protected TaskGraphVO createGraphFromScriptOutput(String output) {

        String[] lines = output.split("\\n");
        if (lines.length == 0) {
            return new TaskGraphVO(new TaskNode(new PipelineTask()));
        }
        // first line is always name of the root task
        String mainTaskName = lines[0].trim();
        // important: for main task inputs and outputs are reverted, due
        // to luigi's approach to graph calculation
        TaskNode mainTask = new TaskNode(new PipelineTask(mainTaskName));
        TaskNode currentTask = mainTask;

        Map<String, TaskNode> tasks = new HashMap<>();
        tasks.put(mainTaskName, mainTask);
        mainTask.getTask().setId(1L);

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith(INPUT_START) && currentTask != null) {
                String value = line.substring(INPUT_START.length());
                parseValue(currentTask.getInputs(), value);
            } else if (line.startsWith(OUTPUT_START) && currentTask != null) {
                String value = line.substring(OUTPUT_START.length());
                parseValue(currentTask.getOutputs(), value);
            } else if (line.startsWith(TOOL_START) && currentTask != null) {
                parseTool(currentTask, line);
            } else if (line.contains(DEPENDENCY_DELIMITER)){
                String[] dependency = line.split(DEPENDENCY_DELIMITER);
                TaskNode fromTask = getOrCreateNode(dependency[0].trim(), tasks);
                String secondTaskName = dependency.length > 1 ? dependency[1].trim(): "";
                // for helper tasks (input files) we omit name and add their output as
                // input to all pipeline
                if (dependency.length == 1 || secondTaskName.isEmpty()) {
                    currentTask = null;
                    mainTask.getOutputs().addAll(fromTask.getInputs());
                } else {
                    TaskNode toTask = getOrCreateNode(secondTaskName, tasks);
                    currentTask = toTask;
                    toTask.addParent(fromTask.getTask().getId());
                }
            }
        }
        //switch output and input for main task
        mainTask.switchInputOutput();
        TaskGraphVO graph = new TaskGraphVO(mainTask);
        graph.setTasks(new ArrayList<>(tasks.values()));
        return graph;
    }

    private void parseTool(TaskNode currentTask, String line) {
        String value = line.substring(TOOL_START.length());
        if (!StringUtils.isEmpty(value)) {
            String[] toolDescription = value.trim().split("\\s+");
            String image = toolDescription[0];
            if (!image.isEmpty()) {
                Tool tool = new Tool(image);
                currentTask.setTool(tool);
                for (int j = 1; j < toolDescription.length; j++) {
                    if (toolDescription[j].startsWith(CPU_PREFIX)) {
                        tool.setCpu(toolDescription[j].substring(CPU_PREFIX.length()));
                    } else if (toolDescription[j].startsWith(RAM_PREFIX)) {
                        tool.setRam(toolDescription[j].substring(RAM_PREFIX.length()));
                    } else if (toolDescription[j].startsWith(REGISTRY_PREFIX)) {
                        tool.setRam(toolDescription[j].substring(REGISTRY_PREFIX.length()));
                    }
                }
            }
        }
    }

    private TaskNode getOrCreateNode(String taskName, Map<String, TaskNode> tasks) {
        if (!tasks.containsKey(taskName)) {
            PipelineTask task = new PipelineTask(taskName);
            task.setId((long)tasks.size() + 1);
            tasks.put(taskName, new TaskNode(task));
        }
        return tasks.get(taskName);
    }

    private void parseValue(Set<String> collection, String valuesString) {
        if (valuesString.isEmpty()) {
            return;
        }
        String[] values = valuesString.split(VALUES_DELIMITER);
        for (String value : values) {
            if (!value.isEmpty()) {
                collection.add(value);
            }
        }
    }
}
