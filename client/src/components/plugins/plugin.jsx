import React from 'react';
import PropTypes from 'prop-types';
import PluginLoader from './plugin-loader';
import {fetchPlugin, UI_PLUGIN_TYPE_LAUNCH_FORM, UI_PLUGIN_TYPE_RUN_LOG} from './utilities';
import LoadingView from '../special/LoadingView';
import {inject, observer} from 'mobx-react';
import {run, runPipelineActions, submitsRun} from '../runs/actions';
import SessionStorageWrapper from '../special/SessionStorageWrapper';
import localization from '../../utils/localization';
import roleModel from '../../utils/roleModel';

@localization.localizedComponent
@submitsRun
@runPipelineActions
@roleModel.authenticationInfo
@inject('awsRegions', 'pipelines', 'preferences', 'dockerRegistries', 'usersInfo')
@inject('routing')
@observer
class Plugin extends React.PureComponent {
  state = {
    pluginOptions: {},
    plugin: undefined,
    pending: false,
    initialized: false
  };

  _token = {};

  componentDidMount () {
    this.updatePluginOptions();
    this.loadPlugin();
  }

  componentDidUpdate (prevProps) {
    const {
      pipelineId,
      pipelineVersion,
      pipelineConfiguration,
      runId,
      toolId,
      toolVersion,
      pluginType
    } = this.props;
    const {
      pipelineId: prevPipelineId,
      pipelineVersion: prevPipelineVersion,
      pipelineConfiguration: prevPipelineConfiguration,
      runId: prevRunId,
      toolId: prevToolId,
      toolVersion: prevToolVersion,
      pluginType: prevPluginType
    } = prevProps;
    if (
      pipelineId !== prevPipelineId ||
      pipelineVersion !== prevPipelineVersion ||
      pipelineConfiguration !== prevPipelineConfiguration ||
      runId !== prevRunId ||
      toolId !== prevToolId ||
      toolVersion !== prevToolVersion
    ) {
      this.updatePluginOptions();
    }
    if (
      pipelineId !== prevPipelineId ||
      pipelineVersion !== prevPipelineVersion ||
      runId !== prevRunId ||
      toolId !== prevToolId ||
      toolVersion !== prevToolVersion ||
      pluginType !== prevPluginType
    ) {
      this.loadPlugin();
    }
  }

  componentWillUnmount () {
    this.abortFetchPlugin();
  }

  abortFetchPlugin = () => {
    this._token = {};
  };

  updatePluginOptions = () => {
    const {
      pipelineId,
      pipelineVersion,
      pipelineConfiguration,
      runId,
      toolId,
      toolVersion
    } = this.props;
    this.setState({
      pluginOptions: {
        pipelineId,
        pipelineVersion,
        pipelineConfiguration,
        run: runId,
        dockerImage: toolId,
        toolVersion
      }
    });
  };

  loadPlugin = () => {
    this.abortFetchPlugin();
    const token = this._token = {};
    const commit = async (st, cb) => {
      if (token === this._token) {
        return new Promise(resolve => {
          this.setState(st, () => {
            if (cb) {
              cb();
            }
            resolve();
          });
        });
      }
    };
    const {
      pipelineId,
      pipelineVersion,
      runId,
      toolId,
      toolVersion,
      pluginType
    } = this.props;
    (async () => {
      if (pipelineId || toolId || runId) {
        await commit({
          initialized: false,
          pending: false,
          plugin: undefined
        });
        try {
          const uiPlugin = await fetchPlugin({
            pipelineId,
            pipelineVersion,
            runId,
            toolId,
            toolVersion,
            pluginType
          });
          await commit({
            initialized: true,
            pending: false,
            plugin: uiPlugin
          });
        } catch (error) {
          console.error('error fetching plugin', error);
          await commit({
            initialized: true,
            pending: false,
            plugin: undefined
          });
        }
      } else {
        await commit({
          initialized: true,
          pending: false,
          plugin: undefined
        });
      }
    })();
  };

  onLaunchPlugin = async (payload) => {
    const {routing} = this.props;
    const runResolved = await run(this)(
      payload
    );
    if (runResolved) {
      SessionStorageWrapper.navigateToActiveRuns(routing);
    }
  }

  onLaunched = async (runId) => {
    const {routing} = this.props;
    SessionStorageWrapper.navigateToRun(routing, runId);
  }

  render () {
    const {className, style, children, pluginType} = this.props;
    const {plugin, pending, initialized, pluginOptions} = this.state;
    if (!initialized && pending) {
      return (
        <div className={className} style={style}>
          <LoadingView />
        </div>
      );
    }
    if (plugin) {
      return (
        <PluginLoader
          plugin={plugin}
          type={pluginType}
          className={className}
          style={style}
          pluginOptions={pluginOptions}
          onLaunch={this.onLaunchPlugin}
          onLaunched={this.onLaunched}
        />
      );
    }
    return (
      <div className={className} style={style}>
        {children}
      </div>
    );
  }
}

Plugin.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  children: PropTypes.node,
  pluginType: PropTypes.string.isRequired,
  pipelineId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  pipelineVersion: PropTypes.string,
  pipelineConfiguration: PropTypes.string,
  runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  toolId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  toolVersion: PropTypes.string
};

function LaunchFormPlugin (props) {
  return (<Plugin {...props} pluginType={UI_PLUGIN_TYPE_LAUNCH_FORM} />);
}

LaunchFormPlugin.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  children: PropTypes.node,
  pipelineId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  pipelineVersion: PropTypes.string,
  pipelineConfiguration: PropTypes.string,
  runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  toolId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  toolVersion: PropTypes.string
};

function RunLogPlugin (props) {
  return (<Plugin {...props} pluginType={UI_PLUGIN_TYPE_RUN_LOG} />);
}

RunLogPlugin.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  children: PropTypes.node,
  pipelineId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  pipelineVersion: PropTypes.string,
  pipelineConfiguration: PropTypes.string,
  runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  toolId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  toolVersion: PropTypes.string
};

export {LaunchFormPlugin, RunLogPlugin};

export default Plugin;
