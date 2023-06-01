/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer} from 'mobx-react/index';
import {computed} from 'mobx';
import {Button, Row} from 'antd';
import PlatformIcon from './platform-icon';
import styles from './DockerRegistryGroupsList.css';

const MODES = {
  groups: 'groups',
  extended: 'extended'
};

export const FILTER_TYPES = {
  personal: 'personal',
  myTools: 'myTools',
  os: 'os',
  gpu: 'gpu',
  top: 'top'
};

export const GROUP_NAMES = {
  [FILTER_TYPES.personal]: {
    name: 'Personal tools',
    comment: 'Tools from personal users groups'
  },
  [FILTER_TYPES.myTools]: {
    name: 'My tools',
    comment: 'Tools from my personal group'
  },
  [FILTER_TYPES.os]: {
    name: 'OS'
  },
  [FILTER_TYPES.gpu]: {
    name: 'GPU enabled tools'
  },
  [FILTER_TYPES.top]: {
    name: 'Top used tools',
    comment: 'Top 5 tools by usage frequency'
  }
};

@observer
export default class DockerRegistryGroupsList extends React.Component {
  static propTypes = {
    groups: PropTypes.array,
    groupSearch: PropTypes.string,
    onNavigate: PropTypes.func,
    mode: PropTypes.string,
    filter: PropTypes.object
  };

  static defaultProps = {
    mode: MODES.extended
  };

  @computed
  get filter () {
    if (!this.props.filter) {
      return {};
    }
    return this.props.filter;
  }

  get allTools () {
    const {currentGroup, groups} = this.props;
    return [...groups, currentGroup].reduce((acc, current) => {
      acc.push(...(current.tools || []));
      return acc;
    }, []);
  }

  get toolsInfo () {
    const osSet = new Set();
    const cpuSet = new Set();
    this.allTools.forEach(tool => {
      if (tool.toolOSVersion?.distribution) {
        osSet.add(tool.toolOSVersion.distribution);
      }
      if (tool.cpu) {
        cpuSet.add(tool.cpu);
      }
    });
    return {
      os: [...osSet],
      cpu: [...cpuSet]
    };
  };

  get personalGroup () {
    const {groups} = this.props;
    return groups.find(group => group.privateGroup);
  }

  groupsFilter = group => {
    const {mode} = this.props;
    if (
      mode === MODES.extended &&
      (group.privateGroup || !this.props.groupSearch)
    ) {
      return false;
    }
    if (!this.props.groupSearch || !this.props.groupSearch.length) {
      return true;
    }
    if (group.privateGroup && 'personal'.indexOf(this.props.groupSearch.toLowerCase()) === 0) {
      return true;
    }
    return group.name.toLowerCase().indexOf(this.props.groupSearch.toLowerCase()) >= 0;
  };

  getGroupName = (group) => {
    if (group.privateGroup) {
      return 'Personal';
    }
    return group.name;
  };

  onSelectGroup = (id) => {
    this.props.onNavigate && this.props.onNavigate(id);
  };

  onSelectFilter = (filter) => {
    this.onSelectGroup(encodeURIComponent(filter));
  };

  renderFilterCard = ({name, comment}) => {
    return (
      <div className={styles.groupCard}>
        <span className={styles.heading}>
          {name}
        </span>
        {comment ? (
          <span
            className={classNames(
              styles.comment,
              'cp-text-not-important'
            )}
          >
            {comment}
          </span>
        ) : null}
      </div>
    );
  }

  renderOSSelectorGroup = () => {
    return (
      <div className={styles.section}>
        <span
          className={classNames(
            styles.heading,
            'cp-text-not-important'
          )}
        >
          {this.renderFilterCard(GROUP_NAMES[FILTER_TYPES.os])}
        </span>
        {this.toolsInfo.os
          .filter(os => {
            return this.filter.filterBy === FILTER_TYPES.os
              ? os !== this.filter.value
              : true;
          })
          .map(os => (
            <Row key={os} type="flex">
              <Button
                id={`os-${os}-button`}
                className={classNames(
                  styles.button,
                  styles.os
                )}
                onClick={() => this.onSelectFilter(`${FILTER_TYPES.os}_${os}`)}
              >
                <PlatformIcon
                  platform={os}
                  style={{marginRight: '5px'}}
                />
                <b>{(os || '').toLowerCase()}</b>
              </Button>
            </Row>
          ))}
      </div>
    );
  };

  renderGroupsSelector = () => {
    const {groups, mode} = this.props;
    const filteredGroups = groups.filter(this.groupsFilter)
      .map(group => {
        return (
          <Row key={group.id} type="flex">
            <Button
              id={`group-${group.id}-button`}
              className={styles.button}
              style={{
                fontWeight: group.privateGroup ? 'bold' : 'normal',
                fontStyle: group.privateGroup ? 'italic' : 'normal'
              }}
              onClick={() => this.onSelectGroup(group.id)}
            >
              {this.getGroupName(group)}
            </Button>
          </Row>
        );
      });
    if (mode === MODES.extended) {
      if (filteredGroups.length === 0) {
        return null;
      }
      return (
        <div className={styles.section}>
          <span
            className={classNames(
              styles.heading,
              'cp-text-not-important'
            )}
          >
            Search results:
          </span>
          {filteredGroups}
        </div>
      );
    }
    return filteredGroups;
  };

  render () {
    const {mode} = this.props;
    if (mode === MODES.groups) {
      return (
        <div style={{overflowY: 'auto', flex: '1 1 auto'}}>
          {this.renderGroupsSelector()}
        </div>
      );
    }
    return (
      <div style={{overflowY: 'auto', flex: '1 1 auto'}}>
        {this.filter.filterBy !== FILTER_TYPES.personal ? (
          <Row key="personal" type="flex">
            <Button
              id={`group-personal-button`}
              className={styles.button}
              onClick={() => this.onSelectGroup(FILTER_TYPES.personal)}
            >
              {this.renderFilterCard(GROUP_NAMES[FILTER_TYPES.personal])}
            </Button>
          </Row>
        ) : null}
        {this.personalGroup && this.filter.filterBy !== FILTER_TYPES.myTools ? (
          <Row key="my-tools" type="flex">
            <Button
              id={`group-top-used-button`}
              className={styles.button}
              onClick={() => this.onSelectFilter(this.personalGroup.id)}
            >
              {this.renderFilterCard(GROUP_NAMES[FILTER_TYPES.myTools])}
            </Button>
          </Row>
        ) : null}
        {this.filter.filterBy !== FILTER_TYPES.top ? (
          <Row key="top-used" type="flex">
            <Button
              id={`group-top-used-button`}
              className={styles.button}
              onClick={() => this.onSelectFilter(FILTER_TYPES.top)}
            >
              {this.renderFilterCard(GROUP_NAMES[FILTER_TYPES.top])}
            </Button>
          </Row>
        ) : null}
        {this.filter.filterBy !== FILTER_TYPES.gpu ? (
          <Row key="gpu-enabled" type="flex">
            <Button
              id={`group-top-used-button`}
              className={styles.button}
              onClick={() => this.onSelectFilter(FILTER_TYPES.gpu)}
            >
              {this.renderFilterCard(GROUP_NAMES[FILTER_TYPES.gpu])}
            </Button>
          </Row>
        ) : null}
        {this.renderOSSelectorGroup()}
        {this.renderGroupsSelector()}
      </div>
    );
  }
}
