import React, {useEffect, useState} from 'react';
import getApplications from '../models/applications';

const ApplicationsContext = React.createContext([]);
const UserContext = React.createContext(undefined);

export {ApplicationsContext, UserContext};
export function useApplications () {
  const [pending, setPending] = useState(true);
  const [apps, setApps] = useState([]);
  const [user, setUser] = useState(undefined);
  const [error, setError] = useState();
  useEffect(() => {
    getApplications()
      .then((payload) => {
        setPending(false);
        if (!payload || payload.error) {
          const fetchError = payload && payload.error
            ? payload.error
            : 'Empty response';
          setApps([]);
          setUser(undefined);
          setError(fetchError);
        } else {
          setApps(payload.applications || []);
          setUser(payload.userInfo);
          setError(undefined);
        }
      })
      .catch(e => {
        setPending(false);
        setError(e.message);
      });
  }, [setPending, setApps, setError]);
  return {
    applications: apps,
    pending,
    error,
    user
  };
}
