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

package com.epam.pipeline.acl.dts;

import com.epam.pipeline.controller.vo.dts.TransferTask;
import com.epam.pipeline.controller.vo.dts.TransferTaskFilter;
import com.epam.pipeline.manager.dts.TransferTaskService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.epam.pipeline.security.acl.AclExpressions.DTS_REGISTRY_PERMISSIONS;

@Service
@AllArgsConstructor
public class TransferTaskApiService {
    private TransferTaskService taskService;

    @PreAuthorize(DTS_REGISTRY_PERMISSIONS)
    public List<TransferTask> create(final List<TransferTask> transferTasks) {
        return taskService.create(transferTasks);
    }

    @PreAuthorize(DTS_REGISTRY_PERMISSIONS)
    public List<TransferTask> loadAll() {
        return taskService.loadAll();
    }

    @PreAuthorize(DTS_REGISTRY_PERMISSIONS)
    public Page<TransferTask> filter(final TransferTaskFilter filter) {
        return taskService.filter(filter);
    }
}
