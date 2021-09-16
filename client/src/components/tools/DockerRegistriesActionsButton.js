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
import {observer} from 'mobx-react';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import {Icon, message, Button} from 'antd';
import Menu, {MenuItem, Divider, SubMenu} from 'rc-menu';
import Dropdown from 'rc-dropdown';
import roleModel from '../../utils/roleModel';
import AddRegistry from '../../models/tools/RegistryCreate';
import UpdateRegistry from '../../models/tools/RegistryUpdate';
import UpdateCredentials from '../../models/tools/UpdateCredentials';
import RegistryDelete from '../../models/tools/RegistryDelete';
import ToolRegister from '../../models/tools/ToolRegister';
import ToolsGroupCreate from '../../models/tools/ToolsGroupCreate';
import ToolsGroupPrivateCreate from '../../models/tools/ToolsGroupPrivateCreate';
import ToolsGroupUpdate from '../../models/tools/ToolsGroupUpdate';
import ToolsGroupDelete from '../../models/tools/ToolsGroupDelete';
import EditToolGroupForm from './forms/EditToolGroupForm';
import EnableToolForm from './forms/EnableToolForm';
import EditRegistryForm from './forms/EditRegistryForm';
import DockerConfiguration from './forms/DockerConfiguration';
import registryName from './registryName';
import deleteToolConfirmModal from './tool-deletion-warning';
import DropDownWrapper from '../special/dropdown-wrapper';
import styles from './Tools.css';

@roleModel.authenticationInfo
@observer
export default class DockerRegistriesActionsButton extends React.Component {
  state = {
    overlayVisible: false,
    addRegistryForm: false,
    editRegistryForm: false,
    createPrivateGroupInProgress: false,
    createPrivateGroupError: null,
    searchString: null,
    editGroupFormVisible: false,
    enableToolFormVisible: false,
    createToolGroupFormVisible: false,
    configurationFormVisible: false,
    registryOperationInProgress: false
  };

  @computed
  get groupHasChildTools () {
    return this.props.group
      ? (this.props.group.tools || []).length > 0
      : false;
  }

  _openCreateRegistryForm = () => {
    this.setState({addRegistryForm: true});
  };

  _closeCreateRegistryForm = () => {
    this.setState({addRegistryForm: false});
  };

  _openEditRegistryForm = () => {
    this.setState({editRegistryForm: true});
  };

  _closeEditRegistryForm = () => {
    this.setState({editRegistryForm: false});
  };

  _createRegistry = async (registryData) => {
    const {
      path,
      description,
      userName,
      password,
      certificate,
      pipelineAuth,
      externalUrl,
      securityScanEnabled
    } = registryData;
    const name = registryName({path, description, externalUrl});
    const hide = message.loading(`Adding registry ${name}...`, 0);
    const request = new AddRegistry();
    await request.send({
      path,
      description,
      userName,
      password,
      caCert: certificate,
      pipelineAuth,
      externalUrl,
      securityScanEnabled
    });
    hide();
    if (request.error) {
      message.error(request.error);
      this._closeCreateRegistryForm();
    } else {
      this._closeCreateRegistryForm();
      this.props.onRefresh && await this.props.onRefresh();
      this.props.onNavigate && this.props.onNavigate(request.value.id);
    }
  };

  _registryOperationWrapper = (operation) => (...props) => {
    this.setState({
      registryOperationInProgress: true
    }, async () => {
      await operation(...props);
      this.setState({
        registryOperationInProgress: false
      });
    });
  };

  _editRegistry = async (registryData) => {
    const {
      description,
      userName,
      password,
      certificate,
      pipelineAuth,
      externalUrl,
      securityScanEnabled
    } = registryData;
    const hide = message.loading(`Updating registry ${registryName(this.props.registry)}...`, 0);
    const request = new UpdateRegistry();
    await request.send({
      id: this.props.registry.id,
      path: this.props.registry.path,
      description,
      securityScanEnabled
    });
    if (request.error) {
      hide();
      message.error(request.error);
    } else {
      if (
        (userName && userName.length) ||
        (password && password.length) ||
        (certificate && certificate.length) ||
        (pipelineAuth && pipelineAuth.length) ||
        externalUrl !== undefined
      ) {
        const updateCredentials = new UpdateCredentials();
        await updateCredentials.send({
          id: this.props.registry.id,
          userName,
          password,
          caCert: certificate,
          pipelineAuth,
          externalUrl
        });
        hide();
        if (updateCredentials.error) {
          message.error(updateCredentials.error, 5);
          return;
        }
      } else {
        hide();
      }
      this._closeEditRegistryForm();
      this.props.onRefresh && await this.props.onRefresh();
    }
  };

  _deleteRegistry = async () => {
    const hide = message.loading(`Deleting registry ${registryName(this.props.registry)}...`);
    const request = new RegistryDelete(this.props.registry.id);
    await request.fetch();
    hide();
    if (request.error) {
      message.error(request.error);
    } else {
      this._closeEditRegistryForm();
      this.props.onRefresh && await this.props.onRefresh();
      this.props.onNavigate && this.props.onNavigate();
    }
  };

