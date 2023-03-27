/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import Remote from '../basic/Remote';
import {computed} from 'mobx';

const FETCH_ID_SYMBOL = Symbol('Fetch id');
// eslint-disable-next-line max-len
const MAINTENANCE_MODE_DISCLAIMER = 'Platform is in a maintenance mode, operation is temporary unavailable';

export const RUN_CAPABILITIES = {
  dinD: 'DinD',
  singularity: 'Singularity',
  systemD: 'SystemD',
  noMachine: 'NoMachine',
  module: 'Module',
  disableHyperThreading: 'Disable Hyper-Threading',
  dcv: 'NICE DCV'
};

class PreferencesLoad extends Remote {
  constructor () {
    super();
    this.url = '/preferences';
    this[FETCH_ID_SYMBOL] = 0;
  }

  update (value) {
    this[FETCH_ID_SYMBOL] += 1;
    super.update(value);
  }

  postprocess (value) {
    if (value.payload) {
      const formatJson = (string, presentation = true, catchError = true) => {
        if (!string) {
          return string;
        }
        try {
          return JSON.stringify(JSON.parse(string), null, presentation ? ' ' : undefined);
        } catch (e) {
          if (!catchError) {
            throw e;
          }
        }
        return string;
      };
      value.payload.forEach(preference => {
        if (preference.type === 'OBJECT') {
          preference.value = formatJson(preference.value);
        }
      });
    }
    return value.payload;
  }

  @computed
  get deploymentName () {
    return this.getPreferenceValue('ui.pipeline.deployment.name');
  }

  @computed
  get myCostsDisclaimer () {
    return this.getPreferenceValue('ui.my.costs.disclaimer');
  }

  @computed
  get useSpot () {
    return `${this.getPreferenceValue('cluster.spot')}` === 'true';
  }

  @computed
  get toolScanningEnabled () {
    return `${this.getPreferenceValue('security.tools.scan.enabled')}` === 'true';
  }

  @computed
  get maximumFileSize () {
    return +this.getPreferenceValue('misc.max.tool.icon.size.kb') || undefined;
  }

  @computed
  get forceToolScanningEnabled () {
    return `${this.getPreferenceValue('security.tools.scan.all.registries')}` === 'true';
  }

  @computed
  get searchEnabled () {
    return !!this.getPreferenceValue('search.elastic.host');
  }

  @computed
  get billingEnabled () {
    const value = this.getPreferenceValue('billing.reports.enabled');
    return value && `${value}`.toLowerCase() === 'true';
  }

  @computed
  get billingAdminsEnabled () {
    const value = this.getPreferenceValue('billing.reports.enabled.admins');
    return value && `${value}`.toLowerCase() === 'true';
  }

  @computed
  get allowedMasterPriceTypes () {
    const value = this.getPreferenceValue('cluster.allowed.price.types.master') || '';
    if (!value) {
      return [true, false];
    }
    return value.split(',').map(v => /^spot$/i.test(v));
  }

  @computed
  get storageMountsPerGBRatio () {
    const value = this.getPreferenceValue('storage.mounts.per.gb.ratio');
    if (!value || Number.isNaN(value)) {
      return undefined;
    }
    return Number(value);
  }

  @computed
  get nfsSensitivePolicy () {
    return this.getPreferenceValue('storage.mounts.nfs.sensitive.policy');
  }

