import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {
  Modal,
} from 'antd';
import {parse, build} from '../../utilities/query-parameters';
import styles from './download.css';

@inject('taskManager')
@inject(({taskManager}, params) => {
  const {taskId} = params.match.params;
  return {
    taskId,
    path: parse(params.history.location.search).path,
    status: taskManager.getTaskById(taskId),
  };
})
@observer
class Download extends React.Component {
  static propTypes = {
    path: PropTypes.string,
    taskId: PropTypes.string,
  };

  static defaultProps = {
    path: null,
    taskId: null,
  };

  onClose = () => {
    const {history, path} = this.props;
    history.push(`/${build({path})}`);
  };

  render() {
    const {status, taskId} = this.props;
    if (!status || !status.loaded) {
      return null;
    }
    return (
      <Modal
        className={styles.modal}
        centered
        destroyOnClose
        visible
        footer={false}
        maskStyle={{backgroundColor: 'rgba(0, 0, 0, 0.25)'}}
        width="50vw"
        onCancel={this.onClose}
      >
        Download {taskId}: {status.value.status}
      </Modal>
    );
  }
}

export default Download;
