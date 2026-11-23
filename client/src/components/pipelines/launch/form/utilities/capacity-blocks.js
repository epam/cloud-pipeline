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

/**
 * Whether the launch form offers capacity blocks: only when it launches a run.
 * A detached configuration, a pipeline configuration and a launch profile are
 * saved templates, and none of them keeps a pool.
 * @param {{detached?: boolean, editConfigurationMode?: boolean, launchProfile?: *}} props
 * @returns {boolean}
 */
export function formOffersCapacityBlocks (props) {
  const {
    detached,
    editConfigurationMode,
    launchProfile
  } = props || {};
  return !detached && !editConfigurationMode && !launchProfile;
}
