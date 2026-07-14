/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.compute.quota;

import com.epam.pipeline.dto.compute.quota.ComputeQuotaActionType;
import com.epam.pipeline.dto.compute.quota.ComputeQuotaStrategyType;
import com.epam.pipeline.dto.compute.quota.QuotaFilterExpression;
import com.epam.pipeline.entity.utils.TimestampConverter;
import com.epam.pipeline.hibernate.QuotaFilterExpressionUserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@Table(name = "compute_quota_rule", schema = "pipeline")
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(name = "QuotaFilterExpressionUserType", typeClass = QuotaFilterExpressionUserType.class)
public class ComputeQuotaRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_type", nullable = false)
    private ComputeQuotaStrategyType strategyType;

    @Type(type = "QuotaFilterExpressionUserType")
    @Column(name = "filter_expression", nullable = false)
    private QuotaFilterExpression filterExpression;

    @Type(type = "QuotaFilterExpressionUserType")
    @Column(name = "exclude_expression")
    private QuotaFilterExpression excludeExpression;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ComputeQuotaActionType actionType;

    @Column(name = "action_value", nullable = false)
    private int actionValue;

    @Column(name = "action_message")
    private String actionMessage;

    @Column(name = "per_incident", nullable = false)
    private boolean perIncident;

    @Convert(converter = TimestampConverter.class)
    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @Convert(converter = TimestampConverter.class)
    @Column(name = "modified_date", nullable = false)
    private LocalDateTime modifiedDate;
}
