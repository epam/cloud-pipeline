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
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {Row, Splitter} from 'antd';
import {CloseOutlined} from '@ant-design/icons';
import localization from '../../../utils/localization';

export const CONTENT_PANEL_KEY = 'content';
export const ISSUES_PANEL_KEY = 'issues';
export const METADATA_PANEL_KEY = 'metadata';
export const PREVIEW_PANEL_KEY = 'configuration-preview';

const filterRealChild = (child) => !!child;

const CONTENT_ISSUES_METADATA_PANEL_ORDER = [
  CONTENT_PANEL_KEY,
  ISSUES_PANEL_KEY,
  METADATA_PANEL_KEY,
  PREVIEW_PANEL_KEY,
];

@localization.localizedComponent
@observer
export class ContentIssuesMetadataPanel extends localization.LocalizedReactComponent {
  static propTypes = {
    onPanelClose: PropTypes.func,
    style: PropTypes.object,
  };

  getPanelContentInfo() {
    return {
      [CONTENT_PANEL_KEY]: {
        key: CONTENT_PANEL_KEY,
        defaultSize: '50%',
        min: '33%',
        containerStyle: {display: 'flex', flexDirection: 'column'},
      },
      [ISSUES_PANEL_KEY]: {
        key: ISSUES_PANEL_KEY,
        title: `${this.localizedString('Issue')}s`,
        closable: true,
        defaultSize: '25%',
        min: 200,
        containerStyle: {display: 'flex', flexDirection: 'column'},
      },
      [METADATA_PANEL_KEY]: {
        key: METADATA_PANEL_KEY,
        title: 'Attributes',
        closable: true,
        defaultSize: '25%',
        min: 200,
        containerStyle: {display: 'flex', flexDirection: 'column'},
      },
      [PREVIEW_PANEL_KEY]: {
        key: PREVIEW_PANEL_KEY,
        title: 'Preview',
        defaultSize: '25%',
        min: 200,
        containerStyle: {display: 'flex', flexDirection: 'column'},
      },
    };
  }

  render() {
    const children = (this.props.children || []).filter(filterRealChild);
    const childrenByKey = {};
    children.forEach((child) => {
      if (child.key) {
        childrenByKey[child.key] = child;
      }
    });
    const contentInfo = this.getPanelContentInfo();
    const panels = CONTENT_ISSUES_METADATA_PANEL_ORDER.filter((key) => childrenByKey[key]).map(
      (key) => ({key, child: childrenByKey[key], info: contentInfo[key]}),
    );
    if (panels.length === 0) {
      return null;
    }
    if (panels.length === 1) {
      const {child, info} = panels[0];
      return (
        <div style={{...this.props.style, ...info.containerStyle, overflow: 'auto'}}>{child}</div>
      );
    }

    return (
      <Splitter style={this.props.style}>
        {panels.map(({key, child, info}) => (
          <Splitter.Panel
            key={key}
            defaultSize={info.defaultSize}
            min={info.min}
            resizable={panels.length > 1}
          >
            <div
              className="cp-split-panel-panel"
              style={{height: '100%', overflow: 'auto', ...info.containerStyle}}
            >
              {(info.title || info.closable) && (
                <Row
                  type="flex"
                  justify="space-between"
                  align="middle"
                  className="cp-split-panel-header"
                  style={{padding: '0px 5px'}}
                >
                  <span>{info.title || ''}</span>
                  {info.closable && (
                    <CloseOutlined
                      onClick={(e) => {
                        e.stopPropagation();
                        e.preventDefault();
                        if (this.props.onPanelClose) {
                          this.props.onPanelClose(info.key);
                        }
                      }}
                      style={{cursor: 'pointer'}}
                    />
                  )}
                </Row>
              )}
              {child}
            </div>
          </Splitter.Panel>
        ))}
      </Splitter>
    );
  }
}

const CONTENT_METADATA_PANEL_ORDER = [CONTENT_PANEL_KEY, METADATA_PANEL_KEY];

export class ContentMetadataPanel extends React.Component {
  static propTypes = {
    onPanelClose: PropTypes.func,
    contentContainerStyle: PropTypes.object,
    orientation: PropTypes.oneOf(['horizontal', 'vertical']),
    style: PropTypes.object,
  };

  getContentContainerStyle = () => {
    const defaultStyle = {
      display: 'flex',
      flexDirection: 'column',
    };
    if (this.props.contentContainerStyle) {
      return Object.assign({}, defaultStyle, this.props.contentContainerStyle);
    }
    return defaultStyle;
  };

  getPanelContentInfo() {
    return {
      [CONTENT_PANEL_KEY]: {
        key: CONTENT_PANEL_KEY,
        defaultSize: '75%',
        min: '15%',
        containerStyle: this.getContentContainerStyle(),
      },
      [METADATA_PANEL_KEY]: {
        key: METADATA_PANEL_KEY,
        title: 'Attributes',
        closable: !!this.props.onPanelClose,
        defaultSize: '25%',
        min: 200,
        containerStyle: {display: 'flex', flexDirection: 'column'},
      },
    };
  }

  render() {
    const children = (this.props.children || []).filter(filterRealChild);
    const childrenByKey = {};
    children.forEach((child) => {
      if (child.key) {
        childrenByKey[child.key] = child;
      }
    });
    const contentInfo = this.getPanelContentInfo();
    const panels = CONTENT_METADATA_PANEL_ORDER.filter((key) => childrenByKey[key]).map((key) => ({
      key,
      child: childrenByKey[key],
      info: contentInfo[key],
    }));

    if (panels.length === 0) {
      return null;
    }
    if (panels.length === 1) {
      const {child, info} = panels[0];
      return (
        <div
          className={this.props.className}
          style={{...this.props.style, ...info.containerStyle, overflow: 'auto'}}
        >
          {child}
        </div>
      );
    }

    return (
      <Splitter
        className={this.props.className}
        style={this.props.style}
        orientation={this.props.orientation || 'horizontal'}
      >
        {panels.map(({key, child, info}) => (
          <Splitter.Panel key={key} defaultSize={info.defaultSize} min={info.min} resizable>
            <div
              className="cp-split-panel-panel"
              style={{height: '100%', overflow: 'auto', ...info.containerStyle}}
            >
              {(info.title || info.closable) && (
                <Row
                  type="flex"
                  justify="space-between"
                  align="middle"
                  className="cp-split-panel-header"
                  style={{padding: '0px 5px'}}
                >
                  <span>{info.title || ''}</span>
                  {info.closable && (
                    <CloseOutlined
                      onClick={(e) => {
                        e.stopPropagation();
                        e.preventDefault();
                        if (this.props.onPanelClose) {
                          this.props.onPanelClose(info.key);
                        }
                      }}
                      style={{cursor: 'pointer'}}
                    />
                  )}
                </Row>
              )}
              {child}
            </div>
          </Splitter.Panel>
        ))}
      </Splitter>
    );
  }
}
