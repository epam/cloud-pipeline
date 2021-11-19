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
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {Icon, Row} from 'antd';
import classNames from 'classnames';
import renderHighlights from './renderHighlights';
import renderSeparator from './renderSeparator';
import {PreviewIcons} from './previewIcons';
import styles from './preview.css';

@inject((stores, params) => {
  const {dockerRegistries} = stores;

  return {
    dockerRegistries
  };
})
@observer
export default class DockerRegistryPreview extends React.Component {
  static propTypes = {
    item: PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      parentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      name: PropTypes.string,
      description: PropTypes.string
    }),
    lightMode: PropTypes.bool
  };

  @computed
  get registries () {
    if (this.props.dockerRegistries.loaded) {
      return (this.props.dockerRegistries.value.registries || []).map(r => r);
    }
    return [];
  }

  @computed
  get currentRegistry () {
    if (!this.props.item.id) {
      return null;
    }
    return this.registries.filter(r => `${r.id}` === `${this.props.item.id}`)[0];
  }

  @computed
  get groups () {
    let groups = [];
    if (this.currentRegistry) {
      groups = (this.currentRegistry.groups || [])
        .map(g => g)
        .sort((a, b) => a.name.localeCompare(b.name));
    }

    return groups;
  }

  renderGroups = () => {
    if (!this.props.dockerRegistries) {
      return null;
    }
    if (this.props.dockerRegistries.pending) {
      return (
        <Row className={styles.contentPreview} type="flex" justify="center">
          <Icon type="loading" />
        </Row>
      );
    }
    if (this.props.dockerRegistries.error) {
      return (
        <div className={styles.contentPreview}>
          <span style={{color: '#ff556b'}}>{this.props.dockerRegistries.error}</span>
        </div>
      );
    }
    if (!this.groups.length) {
      return null;
    }

    return (
      <div className={styles.contentPreview}>
        <table>
          <tbody>
            {
              (this.groups).map(item => {
                return (
                  <tr key={item.id}>
                    <td>
                      {item.name}
                    </td>
                  </tr>
                );
              })
            }
          </tbody>
        </table>
      </div>
    );
  };

  @computed
  get name() {
    if (this.currentRegistry) {
      return this.currentRegistry.description || this.currentRegistry.externalUrl || this.currentRegistry.path;
    }
    return this.props.item.name;
  }

  render () {
    if (!this.props.item) {
      return null;
    }

    const highlights = renderHighlights(this.props.item);
    const groups = this.renderGroups();

    return (
      <div
        className={
          classNames(
            styles.container,
            {
              [styles.light]: this.props.lightMode
            }
          )
        }
      >
        <div className={styles.header}>
          <Row className={styles.title} type="flex" align="middle">
            <Icon type={PreviewIcons[this.props.item.type]} />
            <span>{this.name}</span>
          </Row>
        </div>
        <div className={styles.content}>
          {highlights && renderSeparator()}
          {highlights}
          {groups && renderSeparator()}
          {groups}
        </div>
      </div>
    );
  }

}
