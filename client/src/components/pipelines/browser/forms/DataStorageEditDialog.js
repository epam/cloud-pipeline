/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject} from 'mobx-react';
import {computed} from 'mobx';
import {
  Button,
  Checkbox,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Spin,
  Tabs
} from 'antd';
import PermissionsForm from '../../../roleModel/PermissionsForm';
import RestrictDockerImages from './restrict-docker-images';
import roleModel from '../../../../utils/roleModel';
import AWSRegionTag from '../../../special/AWSRegionTag';
import {
  extractFileShareMountList,
  DataStoragePathInput,
  parseFSMountPath
} from './DataStoragePathInput';
import LifeCycleRules from './life-cycle-rules';
import dataStorageRestrictedAccessCheck from '../../../../utils/data-storage-restricted-access';
import styles from './DataStorageEditDialog.css';

export const ServiceTypes = {
  objectStorage: 'OBJECT_STORAGE',
  fileShare: 'FILE_SHARE'
};

@roleModel.authenticationInfo
@inject('awsRegions', 'preferences')
@Form.create()
export class DataStorageEditDialog extends React.Component {
  static propTypes = {
    pending: PropTypes.bool,
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    onDelete: PropTypes.func,
    visible: PropTypes.bool,
    dataStorage: PropTypes.object,
    addExistingStorageFlag: PropTypes.bool,
    isNfsMount: PropTypes.bool,
    policySupported: PropTypes.bool
  };

