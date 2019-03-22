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

export default class SessionStorageWrapper {

  static ACTIVE_RUNS_KEY = 'active_runs';

  static getItem (key, defaultValue) {
    const data = sessionStorage.getItem(key);
    if (data) {
      try {
        return JSON.parse(data);
      } catch (___) {
        return defaultValue;
      }
    }
    return defaultValue;
  }
  static setItem (key, value) {
    try {
      const data = JSON.stringify(value);
      sessionStorage.setItem(key, data);
    } catch (___) {}
  }

  static navigateToActiveRuns (router) {
    if (!router || !router.push) {
      return;
    }
    router.push(SessionStorageWrapper.getActiveRunsLink());
  }

  static getActiveRunsLink () {
    const myRuns = SessionStorageWrapper.getItem(SessionStorageWrapper.ACTIVE_RUNS_KEY, true);
    if (myRuns) {
      return '/runs/active';
    } else {
      return '/runs/active?all';
    }
  }

}
