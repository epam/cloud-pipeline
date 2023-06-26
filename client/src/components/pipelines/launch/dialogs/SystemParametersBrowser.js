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

import React, {Component} from 'react';
import {observer, inject} from 'mobx-react';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import {
  Alert,
  Button,
  Icon,
  Input,
  Modal,
  Row,
  Table
} from 'antd';
import roleModel from '../../../../utils/roleModel';
import LoadingView from '../../../special/LoadingView';
import styles from './Browser.css';

@roleModel.authenticationInfo
@inject('runDefaultParameters')
@observer
export default class SystemParametersBrowser extends Component {
  state = {
    selectedParameters: [],
    searchString: null
  };

  onSave = () => {
    if (this.props.onSave) {
      this.props.onSave(this.state.selectedParameters);
    }
  };

  onCancel = () => {
    if (this.props.onCancel) {
      this.props.onCancel();
    }
  };

  onSearch = (event) => {
    const searchString = event.target.value;
    this.setState({searchString});
  };

  onSelect = (parameter) => {
    const alreadySelected = this.state.selectedParameters
      .filter(p => p.name === parameter.name).length > 0;
    const selectedParameters = this.state.selectedParameters;
    if (alreadySelected) {
      selectedParameters.splice(selectedParameters.indexOf(parameter), 1);
    } else {
      selectedParameters.push(parameter);
    }
    this.setState({selectedParameters});
  };

  isParameterSelected = (parameter) => {
    return this.state.selectedParameters &&
      this.state.selectedParameters.filter(p => p.name === parameter.name).length > 0;
  };

  @computed
  get authenticatedUserRolesNames () {
    if (!this.props.authenticatedUserInfo.loaded) {
      return [];
    }
    const {
      roles = []
    } = this.props.authenticatedUserInfo.value;
    return roles.map(r => r.name);
  }

  @computed
  get isAdmin () {
    if (!this.props.authenticatedUserInfo.loaded) {
      return false;
    }
    const {
      admin
    } = this.props.authenticatedUserInfo.value;
    return admin;
  }

  @computed
  get systemParameters () {
    const {runDefaultParameters} = this.props;
    if (!runDefaultParameters.loaded) {
      return [];
    }
    return (runDefaultParameters.value || []).slice();
  }

  get currentParameters () {
    const checkUserRoles = (parameter) => {
      if (
        !parameter.roles ||
        !parameter.roles.length ||
        this.isAdmin
      ) {
        return true;
      }
      return parameter.roles.some(roleName => this.authenticatedUserRolesNames.includes(roleName));
    };
    const needToShow = (parameter) => {
      return !(this.props.notToShow &&
        this.props.notToShow.length &&
        this.props.notToShow.includes(parameter.name));
    };
    const parameterMatches = (parameter) => {
      if (!this.state.searchString || !this.state.searchString.length) {
        return true;
      }
      return parameter.name.toLowerCase().includes(this.state.searchString.toLowerCase()) ||
        parameter.type.toLowerCase().includes(this.state.searchString.toLowerCase()) ||
        (parameter.description &&
          parameter.description.toLowerCase().includes(this.state.searchString.toLowerCase()));
    };
    return this.systemParameters
      .filter(checkUserRoles)
      .filter(needToShow)
      .filter(parameterMatches)
      .sort((parameterA, parameterB) => {
        if (parameterA.name > parameterB.name) {
          return 1;
        } else if (parameterA.name < parameterB.name) {
          return -1;
        } else {
          return 0;
        }
      });
  }

  renderParametersTable = () => {
    const {
      runDefaultParameters,
      authenticatedUserInfo
    } = this.props;
    if (runDefaultParameters.error) {
      return <Alert type="error" message={runDefaultParameters.error} />;
    }
    if (
      (authenticatedUserInfo.pending && !authenticatedUserInfo.loaded) ||
      (runDefaultParameters.pending && !runDefaultParameters.loaded)
    ) {
      return (
        <LoadingView />
      );
    }

    const columns = [
      {
        title: 'Name',
        dataIndex: 'name',
        key: 'name',
        render: (name, parameter) => {
          if (parameter.description) {
            return (
              <Row>
                <Row style={{
                  paddingLeft: this.isParameterSelected(parameter) ? 0 : 20
                }}>
                  {
                    this.isParameterSelected(parameter) &&
                    <Icon type="check-circle" style={{width: 20}} />
                  }
                  {name}
                </Row>
                <Row style={{
                  fontSize: 'smaller',
                  paddingLeft: 20
                }}>
                  {parameter.description}
                </Row>
              </Row>
            );
          }
          return (
            <span style={{
              marginLeft: this.isParameterSelected(parameter) ? 0 : 20
            }}>
              {
                this.isParameterSelected(parameter) &&
                <Icon type="check-circle" style={{width: 20}} />
              }
              {name}
            </span>
          );
        }
      },
      {
        title: 'Type',
        dataIndex: 'type',
        key: 'type',
        render: (type) => type.toLowerCase()
      },
      {
        title: 'Default value',
        dataIndex: 'defaultValue',
        key: 'defaultValue'
      }
    ];

    return (
      <Row type="flex" style={{height: '40vh', overflowY: 'auto'}}>
        <Table
          className={styles.table}
          dataSource={this.currentParameters}
          columns={columns}
          showHeader
          onRowClick={(parameter) => this.onSelect(parameter)}
          rowKey="name"
          rowClassName={() => styles.parameterRow}
          pagination={false}
          style={{width: '100%'}}
          locale={{emptyText: 'No system parameters'}}
          size="small" />
      </Row>
    );
  };

  render () {
    return (
      <Modal
        width="50%"
        title="Select system parameter to override"
        visible={this.props.visible}
        onCancel={this.onCancel}
        footer={
          <Row type="flex" justify="end">
            <Button
              id="system-parameters-browser-cancel-button"
              onClick={this.onCancel}
            >
              CANCEL
            </Button>
            <Button
              type="primary"
              disabled={!this.state.selectedParameters.length}
              id="system-parameters-browser-ok-button"
              onClick={this.onSave}
            >
              OK{
                !!this.state.selectedParameters.length &&
                ` (${this.state.selectedParameters.length})`
              }
            </Button>
          </Row>
        }>
        <Row type="flex" align="middle" style={{marginBottom: 10}}>
          <Input.Search
            style={{flex: 1}}
            value={this.state.searchString}
            placeholder="Parameter"
            onChange={this.onSearch}
          />
        </Row>
        {this.renderParametersTable()}
      </Modal>
    );
  }

  componentWillReceiveProps () {
    this.setState({
      selectedParameters: [],
      searchString: null
    });
  }
}

SystemParametersBrowser.propTypes = {
  visible: PropTypes.bool,
  onCancel: PropTypes.func,
  onSave: PropTypes.func,
  notToShow: PropTypes.array
};
