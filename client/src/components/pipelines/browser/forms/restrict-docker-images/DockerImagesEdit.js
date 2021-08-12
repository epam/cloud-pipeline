/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Button,
  Icon,
  Input
} from 'antd';
// eslint-disable-next-line max-len
import AddDockerRegistryControl from '../../../../../components/cluster/hot-node-pool/add-docker-registry-control';
import styles from './RestrictDockerDialog.css';

const getImageName = tool => tool.registry && tool.image ? `${tool.registry}/${tool.image}` : '';

class DockerImagesEdit extends React.Component {
  state = {
    searchString: ''
  }

  get filteredTools () {
    const {toolsToMount = []} = this.props;
    const {searchString} = this.state;
    if (!searchString) {
      return toolsToMount;
    }
    return toolsToMount
      .filter(tool => getImageName(tool).includes(searchString));
  }

  get toolsToMountImages () {
    const {toolsToMount} = this.props;
    return (toolsToMount || [])
      .map(tool => getImageName(tool));
  }

  onAddDockerImage = () => {
    const {toolsToMount, onChange} = this.props;
    const tools = [...toolsToMount];
    tools.push({});
    onChange && onChange(tools);
  };

  onRemoveDockerImage = (tool) => () => {
    const {toolsToMount, onChange} = this.props;
    const payload = [...(toolsToMount || [])];
    const index = payload.indexOf(tool);
    if (index >= 0) {
      payload.splice(index, 1);
      onChange && onChange(payload);
    }
  };

  onChangeDockerImage = (tool) => (toolImage, versions, toolId) => {
    const {toolsToMount, onChange} = this.props;
    const payload = [...(toolsToMount || [])];
    const index = payload.indexOf(tool);
    const [registry, group, imageName] = (toolImage || '').split('/');
    const image = `${group || ''}/${imageName || ''}`;
    if (index >= 0) {
      payload[index].id = toolId;
      payload[index].image = image;
      payload[index].registry = registry;
      payload[index].versions = [...(versions || [])];
    } else {
      payload.push({
        id: toolId,
        image,
        registry,
        versions: [...(versions || [])]
      });
    }
    onChange && onChange(payload);
  };

  onSearch = (value) => {
    this.setState({searchString: value});
  };

  renderSearchPanel = () => {
    const {disabled} = this.props;
    return (
      <div className={styles.searchContainer}>
        <Input.Search
          disabled={disabled}
          placeholder="Search for a docker image"
          className={styles.search}
          onSearch={this.onSearch}
        />
      </div>
    );
  };

  renderDockerImage = (tool) => {
    const {toolsToMount, disabled} = this.props;
    const isDuplicate = toolsToMount.filter(t => t.id === tool.id).length > 1;
    const index = toolsToMount.indexOf(tool);
    const image = getImageName(tool);
    return (
      <div
        className={styles.imageRow}
        key={image || `tool-${index}`}
      >
        <AddDockerRegistryControl
          disabled={disabled}
          duplicate={isDuplicate}
          style={{width: '100%'}}
          containerStyle={{
            height: 'auto',
            marginBottom: '3px'
          }}
          docker={image}
          versionsSelected={tool.versions}
          multipleMode
          imagesToExclude={this.toolsToMountImages}
          onChange={this.onChangeDockerImage(tool)}
          onRemove={this.onRemoveDockerImage(tool)}
        />
      </div>
    );
  };

  render () {
    const {disabled} = this.props;
    return (
      <div className={styles.container}>
        {this.renderSearchPanel()}
        {this.filteredTools.map(this.renderDockerImage)}
        <div>
          <Button
            onClick={this.onAddDockerImage}
            type="dashed"
            disabled={disabled}
            style={{marginTop: '20px'}}
          >
            <Icon type="plus" />
            Add docker image
          </Button>
        </div>
      </div>
    );
  }
}

DockerImagesEdit.PropTypes = {
  toolsToMount: PropTypes.arrayOf(PropTypes.shape({
    id: PropTypes.number,
    image: PropTypes.string,
    registry: PropTypes.string,
    versions: PropTypes.oneOfType([PropTypes.array, PropTypes.object])
  })),
  onChange: PropTypes.func,
  disabled: PropTypes.bool
};

export default DockerImagesEdit;
