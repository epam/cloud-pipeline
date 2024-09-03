import React from 'react';
import {observer} from 'mobx-react';
import UsageNavigation, {RunnerTypes} from '../navigation';
import UsersRolesSelect from '../../../../special/users-roles-select';
import runnerTypes from '../navigation/runner-types';

function UserFilter ({filters}) {
  const {
    runner: runners = [],
    navigate = () => {}
  } = filters;
  const changeRunner = (values) => {
    const runners = (values || []).map(({principal, name}) => {
      return {
        type: principal ? runnerTypes.user : runnerTypes.group,
        id: name
      };
    });
    navigate({runner: runners});
  };
  return (
    <UsersRolesSelect
      onChange={changeRunner}
      adGroups={false}
      style={{minWidth: 180}}
      placeholder={'All users / groups'}
      value={
        runners.map(runner => ({name: runner.id, principal: runner.type === RunnerTypes.user}))
      }
    />);
}

export default UsageNavigation.attach(observer(UserFilter));
