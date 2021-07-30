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

  @computed
  get versionStorageIgnoredFiles () {
    const value = this.getPreferenceValue('storage.version.storage.ignored.files');
    if (!value) {
      return ['.gitkeep'];
    }
    return (value || '').split(',').map(o => o.trim());
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

export {FETCH_ID_SYMBOL};
export default new PreferencesLoad();
