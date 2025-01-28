import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Checkbox, Icon, Popover} from 'antd';
import styles from './tasks-table.css';
import {NextflowTaskStatus, nextflowTaskStatuses} from '../utilities';

function prevent (event) {
  event.stopPropagation();
  event.preventDefault();
}

function statusesEqual (a, b) {
  const aa = [...new Set(a || [])].sort();
  const bb = [...new Set(b || [])].sort();
  if (aa.length !== bb.length) {
    return false;
  }
  for (let i = 0; i < aa.length; i += 1) {
    if (aa[i] !== bb[i]) {
      return false;
    }
  }
  return true;
}

class TasksTableStatuses extends React.Component {
  state = {
    visible: false,
    value: []
  };
  componentDidMount () {
    this.updateFromProps();
  }
  componentDidUpdate (prevProps) {
    if (prevProps.filter !== this.props.filter) {
      this.updateFromProps();
    }
  }
  updateFromProps = () => {
    const {filter = {}} = this.props;
    const {statuses = []} = filter;
    this.setState({
      value: statuses
    });
  };
  reportValue = () => {
    const {filter = {}, onFilterChange} = this.props;
    const {statuses = [], ...rest} = filter;
    const {value = []} = this.state;
    if (!statusesEqual(statuses, value)) {
      onFilterChange(value.length ? {...rest, statuses: value} : rest);
    }
  };
  onToggleStatus = (status, visible, event) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    const {value: values = []} = this.state;
    const newValues = values.slice().filter((v) => v !== status);
    if (visible) {
      newValues.push(status);
    }
    this.setState({value: newValues}, () => this.reportValue());
  };
  resetValues = () => {
    this.setState({value: []}, () => this.reportValue());
  };
  onTableFilterVisibilityChanged = (visible) => this.setState({visible});
  render () {
    const {
      className,
      style,
      filter = {}
    } = this.props;
    const {statuses = []} = filter;
    const filterApplied = statuses.length > 0;
    const {visible, value} = this.state;
    const filterContent = (
      <div>
        <div style={{marginRight: 5}}>Task status:</div>
        {
          nextflowTaskStatuses.map((st) => (
            <div key={st} style={{margin: '3px 0'}}>
              <Checkbox
                checked={value.includes(st)}
                onChange={(e) => this.onToggleStatus(st, e.target.checked, e)}
              >
                <NextflowTaskStatus status={st} showLabel filled />
              </Checkbox>
            </div>
          ))
        }
        <div style={{marginTop: 10, textAlign: 'right'}}>
          <a onClick={this.resetValues}>
            Reset filter
          </a>
        </div>
      </div>
    );
    return (
      <Popover
        content={filterContent}
        visible={visible}
        onVisibleChange={this.onTableFilterVisibilityChanged}
        trigger="click"
      >
        <div
          className={classNames(className, {'cp-primary': filterApplied})}
          onClick={prevent}
          style={style}
        >
          <Icon type="filter" className={styles.tasksTableColumnFilter} />
        </div>
      </Popover>
    );
  }
}

TasksTableStatuses.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  filter: PropTypes.object,
  onFilterChange: PropTypes.func
};

export default TasksTableStatuses;
