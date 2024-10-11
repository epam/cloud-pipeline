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
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {Link} from 'react-router';
import {
  Dropdown,
  Icon,
  message,
  Button,
  Modal
} from 'antd';
import Menu, {MenuItem, Divider} from 'rc-menu';
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
import roleModel from '../../../../../utils/roleModel';
import FacetedSearchExport from '../../../../../models/search/faceted-search-export';
// eslint-disable-next-line max-len
import FacetedSearchExportTemplatesSave from '../../../../../models/search/faceted-search-export-templates-save';
// eslint-disable-next-line max-len
import FacetedSearchExportTemplates from '../../../../../models/search/faceted-search-export-templates';
import checkBlob from '../../../../../utils/check-blob';
import displayDate from '../../../../../utils/displayDate';

const exportVOColumns = {
  [Name.key]: 'includeName',
  [Changed.key]: 'includeChanged',
  [Size.key]: 'includeSize',
  [Owner.key]: 'includeOwner',
  [Path.key]: 'includePath',
  [CloudPath.key]: 'includeCloudPath',
  [MountPath.key]: 'includeMountPath'
};

function parseExportTemplates (rawTemplates = {}) {
  return Object.entries(rawTemplates).map(([key, template]) => ({
    ...template,
    key
  }));
}

function checkTemplatePermissions (userInfo, template) {
  if (!userInfo) {
    return false;
  }
  const {userName, groups = []} = userInfo;
  return template.permissions.some(permission => {
    if (permission.principal) {
      return permission.name === userName;
    }
    return roleModel.userHasRole(
      userInfo,
      (permission.name || '').toUpperCase()
    ) || groups.includes(permission.nzme);
  });
}

function ExportMenu ({onExport, onExportTemplate, onConfigure, templates}) {
  const handle = ({key}) => {
    const [exportType, exportKey] = key.split('|');
    switch (exportType) {
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
      case 'template':
        if (typeof onExportTemplate === 'function') {
          const currentTemplate = templates.find(({key}) => key === exportKey);
          onExportTemplate(currentTemplate);
        }
        break;
      default:
        break;
    }
  };
  const templatesSection = templates.length ? [
    <Divider key="divider" />,
    ...templates.map(template => (
      <MenuItem key={`template|${template.key}`}>
        {template['friendly_name'] || template.key}
      </MenuItem>
    ))
  ] : [];
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
      {templatesSection}
    </Menu>
  );
}

@inject('preferences', 'authenticatedUserInfo', 'dataStorages')
@observer
class ExportButton extends React.Component {
  state = {
    pending: false,
    modalVisible: false,
    dropdownVisible: false
  };

  @computed
  get exportTemplates () {
    const {preferences} = this.props;
    if (preferences?.loaded) {
      return preferences.searchExportTemplates;
    }
    return undefined;
  }

  @computed
  get isAdmin () {
    const {authenticatedUserInfo} = this.props;
    if (authenticatedUserInfo.loaded) {
      return authenticatedUserInfo.value.admin;
    }
    return false;
  }

  getFacetedSearchExportPayload = (configuration) => {
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
    const keys = columnsToExport.map((column) => column.key);
    const metadataFields = keys.filter((key) => !exportVOColumns[key]);
    return {
      query: !advanced && currentQuery
        ? `*${currentQuery}*`
        : (currentQuery || '*'),
      filters,
      sorts: getSortingPayload(sorting),
      metadataFields,
      facets: facets.map((facet) => facet.name),
      highlight: false
    };
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
    const {columns = []} = this.props;
    const columnsToExport = configuration && configuration.length
      ? columns.filter((aColumn) => configuration.includes(aColumn.key))
      : columns.slice();
    try {
      const keys = columnsToExport.map((column) => column.key);
      const facetedSearchExportVO = Object.entries(exportVOColumns)
        .reduce((acc, [key, exportVOKey]) => ({
          ...acc,
          [exportVOKey]: !!keys.includes(key)
        }), {delimiter: ','});
      const csvFileName = 'export.csv';
      const payload = {
        csvFileName,
        facetedSearchExportVO,
        facetedSearchRequest: this.getFacetedSearchExportPayload(configuration)
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

  onExportTemplate = async (template, payload) => {
    const downloadExport = async (template) => {
      const request = new FacetedSearchExportTemplates(template.key);
      await request.send(payload);
      if (request.error) {
        return message.error(request.error, 5);
      }
      if (request.value instanceof Blob) {
        const error = await checkBlob(request.value, 'Error exporting search results');
        if (error) {
          return message.error(error.message, 5);
        }
        const fileName = `${template.key}-${displayDate(Date.now(), 'YYYY-MM-DD HH:mm:ss')}.xls`;
        FileSaver.saveAs(request.value, fileName);
      }
    };
    const saveExport = async (template, payload) => {
      const request = new FacetedSearchExportTemplatesSave(template.key);
      await request.send(payload);
      if (request.error) {
        return message.error(request.error, 5);
      }
      this.setState({savedExport: request.value});
    };
    const uploadToBucket = !!template.save_to;
    this.closeModal();
    const setStateAwaited = (state) =>
      new Promise((resolve) =>
        this.setState(state, () => resolve()));
    const hide = message.loading(uploadToBucket ? 'Exporting...' : 'Downloading...', 0);
    await setStateAwaited({pending: true});
    try {
      const payload = this.getFacetedSearchExportPayload();
      if (uploadToBucket) {
        await saveExport(template, payload);
      } else {
        await downloadExport(template, payload);
      }
    } catch (e) {
      message.error(e.message, 5);
    } finally {
      hide();
      this.setState({
        pending: false
      });
    }
  };

  closeExportResultModal = () => this.setState({savedExport: undefined});

  openExportResultModal = template => this.setState({savedExport: template});

  renderSavedExportContent = () => {
    const {savedExport} = this.state;
    const getStorageById = (id) => {
      const {
        dataStorages
      } = this.props;
      if (dataStorages.loaded) {
        return (dataStorages.value || []).find(d => Number(d.id) === Number(id));
      }
      return undefined;
    };
    if (!savedExport) {
      return null;
    }
    const storage = getStorageById(savedExport.storageId);
    const path = savedExport.storagePath.split('/');
    const file = path.pop();
    const folder = path.join('/');
    return (
      <div style={{display: 'flex', flexDirection: 'column'}}>
        <div>
          Export saved to:
          <Link
            style={{marginLeft: 5}}
            to={`storage/${savedExport.storageId}?path=${folder}`}
          >
            {`${storage?.name || savedExport.storageId}/${folder}`}
          </Link>.
        </div>
        <span>File name: <b>{file}</b></span>
      </div>
    );
  };

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
    const templates = parseExportTemplates(this.exportTemplates)
      .filter(template => {
        if (!template.permissions || this.isAdmin) {
          return true;
        }
        if (!template['template_path'] || !this.props.authenticatedUserInfo.loaded) {
          return false;
        }
        return checkTemplatePermissions(this.props.authenticatedUserInfo.value, template);
      });
    return (
      <Dropdown.Button
        overlay={(
          <ExportMenu
            onExport={this.onDefaultExport}
            onExportTemplate={this.onExportTemplate}
            onConfigure={this.onConfigure}
            templates={templates}
            storages={this.storages}
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
        <Modal
          title={null}
          visible={!!this.state.savedExport}
          onCancel={this.closeExportResultModal}
          footer={
            <Button
              type="primary"
              onClick={this.closeExportResultModal}>
              OK
            </Button>
          }
        >
          {this.renderSavedExportContent()}
        </Modal>
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
