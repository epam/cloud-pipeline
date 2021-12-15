import React, {useEffect, useMemo, useState} from 'react';
import {useSettings} from '../use-settings';
import fetchMountsForPlaceholders from '../../models/parse-limit-mounts-placeholders-config';
import fetchPlaceholderTagsDependencies from '../../models/cloud-pipeline-api/fetch-tag-dependencies';
import getAvailableDataStorages from '../../models/cloud-pipeline-api/data-storage-available';

const ExtendedSettingsContext = React.createContext({});
export {ExtendedSettingsContext};

function mapSubOption(key, subOption) {
  return {
    key: subOption.key,
    title: subOption.key,
    type: 'radio',
    default: subOption.default,
    values: (subOption.options || []).map(o => o.value),
    itemName: o => o,
    itemValue: o => o,
    itemSubOptions: () => [],
    required: true,
    valuePresentation: o => o,
    valueHasSubOptions: () => false
  };
}

export default function useExtendedSettings () {
  const settings = useSettings();
  const [placeholdersMounts, setPlaceholderMounts] = useState({});
  const [dependencies, setDependencies] = useState({});
  const [sensitiveStorages, setSensitiveStorages] = useState([]);
  useEffect(() => {
    getAvailableDataStorages()
      .then((storages) => {
        setSensitiveStorages((storages || []).filter(o => o.sensitive));
      })
      .catch(() => {});
  }, [setSensitiveStorages]);
  const placeholders = (settings?.limitMounts || '')
    .split(',')
    .filter(o => Number.isNaN(Number(o)))
    .filter(o => !/^(default|user_default_storage|current_user_default_storage)$/.test(o))
    .map(placeholder => ({
      placeholder,
      config: settings?.limitMountsPlaceholders
        ? settings.limitMountsPlaceholders[placeholder]
        : undefined
    }))
    .filter(o => !!o.config);
  useEffect(() => {
    if (placeholders.length > 0) {
      fetchMountsForPlaceholders(placeholders)
        .then(setPlaceholderMounts);
    }
  }, [settings]);
  useEffect(() => {
    const uniqueStorages = Array.from(
      new Set(
        Object.values(placeholdersMounts || {})
          .reduce((r, c) => ([...r, ...c]), [])
          .map(r => r.id)
      )
    );
    fetchPlaceholderTagsDependencies(settings, uniqueStorages)
      .then(setDependencies)
  }, [settings, placeholdersMounts, setDependencies]);
  return useMemo(() => {
    const extendedSettings = [];
    const appendDivider = divider => {
      if (extendedSettings.length > 0) {
        extendedSettings.push(divider);
      }
    };
    if (settings?.appConfigNodeSizes) {
      const nodeSizes = Object.keys(settings?.appConfigNodeSizes || {});
      if (nodeSizes.length > 0) {
        extendedSettings.push({
          key: 'node-size',
          title: 'Size',
          type: 'radio',
          values: nodeSizes,
          required: false,
          optionsField: 'nodeSize',
          valuePresentation: (o => o),
          itemSubOptions: () => [],
          valueHasSubOptions: () => false
        });
      }
    }
    placeholders.forEach(placeholder => {
      const {
        placeholder: name,
        config = {}
      } = placeholder;
      const mounts = placeholdersMounts[name] || [];
      if (mounts.length > 0) {
        const title = config.title || name;
        const key = `limit-mounts-placeholder-${name}`;
        appendDivider({
          key: `${key}-divider`,
          type: 'divider',
        });
        extendedSettings.push({
          key,
          title,
          name,
          type: 'radio',
          default: Number(config.default),
          values: mounts,
          itemName: storage => storage.name,
          itemValue: storage => storage.id,
          itemSubOptions: storageId => (dependencies[storageId] || []).map(o => mapSubOption(key, o)),
          required: false,
          optionsField: `limitMountsPlaceholders.${name}`,
          valuePresentation(id) {
            const itemValue = this.itemValue;
            const item = (this.values || []).find(i => itemValue(i) === id);
            if (item) {
              return this.itemName(item);
            }
            return id;
          },
          valueHasSubOptions(id) {
            const itemValue = this.itemValue;
            const item = (this.values || []).find(i => itemValue(i) === id);
            if (item) {
              return this.itemSubOptions(item.id).length > 0;
            }
            return false;
          },
          isPlaceholder: true
        });
      }
    });
    if (sensitiveStorages.length > 0) {
      appendDivider({
        key: 'sensitive-storages-divider',
        type: 'divider',
      });
      extendedSettings.push({
        key: 'sensitive-storages',
        title: 'Mount sensitive data',
        type: 'multi-selection',
        values: sensitiveStorages,
        required: false,
        optionsField: 'sensitiveMounts',
        itemSubOptions: () => [],
        valueHasSubOptions: () => false,
        valuePresentation(values = []) {
          const itemName = this.itemName;
          if (!values || !values.length) {
            return 'none';
          }
          if (values.length === 1) {
            const itemValue = this.itemValue;
            const item = (this.values || []).find(i => itemValue(i) === values[0]);
            if (item) {
              return itemName(item);
            }
          }
          return `${values.length} storage${values.length > 1 ? 's' : ''}`;
        },
        itemName: storage => storage.name,
        itemValue: storage => storage.id,
        availableForApp: app => app && app.allowSensitive
      });
    }
    appendDivider({
      key: 'persist-session-state-divider',
      type: 'divider',
    });
    extendedSettings.push({
      key: 'persist-session-state',
      title: 'Persist session state',
      type: 'boolean',
      values: [],
      default: true,
      required: false,
      optionsField: 'persistSessionState',
      itemSubOptions: () => [],
      valueHasSubOptions: () => false,
      availableForApp: () => true
    });
    return {
      appExtendedSettings: extendedSettings,
      dependencies
    };
  }, [settings, placeholdersMounts, dependencies, sensitiveStorages]);
}
