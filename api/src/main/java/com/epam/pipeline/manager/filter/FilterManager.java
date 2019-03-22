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

package com.epam.pipeline.manager.filter;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.FilterFieldVO;
import com.epam.pipeline.controller.vo.PagingRunFilterExpressionVO;
import com.epam.pipeline.dao.filter.FilterDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FilterManager {

    @Autowired
    private FilterDao filterDao;

    public PagedResult<List<PipelineRun>> filterRuns(PagingRunFilterExpressionVO pagingRunFilterExpressionVO)
            throws WrongFilterException {
        Assert.isTrue(pagingRunFilterExpressionVO.getPage() > 0,
                "Page should be greater, than 0");
        Assert.isTrue(pagingRunFilterExpressionVO.getPageSize() > 0,
                "Page size should be greater, than 0");

        FilterExpression expression = FilterExpression.generate(
                pagingRunFilterExpressionVO.getFilterExpression(),
                pagingRunFilterExpressionVO.getAllowedPipelines(),
                pagingRunFilterExpressionVO.getOwnershipFilter()
        );

        List<PipelineRun> runs = filterDao.filterPipelineRuns(
                expression,
                pagingRunFilterExpressionVO.getPage(),
                pagingRunFilterExpressionVO.getPageSize(),
                pagingRunFilterExpressionVO.getTimezoneOffsetInMinutes());
        int count = filterDao.countFilterPipelineRuns(expression,
                pagingRunFilterExpressionVO.getTimezoneOffsetInMinutes());
        return new PagedResult<>(runs, count);
    }

    public List<FilterFieldVO> getAvailableFilterKeywords(Class context) {
        Field[] fields = context.getFields();
        List<FilterFieldVO> result = new ArrayList<>();
        for (Field field : fields) {
            FilterFields filterFields = field.getAnnotation(FilterFields.class);
            if (filterFields != null) {
                for (FilterField filterField : filterFields.value()) {
                    FilterFieldVO keyword = new FilterFieldVO();
                    keyword.setFieldName(filterField.displayName());
                    keyword.setFieldDescription(filterField.description());
                    keyword.setRegex(filterField.isRegex());
                    keyword.setSupportedOperands(
                            Arrays.stream(filterField.supportedOperands())
                                    .map(FilterOperandType::getOperand).collect(Collectors.toList())
                    );
                    result.add(keyword);
                }
            } else {
                FilterField filterField = field.getAnnotation(FilterField.class);
                if (filterField != null) {
                    FilterFieldVO keyword = new FilterFieldVO();
                    keyword.setFieldName(filterField.displayName());
                    keyword.setFieldDescription(filterField.description());
                    keyword.setRegex(filterField.isRegex());
                    keyword.setSupportedOperands(
                            Arrays.stream(filterField.supportedOperands())
                                    .map(FilterOperandType::getOperand).collect(Collectors.toList())
                    );
                    result.add(keyword);
                }
            }
        }
        return result;
    }
}
