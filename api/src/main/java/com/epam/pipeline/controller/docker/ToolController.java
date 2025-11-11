/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.docker;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.docker.ImageDescription;
import com.epam.pipeline.entity.docker.ImageHistoryLayer;
import com.epam.pipeline.entity.docker.ToolDescription;
import com.epam.pipeline.entity.docker.ToolImageDockerfile;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.docker.ToolVersionAttributes;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.scan.ToolScanPolicy;
import com.epam.pipeline.entity.scan.ToolScanResultView;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.entity.tool.ToolSymlinkRequest;
import com.epam.pipeline.acl.docker.ToolApiService;
import com.epam.pipeline.manager.pipeline.ToolManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@Tag(name = "Tools")
public class ToolController extends AbstractRestController {

    public static final String REGISTRY_PARAM = "registry";
    public static final String IMAGE_PARAM = "image";
    public static final String LABELS_PARAM = "labels";
    private static final Set<String> ALLOWED_ICON_EXTENSIONS = new HashSet<>(
        Arrays.asList("jpg", "jpeg", "png", "gif"));
    private static final String VERSION = "version";
    private static final String FROM = "from";
    private static final String DOCKERFILE = "Dockerfile";

    @Autowired
    private ToolApiService toolApiService;

    @Autowired
    private ToolManager toolManager;

    @RequestMapping(value = "/tool/register", method= RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Registers a new tool.",
            description = "Registers a new tool.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Tool> registerTool(@RequestBody Tool tool) {
        return Result.success(toolApiService.create(tool));
    }

    @RequestMapping(value = "/tool/update", method= RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Updates tool's requirements.",
            description = "Updates tool's requirements.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Tool> updateTool(@RequestBody Tool tool) {
        return Result.success(toolApiService.updateTool(tool));
    }

    @RequestMapping(value = "/tool/updateWhiteList", method= RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Add or remove image from white list.",
            description = "Add or remove image from white list.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<ToolVersionScanResult> updateWhiteListWithToolVersion(@RequestParam long toolId,
                                                                        @RequestParam String version,
                                                                        @RequestParam(defaultValue = "true")
                                                                                    boolean whiteList) {
        return Result.success(toolApiService.updateWhiteListWithToolVersion(toolId, version, whiteList));
    }

    @RequestMapping(value = "/tool/load", method= RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns a tool, specified by image name.",
            description = "Returns a tool, specified by image name.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Tool> loadTool(@RequestParam(required = false, value = REGISTRY_PARAM) final String registry,
                                 @RequestParam(value = IMAGE_PARAM) final String image) {
        return Result.success(toolApiService.loadTool(registry, image));
    }

    @RequestMapping(value = "/tool/delete", method= RequestMethod.DELETE)
    @ResponseBody
    @Operation(
            summary = "Deletes a tool, specified by image name.",
            description = "Deletes a tool, specified by image name. "
                    + "To delete tools's version, provide a auxiliary parameter \"version\". "
                    + "To delete the whole tool, provide no \"version\" parameter. "
                    + "Use \"hard\" == true to delete tool from database and docker registry as well.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Tool> deleteTool(@RequestParam(required = false, value = REGISTRY_PARAM) final String registry,
                                   @RequestParam(value = IMAGE_PARAM) final String image,
                                   @RequestParam(value = VERSION, required = false) final String version,
                                   @RequestParam(value = "hard", defaultValue = "false") boolean hard) {
        if (version == null) {
            return Result.success(toolApiService.delete(registry, image, hard));
        } else {
            return Result.success(toolApiService.deleteToolVersion(registry, image, version));
        }
    }

    @RequestMapping(value = "/tool/{id}/tags", method= RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns a list of tags for a tool, specified by ID.",
            description = "Returns a list of tags for a tool, specified by ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<String>> loadImageTags(@PathVariable final Long id) {
        return Result.success(toolApiService.loadImageTags(id));
    }

    @RequestMapping(value = "/tool/{id}/description", method= RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns a description of a tool, specified by ID and tag.",
            description = "Returns a description of a tool, specified by ID and tag.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<ImageDescription> loadImageDescription(@PathVariable final Long id,
            @RequestParam(value = "tag") final String tag) {
        return Result.success(toolApiService.getImageDescription(id, tag));
    }

