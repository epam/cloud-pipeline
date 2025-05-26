/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import {autorun, computed} from 'mobx';
import {observer, inject} from 'mobx-react';
import {Alert, Button, Spin} from 'antd';
import {Environment, LaunchFormInfo, ParameterGroup} from './index';
import LaunchFormStore from './launch-form-store';
import {getDockerImage} from '../../../../utils/get-docker-image';
import styles from './launch-form.css';
import LoadTool from '../../../../models/tools/LoadTool';
import LoadToolVersionSettings from '../../../../models/tools/LoadToolVersionSettings';

const DEFAULT_REGISTRY_ID = 1; // Default registry

@inject('dockerRegistries')
@observer
export default class LaunchForm extends React.Component {
  constructor (props) {
    super(props);
    this.formStore = new LaunchFormStore();
  }

  formStore;
  dispose;

  componentDidMount () {
    this.dispose = autorun(() => {
      if (this.props.dockerRegistries.loaded) {
        this.initializeData();
      }
    });
  }

  componentDidUpdate (prevProps) {
    if (prevProps.data !== this.props.data && this.props.dockerRegistries.loaded) {
      this.initializeData();
    }
  }

  componentWillUnmount () {
    if (typeof this.dispose === 'function') {
      this.dispose();
    }
  }

  @computed
  get registries () {
    if (this.props.dockerRegistries.loaded) {
      return this.props.dockerRegistries.value.registries;
    }
    return [];
  }

  initializeData = async () => {
    const {data} = this.props;
    if (!this.props.dockerRegistries.loaded || !data) {
      return;
    }
    const registry = this.registries.find(r => r.id === DEFAULT_REGISTRY_ID);
    const {tool} = getDockerImage(
      `${registry.name}/${data.dockerImage}`,
      this.props.dockerRegistries
    ) || {};
    if (!tool) {
      this.formStore.error = `Tool ${data.dockerImage} not found!`;
      return;
    };
    const [toolVersions, toolInfo] = [
      new LoadToolVersionSettings(tool.id),
      new LoadTool(tool.id)
    ];
    await Promise.all([toolVersions, toolInfo].map(request => request.fetch()));
    if (!toolInfo.loaded || toolInfo.error || !toolVersions.loaded || toolVersions.error) {
      const error = toolVersions.error || toolInfo.error;
      this.formStore.error = `Error loading tool info. ${error}`;
      return;
    };
    this.formStore.initializeData({
      data,
      toolInfo: toolInfo.value,
      toolVersion: toolVersions.value
        .find(({version}) => version === (data.version || 'latest'))
    });
  };

  render () {
    if (this.formStore?.error) {
      return <Alert type="error" message={this.formStore.error} />;
    }
    if (this.formStore.pending) {
      return <Spin />;
    }

    return (
      <div className={classNames(styles.launchForm, 'cp-panel')}>
        <LaunchFormInfo
          formStore={this.formStore}
        />
        <Environment formStore={this.formStore} />
        <ParameterGroup formStore={this.formStore} />
        <div className={styles.controlBtn}>
          <Button
            type="primary"
            onClick={() => {
              console.log('[LaunchForm]', this.formStore);
            }}
          >
            SUBMIT
          </Button>
        </div>
      </div>
    );
  }
}

LaunchForm.propTypes = {
  data: PropTypes.shape({
    dockerImage: PropTypes.string,
    disk: PropTypes.string,
    cmd: PropTypes.string,
    instanceType: PropTypes.string,
    is_spot: PropTypes.bool,
    parameters: PropTypes.objectOf(
      PropTypes.shape({
        type: PropTypes.string,
        value: PropTypes.any
      })
    )
  })
};
