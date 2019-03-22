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

package com.epam.pipeline.rest;

public class PagedResult<T> { // TODO: refactor to extend Result class
    private T elements; // TODO; refactor to contain a list of T
    private int totalCount;

    public PagedResult(T elements, int totalCount) {
        this.elements = elements;
        this.totalCount = totalCount;
    }

    public T getElements() {
        return elements;
    }

    public int getTotalCount() {
        return totalCount;
    }
}
