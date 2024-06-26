import {
  useCallback,
  useContext,
  useEffect,
  useState
} from 'react';
import {ExtendedSettingsContext} from './use-extended-settings';
import appendOptionsValue from './append-options-value';
import readOptionsValue from './read-options-value';
import appendOptionsDefaultValue from "./append-options-default-value";

function getKey(key) {
  return `gateway-launch-options-${key}`
}

function getDependenciesKey(key) {
  return `gateway-launch-options-dependencies-${key}`
}

function getLocalStorageValueFn(keyFn) {
  return (key) => {
    try {
      return JSON.parse(localStorage.getItem(keyFn(key)) || '{}');
    } catch (_) {
      return {};
    }
  }
}

function setLocalStorageValueFn(keyFn) {
  return (key, value) => localStorage.setItem(keyFn(key), JSON.stringify(value));
}

const getLocalStorageValue = getLocalStorageValueFn(getKey);
const setLocalStorageValue = setLocalStorageValueFn(getKey);
const getLocalStorageDependenciesValue = getLocalStorageValueFn(getDependenciesKey);
const setLocalStorageDependencyValue = setLocalStorageValueFn(getDependenciesKey);


export default function useAppExtendedSettings (application) {
  const appExtendedSettings = useContext(ExtendedSettingsContext);
  const [options, setOptions] = useState({});
  useEffect(() => {
    if (application && appExtendedSettings) {
      const opts = {__dependencies__: {}};
      for (const setting of appExtendedSettings) {
        if (
          typeof setting.availableForApp === 'function' &&
          !setting.availableForApp(application)
        ) {
          console.log('Setting', setting.title, 'not available for application', application.name);
          continue;
        }
        const {key} = setting;
        const storageValue = getLocalStorageValue(key);
        if (storageValue.hasOwnProperty(application.id)) {
          appendOptionsValue(opts, setting.optionsField, storageValue[application.id]);
        }
        const storageDependencyValue = getLocalStorageDependenciesValue(key);
        if (storageDependencyValue.hasOwnProperty(application.id)) {
          appendOptionsValue(opts.__dependencies__, setting.optionsField, storageDependencyValue[application.id]);
        }
      }
      setOptions(opts);
    }
  }, [application, appExtendedSettings, setOptions]);
  const save = useCallback((opts) => {
    if (application && appExtendedSettings) {
      for (const setting of appExtendedSettings) {
        const {key} = setting;
        const value = readOptionsValue(opts, setting.optionsField);
        const dependencyValue = readOptionsValue(opts?.__dependencies__, setting.optionsField);
        const storageValue = getLocalStorageValue(key);
        const storageDependencyValue = getLocalStorageDependenciesValue(key);
        storageValue[application.id] = value;
        storageDependencyValue[application.id] = dependencyValue;
        setLocalStorageValue(key, storageValue);
        setLocalStorageDependencyValue(key, storageDependencyValue);
      }
    }
  }, [application, appExtendedSettings]);
  const onChange = useCallback((setting, value, saveLocal = false) => {
    if (setting && options) {
      const current = readOptionsValue(options, setting.optionsField);
      if (current !== value) {
        const newOptions = {...appendOptionsDefaultValue(options, setting, value)};
        setOptions(newOptions);
        if (saveLocal) {
          save(newOptions);
        }
      }
    }
  }, [appendOptionsDefaultValue, options, setOptions, save]);
  const onDependencyChange = useCallback((setting, dependency, value, saveLocal = false) => {
    if (setting && options && dependency) {
      const current = readOptionsValue(options?.__dependencies__ || {}, setting.optionsField) || {};
      options.__dependencies__ = appendOptionsValue(
        options.__dependencies__,
        setting.optionsField,
        {...(current) || {}, [dependency]: value}
      )
      const newOptions = {...options};
      setOptions(newOptions);
      if (saveLocal) {
        save(newOptions);
      }
    }
  }, [appendOptionsValue, options, setOptions, save]);
  const getSettingValue = useCallback((setting, useDefault = false) => {
    if (setting) {
      const value = readOptionsValue(options, setting.optionsField);
      if (value === undefined && useDefault) {
        return setting.default;
      }
      return value;
    }
    return undefined;
  }, [options, readOptionsValue]);
  const getSettingDependencyValues = useCallback((setting) => {
    if (setting) {
      return readOptionsValue(options?.__dependencies__ || {}, setting.optionsField);
    }
    return {};
  }, [options, readOptionsValue]);
  const appendDefault = useCallback((opts) => {
    const result = {__dependencies__: {}, ...opts};
    for (const setting of appExtendedSettings) {
      if (setting.default && readOptionsValue(result, setting.optionsField) === undefined) {
        appendOptionsDefaultValue(result, setting);
      }
    }
    return result;
  }, [appExtendedSettings, appendOptionsDefaultValue]);
  return {
    appendDefault,
    getSettingValue,
    getSettingDependencyValues,
    onChange,
    onDependencyChange,
    options,
    save
  }
}
