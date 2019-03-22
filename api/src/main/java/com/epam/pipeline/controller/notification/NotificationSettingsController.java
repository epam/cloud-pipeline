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

package com.epam.pipeline.controller.notification;

import java.util.List;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.manager.notification.NotificationSettingsApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/notification/settings")
public class NotificationSettingsController extends AbstractRestController {

    @Autowired
    private NotificationSettingsApiService notificationSettingsApiService;

    @GetMapping
    public Result<List<NotificationSettings>> loadAll() {
        return Result.success(notificationSettingsApiService.loadAll());
    }

    @PostMapping
    public Result<NotificationSettings> createOrUpdate(@RequestBody NotificationSettings settings) {
        return Result.success(notificationSettingsApiService.createOrUpdate(settings));
    }

    @GetMapping("{id}")
    public Result<NotificationSettings> load(@PathVariable long id) {
        return Result.success(notificationSettingsApiService.load(id));
    }

    @DeleteMapping("{id}")
    public Result<Boolean> delete(@PathVariable long id) {
        notificationSettingsApiService.delete(id);
        return Result.success(true);
    }
}
