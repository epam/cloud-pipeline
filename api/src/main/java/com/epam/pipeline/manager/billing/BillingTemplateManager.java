/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.billing;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.billing.BillingTemplateDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.billing.BillingReportTemplate;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import com.epam.pipeline.manager.security.acl.AclSync;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@AclSync
@Service
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class BillingTemplateManager implements SecuredEntityManager {

    private final BillingTemplateDao billingTemplateDao;
    private final MessageHelper messageHelper;

    public List<BillingReportTemplate> loadAll() {
        return billingTemplateDao.loadAll();
    }

    @Override
    public BillingReportTemplate load(final Long id) {
        return billingTemplateDao.load(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_BILLING_CUSTOM_TEMPLATE_NOT_FOUND, id)));
    }

    public BillingReportTemplate loadByName(final String name) {
        return billingTemplateDao.loadByName(name).orElseThrow(
                () -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_BILLING_CUSTOM_TEMPLATE_NOT_FOUND, name)));
    }

    @Override
    public BillingReportTemplate loadByNameOrId(final String identifier) {
        if (StringUtils.isEmpty(identifier)) {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(MessageConstants.ERROR_BILLING_CUSTOM_TEMPLATE_ID_EMPTY));
        }
        if (NumberUtils.isNumber(identifier)) {
            return load(Long.getLong(identifier));
        } else {
            return loadByName(identifier);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public BillingReportTemplate create(final BillingReportTemplate template) {
        return billingTemplateDao.create(template);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public BillingReportTemplate update(final BillingReportTemplate template) {
        if (template.getId() == null) {
            throw new IllegalArgumentException();
        }
        final BillingReportTemplate loaded = load(template.getId());
        if (loaded != null) {
            return billingTemplateDao.update(template);
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public BillingReportTemplate changeOwner(final Long id, final String owner) {
        final BillingReportTemplate toBeUpdated = load(id);
        if (toBeUpdated == null) {
            throw new IllegalArgumentException();
        }
        toBeUpdated.setOwner(owner);
        billingTemplateDao.update(toBeUpdated);
        return toBeUpdated;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(final Long id) {
        billingTemplateDao.delete(id);
    }

    @Override
    public Integer loadTotalCount() {
        return loadAll().size();
    }

    @Override
    public Collection<? extends AbstractSecuredEntity> loadAllWithParents(final Integer page, final Integer pageSize) {
        return Collections.emptyList();
    }

    @Override
    public AbstractSecuredEntity loadWithParents(final Long id) {
        return load(id);
    }

    @Override
    public AclClass getSupportedClass() {
        return AclClass.BILLING_REPORT_TEMPLATE;
    }
}
