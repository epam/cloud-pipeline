import React from 'react';
import {observer} from 'mobx-react';
import {isObservableArray} from 'mobx';
import UsageNavigation from '../navigation';
import UsersRolesSelect from '../../../../special/users-roles-select';
import runnerTypes from '../navigation/runner-types';

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

class UserFilter extends React.Component {
    state={
      filter: undefined,
      initialFilter: undefined,
      runners: []
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

    changeRunner = (values) => {
      const runners = (values || []).map(({principal, name}) => {
        return {
          type: principal ? runnerTypes.user : runnerTypes.group,
          id: name,
          principal,
          name
        };
      });
      let [runnersType] = runners.filter(r => r.type !== this.currentType).map(r => r.type);
      runnersType = runnersType || this.currentType;
      const newRunners = runners.filter(r => r.type === runnersType);
      if (newRunners.length === 1) {
        this.setState({
          filter: newRunners[0],
          runners: newRunners
        });
      } else if (newRunners.length > 1) {
        this.setState({
          filter: {type: runnersType, id: newRunners.map(r => r.id)},
          runners: newRunners
        });
      } else {
        this.setState({
          filter: null,
          runners: []
        });
      }
    };

    onBlur = () => {
      const {filter} = this.state;
      const {filters = {}} = this.props;
      const {runner, buildNavigationFn = () => {}} = filters;
      if (!runnersEqual(filter, runner)) {
        const onChange = buildNavigationFn('runner');
        onChange && onChange(filter);
      }
    };
    render () {
      return (
        <UsersRolesSelect
          onChange={this.changeRunner}
          onBlur={this.onBlur}
          style={{minWidth: 180}}
          placeholder={'All users / groups'}
          value={this.state.runners}
        />);
    }
}

export default UsageNavigation.attach(observer(UserFilter));
