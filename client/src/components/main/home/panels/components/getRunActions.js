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
import {Link} from 'react-router';
import {Row} from 'antd';
import parseRunServiceUrl from '../../../../../utils/parseRunServiceUrl';
import {canPauseRun} from '../../../../runs/actions';

export default function (callbacks) {
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
          const urls = parseRunServiceUrl(run.serviceUrl);
          if (urls.length === 1) {
            actions.push({
              title: 'OPEN',
              icon: 'export',
              action: callbacks && callbacks.openUrl
                ? () => callbacks.openUrl(urls[0].url)
                : undefined
            });
          } else {
            const overlay = urls.map((url, index) => {
              return (
                <Row type="flex" key={index} style={{fontSize: 'larger'}}>
                  <a href={url.url} target="_blank" style={`${url.isDefault}` === 'true' ? {fontWeight: 'bold'} : {}}>{url.name || url.url}</a>
                </Row>
              );
            });
            let defaultAction;
            if (urls.filter(url => `${url.isDefault}` === 'true').length === 1) {
              const [url] = urls.filter(url => `${url.isDefault}` === 'true');
              if (url && url.url) {
                defaultAction = callbacks && callbacks.openUrl
                  ? () => callbacks.openUrl(url.url)
                  : undefined;
              }
            }
            actions.push({
              title: 'OPEN',
              icon: 'export',
              overlay,
              action: defaultAction
            });
          }
        }
        if (canPauseRun(run)) {
          actions.push({
            title: 'PAUSE',
            icon: 'pause-circle-o',
            action: callbacks ? callbacks.pause : undefined
          });
        }
        if ((run.commitStatus || '').toLowerCase() !== 'committing') {
          actions.push({
            title: 'STOP',
            icon: 'close-circle-o',
            style: {color: 'red'},
            action: callbacks ? callbacks.stop : undefined
          });
        }
        if (run.initialized && run.podIP) {
          actions.push({
            title: 'SSH',
            icon: 'code-o',
            action: callbacks && callbacks.ssh ? callbacks.ssh : undefined
          });
        }
        break;
      case 'PAUSED':
        if (run.initialized && run.instance && run.instance.spot !== undefined && !run.instance.spot) {
          actions.push({
            title: 'RESUME',
            icon: run.resumeFailureReason ? 'exclamation-circle-o' : 'play-circle-o',
            action: callbacks ? callbacks.resume : undefined,
            overlay: run.resumeFailureReason ? (
              <div style={{maxWidth: '40vw'}}>
                {run.resumeFailureReason}
              </div>
            ) : undefined
          });
        }
        actions.push({
          title: 'TERMINATE',
          icon: 'close-circle-o',
          style: {color: 'red'},
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
        if (runParameter.dataStorageLinks) {
          const valueParts = valueSelector().split(/[,|]/);
          const urls = [];
          for (let i = 0; i < valueParts.length; i++) {
            const value = valueParts[i].trim();
            const [link] = runParameter.dataStorageLinks.filter(link => {
              return link.absolutePath && value.toLowerCase() === link.absolutePath.toLowerCase();
            });
            if (link) {
              let url = `/storage/${link.dataStorageId}`;
              if (link.path && link.path.length) {
                url = `/storage/${link.dataStorageId}?path=${link.path}`;
              }
              urls.push((
                <Link key={i} to={url}>{value}</Link>
              ));
            } else {
              urls.push(<span key={i}>{value}</span>);
            }
          }
          return (
            <tr key={runParameter.name}>
              <td style={{verticalAlign: 'top', paddingLeft: 5}}>
                <span>{runParameter.name}: </span>
              </td>
              <td>
                <ul>
                  {urls.map((url, index) => <li key={index}>{url}</li>)}
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
