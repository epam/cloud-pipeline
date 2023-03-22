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
import {
  Alert,
  Button,
  Card,
  Col,
  Icon,
  Input,
  Menu as MenuHorizontal,
  message,
  Modal,
  Popover,
  Row,
  Table,
  Tooltip,
  Upload
} from 'antd';
import Menu, {MenuItem, Divider} from 'rc-menu';
import Dropdown from 'rc-dropdown';
import classNames from 'classnames';
import LoadTool from '../../models/tools/LoadTool';
import ToolImage from '../../models/tools/ToolImage';
import ToolUpdate from '../../models/tools/ToolUpdate';
import ToolDelete from '../../models/tools/ToolDelete';
import ToolSymlink from '../../models/tools/ToolSymlink';
import LoadToolVersionSettings from '../../models/tools/LoadToolVersionSettings';
import UpdateToolVersionSettings from '../../models/tools/UpdateToolVersionSettings';
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
} from '../special/splitPanel';
import Owner from '../special/owner';
import styles from './Tools.css';
import PermissionsForm from '../roleModel/PermissionsForm';
import roleModel from '../../utils/roleModel';
import localization from '../../utils/localization';
import displayDate from '../../utils/displayDate';
import displaySize from '../../utils/displaySize';
import LoadToolAttributes from '../../models/tools/LoadToolInfo';
import LoadToolScanPolicy from '../../models/tools/LoadToolScanPolicy';
import UpdateToolVersionWhiteList from '../../models/tools/UpdateToolVersionWhiteList';
import ToolScan from '../../models/tools/ToolScan';
import AllowedInstanceTypes from '../../models/utils/AllowedInstanceTypes';
import VersionScanResult from './elements/VersionScanResult';
import {
  submitsRun,
  modifyPayloadForAllowedInstanceTypes,
  run,
  runPipelineActions
} from '../runs/actions';
import InstanceTypesManagementForm
from '../settings/forms/InstanceTypesManagementForm';
import deleteToolConfirmModal from './tool-deletion-warning';
import ToolLink from './elements/ToolLink';
import CreateLinkForm from './forms/CreateLinkForm';
import PlatformIcon from './platform-icon';
import HiddenObjects from '../../utils/hidden-objects';
import {withCurrentUserAttributes} from '../../utils/current-user-attributes';
import Markdown from '../special/markdown';
import {applyUserCapabilities} from '../pipelines/launch/form/utilities/run-capabilities';
import ToolHistory from './ToolHistory';

const INSTANCE_MANAGEMENT_PANEL_KEY = 'INSTANCE_MANAGEMENT';
const MAX_INLINE_VERSION_ALIASES = 7;
const DEFAULT_FILE_SIZE_KB = 50;

