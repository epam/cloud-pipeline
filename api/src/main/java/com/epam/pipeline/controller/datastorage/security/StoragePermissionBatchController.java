package com.epam.pipeline.controller.datastorage.security;

import com.epam.pipeline.acl.datastorage.security.StoragePermissionBatchApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.dto.datastorage.security.StoragePermission;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionDeleteBatchRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionInsertBatchRequest;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionLoadBatchRequest;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@Api(value = "Storage object permission batch methods")
@RequiredArgsConstructor
public class StoragePermissionBatchController extends AbstractRestController {

    private static final String ID = "id";

    private final StoragePermissionBatchApiService service;

    @RequestMapping(value = "/datastorage/{id}/permission/batch/upsert", method = RequestMethod.PUT)
    @ResponseBody
    @ApiOperation(value = "Upserts storage object permissions overriding already existing ones.",
            notes = "Upserts storage object permissions overriding already existing ones.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result upsert(@PathVariable(value = ID) final Long id,
                         @RequestBody final StoragePermissionInsertBatchRequest request) {
        service.upsert(id, request);
        return Result.success();
    }

    @RequestMapping(value = "/datastorage/{id}/permission/batch/delete", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(value = "Deletes storage object permissions.",
            notes = "Deletes storage object permissions.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result delete(@PathVariable(value = ID) final Long id,
                         @RequestBody final StoragePermissionDeleteBatchRequest request) {
        service.delete(id, request);
        return Result.success();
    }

    @RequestMapping(value = "/datastorage/{id}/permission/batch/load", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(value = "Loads storage object permissions.",
            notes = "Loads storage object permissions.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<StoragePermission>> load(@PathVariable(value = ID) final Long id,
                                                @RequestBody final StoragePermissionLoadBatchRequest request) {
        return Result.success(service.load(id, request));
    }
}
