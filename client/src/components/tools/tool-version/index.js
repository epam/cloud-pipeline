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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import classNames from 'classnames';
import LoadTool from '../../../models/tools/LoadTool';
import {
  Alert,
  Button,
  Icon,
  Row,
  Tabs,
  Card
} from 'antd';
import LoadingView from '../../special/LoadingView';
import Owner from '../../special/owner';
import roleModel from '../../../utils/roleModel';
import ToolLink from '../elements/ToolLink';
import PlatformIcon from '../platform-icon';
import styles from './ToolVersion.css';
import LoadToolVersionSettings from '../../../models/tools/LoadToolVersionSettings';

@inject('preferences', 'dockerRegistries')
@inject((stores, {params}) => {
  return {
    ...stores,
    toolId: params.id,
    version: params.version,
    tool: new LoadTool(params.id),
    settings: new LoadToolVersionSettings(params.id, params.version)
  };
})
@observer
export default class ToolVersion extends React.Component {
  @computed
  get dockerRegistry () {
    if (this.props.dockerRegistries.loaded && this.props.tool.loaded) {
      return (this.props.dockerRegistries.value.registries || [])
        .filter(r => r.id === this.props.tool.value.registryId)[0];
    }
    return null;
  }

  navigateBack = () => {
    this.props.router.push(`/tool/${this.props.tool.value.id}/versions`);
  };

  navigateTo = (key) => {
    this.props.router.push(`/tool/${this.props.tool.value.id}/info/${this.props.version}/${key}`);
  };

  render () {
    if (!this.props.tool.loaded && this.props.tool.pending) {
      return <LoadingView />;
    }
    if (this.props.tool.error) {
      return <Alert type="error" message={this.props.tool.error} />;
    }
    if (!roleModel.readAllowed(this.props.tool.value)) {
      return (
        <Card
          className={
            classNames(
              styles.toolVersionCard,
              'cp-panel',
              'cp-panel-no-hover',
              'cp-panel-borderless'
            )
          }
          bodyStyle={{padding: 15, height: '100%', display: 'flex', flexDirection: 'column'}}>
          <Alert type="error" message="You have no permissions to view tool details" />
        </Card>
      );
    }

    let platform;
    if (this.props.settings.loaded) {
      const currentVersion = (this.props.settings.value || [])
        .find(v => v.version === this.props.version);
      platform = currentVersion ? currentVersion.platform : undefined;
    }

    const isWindowsPlatform = /^windows$/i.test(platform);

    let activeKey = 'scaninfo';
    if (this.props.routes) {
      activeKey = this.props.routes[this.props.routes.length - 1].tabKey;
    }

    if (isWindowsPlatform && ['scaninfo', 'packages'].indexOf(activeKey) >= 0) {
      activeKey = 'settings';
    }

    return (
      <Card
        className={
          classNames(
            styles.toolVersionCard,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )
        }
        bodyStyle={{padding: 15, height: '100%', display: 'flex', flexDirection: 'column'}}>
        <Row>
          <Row className={styles.title}>
            <Button
              onClick={this.navigateBack}
              size="small"
              style={{marginBottom: 3, verticalAlign: 'middle', lineHeight: 'inherit'}}>
              <Icon type="arrow-left" />
            </Button>
            <ToolLink link={this.props.tool.value.link} style={{marginLeft: 5}} />
            <span style={{marginLeft: 5}}>{this.props.tool.value.image}:{this.props.version}</span>
            <PlatformIcon
              platform={platform}
              style={{marginLeft: 5}}
            />
            <Owner subject={this.props.tool.value} style={{marginLeft: 5}} />
          </Row>
        </Row>
        <Tabs
          activeKey={activeKey}
          onChange={this.navigateTo}
          size="small">
          {
            this.props.settings.loaded &&
            !isWindowsPlatform &&
            this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry) && (
              <Tabs.TabPane key="scaninfo" tab="VULNERABILITIES REPORT" />
            )
          }
          <Tabs.TabPane key="settings" tab="SETTINGS" />
          {
            this.props.settings.loaded &&
            !isWindowsPlatform && (
              <Tabs.TabPane key="packages" tab="PACKAGES" />
            )
          }
          <Tabs.TabPane key="history" tab="IMAGE HISTORY" />
        </Tabs>
        <div style={{flex: 1, overflow: 'auto'}}>
          {this.props.children}
        </div>
      </Card>
    );
  }
}
