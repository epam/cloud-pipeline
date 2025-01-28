import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Icon, Input, Popover} from 'antd';
import styles from './tasks-table.css';

function prevent (event) {
  event.stopPropagation();
  event.preventDefault();
}

class TasksTableTagFilter extends React.Component {
  state = {
    visible: false,
    value: ''
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
    const {taskTag} = filter;
    this.setState({
      value: taskTag || ''
    });
  };
  reportValue = () => {
    const {filter = {}, onFilterChange} = this.props;
    const {taskTag = '', ...rest} = filter;
    const {value = ''} = this.state;
    if (taskTag !== value) {
      onFilterChange(value && value.trim().length ? {...rest, taskTag: value} : rest);
      this.setState({visible: false});
    }
  };
  resetValue = () => {
    this.setState({value: '', visible: false}, () => this.reportValue());
  };
  onValueChange = (e) => this.setState({value: e.target.value});
  onTableFilterVisibilityChanged = (visible) => this.setState({visible}, () => {
    if (!visible) {
      this.reportValue();
    }
  });
  render () {
    const {
      className,
      style,
      filter = {}
    } = this.props;
    const {taskTag} = filter;
    const filterApplied = taskTag && taskTag.length > 0;
    const {visible, value} = this.state;
    const filterContent = (
      <div>
        <div style={{display: 'flex', alignItems: 'center'}}>
          <div style={{marginRight: 5}}>Task name:</div>
          <Input
            style={{flex: 1}}
            size="small"
            placeholder="Filter task"
            value={value}
            onChange={this.onValueChange}
            onPressEnter={this.reportValue}
          />
        </div>
        <div style={{marginTop: 10, textAlign: 'right'}}>
          <a onClick={this.resetValue}>
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
          <Icon type="filter" className={styles.tasksTableColumnFilter}/>
        </div>
      </Popover>
    );
  }
}

TasksTableTagFilter.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  filter: PropTypes.object,
  onFilterChange: PropTypes.func
};

export default TasksTableTagFilter;
