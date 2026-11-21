import {
  useEffect,
  useState
} from 'react';
import appendOptionsDefaultValue from './append-options-default-value';

export default function useDefaultLaunchExtendedSettings (appExtendedSettings) {
  const [options, setOptions] = useState({});
  useEffect(() => {
    if (appExtendedSettings) {
      const result = {__dependencies__: {}};
      for (const setting of appExtendedSettings) {
        if (setting.default) {
          appendOptionsDefaultValue(result, setting);
        }
      }
      setOptions(result);
    }
  }, [setOptions, appExtendedSettings, appendOptionsDefaultValue]);
  return [
    options,
    setOptions
  ];
}
