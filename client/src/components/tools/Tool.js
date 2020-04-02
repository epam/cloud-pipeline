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
import {inject, observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import connect from '../../utils/connect';
import {
  Alert,
  Button,
  Card,
  Col,
  Dropdown,
  Icon,
  Input,
  Menu,
  message,
  Modal,
  Popover,
  Row,
  Table,
  Tooltip,
  Upload
} from 'antd';
import dockerRegistries from '../../models/tools/DockerRegistriesTree';
import LoadTool from '../../models/tools/LoadTool';
import ToolImage from '../../models/tools/ToolImage';
import ToolUpdate from '../../models/tools/ToolUpdate';
import ToolDelete from '../../models/tools/ToolDelete';
import LoadToolVersionSettings from '../../models/tools/LoadToolVersionSettings';
import UpdateToolVersionSettings from '../../models/tools/UpdateToolVersionSettings';
import preferences from '../../models/preferences/PreferencesLoad';
import LoadingView from '../special/LoadingView';
import Metadata from '../special/metadata/Metadata';
import Issues from '../special/issues/Issues';
import SessionStorageWrapper from '../special/SessionStorageWrapper';
import EditToolForm from './forms/EditToolForm';
import {
  getVersionRunningInfo,
  ScanStatuses
} from './utils';
import {
  CONTENT_PANEL_KEY,
  METADATA_PANEL_KEY,
  ISSUES_PANEL_KEY,
  SplitPanel
} from '../special/splitPanel/SplitPanel';
import styles from './Tools.css';
import Remarkable from 'remarkable';
import hljs from 'highlight.js';
import PermissionsForm from '../roleModel/PermissionsForm';
import roleModel from '../../utils/roleModel';
import localization from '../../utils/localization';
import 'highlight.js/styles/github.css';
import displayDate from '../../utils/displayDate';
import displaySize from '../../utils/displaySize';
import LoadToolAttributes from '../../models/tools/LoadToolAttributes';
import LoadToolScanPolicy from '../../models/tools/LoadToolScanPolicy';
import UpdateToolVersionWhiteList from '../../models/tools/UpdateToolVersionWhiteList';
import ToolScan from '../../models/tools/ToolScan';
import VersionScanResult from './elements/VersionScanResult';
import {submitsRun, modifyPayloadForAllowedInstanceTypes, run, runPipelineActions} from '../runs/actions';
import InstanceTypesManagementForm
  from '../main/navigation/instance-types-management/InstanceTypesManagementForm';
import deleteToolConfirmModal from './tool-deletion-warning';

const MarkdownRenderer = new Remarkable('full', {
  html: true,
  xhtmlOut: true,
  breaks: false,
  langPrefix: 'language-',
  linkify: true,
  linkTarget: '',
  typographer: true,
  highlight: function (str, lang) {
    lang = lang || 'bash';
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(lang, str).value;
      } catch (__) {}
    }
    try {
      return hljs.highlightAuto(str).value;
    } catch (__) {}
    return '';
  }
});

const INSTANCE_MANAGEMENT_PANEL_KEY = 'INSTANCE_MANAGEMENT';
const MAX_INLINE_VERSION_ALIASES = 7;
const DEFAULT_FILE_SIZE_KB = 50;

@localization.localizedComponent
@connect({
  dockerRegistries,
  preferences
})
@submitsRun
@runPipelineActions
@inject('awsRegions')
@inject(({allowedInstanceTypes, dockerRegistries, authenticatedUserInfo, preferences}, {params}) => {
  return {
    allowedInstanceTypes: allowedInstanceTypes.getAllowedTypes(params.id),
    allowedInstanceTypesCache: allowedInstanceTypes,
    toolId: params.id,
    tool: new LoadTool(params.id),
    versions: new LoadToolAttributes(params.id),
    section: params.section.toLowerCase(),
    preferences,
    docker: dockerRegistries,
    scanPolicy: new LoadToolScanPolicy(),
    authenticatedUserInfo,
    versionSettings: new LoadToolVersionSettings(params.id)
  };
})
@observer
export default class Tool extends localization.LocalizedReactComponent {

  state = {
    metadata: false,
    editDescriptionMode: false,
    description: null,
    editShortDescriptionMode: false,
    shortDescription: null,
    permissionsFormVisible: false,
    isShowUnscannedVersion: false,
    showIssuesPanel: false,
    instanceTypesManagementPanel: false
  };

  @observable defaultVersionSettings;

  @computed
  get awsRegions () {
    if (this.props.awsRegions.loaded) {
      return (this.props.awsRegions.value || []).map(r => r);
    }
    return [];
  }

  @computed
  get defaultCloudRegionId () {
    const [defaultRegion] = this.awsRegions.filter(r => r.default);
    if (defaultRegion) {
      return `${defaultRegion.id}`;
    }
    return null;
  }

  @computed
  get defaultVersionSettingsConfiguration () {
    if (this.defaultVersionSettings && this.defaultVersionSettings.loaded) {
      if ((this.defaultVersionSettings.value || []).length > 0 &&
          this.defaultVersionSettings.value[0].settings &&
          this.defaultVersionSettings.value[0].settings.length &&
          this.defaultVersionSettings.value[0].settings[0].configuration) {
        return this.defaultVersionSettings.value[0].settings[0].configuration;
      }
      return {
        parameters: {}
      };
    }
    return null;
  }

  @computed
  get dockerRegistry () {
    if (this.props.docker.loaded && this.props.tool.loaded) {
      return (this.props.docker.value.registries || [])
        .filter(r => r.id === this.props.tool.value.registryId)[0];
    }
    return null;
  }

  fetchVersions = async () => {
    await this.props.versions.fetch();
  };

  updateTool = async (values, configuration) => {
    const hide = message.loading('Updating settings...', 0);
    const request = new ToolUpdate();
    const tool = {
      image: this.props.tool.value.image,
      registry: this.props.tool.value.registry,
      registryId: this.props.tool.value.registryId,
      description: this.props.tool.value.description,
      shortDescription: this.props.tool.value.shortDescription,
      ...values
    };
    await request.send(tool);
    let updateToolVersionParametersRequest;
    if (this.defaultTag && configuration) {
      updateToolVersionParametersRequest = new UpdateToolVersionSettings(this.props.tool.value.id, this.defaultTag);
      await updateToolVersionParametersRequest.send([{
        configuration,
        name: 'default',
        default: true
      }]);
    }
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else if (updateToolVersionParametersRequest && updateToolVersionParametersRequest.error) {
      message.error(updateToolVersionParametersRequest.error, 5);
    } else {
      this.defaultVersionSettings && await this.defaultVersionSettings.fetch();
      await this.props.allowedInstanceTypes.fetch();
      await this.props.tool.fetch();
    }
  };

