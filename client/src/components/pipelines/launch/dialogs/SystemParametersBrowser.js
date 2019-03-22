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
  Popover,
  Row,
  Table
} from 'antd';
import styles from './Browser.css';

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
  get currentParameters () {
    if (!this.props.runDefaultParameters.loaded) {
      return [];
    }
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
    return (this.props.runDefaultParameters.value || [])
      .map(t => t)
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
    if (this.props.runDefaultParameters.error) {
      return <Alert type="error" message={this.props.runDefaultParameters.error} />;
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
          loading={this.props.runDefaultParameters.pending}
          showHeader={true}
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
            <Button id="system-parameters-browser-cancel-button" onClick={this.onCancel}>
              Cancel
            </Button>
            <Button
              type="primary"
              disabled={!this.state.selectedParameters.length}
              id="system-parameters-browser-ok-button" onClick={this.onSave}>
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
