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

import Multizone from './multizone';
import cloudRegionsInfo from '../../models/cloudRegions/CloudRegionsInfo';
import parseRunServiceUrlConfiguration from './parse-run-service-url-configuration';
import getEdgeExternalEndpoints from './get-edge-external-endpoints';

class MultizoneManager extends Multizone {
  constructor () {
    super();
    this.checkRegions();
  }

  checkRegions () {
    if (this.checkRegionsPromise) {
      return this.checkRegionsPromise;
    }
    this.checkRegionsPromise = new Promise((resolve) => {
      cloudRegionsInfo.fetchIfNeededOrWait()
        .then(() => {
          if (cloudRegionsInfo.loaded) {
            const regionIds = (cloudRegionsInfo.value || [])
              .map(region => region.regionId);
            return getEdgeExternalEndpoints(regionIds);
          } else {
            throw new Error(cloudRegionsInfo.error);
          }
        })
        .then(endpoints => {
          console.info('Edge external endpoints: ', endpoints);
          return this.check(endpoints);
        })
        .catch(() => Promise.resolve(this.defaultRegion))
        .then(resolve);
    });
    return this.checkRegionsPromise;
  }
}

const defaultManager = new MultizoneManager();
export default defaultManager;
export {MultizoneManager, Multizone, parseRunServiceUrlConfiguration};
