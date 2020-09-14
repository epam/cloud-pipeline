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
import {Provider as MobxProvider} from 'mobx-react';
import {Button, Dropdown, Icon, Menu} from 'antd';
import ExportConsumer from './export-consumer';
import ExportImageConsumer from './export-image-consumer';
import exportStore from './export-store';
import * as ExportComposers from './composers';
import ExportFormat from './export-formats';

const ExportFormatName = {
  [ExportFormat.csv]: 'As CSV',
  [ExportFormat.image]: 'As Image',
  [ExportFormat.csvCostCenters]: 'As CSV (Cost centers)',
  [ExportFormat.csvUsers]: 'As CSV (Users)'
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
    const {documentName} = this.props;
    const title = typeof documentName === 'function' ? documentName() : documentName;
    switch (format) {
      case ExportFormat.image:
        exportStore.doImageExport(title, {format});
        break;
      default:
      case ExportFormat.csv:
      case ExportFormat.csvCostCenters:
      case ExportFormat.csvUsers:
        exportStore.doCsvExport(title, {format});
        break;
    }
  };

  renderExportMenu = (formats = [ExportFormat.csv, ExportFormat.image]) => {
    if (!formats || formats.length === 0) {
      return null;
    }
    return (
      <Menu onClick={({key: format}) => this.onExport(format)}>
        {
          formats.map((format) => (
            <Menu.Item key={format}>{ExportFormatName[format]}</Menu.Item>
          ))
        }
      </Menu>
    );
  };

  render () {
    const {className, formats} = this.props;
    return (
      <Dropdown
        overlay={this.renderExportMenu(formats)}
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
  className: PropTypes.string,
  documentName: PropTypes.oneOfType([PropTypes.func, PropTypes.string]),
  formats: PropTypes.arrayOf(PropTypes.oneOf([
    ExportFormat.csv,
    ExportFormat.csvCostCenters,
    ExportFormat.csvUsers,
    ExportFormat.image
  ]))
};

ExportReports.defaultProps = {
  documentName: 'Billing report',
  formats: [ExportFormat.csv, ExportFormat.image]
};

export default ExportReports;
export {ExportComposers, ExportFormat};
