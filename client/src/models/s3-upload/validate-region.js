/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

// A host name label: 1 to 63 letters, digits and hyphens, not starting or ending with a hyphen
const REGION_REGEXP = /^(?!-)(?!.*-$)[a-zA-Z0-9-]{1,63}$/;

/**
 * aws-sdk v2 builds the endpoint host name from the region and does not check it,
 * so a region is accepted only when it is a valid host name label, as aws-sdk v3 does.
 * An empty region is returned as it is: aws-sdk then uses its default one.
 * @param {string} region
 * @returns {string}
 */
export default function validateRegion (region) {
  if (region && !REGION_REGEXP.test(region)) {
    throw new Error(`Invalid storage region: ${region}`);
  }
  return region;
}
