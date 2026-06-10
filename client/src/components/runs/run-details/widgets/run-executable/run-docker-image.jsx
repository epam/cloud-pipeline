import React from 'react';
import PropTypes from 'prop-types';
import {Link} from 'react-router-dom';
import {Popover} from 'antd';
import {ToolOutlined} from '@ant-design/icons';
import {inject, observer} from 'mobx-react';
import {getDockerImage} from '../../../../../utils/get-docker-image';
import ToolImage from '../../../../../models/tools/ToolImage';
import styles from './run-executable.module.css';

function RunDockerImage(props) {
  const {className, style, run, dockerRegistries} = props;
  if (!run) {
    return null;
  }
  const {dockerImage} = run || {};
  const toolInfo = getDockerImage(dockerImage, dockerRegistries);
  if (toolInfo) {
    const toolName = dockerImage.split('/').pop();
    return (
      <Popover content={<div>{dockerImage}</div>}>
        <div className={className} style={style}>
          {toolInfo.tool.iconId ? (
            <img
              className={styles.toolIcon}
              src={ToolImage.url(toolInfo.tool.id, toolInfo.tool.iconId)}
              style={style}
            />
          ) : (
            <ToolOutlined />
          )}
          <Link to={`/tool/${toolInfo.tool.id}`}>
            <span>{toolName}</span>
          </Link>
        </div>
      </Popover>
    );
  }
  if (dockerImage) {
    const toolName = dockerImage.split('/').pop();
    return (
      <Popover content={<div>{dockerImage}</div>}>
        <span className={className} style={style}>
          {toolName}
        </span>
      </Popover>
    );
  }
  return null;
}

RunDockerImage.propTypes = {
  className: PropTypes.string,
  run: PropTypes.object,
};

export default inject('dockerRegistries')(observer(RunDockerImage));