@localization.localizedComponent
@submitsRun
@runPipelineActions
@HiddenObjects.injectToolsFilters
@HiddenObjects.checkTools(props => props?.params?.id)
@inject('awsRegions', 'dockerRegistries', 'preferences')
@inject(({allowedInstanceTypes, dockerRegistries, authenticatedUserInfo, preferences}, {params}) => {
  return {
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
@withCurrentUserAttributes()
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
    instanceTypesManagementPanel: false,
    createLinkInProgress: false,
    createLinkFormVisible: false,
    versionFilterValue: undefined
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
  get defaultVersionPlatform () {
    if (this.defaultVersionSettings && this.defaultVersionSettings.loaded) {
      if ((this.defaultVersionSettings.value || []).length > 0) {
        return this.defaultVersionSettings.value[0].platform;
      }
    }
    return undefined;
  }

  @computed
  get defaultVersionAllowCommit () {
    let allowCommit;
    if (this.defaultVersionSettings && this.defaultVersionSettings.loaded) {
      if ((this.defaultVersionSettings.value || []).length > 0) {
        allowCommit = this.defaultVersionSettings.value[0].allowCommit;
      }
    }
    if (allowCommit === undefined) {
      return true;
    }
    return allowCommit;
  }

  @computed
  get registries () {
    if (this.props.docker.loaded) {
      return this.props.hiddenToolsTreeFilter(this.props.docker.value)
        .registries;
    }
    return [];
  }

  @computed
  get dockerRegistry () {
    if (this.registries.length > 0 && this.props.tool.loaded) {
      return this.registries
        .find(r => r.id === this.props.tool.value.registryId);
    }
    return null;
  }

  @computed
  get toolImage () {
    if (this.props.tool.loaded) {
      return `${this.props.tool.value.registry}/${this.props.tool.value.image}`;
    }
    return null;
  }

  @computed
  get link () {
    if (this.props.tool.loaded) {
      return !!this.props.tool.value.link;
    }
    return false;
  }

  @computed
  get hasWritableToolGroups () {
    if (this.registries.length > 0 && this.props.tool.loaded) {
      const toolGroupId = +(this.props.tool.value.toolGroupId);
      for (let r = 0; r < this.registries.length; r++) {
        const groups = this.registries[r].groups || [];
        if (groups.filter(g => +g.id !== toolGroupId && roleModel.writeAllowed(g)).length > 0) {
          return true;
        }
      }
    }
    return false;
  }

  fetchVersions = async () => {
    await this.props.versions.fetch();
  };

  updateTool = async (values, configuration, allowCommit) => {
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
      updateToolVersionParametersRequest = new UpdateToolVersionSettings(
        this.props.tool.value.id,
        this.defaultTag,
        allowCommit
      );
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
      await this.props.tool.fetch();
      this.defaultVersionSettings && await this.defaultVersionSettings.fetch();
      this.props.versionSettings && await this.props.versionSettings.fetch();
    }
  };

  deleteToolConfirm = () => {
    const deleteToolVersion = this.deleteToolVersion;
    deleteToolConfirmModal({tool: this.props.toolId, link: this.link}, this.props.router)
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
          className={classNames({
            [styles.toolImage]: roleModel.writeAllowed(this.props.tool.value),
            [styles.toolImageReadOnly]: !roleModel.writeAllowed(this.props.tool.value)
          })}
          src={ToolImage.url(this.props.tool.value.id, this.props.tool.value.iconId)} />
      );
    } else {
      image = (
        <Row type="flex" align="middle" justify="center" className={styles.noImageContainer}>
          <Icon
            className={classNames(styles.noImage, 'cp-text-not-important')}
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
      <div
        className={
          classNames(
            styles.toolImageContainer,
            'cp-tool-icon-container'
          )
        }
        style={{marginRight: 10}}
      >
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
            className={
              classNames(styles.noDescription, 'cp-tool-no-description')
            }
          >
            No description
          </span>;
        }
      }
    };
    const renderDescription = () => {
      if (this.state.editDescriptionMode) {
        return (
          <Input.TextArea
            autosize
            autoFocus
            id="description-input"
            value={this.state.description}
            onChange={(e) => this.setState({description: e.target.value})}
            onKeyDown={(e) => {
              if (e.key && e.key === 'Escape') {
                this.toggleEditDescriptionMode(false);
              }
            }}
            style={{
              width: '100%',
              height: '100%',
              resize: 'none',
              borderRadius: 0,
              minHeight: '20vh'
            }} />
        );
      } else {
        const description = this.props.tool.value.description;
        if (description && description.trim().length) {
          return (
            <Markdown
              id="description-text-container"
              md={description}
            />
          );
        } else {
          return (
            <span
              id="description-text"
              className={classNames(styles.noDescription, 'cp-tool-no-description')}
            >
              No description
            </span>
          );
        }
      }
    };

    const renderActions = (isShortDescription) => {
      if (!roleModel.writeAllowed(this.props.tool.value) || this.link) {
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
            onClick={() => method(false)}
            style={{lineHeight: 1}}
          >
            CANCEL
          </Button>
        );
        buttons.push(
          <Button
            id={`${isShortDescription ? 'short-description' : 'description'}-edit-save-button`}
            key="save"
            size="small"
            type="primary"
            onClick={this.onSave(isShortDescription)}
            style={{lineHeight: 1}}
          >
            SAVE
          </Button>
        );
      } else {
        buttons.push(
          <Button
            id={`${isShortDescription ? 'short-description' : 'description'}-edit-button`}
            key="edit"
            size="small"
            onClick={() => method(true)}
            style={{lineHeight: 1}}
          >
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
    const registry = this.registries.find(r => r.id === this.props.tool.value.registryId);
    if (registry && roleModel.readAllowed(this.props.tool.value) && registry.pipelineAuth) {
      const renderPullCommand = () => {
        if (!registry) {
          return <Icon type="loading" />;
        }
        return `docker pull ${registry.externalUrl || registry.path}/${this.props.tool.value.image}`;
      };
      shortDescriptionAndPullCommand = (
        <Row type="flex">
          <Col className="cp-tool-panel" style={{flex: 2}}>
            <Row
              type="flex"
              align="middle"
              justify="space-between"
              className={classNames(styles.descriptionTableHeader, 'cp-tool-panel-header')}
            >
              <span className={styles.descriptionTitle}>Short description</span>
              {renderActions(true)}
            </Row>
            <Row
              type="flex"
              className={classNames(styles.descriptionTableBody, 'cp-tool-panel-body')}>
              {renderShortDescription()}
            </Row>
          </Col>
          <Col className="cp-tool-panel" style={{flex: 1}}>
            <Row
              type="flex"
              align="middle"
              justify="space-between"
              className={classNames(styles.descriptionTableHeader, 'cp-tool-panel-header')}>
              <span className={styles.descriptionTitle}>Default pull command</span>
            </Row>
            <Row
              type="flex"
              className={classNames(styles.descriptionTableBody, 'cp-tool-panel-body')}>
              <span className={styles.description}>
                <code>{renderPullCommand()}</code>
              </span>
            </Row>
          </Col>
        </Row>
      );
    } else {
      shortDescriptionAndPullCommand = (
        <div className="cp-tool-panel">
          <Row
            key="header"
            type="flex"
            align="middle"
            justify="space-between"
            className={classNames(styles.descriptionTableHeader, 'cp-tool-panel-header')}>
            <span className={styles.descriptionTitle}>Short description</span>
            {renderActions(true)}
          </Row>
          <Row
            key="body"
            type="flex"
            className={classNames(styles.descriptionTableBody, 'cp-tool-panel-body')}>
            {renderShortDescription()}
          </Row>
        </div>
      );
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
        <div className="cp-tool-panel">
          <Row
            type="flex"
            align="middle"
            justify="space-between"
            className={classNames(styles.descriptionTableHeader, 'cp-tool-panel-header')}
          >
            <span className={styles.descriptionTitle}>Full description</span>
            {renderActions(false)}
          </Row>
          <Row
            type="flex"
            className={
              classNames(
                styles.descriptionTableBody,
                'cp-tool-panel-body',
                {
                  'no-padding': this.state.editDescriptionMode
                }
              )
            }
            style={{flex: 1, overflowY: 'auto'}}>
            {renderDescription()}
          </Row>
        </div>
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

  historyAvailableForUser = () => {
    if (!this.props.tool.loaded) {
      return false;
    }
    return roleModel.writeAllowed(this.props.tool.value) ||
      roleModel.executeAllowed(this.props.tool.value);
  };

  getVersionScanningInfo = (item) => {
    if (/^windows$/i.test(item.platform)) {
      return null;
    }
    if (this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry) && item.status) {
      let scanningInfo;
      switch (item.status.toUpperCase()) {
        case ScanStatuses.completed:
          if (item.successScanDate) {
            scanningInfo = <span>Successfully scanned at {displayDate(item.successScanDate)}</span>;
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
                className={classNames(styles.scanningError, 'cp-error')}>failed</span>{item.scanDate ? ` at ${displayDate(item.scanDate)}` : ''}. Last successful scan: {displayDate(item.successScanDate)}</span>;
          } else {
            scanningInfo = <span>Scanning <span
              className={classNames(styles.scanningError, 'cp-error')}>failed</span>{item.scanDate ? ` at ${displayDate(item.scanDate)}` : ''}</span>;
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
        const {
          Critical = 0,
          High = 0,
          Low = 0,
          Medium = 0,
          Negligible = 0
        } = scanResult.vulnerabilitiesCount || {};

        const digestAliases = versionAttributes && versionAttributes.digest
          ? versionsByDigest[versionAttributes.digest]
            .filter(version => version !== currentVersion.version)
          : [];

        data.push({
          key: keyIndex,
          name: currentVersion.version,
          digest: versionAttributes && versionAttributes.digest ? versionAttributes.digest : '',
          platform: versionAttributes ? versionAttributes.platform : undefined,
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
            critical: Critical,
            high: High,
            medium: Medium,
            low: Low,
            negligible: Negligible
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

  get toolVersionOS () {
    const scanInfo = this.toolVersionScanResults
      .find(o => o.name === this.defaultTag);
    if (scanInfo && scanInfo.toolOSVersion && scanInfo.toolOSVersion.distribution) {
      const {
        distribution,
        version = ''
      } = scanInfo.toolOSVersion;
      return [
        distribution,
        version
      ]
        .filter(Boolean)
        .join(' ');
    }
    return undefined;
  }

  renderDigestAliases = (aliases) => {
    const renderAlias = alias => {
      return (
        <span
          key={alias}
          className={classNames(styles.versionAliasItem, 'cp-tag')}>
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
                <th className={styles.versionName}>
                  {name}
                </th>
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
                <td
                  colSpan={3}
                  className={classNames(styles.versionScanningInfo, 'cp-text-not-important')}
                >
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
      render: (toolOSVersion, item) => {
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
              !/^windows$/i.test(version.platform) &&
              this.isAdmin() &&
              !this.link &&
              this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry) &&
              (
                <Button
                  size="small"
                  onClick={(e) => this.toggleVersionWhiteList(e, version)}
                  style={{lineHeight: 1}}
                >
                  {version.fromWhiteList ? 'Remove from ' : 'Add to '}white list
                </Button>
              )
            }
            {
              !/^windows$/i.test(version.platform) &&
              this.isAdmin() &&
              !this.link &&
              this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry) &&
              (
                <Button
                  type="primary"
                  loading={version.status === ScanStatuses.pending}
                  size="small"
                  onClick={(e) => this.toolScan(e, version.name)}
                  style={{lineHeight: 1}}
                >
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
              roleModel.writeAllowed(this.props.tool.value) && !this.link &&
              (
                <Button
                  size="small"
                  type="danger"
                  disabled={this.isLastVersion}
                  onClick={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    this.deleteToolVersionConfirm(version.name);
                  }}
                  style={{lineHeight: 1}}
                >
                  <Icon type="delete" />
                </Button>
              )
            }
          </Row>
        );
      }
    }];

    let data = this.toolVersionScanResults;
    const containsUnScannedVersion = this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry) &&
      data.filter(item => item.platform !== 'windows' && item.status === ScanStatuses.notScanned).length > 0;
    if (this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry) && !this.state.isShowUnscannedVersion) {
      data = data.filter(d => d.platform === 'windows' || d.status !== ScanStatuses.notScanned);
    }
    const {versionFilterValue} = this.state;
    if (versionFilterValue && versionFilterValue.length) {
      data = data.filter((item) => (
        item.name.toLowerCase().includes(versionFilterValue.toLocaleLowerCase())
      ));
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
        <Input
          value={versionFilterValue}
          onChange={(e) => {
            this.setState({versionFilterValue: e.target.value});
          }}
          placeholder="Enter version name"
          className={styles.versionFilterInput}
        />
        <Table
          className={styles.table}
          loading={this.props.versions.pending}
          showHeader
          rowClassName={(item) => classNames(
            styles.versionTableRow,
            {
              [styles.whiteListedVersionRow]: item.fromWhiteList &&
              !/^windows$/i.test(item.platform) &&
              this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry),
              'cp-tool-white-listed-version': item.fromWhiteList &&
                !/^windows$/i.test(item.platform) &&
                this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry)
            }
          )}
          columns={columns}
          dataSource={data}
          pagination={{pageSize: 20}}
          onRowClick={(version) => {
            if (this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry) &&
              !/^windows$/i.test(version.platform) &&
              version.status !== ScanStatuses.notScanned) {
              this.props.router.push(`/tool/${this.props.toolId}/info/${version.name}/scaninfo`);
            } else {
              this.props.router.push(`/tool/${this.props.toolId}/info/${version.name}/settings`);
            }
          }}
          size="small" />
        {
          containsUnScannedVersion &&
          <Row className={styles.viewUnscannedVersion}>
            <Button
              style={{marginTop: 10, lineHeight: 1}}
              key="view_unscanned_version"
              size="large"
              type="primary"
              ghost
              onClick={this.onChangeViewUnscannedVersion}
            >
              {
                this.state.isShowUnscannedVersion
                  ? 'HIDE UNSCANNED VERSIONS'
                  : 'VIEW UNSCANNED VERSIONS'
              }
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
          readOnly={!roleModel.writeAllowed(this.props.tool.value) || this.link}
          configuration={this.defaultVersionSettingsConfiguration}
          platform={this.defaultVersionPlatform}
          allowCommitVersion={this.defaultVersionAllowCommit}
          tool={this.props.tool.value}
          toolId={this.props.toolId}
          defaultPriceTypeIsSpot={this.props.preferences.useSpot}
          executionEnvironmentDisabled={!this.defaultTag}
          onSubmit={this.updateTool}
          dockerOSVersion={this.toolVersionOS}
        />
      </div>
    );
  };

  renderToolHistory = () => {
    if (!this.props.tool.loaded || !this.historyAvailableForUser()) {
      return undefined;
    }
    return (
      <ToolHistory image={this.toolImage} />
    );
  };

  renderToolContent = () => {
    switch (this.props.section) {
      case 'description': return this.renderDescription();
      case 'versions': return this.renderVersions();
      case 'settings': return this.renderToolSettings();
      case 'history': return this.renderToolHistory();
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
            readOnly={!!this.link}
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
            readOnly={!roleModel.isOwner(this.props.tool.value) || !!this.link}
            entityId={this.props.toolId}
            entityClass="TOOL" />
        }
        {
          this.state.instanceTypesManagementPanel && (roleModel.isOwner(this.props.tool.value) || this.isAdmin()) &&
          <InstanceTypesManagementForm
            key={INSTANCE_MANAGEMENT_PANEL_KEY}
            level="TOOL"
            resourceId={this.props.toolId}
            disabled={!!this.link}
          />
        }
      </SplitPanel>
    );
  };

  renderMenu = () => {
    const onChangeSection = ({key}) => {
      this.props.router.push(`/tool/${this.props.toolId}/${key}`);
    };
    return (
      <MenuHorizontal
        className={styles.toolMenu}
        onClick={onChangeSection}
        mode="horizontal"
        selectedKeys={[this.props.section]}>
        <MenuHorizontal.Item key="description">
          DESCRIPTION
        </MenuHorizontal.Item>
        <MenuHorizontal.Item key="versions">
          VERSIONS
        </MenuHorizontal.Item>
        <MenuHorizontal.Item key="settings">
          SETTINGS
        </MenuHorizontal.Item>
        {
          this.historyAvailableForUser() && (
            <MenuHorizontal.Item key="history">
              HISTORY
            </MenuHorizontal.Item>
          )
        }
      </MenuHorizontal>
    );
  };

  runToolDefault = async (version) => {
    const {currentUserAttributes} = this.props;
    await currentUserAttributes.refresh();
    const parameterIsNotEmpty = (parameter, additionalCriteria) =>
      parameter !== null &&
      parameter !== undefined &&
      `${parameter}`.trim().length > 0 &&
      (!additionalCriteria || additionalCriteria(parameter));
    const [versionSettings] = (this.props.versionSettings.value || [])
      .filter(v => v.version === version);
    const [defaultVersionSettings] = (this.props.versionSettings.value || [])
      .filter(v => v.version === this.defaultTag);
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
    const chooseDefaultValue = (
      versionSettingsValue,
      toolValue,
      settingsValue,
      additionalCriteria
    ) => {
      if (parameterIsNotEmpty(versionSettingsValue, additionalCriteria)) {
        return versionSettingsValue;
      }
      if (parameterIsNotEmpty(toolValue, additionalCriteria)) {
        return toolValue;
      }
      return settingsValue;
    };
    const registry = this.registries.find(r => r.id === this.props.tool.value.registryId);
    const prepareParameters = (parameters) => {
      const result = {};
      if (parameters) {
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
      }
      return currentUserAttributes.extendLaunchParameters(
        result,
        this.props.tool.value.allowSensitive
      );
    };
    const cloudRegionIdValue = parameterIsNotEmpty(versionSettingValue('cloudRegionId'))
      ? versionSettingValue('cloudRegionId')
      : this.defaultCloudRegionId;
    const isSpotValue = parameterIsNotEmpty(versionSettingValue('is_spot'))
      ? versionSettingValue('is_spot')
      : this.props.preferences.useSpot;
    const allowedInstanceTypesRequest = new AllowedInstanceTypes(
      this.props.toolId,
      cloudRegionIdValue,
      isSpotValue
    );
    await allowedInstanceTypesRequest.fetch();
    const payload = modifyPayloadForAllowedInstanceTypes({
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
      params: prepareParameters(versionSettingValue('parameters')),
      isSpot: isSpotValue,
      nodeCount: parameterIsNotEmpty(versionSettingValue('node_count'))
        ? +versionSettingValue('node_count')
        : undefined,
      cloudRegionId: cloudRegionIdValue
    }, allowedInstanceTypesRequest);
    const titleFn = (runName) => ([
      <span key="launch">
        Are you sure you want to launch
      </span>,
      runName,
      <span key="question">
        with default settings?
      </span>
    ]);
    const info = this.getVersionRunningInformation(version || this.defaultTag);
    const platform = this.defaultVersionPlatform;
    payload.params = await applyUserCapabilities(
      payload.params || {},
      this.props.preferences,
      platform
    );
    if (await run(this)(
      payload,
      true,
      titleFn,
      info.launchTooltip,
      allowedInstanceTypesRequest,
      undefined,
      platform
    )) {
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
      versions[version.version] = version;
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
    const [versionSettings] = (this.props.versionSettings.value || [])
      .filter(v => v.version === (version || this.defaultTag));
    const [defaultVersionSettings] = (this.props.versionSettings.value || [])
      .filter(v => v.version === this.defaultTag);
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
      parameterIsNotEmpty(
        this.props.preferences.getPreferenceValue('cluster.instance.hdd'),
        p => +p > 0
      );
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
            className={styles.runButton}
            disabled={!allowedToExecute && !this.isAdmin()}
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              this.runTool(version || this.anyTag);
            }}
            style={{lineHeight: 1}}
          >
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
    version = version || this.defaultTag;
    const {allowedToExecute, tooltip, notLoaded} = this.getVersionRunningInformation(version);
    if (!allowedToExecute) {
      return (
        <Tooltip
          placement="left"
          title={tooltip}
          trigger="hover">
          <Button
            id={`run-${version}-button`}
            className={styles.runButton}
            type="primary"
            size="small"
            disabled={!allowedToExecute && !this.isAdmin()}
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              return this.runToolDefault(version);
            }}
            style={{lineHeight: 1}}
          >
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
          case runDefaultKey: this.runToolDefault(version); break;
          case runCustomKey: this.runTool(version); break;
        }
      };
      const runMenu = (
        <Menu
          selectedKeys={[]}
          onClick={onSelect}
          style={{cursor: 'pointer'}}
        >
          <MenuItem id="run-default-button" key={runDefaultKey}>
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
          </MenuItem>
          <MenuItem id="run-custom-button" key={runCustomKey}>
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
          </MenuItem>
        </Menu>
      );
      return (
        <Button.Group className={styles.runButton}>
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
                return this.runToolDefault(version);
              }}
              style={{lineHeight: 1}}
            >
              {
                tooltip && !notLoaded
                  ? <Icon type="exclamation-circle" style={{marginRight: 5}} />
                  : undefined
              }
              <span style={{lineHeight: 'inherit'}}>Run</span>
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
              type="primary"
              style={{lineHeight: 1}}
            >
              <Icon type="down" style={{lineHeight: 'inherit'}} />
            </Button>
          </Dropdown>
        </Button.Group>
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
      <MenuItem
        id={this.state.metadata ? 'hide-metadata-button' : 'show-metadata-button'}
        key="metadata">
        <Row type="flex" justify="space-between" align="middle">
          <span>Attributes</span>
          <Icon type="check-circle" style={{display: this.state.metadata ? 'inherit' : 'none'}} />
        </Row>
      </MenuItem>
    );
    displayOptionsMenuItems.push(
      <MenuItem
        id={this.state.showIssuesPanel ? 'hide-issues-panel-button' : 'show-issues-panel-button'}
        key="issues">
        <Row type="flex" justify="space-between" align="middle">
          <span>{this.localizedString('Issue')}s</span>
          <Icon type="check-circle" style={{display: this.state.showIssuesPanel ? 'inherit' : 'none'}} />
        </Row>
      </MenuItem>
    );
    if (roleModel.isOwner(this.props.tool.value) || this.isAdmin()) {
      displayOptionsMenuItems.push(
        <MenuItem
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
              style={{display: this.state.instanceTypesManagementPanel ? 'inherit' : 'none'}} />
          </Row>
        </MenuItem>
      );
    }
    const displayOptionsMenu = (
      <Menu
        onClick={onSelectDisplayOption}
        style={{width: 150, cursor: 'pointer'}}
        selectedKeys={[]}
      >
        {displayOptionsMenuItems}
      </Menu>
    );

    return (
      <Dropdown
        key="display attributes"
        overlay={displayOptionsMenu}>
        <Button
          id="display-attributes"
          size="small"
          style={{lineHeight: 1}}
        >
          <Icon type="appstore" style={{lineHeight: 'inherit'}} />
        </Button>
      </Dropdown>
    );
  };

  openCreateLinkForm = () => {
    this.setState({
      createLinkFormVisible: true
    });
  }

  closeCreateLinkForm = () => {
    this.setState({
      createLinkFormVisible: false
    });
  }

  onCreateLink = ({groupId} = {}) => {
    const wrap = (fn) => {
      this.setState({
        createLinkInProgress: true
      }, async () => {
        const {error, id} = await fn();
        if (error) {
          this.setState({
            createLinkInProgress: false
          }, () => {
            message.error(error, 5);
          });
        } else {
          this.setState({
            createLinkInProgress: false,
            createLinkFormVisible: false
          }, () => {
            this.props.router.push(`tool/${id}`);
          });
        }
      });
    };
    wrap(async () => {
      const hide = message.loading('Creating tool link...', 0);
      const request = new ToolSymlink();
      await request.send({
        groupId,
        toolId: this.props.toolId
      });
      hide();
      if (request.error) {
        return {error: request.error};
      } else {
        return request.value;
      }
    });
  };

  renderCreateLinkButton = () => {
    if (
      !this.link &&
      this.hasWritableToolGroups &&
      this.props.tool.loaded &&
      roleModel.executeAllowed(this.props.tool.value)
    ) {
      return (
        <Button
          disabled={!this.props.tool.loaded}
          size="small"
          onClick={this.openCreateLinkForm}
          style={{lineHeight: 1}}
        >
          <Icon type="link" />
        </Button>
      );
    }
    return null;
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
      <Menu
        onClick={onClick}
        style={{cursor: 'pointer'}}
        selectedKeys={[]}
      >
        <MenuItem key={permissionsKey}>
          <Icon type="setting" /> Permissions
        </MenuItem>
        <Divider />
        <MenuItem key={deleteKey} className="cp-danger">
          <Icon type="delete" /> Delete tool {this.link ? 'link' : false}
        </MenuItem>
      </Menu>
    );
    return (
      <Dropdown overlay={menu} placement="bottomRight">
        <Button id="setting-button" size="small" style={{lineHeight: 1}}>
          <Icon type="setting" style={{lineHeight: 'inherit'}} />
        </Button>
      </Dropdown>
    );
  };

  render () {
    if ((!this.props.tool.loaded && this.props.tool.pending) ||
      (!this.props.docker.loaded && this.props.docker.pending) ||
      (!this.props.versionSettings.loaded && this.props.versionSettings.pending)) {
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
          className={
            classNames(
              styles.toolsCard,
              'cp-panel',
              'cp-panel-no-hover',
              'cp-panel-borderless'
            )
          }
          bodyStyle={{padding: 15, height: '100%', display: 'flex', flexDirection: 'column'}}>
          <Alert type="error" message="You have no permissions to view tool details" />
        </Card>
      );
    }
    return (
      <Card
        className={
          classNames(
            styles.toolsCard,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )
        }
        bodyStyle={{padding: 15, height: '100%', display: 'flex', flexDirection: 'column'}}>
        <div className={classNames(styles.toolsHeader, 'cp-tool-header')}>
          <div className={styles.title} style={{flex: 1}}>
            <Button
              onClick={this.navigateBack}
              size="small"
              style={{lineHeight: 1}}>
              <Icon type="arrow-left" />
            </Button>
            <ToolLink link={this.link} style={{marginLeft: 5}} />
            <span style={{marginLeft: 5}}>{this.props.tool.value.image}</span>
            <PlatformIcon
              platform={this.defaultVersionPlatform}
              style={{marginLeft: 5}}
            />
            <Owner subject={this.props.tool.value} style={{marginLeft: 5}} />
          </div>
          <div style={{flex: 1}}>
            {this.renderMenu()}
          </div>
          <div className={styles.toolActions} style={{textAlign: 'right', flex: 1}}>
            {
              this.renderDisplayOptionsMenu()
            }
            {
              this.renderCreateLinkButton()
            }
            {
              roleModel.isOwner(this.props.tool.value) && this.renderActionsMenu()
            }
            {
              roleModel.executeAllowed(this.props.tool.value) && this.renderRunButton()
            }
          </div>
        </div>
        {
          this.renderContent()
        }
        <CreateLinkForm
          disabled={this.state.createLinkInProgress}
          visible={this.state.createLinkFormVisible}
          onSubmit={this.onCreateLink}
          onClose={this.closeCreateLinkForm}
          source={this.props.tool.loaded ? this.props.tool.value : null}
        />
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
    if (
      this.props.tool.loaded &&
      !this.historyAvailableForUser() &&
      /^history$/i.test(this.props.section)
    ) {
      this.props.router.push(`/tool/${this.props.toolId}`);
    }
  }

  componentWillUnmount () {
    this.props.allowedInstanceTypesCache.invalidateAllowedTypes(this.props.toolId);
    this.props.versions.clearInterval();
  }
}
