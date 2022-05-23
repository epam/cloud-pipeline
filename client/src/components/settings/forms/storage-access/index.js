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
import {inject, observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import classNames from 'classnames';
import {Button, Modal, Select, message, Table, Checkbox, Spin} from 'antd';
import Markdown from '../../../special/markdown';

import UserCredentialsCreate from '../../../../models/user/UserCredentialsCreate';
import UserCredentialsRevoke from '../../../../models/user/UserCredentialsRevoke';
import UserStoragePermissionsUpdate from '../../../../models/user/UserStoragePermissionsUpdate';
import UserStoragePermissionsCreate from '../../../../models/user/UserStoragePermissionsCreate';
import UserStoragePermissionsRevoke from '../../../../models/user/UserStoragePermissionsRevoke';
import UserProfiles from '../../../../models/user/UserProfiles';
import styles from './storage-access.css';

const actionsNames = {
  write: 'write',
  read: 'read'
};

class StorageAccess extends React.Component {
  @observable profilesRequest = new UserProfiles(this.props.user);

  state = {
    dialogVisible: false,
    regionId: '',
    permissions: [],
    credentials: {},
    inProgress: false
  };

  componentDidMount () {
    this.setState({
      regionId: this.cloudRegions[0].id
    }, () => this.reload());
  }

  getStorageAccessConfigurationByProvider (provider) {
    const configurations = this.storageAccessConfigurations || {};
    return configurations[provider];
  }

  getCloudRegionName (id) {
    return (this.cloudRegions.find(r => r.id.toString() === id.toString())?.name || '');
  }

  getStorageName (id) {
    if (id) {
      return (this.storages.find(s => s.id.toString() === id.toString())?.name || '');
    }
  }

  getTemplateByRegion (regionId) {
    const [config] = Object.values(this.storageAccessConfigurations)
      .flat()
      .filter(f => f.cloudRegion === this.getCloudRegionName(regionId));
    if (config) {
      return config.credentialsTemplate ||
      '**Public key:** {public_key}<br/>**Private key:** {private_key}';
    } else {
      return '**Public key:** {public_key}<br/>**Private key:** {private_key}';
    }
  }

  @computed
  get cloudRegions () {
    const {awsRegions} = this.props;
    if (awsRegions && awsRegions.loaded) {
      return (awsRegions.value || []).slice();
    }
    return [];
  }

  @computed
  get cloudProviders () {
    return [...new Set(this.cloudRegions.map(region => region.provider))];
  }

  @computed
  get storageAccessConfigurations () {
    const {preferences} = this.props;
    return preferences.storageOutsideAccessCredentials;
  }

  @computed
  get storageAccessAvailable () {
    const providers = this.cloudProviders.slice();
    return providers.some(provider => !!this.getStorageAccessConfigurationByProvider(provider));
  }

  @computed
  get storages () {
    const {dataStorageAvailable} = this.props;
    if (dataStorageAvailable && dataStorageAvailable.loaded) {
      return (dataStorageAvailable.value || []).slice();
    }
    return [];
  }

  @computed
  get filteredStorages () {
    const {regionId} = this.state;
    const isNFS = (storage) => (storage.type || '').toUpperCase() === 'NFS';
    return this.storages.filter(s => !isNFS(s) && s.regionId.toString() === regionId.toString());
  }

  @computed
  get userProfiles () {
    if (this.profilesRequest.loaded) {
      return (this.profilesRequest.value.profiles || {});
    }
    return {};
  }

  @computed
  get credentials () {
    const {regionId} = this.state;
    return this.userProfiles[regionId] ? this.userProfiles[regionId].keys : {};
  }

  @computed
  get preprocessedCredentials () {
    const {regionId} = this.state;
    return {...this.credentials, template: this.getTemplateByRegion(regionId)};
  }

  @computed
  get permissions () {
    const {regionId} = this.state;
    return this.userProfiles[regionId] ? this.userProfiles[regionId].policy.statements
      .map(({resource, effect, actions}) => {
        return ({
          resource,
          effect,
          write: actions.includes(actionsNames.write.toUpperCase()),
          read: actions.includes(actionsNames.read.toUpperCase())
        });
      }) : [];
  }

  get permissionsToRevoke () {
    return this.state.permissions.filter(p => p.removed && !p.isNew);
  }

  get permissionsToAdd () {
    return this.state.permissions.filter(p => (
      !p.removed &&
      this.permissions.findIndex(item => (item.resource === p.resource)) === -1
    ));
  }

  get permissionsToUpdate () {
    return this.state.permissions.filter(p => (
      !p.removed &&
      this.permissions.findIndex(item => (
        item.resource === p.resource &&
        (item.read !== p.read || item.write !== p.write)
      )) > -1
    ));
  }

  @computed
  get permissionsModified () {
    return !!(
      this.permissionsToRevoke.length ||
      this.permissionsToUpdate.length ||
      this.permissionsToAdd.length
    );
  }

  reload = async () => {
    await this.profilesRequest.fetch();
    this.updateUserProfile();
  }

  updateUserProfile = () => {
    this.setState({
      permissions: this.permissions,
      credentials: this.preprocessedCredentials
    });
  }

  onOpenDialog = () => {
    this.setState({
      dialogVisible: true,
      permissions: this.permissions,
      credentials: this.preprocessedCredentials
    });
  };

  onCloseDialog = () => {
    this.setState({
      dialogVisible: false,
      regionId: '',
      pending: false
    });
  };

  onGenerateCredentials = async () => {
    const {credentials, regionId} = this.state;
    const {user} = this.props;
    const regionHasCredentials = !!credentials.accessKeyId;
    const request = new UserCredentialsCreate(user, regionId, regionHasCredentials);
    this.setState({
      inProgress: true
    });
    await request.send();
    this.setState({
      inProgress: false
    });
    if (request.error) {
      message.error(request.error, 5);
    } else if (request.value) {
      const newCredentials = {...request.value, template: this.getTemplateByRegion(regionId)};
      this.setState({
        credentials: newCredentials
      });
    }
  }

  onRegionChanged = (value) => {
    this.setState({
      regionId: value
    }, this.updateUserProfile);
  }
  onStoragesChanged = (storageId) => {
    this.onAddStorage(storageId);
  }

  onRevokeCreds = async () => {
    const {regionId} = this.state;
    const {user} = this.props;
    const hide = message.loading('Removing current credentials...', 0);
    const request = new UserCredentialsRevoke(user, regionId);
    await request.send();
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.setState({
        credentials: {}
      });
    }
  }

  renderCredentialsForm = () => {
    const {regionId} = this.state;
    const regionHasCredentials = this.credentials && this.credentials.accessKeyId;
    return (
      <div>
        <div className={styles.controlRow}>
          <span>Cloud region:</span>
          <Select
            style={{width: '50%'}}
            defaultValue={regionId.toString()}
            onChange={this.onRegionChanged}
          >
            {this.cloudRegions.map(region => (
              <Select.Option
                key={region.regionId}
                value={region.id.toString()}
              >
                {region.name}
              </Select.Option>))
            }
          </Select>
          <Button
            type="primary"
            htmlType="submit"
            onClick={this.onGenerateCredentials}
            style={{minWidth: 160}}
          >
            {regionHasCredentials ? 'Regenerate' : 'Generate'} credentials
          </Button>
        </div>
        {this.renderCredentialsMd()}
      </div>);
  }

  renderFooter = () => {
    return (
      <div className={styles.footer}>
        <Button type="primary"onClick={this.onCloseDialog}>OK</Button>
      </div>
    );
  }

  copyCredentialsToClipboard = (event, credentials) => {
    event && event.stopPropagation();
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(credentials).then(() => {
        message.info('Credentials copied to clipboard', 3);
      });
    }
  }

  renderCredentialsMd = () => {
    const {credentials} = this.state;
    if (credentials && credentials.accessKeyId) {
      let credentialsString = '';
      if (credentials.accessKeyId && credentials.template.match(/{public_key}/)) {
        credentialsString = credentials.template.replace(/{public_key}/, credentials.accessKeyId);
      }
      if (credentials.secretKey && credentials.template.match(/{private_key}/)) {
        credentialsString = credentialsString.replace(/{private_key}/, credentials.secretKey);
      }
      return (
        <div
          className={classNames(styles.credentialsCard, 'cp-user-storage-access-card')}
        >
          <div className={classNames(styles.cardHeader, 'cp-user-storage-access-card-title')}>
            <div>Credentials</div>
            <Button type="danger" onClick={this.onRevokeCreds}>Revoke</Button>
          </div>
          <div className={classNames(styles.cardContent, 'cp-user-storage-access-card-content')}>
            <div className={styles.shareCredentialsContainer}>
              <Markdown md={credentialsString} style={{width: '100%'}} />
              <Button
                type="primary"
                onClick={(e) => this.copyCredentialsToClipboard(e, credentialsString)}
                style={{
                  marginLeft: 5
                }}
                icon="copy"
              />
            </div>
          </div>
        </div>);
    } else {
      return null;
    }
  }

  onActionChanged = (item, action) => {
    const {resource} = item;
    const {permissions} = this.state;
    const index = permissions.findIndex(p => p.resource === resource);
    const updatedPermissions = JSON.parse(JSON.stringify([...permissions]));
    updatedPermissions[index][action] = !permissions[index][action];
    this.setState({
      permissions: updatedPermissions
    });
  }

  onRemoveStorage = (item) => {
    const {permissions} = this.state;
    const permissionsToUpdate = JSON.parse(JSON.stringify([...permissions]));
    const index = permissions.findIndex(({resource}) => resource === item.resource);
    if (index > -1) {
      permissionsToUpdate[index].removed = true;
    }
    this.setState({
      permissions: permissionsToUpdate
    });
  }

  onAddStorage = (storageId) => {
    const {permissions, regionId} = this.state;
    const permissionsToUpdate = JSON.parse(JSON.stringify([...permissions]));
    const existedPermissionWithSameResource = permissions.find(p => p.resource === storageId);
    if (!existedPermissionWithSameResource) {
      permissionsToUpdate.push({
        regionId: regionId,
        effect: 'ALLOW',
        read: true,
        write: false,
        resource: storageId,
        isNew: true
      });
    }

    this.setState({
      permissions: permissionsToUpdate
    });
  }

  onSavePermissions = async () => {
    const {user} = this.props;
    const {regionId} = this.state;
    if (this.permissionsToRevoke.length) {
      const hide = message.info('Revoking permissions...', 0);
      const request = new UserStoragePermissionsRevoke(user, regionId, this.permissionsToRevoke);
      await request.send();
      hide();
      if (request.error) {
        message.error(request.error, 5);
      } else {
        this.reload();
      }
    } else if (this.permissionsToAdd.length) {
      this.permissionsToAdd.forEach(async (p) => {
        const actions = [];
        if (p.write) actions.push(actions.write.toUpperCase());
        if (p.read) actions.push(actions.read.toUpperCase());
        const payload = {
          statements: p.map(s => (
            {
              effect: s.effect,
              actions,
              resource: s.resource
            }
          ))
        };
        const hide = message.info('Saving permissions...', 0);
        const request = new UserStoragePermissionsCreate(user, regionId);
        await request.send(payload);
        hide();
        if (request.error) {
          message.error(request.error, 5);
        } else {
          this.reload();
        }
      });
    } else if (this.permissionsToUpdate.length) {
      this.permissionsToUpdate.forEach(async (p) => {
        const actions = [];
        if (p.write) actions.push(actions.write.toUpperCase());
        if (p.read) actions.push(actions.read.toUpperCase());
        const payload = {
          statements: p.map(s => (
            {
              effect: s.effect,
              actions,
              resource: s.resource
            }
          ))
        };
        const hide = message.info('Saving permissions...', 0);
        const request = new UserStoragePermissionsUpdate(user, regionId);
        await request.send(payload);
        hide();
        if (request.error) {
          message.error(request.error, 5);
        } else {
          this.reload();
        }
      });
    }
  }

  renderPermissionsForm = () => {
    if (this.state.permissions.length) {
      const {regionId} = this.state;
      const columns = [
        {
          title: 'Storage',
          key: 'resource',
          render: (item) => this.getStorageName(item.resource)
        },
        {
          title: 'Read',
          width: 50,
          className: styles.storageActions,
          key: 'read',
          render: (item) => (
            <Checkbox
              checked={item.read}
              onChange={(e) => this.onActionChanged(item, actionsNames.read)} />
          )
        },
        {
          title: 'Write',
          width: 50,
          key: 'write',
          className: styles.storageActions,
          render: (item) => (
            <Checkbox
              checked={item.write}
              onChange={(e) => this.onActionChanged(item, actionsNames.write)} />
          )
        },
        {
          width: 50,
          className: styles.storageActions,
          key: 'remove',
          render: (item) => (
            <Button
              type="danger"
              icon="delete"
              onClick={() => this.onRemoveStorage(item)} />
          )
        }
      ];
      const tableData = this.state.permissions.filter(p => !p.removed);
      return (
        <div className={classNames(
          styles.permissionsFormContainer,
          'cp-user-storage-access-card'
        )}>
          <Table
            style={{marginTop: 10}}
            title={(currentPageData) => 'Permissions'}
            size="small"
            columns={columns}
            pagination={false}
            rowKey={(item) => this.getStorageName(item.resource)}
            dataSource={tableData} />
          <div className={styles.controlRow}>
            <span>Add storage: </span>
            <Select
              style={{width: '70%'}}
              disabled={!regionId}
              onChange={this.onStoragesChanged}
            >
              {this.filteredStorages.map(storage => (
                <Select.Option key={storage.id}>
                  <span><b>{storage.name}</b>{` (${storage.pathMask})`}</span>
                </Select.Option>))}
            </Select>
            <Button
              disabled={!this.permissionsModified}
              type="primary"
              onClick={this.onSavePermissions}
            >
              Save
            </Button>
          </div>
        </div>
      );
    } else {
      return null;
    }
  }

  render () {
    const {
      className,
      style
    } = this.props;
    if (!this.storageAccessAvailable) {
      return null;
    }
    return (
      <div
        className={
          classNames(
            className
          )
        }
        style={style}
      >
        <b>Storage access:</b>
        <a
          onClick={this.onOpenDialog}
          style={{marginLeft: 5}}
        >
          configure
        </a>
        <Modal
          title="Storages credentials"
          visible={this.state.dialogVisible}
          footer={this.renderFooter()}
          onCancel={this.onCloseDialog}
        >
          <Spin spinning={this.state.inProgress}>
            {this.renderCredentialsForm()}
            {this.renderPermissionsForm()}
          </Spin>
        </Modal>
      </div>
    );
  }
}

StorageAccess.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object
};

export default inject(
  'preferences',
  'awsRegions',
  'dataStorageAvailable'
)(observer(StorageAccess));
