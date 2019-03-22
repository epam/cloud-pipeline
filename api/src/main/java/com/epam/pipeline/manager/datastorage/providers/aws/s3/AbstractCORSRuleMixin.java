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

package com.epam.pipeline.manager.datastorage.providers.aws.s3;

import com.amazonaws.services.s3.model.CORSRule;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.List;

public abstract class AbstractCORSRuleMixin extends CORSRule {

    @Override
    @JsonSetter
    public abstract void setAllowedMethods(List<AllowedMethods> allowedMethods);

    @Override
    @JsonSetter
    public abstract void setAllowedOrigins(List<String> allowedOrigins);

    @Override
    @JsonSetter("ExposeHeaders")
    public abstract void setExposedHeaders(List<String> exposedHeaders);

    @Override
    @JsonSetter
    public abstract void setAllowedHeaders(List<String> allowedHeaders);
}
