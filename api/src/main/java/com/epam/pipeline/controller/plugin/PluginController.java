/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.controller.plugin;

import com.epam.pipeline.acl.plugin.PluginAssignmentService;
import com.epam.pipeline.acl.plugin.PluginService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.dto.plugin.PluginType;
import com.epam.pipeline.dto.plugin.UIPlugin;
import com.epam.pipeline.dto.plugin.UIPluginAssignment;
import com.epam.pipeline.entity.sharing.StaticResourceSettings;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/plugins")
@RequiredArgsConstructor
@Tag(name = "plugin-controller", description = "UI Plugins API")
public class PluginController extends AbstractRestController {

    private final PluginService pluginService;
    private final PluginAssignmentService assignmentService;
    private final PreferenceManager preferenceManager;
    private static final Map<String, MediaType> EXTENSION_TO_MEDIA_TYPE;
    static {
        EXTENSION_TO_MEDIA_TYPE = new HashMap<>();
        EXTENSION_TO_MEDIA_TYPE.put("js", MediaType.valueOf("application/javascript"));
        EXTENSION_TO_MEDIA_TYPE.put("css", MediaType.valueOf("text/css"));
        EXTENSION_TO_MEDIA_TYPE.put("html", MediaType.TEXT_HTML);
    }

    @GetMapping
    @Operation(
            summary = "List UI plugins",
            description = "Returns a list of UI plugins, optionally filtered by type."/*,
            response = UIPlugin.class,
            responseContainer = "List"*/)
    @ApiResponses({@ApiResponse(description = "Successfully retrieved plugins")})
    public Result<List<UIPlugin>> getPlugins(
            @Parameter(description = "Optional plugin type filter (LaunchForm or RunLog)")
            @RequestParam(required = false) PluginType type) {
        return Result.success(pluginService.getPlugins(type));
    }

    @PostMapping
    @Operation(
            summary = "Create or update a UI plugin",
            description = "Creates a new plugin if ID is absent, updates existing plugin if ID is provided.")
    @ApiResponses({@ApiResponse(description = "Plugin created or updated successfully")})
    public Result<UIPlugin> savePlugin(
            @Parameter(description = "Plugin details", required = true)
            @RequestBody UIPlugin plugin) {
        return Result.success(pluginService.savePlugin(plugin));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a UI plugin by ID",
            description = "Returns the plugin with the specified ID."/*,
            response = UIPlugin.class*/)
    @ApiResponses({@ApiResponse(description = "Plugin retrieved successfully")})
    public Result<UIPlugin> getPlugin(
            @Parameter(description = "Plugin ID", required = true)
            @PathVariable Long id) {
        return Result.success(pluginService.getPlugin(id));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a UI plugin",
            description = "Deletes the plugin with the specified ID.")
    @ApiResponses({@ApiResponse(description = "Plugin deleted successfully")})
    public void deletePlugin(
            @Parameter(description = "Plugin ID", required = true)
            @PathVariable Long id) {
        pluginService.deletePlugin(id);
    }

    @GetMapping(
            value = "/{id}/content/**",
            produces = {"text/javascript", "application/javascript", "text/css"})
    @Operation(
            summary = "Get plugin file content",
            description = "Returns the content of a plugin file specified by ID and relative path."/*,
            response = String.class*/)
    @ApiResponses({@ApiResponse(description = "File content retrieved successfully")})
    public void getPluginFileContent(
            @Parameter(description = "Plugin ID", required = true)
            @PathVariable Long id,
            final HttpServletRequest request,
            final HttpServletResponse response) throws IOException {
        final String contentPrefix = String.format("/plugins/%s/content", id);
        final int prefixIndex = request.getRequestURI().indexOf(contentPrefix);
        if (prefixIndex < 0) {
            throw new IllegalArgumentException("File path is required");
        }
        final String path = request.getRequestURI().substring(prefixIndex + contentPrefix.length());
        final byte[] bytes = pluginService.getPluginFileContent(id, path);
        final String fileName = FilenameUtils.getName(path);
        final MediaType mediaType = defineMediaTypeByFileExtension(fileName);
        final StaticResourceSettings settings = getStaticResourceSettings(fileName);
        writeStreamToResponse(response, new ByteArrayInputStream(bytes), fileName, mediaType,
                !MediaType.APPLICATION_OCTET_STREAM.equals(mediaType), getCustomHeaders(settings));
    }

