/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer, inject} from 'mobx-react';
import classNames from 'classnames';
import {Button, Checkbox, Icon} from 'antd';
import ColorPicker from '../../color-picker';
import styles from './cell-profiler.css';

class ObjectsOutline extends React.Component {
  state = {
    pending: false
  };
  updateOutlines = () => {
    const {
      pipeline,
      viewer
    } = this.props;
    if (
      !pipeline ||
      !pipeline.graphicsOutput
    ) {
      return;
    }
    this.setState({
      pending: true
    }, () => {
      pipeline.graphicsOutput.renderOutlines(viewer)
        .then(() => {
          this.setState({
            pending: false
          });
        });
    });
  };
  toggleGlobalVisibilitySelector = (e) => {
    const {
      pipeline
    } = this.props;
    if (
      !pipeline ||
      !pipeline.graphicsOutput
    ) {
      return null;
    }
    pipeline.graphicsOutput.hidden = !e.target.checked;
    this.updateOutlines();
  };

  /**
   * @param {OutlineObjectConfiguration} configuration
   * @returns {JSX.Element|null}
   */
  renderColorConfiguration = (configuration) => {
    if (!configuration) {
      return null;
    }
    const {
      color
    } = configuration;
    if (!color) {
      return null;
    }
    return (
      <ColorPicker
        color={color}
        hex
        onChange={aColor => {
          configuration.color = aColor;
          this.updateOutlines();
        }}
        ignoreAlpha
      />
    );
  };

  render () {
    const {
      className,
      style,
      pipeline,
      viewer
    } = this.props;
    if (
      !pipeline ||
      !pipeline.graphicsOutput ||
      !pipeline.graphicsOutput.configurations ||
      !pipeline.graphicsOutput.configurations.length
    ) {
      return null;
    }
    const {
      pending
    } = this.state;
    const graphicsOutput = pipeline.graphicsOutput;
    const configurations = graphicsOutput.configurations;
    return (
      <div
        className={
          classNames(
            className,
            styles.cpAnalysisObjectsOutlines
          )
        }
        style={style}
      >
        <div
          className={styles.cpAnalysisObjectsOutlineRow}
        >
          <b>Objects</b>
          <Checkbox
            disabled={pending}
            checked={!graphicsOutput.hidden}
            style={{marginLeft: 'auto'}}
            onChange={this.toggleGlobalVisibilitySelector}
          >
            Display
          </Checkbox>
        </div>
        {
          configurations.map((config, index) => (
            <div
              key={config.object}
              className={styles.cpAnalysisObjectsOutlineRow}
            >
              <Checkbox
                disabled={graphicsOutput.hidden || pending}
                checked={config.visible}
                onChange={(e) => {
                  config.visible = e.target.checked;
                  this.updateOutlines();
                }}
                style={{
                  marginRight: 'auto'
                }}
              >
                <b>{config.object}</b>
              </Checkbox>
              <Button
                style={{marginRight: 5}}
                disabled={index === 0 || pending}
                onClick={() => graphicsOutput.moveUp(index, viewer)}
                size="small"
              >
                <Icon type="up" />
              </Button>
              <Button
                style={{marginRight: 5}}
                disabled={index === configurations.length - 1 || pending}
                onClick={() => graphicsOutput.moveDown(index, viewer)}
                size="small"
              >
                <Icon type="down" />
              </Button>
              {
                this.renderColorConfiguration(config)
              }
            </div>
          ))
        }
      </div>
    );
  }
}

ObjectsOutline.propTypes = {
  className: PropTypes.string,
  pipeline: PropTypes.object,
  viewer: PropTypes.object,
  style: PropTypes.object
};

const ObjectsOutlineRenderer = observer(ObjectsOutline);

export {ObjectsOutlineRenderer};
export default inject('hcsAnalysis')(
  inject(({hcsAnalysis}) => {
    if (hcsAnalysis) {
      return {
        pipeline: hcsAnalysis.pipeline,
        viewer: hcsAnalysis.hcsImageViewer
      };
    }
    return {};
  })(ObjectsOutlineRenderer)
);
