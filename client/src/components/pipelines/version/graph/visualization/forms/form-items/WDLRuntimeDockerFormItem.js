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
import {Checkbox, Row} from 'antd';
import DockerImageInput from '../../../../../launch/form/DockerImageInput';

export function parseRawDockerImageValue (str) {
  // "docker_image:version" || ["docker_image1:version1", "docker_image2:version1"]
  let docker = str;
  const clearQuotes = (raw) => {
    const quotes = ['"', '\'', '`'];
    let result = raw ? raw.trim() : raw;
    quotes.forEach(quote => {
      if (result && result[0] === quote && result[result.length - 1] === quote) {
        result = result.substring(1, result.length - 1);
      }
    });
    return result;
  };
  if (docker && docker.startsWith('[') && docker.endsWith(']')) {
    docker = clearQuotes(docker.slice(1, -1).split(',').shift());
  } else if (docker) {
    docker = clearQuotes(docker);
  }
  return docker;
}

@inject('dockerRegistries')
@observer
export class WDLRuntimeDockerFormItem extends React.Component {

  static propTypes = {
    value: PropTypes.string,
    onChange: PropTypes.func,
    onInitialize: PropTypes.func,
    onUnMount: PropTypes.func,
    disabled: PropTypes.bool
  };

  state = {
    dockerImage: undefined,
    useAnotherDockerImage: false,
    valid: true
  };

  componentDidMount () {
    this.updateState(parseRawDockerImageValue(this.props.value));
    this.props.onInitialize && this.props.onInitialize(this);
  }

  componentWillReceiveProps (nextProps) {
    if (parseRawDockerImageValue(nextProps.value) !== this.state.dockerImage) {
      this.updateState(parseRawDockerImageValue(nextProps.value));
    }
  }

  componentWillUnmount () {
    this.props.onUnMount && this.props.onUnMount(this);
  }

  reset = () => {
    this.setState({
      dockerImage: undefined,
      useAnotherDockerImage: false,
      valid: true
    });
  };

  @computed
  get registries () {
    if (this.props.dockerRegistries.loaded) {
      return (this.props.dockerRegistries.value.registries || []).map(r => r);
    }
    return [];
  }

  updateState = (newValue) => {
    this.setState({
      dockerImage: newValue,
      useAnotherDockerImage: !!newValue,
      valid: true
    }, this.validate);
  };

  validate = () => {
    const valid = !this.state.useAnotherDockerImage || !!this.state.dockerImage;
    this.setState({
      valid
    }, valid ? this.reportOnChange : undefined);
    return valid;
  };

  reportOnChange = () => {
    this.props.onChange &&
    this.props.onChange(
      this.state.useAnotherDockerImage
        ? (this.state.dockerImage ? `"${this.state.dockerImage}"` : undefined)
        : undefined
    );
  };

  onDockerChanged = (newDockerImage) => {
    this.setState({
      dockerImage: newDockerImage
    }, this.validate);
  };

  onUseAnotherDockerChanged = (e) => {
    this.setState({
      useAnotherDockerImage: e.target.checked
    }, this.validate);
  };

  render () {
    return (
      <div>
        <Row>
          <Checkbox
            disabled={this.props.disabled}
            checked={this.state.useAnotherDockerImage}
            onChange={this.onUseAnotherDockerChanged}>
            Use another docker image
          </Checkbox>
        </Row>
        {
          this.state.useAnotherDockerImage &&
          <DockerImageInput
            disabled={this.props.disabled}
            value={this.state.dockerImage}
            onChange={this.onDockerChanged} />
        }
        {
          !this.state.valid &&
          <Row style={{color: 'red', fontSize: 'small'}}>Docker image is required</Row>
        }
      </div>
    );
  }
}
