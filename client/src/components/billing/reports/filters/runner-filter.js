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
import {observer, inject} from 'mobx-react';
import {computed, isObservableArray} from 'mobx';
import {Row, Select} from 'antd';
import styles from './runner-filter.css';

const RunnerType = {
  user: 'user',
  group: 'group'
};

function runnersEqual (runnersA, runnersB) {
  if (!runnersA && !runnersB) {
    return true;
  }
  if (!runnersA || !runnersB) {
    return false;
  }
  const {type: typeA, ids: a} = runnersA;
  const {type: typeB, ids: b} = runnersB;
  if (typeA !== typeB) {
    return false;
  }
  const idsA = (a && (Array.isArray(a) || isObservableArray(a)) ? a : []).sort();
  const idsB = (b && (Array.isArray(b) || isObservableArray(b)) ? b : []).sort();
  if (idsA.length !== idsB.length) {
    return false;
  }
  for (let i = 0; i < idsA.length; i++) {
    if (`${idsA[i]}` !== `${idsB[i]}`) {
      return false;
    }
  }
  return true;
}

function getUserDescription (user, myUserName) {
  if (!user) {
    return null;
  }
  return `${user.userName}${user.userName === myUserName ? ' (you)' : ''}`;
}

function getUserSearchOptions (user) {
  if (user) {
    const searchParts = [
      (user.userName || '').toLowerCase()
    ];
    if (user.attributes) {
      const getAttributesValues = () => {
        const values = [];
        for (let key in user.attributes) {
          if (user.attributes.hasOwnProperty(key)) {
            values.push((user.attributes[key] || '').toLowerCase());
          }
        }
        return values;
      };
      searchParts.push(...getAttributesValues());
    }
    return searchParts;
  }
  return [];
}

function filterRunner (searchOptions, filter) {
  if (!filter) {
    return true;
  }
  return searchOptions.filter(p => p.indexOf(filter.toLowerCase()) >= 0).length > 0;
}

function RenderUserName ({user, myUserName}) {
  if (!user) {
    return null;
  }
  if (user.attributes) {
    const getAttributesValues = () => {
      const values = [];
      for (let key in user.attributes) {
        if (user.attributes.hasOwnProperty(key)) {
          values.push(user.attributes[key]);
        }
      }
      return values;
    };
    const attributesString = getAttributesValues().join(', ');
    return (
      <Row type="flex" style={{flexDirection: 'column'}}>
        <Row>{user.userName}{user.userName === myUserName ? <b> (you)</b> : undefined}</Row>
        <Row><span style={{fontSize: 'smaller'}}>{attributesString}</span></Row>
      </Row>
    );
  }
  return (
    <span>
      {user.userName}
    </span>
  );
}

class RunnerFilter extends React.Component {
  state = {
    filter: undefined,
    focused: false
  };

  componentDidMount () {
    this.updateFilter();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (!runnersEqual(this.props.filter, prevProps.filter)) {
      this.updateFilter();
    }
  }

  updateFilter = () => {
    this.setState({
      filter: this.props.filter
    });
  }

  @computed
  get myUserName () {
    const {authenticatedUserInfo} = this.props;
    return authenticatedUserInfo && authenticatedUserInfo.loaded
      ? authenticatedUserInfo.value.userName
      : undefined;
  }

  get currentRunner () {
    const {filter} = this.state;
    let currentRunner;
    if (filter) {
      const {type, id} = filter;
      if (Array.isArray(id) || isObservableArray(id)) {
        currentRunner = id.map((i) => `${type}_${i}`);
      } else {
        currentRunner = [`${type}_${id}`];
      }
    }
    return currentRunner;
  }

  get currentType () {
    const {filter} = this.state;
    let currentType;
    if (filter) {
      const {type} = filter;
      currentType = type;
    }
    return currentType;
  }

  @computed
  get users () {
    const {
      users: usersRequest
    } = this.props;
    let users = [];
    if (usersRequest && usersRequest.loaded) {
      users = (usersRequest.value || []).map(u => u).sort((a, b) => {
        if (a.userName > b.userName) {
          return 1;
        }
        if (a.userName < b.userName) {
          return -1;
        }
        return 0;
      });
    }
    return users;
  }

  @computed
  get centers () {
    const {
      billingCenters: billingCentersRequest
    } = this.props;
    let centers = [];
    if (billingCentersRequest && billingCentersRequest.loaded) {
      centers = (billingCentersRequest.value || []).map(c => c).sort((a, b) => {
        if (a > b) {
          return 1;
        }
        if (a < b) {
          return -1;
        }
        return 0;
      });
    }
    return centers;
  }

  changeRunner = (keys) => {
    const runners = (keys || []).map((key) => {
      const [type, ...rest] = key.split('_');
      return {type, id: rest.join('_')};
    });
    let [runnersType] = runners.filter(r => r.type !== this.currentType).map(r => r.type);
    runnersType = runnersType || this.currentType;
    const newRunners = runners.filter(r => r.type === runnersType);
    if (newRunners.length === 1) {
      this.setState({filter: newRunners[0]});
    } else if (newRunners.length > 1) {
      this.setState({filter: {type: runnersType, id: newRunners.map(r => r.id)}});
    } else {
      this.setState({filter: null});
    }
  };

  onFocus = () => {
    this.setState({focused: true});
  }

  onBlur = () => {
    const {filter} = this.state;
    if (!runnersEqual(filter, this.props.filter)) {
      const {onChange} = this.props;
      onChange && onChange(filter);
    }
    this.setState({focused: false});
  };

  render () {
    const {focused} = this.state;
    return (
      <Select
        mode="multiple"
        showSearch
        dropdownMatchSelectWidth={false}
        className={styles.runnerSelect}
        dropdownClassName={styles.dropdown}
        style={{width: 200}}
        placeholder="All users / groups"
        value={this.currentRunner}
        onChange={this.changeRunner}
        onFocus={this.onFocus}
        onBlur={this.onBlur}
        optionLabelProp="text"
        filterOption={
          (input, option) => filterRunner(option.props.searchOptions, input)
        }
        open={focused}
      >
        <Select.OptGroup label="Billing centers">
          {
            this.centers.map((center) => (
              <Select.Option
                key={`${RunnerType.group}_${center}`}
                value={`${RunnerType.group}_${center}`}
                text={center}
                searchOptions={[(center || '').toLowerCase()]}
              >
                {center}
              </Select.Option>
            ))
          }
        </Select.OptGroup>
        <Select.OptGroup label="Users">
          {
            this.users.map((user) => (
              <Select.Option
                key={`${RunnerType.user}_${user.id}`}
                value={`${RunnerType.user}_${user.id}`}
                text={getUserDescription(user, this.myUserName)}
                user={user}
                searchOptions={getUserSearchOptions(user)}
              >
                <RenderUserName myUserName={this.myUserName} user={user} />
              </Select.Option>
            ))
          }
        </Select.OptGroup>
      </Select>
    );
  }
}

export default inject('authenticatedUserInfo', 'billingCenters', 'users')(observer(RunnerFilter));
export {RunnerType};
