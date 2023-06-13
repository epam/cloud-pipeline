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
import {
  Button,
  Row,
  Icon,
  Tag,
  Collapse
} from 'antd';
import {observer} from 'mobx-react/index';
import styles from './DockerRegistryGroupsList.css';

const MODES = {
  groups: 'groups',
  extended: 'extended'
};

@observer
export default class DockerRegistryGroupsList extends React.Component {
  static propTypes = {
    groups: PropTypes.array,
    groupSearch: PropTypes.string,
    onNavigate: PropTypes.func,
    mode: PropTypes.string,
    filter: PropTypes.string,
    filters: PropTypes.object
  };

  static defaultProps = {
    mode: MODES.extended
  };

  get selectedLibraryGroup () {
    const {filter, currentGroup = {}} = this.props;
    if (filter) {
      return undefined;
    }
    return currentGroup;
  }

  groupsFilter = group => {
    const {mode} = this.props;
    if (
      mode === MODES.extended && group.privateGroup
    ) {
      return true;
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
    this.props.onNavigate && this.props.onNavigate(encodeURIComponent(id));
  };

  renderFilterCard = group => {
    const {filter} = this.props;
    const isActive = filter === group.id;
    return (
      <Button
        className={classNames(styles.cardContainer, styles.button)}
        id={`group-${group.id}-filter`}
        onClick={() => this.onSelectGroup(group.id)}
      >
        <div className={styles.card}>
          <span className={styles.heading}>
            {group.title}
          </span>
          {group.description ? (
            <span
              className={classNames(
                styles.comment,
                'cp-text-not-important'
              )}
            >
              {group.description}
            </span>
          ) : null}
        </div>
        {isActive ? (
          <Icon
            className={styles.activeGroupIcon}
            type="check"
          />
        ) : null}
      </Button>
    );
  }

  renderGroupsSelector = () => {
    const {groups} = this.props;
    const filteredGroups = groups.filter(this.groupsFilter)
      .map(group => {
        return (
          <Row key={group.id} type="flex">
            <Button
              id={`group-${group.id}-button`}
              className={styles.button}
              style={{
                fontWeight: group.privateGroup ? 'bold' : 'normal',
                fontStyle: group.privateGroup ? 'italic' : 'normal',
                padding: 0
              }}
              onClick={() => this.onSelectGroup(group.id)}
            >
              {this.getGroupName(group)}
            </Button>
          </Row>
        );
      });
    return (
      <div className={styles.section}>
        {filteredGroups}
      </div>
    );
  };

  render () {
    const {mode, filters} = this.props;
    if (mode === MODES.groups) {
      return (
        <div style={{overflowY: 'auto', flex: '1 1 auto'}}>
          {this.renderGroupsSelector()}
        </div>
      );
    }
    return (
      <div style={{overflowY: 'auto', flex: '1 1 auto'}}>
        {filters.groups.map(group => (
          <Row key={group.id} type="flex">
            {this.renderFilterCard(group)}
          </Row>
        ))}
        <Collapse bordered={false}>
          <Collapse.Panel
            className={styles.collapseContainer}
            header={(
              <div>
                <span>Tool groups</span>
                {this.selectedLibraryGroup ? (
                  <Tag
                    style={{marginLeft: 5}}
                  >
                    {this.getGroupName(this.selectedLibraryGroup)}
                  </Tag>
                ) : null}
              </div>
            )}
            key="groups"
          >
            {this.renderGroupsSelector()}
          </Collapse.Panel>
        </Collapse>
      </div>
    );
  }
}
