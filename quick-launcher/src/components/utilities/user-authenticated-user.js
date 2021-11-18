import {useEffect, useState} from 'react';
import whoAmI from '../../models/cloud-pipeline-api/who-am-i';

export default function useAuthenticatedUser () {
  const [authenticatedUser, setAuthenticatedUser] = useState(undefined);
  const [pending, setPending] = useState(true);
  const [error, setError] = useState(undefined);
  useEffect(() => {
    whoAmI()
      .then(payload => {
        const {status, payload: user, message} = payload || {};
        if (/^ok$/i.test(status)) {
          setAuthenticatedUser(user);
        } else {
          throw new Error(`Error fetching current user: ${message || 'unknown error'}`);
        }
      })
      .catch(e => {
        setError(e.message);
      })
      .then(() => setPending(false));
  }, [setAuthenticatedUser, setPending, setError]);
  return {authenticatedUser, pending, error};
}