    @GetMapping("/assign")
    @Operation(
            summary = "List plugin assignments",
            description = "Returns a list of plugin assignments, " +
                    "optionally filtered by tool ID, pipeline ID, or version."
            /*
            response = UIPluginAssignment.class,
            responseContainer = "List"*/)
    @ApiResponses({@ApiResponse(description = "Successfully retrieved assignments")})
    public Result<List<UIPluginAssignment>> getAssignments(
            @Parameter(description = "Optional tool ID filter")
            @RequestParam(required = false) Long toolId,
            @Parameter(description = "Optional pipeline ID filter")
            @RequestParam(required = false) Long pipelineId,
            @Parameter(description = "Optional version filter")
            @RequestParam(required = false) String version) {
        return Result.success(assignmentService.getAssignments(toolId, pipelineId, version));
    }

    @PostMapping("/assign")
    @Operation(
            summary = "Create or update a plugin assignment",
            description = "Creates a new assignment if ID is absent, updates existing assignment if ID is provided."/*,
            response = UIPluginAssignment.class*/)
    @ApiResponses({@ApiResponse(description = "Assignment created or updated successfully")})
    public Result<UIPluginAssignment> saveAssignment(
            @Parameter(description = "Assignment details", required = true)
            @RequestBody UIPluginAssignment assignment) {
        return Result.success(assignmentService.saveAssignment(assignment));
    }

    @GetMapping("/assign/{id}")
    @Operation(
            summary = "Get a plugin assignment by ID",
            description = "Returns the assignment with the specified ID."/*,
            response = UIPluginAssignment.class*/)
    @ApiResponses({@ApiResponse(description = "Assignment retrieved successfully")})
    public Result<UIPluginAssignment> getAssignment(
            @Parameter(description = "Assignment ID", required = true)
            @PathVariable Long id) {
        return Result.success(assignmentService.getAssignment(id));
    }

    @DeleteMapping("/assign/{id}")
    @Operation(
            summary = "Delete a plugin assignment",
            description = "Deletes the assignment with the specified ID.")
    @ApiResponses({@ApiResponse(description = "Assignment deleted successfully")})
    public void deleteAssignment(
            @Parameter(description = "Assignment ID", required = true)
            @PathVariable Long id) {
        assignmentService.deleteAssignment(id);
    }

    private StaticResourceSettings getStaticResourceSettings(final String fileName) {
        final Map<String, StaticResourceSettings> settings = preferenceManager.getPreference(
                SystemPreferences.DATA_SHARING_STATIC_RESOURCE_SETTINGS);
        if (settings == null) {
            return new StaticResourceSettings();
        }
        final String extension = FilenameUtils.getExtension(fileName);
        return settings.getOrDefault(extension, new StaticResourceSettings());
    }

    private Map<String, String> getCustomHeaders(final StaticResourceSettings settings) {
        return Optional.ofNullable(settings.getHeaders()).orElse(Collections.emptyMap());
    }

    private MediaType defineMediaTypeByFileExtension(final String filenameOrExtension) {
        if (StringUtils.isEmpty(filenameOrExtension)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        String extension = filenameOrExtension.toLowerCase();
        if (extension.contains(".")) {
            extension = extension.substring(extension.lastIndexOf('.') + 1);
        } else {
            extension = extension.replaceAll("^\\.+", "");
        }

        return EXTENSION_TO_MEDIA_TYPE.getOrDefault(extension, MediaType.APPLICATION_OCTET_STREAM);
    }
}