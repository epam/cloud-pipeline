/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import classNames from 'classnames';
import {
  Alert,
  Input
} from 'antd';
import styles from './cwl-tools-repository.css';
import LoadingView from '../../../../../../../special/LoadingView';
import ToolImage from '../../../../../../../../models/tools/ToolImage';

@inject('dockerRegistries', 'preferences')
@observer
class CWLToolsRepository extends React.Component {
  state = {
    search: undefined
  };

  @computed
  get toolGroups () {
    const {
      preferences
    } = this.props;
    if (preferences.loaded) {
      return preferences.uiCWLToolGroups || [];
    }
    return [];
  }

  @computed
  get tools () {
    const {
      dockerRegistries
    } = this.props;
    if (dockerRegistries.loaded) {
      const {
        registries = []
      } = dockerRegistries.value || {};
      const toolGroupIds = this.toolGroups
        .filter((id) => !Number.isNaN(Number(id)))
        .map((id) => Number(id));
      const toolGroupNames = this.toolGroups
        .filter((id) => Number.isNaN(Number(id)))
        .map((name) => name.toLowerCase());
      const tools = [];
      for (let r = 0; r < registries.length; r++) {
        const {
          groups = []
        } = registries[r];
        groups
          .filter((aGroup) => toolGroupIds.includes(aGroup.id) ||
            toolGroupNames.includes(aGroup.name.toLowerCase()))
          .forEach((aGroup) => {
            tools.push(...(aGroup.tools || []));
          });
      }
      return tools;
    }
    return [];
  }

  onDragStart = (tool) => (event) => {
    if (this.props.disabled) {
      return;
    }
    const {
      registry,
      image
    } = tool;
    const docker = `docker:${registry}/${image}`;
    event.dataTransfer.setData('text/plain', docker);
  };

  onToolClick = (tool) => (event) => {
    // empty
  };

  renderTool = (tool) => {
    const {
      id,
      image: toolImage = '',
      iconId,
      shortDescription
    } = tool;
    const image = toolImage.split('/').pop();
    return (
      <div
        key={id}
        className={
          classNames(
            styles.toolRow,
            'cp-panel'
          )
        }
        draggable
        onClick={this.onToolClick(tool)}
        onDragStart={this.onDragStart(tool)}
      >
        {
          iconId && (
            <img
              className={styles.toolIcon}
              src={ToolImage.url(id, iconId)}
            />
          )
        }
        <div
          className={styles.toolDescription}
        >
          <div>
            <b>{image}</b>
          </div>
          {
            shortDescription && (
              <div
                className={
                  classNames(
                    'cp-text-not-important',
                    'cp-ellipsis-text',
                    styles.shortDescription
                  )
                }
              >
                {shortDescription}
              </div>
            )
          }
        </div>
      </div>
    );
  };

  renderContent = () => {
    const {
      dockerRegistries
    } = this.props;
    const {
      loaded,
      pending,
      error
    } = dockerRegistries;
    if (!loaded && pending) {
      return (<LoadingView />);
    }
    if (error) {
      return (
        <Alert message={error} type="error" />
      );
    }
    const {tools = []} = this;
    if (tools.length === 0) {
      return (
        <Alert message="Tools not found" type="info" />
      );
    }
    const {
      search
    } = this.state;
    const filtered = tools
      .filter((aTool) => !search ||
        !search.length ||
        aTool.image.toLowerCase().includes(search.toLowerCase()));
    if (filtered.length === 0 && search && search.length) {
      return (
        <Alert
          message={(
            <div>
              Nothing found for <b>{search}</b>
            </div>
          )}
          type="info"
        />
      );
    }
    return (
      <div className={styles.tools}>
        {
          filtered.map(this.renderTool)
        }
      </div>
    );
  };

  onChangeSearch = (event) => this.setState({
    search: event.target.value
  });

  render () {
    const {
      className,
      style
    } = this.props;
    const {
      search
    } = this.state;
    return (
      <div
        className={classNames(className, styles.cwlToolsRepositoryContainer)}
        style={style}
      >
        <div
          className={styles.searchRow}
        >
          <Input
            style={{width: '100%'}}
            value={search}
            onChange={this.onChangeSearch}
          />
        </div>
        {this.renderContent()}
      </div>
    );
  }
}

CWLToolsRepository.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool
};

export default CWLToolsRepository;
