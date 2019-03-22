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
import EFSHostsFormItem from './forms/EFSHostsFormItem';
import LoadingView from '../../special/LoadingView';
import {SplitPanel} from '../../special/splitPanel';
import {
  Alert,
  Button,
  Checkbox,
  Icon,
  Input,
  Modal,
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

const AWS_REGION_ITEM_TYPE = 'AWS_REGION';

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

@inject('awsRegions', 'awsRegionIds')
@observer
export default class AWSRegionsForm extends React.Component {

  static propTypes = {
    onInitialize: PropTypes.func
  };

  state = {
    currentRegionId: null,
    search: null,
    operationInProgress: false,
    newRegion: false
  };

  @observable awsRegionForm;

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
  get availableRegionIds () {
    if (this.props.awsRegionIds.loaded && this.props.awsRegions.loaded) {
      const notAvailableRegions = (this.props.awsRegions.value || [])
        .filter(r => this.state.newRegion || r.id !== this.state.currentRegionId)
        .map(r => r.regionId);
      const available = (this.props.awsRegionIds.value || [])
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
    return this.awsRegionForm.modified || this.state.newRegion;
  }

  renderAwsRegionsTable = () => {
    const columns = [
      {
        dataIndex: 'name',
        key: 'name',
        render: (name, region) => {
          if (region.isNew) {
            return <i>{name}</i>;
          }
          return (
            <span>
              <AWSRegionTag regionUID={region.regionId} />
              {highlightText(name, this.state.search)}
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
    const hide = message.loading('Saving region properties...', -1);
    const request = new AWSRegionUpdate(this.state.currentRegionId);
    await request.send(region);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
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
      hide();
      this.setState({
        currentRegionId: null
      });
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

  onAddNewRegionClicked = () => {
    this.setState({
      newRegion: true
    });
  };

  onCreateRegion = async (region) => {
    region.id = undefined;
    const hide = message.loading('Creating region...', -1);
    const request = new AWSRegionCreate();
    await request.send(region);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      const id = request.value.id;
      if (this.awsRegionForm) {
        await this.awsRegionForm.submitPermissions(id);
      }
      hide();
      await this.props.awsRegions.fetch();
      this.setState({
        currentRegionId: id,
        newRegion: false
      });
    }
    await this.props.awsRegions.fetch();
  };

  onCancelCreate = () => {
    this.setState({
      newRegion: false
    });
  };

  onFieldsChanged = () => {
    if (this.awsRegionForm) {
      this.awsRegionForm.onFormFieldChanged();
    }
  };

  awsRegionFormComponent = Form.create({onFieldsChange: this.onFieldsChanged})(AWSRegionForm);

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
          <Button
            disabled={this.regionModified || this.state.newRegion}
            onClick={this.onAddNewRegionClicked}
            size="small" style={{marginLeft: 5}}>
            Add new region
          </Button>
        </Row>
        <SplitPanel
          contentInfo={[
            {
              key: 'regions',
              size: {
                pxDefault: 150
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
              isNew={this.state.newRegion}
              pending={this.props.awsRegions.pending || this.state.operationInProgress}
              onSubmit={this.operationWrapper(this.onSaveRegion)}
              onRemove={this.operationWrapper(this.onRemoveRegionConfirm)}
              onCreate={this.operationWrapper(this.onCreateRegion)}
              onCancelCreate={this.onCancelCreate}
              regionIds={this.availableRegionIds}
              region={
                this.state.newRegion
                  ? observable({isNew: true})
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
      newRegion: false
    });
  };

  componentDidMount () {
    this.props.onInitialize && this.props.onInitialize(this);
  }

  selectRegion = (region) => {
    if (region) {
      this.setState({
        currentRegionId: region.id
      });
    } else {
      this.setState({
        currentRegionId: null
      });
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

  onFormFieldChanged = () => {
    if (!this.props.region || !this.props.form) {
      this._modified = false;
      return;
    }
    const checkStringValue = (field) => {
      return this.props.region[field] !== this.props.form.getFieldValue(field);
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
    const checkArrayValue = (field) => {
      return (this.props.region[field] || []).join(';') !== (this.props.form.getFieldValue(field) || []).join(';');
    };
    this._modified = checkStringValue('regionId') ||
      checkStringValue('name') ||
      checkBOOLValue('default') ||
      checkStringValue('kmsKeyId') ||
      checkStringValue('kmsKeyArn') ||
      checkJSONValue('corsRules') ||
      checkJSONValue('policy') ||
      checkArrayValue('efsHosts');
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
      if (!err) {
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
              className="edit-region-id-container">
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
                  disabled={this.props.pending}>
                  {
                    (this.props.regionIds || []).map(r => {
                      return (
                        <Select.Option key={r} value={r}>
                          <AWSRegionTag
                            regionUID={r} style={{marginRight: 5}} />{r}
                        </Select.Option>
                      );
                    })
                  }
                </Select>
              )}
            </Form.Item>
            <Form.Item
              label="Name"
              required
              {...this.formItemLayout}
              className="edit-region-name-container">
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
              label="NFS Mount Targets"
              {...this.formItemLayout}
              className="edit-region-elastic-nfs-hosts-container">
              {getFieldDecorator('efsHosts', {
                initialValue: this.props.region.efsHosts
              })(
                <EFSHostsFormItem />
              )}
            </Form.Item>
            <Form.Item
              label="KMS Key ID"
              required
              {...this.formItemLayout}
              className="edit-region-kmsKeyId-container">
              {getFieldDecorator('kmsKeyId', {
                initialValue: this.props.region.kmsKeyId,
                rules: [{required: true, message: 'KMS Key ID is required'}]
              })(
                <Input
                  size="small"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              label="KMS Key Arn"
              required
              {...this.formItemLayout}
              className="edit-region-kmsKeyArn-container">
              {getFieldDecorator('kmsKeyArn', {
                initialValue: this.props.region.kmsKeyArn,
                rules: [{required: true, message: 'KMS Key Arn is required'}]
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
              className="edit-region-cors-rules-container">
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
              hasFeedback
              {...this.formItemLayout}
              className="edit-region-policy-container">
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
                overlay="You cannot remove default cloud region. Set new default region first.">
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
