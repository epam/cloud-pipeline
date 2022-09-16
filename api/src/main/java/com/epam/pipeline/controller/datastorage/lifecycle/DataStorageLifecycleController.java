/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.datastorage.lifecycle;

import com.epam.pipeline.acl.datastorage.lifecycle.DataStorageLifecycleApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecution;
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecutionStatus;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreAction;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionRequest;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionSearchFilter;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestorePathType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Api(value = "Datastorage lifecycle methods")
public class DataStorageLifecycleController extends AbstractRestController {

    public static final String DATASTORAGE_ID = "datastorageId";
    public static final String RULE_ID = "ruleId";
    public static final String PATH = "path";
    public static final String EXECUTION_ID = "executionId";
    public static final String STATUS = "status";
    public static final String FORCE = "force";
    public static final String DAYS = "days";

    @Autowired
    private DataStorageLifecycleApiService dataStorageLifecycleApiService;

    @GetMapping(value = "/datastorage/{datastorageId}/lifecycle/rule")
    @ApiOperation(
            value = "Lists all available lifecycle rules for specific data storage.",
            notes = "Lists all available lifecycle rules for specific data storage.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<StorageLifecycleRule>> listStorageLifecyclePolicyRules(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @RequestParam(value = PATH, required = false) final String path) {
        return Result.success(dataStorageLifecycleApiService.listStorageLifecyclePolicyRules(datastorageId, path));
    }

    @GetMapping(value = "/datastorage/{datastorageId}/lifecycle/rule/{ruleId}")
    @ApiOperation(
            value = "Gets specific lifecycle rule for specific data storage.",
            notes = "Gets specific lifecycle rule for specific data storage.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<StorageLifecycleRule> loadStorageLifecyclePolicyRule(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @PathVariable(value = RULE_ID) final Long ruleId) {
        return Result.success(dataStorageLifecycleApiService.loadStorageLifecyclePolicyRule(datastorageId, ruleId));
    }

    @PostMapping(value = "/datastorage/{datastorageId}/lifecycle/rule")
    @ApiOperation(
            value = "Creates lifecycle rule for specific data storage.",
            notes = "Creates lifecycle rule for specific data storage.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<StorageLifecycleRule> createStorageLifecyclePolicyRule(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @RequestBody final StorageLifecycleRule rule) {
        return Result.success(dataStorageLifecycleApiService.createStorageLifecyclePolicyRule(datastorageId, rule));
    }

    @PutMapping(value = "/datastorage/{datastorageId}/lifecycle/rule")
    @ApiOperation(
            value = "Updates lifecycle rule for specific data storage.",
            notes = "Updates lifecycle rule for specific data storage.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<StorageLifecycleRule> updateStorageLifecyclePolicyRule(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @RequestBody final StorageLifecycleRule rule) {
        return Result.success(dataStorageLifecycleApiService.updateStorageLifecyclePolicyRule(datastorageId, rule));
    }

    @GetMapping(value = "/datastorage/{datastorageId}/lifecycle/rule/{ruleId}/prolong")
    @ApiOperation(
            value = "Shift a moment in time when action from lifecycle rule should take place.",
            notes = "Shift a moment in time when action from lifecycle rule should take place.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<StorageLifecycleRule> prolongStorageLifecyclePolicyRule(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @PathVariable(value = RULE_ID) final Long ruleId,
            @RequestParam(value = PATH) final String path,
            @RequestParam(value = DAYS, required = false) final Long days,
            @RequestParam(value = FORCE, required = false, defaultValue = FALSE) final Boolean force) {
        return Result.success(
                dataStorageLifecycleApiService.prolongStorageLifecyclePolicyRule(
                        datastorageId, ruleId, path, days, force));
    }

    @DeleteMapping(value = "/datastorage/{datastorageId}/lifecycle/rule/{ruleId}")
    @ApiOperation(
            value = "Deletes specific lifecycle rule for specific data storage.",
            notes = "Deletes specific lifecycle rule for specific data storage.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<StorageLifecycleRule> deleteStorageLifecyclePolicyRule(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @PathVariable(value = RULE_ID) final Long ruleId) {
        return Result.success(dataStorageLifecycleApiService.deleteStorageLifecyclePolicyRule(datastorageId, ruleId));
    }

    @PostMapping(value = "/datastorage/{datastorageId}/lifecycle/rule/{ruleId}/execution")
    @ApiOperation(
            value = "Creates lifecycle rule execution for specific rule.",
            notes = "Creates lifecycle rule execution for specific rule.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<StorageLifecycleRuleExecution> createStorageLifecycleRuleExecution(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @PathVariable(value = RULE_ID) final Long ruleId,
            @RequestBody final StorageLifecycleRuleExecution execution) {
        return Result.success(
                dataStorageLifecycleApiService.createStorageLifecyclePolicyRuleExecution(
                        datastorageId, ruleId, execution));
    }