  state = {
    deleteDialogVisible: false,
    toolsToMount: undefined,
    activeTab: 'info',
    mountDisabled: false,
    versioningEnabled: false,
    sharingEnabled: false,
    sensitive: false,
    restrictedAccess: true,
    restrictedAccessCheckInProgress: false
  };

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 6}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 18}
    }
  };

  @computed
  get storageVersioningAllowed () {
    const {
      dataStorage,
      preferences,
      authenticatedUserInfo
    } = this.props;
    if (!dataStorage) {
      return true;
    }
    const loaded = preferences &&
      preferences.loaded &&
      authenticatedUserInfo &&
      authenticatedUserInfo.loaded;
    if (loaded) {
      const isAdmin = authenticatedUserInfo.value.admin ||
        roleModel.isManager.storageAdmin(this);
      const isOwner = roleModel.isOwner(dataStorage);
      return isAdmin ||
        (isOwner && preferences.storagePolicyBackupVisibleNonAdmins);
    }
    return false;
  }

  openDeleteDialog = () => {
    this.setState({deleteDialogVisible: true});
  };

  closeDeleteDialog = () => {
    this.setState({deleteDialogVisible: false});
  };

  onDeleteClicked = (cloud) => {
    this.closeDeleteDialog();
    if (this.props.onDelete) {
      this.props.onDelete(cloud);
    }
  };
  handleSubmit = (e) => {
    e && e.preventDefault();
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        values.serviceType = this.isNfsMount
          ? ServiceTypes.fileShare
          : ServiceTypes.objectStorage;
        values.mountDisabled = this.state.mountDisabled;
        if (!this.isNfsMount && this.props.policySupported && this.state.versioningEnabled) {
          values.versioningEnabled = true;
        } else {
          values.backupDuration = undefined;
          values.versioningEnabled = false;
        }
        if (!this.isNfsMount) {
          values.sensitive = this.state.sensitive;
        }
        values.shared = !this.isNfsMount && this.state.sharingEnabled;
        values.regionId = +values.regionId;
        const path = values.path;
        values.path = path.path;
        if (this.isNfsMount) {
          values.regionId = path.regionId;
          values.fileShareMountId = path.fileShareMountId;
        }
        this.props.onSubmit(values);
      }
    });
  };

  @computed
  get isNfsMount () {
    return this.props.dataStorage
      ? this.props.dataStorage.storageType
        ? this.props.dataStorage.storageType === 'NFS'
        : this.props.dataStorage.type === 'NFS'
      : this.props.isNfsMount;
  }

  get userPermissions () {
    const {dataStorage} = this.props;
    if (!dataStorage) {
      return {read: false, write: false};
    }
    const readAllowed = roleModel.readAllowed(dataStorage);
    const writeAllowed = roleModel.writeAllowed(dataStorage);
    return {
      read: roleModel.isManager.storageAdmin(this) || ((
        roleModel.isOwner(dataStorage) ||
        roleModel.isManager.archiveManager(this) ||
        roleModel.isManager.archiveReader(this)
      ) && readAllowed),
      write: roleModel.isManager.storageAdmin(this) || ((
        roleModel.isOwner(dataStorage) ||
        roleModel.isManager.archiveManager(this)
      ) && writeAllowed)
    };
  }

  @computed
  get transitionRulesAvailable () {
    const {
      dataStorage
    } = this.props;
    return (this.userPermissions.read || this.userPermissions.write) &&
      dataStorage &&
      dataStorage.id &&
      /^s3$/i.test(dataStorage.storageType || dataStorage.type);
  }

  get transitionRulesReadOnly () {
    return this.userPermissions.read && !this.userPermissions.write;
  }

  @computed
  get awsRegions () {
    return this.props.awsRegions.loaded ? (this.props.awsRegions.value || []).map(r => r) : [];
  }

  @computed
  get fileShareMountsList () {
    return extractFileShareMountList(this.awsRegions);
  }

  @computed
  get defaultAwsRegion () {
    const region = this.awsRegions.filter(region => region.default === true)[0];
    return region || undefined;
  }

  @computed
  get currentRegion () {
    const formValue = this.props.form.getFieldValue('regionId');
    const region = this.awsRegions.filter(region => `${region.id}` === `${formValue}`)[0];
    return region || this.defaultAwsRegion;
  }

  @computed
  get currentRegionSupportsPolicy () {
    return this.currentRegion && ['AWS', 'GCP'].indexOf(this.currentRegion.provider) >= 0;
  }

  @computed
  get isStoragePathValid () {
    return this.state.nfsStoragePathValid || false;
  }

  @computed
  get toolsToMount () {
    const {dataStorage} = this.props;
    if (dataStorage) {
      return (dataStorage.toolsToMount || [])
        .map((tool) => ({
          id: tool.id,
          toolId: tool.id,
          image: `${tool.registry}/${tool.image}`,
          versions: (tool.versions || []).map(v => v.version)
        }));
    }
    return [];
  }

  onNfsPathValidation = (valid) => {
    this.setState({nfsStoragePathValid: valid});
  };

  getEditFooter = () => {
    if (
      (roleModel.isManager.storageAdmin(this) || roleModel.isOwner(this.props.dataStorage)) &&
      !this.state.restrictedAccess
    ) {
      return (
        <Row type="flex" justify="space-between">
          <Col span={12}>
            <Row type="flex" justify="start">
              {
                roleModel.isManager.storage(this) ||
                roleModel.isManager.storageAdmin(this)
                  ? (
                    <Button
                      id="edit-storage-dialog-delete-button"
                      type="danger"
                      onClick={this.openDeleteDialog}
                    >
                      DELETE
                    </Button>
                  ) : null
              }
            </Row>
          </Col>
          <Col span={12}>
            <Row type="flex" justify="end">
              <Button
                id="edit-storage-dialog-cancel-button"
                onClick={this.props.onCancel}>CANCEL</Button>
              <Button
                id="edit-storage-dialog-save-button"
                type="primary"
                htmlType="submit"
                onClick={this.handleSubmit}>SAVE</Button>
            </Row>
          </Col>
        </Row>
      );
    } else {
      return (
        <Row type="flex" justify="end">
          <Button
            id="edit-storage-dialog-cancel-button"
            onClick={this.props.onCancel}>CANCEL</Button>
        </Row>
      );
    }
  };

  getCreateFooter = () => {
    return (
      <Row>
        <Button
          id="edit-storage-dialog-cancel-button"
          onClick={this.props.onCancel}>CANCEL</Button>
        <Button
          id="edit-storage-dialog-create-button"
          type="primary"
          htmlType="submit"
          disabled={this.isNfsMount && !this.isStoragePathValid}
          onClick={this.handleSubmit}>CREATE</Button>
      </Row>
    );
  };

  getDeleteModalFooter = () => {
    const isMirrorStorage = !!this.props.dataStorage && !!this.props.dataStorage.sourceStorageId;
    return (
      <Row type="flex" justify="space-between">
        <Col span={12}>
          <Row type="flex" justify="start">
            <Button
              id="edit-storage-delete-dialog-cancel-button"
              onClick={this.closeDeleteDialog}>CANCEL</Button>
          </Row>
        </Col>
        <Col span={12}>
          <Row type="flex" justify="end">
            <Button
              id="edit-storage-delete-dialog-unregister-button"
              type="danger"
              onClick={() => this.onDeleteClicked(false)}>UNREGISTER</Button>
            {
              !isMirrorStorage && (
                <Button
                  id="edit-storage-delete-dialog-delete-button"
                  type="danger"
                  onClick={() => this.onDeleteClicked(true)}
                >
                  DELETE
                </Button>
              )
            }
          </Row>
        </Col>
      </Row>
    );
  };

  onSectionChange = (key) => {
    this.setState({activeTab: key});
  };

  validateStoragePath = (value, callback) => {
    if (value && this.isNfsMount) {
      const parseResult = parseFSMountPath(value, this.fileShareMountsList);
      if (!parseResult || !parseResult.storagePath) {
        // eslint-disable-next-line standard/no-callback-literal
        callback('Storage path is required');
      } else if (!parseResult.storagePath.startsWith('/')) {
        // eslint-disable-next-line standard/no-callback-literal
        callback('Storage path must begin with \'/\'');
      }
    } else if (!value || !value.path) {
      // eslint-disable-next-line standard/no-callback-literal
      callback('Storage path is required');
    }
    callback();
  };

  render () {
    const {getFieldDecorator, resetFields} = this.props.form;
    const isReadOnly = this.props.dataStorage
      ? (
        this.props.dataStorage.locked ||
        this.state.restrictedAccess || (
          !roleModel.isOwner(this.props.dataStorage) &&
          !roleModel.isManager.storageAdmin(this)
        ))
      : false;
    const modalFooter = this.props.pending || this.state.restrictedAccessCheckInProgress ? false : (
      this.props.dataStorage ? this.getEditFooter() : this.getCreateFooter()
    );
    const onClose = () => {
      resetFields();
      this.setState({activeTab: 'info'});
    };
    return (
      <Modal
        maskClosable={!this.props.pending && !this.state.restrictedAccessCheckInProgress}
        afterClose={() => onClose()}
        closable={!this.props.pending && !this.state.restrictedAccessCheckInProgress}
        visible={this.props.visible}
        title={
          this.props.dataStorage
            ? (this.isNfsMount ? 'Edit FS mount' : 'Edit object storage')
            : (this.isNfsMount
              ? 'Create FS mount'
              : (this.props.addExistingStorageFlag
                ? 'Add existing object storage'
                : 'Create object storage'))
        }
        onCancel={this.props.onCancel}
        style={{transition: 'width 0.2s ease'}}
        width={(this.state.activeTab === 'transitionRules' || this.isNfsMount) ? '50%' : '33%'}
        footer={this.state.activeTab === 'info' ? modalFooter : false}>
        <Spin spinning={this.props.pending || this.state.restrictedAccessCheckInProgress}>
          <Tabs
            size="small"
            activeKey={this.state.activeTab}
            onChange={this.onSectionChange}>
            <Tabs.TabPane key="info" tab="Info">
              <Form id="edit-storage-form">
                <Form.Item
                  className={`${styles.dataStorageFormItem} edit-storage-storage-path-container`}
                  {...this.formItemLayout}
                  label="Storage path">
                  {getFieldDecorator('path', {
                    rules: [{
                      validator: (rule, value, callback) => this.validateStoragePath(
                        value,
                        callback
                      )
                    }],
                    initialValue: this.props.dataStorage
                  })(
                    <DataStoragePathInput
                      cloudRegions={this.awsRegions}
                      onValidation={this.onNfsPathValidation}
                      onPressEnter={this.handleSubmit}
                      visible={this.props.visible}
                      isFS={this.isNfsMount}
                      isNew={!this.props.dataStorage}
                      addExistingStorageFlag={this.props.addExistingStorageFlag}
                      disabled={this.props.pending || !!this.props.dataStorage || isReadOnly} />
                  )}
                </Form.Item>
                <Form.Item
                  className={styles.dataStorageFormItem}
                  {...this.formItemLayout}
                  label="Alias">
                  {getFieldDecorator('name', {
                    initialValue: this.props.dataStorage ? this.props.dataStorage.name : undefined
                  })(
                    <Input
                      ref={this.props.dataStorage ? this.initializeNameInput : null}
                      onPressEnter={this.handleSubmit}
                      disabled={this.props.pending || isReadOnly} />
                  )}
                </Form.Item>
                {
                  !this.isNfsMount &&
                  <Form.Item
                    className={styles.dataStorageFormItem}
                    {...this.formItemLayout}
                    label="Cloud region">
                    {getFieldDecorator('regionId', {
                      initialValue: this.props.dataStorage && this.props.dataStorage.regionId
                        ? this.props.dataStorage.regionId.toString()
                        : this.defaultAwsRegion ? this.defaultAwsRegion.id.toString() : undefined
                    })(
                      <Select
                        style={{width: '100%'}}
                        disabled={!!this.props.dataStorage || isReadOnly}
                      >
                        {this.awsRegions.map(region => {
                          return <Select.Option key={region.id.toString()} title={region.name}>
                            <AWSRegionTag regionUID={region.regionId} /> {region.name}
                          </Select.Option>;
                        })}
                      </Select>
                    )}
                  </Form.Item>
                }
                <Form.Item
                  className={styles.dataStorageFormItem}
                  {...this.formItemLayout}
                  label="Description">
                  {getFieldDecorator('description', {
                    initialValue: this.props.dataStorage
                      ? this.props.dataStorage.description
                      : undefined
                  })(
                    <Input type="textarea" disabled={this.props.pending || isReadOnly} />
                  )}
                </Form.Item>
                <Row>
                  <Col xs={24} sm={6} />
                  <Col xs={24} sm={18}>
                    <Form.Item className={styles.dataStorageFormItem}>
                      <Checkbox
                        disabled={this.props.pending || isReadOnly}
                        onChange={(e) => this.setState({mountDisabled: e.target.checked})}
                        checked={this.state.mountDisabled}>
                        Disable mount
                      </Checkbox>
                    </Form.Item>
                  </Col>
                </Row>
                {
                  !this.state.mountDisabled && (
                    <Form.Item
                      className={styles.dataStorageFormItem}
                      {...this.formItemLayout}
                      label="Allow mount to">
                      {getFieldDecorator('toolsToMount', {
                        initialValue: this.props.dataStorage
                          ? this.props.dataStorage.toolsToMount
                          : undefined
                      })(
                        <RestrictDockerImages disabled={this.props.pending || isReadOnly} />
                      )}
                    </Form.Item>
                  )
                }
                {
                  !this.isNfsMount &&
                  <Row>
                    <Col xs={24} sm={6} />
                    <Col xs={24} sm={18}>
                      <Form.Item className={styles.dataStorageFormItem}>
                        <Checkbox
                          disabled={this.props.pending || isReadOnly || !!this.props.dataStorage}
                          onChange={(e) => this.setState({sensitive: e.target.checked})}
                          checked={this.state.sensitive}>
                          Sensitive storage
                        </Checkbox>
                      </Form.Item>
                    </Col>
                  </Row>
                }
                {!this.isNfsMount &&
                this.props.policySupported &&
                this.currentRegionSupportsPolicy &&
                this.storageVersioningAllowed && (
                  <Row>
                    <Col xs={24} sm={6} />
                    <Col xs={24} sm={18}>
                      <Form.Item className={styles.dataStorageFormItem}>
                        <Checkbox
                          disabled={this.props.pending || isReadOnly}
                          onChange={(e) => this.setState({versioningEnabled: e.target.checked})}
                          checked={this.state.versioningEnabled}>
                          Enable versioning
                        </Checkbox>
                      </Form.Item>
                    </Col>
                  </Row>
                )}
                {!this.isNfsMount &&
                this.props.policySupported &&
                this.state.versioningEnabled &&
                this.currentRegionSupportsPolicy &&
                this.storageVersioningAllowed && (
                  <Form.Item
                    className={styles.dataStorageFormItem}
                    {...this.formItemLayout}
                    label="Backup duration">
                    {getFieldDecorator('backupDuration', {
                      initialValue: this.props.dataStorage && this.props.dataStorage.storagePolicy
                        ? this.props.dataStorage.storagePolicy.backupDuration : undefined
                    })(
                      <InputNumber
                        style={{width: '100%'}}
                        disabled={this.props.pending || isReadOnly} />
                    )}
                  </Form.Item>
                )}
                {
                  !this.state.mountDisabled && (
                    <Form.Item
                      className={styles.dataStorageFormItem}
                      {...this.formItemLayout}
                      label="Mount-point">
                      {getFieldDecorator('mountPoint', {
                        initialValue: this.props.dataStorage && this.props.dataStorage.mountPoint
                          ? this.props.dataStorage.mountPoint : undefined
                      })(
                        <Input
                          style={{width: '100%'}}
                          disabled={this.props.pending || isReadOnly} />
                      )}
                    </Form.Item>
                  )
                }
                {
                  !this.state.mountDisabled && (
                    <Form.Item
                      className={styles.dataStorageFormItem}
                      {...this.formItemLayout}
                      label="Mount options">
                      {getFieldDecorator('mountOptions', {
                        initialValue: this.props.dataStorage && this.props.dataStorage.mountOptions
                          ? this.props.dataStorage.mountOptions : undefined
                      })(
                        <Input
                          style={{width: '100%'}}
                          disabled={this.props.pending || isReadOnly} />
                      )}
                    </Form.Item>
                  )
                }
                {
                  !this.isNfsMount &&
                  (
                    (!this.props.dataStorage && !this.props.addExistingStorageFlag) ||
                    (this.props.dataStorage)
                  ) &&
                  <Row>
                    <Col xs={24} sm={6} />
                    <Col xs={24} sm={18}>
                      <Form.Item className={styles.dataStorageFormItem}>
                        <Checkbox
                          disabled={!!this.props.dataStorage || isReadOnly}
                          onChange={(e) => this.setState({sharingEnabled: e.target.checked})}
                          checked={this.state.sharingEnabled}>
                          Enable sharing
                        </Checkbox>
                      </Form.Item>
                    </Col>
                  </Row>
                }
              </Form>
            </Tabs.TabPane>
            {
              this.props.dataStorage && this.props.dataStorage.id &&
              <Tabs.TabPane key="permissions" tab="Permissions">
                <PermissionsForm
                  readonly={isReadOnly}
                  objectIdentifier={this.props.dataStorage.id}
                  objectType="DATA_STORAGE" />
              </Tabs.TabPane>
            }
            {this.transitionRulesAvailable && (
              <Tabs.TabPane key="transitionRules" tab="Transition rules">
                <LifeCycleRules
                  storageId={this.props.dataStorage.id}
                  readOnly={this.transitionRulesReadOnly}
                />
              </Tabs.TabPane>
            )}
          </Tabs>
        </Spin>
        <Modal
          visible={this.state.deleteDialogVisible}
          onCancel={this.closeDeleteDialog}
          title="Do you want to delete a storage itself or only unregister it?"
          footer={this.getDeleteModalFooter()}>
          <p>This operation cannot be undone.</p>
        </Modal>
      </Modal>
    );
  }

  checkStorageChanged = (prevProps) => {
    if (!prevProps || prevProps.dataStorage !== this.props.dataStorage) {
      const mountDisabled = this.props.dataStorage ? this.props.dataStorage.mountDisabled : false;
      const versioningEnabled = this.props.dataStorage && this.props.dataStorage.storagePolicy
        ? this.props.dataStorage.storagePolicy.versioningEnabled : true;
      const sensitive = this.props.dataStorage
        ? this.props.dataStorage.sensitive
        : false;
      const sharingEnabled = !this.isNfsMount && this.props.dataStorage
        ? this.props.dataStorage.shared
        : false;
      this.setState({mountDisabled, versioningEnabled, sharingEnabled, sensitive});
    }
  };

  componentDidMount () {
    this.checkStorageChanged();
    this.checkRestrictedAccess();
  }

  initializeNameInput = (input) => {
    if (input && input.refs && input.refs.input) {
      this.nameInput = input.refs.input;
      this.nameInput.onfocus = function () {
        setTimeout(() => {
          this.selectionStart = (this.value || '').length;
          this.selectionEnd = (this.value || '').length;
        }, 0);
      };
    }
  };

  focusNameInput = () => {
    if (this.props.visible && this.nameInput) {
      setTimeout(() => {
        this.nameInput.focus();
      }, 0);
    }
  };

  componentDidUpdate (prevProps) {
    this.checkStorageChanged(prevProps);
    if (prevProps.visible !== this.props.visible) {
      this.focusNameInput();
    }
    const dataStorageChanged = (a, b) => {
      const {
        id: aID
      } = a || {};
      const {
        id: bID
      } = b || {};
      return aID !== bID;
    };
    if (dataStorageChanged(this.props.dataStorage, prevProps.dataStorage)) {
      this.checkRestrictedAccess();
    }
  }

  componentWillUnmount () {
    this.increaseCheckRestrictedAccessToken();
  }

  increaseCheckRestrictedAccessToken = () => {
    this.checkRestrictedAccessToken = (this.checkRestrictedAccessToken || 0) + 1;
    return this.checkRestrictedAccessToken;
  };

  checkRestrictedAccess = () => {
    const {
      dataStorage
    } = this.props;
    const {
      id
    } = dataStorage || {};
    const token = this.increaseCheckRestrictedAccessToken();
    this.setState({
      restrictedAccessCheckInProgress: true,
      restrictedAccess: true
    }, async () => {
      const state = {
        restrictedAccessCheckInProgress: false
      };
      try {
        state.restrictedAccess = await dataStorageRestrictedAccessCheck(id);
      } catch (_) {
        state.restrictedAccess = true;
      } finally {
        if (token === this.checkRestrictedAccessToken) {
          if (state.restrictedAccess) {
            console.log(`Storage #${id} is in the restricted access mode for current user`);
          }
          this.setState(state);
        }
      }
    });
  };
}
