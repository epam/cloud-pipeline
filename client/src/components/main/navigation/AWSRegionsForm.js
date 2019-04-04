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
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import LoadingView from '../../special/LoadingView';
import {SplitPanel} from '../../special/splitPanel';
import {
  Alert,
  Button,
  Checkbox,
  Dropdown,
  Icon,
  Input,
  InputNumber,
  Modal,
  Menu,
  message,
  Form,
  Row,
  Select,
  Table,
  Tooltip, Col, AutoComplete
} from 'antd';
import AWSRegionUpdate from '../../../models/dataStorage/AWSRegionUpdate';
import AWSRegionDelete from '../../../models/dataStorage/AWSRegionDelete';
import AWSRegionCreate from '../../../models/dataStorage/AWSRegionCreate';
import FileShareMountUpdate from '../../../models/fileShareMount/FileShareMountUpdate';
import FileShareMountDelete from '../../../models/fileShareMount/FileShareMountDelete';
import GrantGet from '../../../models/grant/GrantGet';
import GrantPermission from '../../../models/grant/GrantPermission';
import GrantRemove from '../../../models/grant/GrantRemove';
import Roles from '../../../models/user/Roles';
import UserFind from '../../../models/user/UserFind';
import GroupFind from '../../../models/user/GroupFind';
import UserName from '../../special/UserName';
import CodeEditorFormItem from '../../special/CodeEditorFormItem';
import AWSRegionTag from '../../special/AWSRegionTag';
import highlightText from '../../special/highlightText';
import styles from './AWSRegionsForm.css';

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

