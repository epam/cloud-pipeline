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
import {computed, isObservableArray, makeObservable} from 'mobx';
import escapeRegExp, {ESCAPE_CHARACTERS} from '../../utils/escape-reg-exp';
import {parsePermissionsRestrictionsConfig} from './utilities/parse-permissions-restrictions';
import {parseRunActionCriteria} from '../../components/runs/actions/actions-availability/utilities';
import {
  systemCapabilitiesParameters,
  RUN_CAPABILITIES,
  RUN_CAPABILITIES_PARAMETERS
} from '../../components/pipelines/launch/form/utilities/parameters';

const FETCH_ID_SYMBOL = Symbol('Fetch id');
// eslint-disable-next-line max-len
const MAINTENANCE_MODE_DISCLAIMER = 'Platform is in a maintenance mode, operation is temporary unavailable';

const SYSTEM_CAPABILITY_PARAMETER_TO_DISPLAY = Object.entries(RUN_CAPABILITIES_PARAMETERS)
  .reduce((acc, [name, parameter]) => ({
    ...acc,
    [parameter]: name
  }), {});

class PreferencesLoad extends Remote {
  constructor () {
    super();
    makeObservable(this, {
      deploymentName: computed,
      myCostsDisclaimer: computed,
      useSpot: computed,
      toolScanningEnabled: computed,
      maximumFileSize: computed,
      forceToolScanningEnabled: computed,
      searchEnabled: computed,
      searchExportTemplates: computed,
      searchPromptTemplate: computed,
      billingEnabled: computed,
      billingAdminsEnabled: computed,
      allowedMasterPriceTypes: computed,
      storageMountsPerGBRatio: computed,
      nfsSensitivePolicy: computed,
      facetedFiltersDictionaries: computed,
      displayNameTag: computed,
      storageFileDisplayNameTag: computed,
      facetedFilterDownloadFileTag: computed,
      storageDownloadAttribute: computed,
      metadataSystemKeys: computed,
      storageAllowSignedUrls: computed,
      hiddenObjects: computed,
      searchExtraFieldsConfiguration: computed,
      searchColumnsOrder: computed,
      versionStorageIgnoredFiles: computed,
      metadataMandatoryKeys: computed,
      groupsUIPreferences: computed,
      vsiPreviewMagnificationMultiplier: computed,
      gitlabIssueStatuses: computed,
      gitlabIssueDefaultFilters: computed,
      sharedStoragesSystemDirectory: computed,
      sharedStoragesDefaultPermissions: computed,
      launchCapabilities: computed,
      webdavStorageAccessDurationSeconds: computed,
      storageSizeRequestDisclaimer: computed,
      storageSortingPageSize: computed,
      systemMaintenanceMode: computed,
      systemMaintenanceModeBanner: computed,
      userNotificationsEnabled: computed,
      storagePolicyBackupVisibleNonAdmins: computed,
      autoscalingMultiQueuesTemplate: computed,
      hcsAnalysisConfiguration: computed,
      dataStorageItemPreviewMasks: computed,
      inlineMetadataEntities: computed,
      allowCommitToOtherPersonalGroups: computed,
      commitMaxLayers: computed,
      systemRunTagDateSuffix: computed,
      hiddenRunCapabilities: computed,
      toolJobMaintenanceConfiguration: computed,
      pipelineJobMaintenanceConfiguration: computed,
      launchDiskSizeThresholds: computed,
      facetedFilterDownload: computed,
      uiRunsCounterFilter: computed,
      uiRunsFilters: computed,
      uiRunsOwnersFilter: computed,
      uiRunsClusterDetailsShowActiveOnly: computed,
      uiRunsTags: computed,
      uiRunsUserTags: computed,
      uiToolsFilters: computed,
      systemJobsPipelineId: computed,
      systemJobsOutputPipelineTask: computed,
      systemJobsScriptsLocation: computed,
      systemLdapUserBlockMonitorGracePeriodDays: computed,
      uiPersonalToolsPermissionsRestrictions: computed,
      uiStoragesPermissionsRestrictions: computed,
      uiPersonalToolsLaunchWarningEnabled: computed,
      uiCWLToolGroups: computed,
      storageTagRestrictedAccess: computed,
      uiUploadChunkCount: computed,
      uiUploadChunkSizeMB: computed,
      uiContinueRunConfirmation: computed,
      storageManagementRestrictedAccess: computed,
      systemRunFilterMaxPageSize: computed,
      uiQuickSearchDisabled: computed,
      uiStandaloneNodesAllowTerminate: computed,
      uiClusterMonitoringAdminsAllowRange: computed,
      uiLaunchParameters: computed,
      uiRunActions: computed,
      uiMlflowSettings: computed,
      miscAIPreferences: computed,
      launchReservationParameters: computed,
      launchJWTTokenExpirationUserLimit: computed,
      launchDockerPreflightChecks: computed
    });
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

  get deploymentName () {
    return this.getPreferenceValue('ui.pipeline.deployment.name');
  }

  get myCostsDisclaimer () {
    return this.getPreferenceValue('ui.my.costs.disclaimer');
  }

  get useSpot () {
    return `${this.getPreferenceValue('cluster.spot')}` === 'true';
  }

  get toolScanningEnabled () {
    return `${this.getPreferenceValue('security.tools.scan.enabled')}` === 'true';
  }

  get maximumFileSize () {
    return +this.getPreferenceValue('misc.max.tool.icon.size.kb') || undefined;
  }

  get forceToolScanningEnabled () {
    return `${this.getPreferenceValue('security.tools.scan.all.registries')}` === 'true';
  }

  get searchEnabled () {
    return !!this.getPreferenceValue('search.elastic.host');
  }

  get searchExportTemplates () {
    const value = this.getPreferenceValue('search.export.template.mapping');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "search.export.template.mapping:', e);
      }
    }
    return undefined;
  }

  get searchPromptTemplate () {
    return this.getPreferenceValue('search.prompt.template');
  }

  get billingEnabled () {
    const value = this.getPreferenceValue('billing.reports.enabled');
    return value && `${value}`.toLowerCase() === 'true';
  }

  get billingAdminsEnabled () {
    const value = this.getPreferenceValue('billing.reports.enabled.admins');
    return value && `${value}`.toLowerCase() === 'true';
  }

  get allowedMasterPriceTypes () {
    const value = this.getPreferenceValue('cluster.allowed.price.types.master') || '';
    if (!value) {
      return [true, false];
    }
    return value.split(',').map(v => /^spot$/i.test(v));
  }

  get storageMountsPerGBRatio () {
    const value = this.getPreferenceValue('storage.mounts.per.gb.ratio');
    if (!value || Number.isNaN(value)) {
      return undefined;
    }
    return Number(value);
  }

  get nfsSensitivePolicy () {
    return this.getPreferenceValue('storage.mounts.nfs.sensitive.policy');
  }

  get facetedFiltersDictionaries () {
    const value = this.getPreferenceValue('faceted.filter.dictionaries');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "faceted.filter.dictionaries" preference:', e);
      }
    }
    return {};
  }

  get displayNameTag () {
    return this.getPreferenceValue('faceted.filter.display.name.tag');
  }

  get storageFileDisplayNameTag () {
    return this.getPreferenceValue('faceted.filter.storage.display.file.name.tag');
  }

  get facetedFilterDownloadFileTag () {
    return this.getPreferenceValue('faceted.filter.download.file.tag');
  }

  get storageDownloadAttribute () {
    return this.getPreferenceValue('ui.storage.download.attribute');
  }

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

  get storageAllowSignedUrls () {
    return `${this.getPreferenceValue('storage.allow.signed.urls')}` !== 'false';
  }

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

  get searchExtraFieldsConfiguration () {
    const value = this.getPreferenceValue('search.elastic.index.metadata.fields');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "search.elastic.index.metadata.fields" preference:', e);
      }
    }
    return {};
  }

  get searchColumnsOrder () {
    const value = this.getPreferenceValue('ui.search.columns.order');
    if (value && typeof value === 'string') {
      const tryParseAsJSON = () => {
        try {
          return JSON.parse(value);
        } catch (e) {
          console.warn('Error parsing "search.columns.order" preference:', e);
        }
        return undefined;
      };
      const tryParseAsString = () => {
        try {
          return value
            .split(/[,\s;]/)
            .map((item) => item.trim())
            .filter((item) => item.length);
        } catch (e) {
          console.warn('Error parsing "search.columns.order" preference:', e);
        }
        return undefined;
      };
      return tryParseAsJSON() || tryParseAsString() || [];
    }
    return [];
  }

  get versionStorageIgnoredFiles () {
    const value = this.getPreferenceValue('storage.version.storage.ignored.files');
    if (!value) {
      return ['.gitkeep'];
    }
    return (value || '').split(',').map(o => o.trim());
  }

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

  get groupsUIPreferences () {
    const value = this.getPreferenceValue('misc.groups.ui.preferences');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "misc.groups.ui.preferences" preference:', e);
      }
    }
    return {};
  }

  get vsiPreviewMagnificationMultiplier () {
    const value = this.getPreferenceValue('ui.wsi.magnification.factor');
    if (value && !Number.isNaN(Number(value))) {
      return Number(value);
    }
    return 1;
  }

  get gitlabIssueStatuses () {
    const value = this.getPreferenceValue('git.gitlab.issue.statuses');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "git.gitlab.issue.statuses" preference:', e);
      }
    }
    return [];
  }

  get gitlabIssueDefaultFilters () {
    const value = this.getPreferenceValue('git.gitlab.issue.default.filter');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "git.gitlab.issue.default.filter" preference:', e);
      }
    }
    return undefined;
  }

  get sharedStoragesSystemDirectory () {
    const value = this.getPreferenceValue('data.sharing.storage.folders.directory');
    if (value && !Number.isNaN(Number(value))) {
      return Number(value);
    }
    return undefined;
  }

  get sharedStoragesDefaultPermissions () {
    const value = this.getPreferenceValue('data.sharing.storage.folders.default.permissions');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn(
          'Error parsing "data.sharing.storage.folders.default.permissions" preference:',
          e
        );
      }
    }
    return {};
  }

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
          if (typeof entry === 'boolean' || entry.visible === false) {
            return undefined;
          }
          const isSystemCapability = systemCapabilitiesParameters.includes(key);
          if (isSystemCapability) {
            const displayName = SYSTEM_CAPABILITY_PARAMETER_TO_DISPLAY[key];
            if (!displayName) {
              return undefined;
            }
            return {
              value: displayName,
              name: entry?.name || displayName,
              custom: false,
              ...(entry?.description !== undefined
                ? {description: entry.description}
                : {}),
              ...(entry?.platforms !== undefined
                ? {platforms: parsePlatforms(entry.platforms)}
                : {}),
              ...(entry?.cloud !== undefined
                ? {cloud: parseCloudProviders(entry.cloud)}
                : {}),
              ...(entry?.os !== undefined
                ? {os: parseOS(entry.os)}
                : {}),
              ...(entry?.disclaimer !== undefined
                ? {disclaimer: entry.disclaimer}
                : {}),
              ...(entry?.privileged !== undefined
                ? {privileged: entry.privileged}
                : {})
            };
          }
          const {
            capabilities: childCapabilities = {}
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
            capabilities: Object.entries(childCapabilities)
              .map(mapCapability),
            multiple: Boolean(entry?.multiple),
            privileged: entry?.privileged
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

  get webdavStorageAccessDurationSeconds () {
    const value = this.getPreferenceValue('storage.webdav.access.duration.seconds');
    if (value && !Number.isNaN(Number(value))) {
      return Number(value);
    }
    return 86400; // 24 hours
  }

  get storageSizeRequestDisclaimer () {
    return this.getPreferenceValue('ui.storage.refresh.request');
  }

  get storageSortingPageSize () {
    const defaultLimit = 1000;
    const value = this.getPreferenceValue('storage.listing.filter.items.limit');
    if (value && !Number.isNaN(Number(value))) {
      return Number(value);
    }
    return defaultLimit;
  }

  get systemMaintenanceMode () {
    return `${this.getPreferenceValue('system.maintenance.mode')}` === 'true' ||
      `${this.getPreferenceValue('system.blocking.maintenance.mode')}` === 'true';
  }

  get systemMaintenanceModeBanner () {
    return this.getPreferenceValue('system.maintenance.mode.banner');
  }

  get userNotificationsEnabled () {
    return `${this.getPreferenceValue('system.notifications.enable')}` === 'true';
  }

  get storagePolicyBackupVisibleNonAdmins () {
    const value = this.getPreferenceValue('storage.policy.backup.visible.non.admins');
    return value === undefined || `${value}` !== 'false';
  }

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

  get hcsAnalysisConfiguration () {
    const value = this.getPreferenceValue('ui.hcs.analysis.configuration');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('ui.hcs.analysis.configuration:', e);
      }
    }
    return {};
  }

  get dataStorageItemPreviewMasks () {
    const extensions = this.getPreferenceValue('ui.storage.static.preview.mask') || '';
    return extensions
      .split(/[,;\s]/g)
      .filter(o => o.length)
      .map(o => o.startsWith('.') ? o.slice(1) : o)
      .map(o => new RegExp(`\\.${o}$`, 'i'));
  }

  get inlineMetadataEntities () {
    const value = this.getPreferenceValue('ui.library.metadata.inline');
    return `${value}`.toLowerCase() === 'true';
  }

  get dataSharingBaseApi () {
    return this.getPreferenceValue('data.sharing.base.api');
  }

  get dataSharingEnabled () {
    return !!this.dataSharingBaseApi;
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

  get uiVscodeExtensionInstallTemplate () {
    const value = this.getPreferenceValue('ui.vscode.extension.install.template');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn(
          'Error parsing "ui.vscode.extension.install.template" preference:',
          e
        );
      }
    }
    return {};
  }

  get launchToolSizeLimits () {
    const value = this.getPreferenceValue('launch.tool.size.limits');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "launch.tool.size.limits" preference:', e.message);
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

  get toolOSWarningText () {
    return this.getPreferenceValue('ui.tools.os.with.warning');
  }

  get allowCommitToOtherPersonalGroups () {
    const value = this.getPreferenceValue('commit.allow.other.personal.group');
    return (value || '').toLowerCase() !== 'false';
  }

  get commitMaxLayers () {
    const value = this.getPreferenceValue('commit.max.layers');
    if (!value || Number.isNaN(Number(value))) {
      return undefined;
    }
    return Number(value);
  }

  get systemRunTagDateSuffix () {
    return this.getPreferenceValue('system.run.tag.date.suffix') || '_date';
  }

  get hiddenRunCapabilities () {
    const value = this.getPreferenceValue('launch.capabilities');
    if (value) {
      try {
        const capabilities = JSON.parse(value);
        const getCapabilityByKey = (key) => {
          if (systemCapabilitiesParameters.includes(key)) {
            return SYSTEM_CAPABILITY_PARAMETER_TO_DISPLAY[key];
          }
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
            (typeof value === 'object' && value.visible === false)
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

  get toolJobMaintenanceConfiguration () {
    return this.getJobMaintenanceConfigurationRules('ui.run.maintenance.tool.enabled');
  }

  get pipelineJobMaintenanceConfiguration () {
    return this.getJobMaintenanceConfigurationRules('ui.run.maintenance.pipeline.enabled');
  }

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

  /**
   * @typedef {object} DownloadCommandTemplate
   * @property {string} template
   * @property {string} [before]
   * @property {string} [after]
   */

  /**
   * @typedef {object} FacetedFilterDownloadConfiguration
   * @property {RegExp[]} allow
   * @property {RegExp[]} deny
   * @property {number} [maximum]
   * @property {{[group: string]: DownloadCommandTemplate}} command
   */

  /**
   * @returns {FacetedFilterDownloadConfiguration}
   */
  get facetedFilterDownload () {
    const processMask = (mask) => {
      if (!mask) {
        return /.+/;
      }
      let escaped = escapeRegExp(mask, ESCAPE_CHARACTERS.filter((ch) => ch !== '*'));
      escaped = escaped.replace(/[*]/g, '.+');
      if (/^[\\/]/.test(escaped)) {
        escaped = '^'.concat(escaped);
      } else {
        escaped = '(^|\\/|\\\\)'.concat(escaped);
      }
      escaped = escaped.concat('$');
      return new RegExp(escaped, 'i');
    };
    const processMasks = (masks) => {
      if (typeof masks === 'string') {
        return [processMask(masks)];
      }
      return masks.map(processMask);
    };
    const processCommandTemplate = (command) => {
      if (!command) {
        return {};
      }
      if (typeof command === 'string') {
        return {
          default: {
            template: command
          }
        };
      }
      if (
        typeof command === 'object' &&
        typeof command.template === 'string'
      ) {
        return {
          default: command
        };
      }
      const keys = Object.keys(command);
      return keys
        .map((key) => {
          const value = command[key];
          if (typeof value === 'string') {
            return {
              [key]: {template: value}
            };
          }
          return {[key]: value};
        })
        .reduce((r, c) => ({...r, ...c}), {});
    };
    const processPreference = (preference = {}) => {
      const {
        allow = [],
        deny = [],
        command,
        ...rest
      } = preference || {};
      return {
        allow: processMasks(allow),
        deny: processMasks(deny),
        command: processCommandTemplate(command),
        ...rest
      };
    };
    const value = this.getPreferenceValue('faceted.filter.download');
    if (value) {
      try {
        return processPreference(JSON.parse(value));
      } catch (e) {
        console.warn('Error parsing "ui.tool.kube.labels" preference:', e.message);
      }
    }
    return processPreference();
  }

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

  get uiRunsOwnersFilter () {
    const value = this.getPreferenceValue('ui.runs.owners.filter');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "ui.runs.owners.filter" preference:', e.message);
      }
    }
    return {};
  }

  get uiRunsClusterDetailsShowActiveOnly () {
    const value = this.getPreferenceValue('ui.runs.cluster.details.show.active.only');
    return (value || '').toLowerCase() !== 'false';
  }

  get uiRunsTags () {
    const value = this.getPreferenceValue('ui.runs.tags');
    if (value) {
      try {
        const result = JSON.parse(value);
        if (!Array.isArray(result)) {
          throw new Error(`array expected, got ${typeof result}`);
        }
        return result.map((o) => {
          const {
            // eslint-disable-next-line camelcase
            user_tag = false,
            userTag = user_tag,
            ...rest
          } = o;
          return {
            ...rest,
            userTag: `${userTag}`.toLowerCase() === 'true'
          };
        });
      } catch (e) {
        console.warn('Error parsing "ui.runs.tags" preference:', e.message);
      }
    }
    return [];
  }

  get uiRunsUserTags () {
    return this.uiRunsTags.filter((tag) => tag.userTag);
  }

  get uiToolsFilters () {
    const value = this.getPreferenceValue('ui.tools.filters');
    if (value) {
      try {
        const {
          groups = [],
          ...rest
        } = JSON.parse(value);
        return {
          ...rest,
          groups: groups.map((aGroup) => ({
            ...aGroup,
            name: aGroup.name || aGroup.title || aGroup.id
          }))
        };
      } catch (e) {
        console.warn('Error parsing "ui.tools.filters" preference:', e.message);
      }
    }
    return {};
  }

  get systemJobsPipelineId () {
    const value = this.getPreferenceValue('system.jobs.pipeline.id');
    if (value && !Number.isNaN(Number(value))) {
      return Number(value);
    }
    return undefined;
  }

  get systemJobsOutputPipelineTask () {
    return this.getPreferenceValue('system.jobs.output.pipeline.task') || 'SystemJob';
  }

  get systemJobsScriptsLocation () {
    return this.getPreferenceValue('system.jobs.scripts.location') || 'src/system-jobs';
  }

  get systemLdapUserBlockMonitorGracePeriodDays () {
    const value = this.getPreferenceValue('system.ldap.user.block.monitor.grace.period.days');
    if (
      value !== undefined &&
      value !== null &&
      !Number.isNaN(Number(value))
    ) {
      return Number(value);
    }
    return 7;
  }

  /**
   * @returns {{role: string, disabledMask: number, defaultMask: number}[]}
   */
  get uiPersonalToolsPermissionsRestrictions () {
    const value = this.getPreferenceValue('ui.personal.tools.permissions.restrictions');
    const defaultValue = [{
      role: 'ALL',
      disable: 'WRITE'
    }];
    let restrictions = defaultValue;
    if (value && value.length) {
      try {
        restrictions = JSON.parse(value);
        if (!Array.isArray(restrictions) && !isObservableArray(restrictions)) {
          restrictions = defaultValue;
          throw new Error('wrong format (should be array)');
        }
      } catch (e) {
        // eslint-disable-next-line max-len
        console.warn('Error parsing "ui.personal.tools.permissions.restrictions" preference:', e.message);
      }
    }
    return parsePermissionsRestrictionsConfig(restrictions);
  }

  /**
   * @returns {{role: string, disabledMask: number, defaultMask: number}[]}
   */
  get uiStoragesPermissionsRestrictions () {
    const value = this.getPreferenceValue('ui.storages.permissions.restrictions');
    const defaultValue = [];
    let restrictions = defaultValue;
    if (value && value.length) {
      try {
        restrictions = JSON.parse(value);
        if (!Array.isArray(restrictions) && !isObservableArray(restrictions)) {
          restrictions = defaultValue;
          throw new Error('wrong format (should be array)');
        }
      } catch (e) {
        // eslint-disable-next-line max-len
        console.warn('Error parsing "ui.storages.permissions.restrictions" preference:', e.message);
      }
    }
    return parsePermissionsRestrictionsConfig(restrictions);
  }

  get uiPersonalToolsLaunchWarningEnabled () {
    const value = this.getPreferenceValue('ui.personal.tools.launch.warning.enabled');
    return value && `${value}`.toLowerCase() === 'true';
  }

  get uiCWLToolGroups () {
    const value = this.getPreferenceValue('ui.cwl.tool.groups');
    return (value || 'library')
      .split(/[\s,;]/)
      .filter((group) => group.length > 0);
  }

  get storageTagRestrictedAccess () {
    const value = this.getPreferenceValue('storage.tag.restricted.access');
    return value && `${value}`.toLowerCase() === 'true';
  }

  get uiUploadChunkCount () {
    const value = this.getPreferenceValue('ui.upload.chunk.count');
    if (value && !Number.isNaN(Number(value)) && Number(value) > 0) {
      return Number(value);
    }
    return undefined;
  }

  get uiUploadChunkSizeMB () {
    const value = this.getPreferenceValue('ui.upload.chunk.size.mb');
    if (value && !Number.isNaN(Number(value)) && Number(value) > 0) {
      return Number(value);
    }
    return undefined;
  }

  get uiContinueRunConfirmation () {
    return this.getPreferenceValue('ui.continue.run.confirmation');
  }

  get storageManagementRestrictedAccess () {
    const value = this.getPreferenceValue('storage.management.restricted.access');
    return value && `${value}`.toLowerCase() === 'true';
  }

  get systemRunFilterMaxPageSize () {
    const value = this.getPreferenceValue('system.run.filter.max.page.size');
    if (value && !Number.isNaN(Number(value)) && Number(value) > 0) {
      return Number(value);
    }
    return 500;
  }

  get uiQuickSearchDisabled () {
    const value = this.getPreferenceValue('ui.quick.search.disabled');
    return value && `${value}`.toLowerCase() === 'true';
  }

  get uiStandaloneNodesAllowTerminate () {
    const value = this.getPreferenceValue('ui.standalone.nodes.allow.terminate');
    return !value || `${value}`.toLowerCase() !== 'false';
  }

  get uiClusterMonitoringAdminsAllowRange () {
    const value = this.getPreferenceValue('ui.cluster.monitoring.admins.allow.range');
    return value && `${value}`.toLowerCase() === 'true';
  }

  get uiLaunchParameters () {
    const value = this.getPreferenceValue('ui.launch.parameters');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "ui.launch.parameters" preference:', e.message);
      }
    }
    return {};
  }

  get uiRunActions () {
    const value = this.getPreferenceValue('ui.run.actions');
    if (value) {
      try {
        const cfg = JSON.parse(value);
        if (typeof cfg === 'object') {
          const result = {};
          for (const [key, value] of Object.entries(cfg)) {
            result[key] = parseRunActionCriteria(key, value);
          }
          return result;
        }
        throw Error(`unsupported ui.run.actions format. expected object, got ${typeof cfg}`);
      } catch (e) {
        console.warn('Error parsing "ui.run.actions" preference:', e.message);
      }
    }
    return {};
  }

  get uiMlflowSettings () {
    const value = this.getPreferenceValue('ui.mlflow.settings');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "ui.mlflow.settings" preference:', e.message);
      }
    }
    return undefined;
  }

  get miscAIPreferences () {
    const value = this.getPreferenceValue('misc.ai.preferences');
    if (value) {
      try {
        const cfg = JSON.parse(value);
        if (typeof cfg === 'object') {
          return cfg;
        }
        throw new Error(`unsupported type "${typeof cfg}"`);
      } catch (e) {
        console.warn('Error parsing "misc.ai.preferences" preference:', e.message);
      }
    }
    return {};
  }

  get launchReservationParameters () {
    const value = this.getPreferenceValue('launch.reservation.parameters');
    if (value) {
      try {
        return JSON.parse(value);
      } catch (e) {
        console.warn('Error parsing "launch.reservation.parameters" preference:', e.message);
      }
    }
    return undefined;
  }

  get launchJWTTokenExpirationUserLimit () {
    const value = this.getPreferenceValue('launch.jwt.token.expiration.user.limit');
    if (value && !Number.isNaN(Number(value)) && Number(value) > 0) {
      return Number(value);
    }
    return 0;
  }

  get launchDockerPreflightChecks () {
    const value = this.getPreferenceValue('launch.docker.preflight.checks') || 'true';
    return `${value}`.toLowerCase() !== 'false';
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
