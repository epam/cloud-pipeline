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

import {LaunchMessages, ScanStatuses, ScanStatusDescriptionsFn} from './constants';
import moment from 'moment-timezone';
import displayDate from '../../../utils/displayDate';

export default function getVersionRunningInfo (
  version,
  versions,
  scanPolicy,
  isAdmin,
  preferences,
  registry) {
  if (!preferences.toolScanningEnabledForRegistry(registry)) {
    return {
      allowedToExecute: true,
      tooltip: null,
      launchTooltip: null,
      notLoaded: false
    };
  }
  if (version && versions && scanPolicy) {
    const isGracePeriod = (versionObject) => {
      if (versionObject && versionObject.gracePeriod) {
        return moment.utc(versionObject.gracePeriod) > moment.utc();
      }
      return false;
    };
    const checkVulnerabilitiesNumber = (currentVersion) => {
      const vulnerabilities = currentVersion.vulnerabilitiesCount || {};
      const countCriticalVulnerabilities = vulnerabilities.Critical || 0;
      const countHighVulnerabilities = vulnerabilities.High || 0;
      const countMediumVulnerabilities = vulnerabilities.Medium || 0;
      return countCriticalVulnerabilities > scanPolicy.maxCriticalVulnerabilities ||
        countHighVulnerabilities > scanPolicy.maxHighVulnerabilities ||
        countMediumVulnerabilities > scanPolicy.maxMediumVulnerabilities;
    };
    const versionScanResult = versions[version]
      ? versions[version].scanResult
      : undefined;
    const versionPlatform = versions[version] && versions[version].attributes
      ? versions[version].attributes.platform
      : undefined;
    if (/^windows$/i.test(versionPlatform)) {
      return {
        allowedToExecute: true,
        tooltip: null,
        launchTooltip: null,
        notLoaded: false
      };
    }
    const allowedToExecuteFlag = versionScanResult
      ? versionScanResult.allowedToExecute
      : false;
    const {
      distribution,
      version: distrVersion,
      isAllowed = true
    } = (versionScanResult ? versionScanResult.toolOSVersion : undefined) || {};
    let tooltip, launchTooltip;
    let defaultTag;
    if (versions['latest']) {
      defaultTag = 'latest';
    } else if (Object.keys(versions).length === 1) {
      defaultTag = Object.keys(versions)[0];
    }
    const isGrace = isGracePeriod(versionScanResult);
    let gracePeriodEnd = isGrace && !isAdmin
      ? displayDate(versionScanResult.gracePeriod, 'D MMMM YYYY')
      : null;
    const isLatest = version === defaultTag;
    let allowedToExecute = allowedToExecuteFlag || isAdmin || isGrace;
    if (!isAllowed) {
      const distributionDescription = distribution
        ? ` (${distribution}${distrVersion ? ` ${distrVersion}` : ''})`
        : '';
      tooltip = `This distribution${distributionDescription} is not supported.`;
      launchTooltip = `This distribution${distributionDescription} is not supported. Run anyway?`;
    } else if (versionScanResult && checkVulnerabilitiesNumber(versionScanResult)) {
      tooltip = ScanStatusDescriptionsFn(isLatest, isGrace || isAdmin).vulnerabilitiesNumberExceeds;
      launchTooltip = LaunchMessages(gracePeriodEnd).vulnerabilitiesNumberExceeds;
    } else if (versionScanResult && versionScanResult.status === ScanStatuses.notScanned) {
      tooltip = ScanStatusDescriptionsFn(isLatest, isGrace || isAdmin).notScanned;
      launchTooltip = LaunchMessages(gracePeriodEnd).launchNotScanned;
    } else if (!allowedToExecuteFlag) {
      tooltip = ScanStatusDescriptionsFn(isLatest, isGrace || isAdmin).againstSecurityPolicy;
      launchTooltip = LaunchMessages(gracePeriodEnd).againstSecurityPolicy;
    }
    return {
      allowedToExecute,
      tooltip,
      launchTooltip,
      notLoaded: false
    };
  }
  return {
    allowedToExecute: false,
    tooltip: null,
    launchTooltip: null,
    notLoaded: true
  };
}
