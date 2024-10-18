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
import ReactDOM from 'react-dom';
import PropTypes from 'prop-types';
import {
  Button,
  Icon,
  LocaleProvider,
  Modal
} from 'antd';
import enUS from 'antd/lib/locale-provider/en_US';
import classNames from 'classnames';
import {observer} from 'mobx-react';
import PipelineRunInfo from '../../../models/pipelines/PipelineRunInfo';
import RunDisplayName from './run-display-name';
import LayersCheckProvider from './check/layers/provider';
import CommitCheckProvider from './check/commit/provider';
import styles from './pause-confirmation.css';

const pauseRunDialogContainer = document.createElement('div');
document.body.appendChild(pauseRunDialogContainer);

async function getRun (id) {
  const runInfo = new PipelineRunInfo(id);
  await runInfo.fetch();
  if (runInfo.loaded) {
    return runInfo.value || {};
  }
  return {};
}

/**
 * @typedef {Object} CommitConfirmationOptions
 * @property {string|number} id
 * @property {*} [run]
 * @property {string|React.ReactNode} [title]
 * @property {boolean} [layers=true]
 */

class ConfirmPauseContainer extends React.Component {
  state = {
    run: undefined,
    title: undefined,
    visible: false,
    defaultCheckPassed: false,
    resolver: undefined
  };

  componentDidMount () {
    const {onRegisterCallback} = this.props;
    if (typeof onRegisterCallback === 'function') {
      onRegisterCallback(this.confirmPause.bind(this));
    }
  }

  /**
   * @param {CommitConfirmationOptions} options
   * @returns {Promise<boolean>}
   */
  check = async (options = {}) => {
    let resolved = false;
    const {
      id,
      run,
      title
    } = options;
    if (!run && !title) {
      getRun(id)
        .then(runInfo => {
          if (!resolved) {
            this.setState({run: runInfo});
          }
        });
    }
    return new Promise((resolve) => {
      const resolver = (confirm) => {
        resolved = true;
        this.setState({
          visible: false,
          resolver: undefined
        }, () => resolve(confirm));
      };
      this.setState({
        resolver,
        runId: id,
        visible: true,
        run,
        title
      });
    });
  };

  /**
   * @param {CommitConfirmationOptions} options
   * @returns {Promise<void>}
   */
  async confirmPause (options) {
    if (this.previousPromise) {
      await this.previousPromise;
    }
    this.previousPromise = undefined;
    const {
      id
    } = options || {};
    if (!id) {
      return;
    }
    this.previousPromise = new Promise((resolve) =>
      this.check(options)
        .then(resolve)
        .catch((error) => {
          console.warn(`Error checking possibility to commit/pause run #${id}: ${error.message}`);
          resolve();
        })
    );
    return this.previousPromise;
  }

  onCancel = () => {
    const {
      resolver = (decision) => {}
    } = this.state;
    if (typeof resolver === 'function') {
      resolver(false);
    }
  };

  onConfirm = () => {
    const {
      resolver = (decision) => {}
    } = this.state;
    if (typeof resolver === 'function') {
      resolver(true);
    }
  };

  renderTitle = () => {
    const {
      run,
      title
    } = this.state;
    if (title) {
      return title;
    }
    return (
      <span>
        Do you want to pause
        <RunDisplayName
          run={run}
          style={{marginLeft: 5}}
        />
        ?
      </span>
    );
  };

  render () {
    const {
      runId,
      visible
    } = this.state;
    return (
      <LayersCheckProvider active={visible} runId={runId}>
        <CommitCheckProvider skipContainerCheck active={visible} runId={runId}>
          <ConfirmPause
            onCancel={this.onCancel}
            onConfirm={this.onConfirm}
            visible={visible}
            title={this.renderTitle()}
          />
        </CommitCheckProvider>
      </LayersCheckProvider>
    );
  }
}

function ConfirmPauseModal (props) {
  const {
    title,
    onConfirm,
    onCancel,
    visible
  } = props;
  const {pending: commitPending} = CommitCheckProvider.getCheckInfo(props);
  const {pending: layersPending} = LayersCheckProvider.getCheckInfo(props);
  const layersCheckPassed = LayersCheckProvider.getCheckResult(props);
  const pending = commitPending || layersPending;
  return (
    <Modal
      title={false}
      closable={false}
      maskClosable={false}
      visible={visible}
      onCancel={onCancel}
      footer={false}
      width={450}
    >
      <div
        className={styles.body}
      >
        <div className={styles.title}>
          <Icon
            type="question-circle"
            className={
              classNames(
                'cp-warning',
                styles.icon
              )
            }
          />
          {
            title
          }
        </div>
        <CommitCheckProvider.Warning />
        <LayersCheckProvider.Warning />
      </div>
      <div
        className={styles.footer}
      >
        <Button
          className={styles.action}
          id="cancel-pause-run"
          onClick={onCancel}
        >
          CANCEL
        </Button>
        <Button
          className={styles.action}
          id="confirm-pause-run"
          type="primary"
          disabled={pending || !layersCheckPassed}
          onClick={onConfirm}
        >
          {
            pending && (
              <Icon type="loading" />
            )
          }
          PAUSE
        </Button>
      </div>
    </Modal>
  );
}

const ConfirmPause = LayersCheckProvider.inject(
  CommitCheckProvider.inject(
    observer(ConfirmPauseModal)
  )
);

ConfirmPause.propTypes = {
  title: PropTypes.node,
  visible: PropTypes.bool,
  onCancel: PropTypes.func,
  onConfirm: PropTypes.func
};

ConfirmPauseContainer.propTypes = {
  onRegisterCallback: PropTypes.func
};

let confirmPauseCallback;
function registerCallback (callback) {
  confirmPauseCallback = callback;
}

ReactDOM.render(
  (
    <LocaleProvider locale={enUS}>
      <ConfirmPauseContainer onRegisterCallback={registerCallback} />
    </LocaleProvider>
  ),
  pauseRunDialogContainer
);

/**
 * @param {CommitConfirmationOptions} options
 * @returns {*}
 */
export default function confirmPause (options) {
  return confirmPauseCallback(options);
}
