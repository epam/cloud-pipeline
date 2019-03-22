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

const ACTIVE_RUNS_KEY = 'my runs';
const ACTIVITIES_KEY = 'activities';
const SERVICES_KEY = 'my services';
const RECENTLY_COMPLETED_RUNS_KEY = 'recently completed runs';
const DATA_KEY = 'my data';
const PERSONAL_TOOLS_KEY = 'personal tools';
const NOTIFICATIONS_KEY = 'notifications';
const PIPELINES_KEY = 'pipelines';
const PROJECTS_KEY = 'projects';

export const Panels = {
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

export const PanelTitles = {
  [ACTIVE_RUNS_KEY]: 'Active runs',
  [ACTIVITIES_KEY]: 'Activities',
  [DATA_KEY]: 'Data',
  [PERSONAL_TOOLS_KEY]: 'Tools',
  [PIPELINES_KEY]: (fn) => `${fn('Pipeline')}s`,
  [PROJECTS_KEY]: 'Projects',
  [NOTIFICATIONS_KEY]: 'Notifications',
  [RECENTLY_COMPLETED_RUNS_KEY]: 'Recently completed runs',
  [SERVICES_KEY]: 'Services'
};

export const PanelIcons = {
  [ACTIVE_RUNS_KEY]: 'play-circle-o',
  [ACTIVITIES_KEY]: 'message',
  [DATA_KEY]: 'hdd',
  [PERSONAL_TOOLS_KEY]: 'tool',
  [PIPELINES_KEY]: 'fork',
  [PROJECTS_KEY]: 'api',
  [NOTIFICATIONS_KEY]: 'notification',
  [RECENTLY_COMPLETED_RUNS_KEY]: 'clock-circle-o',
  [SERVICES_KEY]: 'rocket'
};

export const PanelInfos = {
  [ACTIVE_RUNS_KEY]: (localizedStringFn) => `List of the jobs/tools that are currently in RUNNING or PAUSED state. If this list is empty - start a new run from the TOOLS or ${localizedStringFn('Pipeline')}s widgets. Hover a run item to view a list of available action. Use STOP/PAUSE/RESUME actions to change the state of the run or use OPEN to navigate to the GUI of the interactive job. Click a run item to navigate to the details and logs page. Note: only top 50 active runs will be shown, if more than 50 jobs/tools are running - use EXPLORE ALL ACTIVE RUNS link`,
  [ACTIVITIES_KEY]: (localizedStringFn) => `This widget lists recent comments/${localizedStringFn('issue')}s/posts that occured for the items that you own (e.g. models, pipelines, projects, folders, etc.)`,
  [DATA_KEY]: 'List of the data storages that are available to you for READ/WRITE operations. These data storages are available from the Platform GUI (click an item to navigate to the data storage contents) and from a running job/tool as well',
  [PERSONAL_TOOLS_KEY]: 'Tools/Compute stacks/Docker images that are added in your PERSONAL repository or available to your group. To run a TOOL - please use a RUN button that appears when hovering an item with a mouse. Use a search bar to find tools that are shared by other users and groups. To get a full list of available stacks - please use TOOLS menu item in the left toolbar',
  [PIPELINES_KEY]: (localizedStringFn) => `${localizedStringFn('Pipeline')}s that are available to you for READ/WRITE operations. This is the same list, as available in the LIBRARY hierarchy. ${localizedStringFn('Pipeline')} can be run right from this widget using a RUN button that appears when hovering an item with a mouse`,
  [NOTIFICATIONS_KEY]: 'List of system-wide notifications from administrators posted. These are the same notifications as shown at a login time in the top right corner of the main page',
  [RECENTLY_COMPLETED_RUNS_KEY]: 'This widget lists jobs/tools runs that were recently completed. Click a corresponding entry in this widget to navigate to the run details/logs page',
  [SERVICES_KEY]: 'This widget lists direct links to the Interactive services, that are exposed by the launched tools. Compared to the ACTIVE RUNS widget - this one does NOT show all the active jobs/tools, only links to the web/desktop GUI. If this list is empty - start a new run of an interactive compute stack from the TOOLS. Click an item within this widget to navigate to the corresponding service',
  [PROJECTS_KEY]: 'Projects that are available to you for READ/WRITE operations. This is the same list, as available in the LIBRARY hierarchy. You can access to project\'s run history and storages from this widget.'
};

export const PanelDefaultSizes = {
  [Panels.activities]: {w: 2, h: 2},
  [Panels.data]: {w: 2, h: 1},
  [Panels.notifications]: {w: 1, h: 2},
  [Panels.personalTools]: {w: 1, h: 2},
  [Panels.pipelines]: {w: 1, h: 1},
  [Panels.projects]: {w: 1, h: 2},
  [Panels.recentlyCompletedRuns]: {w: 1, h: 2},
  [Panels.runs]: {w: 1, h: 1},
  [Panels.services]: {w: 1, h: 1}
};

export const DefaultPanelsState = [
  {'w': 12, 'h': 12, 'x': 0, 'y': 12, 'i': 'my data', 'moved': false, 'static': false},
  {'w': 12, 'h': 24, 'x': 12, 'y': 0, 'i': 'personal tools', 'moved': false, 'static': false},
  {'w': 12, 'h': 12, 'x': 0, 'y': 0, 'i': 'my runs', 'moved': false, 'static': false}
];

export const PanelNeighbors = [
  [Panels.runs, Panels.services, Panels.recentlyCompletedRuns],
  [Panels.activities, Panels.notifications],
  [Panels.personalTools, Panels.data]
];
