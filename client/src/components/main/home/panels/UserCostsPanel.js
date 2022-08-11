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
import {
  Icon,
  Tooltip,
  message,
  Spin
} from 'antd';
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
        return new Promise((resolve) => {
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
          request.fetch()
            .then(() => resolve(request))
            .catch(() => resolve(request));
        });
      });
      const results = await Promise.all(promises);
      this.setState({pending: false}, () => {
        results.filter(result => result.error)
          .forEach(result => message.error(result.error, 5));
        this._billingRequests = results;
      });
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
    let format;
    if (period === Period.month) {
      format = 'MMMM';
    } else {
      format = 'YYYY';
    }
    const longDash = (
      <span style={{fontWeight: 'normal'}}>
        &#8211;
      </span>
    );
    const popoverContent = (dateFrom, dateTo, period) => {
      const from = moment(dateFrom);
      const to = moment(dateTo);
      if (period === Period.month) {
        return `${to.format('MMMM YYYY')}, ${from.format('D')} - ${to.format('D')}`;
      }
      return `${to.year()}`;
    };
    const renderStatistics = (percent) => {
      if (percent === undefined || Number(percent) === 0) {
        return null;
      }
      return (
        <div className={styles.statistics}>
          &#40;
          <div
            className={classNames({
              'cp-danger': percent > 0,
              'cp-success': percent < 0
            })}
          >
            <Icon
              type={percent > 0 ? 'caret-up' : 'catet-down'}
              style={{marginRight: '5px'}}
            />
            <span>
              {`${percent > 0 ? '+' : '-'}${percent}%`}
            </span>
          </div>
          &#41;
        </div>
      );
    };
    return (
      [
        <span
          className={
            classNames(
              styles.subHeader,
              'cp-divider',
              'bottom'
            )
          }
        >
          {period}
        </span>,
        <div
          className={classNames(
            styles.cell,
            styles.leftColumn
          )}
        >
          <Tooltip
            placement="topRight"
            title={popoverContent(start, endStrict, period)}
          >
            <span>
              {moment(endStrict).format(format)}:
            </span>
          </Tooltip>
        </div>,
        <div
          className={classNames(
            styles.cell,
            styles.spendingsContainer
          )}
        >
          <span className={styles.bold}>
            {current ? (`$${current}`) : longDash}
          </span>
          {renderStatistics(percent)}
        </div>,
        <div
          className={classNames(
            styles.cell,
            styles.leftColumn
          )}
        >
          <Tooltip
            placement="topRight"
            title={popoverContent(previousStart, previousEndStrict, period)}
          >
            <span>
              {moment(previousEndStrict).format(format)}:
            </span>
          </Tooltip>
        </div>,
        <span className={styles.cell}>
          {previous ? (`$${previous}`) : longDash}
        </span>
      ]
    );
  };

  render () {
    const {pending} = this.state;
    return (
      <Spin
        spinning={pending}
        wrapperClassName={styles.spinner}
      >
        <div className={styles.gridContainer}>
          {this.billingInfo.map(billing => (
            this.renderBillingInfo(billing)
          ))}
        </div>
      </Spin>
    );
  }
}
