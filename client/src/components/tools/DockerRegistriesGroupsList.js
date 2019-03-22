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
import {Button, Row} from 'antd';
import PropTypes from 'prop-types';

@observer
export default class DockerRegistryGroupsList extends React.Component {

  static propTypes = {
    groups: PropTypes.array,
    groupSearch: PropTypes.string,
    onNavigate: PropTypes.func
  };

  groupsFilter = group => {
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
      return 'personal';
    }
    return group.name;
  };

  onSelectGroup = (id) => {
    this.props.onNavigate && this.props.onNavigate(id);
  };

  render () {
    return (
      <div style={{overflowY: 'auto', flex: '1 1 auto'}}>
        {
          this.props.groups.filter(this.groupsFilter)
            .map(group => {
              return (
                <Row key={group.id} type="flex">
                  <Button
                    id={`group-${group.id}-button`}
                    style={{
                      textAlign: 'left',
                      width: '100%',
                      border: 'none',
                      fontWeight: group.privateGroup ? 'bold' : 'normal',
                      fontStyle: group.privateGroup ? 'italic' : 'normal'
                    }}
                    onClick={
                      () => this.onSelectGroup(group.id)
                    }>
                    {
                      this.getGroupName(group)
                    }
                  </Button>
                </Row>
              );
            })
        }
      </div>
    );
  }

}
