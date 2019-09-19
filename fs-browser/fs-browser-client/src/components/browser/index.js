import React from 'react';
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import Icon from '../shared/icon';
import displaySize from '../../utilities/display-size';
import {parse} from '../../utilities/query-parameters';
import {ListDirectory} from '../../models';
import {
  DownloadAction,
  RemoveAction,
} from './actions';
import TaskQueue from './task-queue';
import Upload from '../upload';
import styles from './browser.css';

function rootsAreEqual(rootA, rootB) {
  if (!rootA && !rootB) {
    return true;
  }
  if (!rootA) {
    return false;
  }
  if (!rootB) {
    return false;
  }
  let a = rootA.startsWith('/') ? rootA.substring(1) : rootA;
  let b = rootB.startsWith('/') ? rootB.substring(1) : rootB;
  if (a.endsWith('/')) {
    a = a.substring(0, a.length - 1);
  }
  if (b.endsWith('/')) {
    b = b.substring(0, b.length - 1);
  }
  return a.toLowerCase() === b.toLowerCase();
}

@inject('taskManager')
@inject(({taskManager}, params) => {
  const {path} = parse(params.history.location.search);
  return {
    path,
    directory: new ListDirectory(path),
    taskManager,
  };
})
@observer
class Browser extends React.Component {
  state = {
    disabled: false,
  };

  componentDidMount() {
    const {taskManager} = this.props;
    if (taskManager) {
      taskManager.registerListener(this.reloadIfNeeded);
    }
  }

  componentWillUnmount() {
    const {taskManager} = this.props;
    if (taskManager) {
      taskManager.registerListener(null);
    }
  }

  reloadIfNeeded = (task) => {
    const {activeSession, item} = task;
    const {path, directory} = this.props;
    if (activeSession && item && rootsAreEqual(item.root, path)) {
      directory.fetch();
    }
  };

  blockingOperation = fn => (...opts) => {
    this.setState({disabled: true}, async () => {
      await fn(...opts);
      this.setState({
        disabled: false,
      });
    });
  };

  onSelectItem = item => (e) => {
    e.stopPropagation();
    const {disabled} = this.state;
    if (disabled) {
      return;
    }
    const {history} = this.props;
    if (item.isFolder && item.path && item.path.length > 0) {
      history.push(`?path=${item.path}`);
    } else if (item.isFolder) {
      history.push('/');
    }
  };

  onRemove = async () => {
    const {directory} = this.props;
    return directory.fetch();
  };

  renderDirectory = () => {
    const {path, directory} = this.props;
    if (directory.pending && !directory.loaded) {
      return (
        <Icon type="loading" />
      );
    }
    if (directory.error) {
      return (
        <div className={styles.alert}>
          {directory.error}
        </div>
      );
    }
    if (!directory.loaded) {
      return (
        <div className={styles.alert}>
          Error fetching directory contents
        </div>
      );
    }
    const {disabled} = this.state;
    const elements = [];
    const parts = (path || '').split('/').filter(l => l.length > 0);
    if (parts.length > 0) {
      elements.push({
        name: '..',
        path: parts.slice(0, parts.length - 1).join('/'),
        isFolder: true,
        downloadable: false,
        removable: false,
      });
    }
    const items = (directory.value || [])
      .map(d => ({
        ...d,
        isFolder: /^folder$/i.test(d.type),
        downloadable: true,
        removable: true,
      }));
    items.sort((a, b) => {
      if (a.name > b.name) {
        return 1;
      }
      if (a.name < b.name) {
        return -1;
      }
      return 0;
    });
    elements.push(...items);
    return (
      <table
        className={
          classNames(
            styles.table,
            {
              [styles.disabled]: disabled || (directory.pending && !directory.loaded),
            },
          )
        }
      >
        <tbody>
          {
            elements.map((element, index) => (
              <tr
                /* eslint-disable-next-line */
                key={index}
                className={
                  classNames(
                    styles.item,
                    {[styles.folder]: element.isFolder},
                  )
                }
                onClick={this.onSelectItem(element)}
              >
                <td className={styles.icon}>
                  <Icon
                    type={element.isFolder ? 'folder' : 'file'}
                    color="#666"
                  />
                </td>
                <td className={styles.name}>
                  {element.name}
                </td>
                <td className={styles.size}>
                  {displaySize(element.size)}
                </td>
                <td
                  className={styles.actions}
                >
                  <DownloadAction
                    disabled={disabled}
                    item={element}
                  />
                  <RemoveAction
                    disabled={disabled}
                    item={element}
                    callback={this.blockingOperation(this.onRemove)}
                  />
                </td>
              </tr>
            ))
          }
        </tbody>
      </table>
    );
  };

  render() {
    const {path, taskManager} = this.props;
    return (
      <Upload
        className={styles.uploadContainer}
        path={path || ''}
      >
        <div
          className={styles.container}
        >
          {this.renderDirectory()}
          <TaskQueue
            activeTasksCount={(taskManager.items || []).length}
          />
        </div>
      </Upload>
    );
  }
}

export default Browser;
