/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

export default function parseCapabilityCloudSetting (cloudSetting) {
  const [providerString, ...rest] = cloudSetting.split(':');
  let cloud;
  let region;
  if (/^(aws|azure|gcp)$/i.test(providerString)) {
    cloud = providerString;
    region = rest.length ? rest.join(':') : undefined;
  } else {
    region = cloudSetting;
  }
  if (Number.isNaN(Number(region))) {
    return {cloud, regionIdentifier: region};
  }
  return {cloud, regionId: Number(region)};
}
