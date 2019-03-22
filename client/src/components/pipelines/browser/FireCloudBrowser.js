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
import {computed} from 'mobx';
import {Alert, Avatar, Button, Col, Icon, Input, Row, Select} from 'antd';
import LoadingView from '../../special/LoadingView';
import FireCloudMethodsBrowser from './FireCloudMethodsBrowser';
import FireCloudMethodSnapshotConfigurations from './FireCloudMethodSnapshotConfigurations';
import styles from './Browser.css';

@inject('googleApi', 'fireCloudMethods')
@observer
export default class FireCloudBrowser extends React.Component {

  static propTypes = {
    namespace: PropTypes.string,
    method: PropTypes.string,
    snapshot: PropTypes.string,
    configuration: PropTypes.string,
    configurationSnapshot: PropTypes.string,
    onFireCloudItemSelect: PropTypes.func,
    onNewFireCloudItemSelect: PropTypes.func
  };

  state = {
    selectedMethod: this.props.method || null,
    selectedNameSpace: this.props.namespace || null,
    selectedMethodSnapshot: this.props.snapshot || null,
    selectedMethodConfiguration: this.props.configuration || null,
    selectedMethodConfigurationSnapshot: this.props.configurationSnapshot || null,
    methodSearchString: undefined
  };

  @computed
  get methods () {
    if (this.props.fireCloudMethods.loaded) {
      return (this.props.fireCloudMethods.value || []).map(m => m);
    }
    return [];
  }

  @computed
  get currentMethodIsSelected () {
    return this.props.namespace === this.state.selectedNameSpace &&
      this.props.method === this.state.selectedMethod &&
      this.props.snapshot === this.state.selectedMethodSnapshot;
  }

