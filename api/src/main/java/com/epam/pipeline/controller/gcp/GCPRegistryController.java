package com.epam.pipeline.controller.gcp;

import com.epam.pipeline.acl.gcp.GCPRegistryApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.entity.pipeline.GcpArtifactRegistryEventBody;
import com.epam.pipeline.entity.pipeline.Tool;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Api(value = "GCP Artifact Registry")
public class GCPRegistryController extends AbstractRestController {

    @Autowired
    private GCPRegistryApiService gcpRegistryApiService;

    @RequestMapping(value = "/gcpRegistry/notify", method= RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Notify about gcp registry event.",
            notes = "Notify about gcp registry event.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public ResponseEntity<Tool> notifyGCPRegistryEvent(@RequestHeader(value="Authorization") final String jwtToken,
                                                       @RequestBody final GcpArtifactRegistryEventBody event) {
        return ResponseEntity.ok(gcpRegistryApiService.notifyGcpRegistryEvent(jwtToken, event));
    }
}
