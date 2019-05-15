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
import {observer, inject} from 'mobx-react';
import {computed} from 'mobx';
import {Input, Icon} from 'antd';
import DockerImageBrowser from '../dialogs/DockerImageBrowser';
import dockerRegistries from '../../../../models/tools/DockerRegistriesTree';
import styles from './LaunchPipelineForm.css';

@inject(() => ({
  registries: dockerRegistries
}))
@observer
export default class DockerImageInput extends React.Component {

  static propTypes = {
    onChange: PropTypes.func,
    value: PropTypes.string,
    disabled: PropTypes.bool
  };

  state = {
    browserVisible: false
  };

  input;

  refInput = (input) => {
    if (input && input.refs.input) {
      this.input = input.refs.input;
    } else {
      this.input = null;
    }
  };

  openBrowser = () => {
    if (this.input) {
      this.input.blur();
    }
    this.setState({
      browserVisible: true
    });
  };

  closeBrowser = () => {
    this.setState({
      browserVisible: false
    });
  };

  selectDockerImage = (image) => {
    this.closeBrowser();
    if (this.props.onChange) {
      this.props.onChange(image);
    }
  };

  @computed
  get registries () {
    if (this.props.registries.loaded) {
      return (this.props.registries.value.registries || []).map(r => r);
    }
    return [];
  }

  render () {
    return (
      <div
        className={this.props.className}>
        <Input
          id="docker-image-input"
          style={{width: '100%'}}
          addonBefore={
            <div
              className={styles.pathType}
              onClick={!this.props.disabled && this.openBrowser}>
              <Icon type="tool" />
            </div>
          }
          size="large"
          disabled={this.props.disabled || this.props.registries.pending || this.registries.length === 0}
          ref={this.refInput}
          onFocus={this.openBrowser}
          value={this.state.value} />
        {
          this.registries.length > 0 ?
          <DockerImageBrowser
            registries={this.registries}
            visible={this.state.browserVisible}
            onCancel={this.closeBrowser}
            onChange={this.selectDockerImage}
            dockerImage={this.state.value}/> : undefined
        }
      </div>
    );
  }

  componentWillReceiveProps (nextProps) {
    if ('value' in nextProps) {
      const value = nextProps.value;
      this.setState({value});
    }
  }

  componentDidMount () {
    this.setState({value: this.props.value});
  }
}
