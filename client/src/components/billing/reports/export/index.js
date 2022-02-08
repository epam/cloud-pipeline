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
import {inject, observer, Provider as MobxProvider} from 'mobx-react';
import {Button, Icon} from 'antd';
import Menu, {MenuItem, Divider} from 'rc-menu';
import Dropdown from 'rc-dropdown';
import ExportConsumer from './export-consumer';
import ExportImageConsumer from './export-image-consumer';
import exportStore from './export-store';
import ExportFormat from './export-formats';
import BillingNavigation from '../../navigation';

const ExportFormatName = {
  [ExportFormat.csv]: 'As CSV',
  [ExportFormat.image]: 'As Image',
  [ExportFormat.csvCostCenters]: 'As CSV (Billing centers)',
  [ExportFormat.csvUsers]: 'As CSV (Users)',
  [ExportFormat.rawCsv]: 'Export raw data'
};

class ExportReports extends React.Component {
  static Provider = ({children}) => (
    <MobxProvider export={exportStore}>
      {children}
    </MobxProvider>
  );

  static Consumer = ExportConsumer;

  static ImageConsumer = ExportImageConsumer;

  onExport = (format) => {
    const {
      filters = {},
      users,
      cloudRegionsInfo,
      discounts
    } = this.props;
    const {getDescription} = filters;
    const documentName = typeof getDescription === 'function'
      ? getDescription({
        users,
        cloudRegionsInfo,
        discounts
      })
      : undefined;
    const title = typeof documentName === 'function' ? documentName() : documentName;
    switch (format) {
      case ExportFormat.image:
        exportStore.doImageExport(title, {format});
        break;
      default:
      case ExportFormat.csv:
      case ExportFormat.csvCostCenters:
      case ExportFormat.csvUsers:
      case ExportFormat.rawCsv:
        exportStore.doCsvExport(title, {format});
        break;
    }
  };

  renderExportMenu = () => {
    const {filters: filterStore = {}} = this.props;
    let formats = [ExportFormat.csv, ExportFormat.image];
    if (/^general$/i.test(filterStore.report)) {
      formats = [ExportFormat.csvCostCenters, ExportFormat.csvUsers, ExportFormat.image];
    } else if (/^instances$/i.test(filterStore.report)) {
      formats = [
        ExportFormat.csv,
        ExportFormat.image,
        ExportFormat.divider,
        ExportFormat.rawCsv
      ];
    }
    if (!formats || formats.length === 0) {
      return null;
    }
    return (
      <Menu
        onClick={({key: format}) => this.onExport(format)}
        style={{cursor: 'pointer'}}
        selectedKeys={[]}
      >
        {
          formats.map((format, index) => (
            format === ExportFormat.divider
              ? (<Divider key={`${format}-${index}`} />)
              : (<MenuItem key={format}>{ExportFormatName[format]}</MenuItem>)
          ))
        }
      </Menu>
    );
  };

  render () {
    const {className} = this.props;
    return (
      <Dropdown
        overlay={this.renderExportMenu()}
        trigger={['click']}
      >
        <Button
          id="export-reports"
          className={className}
        >
          <Icon type="export" />
          Export
        </Button>
      </Dropdown>
    );
  }
}

ExportReports.propTypes = {
  className: PropTypes.string
};

ExportReports.defaultProps = {
  documentName: 'Billing report',
  formats: [ExportFormat.csv, ExportFormat.image]
};

export default inject('users', 'cloudRegionsInfo', 'discounts')(
  BillingNavigation.attach(
    observer(ExportReports)
  )
);
export {ExportFormat};
