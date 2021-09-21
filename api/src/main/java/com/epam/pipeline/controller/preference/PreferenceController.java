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

package com.epam.pipeline.controller.preference;

import java.util.Collection;
import java.util.List;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.acl.preference.PreferenceApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/preferences")
public class PreferenceController extends AbstractRestController {

    @Autowired
    private PreferenceApiService preferenceApiService;

    @GetMapping
    public Result<Collection<Preference>> loadAll() {
        return Result.success(preferenceApiService.loadAll());
    }

    @PostMapping
    public Result<List<Preference>> update(@RequestBody List<Preference> preference) {
        return Result.success(preferenceApiService.update(preference));
    }

    @DeleteMapping("{name}")
    public Result<Boolean> delete(@PathVariable String name) {
        preferenceApiService.delete(name);
        return Result.success(true);
    }

    @GetMapping("{name}")
    public Result<Preference> load(@PathVariable String name) {
        return Result.success(preferenceApiService.load(name));
    }

}
