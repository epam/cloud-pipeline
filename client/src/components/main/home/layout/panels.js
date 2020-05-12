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

const ACTIVE_RUNS_KEY = 'my runs';
const ACTIVITIES_KEY = 'activities';
const SERVICES_KEY = 'my services';
const RECENTLY_COMPLETED_RUNS_KEY = 'recently completed runs';
const DATA_KEY = 'my data';
const PERSONAL_TOOLS_KEY = 'personal tools';
const NOTIFICATIONS_KEY = 'notifications';
const PIPELINES_KEY = 'pipelines';
const PROJECTS_KEY = 'projects';

export default {
  activities: ACTIVITIES_KEY,
  data: DATA_KEY,
  notifications: NOTIFICATIONS_KEY,
  personalTools: PERSONAL_TOOLS_KEY,
  pipelines: PIPELINES_KEY,
  projects: PROJECTS_KEY,
  recentlyCompletedRuns: RECENTLY_COMPLETED_RUNS_KEY,
  runs: ACTIVE_RUNS_KEY,
  services: SERVICES_KEY
};
