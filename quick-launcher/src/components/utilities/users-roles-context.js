import React, {useContext, useEffect, useState} from 'react';
import getUsersInfo from '../../models/cloud-pipeline-api/get-users-info';
import getRoles from '../../models/cloud-pipeline-api/get-roles';

const UsersRolesContext = React.createContext({});

export {UsersRolesContext};

export function useUsersRoles() {
  return useContext(UsersRolesContext);
}

export function fetchUsersRoles () {
  const [users, setUsers] = useState([]);
  const [roles, setRoles] = useState([]);
  const [pending, setPending] = useState(true);
  useEffect(() => {
    getUsersInfo()
      .then((users) => {
        setUsers(users);
        return getRoles();
      })
      .then(roles => {
        setRoles(roles);
      })
      .catch(e => {
        console.error(e.message);
      })
      .then(() => {
        setPending(false);
      })
  }, [setUsers, setPending]);
  return {
    users,
    roles,
    pending
  };
}
