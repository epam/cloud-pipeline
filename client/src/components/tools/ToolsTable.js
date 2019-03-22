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
import {observer} from 'mobx-react';
import PropTypes from 'prop-types';
import ToolImage from '../../models/tools/ToolImage';
import {Button, Col, Row, Icon} from 'antd';
import highlightText from '../special/highlightText';
import styles from './Tools.css';

@observer
export default class ToolsTable extends React.Component {

  openIssuesPanel = (tool) => {
    if (this.props.onOpenIssuesPanel) {
      this.props.onOpenIssuesPanel(tool);
    }
  };

  renderSeparator = (text, marginInCols, key, style) => {
    return (
      <Row key={key} type="flex" style={style || {margin: 0}}>
        <Col span={marginInCols} />
        <Col span={24 - 2 * marginInCols}>
          <table style={{width: '100%'}}>
            <tbody>
              <tr>
                <td style={{width: '50%'}}>
                  <div
                    style={{
                      margin: '0 5px',
                      verticalAlign: 'middle',
                      height: 1,
                      backgroundColor: '#ccc'
                    }}>{'\u00A0'}</div>
                </td>
                <td style={{width: 1, whiteSpace: 'nowrap'}}><b>{text}</b></td>
                <td style={{width: '50%'}}>
                  <div
                    style={{
                      margin: '0 5px',
                      verticalAlign: 'middle',
                      height: 1,
                      backgroundColor: '#ccc'
                    }}>{'\u00A0'}</div>
                </td>
              </tr>
            </tbody>
          </table>
        </Col>
        <Col span={marginInCols} />
      </Row>
    );
  };

  renderTool = (tool, index, arr, isGlobalSearch = false) => {
    const renderLabel = (label, index) => {
      return <span key={index} className={styles.toolLabel}>{highlightText(label, this.props.searchString)}</span>;
    };
    return (
      <Row
        className={`${styles.toolRow} ${tool.endpoints && tool.endpoints.length > 0 ? styles.toolRowWithEndpoints : ''}`}
        onClick={() => this.props.onSelectTool && this.props.onSelectTool(tool.id)}
        type="flex"
        key={index}
        align="middle">
        {
          tool.iconId &&
          <div style={{margin: 5, overflow: 'hidden', width: 33, height: 33}}>
            <img src={ToolImage.url(tool.id, tool.iconId)} style={{width: '100%'}} />
          </div>
        }
        <Row type="flex" align="middle" justify="space-between" style={{flex: 1}}>
          <div className={styles.toolRowTitle} style={tool.iconId ? {paddingLeft: 0} : {}}>
            <span className={styles.toolTitle}>
              {tool.endpoints && tool.endpoints.length > 0 ? <Icon type="export" style={{margin: '0px 3px', fontSize: 'larger'}} /> : undefined}
              {highlightText(tool.image, this.props.searchString)}
            </span>
            <span className={styles.toolDescription}>
              {tool.shortDescription}
            </span>
          </div>
          <Row
            type="flex"
            style={{flexWrap: 'nowrap', width: '30%', paddingRight: 10}}
            className={styles.toolRowLabels}
            align="middle">
            {
              (tool.labels || []).map(renderLabel)
            }
          </Row>
        </Row>
        {
          !isGlobalSearch &&
          <Row type="flex" justify="end" style={{padding: '0 5px'}}>
            <Button
              onClick={(e) => {
                e.stopPropagation();
                this.openIssuesPanel(tool);
              }}
              key="issues"
              size="small">
              <Icon type="message" />{tool.issuesCount > 0 ? ` ${tool.issuesCount}` : undefined}
            </Button>
          </Row>
        }
      </Row>
    );
  };

  render () {
    const tools = this.props.tools.sort((toolA, toolB) => {
      if (toolA.image > toolB.image) {
        return 1;
      } else if (toolA.image < toolB.image) {
        return -1;
      } else {
        return 0;
      }
    });
    let globalSearchTools;
    if (this.props.globalSearchTools.length > 0) {
      globalSearchTools = this.props.globalSearchTools.sort((toolA, toolB) => {
        if (toolA.image === toolB.image) {
          return 0;
        } else if (toolA.image > toolB.image) {
          return 1;
        } else {
          return -1;
        }
      });
    }
    return (
      <div
        className={styles.container}>
        <Row className={styles.toolsContainer}>
          {tools.map(this.renderTool)}
          {
            globalSearchTools && globalSearchTools.length &&
            this.renderSeparator('Global search', null, 'tools_list_separator')
          }
          {
            globalSearchTools && globalSearchTools.length &&
            globalSearchTools.map((tool, index) => this.renderTool(tool, index, null, true))
          }
        </Row>
      </div>
    );
  }
}

ToolsTable.propTypes = {
  tools: PropTypes.array,
  globalSearchTools: PropTypes.array,
  onSelectTool: PropTypes.func,
  onOpenIssuesPanel: PropTypes.func,
  searchString: PropTypes.string
};
