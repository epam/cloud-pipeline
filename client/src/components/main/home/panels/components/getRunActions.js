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

import React from 'react';
import {canPauseRun} from '../../../../runs/actions';
import VSActions from '../../../../versioned-storages/vs-actions';
import MultizoneUrl from '../../../../special/multizone-url';
import {parseRunServiceUrlConfiguration} from '../../../../../utils/multizone';
import {MAINTENANCE_MODE_DISCLAIMER} from '../../../../../models/preferences/PreferencesLoad';
import DataStorageLink from '../../../../special/data-storage-link';

export default function (
  {multiZoneManager, vsActions, preferences},
  callbacks,
  disabled = false
) {
  let maintenanceMode = false;
  if (preferences && preferences.loaded) {
    maintenanceMode = preferences.systemMaintenanceMode;
  }
  return function (run) {
    const actions = [];
    switch (run.status.toUpperCase()) {
      case 'FAILURE':
      case 'STOPPED':
      case 'SUCCESS':
        actions.push({
          title: 'RERUN',
          icon: 'play-circle-o',
          action: callbacks ? callbacks.run : undefined
        });
        break;
      case 'RUNNING':
        if (run.initialized && run.serviceUrl) {
          const regionedUrls = parseRunServiceUrlConfiguration(run.serviceUrl);
          if (regionedUrls.length === 1) {
            const regionedUrl = regionedUrls[0];
            const defaultUrlRegion = multiZoneManager.getDefaultURLRegion(regionedUrl.url);
            const url = regionedUrl.url[defaultUrlRegion];
            actions.push({
              title: 'OPEN',
              icon: 'export',
              target: regionedUrl.sameTab ? '_top' : '_blank',
              multiZoneUrl: regionedUrl.url,
              action: url && callbacks && callbacks.openUrl
                ? () => callbacks.openUrl(url, regionedUrl.sameTab ? '_top' : '_blank')
                : undefined
            });
          } else {
            const defaultUrl = regionedUrls.find(o => o.isDefault);
            const defaultUrlRegion = defaultUrl
              ? multiZoneManager.getDefaultURLRegion(defaultUrl.url)
              : undefined;
            const overlay = (
              <div>
                <ul>
                  {
                    regionedUrls.map(({name, url, sameTab}, index) =>
                      <li key={index} style={{margin: 4}}>
                        <MultizoneUrl
                          target={sameTab ? '_top' : '_blank'}
                          configuration={url}
                        >
                          {name}
                        </MultizoneUrl>
                      </li>
                    )
                  }
                </ul>
              </div>
            );
            actions.push({
              title: 'OPEN',
              icon: 'export',
              overlay,
              action: defaultUrl && defaultUrlRegion && callbacks && callbacks.openUrl
                ? () => callbacks.openUrl(
                  defaultUrl.url[defaultUrlRegion],
                  defaultUrlRegion.sameTab ? '_top' : '_blank'
                )
                : undefined
            });
          }
        }
        if (run.initialized && run.podIP) {
          actions.push({
            title: 'SSH',
            icon: 'code-o',
            runSSH: true,
            runId: run.id
          });
          if (
            !run.sensitive &&
            vsActions &&
            vsActions.available
          ) {
            actions.push({
              title: (
                <VSActions
                  run={run}
                  trigger={['click']}
                  getPopupContainer={() => document.getElementById('root')}
                  onDropDownVisibleChange={callbacks && callbacks.vsActionsMenu
                    ? (v) => callbacks.vsActionsMenu(run, v)
                    : undefined
                  }
                >
                  VSC
                </VSActions>
              ),
              icon: 'fork'
            });
          }
        }
        if (canPauseRun(run, preferences)) {
          actions.push({
            title: 'PAUSE',
            icon: 'pause-circle-o',
            disabled: disabled || maintenanceMode,
            overlay: maintenanceMode ? MAINTENANCE_MODE_DISCLAIMER : undefined,
            action: callbacks ? callbacks.pause : undefined
          });
        }
        if ((run.commitStatus || '').toLowerCase() !== 'committing') {
          actions.push({
            title: 'STOP',
            icon: 'close-circle-o',
            className: 'cp-danger',
            action: callbacks ? callbacks.stop : undefined
          });
        }
        break;
      case 'PAUSED':
        if (
          run.initialized && run.instance && run.instance.spot !== undefined &&
          !run.instance.spot
        ) {
          actions.push({
            title: 'RESUME',
            disabled: disabled || maintenanceMode,
            icon: run.resumeFailureReason ? 'exclamation-circle-o' : 'play-circle-o',
            action: callbacks ? callbacks.resume : undefined,
            overlay: maintenanceMode
              ? MAINTENANCE_MODE_DISCLAIMER
              : run.resumeFailureReason ? (
                <div style={{maxWidth: '40vw'}}>
                  {run.resumeFailureReason}
                </div>
              ) : undefined
          });
        }
        actions.push({
          title: 'TERMINATE',
          icon: 'close-circle-o',
          className: 'cp-danger',
          action: callbacks ? callbacks.terminate : undefined
        });
        break;
      case 'PAUSING':
      case 'RESUMING':
        break;
    }
    if (run.pipelineRunParameters && run.pipelineRunParameters.length > 0) {
      const renderRunParameter = (runParameter) => {
        if (!runParameter || !runParameter.name) {
          return null;
        }
        const valueSelector = () => {
          return runParameter.resolvedValue || runParameter.value || '';
        };
        if (/^(input|output|common|path)$/i.test(runParameter.type)) {
          const valueParts = valueSelector().split(/[,|]/);
          return (
            <tr key={runParameter.name}>
              <td style={{verticalAlign: 'top', paddingLeft: 5}}>
                <span>{runParameter.name}: </span>
              </td>
              <td>
                <ul>
                  {
                    valueParts.map((value, index) => (
                      <li
                        key={`${value}-${index}`}
                      >
                        <DataStorageLink
                          key={`link-${value}-${index}`}
                          path={value}
                          isFolder={/^output$/i.test(runParameter.type) ? true : undefined}
                        >
                          {value}
                        </DataStorageLink>
                      </li>
                    ))
                  }
                </ul>
              </td>
            </tr>
          );
        }
        const values = (valueSelector() || '').split(',').map(v => v.trim());
        if (values.length === 1) {
          return (
            <tr key={runParameter.name}>
              <td style={{verticalAlign: 'top', paddingLeft: 5}}>{runParameter.name}:</td>
              <td>{values[0]}</td>
            </tr>
          );
        } else {
          return (
            <tr key={runParameter.name}>
              <td style={{verticalAlign: 'top', paddingLeft: 5}}>
                <span>{runParameter.name}:</span>
              </td>
              <td>
                <ul>
                  {values.map((value, index) => <li key={index}>{value}</li>)}
                </ul>
              </td>
            </tr>
          );
        }
      };
      const inputParameters = run.pipelineRunParameters
        .filter(p => ['input', 'common'].indexOf((p.type || '').toLowerCase()) >= 0);
      const outputParameters = run.pipelineRunParameters
        .filter(p => (p.type || '').toLowerCase() === 'output');
      if (inputParameters.length > 0 || outputParameters.length > 0) {
        const overlay = (
          <table>
            <tbody>
              {
                inputParameters.length > 0
                  ? <tr><td colSpan={2}><b>Input:</b></td></tr>
                  : undefined
              }
              {inputParameters.map(renderRunParameter)}
              {
                outputParameters.length > 0
                  ? <tr><td colSpan={2}><b>Output:</b></td></tr>
                  : undefined
              }
              {outputParameters.map(renderRunParameter)}
            </tbody>
          </table>
        );
        actions.push({
          title: 'LINKS',
          icon: 'link',
          overlay
        });
      }
    }
    return actions;
  };
}
