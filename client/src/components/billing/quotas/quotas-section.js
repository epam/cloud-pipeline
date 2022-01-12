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
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {Button, Icon, message} from 'antd';
import classNames from 'classnames';
import QuotaDescription from './utilities/quota-description';
import EditQuotaDialog from './quota-edit-dialog';
import CreateBillingQuota from '../../../models/billing/quotas/create';
import UpdateBillingQuota from '../../../models/billing/quotas/update';
import DeleteBillingQuota from '../../../models/billing/quotas/delete';
import quotaTypes, {quotaNames} from './utilities/quota-types';
import {periodNames} from './utilities/quota-periods';
import styles from './quotas.css';

function subjectSorter (a, b) {
  const {subject: aSubject = ''} = a;
  const {subject: bSubject = ''} = b;
  return aSubject.localeCompare(bSubject);
}

@inject('users', 'roles', 'billingCenters')
@observer
class QuotasSection extends React.Component {
  static propTypes = {
    disabled: PropTypes.bool,
    quotaType: PropTypes.string,
    quotaGroup: PropTypes.string,
    quotas: PropTypes.array,
    title: PropTypes.string,
    onRefresh: PropTypes.func
  };

  state = {
    editableQuota: null
  };

  get quotas () {
    const {quotas} = this.props;
    if (quotas.loaded) {
      return (quotas.value || []).map(q => q);
    }
    return [];
  }

  @computed
  get quotaTargets () {
    const {quotaType} = this.props;
    switch (quotaType) {
      case quotaTypes.billingCenter: {
        return [];
      }
      case quotaTypes.user: {
        return [];
      }
      case quotaTypes.group: {
        return [];
      }
      case quotaTypes.overall:
      default:
        return [];
    }
  }

  addNewQuota = () => {
    const {
      quotaType,
      quotaGroup
    } = this.props;
    this.setState({
      editableQuota: {
        quotaGroup,
        type: quotaType
      }
    });
  };

  cancelEditQuota = () => {
    this.setState({editableQuota: null});
  };

  onQuotaClick = (quota) => {
    this.setState({editableQuota: quota});
  };

  onEditQuota = async (quota) => {
    if (!quota) {
      return;
    }
    const {quotas = []} = this.props;
    const hasSameQuota = !!quotas.find(q =>
      q.id !== quota.id &&
      q.type === quota.type &&
      q.quotaGroup === quota.quotaGroup &&
      q.subject === quota.subject &&
      q.period === quota.period
    );
    if (hasSameQuota) {
      let error = (
        <span className={styles.message}>
          <b>{quotaNames[quota.type]}</b>
          <span>quota per</span>
          <b>{(periodNames[quota.period] || quota.period || '').toLowerCase()}</b>
          <span>already exists</span>
          {quota.subject ? (<span>for <b>{quota.subject}</b></span>) : undefined}
        </span>
      );
      message.error(error, 5);
      return;
    }
    const hide = message.loading(quota.id ? 'Updating quota...' : 'Creating quota', 0);
    try {
      const {onRefresh} = this.props;
      const {id, ...quotaPayload} = quota;
      const request = quota.id ? new UpdateBillingQuota(id) : new CreateBillingQuota();
      await request.send(quotaPayload);
      if (request.error) {
        throw new Error(request.error);
      } else {
        if (onRefresh) {
          await onRefresh();
        }
        this.setState({editableQuota: null});
      }
    } catch (e) {
      message.error(e.message, 5);
    } finally {
      hide();
    }
  };

  onRemoveQuota = async () => {
    const hide = message.loading('Removing quota...', 0);
    try {
      const {editableQuota} = this.state;
      const request = new DeleteBillingQuota(editableQuota.id);
      await request.send();
      if (request.error) {
        throw new Error(request.error);
      } else {
        const {onRefresh} = this.props;
        if (onRefresh) {
          await onRefresh();
        }
        this.setState({editableQuota: null});
      }
    } catch (e) {
      message.error(e.message, 5);
    } finally {
      hide();
    }
  };

  render () {
    const {
      disabled,
      title,
      quotas = [],
      roles
    } = this.props;
    const {
      editableQuota
    } = this.state;
    return (
      <div
        className={
          classNames(
            styles.sectionContainer,
            'cp-divider',
            'bottom'
          )
        }
      >
        <div className={styles.section}>
          <div className={styles.header}>
            <div className={styles.title}>
              {title || ''}
            </div>
            <div className={styles.actions}>
              <Button
                disabled={disabled}
                size="small"
                onClick={this.addNewQuota}
                style={{lineHeight: 1}}
              >
                <Icon type="plus" /> Add quota
              </Button>
            </div>
          </div>
          {
            quotas.length === 0 && (
              <div className="cp-text-not-important">
                No quotas configured.
              </div>
            )
          }
          <div>
            {
              quotas
                .sort(subjectSorter)
                .map(quota => (
                  <QuotaDescription
                    key={`quota-${quota.id}`}
                    className={
                      classNames(
                        styles.quota,
                        'cp-table-element',
                        'cp-even-odd-element'
                      )
                    }
                    onClick={() => this.onQuotaClick(quota)}
                    quota={quota}
                    roles={roles}
                  />
                ))
            }
          </div>
          <EditQuotaDialog
            visible={!!editableQuota}
            onCancel={this.cancelEditQuota}
            onRemove={this.onRemoveQuota}
            onSave={this.onEditQuota}
            quota={editableQuota}
          />
        </div>
      </div>
    );
  }
}

export default QuotasSection;
