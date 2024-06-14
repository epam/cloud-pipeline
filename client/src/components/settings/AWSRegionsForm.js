/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed, observable} from 'mobx';
import classNames from 'classnames';
import LoadingView from '../special/LoadingView';
import {SplitPanel} from '../special/splitPanel';
import {
  Alert,
  Button,
  Checkbox,
  Icon,
  Input,
  InputNumber,
  Modal,
  message,
  Form,
  Row,
  Select,
  Table,
  Tooltip, Col, AutoComplete
} from 'antd';
import Menu, {MenuItem} from 'rc-menu';
import Dropdown from 'rc-dropdown';
import AWSRegionUpdate from '../../models/dataStorage/AWSRegionUpdate';
import AWSRegionDelete from '../../models/dataStorage/AWSRegionDelete';
import AWSRegionCreate from '../../models/dataStorage/AWSRegionCreate';
import FileShareMountUpdate from '../../models/fileShareMount/FileShareMountUpdate';
import FileShareMountDelete from '../../models/fileShareMount/FileShareMountDelete';
import GrantGet from '../../models/grant/GrantGet';
import GrantPermission from '../../models/grant/GrantPermission';
import GrantRemove from '../../models/grant/GrantRemove';
import Roles from '../../models/user/Roles';
import UserFind from '../../models/user/UserFind';
import GroupFind from '../../models/user/GroupFind';
import UserName from '../special/UserName';
import CodeEditorFormItem from '../special/CodeEditorFormItem';
import AWSRegionTag from '../special/AWSRegionTag';
import ProviderForm from './cloud-provider';
import highlightText from '../special/highlightText';
import styles from './AWSRegionsForm.css';
import RunShiftPolicy, {runShiftPoliciesEqual} from './cloud-regions/run-shift-policy';
import RegionIdSelector from './region-id-selector';
import CloudRegionContextualSetting, {
  fetchContextualSettingValue,
  updateContextualSettingValue,
  launchCommonMountsSetting
} from './cloud-regions/contextual-setting';

const AWS_REGION_ITEM_TYPE = 'CLOUD_REGION';

const preProcessJSON = (value, throwError = false) => {
  if (value) {
    try {
      return JSON.stringify(
        JSON.parse(value),
        null,
        '\t'
      );
    } catch (e) {
      if (throwError) {
        throw e;
      }
    }
  }
  return value;
};

function toJSON (obj, defaultValue) {
  try {
    return JSON.stringify(obj || defaultValue, null, ' ');
  } catch (___) {}
  return '';
}

function fromJSON (obj, defaultValue) {
  try {
    return JSON.parse(obj);
  } catch (___) {}
  return defaultValue;
}

function parseSLSProperties (propertiesObject) {
  if (propertiesObject) {
    return toJSON(propertiesObject);
  }
  return '';
}

function buildSLSProperties (properties) {
  if (!properties) {
    return undefined;
  }
  return fromJSON(properties);
}

@inject('awsRegions', 'availableCloudRegions', 'cloudProviders', 'router', 'authenticatedUserInfo')
@observer
export default class AWSRegionsForm extends React.Component {
  static propTypes = {
    onInitialize: PropTypes.func
  };

  state = {
    currentRegionId: null,
    currentProvider: null,
    search: null,
    operationInProgress: false,
    newRegion: null,
    changesCanBeSkipped: false
  };

  @observable awsRegionForm;
  @observable awsRegionIds;

  componentDidMount () {
    const {route, router} = this.props;
    this.props.onInitialize && this.props.onInitialize(this);
    if (route && router) {
      router.setRouteLeaveHook(route, this.checkSettingsBeforeLeave);
    }
  };

  componentDidUpdate () {
    const {currentRegionId, currentProvider} = this.state;
    if (!currentRegionId && !currentProvider && this.regions.length > 0) {
      this.selectDefaultRegion();
    }
  };

  operationWrapper = (fn) => {
    return (...opts) => {
      this.setState({
        operationInProgress: true
      }, async () => {
        await fn(...opts);
        this.setState({
          operationInProgress: false
        });
      });
    };
  };

  @computed
  get regions () {
    if (this.props.awsRegions.loaded) {
      const searchFn = (region) => {
        if (this.state.search) {
          return region.name.toLowerCase().indexOf(this.state.search.toLowerCase()) >= 0;
        }
        return true;
      };
      return (this.props.awsRegions.value || []).map(
        r => {
          return {
            ...r,
            corsRules: preProcessJSON(r.corsRules),
            policy: preProcessJSON(r.policy),
            storageLifecycleServiceProperties: parseSLSProperties(
              r.storageLifecycleServiceProperties
            )
          };
        }).filter(searchFn);
    }
    return [];
  }

  @computed
  get cloudProviders () {
    if (this.props.cloudProviders.loaded) {
      return this.props.cloudProviders.value || [];
    }
    return [];
  }

  loadAvailableRegionIds = () => {
    if (this.state.newRegion || this.state.currentProvider) {
      this.awsRegionIds = this.props.availableCloudRegions.load(this.state.newRegion || this.state.currentProvider);
      this.awsRegionIds.fetchIfNeededOrWait();
    } else {
      this.awsRegionIds = null;
    }
  };

  @computed
  get availableRegionIds () {
    if (!this.awsRegionIds) {
      return [];
    }
    if (this.awsRegionIds.loaded) {
      return (this.awsRegionIds.value || [])
        .map(r => r)
        .sort();
    }
    return [];
  }

  @computed
  get currentRegion () {
    return this.regions
      .filter(r => r.id === this.state.currentRegionId)
      .map(r => ({...r, customInstanceTypes: toJSON(r.customInstanceTypes, [])}))[0];
  };

  @computed
  get regionModified () {
    if (!this.awsRegionForm) {
      return false;
    }
    return this.awsRegionForm.modified || !!this.state.newRegion;
  }

  renderAwsRegionsTable = () => {
    const columns = [
      {
        dataIndex: 'name',
        key: 'name',
        render: (name, region) => {
          if (region.isProvider) {
            return (
              <div className={styles.provider}>
                <AWSRegionTag
                  provider={region.name}
                  displayFlag={false}
                  displayName={false}
                  showProvider
                />
                {name}
              </div>
            );
          }
          if (region.isNew) {
            return <i className={styles.region}>{name} ({this.state.newRegion})</i>;
          }
          return (
            <div className={styles.region}>
              <AWSRegionTag
                showProvider={false}
                regionUID={region.regionId}
                style={{fontSize: 'larger'}}
              />
              {highlightText(name, this.state.search)}
            </div>
          );
        }
      }
    ];
    let data = [
      ...(this.cloudProviders || []).map(o => ({
        isProvider: true,
        name: o,
        id: o,
        provider: o
      })),
      ...this.regions.map(o => o)
    ];
    const providers = (this.cloudProviders || []).map(o => o);
    data.sort((a, b) => {
      const idx1 = providers.indexOf(a.provider);
      const idx2 = providers.indexOf(b.provider);
      return idx1 - idx2;
    });
    if (this.state.newRegion) {
      const providerIndex = data.findIndex(o => o.isProvider && o.name === this.state.newRegion);
      const newRegionItem = {id: -1, isNew: true, name: 'New cloud region'};
      if (providerIndex === -1) {
        data = [newRegionItem, ...data];
      } else if (providerIndex < data.length) {
        data.splice(providerIndex + 1, 0, newRegionItem);
      } else {
        data.push(newRegionItem);
      }
    }
    return (
      <Table
        className={styles.table}
        dataSource={data}
        columns={columns}
        showHeader={false}
        pagination={false}
        rowKey="id"
        rowClassName={
          (region) => {
            const selected = (!this.state.newRegion && region.id === this.state.currentRegionId) ||
            (
              !this.state.newRegion &&
              !this.state.currentRegionId &&
              region.isProvider &&
              region.name === this.state.currentProvider
            ) ||
            (region.isNew && this.state.newRegion);
            return classNames(
              styles.regionRow,
              'cp-settings-sidebar-element',
              {
                'cp-table-element-selected': selected
              }
            );
          }
        }
        onRowClick={region => !this.state.newRegion && this.selectRegion(region)}
        size="medium" />
    );
  };

  onSearch = (search) => {
    this.setState({
      search: search
    });
  };

  onChange = (e) => {
    if (!e.target.value) {
      this.setState({
        search: null
      });
    }
  };

  onInitializeAWSRegionForm = (form) => {
    this.awsRegionForm = form;
  };

