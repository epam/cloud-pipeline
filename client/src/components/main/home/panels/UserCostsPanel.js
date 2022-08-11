/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import {computed, observable} from 'mobx';
import {observer} from 'mobx-react';
import moment from 'moment-timezone';
import {Icon, Popover, message, Spin} from 'antd';
import roleModel from '../../../../utils/roleModel';
import {GetBillingData} from '../../../../models/billing';
import {Period, getPeriod} from '../../../special/periods';
import styles from './UserCostsPanel.css';

const BILLING_PERIODS = [Period.month, Period.year];

function getTotalCosts (billing) {
  const {
    current,
    previous,
    filters
  } = billing;
  const {
    endStrict,
    previousEndStrict
  } = filters || {};
  if (!billing || !current || !previous) {
    return {};
  }
  if (current && previous) {
    const lastCurrent = ((billing.current._value || {}).values || [])
      .filter(v => v.value && v.initialDate <= endStrict)
      .pop();
    const lastPrevious = ((billing.previous._value || {}).values || [])
      .filter(v => v.value && v.initialDate <= previousEndStrict)
      .pop();
    const currentValue = (lastCurrent || {}).value;
    const previousValue = (lastPrevious || {}).value;
    return {
      current: currentValue,
      previous: previousValue,
      percent: !isNaN(currentValue) && !isNaN(previousValue)
        ? ((Number(currentValue) - Number(previousValue)) / Number(previousValue) * 100)
          .toFixed(2)
        : undefined
    };
  }
  return {};
}

@roleModel.authenticationInfo
@observer
export default class UserCostsPanel extends React.Component {
  state={
    pending: false
  }

  @observable
  _billingRequests;

  componentDidMount () {
    const {authenticatedUserInfo} = this.props;
    if (authenticatedUserInfo) {
      authenticatedUserInfo
        .fetchIfNeededOrWait()
        .then(() => {
          if (authenticatedUserInfo.loaded) {
            const userName = authenticatedUserInfo.value.userName;
            this.fetchBillingInfo(userName);
          }
        });
    }
  }

  @computed
  get billingInfo () {
    if (this._billingRequests && this._billingRequests.length) {
      return this._billingRequests
        .filter(request => request.loaded && !request.error)
        .map(request => {
          return {
            filters: request.filters,
            period: (request.filters || {}).name,
            ...getTotalCosts(request)
          };
        });
    }
    return [];
  }

  fetchBillingInfo = (userName) => {
    this.setState({pending: true}, async () => {
      const promises = BILLING_PERIODS.map(period => {
        const periodInfo = getPeriod(period);
        const request = new GetBillingData(
          {
            user: userName,
            ...periodInfo,
            filterBy: [
              GetBillingData.FILTER_BY.compute,
              GetBillingData.FILTER_BY.storages
            ]
          }
        );
        request.fetch();
        return request;
      });
      const results = await Promise.all(promises);
      results.filter(result => result.error)
        .forEach(result => message.error(result.error, 5));
      this._billingRequests = results;
      this.setState({pending: false});
    });
  };

  renderBillingInfo = (billing) => {
    const {filters, period, percent, current, previous} = billing;
    const {
      start,
      previousStart,
      endStrict,
      previousEndStrict
    } = filters || {};
    const positivePercent = percent !== undefined
      ? percent > 0
      : undefined;
    let format;
    if (period === Period.month) {
      format = 'MMMM';
    } else {
      format = 'YYYY';
    }
    const popoverContent = (dateFrom, dateTo, period) => {
      const from = moment(dateFrom);
      const to = moment(dateTo);
      if (period === Period.month) {
        return `${to.format('MMMM YYYY')}, ${from.format('D')} - ${to.format('D')}`;
      }
      return `${to.year()}`;
    };
    return (
      <tbody key={filters.name}>
        <tr>
          <td
            colSpan={3}
            className={styles.subHeader}
          >
            {period}
          </td>
        </tr>
        <tr>
          <td>
            <Popover
              content={popoverContent(start, endStrict, period)}
            >
              {moment(endStrict).format(format)}
            </Popover>
          </td>
          <td className={styles.bold}>
            {current ? (`$${current}`) : null}
          </td>
          <td
            style={{
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center'
            }}
            className={classNames({
              'cp-danger': positivePercent !== undefined && positivePercent,
              'cp-success': positivePercent !== undefined && !positivePercent
            })}
          >
            {positivePercent !== undefined ? (
              <Icon
                type={positivePercent ? 'caret-up' : 'catet-down'}
                style={{marginRight: '5px'}}
              />
            ) : null
            }
            {positivePercent !== undefined ? (
              <span>
                {`${positivePercent ? '+' : '-'}${percent}%`}
              </span>
            ) : null}
          </td>
        </tr>
        <tr>
          <td>
            <Popover
              content={popoverContent(previousStart, previousEndStrict, period)}
            >
              {moment(previousEndStrict).format(format)}
            </Popover>
          </td>
          <td>
            {previous ? (`$${previous}`) : null}
          </td>
          <td />
        </tr>
      </tbody>
    );
  };

  render () {
    const {pending} = this.state;
    return (
      <Spin spinning={pending}>
        <table className={styles.table}>
          {this.billingInfo.map(billing => (
            this.renderBillingInfo(billing)
          ))}
        </table>
      </Spin>
    );
  }
}
