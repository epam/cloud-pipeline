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
import roleModel from '../../../../utils/roleModel';
import AWSRegionTag from '../../../special/AWSRegionTag';
import {
  extractFileShareMountList,
  DataStoragePathInput,
  parseFSMountPath
} from './DataStoragePathInput';
import styles from './DataStorageEditDialog.css';

export const ServiceTypes = {
  objectStorage: 'OBJECT_STORAGE',
  fileShare: 'FILE_SHARE'
};

@roleModel.authenticationInfo
@inject('awsRegions')
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
    activeTab: 'info',
    versioningEnabled: false,
    sharingEnabled: false
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
    e.preventDefault();
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        values.serviceType = this.isNfsMount
          ? ServiceTypes.fileShare
          : ServiceTypes.objectStorage;
        if (!this.isNfsMount && this.props.policySupported && this.state.versioningEnabled) {
          values.versioningEnabled = true;
        } else {
          values.backupDuration = undefined;
          values.versioningEnabled = false;
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

  onNfsPathValidation = (valid) => {
    this.setState({nfsStoragePathValid: valid});
  };

  getEditFooter = () => {
    if (roleModel.writeAllowed(this.props.dataStorage)) {
      return (
        <Row type="flex" justify="space-between">
          <Col span={12}>
            <Row type="flex" justify="start">
              {
                roleModel.manager.storage(
                  <Button
                    id="edit-storage-dialog-delete-button"
                    type="danger"
                    onClick={this.openDeleteDialog}>Delete</Button>
                )
              }
            </Row>
          </Col>
          <Col span={12}>
            <Row type="flex" justify="end">
              <Button
                id="edit-storage-dialog-cancel-button"
                onClick={this.props.onCancel}>Cancel</Button>
              <Button
                id="edit-storage-dialog-save-button"
                type="primary"
                htmlType="submit"
                onClick={this.handleSubmit}>Save</Button>
            </Row>
          </Col>
        </Row>
      );
    } else {
      return (
        <Row type="flex" justify="end">
          <Button
            id="edit-storage-dialog-cancel-button"
            onClick={this.props.onCancel}>Cancel</Button>
        </Row>
      );
    }
  };

  getCreateFooter = () => {
    return (
      <Row>
        <Button
          id="edit-storage-dialog-cancel-button"
          onClick={this.props.onCancel}>Cancel</Button>
        <Button
          id="edit-storage-dialog-create-button"
          type="primary"
          htmlType="submit"
          disabled={this.isNfsMount && !this.isStoragePathValid}
          onClick={this.handleSubmit}>Create</Button>
      </Row>
    );
  };

  getDeleteModalFooter = () => {
    return (
      <Row type="flex" justify="space-between">
        <Col span={12}>
          <Row type="flex" justify="start">
            <Button
              id="edit-storage-delete-dialog-cancel-button"
              onClick={this.closeDeleteDialog}>Cancel</Button>
          </Row>
        </Col>
        <Col span={12}>
          <Row type="flex" justify="end">
            <Button
              id="edit-storage-delete-dialog-unregister-button"
              type="danger"
              onClick={() => this.onDeleteClicked(false)}>Unregister</Button>
            <Button
              id="edit-storage-delete-dialog-delete-button"
              type="danger"
              onClick={() => this.onDeleteClicked(true)}>Delete</Button>
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
        callback('Storage path is required');
      } else if (!parseResult.storagePath.startsWith('/')) {
        callback('Storage path must begin with \'/\'');
      }
    } else if (!value || !value.path) {
      callback('Bucket is required');
    }
    callback();
  };

  render () {
    const {getFieldDecorator, resetFields} = this.props.form;
    const isReadOnly = this.props.dataStorage
      ? this.props.dataStorage.locked || !roleModel.writeAllowed(this.props.dataStorage)
      : false;
    const modalFooter = this.props.pending ? false : (
      this.props.dataStorage ? this.getEditFooter() : this.getCreateFooter()
    );
    const onClose = () => {
      resetFields();
      this.setState({activeTab: 'info'});
    };
    return (
      <Modal
        maskClosable={!this.props.pending}
        afterClose={() => onClose()}
        closable={!this.props.pending}
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
        width={this.isNfsMount ? '50%' : '33%'}
        footer={this.state.activeTab === 'info' ? modalFooter : false}>
        <Spin spinning={this.props.pending}>
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
                      validator: (rule, value, callback) => this.validateStoragePath(value, callback)
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
                      ref={!!this.props.dataStorage ? this.initializeNameInput : null}
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
                          return <Select.Option key={region.id.toString()}>
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
                    initialValue: this.props.dataStorage ? this.props.dataStorage.description : undefined
                  })(
                    <Input type="textarea" disabled={this.props.pending || isReadOnly} />
                  )}
                </Form.Item>
                {
                  !this.isNfsMount && this.props.policySupported && this.currentRegionSupportsPolicy &&
                  <Form.Item
                    className={styles.dataStorageFormItem}
                    {...this.formItemLayout}
                    label="STS duration">
                    {getFieldDecorator('shortTermStorageDuration', {
                      initialValue: this.props.dataStorage && this.props.dataStorage.storagePolicy
                        ? this.props.dataStorage.storagePolicy.shortTermStorageDuration : undefined
                    })(
                      <InputNumber
                        style={{width: '100%'}}
                        disabled={this.props.pending || isReadOnly} />
                    )}
                  </Form.Item>
                }
                {
                  !this.isNfsMount && this.props.policySupported && this.currentRegionSupportsPolicy &&
                  <Form.Item
                    className={styles.dataStorageFormItem}
                    {...this.formItemLayout}
                    label="LTS duration">
                    {getFieldDecorator('longTermStorageDuration', {
                      initialValue: this.props.dataStorage && this.props.dataStorage.storagePolicy
                        ? this.props.dataStorage.storagePolicy.longTermStorageDuration: undefined
                    })(
                      <InputNumber
                        style={{width: '100%'}}
                        disabled={this.props.pending || isReadOnly} />
                    )}
                  </Form.Item>
                }
                {
                  !this.isNfsMount && this.props.policySupported && this.currentRegionSupportsPolicy &&
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
                }
                {
                  !this.isNfsMount && this.props.policySupported &&
                  this.state.versioningEnabled && this.currentRegionSupportsPolicy &&
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
                }
                <Form.Item
                  className={styles.dataStorageFormItem}
                  {...this.formItemLayout}
                  label="Mount-point">
                  {getFieldDecorator('mountPoint', {
                    initialValue: this.props.dataStorage && this.props.dataStorage.mountPoint
                      ? this.props.dataStorage.mountPoint: undefined
                  })(
                    <Input
                      style={{width: '100%'}}
                      disabled={this.props.pending || isReadOnly} />
                  )}
                </Form.Item>
                <Form.Item
                  className={styles.dataStorageFormItem}
                  {...this.formItemLayout}
                  label="Mount options">
                  {getFieldDecorator('mountOptions', {
                    initialValue: this.props.dataStorage && this.props.dataStorage.mountOptions
                      ? this.props.dataStorage.mountOptions: undefined
                  })(
                    <Input
                      style={{width: '100%'}}
                      disabled={this.props.pending || isReadOnly} />
                  )}
                </Form.Item>
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
      const versioningEnabled = this.props.dataStorage && this.props.dataStorage.storagePolicy ?
        this.props.dataStorage.storagePolicy.versioningEnabled: true;
      const sharingEnabled = !this.isNfsMount && this.props.dataStorage
        ? this.props.dataStorage.shared
        : false;
      this.setState({versioningEnabled, sharingEnabled});
    }
  };

  componentDidMount () {
    this.checkStorageChanged();
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
  }
}
