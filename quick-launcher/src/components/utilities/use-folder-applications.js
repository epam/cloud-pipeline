import {useCallback, useEffect, useState} from 'react';
import fetchFolderApplications from '../../models/fetch-folder-applications';
import {useSettings} from '../use-settings';
import {safePromise as fetchDataStorages} from "../../models/cloud-pipeline-api/data-storage-available";
import {nameSorter} from '../../models/applications';
import fetchSettings from '../../models/base/settings';

/**
 *
 * @param {{options: Object?, user: Object?, force: boolean?, appType: string?}} options
 * @returns {Promise<*[]|*>}
 */
export async function loadFolderApplications(options) {
  const settings = await fetchSettings();
  if (settings && /^(folder|docker\+folder|folder\+docker)$/i.test(settings.applicationsSourceMode)) {
    await fetchDataStorages();
    return fetchFolderApplications(options)
  }
  return Promise.resolve([]);
}

export default function useFolderApplications(options, user) {
  const [applications, setApplications] = useState([]);
  const [pending, setPending] = useState(false);
  const [sync, setSync] = useState(0);
  const settings = useSettings();
  const reload = useCallback(() => setSync(o => o + 1), [setSync]);
  useEffect(() => {
    let ignore = false;
    setPending(true);
    loadFolderApplications({options, user})
      .then(apps => {
        if (!ignore) {
          console.log('Folder applications:', apps);
          setApplications(apps);
          setPending(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, [user, settings, setApplications, setPending, sync]);
  return {
    applications: applications.sort(nameSorter),
    pending,
    reload
  };
}
