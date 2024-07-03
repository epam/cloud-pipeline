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
import {observer, inject} from 'mobx-react';
import {computed, isObservableArray} from 'mobx';
import {Row, Select} from 'antd';
import roleModel from '../../../../utils/roleModel';
import BillingNavigation, {RunnerTypes} from '../../navigation';
import styles from './runner-filter.css';

function runnersEqual (runnersA, runnersB) {
  if (!runnersA && !runnersB) {
    return true;
  }
  if (!runnersA || !runnersB) {
    return false;
  }
  const {type: typeA, id: a} = runnersA;
  const {type: typeB, id: b} = runnersB;
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

function getUserSearchOptions (user) {
  if (user) {
    const searchParts = [
      (user.name || '').toLowerCase()
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

function getAttributesValues (user) {
  const values = [];
  for (let key in user.attributes) {
    if (user.attributes.hasOwnProperty(key)) {
      values.push(user.attributes[key]);
    }
  }
  return values;
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
  const you = user.name === myUserName
    ? (<b>{' (you)'}</b>)
    : undefined;
  if (user.attributesValues) {
    const attributesString = (user.attributesValues || []).join(', ');
    return (
      <Row type="flex" style={{flexDirection: 'column'}}>
        <Row>{user.name}{you}</Row>
        <Row><span style={{fontSize: 'smaller'}}>{attributesString}</span></Row>
      </Row>
    );
  }
  return (
    <span>
      {user.name}{you}
    </span>
  );
}

class RunnerFilter extends React.Component {
  state = {
    filter: undefined,
    initialFilter: undefined,
    focused: false,
    searchCriteria: undefined,
    searching: false,
    filteredUsers: [],
    filteredAdGroups: [],
    filteredCenters: undefined
  };

  componentDidMount () {
    this.updateFilter();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    const {
      filters = {}
    } = this.props;
    const {
      runner: filter
    } = filters;
    if (!runnersEqual(this.state.initialFilter, filter)) {
      this.updateFilter();
    }
  }

  updateFilter = () => {
    const {filters = {}} = this.props;
    const {
      runner: filter
    } = filters;
    this.setState({
      filter,
      initialFilter: filter,
      searchCriteria: undefined,
      searching: false,
      filteredUsers: [],
      filteredCenters: undefined,
      filteredAdGroups: []
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

  get currentRunnerValue () {
    if (!this.currentRunner) {
      return undefined;
    }
    const items = this.users.map((user) => ({
      item: user,
      key: `${RunnerTypes.user}_${user.name}`,
      label: user.name === this.myUserName
        ? `${user.name} (you)`
        : user.name
    }));
    items.push(
      ...this.centers.map((center) => ({
        item: center,
        key: `${RunnerTypes.billingGroup}_${center}`,
        label: center
      }))
    );
    items.push(
      ...this.adGroups.map((group) => ({
        item: group,
        key: `${RunnerTypes.group}_${group}`,
        label: group
      }))
    );
    return this.currentRunner.map((runner) => {
      const [result] = items.filter(item => item.key.toLowerCase() === runner.toLowerCase());
      return {
        key: result ? result.key : runner,
        label: result ? result.label : runner
      };
    });
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
      usersInfo: usersRequest
    } = this.props;
    let users = [];
    if (usersRequest && usersRequest.loaded) {
      users = (usersRequest.value || []).map(u => u).sort((a, b) => {
        if (a.name > b.name) {
          return 1;
        }
        if (a.name < b.name) {
          return -1;
        }
        return 0;
      });
    }
    return users.map((user) => ({
      ...user,
      searchOptions: getUserSearchOptions(user),
      attributesValues: getAttributesValues(user)
    }));
  }

  @computed
  get adGroups () {
    const adGroups = this.users.reduce((acc, user) => {
      acc.push(...(user.groups || []));
      return acc;
    }, []);
    return [...new Set(adGroups)];
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

  findRunnerDelayed = (search) => {
    if (this.findRunnerDelayedRequest) {
      clearTimeout(this.findRunnerDelayedRequest);
      this.findRunnerDelayedRequest = null;
    }
    if (!search || !search.length) {
      this.findRunner(search);
    } else {
      this.findRunnerDelayedRequest = setTimeout(() => this.findRunner(search), 100);
    }
  };

  findRunner = (search) => {
    const searchCriteria = (search || '').toLowerCase();
    this.setState({
      searching: true,
      searchCriteria
    }, async () => {
      const {
        billingCenters: billingCentersRequest,
        usersInfo: usersRequest
      } = this.props;
      await billingCentersRequest.fetchIfNeededOrWait();
      await usersRequest.fetchIfNeededOrWait();
      const {searchCriteria: initialSearch} = this.state;
      if (initialSearch === searchCriteria) {
        if (!searchCriteria || !searchCriteria.length) {
          this.setState({
            filteredCenters: undefined,
            filteredUsers: [],
            filteredAdGroups: [],
            searching: false
          });
        } else {
          const filteredCenters = this.centers
            .filter((center) => filterRunner([(center || '').toLowerCase()], searchCriteria));
          const filteredUsers = this.users
            .filter((user) => filterRunner(user.searchOptions, searchCriteria));
          const filteredAdGroups = this.adGroups
            .filter((group) => filterRunner([(group || '').toLowerCase()], searchCriteria));
          this.setState({
            filteredCenters,
            filteredUsers,
            filteredAdGroups,
            searching: false
          });
        }
      }
    });
  };

  changeRunner = (keys) => {
    const runners = (keys || []).map(({key}) => {
      const [type, ...rest] = key.split('_');
      return {type, id: rest.join('_')};
    });
    let [runnersType] = runners.filter(r => r.type !== this.currentType).map(r => r.type);
    runnersType = runnersType || this.currentType;
    const newRunners = runners.filter(r => r.type === runnersType);
    if (newRunners.length === 1) {
      this.setState({
        filter: newRunners[0],
        filteredUsers: [],
        filteredAdGroups: [],
        filteredCenters: undefined,
        searchCriteria: undefined
      });
    } else if (newRunners.length > 1) {
      this.setState({
        filter: {type: runnersType, id: newRunners.map(r => r.id)},
        filteredUsers: [],
        filteredAdGroups: [],
        filteredCenters: undefined,
        searchCriteria: undefined
      });
    } else {
      this.setState({
        filter: null,
        filteredUsers: [],
        filteredAdGroups: [],
        filteredCenters: undefined,
        searchCriteria: undefined
      });
    }
  };

  onFocus = () => {
    const {searchCriteria} = this.state;
    if (!searchCriteria) {
      this.setState({
        filteredCenters: undefined,
        filteredUsers: [],
        filteredAdGroups: []
      });
    }
    this.setState({focused: true});
  }

  onBlur = () => {
    const {filter} = this.state;
    const {filters = {}} = this.props;
    const {runner, buildNavigationFn = () => {}} = filters;
    if (!runnersEqual(filter, runner)) {
      const onChange = buildNavigationFn('runner');
      onChange && onChange(filter);
    }
    this.setState({focused: false, searchCriteria: undefined});
  };

  render () {
    const {
      focused,
      filteredCenters,
      filteredUsers,
      filteredAdGroups,
      searchCriteria
    } = this.state;
    const showBillingCenters = (filteredCenters || this.centers).length > 0;
    const showUsers = filteredUsers.length > 0;
    const open = focused &&
      showBillingCenters &&
      showUsers;
    return (
      <Select
        mode="multiple"
        labelInValue
        showSearch
        dropdownMatchSelectWidth={false}
        className={styles.runnerSelect}
        dropdownClassName={styles.dropdown}
        style={{width: 200}}
        placeholder="All billing centers / users / groups"
        notFoundContent={
          searchCriteria ? 'Not found' : 'Specify user or billing center name'
        }
        value={this.currentRunnerValue}
        onSearch={this.findRunnerDelayed}
        onChange={this.changeRunner}
        onFocus={this.onFocus}
        onBlur={this.onBlur}
        optionLabelProp="text"
        filterOption={false}
        open={open}
      >
        <Select.OptGroup label="Billing centers">
          {
            (filteredCenters || this.centers).map((center) => (
              <Select.Option
                key={`${RunnerTypes.billingGroup}_${center}`}
                value={`${RunnerTypes.billingGroup}_${center}`}
              >
                {center}
              </Select.Option>
            ))
          }
        </Select.OptGroup>
        <Select.OptGroup label="Users">
          {
            filteredUsers.map((user) => (
              <Select.Option
                key={`${RunnerTypes.user}_${user.name}`}
                value={`${RunnerTypes.user}_${user.name}`}
              >
                <RenderUserName myUserName={this.myUserName} user={user} />
              </Select.Option>
            ))
          }
        </Select.OptGroup>
        <Select.OptGroup label="Groups">
          {
            filteredAdGroups.map((group) => (
              <Select.Option
                key={`${RunnerTypes.group}_${group}`}
                value={`${RunnerTypes.group}_${group}`}
              >
                {group}
              </Select.Option>
            ))
          }
        </Select.OptGroup>
      </Select>
    );
  }
}

export default inject('billingCenters', 'usersInfo')(
  roleModel.authenticationInfo(
    BillingNavigation.attach(
      observer(RunnerFilter)
    )
  )
);
