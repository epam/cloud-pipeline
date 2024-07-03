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

import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import ExportFormats from './export-formats';
import Discounts from '../discounts';
import {getPeriod} from '../../../special/periods';
import exportBillingURL from '../../../../models/billing/export';

class ExportConsumer extends React.Component {
  componentDidMount () {
    const {export: exportStore} = this.props;
    exportStore.attach(this);
  }

  componentWillUnmount () {
    const {export: exportStore} = this.props;
    exportStore.detach(this);
  }

  getExportData = (...options) => {
    const {
      discounts: discountsConfiguration,
      exportConfiguration
    } = this.props;
    const [defaultOptions = {}] = options;
    const {format} = defaultOptions;
    const {
      computeValue: computes = 0,
      storageValue: storages = 0
    } = discountsConfiguration || {};
    const discount = {
      computes: -computes,
      storages: -storages
    };
    const {
      period = 'month',
      range,
      user,
      billingGroup,
      adGroup,
      types: typesPayload = [],
      filters: extraFilters = {},
      properties: extraProperties
    } = exportConfiguration;
    const {
      start,
      endStrict: end
    } = getPeriod(period, range);
    const filters = {
      owner: user,
      billing_center: billingGroup,
      groups: adGroup,
      ...extraFilters
    };
    const types = [];
    switch (format) {
      case ExportFormats.csvUsers:
        types.push('USER');
        break;
      case ExportFormats.csvCostCenters:
        types.push('BILLING_CENTER');
        break;
      case ExportFormats.rawCsv:
        types.push('RUN');
        break;
      case ExportFormats.csv:
      default:
        types.push(
          ...typesPayload
        );
        break;
    }
    const payload = {
      discount,
      types,
      from: start.format('YYYY-MM-DD'),
      to: end.format('YYYY-MM-DD'),
      filters,
      properties: extraProperties
    };
    return new Promise((resolve, reject) => {
      const request = new XMLHttpRequest();
      request.withCredentials = true;
      request.onreadystatechange = function () {
        if (request.readyState !== 4) return;
        if (request.status !== 200) {
          const error = request.statusText
            ? `Export error: ${request.statusText}`
            : 'Export error';
          reject(new Error(error));
        } else {
          const response = request.responseText;
          try {
            const {status, message} = JSON.parse(response);
            if (status && !/^OK$/i.test(status)) {
              reject(new Error(message));
              return;
            }
          } catch (_) {}
          try {
            const blob = new Blob([response], {type: 'text/csv;charset=utf-8'});
            resolve(blob);
          } catch (e) {
            reject(e);
          }
        }
      };
      request.open('POST', exportBillingURL());
      request.timeout = 1000 * 60 * 5; // 5 minutes;
      request.setRequestHeader('Content-Type', 'application/json');
      request.send(JSON.stringify(payload));
    });
  };

  render () {
    const {children} = this.props;
    return children;
  }
}

ExportConsumer.propTypes = {
  discounts: PropTypes.shape({
    compute: PropTypes.func,
    storage: PropTypes.func,
    computeValue: PropTypes.number,
    storageValue: PropTypes.number
  }),
  exportConfiguration: PropTypes.object
};

const ExportConsumerInjected = inject('export')(
  observer(ExportConsumer)
);

function ExportConsumerWithDiscounts ({children, exportConfiguration}) {
  return (
    <Discounts.Consumer>
      {
        (computeDiscounts, storageDiscounts, computeDiscountValue, storageDiscountValue) => (
          <ExportConsumerInjected
            discounts={{
              compute: computeDiscounts,
              storage: storageDiscounts,
              computeValue: computeDiscountValue,
              storageValue: storageDiscountValue
            }}
            exportConfiguration={exportConfiguration}
          >
            {children}
          </ExportConsumerInjected>
        )
      }
    </Discounts.Consumer>
  );
}

ExportConsumerWithDiscounts.propTypes = {
  discounts: PropTypes.shape({
    compute: PropTypes.func,
    storage: PropTypes.func,
    computeValue: PropTypes.number,
    storageValue: PropTypes.number
  }),
  exportConfiguration: PropTypes.object
};

export default ExportConsumerWithDiscounts;
