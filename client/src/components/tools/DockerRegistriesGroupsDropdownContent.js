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
import {observer} from 'mobx-react/index';
import PropTypes from 'prop-types';
import DockerRegistriesGroupsSearch from './DockerRegistriesGroupsSearch';
import DockerRegistryGroupsList from './DockerRegistriesGroupsList';
import styles from './Tools.css';

@observer
export default class DockerRegistriesGroupsDropdownContent extends React.Component {

  static propTypes = {
    isVisible: PropTypes.bool,
    onCancel: PropTypes.func
  };

  state = {
    groupSearch: null
  };

  render () {
    return (
      <div
        id="groups-dropdown"
        className={styles.navigationDropdownContainer}
        style={{
          display: 'flex',
          flexDirection: 'column'
        }}>
        <DockerRegistriesGroupsSearch
          groupSearch={this.state.groupSearch}
          onGroupSearch={(val) => {
            this.setState({
              groupSearch: val
            });
          }}
          onCancel={() => {
            this.setState({
              groupSearch: null
            }, () => {
              this.props.onCancel && this.props.onCancel();
            });
          }}
        />
        <DockerRegistryGroupsList
          groups={this.props.groups}
          currentGroup={this.props.currentGroup}
          onNavigate={(id) => {
            this.setState({
              groupSearch: null
            }, () => {
              this.props.onNavigate &&
              this.props.onNavigate(id);
            });
          }}
          groupSearch={this.state.groupSearch}
        />
      </div>
    );
  }

  componentWillReceiveProps (nextProps) {
    if (!nextProps.isVisible) {
      this.setState({
        groupSearch: null
      });
    }
  }

}
