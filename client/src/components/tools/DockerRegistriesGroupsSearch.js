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
import {Input} from 'antd';
import PropTypes from 'prop-types';

@observer
export default class DockerRegistriesGroupsSearch extends React.Component {

  static propTypes = {
    groupSearch: PropTypes.string,
    onGroupSearch: PropTypes.func,
    onCancel: PropTypes.func
  };

  onGroupSearch = (e) => {
    this.props.onGroupSearch && this.props.onGroupSearch(e.target.value);
  };

  render () {
    return (
      <div style={{padding: '13px 13px 4px'}}>
        <Input.Search
          onKeyDown={(e) => {
            if (this.props.onCancel && e.key && e.key.toLowerCase() === 'escape') {
              this.props.onCancel();
            }
          }}
          id="groups-search-input"
          value={this.props.groupSearch}
          onChange={this.onGroupSearch}
          style={{width: '100%'}}
          size="small"
        />
      </div>
    );
  }

}
