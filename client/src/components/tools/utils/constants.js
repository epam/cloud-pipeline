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

// latest - the version is latest
// allowedToExecute - is admin or we're within grace period
// (not 'allowedToExecute' flag received from server!)
export const ScanStatusDescriptionsFn = (latest = false, allowedToExecute = false) => {
  let againstSecurityPolicy = allowedToExecute
    ? `This Run is pulling the ${latest ? 'latest ' : ''}version which has inappropriate security status.`
    : `This Run is pulling the ${latest ? 'latest ' : ''}version which unfortunately couldn't be run due to security status.`;
  let notScanned = `The ${latest ? 'latest ' : ''}version shall be scanned for vulnerabilities.`;
  let vulnerabilitiesNumberExceeds = `The ${latest ? 'latest ' : ''}version vulnerabilities exceed the security threshold.`;
  if (!allowedToExecute) {
    const extra = latest ? 'an older one' : 'another one';
    againstSecurityPolicy += ` Try to find ${extra}.`;
    notScanned += ` You can try ${extra}.`;
    vulnerabilitiesNumberExceeds += ` You can try ${extra}.`;
  }
  return {
    againstSecurityPolicy,
    notScanned,
    vulnerabilitiesNumberExceeds
  };
};

export const LaunchMessages = (gracePeriodEnd) => {
  const extra = gracePeriodEnd
    ? `, but you can launch it during the grace period (till ${gracePeriodEnd})`
    : '';
  let launchNotScanned = `The version shall be scanned for security vulnerabilities${extra}. Run anyway?`;
  let vulnerabilitiesNumberExceeds = `The version has a critical number of vulnerabilities${extra}. Run anyway?`;
  let againstSecurityPolicy = `The version doesn't meet security policy${extra}. Run anyway?`;
  return {
    launchNotScanned,
    vulnerabilitiesNumberExceeds,
    againstSecurityPolicy
  };
};

export const ScanStatuses = {
  completed: 'COMPLETED',
  pending: 'PENDING',
  failed: 'FAILED',
  notScanned: 'NOT_SCANNED'
};