  onSaveRegion = async (region, settings = []) => {
    const fileShareMounts = region.fileShareMounts;
    region.fileShareMounts = undefined;
    region.customInstanceTypes = fromJSON(region.customInstanceTypes, []);
    const hide = message.loading('Saving region properties...', -1);
    const request = new AWSRegionUpdate(this.state.currentRegionId);
    await request.send(region);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      await this.updateMounts(
        this.currentRegion.id,
        this.currentRegion.fileShareMounts,
        fileShareMounts
      );
      await this.saveContextualSettings(this.state.currentRegionId, settings);
      hide();
      await this.props.awsRegions.fetch();
      if (this.awsRegionForm) {
        this.awsRegionForm.rebuild();
      }
    }
  };

  saveContextualSettings = async (regionId, settings) => {
    if (settings.length > 0) {
      await Promise.all(settings.map((setting) => updateContextualSettingValue(
        setting.setting,
        setting.value,
        setting.type || 'STRING',
        regionId
      )));
    }
  };

  onRemoveRegion = async () => {
    const hide = message.loading('Removing region...', -1);
    const request = new AWSRegionDelete(this.state.currentRegionId);
    await request.fetch();
    if (request.error) {
      message.error(request.error, 5);
      hide();
    } else {
      await this.props.awsRegions.fetch();
      this.props.availableCloudRegions.invalidateCache(this.state.currentProvider);
      hide();
      this.setState({
        currentRegionId: null,
        currentProvider: null
      }, this.loadAvailableRegionIds);
    }
  };

  onRemoveRegionConfirm = async () => {
    return new Promise((resolve) => {
      const remove = async () => {
        await this.onRemoveRegion();
        resolve();
      };
      Modal.confirm({
        title: `Are you sure you want to remove region '${this.currentRegion.name}'?`,
        onOk: remove,
        onCancel: () => resolve()
      });
    });
  };

  onAddNewRegionClicked = (provider) => {
    this.setState({
      newRegion: provider
    }, this.loadAvailableRegionIds);
  };

  updateMounts = async (regionId, originalMounts, targetMounts) => {
    if (!CloudRegionFileShareMountsFormItem.fileShareMountsAreEqual(originalMounts, targetMounts)) {
      const newMounts = (targetMounts || []).filter(m => !m.id);
      const updatedMounts = (targetMounts || []).filter(tm => {
        const [original] = (originalMounts || []).filter(om => om.id === tm.id);
        return !!original && !CloudRegionFileShareMountFormItem.valuesAreEqual(tm, original);
      });
      const deletedMounts = (originalMounts || []).filter(om => {
        return (targetMounts || []).filter(tm => tm.id === om.id).length === 0;
      });
      const mapFn = o => ({
        id: o.id,
        regionId: regionId,
        mountRoot: o.mountRoot,
        mountType: o.mountType,
        mountOptions: o.mountOptions
      });
      const hide = message.loading('Updating mounts...', -1);
      for (let i = 0; i < newMounts.length; i++) {
        const payload = mapFn(newMounts[i]);
        const request = new FileShareMountUpdate();
        await request.send(payload);
        if (request.error) {
          message.error(`Error creating mount ${payload.mountRoot}: ${request.error}`,
            3);
        }
      }
      for (let i = 0; i < updatedMounts.length; i++) {
        const payload = mapFn(updatedMounts[i]);
        const request = new FileShareMountUpdate();
        await request.send(payload);
        if (request.error) {
          message.error(`Error updating mount ${payload.mountRoot}: ${request.error}`,
            3);
        }
      }
      for (let i = 0; i < deletedMounts.length; i++) {
        const request = new FileShareMountDelete(deletedMounts[i].id);
        await request.fetch();
        if (request.error) {
          message.error(`Error deleting mount ${deletedMounts[i].mountRoot}: ${request.error}`,
            3);
        }
      }
      hide();
    }
  };

  onCreateRegion = async (region, settings = []) => {
    region.id = undefined;
    region.provider = this.state.newRegion;
    const fileShareMounts = region.fileShareMounts;
    region.fileShareMounts = undefined;
    region.customInstanceTypes = fromJSON(region.customInstanceTypes, []);
    const hide = message.loading('Creating region...', -1);
    const request = new AWSRegionCreate();
    await request.send(region);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      const id = request.value.id;
      const provider = request.value.provider;
      if (this.awsRegionForm) {
        await this.awsRegionForm.submitPermissions(id);
      }
      await this.updateMounts(id, [], fileShareMounts);
      await this.saveContextualSettings(id, settings);
      hide();
      await this.props.awsRegions.fetch();
      this.props.availableCloudRegions.invalidateCache(provider);
      this.setState({
        currentRegionId: id,
        currentProvider: provider,
        newRegion: null
      }, this.loadAvailableRegionIds);
    }
    await this.props.awsRegions.fetch();
  };

  onCancelCreate = () => {
    this.setState({
      newRegion: null
    }, this.loadAvailableRegionIds);
  };

  onFieldsChanged = () => {
    if (this.awsRegionForm) {
      this.awsRegionForm.onFormFieldChanged();
    }
  };

  awsRegionFormComponent = Form.create({onFieldsChange: this.onFieldsChanged})(AWSRegionForm);

  renderAddNewRegionButton = () => {
    if (this.cloudProviders.length > 1) {
      const menu = (
        <Menu
          onClick={({key}) => this.onAddNewRegionClicked(key)}
          selectedKeys={[]}
          style={{cursor: 'pointer'}}
        >
          {
            this.cloudProviders.map(c => {
              return (
                <MenuItem key={c}>
                  {c}
                </MenuItem>
              );
            })
          }
        </Menu>
      );
      return (
        <Dropdown
          overlay={menu}
          disabled={this.regionModified || !!this.state.newRegion}>
          <Button
            disabled={this.regionModified || !!this.state.newRegion}
            style={{marginLeft: 5}}
            size="small">
            Add new region
          </Button>
        </Dropdown>
      );
    } else if (this.cloudProviders.length === 1) {
      return (
        <Button
          disabled={this.regionModified || !!this.state.newRegion}
          onClick={() => this.onAddNewRegionClicked(this.cloudProviders[0])}
          size="small" style={{marginLeft: 5}}>
          Add new region
        </Button>
      );
    }
    return null;
  };

  renderProviderForm = () => {
    const {currentRegionId, currentProvider, newRegion} = this.state;
    if (currentProvider && !currentRegionId && !newRegion) {
      return (
        <ProviderForm
          provider={currentProvider}
        />
      );
    }
    return null;
  };

  renderRegionForm = () => {
    const {currentRegionId, newRegion} = this.state;
    if (currentRegionId || newRegion) {
      const AWSRegionFormComponent = this.awsRegionFormComponent;
      return (
        <AWSRegionFormComponent
          onInitialize={this.onInitializeAWSRegionForm}
          isNew={!!this.state.newRegion}
          pending={this.props.awsRegions.pending || this.state.operationInProgress}
          onSubmit={this.operationWrapper(this.onSaveRegion)}
          onRemove={this.operationWrapper(this.onRemoveRegionConfirm)}
          onCreate={this.operationWrapper(this.onCreateRegion)}
          onCancelCreate={this.onCancelCreate}
          regionIds={this.availableRegionIds}
          region={
            this.state.newRegion
              ? observable({isNew: true, provider: this.state.newRegion})
              : this.currentRegion
          }
        />
      );
    }
    return null;
  };

  render () {
    if (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending) {
      return null;
    }
    if (!this.props.authenticatedUserInfo.value.admin) {
      return (
        <Alert type="error" message="Access is denied" />
      );
    }
    if (this.props.awsRegions.pending && !this.props.awsRegions.loaded) {
      return <LoadingView />;
    }
    if (this.props.awsRegions.error) {
      return <Alert type="error" message={this.props.awsRegions.error} />;
    }

    return (
      <div style={{flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column'}}>
        <Row type="flex" style={{marginBottom: 10}}>
          <Input.Search
            size="small"
            style={{flex: 1}}
            onChange={this.onChange}
            onSearch={this.onSearch} />
          {this.renderAddNewRegionButton()}
        </Row>
        <SplitPanel
          contentInfo={[
            {
              key: 'regions',
              size: {
                pxDefault: 200
              }
            }
          ]}
          style={{flex: 1, minHeight: 0}}
          className={'cp-transparent-background'}
        >
          <div key="regions">
            {this.renderAwsRegionsTable()}
          </div>
          <div
            key="content"
            style={{
              display: 'flex',
              flexDirection: 'column'
            }}>
            {this.renderProviderForm()}
            {this.renderRegionForm()}
          </div>
        </SplitPanel>
      </div>
    );
  }

  reload = async () => {
    await this.props.awsRegions.fetch();
    this.setState({
      newRegion: null
    }, this.loadAvailableRegionIds);
  };

  checkSettingsBeforeLeave = (nextLocation) => {
    const {router} = this.props;
    const {changesCanBeSkipped} = this.state;
    const makeTransition = nextLocation => {
      this.setState({changesCanBeSkipped: true},
        () => router.push(nextLocation)
      );
    };
    if (this.regionModified && !changesCanBeSkipped) {
      Modal.confirm({
        title: 'You have unsaved changes. Continue?',
        style: {
          wordWrap: 'break-word'
        },
        onOk () {
          makeTransition(nextLocation);
        },
        okText: 'Yes',
        cancelText: 'No'
      });
      return false;
    }
  };

  selectRegion = (region) => {
    if (region.isProvider) {
      if (/^aws$/i.test(region.name)) {
        this.setState({
          currentRegionId: null,
          currentProvider: region.name
        }, this.loadAvailableRegionIds);
      }
    } else if (region) {
      this.setState({
        currentRegionId: region.id,
        currentProvider: region.provider
      }, this.loadAvailableRegionIds);
    } else {
      this.setState({
        currentRegionId: null,
        currentProvider: null
      }, this.loadAvailableRegionIds);
    }
  };

  selectDefaultRegion = () => {
    const [defaultRegion] = this.regions.filter(r => r.default);
    this.selectRegion(defaultRegion || this.regions[0]);
  };
};

@inject(() => {
  const roles = new Roles();
  roles.fetch();

  return {roles};
})
@observer
class AWSRegionForm extends React.Component {
  static propTypes = {
    regionIds: PropTypes.array,
    region: PropTypes.object,
    isNew: PropTypes.bool,
    onInitialize: PropTypes.func,
    onCreate: PropTypes.func,
    onCancelCreate: PropTypes.func,
    onSubmit: PropTypes.func,
    onRemove: PropTypes.func,
    pending: PropTypes.bool
  };

  contextualSettingsFetchToken = {};

  state = {
    findUserVisible: false,
    findGroupVisible: false,
    groupSearchString: null,
    contextualSettingInitialValue: {},
    contextualSettingValue: {},
    contextualSettingValid: {}
  };

  cloudRegionFileShareMountsComponent;

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 5}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 19}
    },
    style: {marginBottom: 2}
  };

  formItemLayoutWideLabel = this.formItemLayout;

  defaultCheckBoxFormItemLayout = {
    wrapperCol: {
      xs: {
        span: 24,
        offset: 0
      },
      sm: {
        span: 19,
        offset: 5
      }
    },
    style: {marginBottom: 2}
  };

  cloudRegionFields = {
    AWS: [
      'regionId',
      'name',
      'default',
      'kmsKeyId',
      'kmsKeyArn',
      'corsRules',
      'policy',
      'storageLifecycleServiceProperties',
      'profile',
      'sshKeyName',
      'iamRole',
      'tempCredentialsRole',
      {
        key: 'backupDuration',
        visible: form => form.getFieldValue('versioningEnabled'),
        required: form => form.getFieldValue('versioningEnabled')
      },
      'versioningEnabled',
      'fileShareMounts',
      'mountStorageRule',
      'mountFileStorageRule',
      'mountCredentialsRule',
      'dnsHostedZoneBase',
      'dnsHostedZoneId',
      'dnsHostedZone',
      'globalDistributionUrl',
      'runShiftPolicy'
    ],
    AZURE: [
      'regionId',
      'name',
      'default',
      'resourceGroup',
      'storageAccount',
      'storageAccountKey',
      'azurePolicy',
      'corsRules',
      'subscription',
      'authFile',
      'sshPublicKeyPath',
      'meterRegionName',
      'azureApiUrl',
      'priceOfferId',
      'enterpriseAgreements',
      'fileShareMounts',
      'mountStorageRule',
      'mountFileStorageRule',
      'mountCredentialsRule',
      'dnsHostedZoneBase',
      'dnsHostedZoneId',
      'dnsHostedZone',
      'globalDistributionUrl',
      'runShiftPolicy'
    ],
    GCP: [
      'regionId',
      'name',
      'default',
      'authFile',
      'sshPublicKeyPath',
      'project',
      'applicationName',
      'tempCredentialsRole',
      'fileShareMounts',
      'customInstanceTypes',
      'corsRules',
      'mountStorageRule',
      'mountFileStorageRule',
      'mountCredentialsRule',
      'dnsHostedZoneBase',
      'dnsHostedZoneId',
      'dnsHostedZone',
      'policy',
      {
        key: 'backupDuration',
        visible: form => form.getFieldValue('versioningEnabled'),
        required: form => form.getFieldValue('versioningEnabled')
      },
      'versioningEnabled',
      'globalDistributionUrl',
      'runShiftPolicy'
    ],
    LOCAL: [
      'regionId',
      'name',
      'default',
      'runShiftPolicy',
      'customInstanceTypes',
      'user',
      'password'
    ]
  };

  cloudRegionContextualSettings = {
    AWS: [launchCommonMountsSetting],
    GCP: [launchCommonMountsSetting],
    AZURE: [launchCommonMountsSetting],
    LOCAL: [launchCommonMountsSetting]
  };

  @observable _modified = false;
  @observable _contextualSettingValid = true;

  @observable permissions = null;

  @observable userFind;
  @observable groupFind;
  selectedUser = null;
  selectedGroup = null;
  @observable usersToAddPermission = [];
  @observable groupsToAddPermission = [];
  @observable usersToRemovePermissions = [];
  @observable groupsToRemovePermissions = [];

  @computed
  get provider () {
    return this.props.region ? this.props.region.provider : null;
  }

  static Section = ({
    title,
    layout,
    className,
    children
  }) => {
    return (
      <Row
        className={
          classNames(
            styles.sectionContainer,
            className
          )
        }
      >
        <Col
          xs={layout.labelCol.xs.span}
          sm={layout.labelCol.sm.span}
          className={
            classNames(
              styles.sectionTitle,
              'cp-settings-form-item-label'
            )
          }
        >
          <span>
            {title}
          </span>
        </Col>
        <Col
          xs={layout.wrapperCol.xs.span}
          sm={layout.wrapperCol.sm.span}
        >
          {children}
        </Col>
      </Row>
    );
  };

  getFieldClassName = (field, defaultClassName) => {
    const classNames = defaultClassName ? [defaultClassName] : [];
    if (this.provider) {
      const fields = this.cloudRegionFields[this.provider] || [];
      const [cloudRegionField] = fields.filter(f => {
        return (typeof f === 'string' && f === field) ||
          (typeof f === 'object' && f.hasOwnProperty('key') && f.key === field);
      });
      if (!cloudRegionField) {
        classNames.push(styles.hiddenItem);
      } else if (cloudRegionField.hasOwnProperty('visible') &&
        !cloudRegionField.visible(this.props.form)) {
        classNames.push(styles.hiddenItem);
      }
    }
    return classNames.join(' ');
  };

  providerSupportsField = (field) => {
    if (this.provider) {
      const fields = this.cloudRegionFields[this.provider] || [];
      const [cloudRegionField] = fields.filter(f => {
        return (typeof f === 'string' && f === field) ||
          (typeof f === 'object' && f.hasOwnProperty('key') && f.key === field);
      });
      if (cloudRegionField && typeof cloudRegionField === 'string') {
        return true;
      } else if (cloudRegionField && cloudRegionField.hasOwnProperty('required') &&
        cloudRegionField.required(this.props.form)) {
        return true;
      }
    }
    return false;
  };

  getProviderContextualSettingConfiguration = (setting) => {
    if (this.provider) {
      const settings = this.cloudRegionContextualSettings[this.provider] || [];
      return settings.find((s) => s.setting === setting);
    }
    return undefined;
  };

  providerSupportsContextualSetting = (setting) => {
    return !!this.getProviderContextualSettingConfiguration(setting);
  };

  onFormFieldChanged = () => {
    if (!this.props.region || !this.props.form) {
      this._modified = false;
      return;
    }
    const check = (field, fn) => {
      if (this.provider) {
        const fields = this.cloudRegionFields[this.provider] || [];
        const [cloudRegionField] = fields.filter(f => {
          return (typeof f === 'string' && f === field) ||
            (typeof f === 'object' && f.hasOwnProperty('key') && f.key === field);
        });
        if (cloudRegionField && typeof cloudRegionField === 'string') {
          return fn(field);
        } else if (cloudRegionField && cloudRegionField.hasOwnProperty('visible') &&
          cloudRegionField.visible(this.props.form)) {
          return fn(field);
        }
      }
      return false;
    };
    const checkStringValue = (field) => {
      return (this.props.region[field] || '') !== (this.props.form.getFieldValue(field) || '');
    };
    const checkIntegerValue = (field) => {
      return +this.props.region[field] !== +this.props.form.getFieldValue(field);
    };
    const checkIPRangeValue = (field) => {
      const rangeStr = obj => obj ? `${obj.ipMin || ''}-${obj.ipMax || ''}` : '-';
      return rangeStr(this.props.region[field]) !== rangeStr(this.props.form.getFieldValue(field));
    };
    const checkBOOLValue = (field) => {
      return this.props.region[field] !== this.props.form.getFieldValue(field);
    };
    const checkJSONValue = (field) => {
      const initialValue = this.props.region[field];
      const value = this.props.form.getFieldValue(field);
      if (initialValue === value) {
        return false;
      }
      try {
        const initialValueStr = preProcessJSON(initialValue, true);
        return (initialValueStr || '') !== (value || '');
      } catch (__) {

      }
      return false;
    };
    const checkMounts = () => {
      return !CloudRegionFileShareMountsFormItem.fileShareMountsAreEqual(
        this.props.region.fileShareMounts, this.props.form.getFieldValue('fileShareMounts')
      );
    };
    const checkRunShiftPolicy = () => !runShiftPoliciesEqual(
      this.props.region.runShiftPolicy,
      this.props.form.getFieldValue('runShiftPolicy')
    );
    const isContextualSettingChanged = (setting) => {
      if (this.provider && this.providerSupportsContextualSetting(setting)) {
        const {contextualSettingInitialValue, contextualSettingValue} = this.state;
        const initial = contextualSettingInitialValue
          ? contextualSettingInitialValue[setting]
          : undefined;
        const current = contextualSettingValue
          ? contextualSettingValue[setting]
          : undefined;
        return (initial || '') !== (current || '');
      }
      return false;
    };
    const contextualSettingsChanged = () => {
      if (this.provider) {
        const allSettings = this.cloudRegionContextualSettings[this.provider] || [];
        for (let i = 0; i < allSettings.length; i += 1) {
          if (isContextualSettingChanged(allSettings[i].setting)) {
            return true;
          }
        }
      }
      return false;
    };
    this._modified = contextualSettingsChanged() ||
      check('regionId', checkStringValue) ||
      check('name', checkStringValue) ||
      check('globalDistributionUrl', checkStringValue) ||
      check('default', checkBOOLValue) ||
      check('kmsKeyId', checkStringValue) ||
      check('kmsKeyArn', checkStringValue) ||
      check('corsRules', checkJSONValue) ||
      check('mountStorageRule', checkStringValue) ||
      check('mountFileStorageRule', checkStringValue) ||
      check('mountCredentialsRule', checkStringValue) ||
      check('dnsHostedZoneBase', checkStringValue) ||
      check('dnsHostedZoneId', checkStringValue) ||
      check('policy', checkJSONValue) ||
      check('storageLifecycleServiceProperties', checkJSONValue) ||
      check('storageAccount', checkStringValue) ||
      check('storageAccountKey', checkStringValue) ||
      check('user', checkStringValue) ||
      check('password', checkStringValue) ||
      check('resourceGroup', checkStringValue) ||
      check('subscription', checkStringValue) ||
      check('authFile', checkStringValue) ||
      check('profile', checkStringValue) ||
      check('sshKeyName', checkStringValue) ||
      check('azurePolicy', checkIPRangeValue) ||
      check('iamRole', checkStringValue) ||
      check('tempCredentialsRole', checkStringValue) ||
      check('backupDuration', checkIntegerValue) ||
      check('versioningEnabled', checkBOOLValue) ||
      check('sshPublicKeyPath', checkStringValue) ||
      check('meterRegionName', checkStringValue) ||
      check('azureApiUrl', checkStringValue) ||
      check('priceOfferId', checkStringValue) ||
      check('enterpriseAgreements', checkBOOLValue) ||
      check('project', checkStringValue) ||
      check('applicationName', checkStringValue) ||
      check('customInstanceTypes', checkJSONValue) ||
      check('fileShareMounts', checkMounts) ||
      checkRunShiftPolicy();
  };

  @computed
  get permissionsModified () {
    return (this.usersToAddPermission && this.usersToAddPermission.length) ||
      (this.groupsToAddPermission && this.groupsToAddPermission.length) ||
      (this.usersToRemovePermissions && this.usersToRemovePermissions.length) ||
      (this.groupsToRemovePermissions && this.groupsToRemovePermissions.length);
  }

  @computed
  get modified () {
    return this._modified || this.permissionsModified;
  }

  @computed
  get contextualSettingsValid () {
    return this._contextualSettingValid;
  }

  isRemovingPermission = (sid) => {
    if (sid.principal) {
      return this.usersToRemovePermissions && this.usersToRemovePermissions.length &&
        this.usersToRemovePermissions.includes(sid.name);
    } else {
      return this.groupsToRemovePermissions && this.groupsToRemovePermissions.length &&
        this.groupsToRemovePermissions.includes(sid.name);
    }
  };

  @computed
  get permissionsDisplayList () {
    let permissions = [];
    if (this.permissions && this.permissions.loaded) {
      permissions = this.permissions.value && this.permissions.value.permissions
        ? this.permissions.value.permissions
        : [];
    }
    const filteredPermissions = permissions.filter(permission => permission.mask === 1);

    if (this.usersToAddPermission && this.usersToAddPermission.length) {
      this.usersToAddPermission.forEach(name => {
        filteredPermissions.push({
          sid: {
            name,
            principal: true
          }
        });
      });
    }
    if (this.groupsToAddPermission && this.groupsToAddPermission.length) {
      this.groupsToAddPermission.forEach(name => {
        filteredPermissions.push({
          sid: {
            name,
            principal: false
          }
        });
      });
    }

    return filteredPermissions.filter(permission => !this.isRemovingPermission(permission.sid));
  }

  @computed
  get addedUserPermissions () {
    const res = [];
    this.permissionsDisplayList.forEach(permission => {
      if (permission.sid.principal) {
        res.push(permission.sid.name);
      }
    });

    return res;
  }

  @computed
  get addedGroupPermissions () {
    const res = [];
    this.permissionsDisplayList.forEach(permission => {
      if (!permission.sid.principal) {
        res.push(permission.sid.name);
      }
    });

    return res;
  }

  submitPermissions = async (regionId = null) => {
    if (this.usersToAddPermission && this.usersToAddPermission.length) {
      await Promise.all(
        this.usersToAddPermission.map(name => this.grantPermission(name, true, regionId)),
        (error) => {
          message.error(error);
        }
      );
    }
    if (this.groupsToAddPermission && this.groupsToAddPermission.length) {
      await Promise.all(
        this.groupsToAddPermission.map(name => this.grantPermission(name, false, regionId)),
        (error) => {
          message.error(error);
        }
      );
    }
    if (this.usersToRemovePermissions && this.usersToRemovePermissions.length) {
      await Promise.all(
        this.usersToRemovePermissions.map(name => this.removePermission(name, true, regionId)),
        (error) => {
          message.error(error);
        }
      );
    }
    if (this.groupsToRemovePermissions && this.groupsToRemovePermissions.length) {
      await Promise.all(
        this.groupsToRemovePermissions.map(name => this.removePermission(name, false, regionId)),
        (error) => {
          message.error(error);
        }
      );
    }
    if (!regionId) {
      await this.fetchPermissions();
    }
  };

  handleSubmit = (e) => {
    e.preventDefault();
    if (!this._contextualSettingValid) {
      return;
    }
    this.props.form.validateFieldsAndScroll(async (err, values) => {
      if (/^azure$/i.test(this.provider) && !values.priceOfferId && !values.enterpriseAgreements) {
        message.error('Price Offer ID or Enterprise Agreement must be specified', 5);
        return;
      }
      this.cloudRegionFileShareMountsComponent &&
      this.cloudRegionFileShareMountsComponent.validate &&
      this.cloudRegionFileShareMountsComponent.validate();
      if (!err && CloudRegionFileShareMountsFormItem.validationPassed(values.fileShareMounts)) {
        values.storageLifecycleServiceProperties = buildSLSProperties(
          values.storageLifecycleServiceProperties
        );
        const {
          contextualSettingValue,
          contextualSettingInitialValue
        } = this.state;
        const settings = [...new Set([
          ...Object.keys(contextualSettingValue),
          ...Object.keys(contextualSettingInitialValue)
        ])];
        const contextualSettings = settings
          .map((setting) => {
            const config = this.getProviderContextualSettingConfiguration(setting);
            return {
              setting,
              value: contextualSettingValue
                ? contextualSettingValue[setting]
                : undefined,
              initial: contextualSettingInitialValue
                ? contextualSettingInitialValue[setting]
                : undefined,
              type: config ? config.type : undefined
            };
          })
          .filter((o) => (o.value || '') !== (o.initial || ''));
        if (this.props.isNew) {
          this.props.onCreate && await this.props.onCreate(values, contextualSettings);
        } else {
          this.props.onSubmit && await this.props.onSubmit(values, contextualSettings);
          await this.submitPermissions();
        }
      }
    });
  };

  jsonValidation = (rule, value, callback) => {
    if (!value) {
      callback();
      return;
    }
    try {
      JSON.parse(value);
    } catch (e) {
      callback(e.toString());
      return;
    }
    callback();
  };

  corsRulesEditor;
  policyEditor;
  customInstanceTypesEditor;

  initializeCorsRulesEditor = (editor) => {
    this.corsRulesEditor = editor;
  };

  initializePolicyEditor = (editor) => {
    this.policyEditor = editor;
  };

  initializeCustomInstanceTypesEditor = (editor) => {
    this.customInstanceTypesEditor = editor;
  };

  initializeCloudRegionFileShareMountsComponent = (component) => {
    this.cloudRegionFileShareMountsComponent = component;
  };

  unInitializeCloudRegionFileShareMountsComponent = () => {
    this.cloudRegionFileShareMountsComponent = undefined;
  };

  fetchPermissions = async () => {
    if (this.permissionsModified) {
      this.revertPermissions();
    }
    if (this.props.region && this.props.region.id) {
      this.permissions = new GrantGet(this.props.region.id, AWS_REGION_ITEM_TYPE);
      await this.permissions.fetch();
    } else {
      this.permissions = null;
    }
  };

  revertPermissions = () => {
    this.usersToAddPermission = [];
    this.groupsToAddPermission = [];
    this.usersToRemovePermissions = [];
    this.groupsToRemovePermissions = [];
  };

  grantPermission = async (name, principal, regionId = null) => {
    const request = new GrantPermission();
    await request.send({
      aclClass: AWS_REGION_ITEM_TYPE,
      id: regionId || this.props.region.id,
      mask: 1,
      principal,
      userName: name
    });

    if (request.error) {
      return Promise.reject(request.error);
    } else {
      return Promise.resolve();
    }
  };

  removePermission = async (name, principal, regionId = null) => {
    const request = new GrantRemove(
      regionId || this.props.region.id,
      AWS_REGION_ITEM_TYPE,
      name,
      principal
    );
    await request.fetch();

    if (request.error) {
      return Promise.reject(request.error);
    } else {
      return Promise.resolve();
    }
  };

  permissionRemoveClicked = (item) => {
    if (item.sid.principal) {
      if (this.usersToAddPermission.includes(item.sid.name)) {
        this.usersToAddPermission.splice(this.usersToAddPermission.indexOf(item.sid.name), 1);
      } else if (!this.usersToRemovePermissions.includes(item.sid.name)) {
        this.usersToRemovePermissions.push(item.sid.name);
      }
    } else {
      if (this.groupsToAddPermission.includes(item.sid.name)) {
        this.groupsToAddPermission.splice(this.groupsToAddPermission.indexOf(item.sid.name), 1);
      } else if (!this.groupsToRemovePermissions.includes(item.sid.name)) {
        this.groupsToRemovePermissions.push(item.sid.name);
      }
    }
  };

  openFindUserDialog = () => {
    this.selectedUser = null;
    this.setState({findUserVisible: true});
  };

  closeFindUserDialog = () => {
    this.setState({findUserVisible: false});
  };

  onSelectUser = () => {
    if (!this.usersToAddPermission.includes(this.selectedUser)) {
      this.usersToAddPermission.push(this.selectedUser);
    }
    this.closeFindUserDialog();
  };

  openFindGroupDialog = () => {
    this.selectedGroup = null;
    this.setState({findGroupVisible: true, groupSearchString: null});
  };

  closeFindGroupDialog = () => {
    this.setState({findGroupVisible: false, groupSearchString: null});
  };

  onSelectGroup = async () => {
    if (!this.groupsToAddPermission.includes(this.selectedGroup)) {
      this.groupsToAddPermission.push(this.selectedGroup);
    }

    this.closeFindGroupDialog();
  };

  findGroupDataSource = () => {
    const roles = (this.props.roles.loaded && this.state.groupSearchString) ? (
      (this.props.roles.value || [])
        .filter(r => r.name.toLowerCase().indexOf(this.state.groupSearchString.toLowerCase()) >= 0)
        .map(r => r.predefined ? r.name : this.splitRoleName(r.name))
        .filter(name => !this.addedGroupPermissions.includes(name))
    ) : [];
    if (this.groupFind && !this.groupFind.pending && !this.groupFind.error) {
      return [
        ...roles,
        ...(this.groupFind.value || [])
          .map(g => g)
          .filter(g => !this.addedGroupPermissions.includes(g.name))
      ];
    }
    return [...roles];
  };

  onGroupFindInputChanged = (value) => {
    this.selectedGroup = value;
    if (value && value.length) {
      this.groupFind = new GroupFind(value);
      this.groupFind.fetch();
    } else {
      this.groupFind = null;
    }
    this.setState({groupSearchString: value});
  };

  onUserFindInputChanged = (value) => {
    this.selectedUser = value;
    if (value && value.length) {
      this.userFind = new UserFind(value);
      this.userFind.fetch();
    } else {
      this.userFind = null;
    }
  };

  findUserDataSource = () => {
    if (this.userFind && !this.userFind.pending && !this.userFind.error) {
      return (this.userFind.value || []).map(user => user)
        .filter(u => !this.addedUserPermissions.includes(u.userName));
    }
    return [];
  };

  splitRoleName = (name) => {
    if (name && name.toLowerCase().indexOf('role_') === 0) {
      return name.substring('role_'.length);
    }
    return name;
  };

  renderUserName = (user) => {
    if (user.attributes) {
      const getAttributesValues = () => {
        const values = [];
        for (let key in user.attributes) {
          if (user.attributes.hasOwnProperty(key)) {
            values.push(user.attributes[key]);
          }
        }
        return values;
      };
      const attributesString = getAttributesValues().join(', ');
      return (
        <Row type="flex" style={{flexDirection: 'column'}}>
          <Row>{user.userName}</Row>
          <Row><span style={{fontSize: 'smaller'}}>{attributesString}</span></Row>
        </Row>
      );
    } else {
      return user.userName;
    }
  };

  renderPermissionsTable = () => {
    if (this.permissions && this.permissions.error) {
      return <Alert type="warning" message={this.permissions.error} />;
    }

    const rolesList = (this.props.roles.loaded ? (this.props.roles.value || []) : []).map(r => r);
    const getSidName = (name, principal) => {
      if (principal) {
        return <UserName userName={name} />;
      } else {
        const [role] = rolesList.filter(r => !r.predefined && r.name === name);
        if (role) {
          return this.splitRoleName(name);
        } else {
          return name;
        }
      }
    };
    const columns = [
      {
        key: 'icon',
        className: `${styles.permissionIcon} ${styles.permissionCell}`,
        render: (item) => {
          if (item.sid.principal) {
            return <Icon type="user" />;
          }
          return <Icon type="team" />;
        }
      },
      {
        dataIndex: 'sid.name',
        className: styles.permissionCell,
        key: 'name',
        render: (name, item) => getSidName(name, item.sid.principal)
      },
      {
        key: 'actions',
        className: `${styles.permissionActions} ${styles.permissionCell}`,
        render: (item) => (
          <Row>
            <Button
              type="danger"
              disabled={this.props.pending}
              onClick={() => this.permissionRemoveClicked(item)}
              size="small">
              <Icon type="delete" />
            </Button>
          </Row>
        )
      }
    ];
    const title = (
      <Row>
        <Col span={12}>
          <b>Groups and users</b>
        </Col>
        <Col span={12} style={{textAlign: 'right', paddingRight: 8}}>
          <span className={styles.permissionTableActions}>
            <Button disabled={this.props.pending} size="small" onClick={this.openFindUserDialog}>
              <Icon type="user-add" />
            </Button>
            <Button disabled={this.props.pending} size="small" onClick={this.openFindGroupDialog}>
              <Icon type="usergroup-add" />
            </Button>
          </span>
        </Col>
      </Row>
    );

    return (
      <Row style={{marginTop: 5}}>
        <Table
          className={styles.table}
          style={{
            maxHeight: 300,
            overflowY: 'auto'
          }}
          loading={!!(this.permissions && this.permissions.pending)}
          title={() => title}
          rowClassName={() => styles.permissionTableRow}
          showHeader={false}
          size="small"
          columns={columns}
          pagination={false}
          rowKey={(item) => item.sid.name}
          dataSource={this.permissionsDisplayList} />
        <Modal
          title="Select user"
          onCancel={this.closeFindUserDialog}
          cancelText="CANCEL"
          onOk={this.onSelectUser}
          visible={this.state.findUserVisible}>
          <AutoComplete
            value={this.selectedUser}
            optionLabelProp="text"
            style={{width: '100%'}}
            onChange={this.onUserFindInputChanged}
            placeholder="Enter the account name">
            {
              (this.findUserDataSource() || []).map(user => {
                return (
                  <AutoComplete.Option key={user.userName} text={user.userName}>
                    {this.renderUserName(user)}
                  </AutoComplete.Option>
                );
              })
            }
          </AutoComplete>
        </Modal>
        <Modal
          title="Select group"
          onCancel={this.closeFindGroupDialog}
          cancelText="CANCEL"
          onOk={this.onSelectGroup}
          visible={this.state.findGroupVisible}>
          <AutoComplete
            value={this.selectedGroup}
            style={{width: '100%'}}
            dataSource={this.findGroupDataSource()}
            onChange={this.onGroupFindInputChanged}
            placeholder="Enter the group name" />
        </Modal>
      </Row>
    );
  };

  onContextualSettingsChanged = () => {
    const {
      contextualSettingValid
    } = this.state;
    this._contextualSettingValid = !Object.values(contextualSettingValid || {}).some((v) => !v);
    this.onFormFieldChanged();
  };

  onChangeContextualSetting = (setting, value, valid) => {
    const {contextualSettingValue, contextualSettingValid} = this.state;
    this.setState({
      contextualSettingValue: {
        ...(contextualSettingValue || {}),
        [setting]: value
      },
      contextualSettingValid: {
        ...(contextualSettingValid || {}),
        [setting]: valid
      }
    }, this.onContextualSettingsChanged);
  };

  renderContextualSetting = (setting) => {
    const config = this.getProviderContextualSettingConfiguration(setting);
    if (config) {
      const {contextualSettingValue} = this.state;
      return (
        <CloudRegionContextualSetting
          disabled={this.props.pending}
          regionId={this.props.region.id}
          setting={setting}
          value={contextualSettingValue ? (contextualSettingValue[setting] || '') : ''}
          onChange={this.onChangeContextualSetting}
          title={config.title ? `${config.title}:` : `${setting}:`}
          type={config.type}
          {...this.formItemLayout}
        />
      );
    }
    return null;
  };

  render () {
    if (!this.props.region) {
      return null;
    }
    const {resetFields, getFieldDecorator} = this.props.form;
    const revertForm = () => {
      if (this.permissionsModified) {
        this.revertPermissions();
      }
      this.setState({
        contextualSettingValue: {
          ...this.state.contextualSettingInitialValue
        },
        contextualSettingValid: {}
      });
      resetFields();
    };
    const onCancel = () => {
      revertForm();
      this.props.onCancelCreate && this.props.onCancelCreate();
    };
    const onRemove = async () => {
      this.props.onRemove && await this.props.onRemove();
    };
    return (
      <div style={{width: '100%', flex: 1, display: 'flex', flexDirection: 'column'}}>
        <div
          style={{
            flex: 1,
            width: '100%',
            overflowY: 'auto',
            paddingRight: 10
          }}
        >
          <Form
            className="edit-region-form"
            layout="horizontal"
          >
            <Form.Item
              label="Region ID"
              required
              {...this.formItemLayout}
              className={this.getFieldClassName('regionId', 'edit-region-id-container')}>
              {getFieldDecorator('regionId', {
                initialValue: this.props.region.regionId,
                rules: [{required: true, message: 'Region id is required'}]
              })(
                <RegionIdSelector
                  style={{marginTop: 4}}
                  regions={(this.props.regionIds || [])}
                  provider={this.provider}
                  disabled={!this.props.isNew || this.props.pending}
                />
              )}
            </Form.Item>
            <Form.Item
              label="Provider"
              required
              className={styles.hiddenItem}
              {...this.formItemLayout}>
              {getFieldDecorator('provider', {
                initialValue: this.props.region.provider,
                rules: [{required: true, message: 'Provider is required'}]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Name"
              required
              {...this.formItemLayout}
              className={this.getFieldClassName('name', 'edit-region-name-container')}>
              {getFieldDecorator('name', {
                initialValue: this.props.region.name,
                rules: [{required: true, message: 'Region name is required'}]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              {...this.defaultCheckBoxFormItemLayout}>
              {getFieldDecorator('default', {
                valuePropName: 'checked',
                initialValue: this.props.region.default
              })(
                <Checkbox
                  disabled={this.props.region.default}>
                  This is default cloud region
                </Checkbox>
              )}
            </Form.Item>
            <Form.Item
              label="User"
              required={this.props.isNew && this.providerSupportsField('user')}
              className={this.getFieldClassName('user')}
              {...this.formItemLayout}>
              {getFieldDecorator('user', {
                initialValue: this.props.region.user,
                rules: [{
                  required: this.props.isNew && this.providerSupportsField('user'),
                  message: 'User is required'
                }]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Password"
              required={this.props.isNew && this.providerSupportsField('password')}
              className={this.getFieldClassName('password')}
              {...this.formItemLayout}>
              {getFieldDecorator('password', {
                initialValue: undefined,
                rules: [{required: this.props.isNew && this.providerSupportsField('password'), message: 'Password is required'}]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <AWSRegionForm.Section
              title="Mount rules:"
              layout={this.formItemLayout}
            >
              <Form.Item
                className={classNames(
                  styles.sectionFormItem,
                  styles.mountRules,
                  this.getFieldClassName('mountStorageRule')
                )}
                label="Object storages"
              >
                {getFieldDecorator('mountStorageRule', {
                  initialValue: this.props.region.mountStorageRule
                })(
                  <Select
                    size="small"
                    allowClear={false}
                    style={{marginTop: 4}}
                  >
                    <Select.Option value="NONE">None</Select.Option>
                    <Select.Option value="CLOUD">Same cloud</Select.Option>
                    <Select.Option value="ALL">All</Select.Option>
                  </Select>
                )}
              </Form.Item>
              <Form.Item
                className={classNames(
                  styles.sectionFormItem,
                  styles.mountRules,
                  this.getFieldClassName('mountFileStorageRule')
                )}
                label="File storages"
              >
                {getFieldDecorator('mountFileStorageRule', {
                  initialValue: this.props.region.mountFileStorageRule
                })(
                  <Select
                    size="small"
                    allowClear={false}
                    style={{marginTop: 4}}
                  >
                    <Select.Option value="NONE">None</Select.Option>
                    <Select.Option value="CLOUD">Same cloud</Select.Option>
                    <Select.Option value="ALL">All</Select.Option>
                  </Select>
                )}
              </Form.Item>
              <Form.Item
                className={classNames(
                  styles.sectionFormItem,
                  styles.mountRules,
                  this.getFieldClassName('mountCredentialsRule')
                )}
                label="Credentials"
              >
                {getFieldDecorator('mountCredentialsRule', {
                  initialValue: this.props.region.mountCredentialsRule
                })(
                  <Select
                    size="small"
                    allowClear={false}
                    style={{marginTop: 4}}
                  >
                    <Select.Option value="NONE">None</Select.Option>
                    <Select.Option value="CLOUD">Same cloud</Select.Option>
                    <Select.Option value="ALL">All</Select.Option>
                  </Select>
                )}
              </Form.Item>
            </AWSRegionForm.Section>
            {
              this.renderContextualSetting(launchCommonMountsSetting.setting)
            }
            <AWSRegionForm.Section
              title="DNS hosted zone:"
              layout={this.formItemLayout}
              className={this.getFieldClassName('dnsHostedZone')}
            >
              <Form.Item
                className={classNames(
                  styles.sectionFormItem,
                  styles.dnsHostedZone,
                  this.getFieldClassName('dnsHostedZoneBase')
                )}
                label="Base"
              >
                {getFieldDecorator('dnsHostedZoneBase', {
                  initialValue: this.props.region.dnsHostedZoneBase
                })(
                  <Input
                    size="small"
                    disabled={this.props.pending}
                  />
                )}
              </Form.Item>
              <Form.Item
                className={classNames(
                  styles.sectionFormItem,
                  styles.dnsHostedZone,
                  this.getFieldClassName('dnsHostedZoneId')
                )}
                label="Id"
              >
                {getFieldDecorator('dnsHostedZoneId', {
                  initialValue: this.props.region.dnsHostedZoneId
                })(
                  <Input
                    size="small"
                    disabled={this.props.pending}
                  />
                )}
              </Form.Item>
            </AWSRegionForm.Section>
            <Form.Item
              label="FS Mounts"
              {...this.formItemLayout}
              className={this.getFieldClassName(
                'fileShareMounts',
                'edit-region-elastic-file-share-mounts-container'
              )}
            >
              {getFieldDecorator('fileShareMounts', {
                initialValue: this.props.region.fileShareMounts
              })(
                <CloudRegionFileShareMountsFormItem
                  onMount={this.initializeCloudRegionFileShareMountsComponent}
                  onUnMount={this.unInitializeCloudRegionFileShareMountsComponent}
                  disabled={this.props.pending}
                  provider={this.provider} />
              )}
            </Form.Item>
            <Form.Item
              label="Storage account"
              required={this.providerSupportsField('storageAccount')}
              className={this.getFieldClassName('storageAccount')}
              {...this.formItemLayout}>
              {getFieldDecorator('storageAccount', {
                initialValue: this.props.region.storageAccount,
                rules: [{
                  required: this.providerSupportsField('storageAccount'),
                  message: 'Storage account is required'
                }]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Storage account key"
              required={this.props.isNew && this.providerSupportsField('storageAccountKey')}
              className={this.getFieldClassName('storageAccountKey')}
              {...this.formItemLayout}>
              {getFieldDecorator('storageAccountKey', {
                initialValue: undefined,
                rules: [{required: this.props.isNew && this.providerSupportsField('storageAccountKey'), message: 'Storage account key is required'}]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="SSH Public Key Path"
              required={this.providerSupportsField('sshPublicKeyPath')}
              {...this.formItemLayout}
              className={this.getFieldClassName('sshPublicKeyPath', 'edit-region-sshPublicKeyPath-container')}>
              {getFieldDecorator('sshPublicKeyPath', {
                initialValue: this.props.region.sshPublicKeyPath,
                rules: [{required: this.providerSupportsField('sshPublicKeyPath'), message: 'SSH Public Key Path is required'}]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Meter Region Name"
              required={this.providerSupportsField('meterRegionName')}
              {...this.formItemLayout}
              className={this.getFieldClassName('meterRegionName', 'edit-region-meterRegionName-container')}>
              {getFieldDecorator('meterRegionName', {
                initialValue: this.props.region.meterRegionName,
                rules: [{required: this.providerSupportsField('meterRegionName'), message: 'Meter Region Name is required'}]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Azure API Url"
              required={this.providerSupportsField('azureApiUrl')}
              {...this.formItemLayout}
              className={this.getFieldClassName('azureApiUrl', 'edit-region-azureApiUrl-container')}>
              {getFieldDecorator('azureApiUrl', {
                initialValue: this.props.region.azureApiUrl,
                rules: [{required: this.providerSupportsField('azureApiUrl'), message: 'Azure API Url is required'}]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Price Offer ID"
              {...this.formItemLayout}
              className={this.getFieldClassName('priceOfferId', 'edit-region-priceOfferId-container')}>
              {getFieldDecorator('priceOfferId', {
                initialValue: this.props.region.priceOfferId
              })(
                <Input
                  size="small"
                  disabled={this.props.pending}
                />
              )}
            </Form.Item>
            <Form.Item
              label="Enterprise Agreement"
              {...this.formItemLayout}
              className={this.getFieldClassName('enterpriseAgreements', 'edit-region-enterpriseAgreements-container')}>
              {getFieldDecorator('enterpriseAgreements', {
                valuePropName: 'checked',
                initialValue: this.props.region.enterpriseAgreements
              })(
                <Checkbox>
                  Enterprise Agreement
                </Checkbox>
              )}
            </Form.Item>
            <Form.Item
              label="Resource group"
              className={this.getFieldClassName('resourceGroup')}
              {...this.formItemLayout}>
              {getFieldDecorator('resourceGroup', {
                initialValue: this.props.region.resourceGroup
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Profile"
              {...this.formItemLayout}
              className={this.getFieldClassName('profile', 'edit-region-profile-container')}>
              {getFieldDecorator('profile', {
                initialValue: this.props.region.profile
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="SSH Key Name"
              required={this.providerSupportsField('sshKeyName')}
              {...this.formItemLayout}
              className={this.getFieldClassName('sshKeyName', 'edit-region-sshKeyName-container')}>
              {getFieldDecorator('sshKeyName', {
                initialValue: this.props.region.sshKeyName,
                rules: [{required: this.providerSupportsField('sshKeyName'), message: 'SSH Key Name is required'}]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Project"
              required={this.providerSupportsField('project')}
              {...this.formItemLayout}
              className={this.getFieldClassName('project', 'edit-region-project-container')}>
              {getFieldDecorator('project', {
                initialValue: this.props.region.project,
                rules: [{
                  required: this.providerSupportsField('project'),
                  message: 'Project is required'
                }]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Application name"
              required={this.providerSupportsField('applicationName')}
              {...this.formItemLayout}
              className={this.getFieldClassName('applicationName', 'edit-region-application-name-container')}>
              {getFieldDecorator('applicationName', {
                initialValue: this.props.region.applicationName,
                rules: [{
                  required: this.providerSupportsField('applicationName'),
                  message: 'Application name is required'
                }]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Main Service Role"
              {...this.formItemLayout}
              className={this.getFieldClassName('iamRole', 'edit-region-iam-role-container')}>
              {getFieldDecorator('iamRole', {
                initialValue: this.props.region.iamRole
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Temp Credentials Role"
              required={this.providerSupportsField('tempCredentialsRole')}
              {...this.formItemLayout}
              className={this.getFieldClassName('tempCredentialsRole', 'edit-region-tempCredentialsRole-container')}>
              {getFieldDecorator('tempCredentialsRole', {
                initialValue: this.props.region.tempCredentialsRole,
                rules: [{required: this.providerSupportsField('tempCredentialsRole'), message: 'Temp Credentials Role is required'}]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              className={this.getFieldClassName('versioningEnabled', 'edit-region-versioningEnabled-container')}
              {...this.defaultCheckBoxFormItemLayout}>
              {getFieldDecorator('versioningEnabled', {
                valuePropName: 'checked',
                initialValue: this.props.region.versioningEnabled
              })(
                <Checkbox>
                  Versioning enabled
                </Checkbox>
              )}
            </Form.Item>
            <Form.Item
              label="Backup Duration"
              required={this.providerSupportsField('backupDuration')}
              {...this.formItemLayout}
              className={
                this.getFieldClassName(
                  'backupDuration',
                  'edit-region-backupDuration-container'
                )
              }>
              {getFieldDecorator('backupDuration', {
                initialValue: this.props.region.backupDuration,
                rules: [{
                  required: this.providerSupportsField('backupDuration'),
                  message: 'Backup duration is required'
                }]
              })(
                <InputNumber
                  style={{width: '100%'}}
                  min={0}
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="KMS Key ID"
              required={this.providerSupportsField('kmsKeyId')}
              {...this.formItemLayout}
              className={this.getFieldClassName('kmsKeyId', 'edit-region-kmsKeyId-container')}>
              {getFieldDecorator('kmsKeyId', {
                initialValue: this.props.region.kmsKeyId,
                rules: [{required: this.providerSupportsField('kmsKeyId'), message: 'KMS Key ID is required'}]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="KMS Key Arn"
              required={this.providerSupportsField('kmsKeyArn')}
              {...this.formItemLayout}
              className={this.getFieldClassName('kmsKeyArn', 'edit-region-kmsKeyArn-container')}>
              {getFieldDecorator('kmsKeyArn', {
                initialValue: this.props.region.kmsKeyArn,
                rules: [{required: this.providerSupportsField('kmsKeyArn'), message: 'KMS Key Arn is required'}]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="CORS rules"
              hasFeedback
              {...this.formItemLayout}
              className={this.getFieldClassName('corsRules', 'edit-region-cors-rules-container')}>
              {getFieldDecorator('corsRules', {
                initialValue: this.props.region.corsRules,
                rules: [{
                  validator: this.jsonValidation
                }]
              })(
                <CodeEditorFormItem
                  ref={this.initializeCorsRulesEditor}
                  editorClassName={styles.codeEditor}
                  editorLanguage="application/json"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Policy"
              {...this.formItemLayout}
              className={this.getFieldClassName('azurePolicy', 'edit-region-subscription-container')}>
              {getFieldDecorator('azurePolicy', {
                initialValue: this.props.region.azurePolicy
              })(
                <IPRangeFormItem
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Subscription"
              {...this.formItemLayout}
              className={this.getFieldClassName('subscription', 'edit-region-subscription-container')}>
              {getFieldDecorator('subscription', {
                initialValue: this.props.region.subscription
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Auth file"
              {...this.formItemLayout}
              className={this.getFieldClassName('authFile', 'edit-region-subscription-container')}>
              {getFieldDecorator('authFile', {
                initialValue: this.props.region.authFile
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Policy"
              hasFeedback
              {...this.formItemLayout}
              className={this.getFieldClassName('policy', 'edit-region-policy-container')}>
              {getFieldDecorator('policy', {
                initialValue: this.props.region.policy,
                rules: [{
                  validator: this.jsonValidation
                }]
              })(
                <CodeEditorFormItem
                  ref={this.initializePolicyEditor}
                  editorClassName={styles.codeEditor}
                  editorLanguage="application/json"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Run shift policy"
              hasFeedback
              {...this.formItemLayout}
              className={
                this.getFieldClassName(
                  'runShiftPolicy',
                  'edit-region-run-shift-policy-container'
                )
              }
            >
              {getFieldDecorator('runShiftPolicy', {
                initialValue: this.props.region.runShiftPolicy
              })(
                <RunShiftPolicy
                  disabled={this.props.pending}
                />
              )}
            </Form.Item>
            <Form.Item
              label="SLS properties"
              hasFeedback
              {...this.formItemLayout}
              className={
                this.getFieldClassName(
                  'storageLifecycleServiceProperties',
                  'edit-region-sls-policy-container'
                )
              }
            >
              {getFieldDecorator('storageLifecycleServiceProperties', {
                initialValue: this.props.region.storageLifecycleServiceProperties,
                rules: [{
                  validator: this.jsonValidation
                }]
              })(
                <CodeEditorFormItem
                  ref={this.initializePolicyEditor}
                  editorClassName={styles.codeEditor}
                  editorLanguage="application/json"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Custom instance types"
              hasFeedback
              {...this.formItemLayout}
              className={this.getFieldClassName('customInstanceTypes', 'edit-region-custom-instance-types-container')}>
              {getFieldDecorator('customInstanceTypes', {
                initialValue: this.props.region.customInstanceTypes,
                rules: [{
                  validator: this.jsonValidation
                }]
              })(
                <CodeEditorFormItem
                  ref={this.initializeCustomInstanceTypesEditor}
                  editorClassName={styles.codeEditor}
                  editorLanguage="application/json"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="Distribution URL"
              {...this.formItemLayout}
              className={
                this.getFieldClassName(
                  'globalDistributionUrl',
                  'edit-region-distribution-url-container'
                )
              }
            >
              {getFieldDecorator('globalDistributionUrl', {
                initialValue: this.props.region.globalDistributionUrl
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Row type="flex">
              <Col
                xs={this.formItemLayout.labelCol.xs.span}
                sm={this.formItemLayout.labelCol.sm.span}
                className="cp-settings-form-item-label"
                style={{
                  textAlign: 'right',
                  paddingRight: 8,
                  paddingTop: 8,
                  lineHeight: '32px'
                }}>
                Permissions:
              </Col>
              <Col
                xs={this.formItemLayout.wrapperCol.xs.span}
                sm={this.formItemLayout.wrapperCol.sm.span}
              >
                {this.renderPermissionsTable()}
              </Col>
            </Row>
          </Form>
        </div>
        {
          this.props.isNew &&
          <Row className={styles.actions} type="flex" justify="end">
            <Button
              id="edit-region-form-cancel-button"
              size="small"
              onClick={onCancel}>Cancel</Button>
            <Button
              id="edit-region-form-create-button"
              disabled={!this.modified || !this.contextualSettingsValid}
              type="primary"
              size="small"
              onClick={this.handleSubmit}>Create</Button>
          </Row>
        }
        {
          !this.props.isNew &&
          <Row className={styles.actions} type="flex" justify="end">
            {
              this.props.region.default &&
              <Tooltip
                overlay="You cannot remove default AWS region. Set new default region first.">
                <Button
                  id="edit-region-form-remove-button"
                  disabled={this.props.region.default}
                  size="small"
                  type="danger"
                  style={{marginRight: 10}}><Icon type="info-circle" /> Remove</Button>
              </Tooltip>
            }
            {
              !this.props.region.default &&
              <Button
                id="edit-region-form-remove-button"
                disabled={this.props.region.default}
                size="small"
                type="danger"
                onClick={onRemove}
                style={{marginRight: 10}}>Remove</Button>
            }
            <Button
              id="edit-region-form-cancel-button"
              disabled={!this.modified}
              size="small"
              onClick={() => revertForm()}>Revert</Button>
            <Button
              id="edit-region-form-ok-button"
              disabled={!this.modified || !this.contextualSettingsValid}
              type="primary"
              size="small"
              onClick={this.handleSubmit}>Save</Button>
          </Row>
        }
      </div>
    );
  }

  componentDidUpdate (prevProps) {
    if (prevProps.isNew !== this.props.isNew) {
      this.rebuild();
    } else if (prevProps.region && !this.props.region) {
      this.rebuild();
    } else if (!prevProps.region && this.props.region) {
      this.rebuild();
    } else if (
      prevProps.region &&
      this.props.region &&
      prevProps.region.id !== this.props.region.id
    ) {
      this.rebuild();
    }
  }

  rebuildContextualSettings = async () => {
    this.contextualSettingsFetchToken = {};
    const token = this.contextualSettingsFetchToken;
    const commitChanges = (state) => {
      if (token === this.contextualSettingsFetchToken) {
        this.setState(state, this.onContextualSettingsChanged);
      }
    };
    const {provider} = this;
    const {region} = this.props;
    if (provider && region) {
      const settings = this.cloudRegionContextualSettings[provider] || [];
      if (region.isNew) {
        commitChanges({
          contextualSettingInitialValue: {},
          contextualSettingValue: {},
          contextualSettingValid: {}
        });
      } else {
        const fetchSettingValue = (setting) => fetchContextualSettingValue(
          setting.setting,
          region.id
        );
        const settingsValues = await Promise.all(settings.map(fetchSettingValue));
        const value = settings.reduce((result, setting, idx) => ({
          ...result,
          [setting.setting]: settingsValues[idx]
        }), {});
        commitChanges({
          contextualSettingInitialValue: {
            ...value
          },
          contextualSettingValue: {
            ...value
          },
          contextualSettingValid: {}
        });
      }
    } else {
      commitChanges({
        contextualSettingInitialValue: {},
        contextualSettingValue: {},
        contextualSettingValid: {}
      });
    }
  };

  rebuild = () => {
    this.props.form.resetFields();
    if (this.corsRulesEditor) {
      this.corsRulesEditor.reset();
    }
    if (this.policyEditor) {
      this.policyEditor.reset();
    }
    if (this.customInstanceTypesEditor) {
      this.customInstanceTypesEditor.reset();
    }
    this.onFormFieldChanged();
    this.fetchPermissions();
    this.rebuildContextualSettings();
  };

  componentDidMount () {
    this.props.onInitialize && this.props.onInitialize(this);
    this.rebuild();
  }
}

@observer
class IPRangeFormItem extends React.Component {
  static propTypes = {
    value: PropTypes.shape({
      ipMin: PropTypes.string,
      ipMax: PropTypes.string
    }),
    onChange: PropTypes.func,
    disabled: PropTypes.bool
  };

  state = {
    ipMin: null,
    ipMax: null
  };

  onChange = () => {
    this.props.onChange && this.props.onChange(this.state);
  };

  onChangeMin = (e) => {
    this.setState({
      ipMin: e.target.value
    }, this.onChange);
  };

  onChangeMax = (e) => {
    this.setState({
      ipMax: e.target.value
    }, this.onChange);
  };

  render () {
    return (
      <Row type="flex" align="middle" style={{marginTop: 1}}>
        <span style={{marginRight: 5}}>IP min:</span>
        <Input disabled={this.props.disabled} value={this.state.ipMin} onChange={this.onChangeMin} style={{width: 300}} />
        <span style={{marginLeft: 5, marginRight: 5}}>IP max:</span>
        <Input disabled={this.props.disabled} value={this.state.ipMax} onChange={this.onChangeMax} style={{width: 300}} />
      </Row>
    );
  }

  componentDidMount () {
    if (this.props.value) {
      this.setState({
        ipMin: this.props.value.ipMin,
        ipMax: this.props.value.ipMax
      });
    } else {
      this.setState({
        ipMin: '',
        ipMax: ''
      });
    }
  }

  componentWillReceiveProps (nextProps) {
    if (!!nextProps.value !== !!this.props.value ||
      (!!nextProps.value &&
        (nextProps.value.ipMin !== this.props.value.ipMin ||
          nextProps.value.ipMax !== this.props.value.ipMax))) {
      if (nextProps.value) {
        this.setState({
          ipMin: nextProps.value.ipMin,
          ipMax: nextProps.value.ipMax
        });
      } else {
        this.setState({
          ipMin: '',
          ipMax: ''
        });
      }
    }
  }
}

const MountOptions = {
  NFS: 'NFS',
  SMB: 'SMB',
  LUSTRE: 'LUSTRE'
};

const DefaultMountOptions = {
  AWS: MountOptions.NFS,
  GCP: MountOptions.NFS,
  AZURE: MountOptions.SMB
};

const MountRootFormat = {
  AWS: {
    [MountOptions.NFS]: {
      mask: /^[^:]+(:[\d]+)?$/i,
      format: 'server:port'
    },
    [MountOptions.SMB]: {
      mask: /^[^:]+(:[\d]+)?$/i,
      format: 'server:port'
    },
    [MountOptions.LUSTRE]: {
      mask: /^([^:]+(:\d+)?@\w+)(:([^:]+(:\d+)?@\w+))*(:\/)[^/]+$/i,
      format: 'host@LND-protocol(:/host@LND-protocol:/...):/root'
    }
  },
  AZURE: {
    [MountOptions.NFS]: {
      mask: /^[^:]+(:[\d]+)?$/i,
      format: 'server:port'
    },
    [MountOptions.SMB]: {
      mask: /^[^:]+(:[\d]+)?$/i,
      format: 'server:port'
    },
    [MountOptions.LUSTRE]: {
      mask: /^([^:]+(:\d+)?@\w+)(:([^:]+(:\d+)?@\w+))*(:\/)[^/]+$/i,
      format: 'host@LND-protocol(:/host@LND-protocol:/...):/root'
    }
  },
  GCP: {
    [MountOptions.NFS]: {
      mask: /^[^:]+(:[\d]+)?:\/.+$/i,
      format: 'server:port:/root'
    },
    [MountOptions.SMB]: {
      mask: /^[^:]+(:[\d]+)?:\/.+$/i,
      format: 'server:port:/root'
    },
    [MountOptions.LUSTRE]: {
      mask: /^([^:]+(:\d+)?@\w+)(:([^:]+(:\d+)?@\w+))*(:\/)[^/]+$/i,
      format: 'host@LND-protocol(:/host@LND-protocol:/...):/root'
    }
  }
};

@observer
class CloudRegionFileShareMountFormItem extends React.Component {
  static propTypes = {
    index: PropTypes.number,
    value: PropTypes.object,
    onChange: PropTypes.func,
    onExpand: PropTypes.func,
    onDelete: PropTypes.func,
    expanded: PropTypes.bool,
    disabled: PropTypes.bool,
    onMount: PropTypes.func,
    onUnMount: PropTypes.func,
    provider: PropTypes.string
  };

  state = {
    id: undefined,
    regionId: undefined,
    mountRoot: undefined,
    mountType: undefined,
    mountOptions: undefined,
    mountRootError: null,
    mountTypeValid: true
  };

  static valuesAreEqual = (valueA, valueB) => {
    if (!!valueA !== !!valueB) {
      return false;
    }
    if (!valueA && !valueB) {
      return true;
    }
    return valueA.id === valueB.id &&
      valueA.regionId === valueB.regionId &&
      valueA.mountRoot === valueB.mountRoot &&
      valueA.mountType === valueB.mountType &&
      valueB.mountOptions === valueB.mountOptions;
  };

  componentWillReceiveProps (nextProps, nextContext) {
    if (
      !CloudRegionFileShareMountFormItem.valuesAreEqual(this.state, nextProps.value) ||
      nextProps.provider !== this.props.provider
    ) {
      this.updateState(nextProps.value, nextProps.provider);
    }
  }

  componentDidMount () {
    this.updateState(this.props.value, this.props.provider);
    this.props.onMount && this.props.onMount(this);
  }

  componentWillUnmount () {
    this.props.onUnMount && this.props.onUnMount(this);
  }

  updateState = (newValue, provider) => {
    if (!newValue) {
      this.setState({
        id: undefined,
        regionId: undefined,
        mountRoot: undefined,
        mountType: undefined,
        mountOptions: undefined,
        mountRootError: null,
        mountTypeValid: true
      }, this.validate);
    } else {
      this.setState({
        id: newValue.id,
        regionId: newValue.regionId,
        mountRoot: newValue.mountRoot,
        mountType: newValue.mountType,
        mountOptions: newValue.mountOptions,
        mountRootError: this.mountRootValidationError(newValue.mountRoot, provider),
        mountTypeValid: !!newValue.mountType
      }, this.validate);
    }
  };

  onChange = () => {
    this.validate();
    this.props.onChange && this.props.onChange(this.state);
  };

  mountRootValidationError = (value, provider) => {
    if (!value || value.trim().length === 0) {
      return 'Host is required';
    }
    const option = this.state.mountType || DefaultMountOptions[provider];
    if (
      MountRootFormat.hasOwnProperty(provider) &&
      MountRootFormat[provider].hasOwnProperty(option) &&
      !MountRootFormat[provider][option].mask.test(value)
    ) {
      return `Host should be in "${MountRootFormat[provider][option].format}" format`;
    }
    return null;
  };

  validate = (updateState = true) => {
    if (updateState) {
      this.setState({
        mountRootError: this.mountRootValidationError(this.state.mountRoot, this.props.provider),
        mountTypeValid: !!this.state.mountType
      });
    }
    return !!this.state.mountType &&
      !this.mountRootValidationError(this.state.mountRoot, this.props.provider);
  };

  onChangeMountRoot = (e) => {
    this.setState({
      mountRoot: e.target.value
    }, this.onChange);
  };

  onChangeMountType = (type) => {
    this.setState({
      mountType: type
    }, this.onChange);
  };

  onChangeMountOptions = (e) => {
    this.setState({
      mountOptions: e.target.value
    }, this.onChange);
  };

  render () {
    return (
      <Row
        className={
          classNames(
            styles.fileShareMountRow,
            'cp-divider',
            'bottom',
            {'cp-even-row': this.props.index % 2 === 0}
          )
        }
        style={{padding: 3}}>
        <Row type="flex" align="top">
          <span style={{width: 50}}>Host:</span>
          <Input
            style={{flex: 1, marginTop: 1}}
            className={
              classNames(
                {
                  'cp-error': this.state.mountRootError
                }
              )
            }
            disabled={this.props.disabled || !!this.state.id}
            placeholder={
              MountRootFormat.hasOwnProperty(this.props.provider)
                ? MountRootFormat[this.props.provider].format
                : null
            }
            value={this.state.mountRoot}
            onChange={this.onChangeMountRoot} />
          <span style={{marginLeft: 5, marginRight: 5}}>Type:</span>
          <Select
            style={{width: 200, marginTop: 1}}
            className={
              classNames(
                {
                  'cp-error': !this.state.mountTypeValid
                }
              )
            }
            disabled={this.props.disabled || !!this.state.id}
            value={this.state.mountType}
            onChange={this.onChangeMountType}>
            {
              Object.keys(MountOptions)
                .map(key => <Select.Option key={key} value={key} title={key}>{key}</Select.Option>)
            }
          </Select>
          <Button
            style={{marginTop: 3, marginLeft: 5}}
            disabled={this.props.disabled}
            onClick={this.props.onExpand}
            size="small">
            ...
          </Button>
          <Button
            style={{marginTop: 3, marginLeft: 5}}
            disabled={this.props.disabled}
            type="danger"
            size="small"
            onClick={this.props.onDelete}>
            <Icon type="close" />
          </Button>
        </Row>
        {
          this.state.mountRootError &&
          <Row
            className="cp-error"
            style={{
              lineHeight: '12px',
              paddingLeft: 50
            }}
          >
            {this.state.mountRootError}
          </Row>
        }
        {
          this.props.expanded &&
          <Row type="flex" align="top" style={{marginBottom: 10}}>
            <span style={{width: 50}}>Options:</span>
            <Input.TextArea
              style={{flex: 1}}
              rows={3}
              disabled={this.props.disabled || !!this.state.id}
              value={this.state.mountOptions}
              onChange={this.onChangeMountOptions} />
          </Row>
        }
      </Row>
    );
  }
}

@observer
class CloudRegionFileShareMountsFormItem extends React.Component {
  static propTypes = {
    value: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
    onChange: PropTypes.func,
    disabled: PropTypes.bool,
    provider: PropTypes.string,
    onMount: PropTypes.func,
    onUnMount: PropTypes.func
  };

  state = {
    mounts: [],
    expandedIndex: -1
  };

  refComponents = {};

  static fileShareMountsAreEqual = (mountsA, mountsB) => {
    mountsA = mountsA || [];
    mountsB = mountsB || [];
    if (mountsA.length !== mountsB.length) {
      return false;
    }
    const filterFn = CloudRegionFileShareMountFormItem.valuesAreEqual;
    for (let i = 0; i < mountsA.length; i++) {
      const equals = mountsB.filter(target => filterFn(mountsA[i], target));
      if (equals.length !== 1) {
        return false;
      }
    }
    return true;
  };

  onChange = () => {
    this.props.onChange && this.props.onChange(this.state.mounts);
  };

  updateState = (newMounts) => {
    this.setState({
      mounts: (newMounts || []).map(m => m)
    });
  };

  componentDidMount () {
    this.updateState(this.props.value);
    this.props.onMount && this.props.onMount(this);
  }

  componentWillUnmount () {
    this.props.onUnMount && this.props.onUnMount(this);
  }

  componentWillReceiveProps (nextProps, nextContext) {
    if (!CloudRegionFileShareMountsFormItem.fileShareMountsAreEqual(
      this.state.mounts,
      nextProps.value
    )) {
      this.updateState(nextProps.value);
    }
  }

  childComponentMounted = (key) => (component) => {
    this.refComponents[key] = component;
  };

  childComponentUnMounted = (key) => () => {
    if (this.refComponents[key]) {
      delete this.refComponents[key];
    }
  };

  static validationPassed = (mounts) =>
    (mounts || []).filter(v => !v.mountRoot || !v.mountType).length === 0;

  validate = () => {
    Object.keys(this.refComponents)
      .forEach(key =>
        this.refComponents[key] &&
        this.refComponents[key].validate &&
        this.refComponents[key].validate()
      );
  };

  onFileShareMountChanged = (index) => (newValue) => {
    const mounts = this.state.mounts;
    mounts.splice(index, 1, newValue);
    this.setState({
      mounts
    }, this.onChange);
  };

  onFileShareMountDelete = (index) => () => {
    const mounts = this.state.mounts;
    mounts.splice(index, 1);
    this.setState({
      mounts,
      expandedIndex: -1
    }, this.onChange);
  };

  onAddFileShareMount = () => {
    const mounts = this.state.mounts;
    mounts.push({
      mountType: DefaultMountOptions[this.props.provider]
    });
    this.setState({
      mounts,
      expandedIndex: -1
    }, this.onChange);
  };

  onExpand = (index) => () => {
    this.setState({
      expandedIndex: this.state.expandedIndex === index ? -1 : index
    });
  };

  render () {
    return (
      <Row>
        <div>
          {
            this.state.mounts.map((mount, index) => {
              return (
                <CloudRegionFileShareMountFormItem
                  key={index}
                  value={mount}
                  index={index}
                  disabled={this.props.disabled}
                  onChange={this.onFileShareMountChanged(index)}
                  onDelete={this.onFileShareMountDelete(index)}
                  onExpand={this.onExpand(index)}
                  onMount={this.childComponentMounted(index)}
                  onUnMount={this.childComponentUnMounted(index)}
                  expanded={this.state.expandedIndex === index}
                  provider={this.props.provider}
                />
              );
            })
          }
        </div>
        <Row type="flex" style={{minHeight: 32}} align="middle">
          <Button
            disabled={this.props.disabled}
            size="small"
            onClick={this.onAddFileShareMount}>
            <Icon type="plus" />Add file share mount
          </Button>
        </Row>
      </Row>
    );
  }
}
