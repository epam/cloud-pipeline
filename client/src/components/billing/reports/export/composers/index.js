/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

export {default as CSV} from './csv';
export {default as billingCentersComposer} from './billing-centers-composer';
export {default as summaryComposer} from './summary-composer';
export {default as tableComposer} from './table-composer';
export {default as discountsComposer} from './discounts-composer';
export {default as usersComposers} from './users-composer';

function buildCascadeComposers (composers = [], discounts = {}, ...exportOptions) {
  function build (index = 0) {
    if (index < composers.length) {
      const next = build(index + 1);
      const {composer, options = []} = composers[index];
      return (csv) => new Promise((resolve, reject) => {
        composer(csv, discounts, exportOptions, ...options)
          .then(() => {
            next(csv)
              .then(resolve)
              .catch(reject);
          })
          .catch(reject);
      });
    }
    return () => Promise.resolve();
  }
  return build();
}

export {buildCascadeComposers};