    @PutMapping(value = "/datastorage/{datastorageId}/lifecycle/rule/execution/{executionId}/status")
    @ApiOperation(
            value = "Updates lifecycle rule execution status.",
            notes = "Updates lifecycle rule execution status.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<StorageLifecycleRuleExecution> updateStorageLifecycleRuleExecutionStatus(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @PathVariable(value = EXECUTION_ID) final Long executionId,
            @RequestParam(value = STATUS) final StorageLifecycleRuleExecutionStatus status) {
        return Result.success(
                dataStorageLifecycleApiService.updateStorageLifecycleRuleExecutionStatus(
                        datastorageId, executionId, status));
    }

    @DeleteMapping(value = "/datastorage/{datastorageId}/lifecycle/rule/execution/{executionId}")
    @ApiOperation(
            value = "Deletes lifecycle rule execution.",
            notes = "Deletes lifecycle rule execution.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<StorageLifecycleRuleExecution> deleteStorageLifecycleRuleExecution(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @PathVariable(value = EXECUTION_ID) final Long executionId) {
        return Result.success(
                dataStorageLifecycleApiService.deleteStorageLifecycleRuleExecution(
                        datastorageId, executionId));
    }

    @GetMapping(value = "/datastorage/{datastorageId}/lifecycle/rule/{ruleId}/execution")
    @ApiOperation(
            value = "Lists all available lifecycle rule executions.",
            notes = "Lists all available lifecycle rule executions. Could be filtered by path and status",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<StorageLifecycleRuleExecution>> filterStorageRestoreActions(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @PathVariable(value = RULE_ID) final Long ruleId,
            @RequestParam(value = PATH, required = false) final String path,
            @RequestParam(value = STATUS, required = false) final StorageLifecycleRuleExecutionStatus status) {
        return Result.success(
                dataStorageLifecycleApiService.listStorageLifecyclePolicyRuleExecutions(
                        datastorageId, ruleId, path, status));
    }

    @PostMapping(value = "/datastorage/{datastorageId}/lifecycle/restore")
    @ApiOperation(
            value = "Initiate process of restoring objects in datastorage under specified path.",
            notes = "Initiate process of restoring objects in datastorage under specified path.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<StorageRestoreAction>> initiateRestoreStorageObjects(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @RequestBody final StorageRestoreActionRequest request) {
        return Result.success(
                dataStorageLifecycleApiService.initiateStorageRestores(datastorageId, request));
    }

    @PutMapping(value = "/datastorage/{datastorageId}/lifecycle/restore")
    @ApiOperation(
            value = "Updates lifecycle restore action.",
            notes = "Updates lifecycle restore action.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<StorageRestoreAction> updateRestoreAction(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @RequestBody final StorageRestoreAction action) {
        return Result.success(dataStorageLifecycleApiService.updateStorageRestoreAction(datastorageId, action));
    }

    @PostMapping(value = "/datastorage/{datastorageId}/lifecycle/restore/filter")
    @ApiOperation(
            value = "Filter lifecycle restore actions for storage",
            notes = "Filter lifecycle restore actions for storage",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<StorageRestoreAction>> filterStorageRestoreActions(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @RequestBody final StorageRestoreActionSearchFilter filter) {
        return Result.success(
                dataStorageLifecycleApiService.filterRestoreStorageActions(datastorageId, filter));
    }

    @GetMapping(value = "/datastorage/{datastorageId}/lifecycle/restore/effective")
    @ApiOperation(
            value = "Find last applied restore action for datastorage and path.",
            notes = "Find last applied restore action for datastorage and path.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<StorageRestoreAction> loadEffectiveRestoreStoragePathAction(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @RequestParam(value = PATH) final String path,
            @RequestParam final StorageRestorePathType pathType) {
        return Result.success(
                dataStorageLifecycleApiService.loadEffectiveRestoreStorageAction(
                        datastorageId, path, pathType));
    }

    @GetMapping(value = "/datastorage/{datastorageId}/lifecycle/restore/effectiveHierarchy")
    @ApiOperation(
            value = "Loads hierarchy of last applied restore actions for datastorage and path.",
            notes = "Loads hierarchy of last applied restore actions for datastorage and path.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<StorageRestoreAction>> loadEffectiveHierarchyRestoreStoragePathAction(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @RequestParam(value = PATH) final String path,
            @RequestParam final StorageRestorePathType pathType,
            @RequestParam(defaultValue = "false") final Boolean recursive) {
        return Result.success(
                dataStorageLifecycleApiService.loadEffectiveRestoreStorageActionHierarchy(
                        datastorageId, path, pathType, recursive));
    }
}
