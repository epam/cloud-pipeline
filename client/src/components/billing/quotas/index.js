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

import React from 'react';
import QuotasSection from './quotas-section';
import {computed, observable} from 'mobx';
import {observer, Provider} from 'mobx-react';
import {Alert} from 'antd';
import roleModel from '../../../utils/roleModel';
import Roles from '../../../models/user/Roles';
import BillingQuotasList from '../../../models/billing/quotas/list';
import LoadingView from '../../special/LoadingView';
import quotaGroups from './utilities/quota-groups';
import quotaTypes, {orderedQuotaTypes, quotaNames} from './utilities/quota-types';
import styles from './quotas.css';

const roles = new Roles();

function parseQuotaGroupFromParams (params = {}) {
  const {
    type = ''
  } = params;
  switch (type.toLowerCase()) {
    case 'compute':
      return quotaGroups.computeInstances;
    case 'storage':
      return quotaGroups.storages;
    case 'global':
    default:
      return quotaGroups.global;
  }
}

@roleModel.authenticationInfo
@observer
class Quotas extends React.Component {
  @observable quotasRequest = new BillingQuotasList();

  componentDidMount () {
    this.quotasRequest.fetchIfNeededOrWait();
    roles.fetchIfNeededOrWait();
  }

  componentWillUnmount () {
    this.quotasRequest.invalidateCache();
    roles.invalidateCache();
  }

  get quotaGroup () {
    return parseQuotaGroupFromParams(this.props.params);
  }

  get isGlobalQuotaGroup () {
    return this.quotaGroup === quotaGroups.global;
  }

  @computed
  get quotas () {
    if (this.quotasRequest.loaded) {
      return (this.quotasRequest.value || [])
        .slice()
        .filter(quota => quota.quotaGroup === this.quotaGroup)
        .map(quota => ({
          ...quota,
          type: quota.type || quotaTypes.overall
        }));
    }
    return [];
  }

  refreshQuotas () {
    return this.quotasRequest.fetch();
  }

  render () {
    const {authenticatedUserInfo} = this.props;
    if (
      !authenticatedUserInfo.loaded && authenticatedUserInfo.pending
    ) {
      return (<LoadingView />);
    }
    if (authenticatedUserInfo.error) {
      return (
        <Alert
          message={authenticatedUserInfo.error}
          type="error"
        />
      );
    }
    if (!roleModel.isManager.billing(this)) {
      return (
        <Alert
          message="Access denied"
          type="error"
        />
      );
    }
    return (
      <Provider roles={roles}>
        <div className={styles.container}>
          {
            orderedQuotaTypes
              .filter(type => !this.isGlobalQuotaGroup || type === quotaTypes.overall)
              .map(type => (
                <QuotasSection
                  key={type}
                  quotaType={type}
                  quotaGroup={this.quotaGroup}
                  title={quotaNames[type] || type}
                  quotas={this.quotas.filter(quota => quota.type === type)}
                  onRefresh={() => this.refreshQuotas()}
                />
              ))
          }
        </div>
      </Provider>
    );
  }
}

export default Quotas;
