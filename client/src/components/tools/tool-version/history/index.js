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

import React from 'react';
import {inject, observer} from 'mobx-react';
import {Alert} from 'antd';
import LoadToolHistory from '../../../../models/tools/LoadToolHistory';
import LoadingView from '../../../special/LoadingView';
import displaySize from '../../../../utils/displaySize';
import hljs from 'highlight.js';
import 'highlight.js/styles/github.css';
import styles from './history.css';

function processScript (script) {
  return hljs.highlight('bash', script).value;
}

@inject((stores, {params}) => {
  return {
    toolId: params.id,
    version: params.version,
    history: new LoadToolHistory(params.id, params.version)
  };
})
@observer
export default class History extends React.Component {
  state = {
    selectedLayer: 0
  };

  onSelectLayer = (index) => {
    this.setState({selectedLayer: index});
  };

  render () {
    if (!this.props.history.loaded && this.props.history.pending) {
      return <LoadingView />;
    }
    if (this.props.history.error) {
      return <Alert type="error" message={this.props.history.error} />;
    }
    const selectedLayer = (this.props.history.value || [])[this.state.selectedLayer];
    return (
      <div style={{display: 'flex', flexDirection: 'row', height: '100%'}}>
        <div style={{flex: 1, display: 'flex', flexDirection: 'column', overflow: 'auto'}}>
          {
            (this.props.history.value || []).map((layer, index) => (
              <div
                className={[
                  styles.layer,
                  this.state.selectedLayer === index ? styles.selected : false
                ].filter(Boolean).join(' ')}
                onClick={() => this.onSelectLayer(index)}
              >
                <span className={styles.index}>{index + 1}.</span>
                <code className={styles.command}>{layer.command}</code>
                <span className={styles.size}>{displaySize(layer.size)}</span>
              </div>
            ))
          }
        </div>
        <div style={{flex: 1, padding: 5}}>
          {
            selectedLayer && (
              <div
                className={styles.code}
              >
                <code
                  dangerouslySetInnerHTML={{
                    __html: processScript(selectedLayer.command)
                  }} />
              </div>
            )
          }
        </div>
      </div>
    );
  }
}
