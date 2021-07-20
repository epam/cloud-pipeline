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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import classNames from 'classnames';
import HaloTool from './halo-tool';
import {
  Button,
  Icon,
  message,
  Popover
} from 'antd';
import styles from './open-in-halo.css';
import ToolImage from '../../../../models/tools/ToolImage';
import fetchActiveJobs from './fetch-active-jobs';
import roleModel from '../../../../utils/roleModel';
import {PipelineRunner} from '../../../../models/pipelines/PipelineRunner';
import getToolLaunchingOptions
  from '../../../pipelines/launch/utilities/get-tool-launching-options';
import HaloJobLink from './halo-job-link';

const haloToolRequest = new HaloTool();

const ButtonTitle = ({toolId, iconId, titleStyle}) => iconId && toolId
  ? (
    <img
      src={ToolImage.url(toolId, iconId)}
      className={styles.toolIcon}
      style={titleStyle}
    />
  )
  : (<span style={titleStyle}>Open in HALO</span>);

@roleModel.authenticationInfo
@inject('awsRegions', 'preferences', 'dataStorages')
@inject(() => ({
  haloTool: haloToolRequest
}))
@observer
class OpenInHaloAction extends React.Component {
  static ActionAvailable (file) {
    return /\.(vsi|mrxs)$/i.test(file);
  }

  pathElement;

  state = {
    modalVisible: false,
    activeJobsFetching: false,
    activeJob: undefined
  };

  componentDidMount () {
    this.props.haloTool.fetch();
  }

  openModal = () => {
    this.setState({
      modalVisible: true,
      activeJobsFetching: true,
      activeJob: undefined
    }, this.fetchJobs);
  };

  fetchJobs = () => {
    this.setState({
      activeJobsFetching: true,
      activeJob: undefined
    }, () => {
      fetchActiveJobs(this.props.authenticatedUserInfo)
        .then(jobs => {
          const dockerImage = this.haloTool
            ? new RegExp(`^${this.haloTool.registry}/${this.haloTool.image}(:|$)`, 'i')
            : undefined;
          const job = jobs.find(j => dockerImage.test(j.dockerImage));
          if (job) {
            this.setState({
              activeJobsFetching: false,
              activeJob: job.id
            });
          } else {
            this.setState({
              activeJobsFetching: false,
              activeJob: undefined
            });
          }
        });
    });
  };

  closeModal = () => {
    this.setState({
      modalVisible: false
    });
  };

  onClick = e => {
    e.stopPropagation();
    e.preventDefault();
    this.openModal();
  };

  get fileName () {
    const {file} = this.props;
    if (file) {
      return file.split('/').pop();
    }
    return undefined;
  }

  @computed
  get storage () {
    const {storageId, dataStorages} = this.props;
    if (storageId && dataStorages.loaded) {
      return (dataStorages.value || []).find(s => +(s.id) === +storageId);
    }
    return undefined;
  }

  @computed
  get haloTool () {
    const {haloTool} = this.props;
    if (haloTool.loaded) {
      return haloTool.tool;
    }
    return undefined;
  }

  launch = (e) => {
    e.stopPropagation();
    e.preventDefault();
    if (this.haloTool) {
      const hide = message.loading('Launching HALO...', 0);
      const request = new PipelineRunner();
      getToolLaunchingOptions(this.props, this.haloTool)
        .then((launchPayload) => {
          return request.send({...launchPayload, force: true});
        })
        .then(() => {
          if (request.error) {
            throw new Error(request.error);
          } else if (request.loaded) {
            const {id} = request.value;
            return Promise.resolve(+id);
          }
        })
        .catch(e => {
          message.error(e.message, 5);
          return Promise.resolve();
        })
        .then((runId) => {
          hide();
          this.setState({activeJob: runId});
        });
    }
  };

  renderModal = () => {
    const {
      modalVisible,
      activeJob,
      activeJobsFetching
    } = this.state;
    const {file} = this.props;
    if (!file || !this.storage) {
      return null;
    }
    const filePath = `Z:\\\\${this.storage.name}\\${file.replace(/\//g, '\\')}`;
    const initializePathElement = element => {
      this.pathElement = element;
    };
    const copy = (e) => {
      e.stopPropagation();
      e.preventDefault();
      if (this.pathElement) {
        const range = document.createRange();
        range.setStart(this.pathElement, 0);
        range.setEnd(this.pathElement, 1);
        window.getSelection().removeAllRanges();
        window.getSelection().addRange(range);
        if (document.execCommand('copy')) {
          message.info('Copied to clipboard', 3);
          window.getSelection().removeAllRanges();
        }
      }
    };
    return (
      <ul className={styles.list}>
        <li>
          <span>
            Copy file path:
          </span>
          <br />
          <div className={styles.code}>
            <div className={styles.part} style={{flex: 1}}>
              <pre ref={initializePathElement}>
                {filePath}
              </pre>
            </div>
            <div
              className={classNames(styles.part, styles.button)}
              onClick={copy}
            >
              <Icon type="copy" />
            </div>
          </div>
        </li>
        <li>
          <HaloJobLink
            jobId="1726"
          />
        </li>
        <li>
          {
            activeJobsFetching && (<Icon type="loading" />)
          }
          {
            !activeJobsFetching && modalVisible && !!activeJob && (
              <HaloJobLink
                jobId={activeJob}
              />
            )
          }
          {
            !activeJobsFetching && !activeJob && (
              <span>
                Run personal HALO instance:
                <Button
                  size="small"
                  type="primary"
                  onClick={this.launch}
                  style={{marginLeft: 5}}
                >
                  Launch
                </Button>
              </span>
            )
          }
        </li>
        <li>
          Open copied file path in the HALO application
        </li>
      </ul>
    );
  };

  modalVisibilityChanged = visible => {
    if (visible) {
      this.openModal();
    } else {
      this.closeModal();
    }
  };

  render () {
    const {
      className,
      file,
      mode,
      style,
      size,
      titleStyle
    } = this.props;
    const {modalVisible} = this.state;
    if (
      !OpenInHaloAction.ActionAvailable(file) ||
      !this.haloTool ||
      !this.storage
    ) {
      return null;
    }
    let component;
    if (/^link$/i.test(mode)) {
      component = (
        <span
          className={classNames(styles.link, className)}
          style={style}
          onClick={this.onClick}
        >
          <ButtonTitle
            toolId={this.haloTool ? this.haloTool.id : undefined}
            iconId={this.haloTool ? this.haloTool.iconId : undefined}
            titleStyle={titleStyle}
          />
        </span>
      );
    } else {
      component = (
        <Button
          className={classNames(styles.link, className)}
          style={style}
          size={size}
          onClick={this.onClick}
        >
          <ButtonTitle
            toolId={this.haloTool ? this.haloTool.id : undefined}
            iconId={this.haloTool ? this.haloTool.iconId : undefined}
            titleStyle={titleStyle}
          />
        </Button>
      );
    }
    return (
      <Popover
        onVisibleChange={this.modalVisibilityChanged}
        visible={modalVisible}
        trigger={['click']}
        title={false}
        content={this.renderModal()}
        placement="left"
      >
        {component}
      </Popover>
    );
  }
}

OpenInHaloAction.propTypes = {
  file: PropTypes.string,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  mode: PropTypes.oneOf(['link', 'button']),
  className: PropTypes.string,
  style: PropTypes.object,
  size: PropTypes.oneOf(['small', 'default', 'large']),
  titleStyle: PropTypes.object
};

OpenInHaloAction.defaultProps = {
  mode: 'link',
  size: 'default'
};

export default OpenInHaloAction;
