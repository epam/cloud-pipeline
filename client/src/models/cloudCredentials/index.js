/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import LoadCloudCredentialsProfiles from './LoadCloudCredentialsProfiles';

const cloudCredentialProfiles = new LoadCloudCredentialsProfiles();

export {
  cloudCredentialProfiles,
  LoadCloudCredentialsProfiles
};
export {default as AssignCredentialProfiles} from './AssignCredentialProfiles';
export {default as LoadEntityCredentialProfiles} from './LoadEntityCredentialProfiles';
export {default as CreateCloudCredentialsProfile} from './CreateCloudCredentialsProfile';
export {default as RemoveCloudCredentialsProfile} from './RemoveCloudCredentialsProfile';
export {default as UpdateCloudCredentialsProfile} from './UpdateCloudCredentialsProfile';
