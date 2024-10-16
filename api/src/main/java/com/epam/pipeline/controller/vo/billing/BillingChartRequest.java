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

package com.epam.pipeline.controller.vo.billing;

import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.billing.BillingGroupingSortOrder;
import lombok.Value;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Value
public class BillingChartRequest {

    LocalDate from;
    LocalDate to;
    Map<String, List<String>> filters;
    DateHistogramInterval interval;
    BillingGrouping grouping;
    BillingGroupingSortOrder order;
    boolean loadDetails;
    boolean loadCostDetails;
    Long pageSize;
    Long pageNum;

}