  deleteToolConfirm = () => {
    const deleteToolVersion = this.deleteToolVersion;
    deleteToolConfirmModal({tool: this.props.toolId}, this.props.router)
      .then((confirm) => {
        if (confirm) {
          return deleteToolVersion();
        }
        return Promise.resolve();
      });
  };

  deleteToolVersionConfirm = (version) => {
    const deleteToolVersion = this.deleteToolVersion;
    deleteToolConfirmModal({tool: this.props.toolId, version}, this.props.router)
      .then((confirm) => {
        if (confirm) {
          return deleteToolVersion(version);
        }
        return Promise.resolve();
      });
  };

  deleteToolVersion = async (version) => {
    if (version && this.isLastVersion) {
      message.warning('It is unable to delete the only version of the tool', 5);
      return;
    }
    const hide =
      message.loading(version ? `Removing version '${version}'...` : 'Removing tool...', 0);
    const request = new ToolDelete(this.props.toolId, null, version);
    await request.fetch();
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      if (!version) {
        this.navigateBack();
      } else {
        await this.fetchVersions();
      }
    }
  };

  toggleMetadataChange = () => {
    this.setState({
      metadata: !this.state.metadata
    });
  };

  toggleEditDescriptionMode = (mode) => {
    this.setState({
      editDescriptionMode: mode,
      description: mode ? this.props.tool.value.description : null
    });
  };

  toggleEditShortDescriptionMode = (mode) => {
    this.setState({
      editShortDescriptionMode: mode,
      shortDescription: mode ? this.props.tool.value.shortDescription : null
    });
  };

  onSave = (isShortDescription) => async () => {
    const hide = message.loading('Updating description...', 0);
    const request = new ToolUpdate();
    const tool = {
      image: this.props.tool.value.image,
      registry: this.props.tool.value.registry,
      registryId: this.props.tool.value.registryId,
      disk: this.props.tool.value.disk,
      labels:
        this.props.tool.value.labels ? (this.props.tool.value.labels || []).map(l => l) : undefined,
      endpoints:
        this.props.tool.value.endpoints
          ? (this.props.tool.value.endpoints || []).map(l => l)
          : undefined,
      instanceType: this.props.tool.value.instanceType,
      defaultCommand: this.props.tool.value.defaultCommand,
      description: isShortDescription ? this.props.tool.value.description : this.state.description,
      shortDescription:
        isShortDescription ? this.state.shortDescription : this.props.tool.value.shortDescription
    };
    await request.send(tool);
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      await this.props.tool.fetch();
      if (isShortDescription) {
        this.toggleEditShortDescriptionMode(false);
      } else {
        this.toggleEditDescriptionMode(false);
      }
    }
  };

  getWarningForLatestVersion = () => {
    return this.getVersionRunningInformation(this.defaultTag).tooltip;
  };

  renderToolImageControl = () => {
    if (!this.props.tool.loaded) {
      return null;
    }
    let image;
    if (this.props.tool.value.iconId) {
      image = (
        <img
          className={roleModel.writeAllowed(this.props.tool.value) ? styles.toolImage : styles.toolImageReadOnly}
          src={ToolImage.url(this.props.tool.value.id, this.props.tool.value.iconId)} />
      );
    } else {
      image = (
        <Row type="flex" align="middle" justify="center" className={styles.noImageContainer}>
          <Icon
            className={styles.noImage}
            type="camera-o" />
        </Row>
      );
    }

    const doUpload = (file) => {
      const maxFileSize = this.props.preferences.maximumFileSize || DEFAULT_FILE_SIZE_KB;
      if (file.type.toLowerCase().indexOf('png') === -1) {
        message.error('Only PNG image files are supported', 5);
        return false;
      } else if (file.size > maxFileSize * 1024) {
        message.error(`Maximum file size is ${maxFileSize} kB`, 5);
        return false;
      }
      const hide = message.loading('Uploading new image...', 0);
      const refresh = () => {
        this.props.tool.fetch();
      };
      const formData = new FormData();
      formData.append('file', file);
      const request = new XMLHttpRequest();
      request.withCredentials = true;
      request.upload.onprogress = function (event) {};
      request.upload.onload = function () {};
      request.upload.onerror = function () {};
      request.onreadystatechange = function () {
        if (request.readyState !== 4) return;

        if (request.status !== 200) {
        } else {
          try {
            const json = JSON.parse(request.response);
            if (json.status === 'ERROR') {
              message.error(json.message, 5);
            }
          } catch (__) {}
          refresh();
        }
        hide();
      };
      request.open('POST', ToolImage.url(this.props.tool.value.id));
      request.send(formData);
      return false;
    };
    return (
      <div className={styles.toolImageContainer} style={{marginRight: 10}}>
        {image}
        {
          roleModel.writeAllowed(this.props.tool.value) &&
          <Upload
            action={ToolImage.url(this.props.tool.value.id)}
            multiple={false}
            showUploadList={false}
            beforeUpload={doUpload}>
            <Row type="flex" align="middle" justify="center" className={styles.uploadToolImage}>
              <Icon
                type="upload"
                style={
                  this.props.tool.value.iconId
                    ? {fontSize: 'xx-large', color: 'white', textShadow: '1px 1px black'}
                    : {fontSize: 'xx-large', color: '#888'}
                } />
            </Row>
          </Upload>
        }
      </div>
    );
  };

  renderDescription = () => {
    const renderShortDescription = () => {
      if (this.state.editShortDescriptionMode) {
        return (
          <Input
            id="short-description-input"
            value={this.state.shortDescription}
            onChange={(e) => this.setState({shortDescription: e.target.value})}
            onPressEnter={this.onSave(true)}
            onKeyDown={(e) => {
              if (e.key && e.key === 'Escape') {
                this.toggleEditShortDescriptionMode(false);
              }
            }}
            style={{
              width: '100%',
              height: '100%',
              resize: 'none',
              marginLeft: -3,
              paddingLeft: 2
            }} />
        );
      } else {
        const shortDescription = this.props.tool.value.shortDescription;
        if (shortDescription && shortDescription.trim().length) {
          return <span
            id="short-description-text"
            className={styles.description}>
            {this.props.tool.value.shortDescription} </span>;
        } else {
          return <span
            id="short-description-text"
            className={styles.noDescription}>No description</span>;
        }
      }
    };
    const renderDescription = () => {
      if (this.state.editDescriptionMode) {
        return (
          <Input
            id="description-input"
            value={this.state.description}
            onChange={(e) => this.setState({description: e.target.value})}
            type="textarea"
            onKeyDown={(e) => {
              if (e.key && e.key === 'Escape') {
                this.toggleEditDescriptionMode(false);
              }
            }}
            style={{width: '100%', height: '100%', resize: 'none'}} />
        );
      } else {
        const description = this.props.tool.value.description;
        if (description && description.trim().length) {
          return (
            <div
              id="description-text-container"
              className={styles.mdPreview}
              dangerouslySetInnerHTML={{__html: MarkdownRenderer.render(description)}} />
          );
        } else {
          return <span id="description-text" className={styles.noDescription}>No description</span>;
        }
      }
    };

    const renderActions = (isShortDescription) => {
      if (!roleModel.writeAllowed(this.props.tool.value)) {
        return undefined;
      }
      const buttons = [];
      const editModeName = isShortDescription ? 'editShortDescriptionMode' : 'editDescriptionMode';
      const method =
        isShortDescription ? this.toggleEditShortDescriptionMode : this.toggleEditDescriptionMode;
      if (this.state[editModeName]) {
        buttons.push(
          <Button
            id={`${isShortDescription ? 'short-description' : 'description'}-edit-cancel-button`}
            key="cancel"
            size="small"
            onClick={() => method(false)}>
            CANCEL
          </Button>
        );
        buttons.push(
          <Button
            id={`${isShortDescription ? 'short-description' : 'description'}-edit-save-button`}
            key="save"
            size="small"
            type="primary"
            onClick={this.onSave(isShortDescription)}>
            SAVE
          </Button>
        );
      } else {
        buttons.push(
          <Button
            id={`${isShortDescription ? 'short-description' : 'description'}-edit-button`}
            key="edit"
            size="small"
            onClick={() => method(true)}>
            EDIT
          </Button>
        );
      }
      return (
        <Row className={styles.descriptionActions}>
          {buttons}
        </Row>
      );
    };

    let shortDescriptionAndPullCommand;
    const [registry] = !this.props.docker.loaded
      ? [null]
      : (this.props.docker.value.registries || []).filter(r => r.id === this.props.tool.value.registryId);
    if (registry && roleModel.readAllowed(this.props.tool.value) && registry.pipelineAuth) {
      const renderPullCommand = () => {
        if (!registry) {
          return <Icon type="loading" />;
        }
        return `docker pull ${registry.externalUrl || registry.path}/${this.props.tool.value.image}`;
      };
      shortDescriptionAndPullCommand = (
        <Row type="flex">
          <Col span={16}>
            <Row
              type="flex"
              align="middle"
              justify="space-between"
              className={styles.descriptionTableHeader}>
              <span className={styles.descriptionTitle}>Short description</span>
              {renderActions(true)}
            </Row>
            <Row
              type="flex"
              style={{marginBottom: 0}}
              className={styles.descriptionTableBody}>
              {renderShortDescription()}
            </Row>
          </Col>
          <Col span={8} style={{paddingLeft: 10}}>
            <Row
              type="flex"
              align="middle"
              justify="space-between"
              className={styles.descriptionTableHeader}>
              <span className={styles.descriptionTitle}>Default pull command</span>
            </Row>
            <Row
              type="flex"
              style={{marginBottom: 0}}
              className={styles.descriptionTableBody}>
              <span className={styles.description}>
                <code>{renderPullCommand()}</code>
              </span>
            </Row>
          </Col>
        </Row>
      );
    } else {
      shortDescriptionAndPullCommand = [
        <Row
          key="header"
          type="flex"
          align="middle"
          justify="space-between"
          className={styles.descriptionTableHeader}>
          <span className={styles.descriptionTitle}>Short description</span>
          {renderActions(true)}
        </Row>,
        <Row
          key="body"
          type="flex"
          style={{marginBottom: 0}}
          className={styles.descriptionTableBody}>
          {renderShortDescription()}
        </Row>
      ];
    }

    const warningForLatestVersion = this.getWarningForLatestVersion();

    return (
      <div className={styles.descriptionContainer}>
        {
          warningForLatestVersion &&
          <Alert
            style={{marginBottom: 10, marginTop: 5}}
            type="warning"
            message={warningForLatestVersion} />
        }
        <Row type="flex" style={{marginBottom: 10}}>
          {this.renderToolImageControl()}
          <div style={{flex: 1}}>
            {shortDescriptionAndPullCommand}
          </div>
        </Row>
        <Row
          type="flex"
          align="middle"
          justify="space-between"
          className={styles.descriptionTableHeader}>
          <span className={styles.descriptionTitle}>Full description</span>
          {renderActions(false)}
        </Row>
        <Row
          type="flex"
          className={styles.descriptionTableBody}
          style={{flex: 1, overflowY: 'auto'}}>
          {renderDescription()}
        </Row>
      </div>
    );
  };

  onChangeViewUnscannedVersion = () => {
    this.setState({isShowUnscannedVersion: !this.state.isShowUnscannedVersion});
  };

  toolScan = (e, version) => {
    e.preventDefault();
    e.stopPropagation();
    const request = new ToolScan(this.props.toolId, version);
    return new Promise(async (resolve) => {
      await request.send({});
      if (request.error) {
        message.error(request.error);
      } else {
        await this.fetchVersions();
      }
      resolve();
    });
  };

  isAdmin = () => {
    if (!this.props.authenticatedUserInfo.loaded) {
      return false;
    }
    return this.props.authenticatedUserInfo.value.admin;
  };

  getVersionScanningInfo = (item) => {
    if (this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry) && item.status) {
      let scanningInfo;
      switch (item.status.toUpperCase()) {
        case ScanStatuses.completed:
          if (item.successScanDate) {
            scanningInfo = <span>Successfully scanned at {displayDate(item.successScanDate)}</span>
          } else {
            scanningInfo = <span>Successfully scanned</span>;
          }
          break;
        case ScanStatuses.pending:
          if (item.successScanDate) {
            scanningInfo = <span>Scanning in progress. Last successful scan: {displayDate(item.successScanDate)}</span>;
          } else {
            scanningInfo = <span>Scanning in progress</span>;
          }
          break;
        case ScanStatuses.failed:
          if (item.successScanDate) {
            scanningInfo = <span>
            Scanning <span
              className={styles.scanningError}>failed</span>{item.scanDate ? ` at ${displayDate(item.scanDate)}` : ''}. Last successful scan: {displayDate(item.successScanDate)}</span>;
          } else {
            scanningInfo = <span>Scanning <span
              className={styles.scanningError}>failed</span>{item.scanDate ? ` at ${displayDate(item.scanDate)}` : ''}</span>;
          }
          break;
        case ScanStatuses.notScanned:
          scanningInfo = <span>Version was not scanned</span>;
          break;
      }
      return scanningInfo;
    }
    return null;
  };

  @computed
  get isLastVersion () {
    if (this.props.versions.loaded) {
      return this.props.versions.value.versions.length === 1;
    }
    return false;
  }

  @computed
  get toolVersionScanResults () {
    const data = [];
    if (this.props.versions.loaded &&
      this.props.versions.value &&
      this.props.versions.value.versions) {
      let keyIndex = 0;
      const versionsByDigest = {};
      const versions = this.props.versions.value.versions;

      versions.forEach(version => {
        if (version.attributes && version.attributes.digest) {
          if (!versionsByDigest[version.attributes.digest]) {
            versionsByDigest[version.attributes.digest] = [];
          }
          versionsByDigest[version.attributes.digest].push(version.version);
        }
      });

      versions.forEach(currentVersion => {
        const scanResult = currentVersion.scanResult || {};
        const versionAttributes = currentVersion.attributes;

        const vulnerabilities = scanResult.vulnerabilities || [];
        const countCriticalVulnerabilities =
          vulnerabilities.filter(vulnerabilitie => vulnerabilitie.severity === 'Critical').length;
        const countHighVulnerabilities =
          vulnerabilities.filter(vulnerabilitie => vulnerabilitie.severity === 'High').length;
        const countMediumVulnerabilities =
          vulnerabilities.filter(vulnerabilitie => vulnerabilitie.severity === 'Medium').length;
        const countLowVulnerabilities =
          vulnerabilities.filter(vulnerabilitie => vulnerabilitie.severity === 'Low').length;
        const countNegligibleVulnerabilities =
          vulnerabilities.filter(vulnerabilitie => vulnerabilitie.severity === 'Negligible').length;

        const digestAliases = versionAttributes && versionAttributes.digest
          ? versionsByDigest[versionAttributes.digest]
            .filter(version => version !== currentVersion.version)
          : [];

        data.push({
          key: keyIndex,
          name: currentVersion.version,
          digest: versionAttributes && versionAttributes.digest ? versionAttributes.digest : '',
          digestAliases,
          fromWhiteList: scanResult.fromWhiteList,
          size: versionAttributes && versionAttributes.size ? versionAttributes.size : '',
          modificationDate: versionAttributes && versionAttributes.modificationDate
            ? versionAttributes.modificationDate : '',
          scanDate: scanResult.scanDate || '',
          status: scanResult.status,
          successScanDate: scanResult.successScanDate || '',
          allowedToExecute: scanResult.allowedToExecute,
          toolOSVersion: scanResult.toolOSVersion,
          vulnerabilitiesStatistics: scanResult.status === ScanStatuses.notScanned ? null : {
            critical: countCriticalVulnerabilities,
            high: countHighVulnerabilities,
            medium: countMediumVulnerabilities,
            low: countLowVulnerabilities,
            negligible: countNegligibleVulnerabilities
          }
        });
        keyIndex += 1;
      });
    }

    return data;
  }

  @computed
  get hasPendingScanning () {
    return this.toolVersionScanResults.filter(i => i.status === ScanStatuses.pending).length > 0;
  }

  renderDigestAliases = (aliases) => {
    const renderAlias = alias => {
      return (
        <span
          key={alias}
          className={styles.versionAliasItem}>
          {alias}
        </span>
      );
    };

    if (aliases.length > MAX_INLINE_VERSION_ALIASES) {
      return (
        <Row>
          {aliases.slice(0, MAX_INLINE_VERSION_ALIASES - 1).map(renderAlias)}
          <div style={{
            margin: 5,
            display: 'inline-block'
          }}>
            <Popover content={
              <div style={{minWidth: 200, display: 'flex'}}>
                {aliases.map(renderAlias)}
              </div>
            }>
              <a>+{aliases.length - MAX_INLINE_VERSION_ALIASES + 1} more</a>
            </Popover>
          </div>
        </Row>
      );
    } else {
      return <Row>{aliases.map(renderAlias)}</Row>;
    }
  };

  toggleVersionWhiteList = (e, version) => {
    e.preventDefault();
    e.stopPropagation();

    const request = new UpdateToolVersionWhiteList(
      this.props.toolId,
      version.name,
      !version.fromWhiteList
    );
    return new Promise(async (resolve) => {
      await request.send({});
      if (request.error) {
        message.error(request.error, 5);
      } else {
        await this.fetchVersions();
      }
      resolve();
    });
  };

  renderVersions = () => {
    const emptyField = <span style={{color: 'rgba(0, 0, 0, 0.43)'}}>No data</span>;
    const columns = [{
      dataIndex: 'name',
      key: 'name',
      className: styles.nameColumn,
      render: (name, item) => {
        return (
          <table className={styles.versionNameTable}>
            <tbody>
              <tr>
                <th className={styles.versionName}>{name}</th>
                <td className={styles.versionScanningInfoGraph}>
                  {
                    this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry) &&
                    <VersionScanResult
                      result={item.vulnerabilitiesStatistics}
                      whiteListed={item.fromWhiteList}
                      placement="right" />
                  }
                </td>
                <td className={styles.versionScanningInfoEmptyCell}>{'\u00A0'}</td>
              </tr>
              <tr>
                <td colSpan={3} className={styles.versionScanningInfo}>
                  {this.getVersionScanningInfo(item)}
                </td>
              </tr>
            </tbody>
          </table>
        );
      }
    }, {
      dataIndex: 'toolOSVersion',
      key: 'tool os version',
      title: 'OS',
      className: styles.osColumn,
      render: (toolOSVersion) => {
        if (toolOSVersion) {
          const {distribution, version} = toolOSVersion;
          if (distribution) {
            return (
              <div>
                <span>{distribution}</span>
                {version && (<span style={{marginLeft: 5}}>{version}</span>)}
              </div>
            );
          }
        }
        return null;
      }
    }, {
      dataIndex: 'digest',
      key: 'digest',
      title: 'Digest',
      className: styles.nameColumn,
      render: (digest, item) => {
        if (item.digestAliases.length) {
          return (
            <Row>
              <Row style={{marginTop: 2}}>{digest}</Row>
              <Row style={{marginTop: 5}}>{this.renderDigestAliases(item.digestAliases)}</Row>
            </Row>
          );
        }
        return digest || emptyField;
      }
    }, {
      dataIndex: 'size',
      key: 'size',
      title: 'Size',
      className: styles.nameColumn,
      render: (size) => size ? displaySize(size) : emptyField
    }, {
      dataIndex: 'modificationDate',
      key: 'modificationDate',
      title: 'Modified date',
      className: styles.nameColumn,
      render: (modificationDate) => modificationDate ? displayDate(modificationDate) : emptyField
    }, {
      key: 'actions',
      className: styles.actionsColumn,
      render: (version) => {
        return (
          <Row type="flex" justify="end" className={styles.toolVersionActions}>
            {
              this.isAdmin() &&
              this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry) &&
              (
                <Button
                  size="small"
                  onClick={(e) => this.toggleVersionWhiteList(e, version)}>
                  {version.fromWhiteList ? 'Remove from ' : 'Add to '}white list
                </Button>
              )
            }
            {
              this.isAdmin() &&
              this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry) &&
              (
                <Button
                  type="primary"
                  loading={version.status === ScanStatuses.pending}
                  size="small"
                  onClick={(e) => this.toolScan(e, version.name)}>
                  {version.status === ScanStatuses.pending ? 'SCANNING' : 'SCAN'}
                </Button>
              )
            }
            {
              roleModel.executeAllowed(this.props.tool.value) &&
              (
                this.renderRunButton(
                  version.name
                )
              )
            }
            {
              roleModel.writeAllowed(this.props.tool.value) &&
              (
                <Button
                  size="small"
                  type="danger"
                  disabled={this.isLastVersion}
                  onClick={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    this.deleteToolVersionConfirm(version.name);
                  }}>
                  <Icon type="delete" />
                </Button>
              )
            }
          </Row>
        );
      }
    }];

    let data = this.toolVersionScanResults;
    const isContainsUnscannedVersion = this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry) && data.filter(item => item.status === ScanStatuses.notScanned).length > 0;
    if (this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry) && !this.state.isShowUnscannedVersion) {
      data = data.filter(d => d.status !== ScanStatuses.notScanned);
    }
    return (
      <Row style={{width: '100%'}}>
        {
          this.props.versions.error &&
          <Alert
            type="error"
            message={this.props.versions.error}
            style={{margin: '5px 0 10px 0'}} />
        }
        <Table
          className={styles.table}
          loading={this.props.versions.pending}
          showHeader
          rowClassName={(item) => item.fromWhiteList && this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry)
            ? `${styles.versionTableRow} ${styles.whiteListedVersionRow}`
            : styles.versionTableRow
          }
          columns={columns}
          dataSource={data}
          pagination={{pageSize: 20}}
          onRowClick={(version) => {
            if (this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry) &&
              version.status !== ScanStatuses.notScanned) {
              this.props.router.push(`/tool/${this.props.toolId}/info/${version.name}/scaninfo`);
            } else {
              this.props.router.push(`/tool/${this.props.toolId}/info/${version.name}/settings`);
            }
          }}
          size="small" />
        {
          isContainsUnscannedVersion &&
          <Row className={styles.viewUnscannedVersion}>
            <Button
              style={{marginTop: 10}}
              key="view_unscanned_version"
              size="large"
              type="primary"
              ghost
              disabled={!isContainsUnscannedVersion}
              onClick={this.onChangeViewUnscannedVersion}>
              {this.state.isShowUnscannedVersion ? 'HIDE UNSCANNED VERSIONS' : 'VIEW UNSCANNED VERSIONS'}
            </Button>
          </Row>
        }
      </Row>
    );
  };

  toolSettingsForm;

  onToolSettingsFormInitialized = (form) => {
    this.toolSettingsForm = form;
  };

  renderToolSettings = () => {
    if (!this.props.tool.loaded || !this.props.preferences.loaded) {
      return undefined;
    }
    const [, image] = this.props.tool.value.image.split('/');
    const warningForLatestVersion = this.getWarningForLatestVersion();
    return (
      <div>
          { warningForLatestVersion &&
          <Alert
            style={{marginBottom: 10, marginTop: 5}}
            type="warning"
            message={warningForLatestVersion} />
          }
        {
          !this.defaultTag && this.props.versions.loaded &&
          <Alert
            style={{marginBottom: 10, marginTop: 5}}
            type="info"
            message={
              <div>
                <Row>
                  <b>{image}</b> has no default <b><i>latest</i></b> version.
                </Row>
                <Row>
                  To edit instance configuration, default parameters and cluster configuration, please create <b><i>latest</i></b> version or modify specific version settings.
                </Row>
              </div>
            } />
        }
        {
          this.defaultTag && this.defaultTag !== 'latest' &&
          <Alert
            style={{marginBottom: 10, marginTop: 5}}
            type="info"
            message={
              <div>
                <Row>
                  <b>{image}</b> has no (<b><i>latest</i></b>) version.
                </Row>
                <Row>
                  Execution environment settings will be applied to version <b>{this.defaultTag}</b>.
                </Row>
              </div>
            } />
        }
        <EditToolForm
          onInitialized={this.onToolSettingsFormInitialized}
          readOnly={!roleModel.writeAllowed(this.props.tool.value)}
          configuration={this.defaultVersionSettingsConfiguration}
          tool={this.props.tool.value}
          toolId={this.props.toolId}
          defaultPriceTypeIsSpot={this.props.preferences.useSpot}
          executionEnvironmentDisabled={!this.defaultTag}
          onSubmit={this.updateTool} />
      </div>
    );
  };

  renderToolContent = () => {
    switch (this.props.section) {
      case 'description': return this.renderDescription();
      case 'versions': return this.renderVersions();
      case 'settings': return this.renderToolSettings();
    }
    return undefined;
  };

  toggleShowIssuesChange = () => {
    this.setState({
      showIssuesPanel: !this.state.showIssuesPanel
    });
  };

  closeIssuesPanel = () => {
    this.setState({
      showIssuesPanel: false
    });
  };

  toggleInstanceTypesManagementPanelChange = () => {
    this.setState({
      instanceTypesManagementPanel: !this.state.instanceTypesManagementPanel
    });
  };

  closeInstanceTypesManagementPanel = () => {
    this.setState({
      instanceTypesManagementPanel: false
    });
  };

  renderContent = () => {
    const onPanelClose = (key) => {
      switch (key) {
        case METADATA_PANEL_KEY:
          this.setState({metadata: false});
          break;
        case ISSUES_PANEL_KEY:
          this.closeIssuesPanel();
          break;
        case INSTANCE_MANAGEMENT_PANEL_KEY:
          this.closeInstanceTypesManagementPanel();
          break;
      }
    };
    return (
      <SplitPanel
        style={{flex: 1, overflow: 'auto'}}
        onPanelClose={onPanelClose}
        contentInfo={[{
          key: CONTENT_PANEL_KEY,
          size: {
            priority: 0,
            percentMinimum: 33,
            percentDefault: 75
          }
        }, {
          key: ISSUES_PANEL_KEY,
          title: `${this.localizedString('Issue')}s`,
          closable: true,
          containerStyle: {
            display: 'flex',
            flexDirection: 'column'
          },
          size: {
            keepPreviousSize: true,
            priority: 1,
            percentDefault: 25,
            pxMinimum: 200
          }
        }, {
          key: METADATA_PANEL_KEY,
          title: 'Attributes',
          closable: true,
          containerStyle: {
            display: 'flex',
            flexDirection: 'column'
          },
          size: {
            keepPreviousSize: true,
            priority: 2,
            percentDefault: 25,
            pxMinimum: 200
          }
        }, {
          key: INSTANCE_MANAGEMENT_PANEL_KEY,
          title: 'Instance management',
          closable: true,
          containerStyle: {
            display: 'flex',
            flexDirection: 'column'
          },
          size: {
            keepPreviousSize: true,
            priority: 2,
            percentDefault: 25,
            pxMinimum: 200
          }
        }]}>
        <div
          key={CONTENT_PANEL_KEY}
          style={{
            height: '100%',
            display: 'flex',
            flexDirection: 'column'
          }}>
          {this.renderToolContent()}
        </div>
        {
          this.state.showIssuesPanel &&
          <Issues
            key={ISSUES_PANEL_KEY}
            canNavigateBack={false}
            onCloseIssuePanel={this.closeIssuesPanel}
            entityId={this.props.toolId}
            entityClass="TOOL"
            entity={this.props.tool.value} />
        }
        {
          this.state.metadata &&
          <Metadata
            key={METADATA_PANEL_KEY}
            readOnly={!roleModel.isOwner(this.props.tool.value)}
            entityId={this.props.toolId}
            entityClass="TOOL" />
        }
        {
          this.state.instanceTypesManagementPanel && (roleModel.isOwner(this.props.tool.value) || this.isAdmin()) &&
          <InstanceTypesManagementForm
            key={INSTANCE_MANAGEMENT_PANEL_KEY}
            level="TOOL"
            resourceId={this.props.toolId} />
        }
      </SplitPanel>
    );
  };

  renderMenu = () => {
    const onChangeSection = ({key}) => {
      this.props.router.push(`/tool/${this.props.toolId}/${key}`);
    };
    return (
      <Row type="flex" justify="center">
        <Menu
          className={styles.toolMenu}
          onClick={onChangeSection}
          mode="horizontal"
          selectedKeys={[this.props.section]}>
          <Menu.Item key="description">
            DESCRIPTION
          </Menu.Item>
          <Menu.Item key="versions">
            VERSIONS
          </Menu.Item>
          <Menu.Item key="settings">
            SETTINGS
          </Menu.Item>
        </Menu>
      </Row>
    );
  };

  runToolDefault = async (version, payload) => {
    const title = version
      ? `Are you sure you want to launch tool (version ${version}) with default settings?`
      : 'Are you sure you want to launch tool with default settings?';
    const info = this.getVersionRunningInformation(version || this.defaultTag);
    if (await run(this)(payload, true, title, info.launchTooltip, this.props.allowedInstanceTypes)) {
      SessionStorageWrapper.navigateToActiveRuns(this.props.router);
    }
  };

  runTool = (version) => {
    const info = this.getVersionRunningInformation(version || this.defaultTag);
    const navigate = () => {
      if (version) {
        this.props.router.push(`/launch/tool/${this.props.toolId}?version=${version}`);
      } else if (this.defaultTag) {
        this.props.router.push(`/launch/tool/${this.props.toolId}?version=${this.defaultTag}`);
      } else {
        this.props.router.push(`/launch/tool/${this.props.toolId}`);
      }
    };
    if (info.launchTooltip) {
      Modal.confirm({
        title: info.launchTooltip,
        style: {
          wordWrap: 'break-word'
        },
        onOk () {
          navigate();
        }
      });
    } else {
      navigate();
    }
  };

  openPermissionsForm = () => {
    this.setState({permissionsFormVisible: true});
  };

  closePermissionsForm = () => {
    this.setState({permissionsFormVisible: false});
  };

  navigateBack = () => {
    this.props.docker.fetch();
    this.props.router.push(`/tools/${this.props.tool.value.registryId}/${this.props.tool.value.toolGroupId}`);
  };

  @computed
  get versionsScanResObject () {
    const versions = {};
    this.props.versions.value.versions.forEach(version => {
      versions[version.version] = version.scanResult;
    });
    return versions;
  };

  @computed
  get defaultTag () {
    if (this.props.versions.loaded &&
      this.props.versions.value && this.props.versions.value.versions) {
      const versions = this.versionsScanResObject;
      if (versions['latest']) {
        return 'latest';
      } else if (Object.keys(versions).length === 1) {
        return Object.keys(versions)[0];
      }
    }
    return null;
  }

  @computed
  get anyTag () {
    if (this.props.versions.loaded &&
      this.props.versions.value &&
      this.props.versions.value.versions) {
      const versions = this.versionsScanResObject;
      if (Object.keys(versions).length > 0) {
        return Object.keys(versions)[0];
      }
    }
    return null;
  }

  getVersionRunningInformation = (version) => {
    return getVersionRunningInfo(
      version,
      this.props.versions.loaded ? this.versionsScanResObject : null,
      this.props.scanPolicy.loaded ? this.props.scanPolicy.value : null,
      this.isAdmin(),
      this.props.preferences,
      this.dockerRegistry
    );
  };

  renderRunButton = (version) => {
    const parameterIsNotEmpty = (parameter, additionalCriteria) =>
    parameter !== null &&
    parameter !== undefined &&
    `${parameter}`.trim().length > 0 &&
    (!additionalCriteria || additionalCriteria(parameter));
    const [versionSettings] = (this.props.versionSettings.value || []).filter(v => v.version === (version || this.defaultTag));
    const [defaultVersionSettings] = (this.props.versionSettings.value || []).filter(v => v.version === this.defaultTag);
    const versionSettingValue = (settingName) => {
      if (versionSettings &&
        versionSettings.settings &&
        versionSettings.settings.length &&
        versionSettings.settings[0].configuration) {
        return versionSettings.settings[0].configuration[settingName];
      }
      if (defaultVersionSettings &&
        defaultVersionSettings.settings &&
        defaultVersionSettings.settings.length &&
        defaultVersionSettings.settings[0].configuration) {
        return defaultVersionSettings.settings[0].configuration[settingName];
      }
      return null;
    };
    const defaultCommandIsNotEmpty =
      parameterIsNotEmpty(versionSettingValue('cmd_template')) ||
      parameterIsNotEmpty(this.props.tool.value.defaultCommand) ||
      parameterIsNotEmpty(this.props.preferences.getPreferenceValue('launch.cmd.template'));
    const instanceTypeIsNotEmpty =
      parameterIsNotEmpty(versionSettingValue('instance_size')) ||
      parameterIsNotEmpty(this.props.tool.value.instanceType) ||
      parameterIsNotEmpty(this.props.preferences.getPreferenceValue('cluster.instance.type'));
    const diskIsNotEmpty =
      parameterIsNotEmpty(versionSettingValue('instance_disk'), p => +p > 0) ||
      parameterIsNotEmpty(this.props.tool.value.disk, p => +p > 0) ||
      parameterIsNotEmpty(this.props.preferences.getPreferenceValue('cluster.instance.hdd'), p => +p > 0);
    if ((!this.defaultTag && !version) ||
      !defaultCommandIsNotEmpty ||
      !instanceTypeIsNotEmpty ||
      !diskIsNotEmpty) {
      version = version || this.defaultTag || this.anyTag;
      const {allowedToExecute, tooltip, notLoaded} = this.getVersionRunningInformation(version);
      return (
        <Tooltip title={tooltip} trigger="hover">
          <Button
            key="run-custom-button"
            size="small"
            type="primary"
            disabled={!allowedToExecute && !this.isAdmin()}
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              this.runTool(version || this.anyTag);
            }}>
            {
              tooltip && !notLoaded
                ? <Icon type="exclamation-circle" style={{marginRight: 5}} />
                : undefined
            }
            Run
          </Button>
        </Tooltip>
      );
    }

    const chooseDefaultValue = (versionSettingsValue, toolValue, settingsValue, additionalCriteria) => {
      if (parameterIsNotEmpty(versionSettingsValue, additionalCriteria)) {
        return versionSettingsValue;
      }
      if (parameterIsNotEmpty(toolValue, additionalCriteria)) {
        return toolValue;
      }
      return settingsValue;
    };

    const [registry] = !this.props.docker.loaded
      ? [null]
      : (this.props.docker.value.registries || []).filter(r => r.id === this.props.tool.value.registryId);

    version = version || this.defaultTag;
    const prepareParameters = (parameters) => {
      const result = {};
      for (let key in parameters) {
        if (parameters.hasOwnProperty(key)) {
          result[key] = {
            type: parameters[key].type,
            value: parameters[key].value,
            required: parameters[key].required,
            defaultValue: parameters[key].defaultValue
          };
        }
      }
      return result;
    };
    const defaultPayload = modifyPayloadForAllowedInstanceTypes({
      instanceType:
        chooseDefaultValue(
          versionSettingValue('instance_size'),
          this.props.tool.value.instanceType,
          this.props.preferences.getPreferenceValue('cluster.instance.type')
        ),
      hddSize: +chooseDefaultValue(
        versionSettingValue('instance_disk'),
        this.props.tool.value.disk,
        this.props.preferences.getPreferenceValue('cluster.instance.hdd'),
        p => +p > 0
      ),
      timeout: +(this.props.tool.value.timeout || 0),
      cmdTemplate: chooseDefaultValue(
        versionSettingValue('cmd_template'),
        this.props.tool.value.defaultCommand,
        this.props.preferences.getPreferenceValue('launch.cmd.template')
      ),
      dockerImage: registry
        ? `${registry.path}/${this.props.tool.value.image}${version ? `:${version}` : ''}`
        : `${this.props.tool.value.image}${version ? `:${version}` : ''}`,
      params: parameterIsNotEmpty(versionSettingValue('parameters'))
        ? prepareParameters(versionSettingValue('parameters'))
        : {},
      isSpot: parameterIsNotEmpty(versionSettingValue('is_spot'))
        ? versionSettingValue('is_spot')
        : this.props.preferences.useSpot,
      nodeCount: parameterIsNotEmpty(versionSettingValue('node_count'))
        ? +versionSettingValue('node_count')
        : undefined,
      cloudRegionId: this.defaultCloudRegionId
    }, this.props.allowedInstanceTypes);
    const {allowedToExecute, tooltip, notLoaded} = this.getVersionRunningInformation(version);
    if (!allowedToExecute) {
      return (
        <Tooltip
          placement="left"
          title={tooltip}
          trigger="hover">
          <Button
            id={`run-${version}-button`}
            type="primary"
            size="small"
            disabled={!allowedToExecute && !this.isAdmin()}
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              return this.runToolDefault(version, defaultPayload);
            }}>
            {
              tooltip && !notLoaded
                ? <Icon type="exclamation-circle" style={{marginRight: 5}} />
                : undefined
            }
            Run
          </Button>
        </Tooltip>
      );
    } else {
      const runDefaultKey = 'default';
      const runCustomKey = 'custom';
      const onSelect = ({key}) => {
        switch (key) {
          case runDefaultKey: this.runToolDefault(version, defaultPayload); break;
          case runCustomKey: this.runTool(version); break;
        }
      };
      const runMenu = (
        <Menu selectedKeys={[]} onClick={onSelect}>
          <Menu.Item id="run-default-button" key={runDefaultKey}>
            {
              tooltip && !notLoaded
                ? (
                <Tooltip
                  placement="left"
                  title={tooltip}
                  trigger="hover">
                  Default settings
                </Tooltip>
              )
                : 'Default settings'
            }
          </Menu.Item>
          <Menu.Item id="run-custom-button" key={runCustomKey}>
            {
              tooltip && !notLoaded
                ? (
                <Tooltip
                  placement="left"
                  title={tooltip}
                  trigger="hover">
                  Custom settings
                </Tooltip>
              )
                : 'Custom settings'
            }
          </Menu.Item>
        </Menu>
      );
      return (
        <span style={{position: 'relative', display: 'inline-block'}}>
          <Button.Group>
            <Tooltip
              placement="left"
              title={tooltip}
              trigger="hover">
              <Button
                id={`run-${version}-button`}
                type="primary"
                size="small"
                disabled={!allowedToExecute && !this.isAdmin()}
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  return this.runToolDefault(version, defaultPayload);
                }}>
                {
                  tooltip && !notLoaded
                    ? <Icon type="exclamation-circle" style={{marginRight: 5}} />
                    : undefined
                }
                <span style={{verticalAlign: 'middle', lineHeight: 'inherit'}}>Run</span>
              </Button>
            </Tooltip>
            <Dropdown
              disabled={!allowedToExecute && !this.isAdmin()}
              overlay={runMenu}
              placement="bottomRight">
              <Button
                id={`run-${version}-menu-button`}
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                }}
                size="small"
                type="primary">
                <Icon type="down" style={{verticalAlign: 'middle', lineHeight: 'inherit'}}/>
              </Button>
            </Dropdown>
          </Button.Group>
        </span>
      );
    }
  };

  renderDisplayOptionsMenu = () => {
    const onSelectDisplayOption = ({key}) => {
      switch (key) {
        case 'metadata': this.toggleMetadataChange(); break;
        case 'issues': this.toggleShowIssuesChange(); break;
        case 'instanceTypeManagement': this.toggleInstanceTypesManagementPanelChange(); break;
      }
    };
    const displayOptionsMenuItems = [];
    displayOptionsMenuItems.push(
      <Menu.Item
        id={this.state.metadata ? 'hide-metadata-button' : 'show-metadata-button'}
        key="metadata">
        <Row type="flex" justify="space-between" align="middle">
          <span>Attributes</span>
          <Icon type="check-circle" style={{display: this.state.metadata ? 'inherit' : 'none'}} />
        </Row>
      </Menu.Item>
    );
    displayOptionsMenuItems.push(
      <Menu.Item
        id={this.state.showIssuesPanel ? 'hide-issues-panel-button' : 'show-issues-panel-button'}
        key="issues">
        <Row type="flex" justify="space-between" align="middle">
          <span>{this.localizedString('Issue')}s</span>
          <Icon type="check-circle" style={{display: this.state.showIssuesPanel ? 'inherit' : 'none'}} />
        </Row>
      </Menu.Item>
    );
    if (roleModel.isOwner(this.props.tool.value) || this.isAdmin()) {
      displayOptionsMenuItems.push(
        <Menu.Item
          id={
            this.state.instanceTypesManagementPanel
              ? 'hide-instance-types-management-panel-button'
              : 'show-instance-types-management-panel-button'
          }
          key="instanceTypeManagement">
          <Row type="flex" justify="space-between" align="middle">
            <span>Instance management</span>
            <Icon
              type="check-circle"
              style={{display: this.state.instanceTypesManagementPanel ? 'inherit' : 'none'}}/>
          </Row>
        </Menu.Item>
      );
    }
    const displayOptionsMenu = (
      <Menu onClick={onSelectDisplayOption} style={{width: 150}}>
        {displayOptionsMenuItems}
      </Menu>
    );

    return (
      <Dropdown
        key="display attributes"
        overlay={displayOptionsMenu}>
        <Button
          id="display-attributes"
          size="small">
          <Icon type="appstore" style={{verticalAlign: 'middle', lineHeight: 'inherit'}} />
        </Button>
      </Dropdown>
    );
  };

  renderActionsMenu = () => {
    const permissionsKey = 'permissions';
    const deleteKey = 'delete';
    const onClick = ({key}) => {
      switch (key) {
        case permissionsKey: this.openPermissionsForm(); break;
        case deleteKey: this.deleteToolConfirm(); break;
      }
    };
    const menu = (
      <Menu onClick={onClick}>
        <Menu.Item key={permissionsKey}>
          <Icon type="setting" /> Permissions
        </Menu.Item>
        <Menu.Divider />
        <Menu.Item key={deleteKey} style={{color: 'red'}}>
          <Icon type="delete" /> Delete tool
        </Menu.Item>
      </Menu>
    );
    return (
      <Dropdown overlay={menu} placement="bottomRight">
        <Button id="setting-button" size="small">
          <Icon type="setting" style={{lineHeight: 'inherit', verticalAlign: 'middle'}} />
        </Button>
      </Dropdown>
    );
  };

  render () {
    if ((!this.props.tool.loaded && this.props.tool.pending) ||
      (!this.props.docker.loaded && this.props.docker.pending) ||
      (!this.props.versionSettings.loaded && this.props.versionSettings.pending) ||
      (!this.props.allowedInstanceTypes.loaded && this.props.allowedInstanceTypes.pending)) {
      return <LoadingView />;
    }
    if (this.props.tool.error) {
      return <Alert type="error" message={this.props.tool.error} />;
    }
    if (this.props.docker.error) {
      return <Alert type="error" message={this.props.docker.error} />;
    }
    if (this.props.versionSettings.error) {
      return <Alert type="error" message={this.props.docker.error} />;
    }
    if (!roleModel.readAllowed(this.props.tool.value)) {
      return (
        <Card
          className={styles.toolsCard}
          bodyStyle={{padding: 15, height: '100%', display: 'flex', flexDirection: 'column'}}>
          <Alert type="error" message="You have no permissions to view tool details" />
        </Card>
      );
    }
    return (
      <Card
        className={styles.toolsCard}
        bodyStyle={{padding: 15, height: '100%', display: 'flex', flexDirection: 'column'}}>
        <table className={styles.toolsHeader}>
          <tbody>
            <tr>
              <td className={styles.title} style={{width: '33%'}}>
                <Button
                  onClick={this.navigateBack}
                  size="small"
                  style={{marginBottom: 3, verticalAlign: 'middle', lineHeight: 'inherit'}}>
                  <Icon type="arrow-left" />
                </Button> {this.props.tool.value.image}
              </td>
              <td style={{width: '33%', verticalAlign: 'bottom'}}>
                {this.renderMenu()}
              </td>
              <td className={styles.toolActions} style={{textAlign: 'right', width: '33%'}}>
                {
                  this.renderDisplayOptionsMenu()
                }
                {
                  roleModel.isOwner(this.props.tool.value) && this.renderActionsMenu()
                }
                {
                  roleModel.executeAllowed(this.props.tool.value) && this.renderRunButton()
                }
              </td>
            </tr>
          </tbody>
        </table>
        {
          this.renderContent()
        }
        <Modal
          title="Permissions"
          footer={false}
          onCancel={this.closePermissionsForm}
          visible={this.state.permissionsFormVisible}>
          <PermissionsForm
            objectIdentifier={this.props.toolId}
            objectType="TOOL" />
        </Modal>
      </Card>
    );
  }

  componentDidUpdate () {
    if (this.hasPendingScanning && !this.props.versions.isUpdating) {
      this.props.versions.startInterval();
    } else if (!this.hasPendingScanning && this.props.versions.isUpdating) {
      this.props.versions.clearInterval();
    }
    if (!this.defaultVersionSettings && this.defaultTag) {
      this.defaultVersionSettings = new LoadToolVersionSettings(this.props.tool.value.id, this.defaultTag);
      this.defaultVersionSettings.fetch();
    }
  }

  componentWillUnmount () {
    this.props.allowedInstanceTypesCache.invalidateAllowedTypes(this.props.toolId);
    this.props.versions.clearInterval();
  }
}
