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

package com.epam.pipeline.entity.issue;


import java.util.Arrays;
import java.util.function.Function;

public enum IssueStatus {
    OPEN(0), IN_PROGRESS(1), CLOSED(2);

    private long id;

    IssueStatus(long id) {
        this.id = id;
    }

    public static IssueStatus getById(Long id) {
        return selectByField(IssueStatus::getId, id);
    }

    public static IssueStatus getByName(String name) {
        return selectByField(IssueStatus::name, name);
    }

    private static <T> IssueStatus selectByField(Function<IssueStatus, T> getter, T value) {
        return Arrays.stream(values())
                .filter(status -> getter.apply(status).equals(value))
                .findFirst()
                .orElse(null);
    }

    public Long getId() {
        return this.id;
    }
}
