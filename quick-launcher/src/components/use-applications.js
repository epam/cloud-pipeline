import React, {useEffect, useState} from 'react';
import getApplications from '../models/applications';
import {safePromise as fetchDataStorages} from "../models/cloud-pipeline-api/data-storage-available";
import useFolderApplications from "./utilities/use-folder-applications";

const ApplicationsContext = React.createContext([]);
const UserContext = React.createContext(undefined);

export {ApplicationsContext, UserContext};
export function useApplications (settings) {
  const [pending, setPending] = useState(true);
  const [apps, setApps] = useState([]);
  const [user, setUser] = useState(undefined);
  const [error, setError] = useState();
  useEffect(() => {
    fetchDataStorages()
      .then(() => getApplications())
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
  const {
    pending: folderAppsPending,
    applications: folderApplications
  } = useFolderApplications({});
  if (!settings) {
    return {
      applications: [],
      pending: true,
      error: undefined,
      user: undefined
    };
  }
  if (/^folder$/i.test(settings.applicationsSourceMode)) {
    const [defaultApplication = {}] = apps;
    const {
      id,
      image
    } = defaultApplication;
    const mapFolderApplication = (folderApplication) => ({
      ...folderApplication,
      toolId: id,
      image,
      hasIcon: !!folderApplication.icon,
      __launch_parameters__: {
        FOLDER_APPLICATION_STORAGE: {value: folderApplication.storage, type: 'number'},
        FOLDER_APPLICATION_PATH: {value: folderApplication.path, type: 'string'}
      }
    });
    return {
      applications: (folderApplications || []).map(mapFolderApplication),
      pending: pending || folderAppsPending,
      error,
      user
    };
  }
  return {
    applications: apps,
    pending,
    error,
    user
  };
}