    @RequestMapping(value = "/tool/{id}/history", method= RequestMethod.GET)
    @ResponseBody
    @Operation(
        summary = "Returns a history of a tool, specified by ID and version.",
        description = "Returns a history of a tool, which contains list of commands by layers of the image.")
    @ApiResponses(
        value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
        })
    public Result<List<ImageHistoryLayer>> loadImageHistory(@PathVariable final Long id,
                                                            @RequestParam(value = VERSION) final String version) {
        return Result.success(toolApiService.getImageHistory(id, version));
    }

    @RequestMapping(value = "/tool/{id}/dockerfile", method= RequestMethod.GET)
    @ResponseBody
    @Operation(
        summary = "Returns a dockerfile of a tool, specified by ID and version.",
        description = "Returns a dockerfile of a tool, which contains list of commands by layers of the image.")
    @ApiResponses(
        value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
        })
    public void loadDockerFile(@PathVariable final Long id,
                                         @RequestParam(value = VERSION) final String version,
                                         @RequestParam(value = FROM) final String from,
                                         HttpServletResponse response) throws IOException {
        final ToolImageDockerfile dockerfile = toolApiService.loadDockerFile(id, version, from);
        byte[] bytes = StringUtils.join(dockerfile.getContent(), "\n").getBytes();
        writeFileToResponse(response, bytes, DOCKERFILE);
    }

    @RequestMapping(value = "/tool/{id}/defaultCmd", method= RequestMethod.GET)
    @ResponseBody
    @Operation(
        summary = "Returns a default command [ENTRYPOINT + CMD] of a tool, specified by ID and version.",
        description = "Returns a default command for a tool, extracted from image history.")
    @ApiResponses(
        value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
        })
    public Result<String> loadDefaultImageCmd(@PathVariable final Long id,
                                              @RequestParam(value = VERSION) final String version) {
        return Result.success(toolApiService.getImageDefaultCommand(id, version));
    }

    @RequestMapping(value = "/tool/scan", method = RequestMethod.POST)
    @ResponseBody
    public Result<Boolean> scanTool(
            @RequestParam(required = false) String registry,
            @RequestParam String tool,
            @RequestParam String version,
            @RequestParam(required = false, defaultValue = "false") Boolean rescan) {
        toolApiService.forceScanTool(registry, tool, version, rescan);
        return Result.success(true);
    }

    @DeleteMapping(value = "/tool/scan")
    @ResponseBody
    public Result<Boolean> clearToolScan(@RequestParam(required = false) String registry,
                                         @RequestParam String tool,
                                         @RequestParam String version) {
        toolApiService.clearToolScan(registry, tool, version);
        return Result.success(true);
    }

    @RequestMapping(value = "/tool/scan", method = RequestMethod.GET)
    @ResponseBody
    public Result<ToolScanResultView> loadVulnerabilities(@RequestParam(required = false) String registry,
                                                          @RequestParam String tool) {
        return Result.success(toolApiService.loadToolScanResult(registry, tool));
    }

    @RequestMapping(value = "/tool/scan/policy", method = RequestMethod.GET)
    @ResponseBody
    public Result<ToolScanPolicy> loadSecurityPolicy() {
        return Result.success(toolApiService.loadSecurityPolicy());
    }

    @RequestMapping(value = "/tool/scan/enabled", method = RequestMethod.GET)
    @ResponseBody
    public Result<Boolean> isToolScanningEnabled() {
        return Result.success(toolManager.isToolScanningEnabled());
    }

    @PostMapping(value = "/tool/{toolId}/icon")
    @ResponseBody
    public Result<Long> uploadToolIcon(@RequestParam("file") final MultipartFile file, @PathVariable long toolId)
        throws FileUploadException, IOException {
        if (file.isEmpty()) {
            throw new FileUploadException("File is empty!");
        }
        return Result.success(toolApiService.updateToolIcon(toolId, file.getOriginalFilename(), file.getBytes()));
    }

    @GetMapping(value = "/tool/{toolId}/icon")
    public void downloadToolIcon(HttpServletResponse response, @PathVariable long toolId) throws IOException {
        Pair<String, InputStream> fileNameAndStream = toolApiService.loadToolIcon(toolId);
        writeStreamToResponse(response, fileNameAndStream.getRight(), fileNameAndStream.getLeft(),
                              guessMediaType(fileNameAndStream.getLeft()));
    }

    @DeleteMapping(value = "/tool/{toolId}/icon")
    @ResponseBody
    public Result<Boolean> deleteToolIcon(@PathVariable long toolId) {
        toolApiService.deleteToolIcon(toolId);
        return Result.success(true);
    }

    @GetMapping(value = "/tool/{toolId}/attributes")
    @ResponseBody
    @Operation(
            summary = "Loads tool attributes for all tool versions, specified by tool ID.",
            description = "Loads tool attributes for all tool versions, specified by tool ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<ToolDescription> loadToolAttributes(@PathVariable Long toolId) {
        return Result.success(toolApiService.loadToolAttributes(toolId));
    }

    @GetMapping(value = "/tool/{toolId}/info")
    @ResponseBody
    @Operation(
            summary = "Loads tool and info for all its versions",
            description = "Loads tool and info for all its versions")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<ToolDescription> loadToolInfo(@PathVariable Long toolId) {
        return Result.success(toolApiService.loadToolInfo(toolId));
    }

    @GetMapping(value = "/tool/{toolId}/attributes", params = VERSION)
    @ResponseBody
    @Operation(
            summary = "Loads tool attributes for a tool version, specified by tool ID.",
            description = "Loads tool attributes for a tool version, specified by tool ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<ToolVersionAttributes> loadToolVersionAttributes(@PathVariable final Long toolId,
                                                                   @RequestParam final String version) {
        return Result.success(toolApiService.loadToolVersionAttributes(toolId, version));
    }

    @PostMapping(value = "/tool/{toolId}/settings")
    @ResponseBody
    @Operation(
            summary = "Creates tool settings for tool version.",
            description = "Creates tool settings for tool version.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<ToolVersion> createToolVersionSettings(@PathVariable final Long toolId,
                                                         @RequestParam final String version,
                                                         @RequestParam(required = false,
                                                             defaultValue = "true") final boolean allowCommit,
                                                         @RequestBody final List<ConfigurationEntry> settings) {
        return Result.success(toolApiService.createToolVersionSettings(toolId, version, allowCommit, settings));
    }

    @GetMapping(value = "/tool/{toolId}/settings")
    @ResponseBody
    @Operation(
            summary = "Loads tool settings for tool version.",
            description = "Loads tool settings for tool version.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<ToolVersion>> loadToolVersionSettings(@PathVariable final Long toolId,
                                                             @RequestParam(required = false) final String version) {
        return Result.success(toolApiService.loadToolVersionSettings(toolId, version));
    }

    @RequestMapping(value = "/tool/symlink", method= RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Symlinks an existing tool.",
            description = "Symlinks an existing tool.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Tool> symlinkTool(@RequestBody final ToolSymlinkRequest request) {
        return Result.success(toolApiService.symlink(request));
    }
}
