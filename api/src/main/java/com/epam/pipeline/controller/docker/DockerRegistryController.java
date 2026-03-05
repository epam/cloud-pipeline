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
import com.epam.pipeline.controller.vo.docker.DockerRegistryVO;
import com.epam.pipeline.entity.docker.DockerRegistryList;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.DockerRegistryEventEnvelope;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.exception.docker.DockerAuthorizationException;
import com.epam.pipeline.acl.docker.DockerRegistryApiService;
import com.epam.pipeline.utils.AuthorizationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
@Tag(name = "docker-registry-controller", description = "Docker Registry Controller")
public class DockerRegistryController extends AbstractRestController {

    public static final String CERTIFICATE_NAME = "ca.crt";
    public static final String DOCKER_LOGIN_SCRIPT = "docker-login.sh";

    @Autowired
    private DockerRegistryApiService dockerRegistryApiService;

    @RequestMapping(value = "/dockerRegistry/register", method= RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Registers a new docker registry.",
            description = "Registers a new docker registry.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<DockerRegistry> registerDockerRegistry(@RequestBody
            DockerRegistryVO dockerRegistryVO) {
        return Result.success(dockerRegistryApiService.create(dockerRegistryVO));
    }

    @RequestMapping(value = "/dockerRegistry/update", method= RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Updates docker registry.",
            description = "Updates docker registry.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<DockerRegistry> updateDockerRegistry(@RequestBody DockerRegistry dockerRegistry) {
        return Result.success(dockerRegistryApiService.updateDockerRegistry(dockerRegistry));
    }

    @RequestMapping(value = "/dockerRegistry/updateCredentials", method= RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Updates docker registry credentials.",
            description = "Updates docker registry credentials.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<DockerRegistry> updateDockerRegistryCredentials(@RequestBody DockerRegistryVO dockerRegistry) {
        return Result.success(dockerRegistryApiService.updateDockerRegistryCredentials(dockerRegistry));
    }

    @RequestMapping(value = "/dockerRegistry/oauth", method= RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Authorization endpoint for docker registry.",
            description = "Authorization endpoint for docker registry.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public JwtRawToken oauthEndpoint(@RequestParam String service,
            @RequestParam(required = false) String scope, HttpServletRequest request) {
        final String authorization = request.getHeader("Authorization");
        final String[] credentials = AuthorizationUtils.parseBasicAuth(authorization);
        if (Objects.isNull(credentials)) {
            throw new DockerAuthorizationException(service);
        }
        return dockerRegistryApiService.issueTokenForDockerRegistry(credentials[0], credentials[1], service, scope);
    }

    @RequestMapping(value = "/dockerRegistry/loadTree", method= RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Lists all registered docker registries with groups and tools.",
            description = "Lists all registered docker registries with groups and tools.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<DockerRegistryList> loadAllRegistryContent() {
        return Result.success(dockerRegistryApiService.loadAllRegistriesContent());
    }

    @RequestMapping(value = "/dockerRegistry/loadCerts", method= RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Lists registered docker registries with certificates.",
            description = "Lists registered docker registries with certificates.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Map<String, String>> loadRegistryCertificates() {
        DockerRegistryList registryList =
                dockerRegistryApiService.listDockerRegistriesWithCerts();
        Map<String, String> result =
                registryList.getRegistries().stream()
                        .collect(Collectors.toMap(DockerRegistry::getPath, DockerRegistry::getCaCert));
        return Result.success(result);
    }

    @RequestMapping(value = "/dockerRegistry/{id}/load", method= RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Returns a docker registry, specified by id.",
            description = "Returns a docker registry, specified by ID.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<DockerRegistry> loadDockerRegistry(@PathVariable(value = "id") final Long id) {
        return Result.success(dockerRegistryApiService.load(id));
    }

    @RequestMapping(value = "/dockerRegistry/{id}/delete", method= RequestMethod.DELETE)
    @ResponseBody
    @Operation(
            summary = "Deletes a docker registry, specified by id.",
            description = "Deletes a docker registry, specified by id.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<DockerRegistry> deleteDockerRegistry(@PathVariable(value = "id") final Long id,
                                                       @RequestParam(value = "force") final boolean force) {
        return Result.success(dockerRegistryApiService.delete(id, force));
    }

    @RequestMapping(value = "/dockerRegistry/notify", method= RequestMethod.POST)
    @ResponseBody
    @Operation(
            summary = "Notify about docker registry event.",
            description = "Notify about docker registry event.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<Tool>> notifyDockerRegistryEvents(@RequestHeader(value="Registry-Path") final String registry,
                                                         @RequestBody final DockerRegistryEventEnvelope events) {
        return Result.success(dockerRegistryApiService.notifyDockerRegistryEvents(registry, events));
    }


    @RequestMapping(value = "/dockerRegistry/{id}/cert", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Downloads a certificate file",
            description = "Downloads a certificate file for a registry, specified by id.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public void downloadPipelineFile(@PathVariable Long id, HttpServletResponse response)
            throws IOException {
        byte[] bytes = dockerRegistryApiService.getCertificateContent(id);
        writeFileToResponse(response, bytes, CERTIFICATE_NAME);
    }

    @RequestMapping(value = "/dockerRegistry/{id}/login", method = RequestMethod.GET)
    @ResponseBody
    @Operation(
            summary = "Downloads a script to configure local docker client to work with remote repository.",
            description = "Downloads a script to configure local docker client to work with remote repository.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public void downloadConfigScript(@PathVariable Long id, HttpServletResponse response)
            throws IOException {
        byte[] bytes = dockerRegistryApiService.getConfigScript(id);
        writeFileToResponse(response, bytes, DOCKER_LOGIN_SCRIPT);
    }
}
