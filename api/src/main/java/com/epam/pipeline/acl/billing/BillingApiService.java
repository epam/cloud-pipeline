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

package com.epam.pipeline.acl.billing;

import com.epam.pipeline.controller.ResultWriter;
import com.epam.pipeline.controller.vo.billing.BillingChartRequest;
import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.entity.billing.BillingChartInfo;
import com.epam.pipeline.entity.billing.BillingReportTemplate;
import com.epam.pipeline.entity.search.FacetedSearchResult;
import com.epam.pipeline.manager.billing.BillingManager;
import com.epam.pipeline.manager.billing.BillingTemplateManager;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BillingApiService {

    private final BillingManager billingManager;
    private final BillingTemplateManager billingTemplateManager;

    public List<BillingChartInfo> getBillingChartInfo(final BillingChartRequest request) {
        return billingManager.getBillingChartInfo(request);
    }

    public FacetedSearchResult getAvailableFields(final BillingChartRequest request) {
        return billingManager.getAvailableFacets(request);
    }

    public List<BillingChartInfo> getBillingChartInfoPaginated(final BillingChartRequest request) {
        return billingManager.getBillingChartInfoPaginated(request);
    }

    public List<String> getAllBillingCenters() {
        return billingManager.getAllBillingCenters();
    }

    public ResultWriter export(final BillingExportRequest request) {
        return billingManager.export(request);
    }

    @PostFilter(AclExpressions.BILLING_TEMPLATE_READ_FILTER)
    public List<BillingReportTemplate> getAvailableCustomBillingTemplates() {
        return billingTemplateManager.loadAll();
    }

    @PreAuthorize(AclExpressions.ADMIN_OR_GENERAL_USER)
    public BillingReportTemplate createCustomBillingTemplates(final BillingReportTemplate template) {
        return billingTemplateManager.create(template);
    }

    @PreAuthorize(AclExpressions.BILLING_TEMPLATE_ID_READ)
    public BillingReportTemplate getCustomBillingTemplate(final Long id) {
        return billingTemplateManager.load(id);
    }

    @PreAuthorize(AclExpressions.BILLING_TEMPLATE_ID_WRITE)
    public void deleteCustomBillingTemplate(final Long id) {
        billingTemplateManager.delete(id);
    }

    @PreAuthorize(AclExpressions.BILLING_TEMPLATE_WRITE)
    public BillingReportTemplate updateCustomBillingTemplates(final BillingReportTemplate template) {
        return billingTemplateManager.update(template);
    }
}
