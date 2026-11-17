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

// versionLabel - 'latest' or 'default' for the version, which is used if a user does not choose one
// ('default' - if it is a default version set for a tool), undefined for any other version
// allowedToExecute - is admin or we're within grace period
// (not 'allowedToExecute' flag received from server!)
export const ScanStatusDescriptionsFn = (versionLabel = undefined, allowedToExecute = false) => {
  const label = versionLabel ? `${versionLabel} ` : '';
  let againstSecurityPolicy = allowedToExecute
    ? `This Run is pulling the ${label}version which has inappropriate security status.`
    : `This Run is pulling the ${label}version which unfortunately couldn't be run due to security status.`;
  let notScanned = `The ${label}version shall be scanned for vulnerabilities.`;
  let vulnerabilitiesNumberExceeds = `The ${label}version vulnerabilities exceed the security threshold.`;
  let blackListed = `The ${label}version is on the black list.`;
  if (!allowedToExecute) {
    const extra = versionLabel === 'latest' ? 'an older one' : 'another one';
    againstSecurityPolicy += ` Try to find ${extra}.`;
    notScanned += ` You can try ${extra}.`;
    vulnerabilitiesNumberExceeds += ` You can try ${extra}.`;
    blackListed += ` Try to find ${extra}.`;
  }
  return {
    againstSecurityPolicy,
    notScanned,
    vulnerabilitiesNumberExceeds,
    blackListed
  };
};

export const LaunchMessages = (gracePeriodEnd) => {
  const extra = gracePeriodEnd
    ? `, but you can launch it during the grace period (till ${gracePeriodEnd})`
    : '';
  let launchNotScanned = `The version shall be scanned for security vulnerabilities${extra}. Run anyway?`;
  let vulnerabilitiesNumberExceeds = `The version has a critical number of vulnerabilities${extra}. Run anyway?`;
  let againstSecurityPolicy = `The version doesn't meet security policy${extra}. Run anyway?`;
  let blackListed = 'The version is on the black list.';
  return {
    launchNotScanned,
    vulnerabilitiesNumberExceeds,
    againstSecurityPolicy,
    blackListed
  };
};

export const ScanStatuses = {
  completed: 'COMPLETED',
  pending: 'PENDING',
  failed: 'FAILED',
  notScanned: 'NOT_SCANNED'
};
