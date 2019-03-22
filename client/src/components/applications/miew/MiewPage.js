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
import {inject, observer} from 'mobx-react';
import {Row, Col, Select, Icon} from 'antd';
import parseQueryParameters from '../../../utils/queryParameters';
import EmbeddedMiew from './EmbeddedMiew';
import $ from 'jquery';
import 'jquery.terminal';
import 'jquery.terminal/css/jquery.terminal.css';
import styles from './EmbeddedMiew.css';

@inject(({routing}) => {
  const queryParameters = parseQueryParameters(routing);
  return {
    s3item: {
      storageId: queryParameters.storageId,
      path: queryParameters.path,
      version: queryParameters.version
    }
  };
})
@observer
export default class MiewPage extends React.Component {

  displayModes = [
    {
      mode: 'LN',
      title: 'Lines'
    },
    {
      mode: 'LC',
      title: 'Licorice'
    },
    {
      mode: 'BS',
      title: 'Balls'
    },
    {
      mode: 'VW',
      title: 'VDW'
    },
    {
      mode: 'TR',
      title: 'Trace'
    },
    {
      mode: 'TU',
      title: 'Tube'
    },
    {
      mode: 'CA',
      title: 'Cartoon'
    },
    {
      mode: 'QS',
      title: 'Quick Surf'
    },
    {
      mode: 'SA',
      title: 'SAS'
    },
    {
      mode: 'SE',
      title: 'SES'
    },
    {
      mode: 'CS',
      title: 'Contact Surf'
    },
    {
      mode: 'TX',
      title: 'Text'
    }
  ];
  colorModes = [
    {
      mode: 'EL',
      title: 'Element'
    },
    {
      mode: 'SQ',
      title: 'Sequence'
    },
    {
      mode: 'UN',
      title: 'Uniform'
    },
    {
      mode: 'CO',
      title: 'Conditional'
    },
    {
      mode: 'TE',
      title: 'Temperature'
    },
    {
      mode: 'OC',
      title: 'Occupancy'
    },
    {
      mode: 'HY',
      title: 'Hydrophobicity'
    },
    {
      mode: 'MO',
      title: 'Molecule'
    }
  ];

  state = {
    displayMode: 'CA',
    colorMode: 'EL',
    terminalVisible: false
  };

  embeddedMiew;
  miewTerminal;
  terminal;

  initializeEmbeddedMiew = (control) => {
    if (control) {
      this.embeddedMiew = control;
    }
  };

  initializeTerminal = (component) => {
    if (component) {
      this.miewTerminal = component;
    }
  };

  onDisplayModeChange = ({key}) => {
    this.setState({
      displayMode: key
    }, () => {
      if (this.embeddedMiew && this.embeddedMiew.setRep) {
        this.embeddedMiew.setRep(key, this.state.colorMode);
      }
    });
  };

  onColorModeChange = ({key}) => {
    this.setState({
      colorMode: key
    }, () => {
      if (this.embeddedMiew && this.embeddedMiew.setRep) {
        this.embeddedMiew.setRep(this.state.displayMode, key);
      }
    });
  };

  changeTerminalVisibility = () => {
    this.setState({
      terminalVisible: !this.state.terminalVisible
    });
  };

  render () {
    return (
      <div style={{flex: 1, display: 'flex', backgroundColor: '#202020'}}>
        <EmbeddedMiew
          ref={this.initializeEmbeddedMiew}
          s3item={this.props.s3item}
          onMessage={this.onViewerMessage}
          previewMode={false} />
        <Row className={styles.miewToolbar} type="flex" align="middle">
          <div className={styles.miewToolbarOverlay} />
          <Col span={4} style={{textAlign: 'left', paddingLeft: 15}}>
            <Icon
              onClick={this.changeTerminalVisibility}
              type={this.state.terminalVisible ? 'code' : 'code-o'}
              style={{
                cursor: 'pointer',
                verticalAlign: 'middle',
                fontSize: '16pt'
              }} />
          </Col>
          <Col span={20} className={styles.toolbarActions}>
            <span>Display mode: </span>
            <Select
              labelInValue
              defaultValue={{key: this.state.displayMode}}
              onSelect={this.onDisplayModeChange}
              style={{width: 150}}>
              {
                this.displayModes.map(displayMode => {
                  return (
                    <Select.Option
                      key={displayMode.mode}
                      value={displayMode.mode}>{displayMode.title}</Select.Option>
                  );
                })
              }
            </Select>
            <span> Color mode: </span>
            <Select
              labelInValue
              defaultValue={{key: this.state.colorMode}}
              onSelect={this.onColorModeChange}
              style={{width: 150}}>
              {
                this.colorModes.map(colorMode => {
                  return (
                    <Select.Option
                      key={colorMode.mode}
                      value={colorMode.mode}>{colorMode.title}</Select.Option>
                  );
                })
              }
            </Select>
          </Col>
        </Row>
        <div className={styles.miewTerminal} style={{display: this.state.terminalVisible ? 'block' : 'none'}}>
          <div className={styles.miewTerminalOverlay} />
          <div className={styles.miewTerminalComponent}>
            <div ref={this.initializeTerminal} />
          </div>
        </div>
      </div>
    );
  }

  onCommand = (command, terminal) => {
    if (this.embeddedMiew && this.embeddedMiew.execute) {
      this.embeddedMiew.execute(command, (str) => {
        terminal.echo(str);
      }, (str) => {
        terminal.error(str);
      });
    }
  };

  onViewerMessage = (e) => {
    if (this.terminal) {
      const colors = {
        'error': '#f00',
        'warn': '#990',
        'report': '#1a9cb0'
      };
      const msg = e.message.replace(/]/g, '\\]');
      this.terminal.echo('[[b;' + (colors[e.level] || '#666') + ';]' + msg + ']');
    }
  };

  componentDidMount () {
    if (this.embeddedMiew && this.miewTerminal) {
      $(this.miewTerminal).terminal(
        this.onCommand, {
          greetings: 'Miew - 3D Molecular Viewer\nCopyright © 2015-2017 EPAM Systems, Inc.\n',
          prompt: 'miew> ',
          name: 'miew',
          scrollOnEcho: true,
          height: '100%',
          onInit: terminal => { this.terminal = terminal; }
        }
      );
    }
  }

}