  @computed
  get metadataSystemKeys () {
    const value = this.getPreferenceValue('misc.metadata.sensitive.keys');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "misc.metadata.sensitive.keys" preference:', e);
      }
    }
    return [];
  }

  @computed
  get storageAllowSignedUrls () {
    return `${this.getPreferenceValue('storage.allow.signed.urls')}` !== 'false';
  }

  @computed
  get hiddenObjects () {
    const value = this.getPreferenceValue('ui.hidden.objects');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "ui.hidden.objects" preference:', e);
      }
    }
    return {};
  }

  @computed
  get metadataMandatoryKeys () {
    const value = this.getPreferenceValue('misc.metadata.mandatory.keys');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "misc.metadata.mandatory.keys" preference:', e);
      }
    }
    return [];
  }

  @computed
  get launchCapabilities () {
    const value = this.getPreferenceValue('launch.capabilities');
    if (value) {
      try {
        const capabilities = JSON.parse(value);
        const parsePlatforms = o => {
          if (!o) {
            return [];
          }
          if (Array.isArray(o)) {
            return o.slice();
          }
          if (typeof o === 'string') {
            return o.split(',').map(o => o.trim());
          }
          return [];
        };
        const parseOSValue = o => {
          if (!o) {
            return [];
          }
          if (Array.isArray(o)) {
            return o.slice();
          }
          if (typeof o === 'string') {
            return o.split(',').map(o => o.trim());
          }
          return [];
        };
        const parseOS = o => parseOSValue(o)
          .map(mask => mask.trim())
          .filter(mask => mask.length);
        const parseCloudProviders = o => {
          if (o && /^all$/i.test(o.trim())) {
            return [];
          }
          return (o || '')
            .split(',')
            .map(o => o.trim().toLowerCase())
            .filter(o => o.length);
        };
        const mapCapability = ([key, entry]) => {
          if (
            typeof entry === 'boolean' ||
            entry.visible === false ||
            Object.keys(RUN_CAPABILITIES).includes(key)
          ) {
            return undefined;
          }
          const {
            capabilities = {}
          } = entry;
          return {
            value: `CP_CAP_CUSTOM_${key}`,
            name: entry?.name || key,
            description: entry?.description,
            platforms: parsePlatforms(entry?.platforms),
            cloud: parseCloudProviders(entry?.cloud),
            os: parseOS(entry?.os),
            custom: true,
            params: entry?.params || {},
            disclaimer: entry?.disclaimer || '',
            capabilities: Object.entries(capabilities)
              .map(c => mapCapability(c, entry)),
            multiple: Boolean(entry?.multiple)
          };
        };
        return Object
          .entries(capabilities || {})
          .map(mapCapability)
          .filter(Boolean);
      } catch (e) {
        console.warn(
          'Error parsing "launch.capabilities" preference:',
          e
        );
      }
    }
    return [];
  }

  @computed
  get webdavStorageAccessDurationSeconds () {
    const value = this.getPreferenceValue('storage.webdav.access.duration.seconds');
    if (value && !Number.isNaN(Number(value))) {
      return Number(value);
    }
    return 86400; // 24 hours
  }

  @computed
  get storageSizeRequestDisclaimer () {
    return this.getPreferenceValue('ui.storage.refresh.request');
  }

  @computed
  get systemMaintenanceMode () {
    return `${this.getPreferenceValue('system.maintenance.mode')}` === 'true' ||
      `${this.getPreferenceValue('system.blocking.maintenance.mode')}` === 'true';
  }

  @computed
  get systemMaintenanceModeBanner () {
    return this.getPreferenceValue('system.maintenance.mode.banner');
  }

  @computed
  get userNotificationsEnabled () {
    return `${this.getPreferenceValue('system.notifications.enable')}` === 'true';
  }

  @computed
  get storagePolicyBackupVisibleNonAdmins () {
    const value = this.getPreferenceValue('storage.policy.backup.visible.non.admins');
    return value === undefined || `${value}` !== 'false';
  }

  @computed
  get autoscalingMultiQueuesTemplate () {
    const value = this.getPreferenceValue('ge.autoscaling.scale.multi.queues.template');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "ge.autoscaling.scale.multi.queues.template:', e);
      }
    }
    return {};
  }

  get requestFileSystemAccessTooltip () {
    const value = this.getPreferenceValue('ui.pipe.file.browser.request');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn(
          'Error parsing "ui.pipe.file.browser.request" preference:',
          e
        );
      }
    }
    return {};
  }

  get toolPredefinedKubeLabels () {
    const value = this.getPreferenceValue('ui.tool.kube.labels');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "ui.tool.kube.labels" preference:', e.message);
      }
    }
    return [];
  }

  @computed
  get commitMaxLayers () {
    const value = this.getPreferenceValue('commit.max.layers');
    if (!value || Number.isNaN(Number(value))) {
      return undefined;
    }
    return Number(value);
  }

  @computed
  get systemRunTagDateSuffix () {
    return this.getPreferenceValue('system.run.tag.date.suffix') || '_date';
  }

  @computed
  get hiddenRunCapabilities () {
    const value = this.getPreferenceValue('launch.capabilities');
    if (value) {
      try {
        const capabilities = JSON.parse(value);
        const getCapabilityByKey = (key) => {
          const capabilityKey = Object
            .keys(RUN_CAPABILITIES)
            .find((aKey) => aKey.toLowerCase() === (key || '').toLowerCase());
          if (capabilityKey) {
            return RUN_CAPABILITIES[capabilityKey];
          }
          return undefined;
        };
        return Object
          .entries(capabilities || {})
          .filter(([, value]) => (typeof value === 'boolean' && !value) ||
            (typeof value === 'object' && !value.visible)
          )
          .map(([key]) => getCapabilityByKey(key))
          .filter(Boolean);
      } catch (e) {
        console.warn(
          'Error parsing "launch.capabilities" preference:',
          e
        );
      }
    }
    return [];
  }

  getJobMaintenanceConfigurationRules (preference) {
    const value = this.getPreferenceValue(preference);
    const defaultSettings = {
      pause: true,
      resume: true
    };
    try {
      return {
        ...defaultSettings,
        ...JSON.parse(value)
      };
    } catch (e) {
      console.warn(
        `Error parsing "${preference}" preference:`,
        e
      );
    }
    return defaultSettings;
  }

  @computed
  get toolJobMaintenanceConfiguration () {
    return this.getJobMaintenanceConfigurationRules('ui.run.maintenance.tool.enabled');
  }

  @computed
  get pipelineJobMaintenanceConfiguration () {
    return this.getJobMaintenanceConfigurationRules('ui.run.maintenance.pipeline.enabled');
  }

  @computed
  get launchDiskSizeThresholds () {
    const value = this.getPreferenceValue('launch.job.disk.size.thresholds');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "launch.job.disk.size.thresholds" preference:', e.message);
      }
    }
    return [];
  }

  @computed
  get uiRunsCounterFilter () {
    const value = this.getPreferenceValue('ui.runs.counter.filter');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "ui.runs.counter.filter" preference:', e.message);
      }
    }
    return undefined;
  }

  @computed
  get uiRunsFilters () {
    const value = this.getPreferenceValue('ui.runs.filters');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "ui.runs.filters" preference:', e.message);
      }
    }
    return [];
  }

  @computed
  get uiRunsClusterDetailsShowActiveOnly () {
    const value = this.getPreferenceValue('ui.runs.cluster.details.show.active.only');
    return (value || '').toLowerCase() !== 'false';
  }

  toolScanningEnabledForRegistry (registry) {
    return this.loaded &&
      this.toolScanningEnabled &&
      ((registry && registry.securityScanEnabled) || this.forceToolScanningEnabled);
  }

  getPreferenceValue = (key) => {
    if (!this.loaded) {
      return null;
    }
    return (this.value || []).filter(p => p.name === key).map(p => p.value)[0];
  };

  replacePlaceholders = (string) => {
    if (!this.loaded) {
      return string;
    }
    for (let i = 0; i < (this.value || []).length; i++) {
      const preference = this.value[i];
      const regexp = new RegExp('\\$\\{' + preference.name + '\\}', 'gm');
      string = string.replace(regexp, `${preference.value}` || '');
    }
    return string;
  };
}

export {FETCH_ID_SYMBOL, MAINTENANCE_MODE_DISCLAIMER};
export default new PreferencesLoad();
