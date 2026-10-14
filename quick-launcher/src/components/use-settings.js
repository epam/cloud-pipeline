import React, { useEffect, useMemo, useState } from 'react';
import getSettings from '../models/base/settings';
import { getApplicationTypeSettings } from '../models/folder-application-types';

const SettingsContext = React.createContext(undefined);

export {SettingsContext};
export function useSettings() {
  const [settings, setSettings] = useState(undefined);
  useEffect(() => {
    getSettings()
      .then(setSettings)
      .catch(() => {});
  }, []);
  return settings;
}

export function useApplicationTypeSettings (applicationType) {
  const globalSettings = useSettings();
  return useMemo(
    () => getApplicationTypeSettings(globalSettings, applicationType),
    [applicationType, globalSettings]
  );
}
