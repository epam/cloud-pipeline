import {useCallback, useEffect, useState} from 'react';
import fetchFolderApplications from '../../models/fetch-folder-applications';
import {useSettings} from '../use-settings';

export default function useFolderApplications(options, useServiceUser = true, ...users) {
  const [applications, setApplications] = useState([]);
  const [pending, setPending] = useState(false);
  const [sync, setSync] = useState(0);
  const settings = useSettings();
  const reload = useCallback(() => setSync(o => o + 1), [setSync]);
  const userNames = [...(new Set(
    [
      useServiceUser ? settings?.serviceUser : undefined,
      ...users.map(user => user.userName)
    ].filter(Boolean)
  ))].join(',');
  useEffect(() => {
    let ignore = false;
    if (settings) {
      setPending(true);
      const uniqueUsers = [
        useServiceUser && settings.serviceUser
          ? {userName: settings.serviceUser}
          : undefined
      ]
        .filter(Boolean)
        .concat(users)
        .filter(
            (user, index, array) => array.findIndex(o => o.userName === user.userName) === index
        );
      Promise.all(
        uniqueUsers.map(user => fetchFolderApplications(settings, options, user))
      )
        .then((payloads) => {
          if (!ignore) {
            setApplications(payloads.reduce((r, c) => ([...r, ...c]), []));
            setPending(false);
          }
        });
    }
    return () => {
      ignore = true;
    };
  }, [userNames, settings, setApplications, setPending, sync]);
  return {
    applications,
    pending,
    reload
  };
}
