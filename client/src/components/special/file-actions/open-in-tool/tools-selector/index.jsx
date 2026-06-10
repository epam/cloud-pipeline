/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Dropdown} from 'antd';
import {DownOutlined} from '@ant-design/icons';
import ToolImage from '../../../../../models/tools/ToolImage';
import styles from './tool-selector.module.css';

const ToolIcon = ({iconId, toolId, style}) => {
  if (toolId && iconId) {
    return <img className={styles.toolIcon} src={ToolImage.url(toolId, iconId)} style={style} />;
  }
  return null;
};

const ButtonTitle = ({toolId, iconId, titleStyle, toolName}) => {
  return iconId && toolId ? (
    <ToolIcon style={titleStyle} toolId={toolId} iconId={iconId} />
  ) : (
    <span style={titleStyle}>{`Open in ${toolName}`}</span>
  );
};

class ToolsSelector extends React.Component {
  onClick = (toolId, event) => {
    const {onSelectTool} = this.props;
    if (event) {
      event.stopPropagation();
    }
    if (event) {
      event.preventDefault();
    }
    if (onSelectTool) {
      onSelectTool(toolId);
    }
  };

  onMenuClick = ({key, domEvent}) => {
    this.onClick(Number(key), domEvent);
  };

  getMenuItems = () => {
    const {tools} = this.props;
    if (!tools || !tools.length) {
      return [];
    }
    return tools.map((tool) => ({
      key: tool.id,
      className: styles.dropdownItem,
      label: (
        <>
          <ToolIcon toolId={tool.id} iconId={tool.iconId} />
          <span className={styles.toolName}>{tool.image}</span>
        </>
      ),
    }));
  };

  render() {
    const {tools, className, style, titleStyle, singleMode} = this.props;
    if (tools.length === 0) {
      return null;
    }
    return singleMode || tools.length === 1 ? (
      <span
        className={classNames(styles.link, className)}
        style={style}
        onClick={(e) => this.onClick(tools[0].id, e)}
      >
        <ButtonTitle
          toolId={tools[0].id}
          iconId={tools[0].iconId}
          toolName={tools[0].image}
          titleStyle={titleStyle}
        />
      </span>
    ) : (
      <Dropdown menu={{items: this.getMenuItems(), onClick: this.onMenuClick}}>
        <span
          className={classNames(styles.selectorBtn, className)}
          onClick={(e) => this.onClick(tools[0].id, e)}
          style={style}
        >
          <ButtonTitle
            toolId={tools[0].id}
            iconId={tools[0].iconId}
            toolName={tools[0].image}
            titleStyle={titleStyle}
          />
          <DownOutlined style={{marginLeft: '10px'}} />
        </span>
      </Dropdown>
    );
  }
}

ToolsSelector.propTypes = {
  style: PropTypes.object,
  titleStyle: PropTypes.object,
  onSelectTool: PropTypes.func,
  tools: PropTypes.arrayOf(PropTypes.object),
  singleMode: PropTypes.bool,
};

export default ToolsSelector;
export {ToolIcon};
