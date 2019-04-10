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

package com.epam.pipeline.manager.cloud.gcp.extractor;

import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.gcp.resource.AbstractGCPObject;

import java.util.List;

/**
 * Google Cloud Provider billable object extractor.
 *
 * It generates compute machine and disk descriptions for a specific region.
 */
public interface GCPObjectExtractor {

    /**
     * Extracts Google Cloud Provider billable object from the given region.
     */
    List<AbstractGCPObject> extract(GCPRegion region);
}
