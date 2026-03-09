import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Alert, Menu} from 'antd';
import styles from './launch-command.css';
import {getOS, OperationSystems} from '../../../../../utils/OSDetection';
import {API_PATH, SERVER} from '../../../../../config';
import {inject, observer} from 'mobx-react';
import {computed, makeObservable} from 'mobx';
import LoadingView from '../../../../special/LoadingView';
import BashCode from '../../../../special/bash-code';

const isWindowsOS = getOS() === OperationSystems.windows;

const tabCliLinux = {
  tab: 'cli-linux',
  title: (<span>CLI - Linux</span>)
};

const tabCliWindows = {
  tab: 'cli-windows',
  title: (<span>CLI - Windows</span>)
};

const defaultCliCommand = isWindowsOS ? tabCliWindows.tab : tabCliLinux.tab;

const tabs = [
  ...(isWindowsOS ? [tabCliWindows, tabCliLinux] : [tabCliLinux, tabCliWindows]),
  {
    tab: 'api',
    title: (<span>API</span>)
  }
];

function wrapCommand (command, template) {
  if (!template) {
    return `# PIPE CLI command: \n${command || ''}`;
  }
  return template.replace(/\{LAUNCH_COMMAND\}/ig, command);
}

function wrapNewLines (command) {
  if (!command) {
    return '';
  }
  return command.replace(/\\\\n/g, '\n');
}

function generateRunMethodUrl () {
  const el = document.createElement('div');
  el.innerHTML = '<a href="' + (SERVER + API_PATH) + '/run"></a>';
  return el.firstChild.href;
}

@inject('preferences')
@observer
class RunLaunchCommandSection extends React.Component {
  state = {
    tab: defaultCliCommand
  };

  constructor (props) {
    super(props);
    makeObservable(this, {
      launchCommandTemplate: computed
    });
  }

  get launchCommandTemplate () {
    const {preferences} = this.props;
    return preferences.getPreferenceValue('ui.launch.command.template');
  }

  onTabChange = ({key: tab}) => this.setState({tab});

  renderCliContent = () => {
    const {
      linuxCode,
      windowsCode,
      pending
    } = this.props;
    const {
      tab
    } = this.state;
    const code = tab === tabCliLinux.tab ? linuxCode : windowsCode;
    if (pending && !code) {
      return (
        <LoadingView />
      );
    }
    return (
      <BashCode
        id="launch-command"
        className={styles.launchCommand}
        code={
          wrapNewLines(
            wrapCommand(code, this.launchCommandTemplate)
          )
        }
      />
    );
  };

  renderApiContent = () => {
    const {
      runPayload,
      pending
    } = this.props;
    if (pending && !runPayload) {
      return (
        <LoadingView />
      );
    }
    return (
      <BashCode
        id="launch-command"
        className={styles.launchCommand}
        code={`POST ${generateRunMethodUrl()}\n\n`.concat(JSON.stringify(runPayload, null, ' '))}
      />
    );
  }

  renderTabContent = () => {
    const {
      error,
      pending
    } = this.props;
    if (!pending && error) {
      return (
        <Alert type="error" title={error} />
      );
    }
    const {tab} = this.state;
    switch (tab) {
      case tabCliLinux.tab:
      case tabCliWindows.tab:
        return this.renderCliContent();
      default:
        return this.renderApiContent();
    }
  };

  render () {
    const {
      className,
      style,
      run
    } = this.props;
    if (!run) {
      return null;
    }
    const {tab} = this.state;
    return (
      <div
        className={classNames(
          className,
          styles.runLaunchCommandSection
        )}
        style={style}
      >
        <div
          className={classNames(
            styles.runLaunchCommandTabs,
            'cp-run-details-tabs',
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )}
        >
          <Menu
            className={styles.runLaunchCommandTabsMenu}
            selectedKeys={[tab]}
            style={{cursor: 'pointer'}}
            mode="horizontal"
            onClick={this.onTabChange}
          >
            {
              tabs.map((tab) => (
                <Menu.Item
                  key={tab.tab}
                  id={tab.tab}
                >
                  <div className={styles.runLaunchCommandTabsMenuItem}>
                    <span>{tab.title}</span>
                  </div>
                </Menu.Item>
              ))
            }
          </Menu>
        </div>
        <div
          className={classNames(
            styles.runLaunchCommandContent,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )}
        >
          {this.renderTabContent()}
        </div>
      </div>
    );
  }
}

RunLaunchCommandSection.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  pending: PropTypes.bool,
  error: PropTypes.string,
  linuxCode: PropTypes.string,
  windowsCode: PropTypes.string,
  runPayload: PropTypes.object
};

export default RunLaunchCommandSection;
