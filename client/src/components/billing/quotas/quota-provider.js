/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {observer, Provider} from 'mobx-react';
import {computed, observable} from 'mobx';
import BillingQuotasList from '../../../models/billing/quotas/list';
import QuotaTypes from '../quotas/utilities/quota-types';
import QuotaPeriods from '../quotas/utilities/quota-periods';
import QuotaGroups from '../quotas/utilities/quota-groups';

export const __TOTAL__ = '__total__';

function reduceQuotasByPeriod (quotas = []) {
  return Object
    .values(QuotaPeriods)
    .map(period => {
      const quota = quotas.find(q => q.period === period);
      if (quota) {
        return {[period]: Number(quota.value)};
      }
      return undefined;
    })
    .filter(Boolean)
    .reduce((r, c) => ({...r, ...c}), {});
}

function groupQuotas (quotas = []) {
  const keyFn = quota => [
    quota.quotaGroup || '',
    quota.type || '',
    quota.subject || ''
  ].join('|');
  const unParseKey = key => {
    const [quotaGroup, type, subject] = (key || '').split('|');
    return {
      quotaGroup,
      type,
      subject
    };
  };
  const keys = [...(new Set(quotas.map(keyFn)))];
  return keys.map(key => ({
    ...unParseKey(key),
    quotas: reduceQuotasByPeriod(quotas.filter(quota => key === keyFn(quota)))
  }));
}

function groupQuotasBySubject (quotas = []) {
  return groupQuotas(quotas)
    .filter(quota => quota.quotas)
    .reduce((result, quota) => ({
      ...result,
      [quota.subject || __TOTAL__]: quota.quotas
    }), {});
}

class Quotas {
  @observable list = [];
  @observable enabled = true;

  @computed
  get overallGlobal () {
    return this.getOverallQuotas(QuotaGroups.global);
  }

  @computed
  get overallComputeInstances () {
    return this.getOverallQuotas(QuotaGroups.computeInstances);
  }

  @computed
  get overallStorages () {
    return this.getOverallQuotas(QuotaGroups.storages);
  }

  getOverallQuotas (group) {
    return reduceQuotasByPeriod(
      this.getFilteredQuotas({
        group,
        type: QuotaTypes.overall
      })
    );
  }

  getUsersQuotas (group) {
    const groups = group && group !== QuotaGroups.global
      ? [group]
      : [QuotaGroups.computeInstances, QuotaGroups.storages];
    const usersQuotas = this.getFilteredQuotas({
      groups,
      type: QuotaTypes.user
    });
    return groupQuotas(usersQuotas);
  }

  getSubjectQuotasByTypeAndGroup (type, group = QuotaGroups.global) {
    return groupQuotasBySubject(this.getFilteredQuotas({type, group}));
  }

  getUserQuotas (user, group = QuotaGroups.global) {
    return this
      .getUsersQuotas(group)
      .filter(quota => quota.subject === user);
  }

  getFilteredQuotas (options = {}) {
    if (!this.enabled) {
      return [];
    }
    const {
      group,
      groups = [group].filter(Boolean),
      type,
      types = [type].filter(Boolean),
      subject,
      subjects = [subject].filter(Boolean)
    } = options;
    const test = (allowed, property) =>
      quota => !allowed || allowed.length === 0 || allowed.includes(quota[property]);
    return this.list
      .filter(test(groups, 'quotaGroup'))
      .filter(test(types, 'type'))
      .filter(test(subjects, 'subject'));
  }

  async refreshQuotas () {
    const request = new BillingQuotasList();
    try {
      await request.fetch();
      if (request.error) {
        throw new Error(request.error);
      }
      this.list = request.value || [];
    } catch (error) {
      console.warn(error.message);
    }
  };
}

@observer
class QuotaProvider extends React.Component {
  @observable quotas = new Quotas();

  componentDidMount () {
    (this.quotas.refreshQuotas)();
  }

  render () {
    const {children} = this.props;
    return (
      <Provider quotas={this.quotas}>
        {children}
      </Provider>
    );
  }
}

QuotaProvider.propTypes = {
  children: PropTypes.node
};

export default QuotaProvider;
