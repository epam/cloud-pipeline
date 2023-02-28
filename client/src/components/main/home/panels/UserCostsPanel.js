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
import PropTypes from 'prop-types';
import {computed, observable} from 'mobx';
import {inject, observer} from 'mobx-react';
import moment from 'moment-timezone';
import {
  Alert,
  Icon,
  Tooltip,
  message,
  Spin
} from 'antd';
import roleModel from '../../../../utils/roleModel';
import {GetBillingData} from '../../../../models/billing';
import {Period, getPeriod} from '../../../special/periods';
import Markdown from '../../../special/markdown';
import styles from './UserCostsPanel.css';

const BILLING_PERIODS = [Period.month, Period.year];

function renderStatistics (percent) {
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
          type={percent > 0 ? 'caret-up' : 'caret-down'}
          style={{marginRight: '5px'}}
        />
        <span>
          {`${percent > 0 ? '+' : ''}${percent}%`}
        </span>
      </div>
      &#41;
    </div>
  );
}

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

function fetchUserName (userName, authenticatedUserInfo) {
  if (userName) {
    return Promise.resolve(userName);
  }
  return new Promise((resolve) => {
    authenticatedUserInfo
      .fetchIfNeededOrWait()
      .then(() => {
        if (authenticatedUserInfo.loaded && authenticatedUserInfo.value) {
          resolve(authenticatedUserInfo.value.userName);
        } else {
          resolve();
        }
      });
  });
}

@roleModel.authenticationInfo
@inject('preferences')
@observer
export default class UserCostsPanel extends React.Component {
  state={
    pending: false
  }

  @observable
  _billingRequests;

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps) {
    const {userName, period} = this.props;
    if (
      (prevProps.userName !== userName) ||
      (period && period !== prevProps.period)
    ) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {userName, authenticatedUserInfo} = this.props;
    fetchUserName(userName, authenticatedUserInfo)
      .then(this.fetchBillingInfo.bind(this));
  };

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

  @computed
  get disclaimer () {
    const {preferences} = this.props;
    if (preferences.loaded) {
      return preferences.myCostsDisclaimer || '';
    }
    return '';
  }

  fetchBillingInfo = (userName) => {
    const {
      billingPeriods = BILLING_PERIODS,
      period: periodDate
    } = this.props;
    this.setState({pending: true}, async () => {
      const promises = billingPeriods.map(period => {
        return new Promise((resolve) => {
          const rangeFormat = period === Period.month
            ? 'YYYY-MM'
            : 'YYYY';
          const range = periodDate
            ? moment(periodDate).format(rangeFormat)
            : undefined;
          const periodInfo = getPeriod(period, range);
          const request = new GetBillingData({
            filters: {
              user: userName,
              ...periodInfo,
              filterBy: [
                GetBillingData.FILTER_BY.compute,
                GetBillingData.FILTER_BY.storages
              ]
            }
          });
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

  renderDisclaimer = () => {
    if (!this.disclaimer) {
      return null;
    }
    const message = (
      <Markdown
        md={this.disclaimer}
        className={styles.markdown}
      />
    );
    return (
      <Alert
        message={message}
        type="warning"
        style={{padding: '8px 15px', marginBottom: '10px'}}
      />
    );
  };

  renderBillingInfo = (billing) => {
    const {
      showPeriodHeaders = true
    } = this.props;
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
    return (
      [
        showPeriodHeaders && (
          <span
            key="period-header"
            className={
              classNames(
                styles.subHeader,
                'cp-divider',
                'bottom'
              )
            }
          >
            {period}
          </span>
        ),
        <div
          key="period-name"
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
          key="period-current-billing"
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
          key="period-previous"
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
        <span
          key="period-previous-billing"
          className={styles.cell}
        >
          {previous ? (`$${previous}`) : longDash}
        </span>
      ].filter(Boolean)
    );
  };

  render () {
    const {pending} = this.state;
    const {showDisclaimer} = this.props;
    return (
      <Spin
        spinning={pending}
        wrapperClassName={styles.spinner}
      >
        <div className={styles.container}>
          {showDisclaimer ? this.renderDisclaimer() : null}
          <div className={styles.gridContainer}>
            {this.billingInfo.map(billing => (
              this.renderBillingInfo(billing)
            ))}
          </div>
        </div>
      </Spin>
    );
  }
}

UserCostsPanel.PropTypes = {
  showDisclaimer: PropTypes.bool,
  userName: PropTypes.string,
  billingPeriods: PropTypes.arrayOf(PropTypes.string),
  period: PropTypes.string,
  showPeriodHeaders: PropTypes.bool
};

UserCostsPanel.defaultProps = {
  showDisclaimer: true,
  showPeriodHeaders: true
};