@inject('awsRegions', 'availableCloudRegions', 'cloudProviders')
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
    newRegion: null
  };

  @observable awsRegionForm;
  @observable awsRegionIds;

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
            policy: preProcessJSON(r.policy)
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
    if (this.awsRegionIds.loaded && this.props.awsRegions.loaded) {
      const notAvailableRegions = (this.props.awsRegions.value || [])
        .filter(r => (!!this.state.newRegion && r.provider === this.state.newRegion) ||
          (!this.state.newRegion && r.id !== this.state.currentRegionId && r.provider === this.state.currentProvider))
        .map(r => r.regionId);
      const available = (this.awsRegionIds.value || [])
        .map(r => r)
        .filter(r => notAvailableRegions.indexOf(r) === -1);
      available.sort();
      return available;
    }
    return [];
  }

  @computed
  get currentRegion () {
    return this.regions.filter(r => r.id === this.state.currentRegionId)[0];
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
          if (region.isNew) {
            return <i>{name} ({this.state.newRegion})</i>;
          }
          return (
            <span>
              <AWSRegionTag regionUID={region.regionId} />
              {highlightText(name, this.state.search)} ({region.provider})
            </span>
          );
        }
      }
    ];
    let data = this.regions;
    if (this.state.newRegion) {
      data = [{id: -1, isNew: true, name: 'New cloud region'}, ...data];
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
          (region) =>
            (!this.state.newRegion && region.id === this.state.currentRegionId) ||
            (region.isNew && this.state.newRegion)
              ? `${styles.regionRow} ${styles.selected}`
              : styles.regionRow
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

  onSaveRegion = async (region) => {
    const fileShareMounts = region.fileShareMounts;
    region.fileShareMounts = undefined;
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
      hide();
      await this.props.awsRegions.fetch();
      if (this.awsRegionForm) {
        this.awsRegionForm.rebuild();
      }
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

  onCreateRegion = async (region) => {
    region.id = undefined;
    region.provider = this.state.newRegion;
    const fileShareMounts = region.fileShareMounts;
    region.fileShareMounts = undefined;
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
        <Menu onClick={({key}) => this.onAddNewRegionClicked(key)}>
          {
            this.cloudProviders.map(c => {
              return (
                <Menu.Item key={c}>
                  {c}
                </Menu.Item>
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

  render () {
    if (this.props.awsRegions.pending && !this.props.awsRegions.loaded) {
      return <LoadingView />;
    }
    if (this.props.awsRegions.error) {
      return <Alert type="error" message={this.props.awsRegions.error} />;
    }

    const AWSRegionFormComponent = this.awsRegionFormComponent;

    return (
      <div>
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
                pxDefault: 175
              }
            }
          ]}>
          <div key="regions">
            {this.renderAwsRegionsTable()}
          </div>
          <div
            key="content"
            style={{
              height: '50vh',
              display: 'flex',
              flexDirection: 'column'
            }}>
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
              } />
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

  componentDidMount () {
    this.props.onInitialize && this.props.onInitialize(this);
  }

  selectRegion = (region) => {
    if (region) {
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

  componentDidUpdate () {
    if (!this.state.currentRegionId && this.regions.length > 0) {
      this.selectDefaultRegion();
    }
  }
}

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

  state = {
    findUserVisible: false,
    findGroupVisible: false,
    groupSearchString: null
  };

  cloudRegionFileShareMountsComponent;

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 3}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 21}
    },
    style: {marginBottom: 2}
  };

  defaultCheckBoxFormItemLayout = {
    wrapperCol: {
      xs: {
        span: 24,
        offset: 0
      },
      sm: {
        span: 14,
        offset: 3
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
      'profile',
      'sshKeyName',
      'tempCredentialsRole',
      {
        key: 'backupDuration',
        visible: form => form.getFieldValue('versioningEnabled'),
        required: form => form.getFieldValue('versioningEnabled')
      },
      'versioningEnabled',
      'fileShareMounts'
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
      'fileShareMounts'
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
      'fileShareMounts'
    ]
  };

  @observable _modified = false;

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

  getFieldClassName = (field, defaultClassName) => {
    const classNames = defaultClassName ? [defaultClassName] : [];
    if (this.provider) {
      const [cloudRegionField] = this.cloudRegionFields[this.provider].filter(f => {
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
      const [cloudRegionField] = this.cloudRegionFields[this.provider].filter(f => {
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

  onFormFieldChanged = () => {
    if (!this.props.region || !this.props.form) {
      this._modified = false;
      return;
    }
    const check = (field, fn) => {
      if (this.provider) {
        const [cloudRegionField] = this.cloudRegionFields[this.provider].filter(f => {
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
        return initialValueStr !== value;
      } catch (__) {

      }
      return false;
    };
    const checkMounts = () => {
      return !CloudRegionFileShareMountsFormItem.fileShareMountsAreEqual(
        this.props.region.fileShareMounts, this.props.form.getFieldValue('fileShareMounts')
      );
    };
    this._modified = check('regionId', checkStringValue) ||
      check('name', checkStringValue) ||
      check('default', checkBOOLValue) ||
      check('kmsKeyId', checkStringValue) ||
      check('kmsKeyArn', checkStringValue) ||
      check('corsRules', checkJSONValue) ||
      check('policy', checkJSONValue) ||
      check('storageAccount', checkStringValue) ||
      check('storageAccountKey', checkStringValue) ||
      check('resourceGroup', checkStringValue) ||
      check('subscription', checkStringValue) ||
      check('authFile', checkStringValue) ||
      check('profile', checkStringValue) ||
      check('sshKeyName', checkStringValue) ||
      check('azurePolicy', checkIPRangeValue) ||
      check('tempCredentialsRole', checkStringValue) ||
      check('backupDuration', checkIntegerValue) ||
      check('versioningEnabled', checkBOOLValue) ||
      check('sshPublicKeyPath', checkStringValue) ||
      check('meterRegionName', checkStringValue) ||
      check('azureApiUrl', checkStringValue) ||
      check('priceOfferId', checkStringValue) ||
      check('project', checkStringValue) ||
      check('applicationName', checkStringValue) ||
      check('fileShareMounts', checkMounts);
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
    this.props.form.validateFieldsAndScroll(async (err, values) => {
      this.cloudRegionFileShareMountsComponent &&
      this.cloudRegionFileShareMountsComponent.validate &&
      this.cloudRegionFileShareMountsComponent.validate();
      if (!err && CloudRegionFileShareMountsFormItem.validationPassed(values.fileShareMounts)) {
        if (this.props.isNew) {
          this.props.onCreate && await this.props.onCreate(values);
        } else {
          this.props.onSubmit && await this.props.onSubmit(values);
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

  initializeCorsRulesEditor = (editor) => {
    this.corsRulesEditor = editor;
  };

  initializePolicyEditor = (editor) => {
    this.policyEditor = editor;
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

  render () {
    if (!this.props.region) {
      return null;
    }
    const {resetFields, getFieldDecorator} = this.props.form;
    const revertForm = () => {
      if (this.permissionsModified) {
        this.revertPermissions();
      }
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
        <div style={{flex: 1, width: '100%', overflowY: 'auto'}}>
          <Form className="edit-region-form" layout="horizontal">
            <Form.Item
              label="Region ID"
              required
              {...this.formItemLayout}
              className={this.getFieldClassName('regionId', 'edit-region-id-container')}>
              {getFieldDecorator('regionId', {
                initialValue: this.props.region.regionId,
                rules: [{required: true, message: 'Region id is required'}]
              })(
                <Select
                  size="small"
                  showSearch
                  allowClear={false}
                  placeholder="Region ID"
                  optionFilterProp="children"
                  style={{marginTop: 4}}
                  filterOption={
                    (input, option) =>
                    option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0}
                  disabled={!this.props.isNew || this.props.pending}>
                  {
                    (this.props.regionIds || []).map(r => {
                      return (
                        <Select.Option key={r} value={r}>
                          <AWSRegionTag
                            provider={this.provider}
                            regionUID={r} style={{marginRight: 5}} />{r}
                        </Select.Option>
                      );
                    })
                  }
                </Select>
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
              label="FS Mounts"
              {...this.formItemLayout}
              className={this.getFieldClassName('fileShareMounts', 'edit-region-elastic-file-share-mounts-container')}>
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
                rules: [{required: this.providerSupportsField('storageAccount'), message: 'Storage account is required'}]
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
              required={this.providerSupportsField('priceOfferId')}
              {...this.formItemLayout}
              className={this.getFieldClassName('priceOfferId', 'edit-region-priceOfferId-container')}>
              {getFieldDecorator('priceOfferId', {
                initialValue: this.props.region.priceOfferId,
                rules: [{required: this.providerSupportsField('priceOfferId'), message: 'Price Offer ID is required'}]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
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
            <Row type="flex">
              <Col
                xs={24}
                sm={3}
                style={{
                  textAlign: 'right',
                  paddingRight: 8,
                  paddingTop: 8,
                  lineHeight: '32px',
                  color: 'rgba(0, 0, 0, 0.85)'
                }}>
                Permissions:
              </Col>
              <Col xs={24} sm={21}>
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
              disabled={!this.modified}
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
              disabled={!this.modified}
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
    } else if (prevProps.region && this.props.region && prevProps.region.id !== this.props.region.id) {
      this.rebuild();
    }
  }

  rebuild = () => {
    this.props.form.resetFields();
    if (this.corsRulesEditor) {
      this.corsRulesEditor.reset();
    }
    if (this.policyEditor) {
      this.policyEditor.reset();
    }
    this.onFormFieldChanged();
    this.fetchPermissions();
  };

  componentDidMount () {
    this.props.onInitialize && this.props.onInitialize(this);
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
  SMB: 'SMB'
};

const DefaultMountOptions = {
  AWS: MountOptions.NFS,
  GCP: MountOptions.NFS,
  AZURE: MountOptions.SMB
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
    onUnMount: PropTypes.func
  };

  state = {
    id: undefined,
    regionId: undefined,
    mountRoot: undefined,
    mountType: undefined,
    mountOptions: undefined,
    mountRootValid: true,
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
    if (!CloudRegionFileShareMountFormItem.valuesAreEqual(this.state, nextProps.value)) {
      this.updateState(nextProps.value);
    }
  }

  componentDidMount () {
    this.updateState(this.props.value);
    this.props.onMount && this.props.onMount(this);
  }

  componentWillUnmount () {
    this.props.onUnMount && this.props.onUnMount(this);
  }

  updateState = (newValue) => {
    if (!newValue) {
      this.setState({
        id: undefined,
        regionId: undefined,
        mountRoot: undefined,
        mountType: undefined,
        mountOptions: undefined,
        mountRootValid: true,
        mountTypeValid: true
      });
    } else {
      this.setState({
        id: newValue.id,
        regionId: newValue.regionId,
        mountRoot: newValue.mountRoot,
        mountType: newValue.mountType,
        mountOptions: newValue.mountOptions,
        mountRootValid: !!newValue.mountRoot,
        mountTypeValid: !!newValue.mountType
      });
    }
  };

  onChange = () => {
    this.validate();
    this.props.onChange && this.props.onChange(this.state);
  };

  validate = (updateState = true) => {
    if (updateState) {
      this.setState({
        mountRootValid: !!this.state.mountRoot,
        mountTypeValid: !!this.state.mountType
      });
    }
    return !!this.state.mountRoot && !!this.state.mountType;
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
        className={styles.fileShareMountRow}
        style={Object.assign(
          {padding: 3},
          this.props.index % 2 === 0 ? {} : {backgroundColor: '#fafafa'})}>
        <Row type="flex" align="top">
          <span style={{width: 50}}>Host:</span>
          <Input
            style={
              Object.assign(
                {flex: 1, marginTop: 1},
                this.state.mountRootValid ? {} : {borderColor: 'red'}
              )
            }
            disabled={this.props.disabled || !!this.state.id}
            value={this.state.mountRoot}
            onChange={this.onChangeMountRoot} />
          <span style={{marginLeft: 5, marginRight: 5}}>Type:</span>
          <Select
            style={
              Object.assign(
                {width: 200, marginTop: 1},
                this.state.mountTypeValid ? {} : {borderColor: 'red'}
                )
            }
            disabled={this.props.disabled || !!this.state.id}
            value={this.state.mountType}
            onChange={this.onChangeMountType}>
            {
              Object.keys(MountOptions)
                .map(key => <Select.Option key={key} value={key}>{key}</Select.Option>)
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
                  expanded={this.state.expandedIndex === index} />
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
