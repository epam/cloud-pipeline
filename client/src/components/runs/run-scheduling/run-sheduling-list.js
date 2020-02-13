import React from 'react';
import PropTypes from 'prop-types';
import {Icon, Row} from 'antd';
import daysOfWeek from './days-of-week';
import {isTimeZoneEqualCurrent, CronConvert, ruleModes} from './cron-convert';
import RunScheduleDialog from './run-scheduling-dialog';

function getDayOfWeek (value) {
  const [dayOfWeek] = daysOfWeek.filter(({value: dayValue}) => +dayValue === +value);
  if (dayOfWeek) {
    return dayOfWeek.day;
  }
  return '';
}

export default class RunSchedulingList extends React.Component {
  static propTypes = {
    rules: PropTypes.array,
    allowEdit: PropTypes.bool,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool
  };

  state = {
    rules: [],
    editScheduleDialogVisible: false
  };

  componentDidMount () {
    this.prepareProps();
  }

  componentDidUpdate (prevProps) {
    if (this.props.rules !== prevProps.rules) {
      this.prepareProps();
    }
  }

  prepareProps = (props = this.props) => {
    const convertRule = ({action, cronExpression, scheduleId, timeZone}) => {
      const schedule = CronConvert.convertToRuleScheduleObject(cronExpression);

      return {
        scheduleId,
        timeZone,
        action,
        schedule
      };
    };

    const rules = (props.rules || []).map(convertRule);

    this.setState({rules});
  };

  openRunSchedulingDialog = () => {
    this.setState({editScheduleDialogVisible: true});
  };

  closeRunSchedulingDialog = () => {
    this.setState({editScheduleDialogVisible: false});
  };

  submitSchedule = (rules) => {
    const {onSubmit} = this.props;

    onSubmit && onSubmit(rules);
    this.closeRunSchedulingDialog();
  };

  renderEditScheduleControl = () => {
    const {allowEdit, rules} = this.props;
    const {editScheduleDialogVisible} = this.state;

    if (!allowEdit) {
      return null;
    }

    const trigger = (
      <a
        key="configure scheduling trigger"
        onClick={this.openRunSchedulingDialog}
        style={{color: '#777', textDecoration: 'underline'}}>
        <Icon type="setting" />
        Configure
      </a>
    );
    const modal = (
      <RunScheduleDialog
        key="configure scheduling dialog"
        onSubmit={this.submitSchedule}
        rules={rules}
        visible={editScheduleDialogVisible}
        onClose={this.closeRunSchedulingDialog}
      />
    );
    return (
      <Row type="flex">
        {trigger}
        {modal}
      </Row>
    );
  };

  getScheduleString = ({mode, every, dayOfWeek, time: {hours, minutes}}, timeZone) => {
    const zone = !isTimeZoneEqualCurrent(timeZone) ? timeZone : null;
    const recurrence = mode === ruleModes.daily
      ? `every ${every} day${+every > 1 ? 's' : ''}`
      : `on ${dayOfWeek.sort().map((day) => getDayOfWeek(+day)).join(', ')}`;

    const time = `${`0${hours}`.slice(-2)}:${`0${minutes}`.slice(-2)}`;

    return `At ${time}, ${recurrence}${zone ? ` (${zone})` : ''}`;
  };

  renderList = () => {
    const {rules} = this.state;
    const renderRule = ({action, schedule, timeZone}, i) => {
      return (
        <Row type="flex" key={`rule_${action}_${i}`}>
          <b>{action}</b>: {this.getScheduleString(schedule, timeZone)}
        </Row>
      );
    };

    return (rules || []).map(renderRule);
  };

  render () {
    const {pending} = this.props;
    if (pending) {
      return <Icon type="loading" />;
    }

    return (
      <Row type="flex" style={{flexDirection: 'column'}}>
        {this.renderList()}
        {this.renderEditScheduleControl()}
      </Row>
    );
  }
}