  _confirmDeleteRegistry = () => {
    const deleteRegistry = this._deleteRegistry;
    deleteToolConfirmModal({registry: this.props.registry}, this.props.history)
      .then((confirm) => {
        if (confirm) {
          return deleteRegistry();
        }
        return Promise.resolve();
      });
  };

  _openEditGroupForm = () => {
    this.setState({editGroupFormVisible: true});
  };

  _closeEditGroupForm = () => {
    this.setState({editGroupFormVisible: false});
  };

  _editGroup = async ({id, description}) => {
    const hide = message.loading('Updating tool group description...', 0);
    const request = new ToolsGroupUpdate();
    await request.send({
      id,
      description
    });
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      this._closeEditGroupForm();
      hide();
      this.props.onRefresh && await this.props.onRefresh();
    }
  };

  _createPrivateGroup = () => {
    this.setState({
      createPrivateGroupInProgress: true,
      createPrivateGroupError: null
    }, async () => {
      const request = new ToolsGroupPrivateCreate(this.props.registry.id);
      await request.send({});
      if (!request.error) {
        this.props.onRefresh && await this.props.onRefresh();
      }
      this.setState({
        createPrivateGroupInProgress: false,
        createPrivateGroupError: request.error
      });
    });
  };

  _openCreateToolGroupForm = () => {
    this.setState({createToolGroupFormVisible: true});
  };

  _closeCreateToolGroupForm = () => {
    this.setState({createToolGroupFormVisible: false});
  };

  _createToolGroup = async ({name, description}) => {
    const hide = message.loading(`Creating group '${name}'...`, 0);
    const request = new ToolsGroupCreate();
    await request.send({
      name,
      description,
      registryId: this.props.registry.id
    });
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      this._closeCreateToolGroupForm();
      hide();
      this.props.onRefresh && await this.props.onRefresh();
      this.props.onNavigate && this.props.onNavigate(this.props.registry.id, request.value.id);
    }
  };

  _enableTool = async ({image}) => {
    const hide = message.loading(`Enabling tool ${image}...`, 0);
    const request = new ToolRegister();
    const tool = {
      image: `${this.props.group.name}/${image}`,
      registryId: this.props.registry.id,
      cpu: '1000mi',
      ram: '1Gi',
      toolGroupId: this.props.group.id
    };
    await request.send(tool);
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this._closeEnableToolForm();
      this.props.onRefresh && await this.props.onRefresh();
    }
  };

  _openEnableToolForm = () => {
    this.setState({enableToolFormVisible: true});
  };

  _closeEnableToolForm = () => {
    this.setState({enableToolFormVisible: false});
  };

  _deleteGroup = async () => {
    const hide = message.loading('Removing tool group...', 0);
    const request = new ToolsGroupDelete(this.props.group.id, true);
    await request.fetch();
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      hide();
      this.props.onRefresh && await this.props.onRefresh();
      this.props.onNavigate && this.props.onNavigate(this.props.registry.id);
    }
  };

  _confirmDeleteGroup = () => {
    deleteToolConfirmModal({group: this.props.group}, this.props.history)
      .then((confirm) => {
        if (confirm) {
          return this._deleteGroup();
        }
      });
  };

  _openDockerConfigurationForm = () => {
    this.setState({configurationFormVisible: true});
  };

  _closeDockerConfigurationForm = () => {
    this.setState({configurationFormVisible: false});
  };

  _onMenuSelect = ({key}) => {
    this.setState({
      overlayVisible: false
    }, () => {
      switch (key) {
        case 'add-registry': this._openCreateRegistryForm(); break;
        case 'edit-registry': this._openEditRegistryForm(); break;
        case 'configure-registry': this._openDockerConfigurationForm(); break;
        case 'add-private-group': this._createPrivateGroup(); break;
        case 'add-group': this._openCreateToolGroupForm(); break;
        case 'edit-group': this._openEditGroupForm(); break;
        case 'delete-group': this._confirmDeleteGroup(); break;
        case 'enable-tool': this._openEnableToolForm(); break;
      }
    });
  };

  _renderActionsMenu = () => {
    const registryActions = [];
    const canEditGroup = roleModel.writeAllowed(this.props.group);
    if (roleModel.writeAllowed(this.props.docker)) {
      registryActions.push(
        <MenuItem
          key="add-registry"
          className={styles.menuItem}
        >
          <Icon type="plus" /> Create
        </MenuItem>
      );
    }
    if (this.props.registry && roleModel.writeAllowed(this.props.registry)) {
      registryActions.push(
        <MenuItem
          key="edit-registry"
          className={styles.menuItem}
        >
          <Icon type="edit" /> Edit
        </MenuItem>
      );
    }
    const groupActions = [];

    if (this.props.registry &&
      this.props.registry.privateGroupAllowed &&
      !this.props.hasPersonalGroup) {
      groupActions.push(
        <MenuItem
          key="add-private-group"
          className={styles.menuItem}
        >
          <Icon type="plus" /> Create personal
        </MenuItem>
      );
    }
    if (this.props.registry &&
      roleModel.isManager.toolGroup(this) &&
      roleModel.writeAllowed(this.props.registry)) {
      groupActions.push(
        <MenuItem
          key="add-group"
          className={styles.menuItem}
        >
          <Icon type="plus" /> Create
        </MenuItem>
      );
    }

    if (canEditGroup) {
      if (groupActions.length > 0) {
        groupActions.push(
          <Divider key="group-divider" />
        );
      }
      groupActions.push(
        <MenuItem
          key="edit-group"
          className={styles.menuItem}
        >
          <Icon type="edit" /> Edit
        </MenuItem>
      );
      if (roleModel.isManager.toolGroup(this)) {
        groupActions.push(
          <MenuItem
            key="delete-group"
            className={styles.menuItem}
            style={{color: 'red'}}
          >
            <Icon type="delete" /> Delete
          </MenuItem>
        );
      }
    }
    const toolActions = [];
    if (canEditGroup) {
      toolActions.push(
        <MenuItem
          key="enable-tool"
          className={styles.menuItem}
        >
          <Icon type="plus" /> Enable tool
        </MenuItem>
      );
    }
    const subMenus = [];
    if (registryActions.length > 0) {
      subMenus.push(
        <SubMenu
          key="registry"
          title="Registry"
          className={styles.actionsSubMenu}
        >
          {registryActions}
        </SubMenu>
      );
    }
    if (groupActions.length > 0) {
      subMenus.push(
        <SubMenu
          key="group"
          title="Group"
          className={styles.actionsSubMenu}
        >
          {groupActions}
        </SubMenu>
      );
    }
    if (toolActions.length > 0) {
      subMenus.push(...toolActions);
    }
    if (this.props.registry && this.props.registry.pipelineAuth) {
      if (subMenus.length > 0) {
        subMenus.push(<Divider key="divider" />);
      }
      subMenus.push(
        <MenuItem
          key="configure-registry"
          className={styles.menuItem}
        >
          <Icon type="question-circle-o" /> How to configure
        </MenuItem>
      );
    }
    if (subMenus.length > 0) {
      return (
        <Menu
          className={styles.actionsMenu}
          mode="vertical"
          selectedKeys={[]}
          onClick={this._onMenuSelect}
          openAnimation="zoom"
          getPopupContainer={node => node.parentNode}
        >
          {subMenus}
        </Menu>
      );
    }
    return null;
  };

  handleOverlayVisibility = (visible) => {
    this.setState({
      overlayVisible: visible
    });
  }

  render () {
    const menu = this._renderActionsMenu();
    const {overlayVisible} = this.state;
    if (menu) {
      return (
        <DropDownWrapper visible={overlayVisible}>
          <Dropdown
            trigger={['click']}
            overlayStyle={{zIndex: 2}}
            visible={overlayVisible}
            onVisibleChange={this.handleOverlayVisibility}
            overlay={(
              <div
                className={styles.menuContainer}>
                {menu}
              </div>
            )}
          >
            <Button size="small" style={{zIndex: 2}}>
              <Icon type="setting" style={{
                lineHeight: 'inherit',
                verticalAlign: 'middle'
              }} />
              <EditRegistryForm
                pending={this.state.registryOperationInProgress}
                onCancel={() => this._closeCreateRegistryForm()}
                visible={this.state.addRegistryForm}
                onSubmit={this._registryOperationWrapper(this._createRegistry)} />
              <EditRegistryForm
                pending={this.state.registryOperationInProgress}
                registry={this.props.registry}
                onCancel={this._closeEditRegistryForm}
                onSubmit={this._registryOperationWrapper(this._editRegistry)}
                onDelete={this._confirmDeleteRegistry}
                visible={this.state.editRegistryForm} />
              <EditToolGroupForm
                visible={this.state.editGroupFormVisible}
                pending={this.state.registryOperationInProgress}
                toolGroup={this.props.group}
                onSubmit={this._registryOperationWrapper(this._editGroup)}
                onCancel={this._closeEditGroupForm} />
              <EditToolGroupForm
                visible={this.state.createToolGroupFormVisible}
                onSubmit={this._registryOperationWrapper(this._createToolGroup)}
                onCancel={this._closeCreateToolGroupForm}
                pending={this.state.registryOperationInProgress} />
              <EnableToolForm
                imagePrefix={
                  this.props.registry && this.props.group
                    ? `${this.props.registry.path}/${this.props.group.name}/`
                    : null
                }
                onCancel={this._closeEnableToolForm}
                onSubmit={this._registryOperationWrapper(this._enableTool)}
                visible={this.state.enableToolFormVisible}
                pending={this.state.registryOperationInProgress} />
              <DockerConfiguration
                registry={this.props.registry}
                group={this.props.group}
                visible={this.state.configurationFormVisible}
                onClose={this._closeDockerConfigurationForm} />
            </Button>
          </Dropdown>
        </DropDownWrapper>
      );
    }
    return null;
  }
}

DockerRegistriesActionsButton.propTypes = {
  docker: PropTypes.object,
  registry: PropTypes.object,
  group: PropTypes.object,
  hasPersonalGroup: PropTypes.bool,
  onRefresh: PropTypes.func,
  onNavigate: PropTypes.func,
  history: PropTypes.any
};
