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

import Panels from './panels';

export default {
  [Panels.runs]: (localizedStringFn) => `List of the jobs/tools that are currently in RUNNING or PAUSED state. If this list is empty - start a new run from the TOOLS or ${localizedStringFn('Pipeline')}s widgets. Hover a run item to view a list of available action. Use STOP/PAUSE/RESUME actions to change the state of the run or use OPEN to navigate to the GUI of the interactive job. Click a run item to navigate to the details and logs page. Note: only top 50 active runs will be shown, if more than 50 jobs/tools are running - use EXPLORE ALL ACTIVE RUNS link`,
  [Panels.activities]: (localizedStringFn) => `This widget lists recent comments/${localizedStringFn('issue')}s/posts that occured for the items that you own (e.g. models, pipelines, projects, folders, etc.)`,
  [Panels.data]: 'List of the data storages that are available to you for READ/WRITE operations. These data storages are available from the Platform GUI (click an item to navigate to the data storage contents) and from a running job/tool as well',
  [Panels.personalTools]: 'Tools/Compute stacks/Docker images that are added in your PERSONAL repository or available to your group. To run a TOOL - please use a RUN button that appears when hovering an item with a mouse. Use a search bar to find tools that are shared by other users and groups. To get a full list of available stacks - please use TOOLS menu item in the left toolbar',
  [Panels.pipelines]: (localizedStringFn) => `${localizedStringFn('Pipeline')}s that are available to you for READ/WRITE operations. This is the same list, as available in the LIBRARY hierarchy. ${localizedStringFn('Pipeline')} can be run right from this widget using a RUN button that appears when hovering an item with a mouse`,
  [Panels.notifications]: 'List of system-wide notifications from administrators posted. These are the same notifications as shown at a login time in the top right corner of the main page',
  [Panels.recentlyCompletedRuns]: 'This widget lists jobs/tools runs that were recently completed. Click a corresponding entry in this widget to navigate to the run details/logs page',
  [Panels.services]: 'This widget lists direct links to the Interactive services, that are exposed by the launched tools. Compared to the ACTIVE RUNS widget - this one does NOT show all the active jobs/tools, only links to the web/desktop GUI. If this list is empty - start a new run of an interactive compute stack from the TOOLS. Click an item within this widget to navigate to the corresponding service',
  [Panels.projects]: 'Projects that are available to you for READ/WRITE operations. This is the same list, as available in the LIBRARY hierarchy. You can access to project\'s run history and storages from this widget.'
};
