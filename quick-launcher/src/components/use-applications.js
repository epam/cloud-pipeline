import React, {useEffect, useState} from 'react';
import getApplications, {nameSorter} from '../models/applications';
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
  const isFolderApps = /^(folder|folder\+docker|docker\+folder)$/i.test(settings?.applicationsSourceMode);
  const isFolderAndDockerApps = /^(folder\+docker|docker\+folder)$/i.test(settings?.applicationsSourceMode);
  useEffect(async () => {
    if (!settings) {
      return;
    }
    try {
      await fetchDataStorages();
      const payload = await getApplications({folder: isFolderApps});
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
    } catch (e) {
      setError(e.message);
    } finally {
      setPending(false);
    }
  }, [
    setPending,
    setApps,
    setError,
    settings,
    isFolderApps,
  ]);
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
  if (isFolderApps) {
    const [defaultApplication] = apps.filter(app => app._folder_);
    if (!defaultApplication) {
      return {
        applications: [],
        pending: pending || folderAppsPending,
        error: pending || folderAppsPending
          ? undefined
          : 'There is no associated tool for folder applications',
        user: undefined
      };
    }
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
    const extend = (original) => [
      ...original,
      ...(isFolderAndDockerApps ? apps.filter(app => !app._folder_) : [])
    ].sort(nameSorter);
    return {
      applications: extend((folderApplications || []).map(mapFolderApplication)),
      pending: pending || folderAppsPending,
      error,
      user
    };
  }
  return {
    applications: apps.filter(apps => !apps._folder_).sort(nameSorter),
    pending,
    error,
    user
  };
}
