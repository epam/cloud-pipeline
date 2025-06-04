import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './mlflow-engine.css';
import {inject, observer} from 'mobx-react';
import {Alert, Button, message, Modal} from 'antd';
import {PUBLIC_URL} from '../../../../../config';
import {computed} from 'mobx';
import LoadingView from '../../../../special/LoadingView';

function getStaticResourcePath (url) {
  if (!url) {
    return undefined;
  }
  if (url.startsWith('/')) {
    url = url.slice(1);
  }
  if (!PUBLIC_URL || !PUBLIC_URL.length) {
    return url;
  }
  if (PUBLIC_URL.endsWith('/')) {
    return `${PUBLIC_URL}${url}`;
  }
  return `${PUBLIC_URL}/${url}`;
}

@inject('themes', 'routing', 'preferences')
@observer
class MLFlowEngine extends React.Component {
  state = {
    deployModel: undefined,
    confirmation: undefined
  };

  get currentThemeIsDark () {
    const {themes} = this.props;
    if (themes && themes.currentTheme && themes.themes) {
      const getRoot = (id) => {
        const t = themes.themes.find((o) => o.identifier === id);
        if (t && !('dark' in t) && t.extends) {
          return getRoot(t.extends);
        }
        return t;
      };
      const root = getRoot(themes.currentTheme);
      return root ? root.dark : false;
    }
    return false;
  }

  get experimentId () {
    const {run = {}} = this.props;
    const {tags = {}} = run;
    const {
      cp_mlflow_experiment_id_tag_name: cpMlFlowExperimentIdTagName = 'CP_MLFLOW_EXPERIMENT_ID'
    } = this.uiMlflowSettings ?? {};
    return tags[cpMlFlowExperimentIdTagName];
  }

  @computed
  get uiMlflowSettings () {
    const {preferences} = this.props;
    return preferences.uiMlflowSettings;
  }

  componentDidMount () {
    window.addEventListener('message', this.onMessage);
    this._token = setInterval(() => this.synchronizeTheme(), 250);
  }

  componentWillUnmount () {
    window.removeEventListener('message', this.onMessage);
    clearInterval(this._token);
  }

  onMessage = (event) => {
    const {
      data
    } = event || {};
    if (data && typeof data === 'object') {
      const {
        messageType,
        runId,
        error,
        confirmation
      } = data;
      if (messageType === 'model-deployed' && runId && typeof runId === 'number') {
        this.setState({deployModel: {runId}});
      } else if (messageType === 'model-deploy-error') {
        this.setState({deployModel: undefined});
        message.error(
          error && typeof error === 'string'
            ? (<span>Error deploying model: {error}</span>)
            : <span>Error deploying model</span>,
          5
        );
      } else if (messageType === 'model-deploy-confirm' && confirmation) {
        const {
          id
        } = confirmation || {};
        this.setState({confirmation: {id}});
        Modal.confirm({
          title: (
            <span>
              Are you sure you want to deploy this model?
            </span>
          ),
          style: {
            wordWrap: 'break-word'
          },
          onOk: () => {
            const {uiMlflowSettings = {}} = this;
            const {mlflow_base: mlFlowBase} = uiMlflowSettings;
            if (this.iframe && mlFlowBase) {
              const url = new URL(mlFlowBase);
              this.iframe.contentWindow.postMessage(
                {messageType: 'model-deploy-confirm-done', confirmation: {id}},
                url.origin
              );
            }
          },
          okText: 'DEPLOY',
          cancelText: 'CANCEL'
        });
      }
    }
  };

  onCloseDeployedModelAlert = () => {
    this.setState({deployModel: undefined});
  };

  initialize = (iframe) => {
    this.iframe = iframe;
    this.synchronizeTheme();
  };

  onLoad = (e) => {
    const iframe = e.target;
    this.initialize(iframe);
    this.synchronizeTheme();
  }

  synchronizeTheme = () => {
    const {uiMlflowSettings = {}} = this;
    const {mlflow_base: mlFlowBase} = uiMlflowSettings;
    if (this.iframe && this.iframe.contentWindow && mlFlowBase) {
      const url = new URL(mlFlowBase);
      this.iframe.contentWindow.postMessage(
        {messageType: 'set-theme', dark: this.currentThemeIsDark},
        url.origin
      );
    }
  };

  onOpenDeployedModel = (e) => {
    if (e) {
      e.stopPropagation();
      e.preventDefault();
    }
    const {deployModel} = this.state;
    if (deployModel && deployModel.runId) {
      const {routing} = this.props;
      if (routing && routing.push && typeof routing.push === 'function') {
        routing.push(`/run/${deployModel.runId}`);
      }
    }
    this.onCloseDeployedModelAlert();
  };

  render () {
    const {
      className,
      style,
      run
    } = this.props;
    const {id: cpRunId} = run || {};
    const {
      uiMlflowSettings = {},
      experimentId
    } = this;
    const {
      mlflow_base: mlFlowBase
    } = uiMlflowSettings;
    const {
      deployModel
    } = this.state;
    const {
      runId
    } = deployModel || {};
    const runLink = runId ? (
      <a
        href={getStaticResourcePath(`#/run/${deployModel.runId}`)}
        onClick={this.onOpenDeployedModel}
      >#{deployModel.runId}
      </a>
    ) : undefined;
    if (!mlFlowBase) {
      return (
        <div className={classNames(className, styles.mlflowEngineContainer)} style={style}>
          <div className={styles.centered}>
            <Alert type="warning" message="MLflow server endpoint not specified" />
          </div>
        </div>
      );
    }
    if (!experimentId) {
      return (
        <div className={classNames(className, styles.mlflowEngineContainer)} style={style}>
          <div className={styles.centered}>
            <LoadingView>
              <span className="cp-text-not-important" style={{fontSize: 'larger'}}>
                Waiting for <b>MLflow run</b> to be started
              </span>
            </LoadingView>
          </div>
        </div>
      );
    }
    const mlflowUrl = `${mlFlowBase}#/embedded/cp/${cpRunId}`;
    return (
      <div className={classNames(className, styles.mlflowEngineContainer)} style={style}>
        {mlflowUrl && (
          <iframe
            className={styles.mlflow}
            src={mlflowUrl}
            onLoad={this.onLoad}
            ref={this.initialize}
          />)
        }
        <Modal
          visible={deployModel !== undefined}
          title="Model deployment has been started"
          onCancel={this.onCloseDeployedModelAlert}
          footer={(
            <div style={{display: 'flex', alignItems: 'center', justifyContent: 'flex-end'}}>
              <Button type="primary" onClick={this.onCloseDeployedModelAlert}>OK</Button>
            </div>
          )}
        >
          {
            runLink
              ? (
                <div style={{fontSize: 'larger'}}>
                  Deployment ID: {runLink}
                </div>
              ) : undefined
          }

        </Modal>
      </div>
    );
  }
}

MLFlowEngine.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object
};

export default MLFlowEngine;
