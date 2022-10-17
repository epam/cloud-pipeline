import React, { useCallback, useEffect, useReducer } from 'react';
import getApplications, {nameSorter} from '../models/applications';
import {safePromise as fetchDataStorages} from "../models/cloud-pipeline-api/data-storage-available";
import { loadFolderApplications } from './utilities/use-folder-applications';
import fetchSettings from '../models/base/settings';
import mapFolderApplication from '../models/map-folder-application-with-tool';

const ApplicationsContext = React.createContext([]);
const UserContext = React.createContext(undefined);

export {ApplicationsContext, UserContext};

async function fetchApplications () {
  const globalSettings = await fetchSettings();
  await fetchDataStorages();
  const isFolderApps = /^folder$/i.test(globalSettings?.applicationsSourceMode);
  const isFolderAndDockerApps = /^(folder\+docker|docker\+folder)$/i.test(globalSettings?.applicationsSourceMode);
  const isDockerApps = /^docker$/i.test(globalSettings?.applicationsSourceMode);
  const payload = await getApplications({folder: isFolderApps || isFolderAndDockerApps});
  if (!payload || payload.error) {
    throw new Error(payload.error || 'Applications not found (empty response)');
  }
  const {
    applications: tools = [],
    userInfo: user
  } = payload;
  if (isDockerApps) {
    return {
      applications: tools,
      user
    };
  }
  // isFolderApps OR isFolderAndDockerApps
  const folderApplications = await loadFolderApplications({});
  const folderApplicationsProcessed = (folderApplications || [])
    .map(folderApp => mapFolderApplication(folderApp, tools))
    .filter(Boolean);
  console.log('Folder applications tools info:');
  folderApplicationsProcessed.forEach((app) => {
    console.log(`Application "${app.name}" ("${app.appType || 'default'}" app type): tool #${app.toolId}`);
  })
  console.log('');
  const dockerApplications = tools.filter(aTool => !aTool._folder_);
  return {
    applications: [
      ...folderApplicationsProcessed,
      ...dockerApplications
    ].sort(nameSorter),
    user
  };
}

const reducer = (state, action) => {
  switch (action.type) {
    case 'init':
      return {
        ...state,
        pending: true,
        error: undefined
      };
    case 'error':
      return {
        ...state,
        pending: false,
        error: action.error
      };
    case 'apps':
      return {
        ...state,
        applications: action.applications || [],
        user: action.user,
        error: undefined,
        pending: false
      };
    default:
      return state;
  }
};
const init = () => ({
  applications: [],
  pending: true,
  user: undefined,
  error: undefined
});

export function useApplications () {
  const [state, dispatch] = useReducer(reducer, undefined, init);
  const onInit = useCallback(() => dispatch({type: 'init'}), [dispatch]);
  const onError = useCallback((error) => dispatch({type: 'error', error}), [dispatch]);
  const onLoad = useCallback((applications, user) => dispatch({type: 'apps', applications, user}), [dispatch]);
  useEffect(() => {
    onInit();
    fetchApplications()
      .then(({applications, user: userInfo}) => {
        onLoad(applications, userInfo);
      })
      .catch(error => {
        onError(error.message);
      })
  }, [onInit, onError, onLoad]);
  return state;
}
