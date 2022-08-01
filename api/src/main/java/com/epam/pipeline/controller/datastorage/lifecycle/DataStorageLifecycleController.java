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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@Api(value = "Datastorage lifecycle methods")
public class DataStorageLifecycleController extends AbstractRestController {

    public static final String DATASTORAGE_ID = "datastorageId";
    public static final String RULE_ID = "ruleId";
    @Autowired
    private DataStorageLifecycleApiService dataStorageLifecycleApiService;

    @GetMapping(value = "/datastorage/{datastorageId}/lifecycle/rule")
    @ResponseBody
    @ApiOperation(
            value = "Lists all available lifecycle rules for specific data storage.",
            notes = "Lists all available lifecycle rules for specific data storage.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<StorageLifecycleRule>> listStorageLifecyclePolicyRules(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId) {
        return Result.success(dataStorageLifecycleApiService.listStorageLifecyclePolicyRules(datastorageId));
    }

    @GetMapping(value = "/datastorage/{datastorageId}/lifecycle/rule/{ruleId}")
    @ResponseBody
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
    @ResponseBody
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
    @ResponseBody
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

    @PutMapping(value = "/datastorage/{datastorageId}/lifecycle/rule/{ruleId}/prolong")
    @ResponseBody
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
            @RequestParam(value = "path") final String path,
            @RequestParam(value = "days", required = false) final Long days) {
        return Result.success(
                dataStorageLifecycleApiService.prolongStorageLifecyclePolicyRule(datastorageId, ruleId, path, days)
        );
    }

    @DeleteMapping(value = "/datastorage/{datastorageId}/lifecycle/rule/{ruleId}")
    @ResponseBody
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
    @ResponseBody
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
    @ResponseBody
    @ApiOperation(
            value = "Updates lifecycle rule execution status.",
            notes = "Updates lifecycle rule execution status.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<StorageLifecycleRuleExecution> updateStorageLifecycleRuleExecutionStatus(
            @PathVariable(value = DATASTORAGE_ID) final Long datastorageId,
            @PathVariable(value = "executionId") final Long executionId,
            @RequestParam(value = "status") final StorageLifecycleRuleExecutionStatus status) {
        return Result.success(
                dataStorageLifecycleApiService.updateStorageLifecycleRuleExecutionStatus(
                        datastorageId, executionId, status));
    }

    @GetMapping(value = "/datastorage/{datastorageId}/lifecycle/rule/{ruleId}/execution")
    @ResponseBody
    @ApiOperation(
            value = "Lists all available lifecycle rule executions.",
            notes = "Lists all available lifecycle rule executions. Could be filtered by path",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<StorageLifecycleRuleExecution>> listStorageLifecyclePolicyRuleExecutions(
            @PathVariable(value = RULE_ID) final Long ruleId,
            @RequestParam(value = "path", required = false) final String path) {
        return Result.success(dataStorageLifecycleApiService.listStorageLifecyclePolicyRuleExecutions(ruleId, path));
    }
}