  renderSignIn = () => {
    return (
      <Row
        type="flex"
        align="middle"
        justify="center"
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          margin: 5,
          border: '1px dashed #ccc',
          borderRadius: 5,
          backgroundColor: '#efefef'
        }}>
        <Row style={{margin: 2}}>
          You must sign in with your Google account to browse FireCloud methods
        </Row>
        <Row style={{margin: 2}}>
          <Button type="primary" onClick={this.props.googleApi.signIn}>
            Sign In
          </Button>
        </Row>
      </Row>
    );
  };

  onMethodSelect = (methodName, methodNamespace) => {
    if (this.props.onMethodSelect) {
      this.props.onMethodSelect(methodName, methodNamespace);
    }
    this.setState({
      selectedMethod: methodName,
      selectedNameSpace: methodNamespace,
      selectedMethodSnapshot: null
    });
  };

  onMethodConfigurationSelect = (configurationId, configurationSnapshotId) => {
    this.setState({
      selectedMethodConfiguration: configurationId,
      selectedMethodConfigurationSnapshot: configurationSnapshotId
    }, this.onFireCloudItemSelect);
  };

  onCreateNewConfigurationClicked = () => {
    if (this.props.onNewFireCloudItemSelect) {
      this.props.onNewFireCloudItemSelect({
        namespace: this.state.selectedNameSpace,
        name: this.state.selectedMethod,
        snapshot: this.state.selectedMethodSnapshot,
        configuration: null,
        configurationSnapshot: null
      });
    }
  };

  onFireCloudItemSelect = () => {
    if (this.props.onFireCloudItemSelect) {
      this.props.onFireCloudItemSelect({
        namespace: this.state.selectedNameSpace,
        name: this.state.selectedMethod,
        snapshot: this.state.selectedMethodSnapshot,
        configuration: this.state.selectedMethodConfiguration,
        configurationSnapshot: this.state.selectedMethodConfigurationSnapshot
      });
    }
  };

  onMethodsSearch = (text) => {
    this.setState({methodSearchString: text});
  };

  @computed
  get currentMethod () {
    if (this.state.selectedMethod) {
      return this.methods
        .filter(
          m => m.name === this.state.selectedMethod && m.namespace === this.state.selectedNameSpace
        )
        .shift();
    }
    return null;
  }

  @computed
  get snapshots () {
    if (this.currentMethod) {
      return (this.currentMethod.snapshotIds || [])
        .map(id => id);
    }
    return [];
  }

  onSelectSnapshot = (snapshot) => {
    this.setState({selectedMethodSnapshot: snapshot});
  };

  render () {
    if (this.props.googleApi.error) {
      return <Alert type="warning" message="Google auth initialization error" />;
    }
    if (!this.props.googleApi.loaded) {
      return <LoadingView />;
    }
    if (!this.props.googleApi.isSignedIn) {
      return this.renderSignIn();
    }
    let content;
    if (this.state.selectedMethod) {
      if (!this.state.selectedMethodSnapshot) {
        this.setState({selectedMethodSnapshot: this.snapshots[this.snapshots.length - 1]});
        return <LoadingView />;
      }
      content = <FireCloudMethodSnapshotConfigurations
        isSelected={this.currentMethodIsSelected}
        method={this.state.selectedMethod}
        namespace={this.state.selectedNameSpace}
        snapshot={this.state.selectedMethodSnapshot}
        configuration={this.state.selectedMethodConfiguration}
        configurationSnapshot={this.state.selectedMethodConfigurationSnapshot}
        onConfigurationSelect={this.onMethodConfigurationSelect}
        onCreateNew={this.onCreateNewConfigurationClicked}
      />;
    } else {
      content = <FireCloudMethodsBrowser
        methodSearchString={this.state.methodSearchString}
        onMethodSelect={this.onMethodSelect}
      />;
    }
    const googleInfoContent = this.props.googleApi.userAuthEnabled ? (
      <Row type="flex" justify="end" align="middle">
        <Avatar src={this.props.googleApi.authenticatedGoogleUserAvatarUrl} />
        <span style={{fontWeight: 'bold', margin: 5}}>
          {this.props.googleApi.authenticatedGoogleUser}
        </span>
        <Button onClick={this.props.googleApi.signOut} size="small">Sign Out</Button>
      </Row>
    ) : undefined;
    let contentHeader;
    if (this.state.selectedMethod) {
      contentHeader = (
        <Row type="flex" justify="space-between" align="middle">
          <Col style={{flex: 1}} className={styles.itemHeader}>
            <Row type="flex" align="middle">
              <Button
                size="small"
                onClick={() => {
                  this.setState({
                    selectedMethod: null,
                    selectedNameSpace: null,
                    selectedMethodSnapshot: null,
                    selectedMethodConfiguration: null,
                    selectedMethodConfigurationSnapshot: null
                  });
                }}>
                <Icon type="left" />
              </Button>
              <Icon type="fork" style={{color: '#2796dd', margin: '0px 5px'}} />
              {this.state.selectedMethod}
              {
                this.snapshots &&
                <Select
                  value={this.state.selectedMethodSnapshot}
                  onSelect={this.onSelectSnapshot}
                  style={{margin: '0 10px', minWidth: 200}}>
                  {
                    this.snapshots.map(snapshot => {
                      return (
                        <Select.Option key={snapshot} value={snapshot}>
                          {snapshot}
                        </Select.Option>
                      );
                    })
                  }
                </Select>
              }
            </Row>
          </Col>
          {
            googleInfoContent &&
            <Col>
              {googleInfoContent}
            </Col>
          }
        </Row>
      );
    } else {
      contentHeader = (
        <Row type="flex" justify="space-between" align="middle">
          <Col span={googleInfoContent ? 12 : 24}>
            <Input.Search
              placeholder="Search"
              defaultValue={this.state.methodSearchString}
              onSearch={this.onMethodsSearch} />
          </Col>
          {
            googleInfoContent &&
            <Col>
              {googleInfoContent}
            </Col>
          }
        </Row>
      );
    }
    return (
      <div style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column'
      }}>
        {contentHeader}
        {content}
      </div>
    );
  }

  componentWillReceiveProps (nextProps) {
    if (this.props.method !== nextProps.method ||
      this.props.namespace !== nextProps.namespace ||
      this.props.snapshot !== nextProps.snapshot ||
      this.props.configuration !== nextProps.configuration ||
      this.props.configurationSnapshot !== nextProps.configurationSnapshot) {
      this.setState({
        selectedMethod: nextProps.method,
        selectedNameSpace: nextProps.namespace,
        selectedMethodSnapshot: nextProps.snapshot,
        selectedMethodConfiguration: nextProps.configuration,
        selectedMethodConfigurationSnapshot: nextProps.configurationSnapshot
      });
    }
  }

}
