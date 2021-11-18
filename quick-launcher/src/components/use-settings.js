import React, {useEffect, useState} from 'react';
import getSettings from '../models/base/settings';

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
