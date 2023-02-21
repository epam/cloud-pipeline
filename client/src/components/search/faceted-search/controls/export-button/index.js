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
import {
  Dropdown,
  Icon,
  message
} from 'antd';
import Menu, {MenuItem} from 'rc-menu';
import FileSaver from 'file-saver';
import ExportConfigurationModal from './configuration-modal';
import {getSortingPayload} from '../../utilities';
import {
  Name,
  Changed,
  Size,
  Owner,
  Path,
  CloudPath,
  MountPath
} from '../../utilities/document-columns';
import FacetedSearchExport from '../../../../../models/search/faceted-search-export';
import checkBlob from '../../../../../utils/check-blob';

const exportVOColumns = {
  [Name.key]: 'includeName',
  [Changed.key]: 'includeChanged',
  [Size.key]: 'includeSize',
  [Owner.key]: 'includeOwner',
  [Path.key]: 'includePath',
  [CloudPath.key]: 'includeCloudPath',
  [MountPath.key]: 'includeMountPath'
};

function ExportMenu ({onExport, onConfigure}) {
  const handle = ({key}) => {
    switch (key) {
      case 'export':
        if (typeof onExport === 'function') {
          onExport();
        }
        break;
      case 'configure':
        if (typeof onConfigure === 'function') {
          onConfigure();
        }
        break;
      default:
        break;
    }
  };
  return (
    <Menu
      onClick={handle}
      selectedKeys={[]}
      style={{cursor: 'pointer'}}
    >
      <MenuItem key="export">
        <Icon type="download" style={{marginRight: 10}} />
        Default configuration
      </MenuItem>
      <MenuItem key="configure">
        <Icon type="bars" style={{marginRight: 10}} />
        Custom configuration
      </MenuItem>
    </Menu>
  );
}

class ExportButton extends React.Component {
  state = {
    pending: false,
    modalVisible: false,
    dropdownVisible: false
  };

  openModal = () => {
    this.setState({
      modalVisible: true,
      dropdownVisible: false
    });
  };

  closeModal = () => {
    this.setState({
      modalVisible: false,
      dropdownVisible: false
    });
  };

  handleDropDownVisible = (visible) => {
    this.setState({
      dropdownVisible: visible
    });
  };

  onConfigure = () => this.openModal();

  onExport = async (configuration = []) => {
    this.closeModal();
    const setStateAwaited = (state) =>
      new Promise((resolve) =>
        this.setState(state, () => resolve()));
    const hide = message.loading('Exporting...', 0);
    await setStateAwaited({pending: true});
    const {
      advanced,
      columns = [],
      query: currentQuery,
      filters = {},
      sorting = [],
      facets = []
    } = this.props;
    const columnsToExport = configuration && configuration.length
      ? columns.filter((aColumn) => configuration.includes(aColumn.key))
      : columns.slice();
    try {
      const keys = columnsToExport.map((column) => column.key);
      const metadataFields = keys.filter((key) => !exportVOColumns[key]);
      const facetedSearchExportVO = Object.entries(exportVOColumns)
        .reduce((acc, [key, exportVOKey]) => ({
          ...acc,
          [exportVOKey]: !!keys.includes(key)
        }), {delimiter: ','});
      const csvFileName = 'export.csv';
      const payload = {
        csvFileName: 'export.csv',
        facetedSearchExportVO,
        facetedSearchRequest: {
          query: !advanced && currentQuery
            ? `*${currentQuery}*`
            : (currentQuery || '*'),
          filters,
          sorts: getSortingPayload(sorting),
          metadataFields,
          facets: facets.map((facet) => facet.name),
          highlight: false
        }
      };
      const request = new FacetedSearchExport();
      await request.send(payload);
      if (request.value instanceof Blob) {
        const error = await checkBlob(request.value, 'Error exporting search results');
        if (error) {
          throw new Error(error);
        }
        FileSaver.saveAs(request.value, csvFileName);
      } else {
        throw new Error(request.error || 'Error downloading search results');
      }
    } catch (error) {
      message.error(error.message, 5);
    } finally {
      hide();
      this.setState({
        pending: false
      });
    }
  };

  onDefaultExport = () => this.onExport();

  render () {
    const {
      columns,
      className,
      style,
      size,
      type
    } = this.props;
    const {
      modalVisible,
      dropdownVisible
    } = this.state;
    return (
      <Dropdown.Button
        overlay={(
          <ExportMenu
            onExport={this.onDefaultExport}
            onConfigure={this.onConfigure}
          />
        )}
        className={className}
        style={style}
        size={size}
        type={type}
        trigger={['click']}
        visible={dropdownVisible}
        onVisibleChange={this.handleDropDownVisible}
        onClick={this.onDefaultExport}
      >
        <Icon type="download" />
        Export
        <ExportConfigurationModal
          visible={modalVisible}
          onCancel={this.closeModal}
          onExport={this.onExport}
          columns={columns}
        />
      </Dropdown.Button>
    );
  }
}

ExportButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  size: PropTypes.string,
  type: PropTypes.string,
  disabled: PropTypes.bool,
  columns: PropTypes.arrayOf(PropTypes.shape({
    key: PropTypes.string,
    name: PropTypes.string
  })),
  advanced: PropTypes.bool,
  query: PropTypes.string,
  filters: PropTypes.object,
  sorting: PropTypes.array,
  facets: PropTypes.array
};

export default ExportButton;
