/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.billingreportagent.service.impl.converter;

import com.epam.pipeline.billingreportagent.model.billing.StoragePricing;
import com.epam.pipeline.entity.region.CloudProvider;

import java.util.Map;

public interface StoragePriceListLoader {

    int CENTS_IN_DOLLAR = 100;
    int BYTES_TO_GB = 1 << (Integer.SIZE - 2);
    int PRECISION = 5;

    String DEFAULT_STORAGE_CLASS = "STANDARD";

    Map<String, StoragePricing> loadFullPriceList() throws Exception;

    CloudProvider getProvider();
}
