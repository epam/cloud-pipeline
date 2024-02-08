import React from 'react';
import PropTypes from 'prop-types';
import {Icon, Row} from 'antd';
import classNames from 'classnames';
import {COMPUTED_DAYS, DAYS, MONTHS, ORDINALS, getOrdinalSuffix} from './forms';
import {isTimeZoneEqualCurrent, CronConvert, ruleModes} from './cron-convert';
import RunScheduleDialog from './run-scheduling-dialog';

function getDayOfWeek (value) {
  const [dayOfWeek] = DAYS.filter(({key: dayValue}) => +dayValue === +value);
  if (dayOfWeek) {
    return dayOfWeek.title;
  }
  return '';
}

class RunSchedulingList extends React.Component {
  static propTypes = {
    rules: PropTypes.array,
    allowEdit: PropTypes.bool,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    availableActions: PropTypes.arrayOf(PropTypes.oneOf([
      RunScheduleDialog.Actions.pause,
      RunScheduleDialog.Actions.resume
    ]))
  };

  static Actions = RunScheduleDialog.Actions;

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
        className={
          classNames(
            'cp-text',
            'underline'
          )
        }
      >
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
        availableActions={this.props.availableActions}
        showActionType
      />
    );
    return (
      <Row type="flex">
        {trigger}
        {modal}
      </Row>
    );
  };

  getScheduleString = (schedule, timeZone) => {
    const {
      mode,
      every,
      day,
      dayOfWeek,
      daySelectorMode,
      dayNumber,
      month,
      ordinal,
      time: {hours, minutes}
    } = schedule;
    const zone = !isTimeZoneEqualCurrent(timeZone) ? timeZone : null;
    let recurrence;
    const scheduleOrdinal = ORDINALS
      .find(({cronCode}) => ordinal === cronCode);
    const scheduleDay = [...DAYS, ...Object.values(COMPUTED_DAYS)]
      .find(({key}) => key === day);
    const scheduleMonth = MONTHS.find(({key}) => key === month);
    switch (mode) {
      case ruleModes.daily:
        recurrence = `every ${every} day${+every > 1 ? 's' : ''}`;
        break;
      case ruleModes.weekly:
        recurrence = `on ${dayOfWeek.sort().map((day) => getDayOfWeek(+day)).join(', ')}`;
        break;
      case ruleModes.monthly:
        if (!scheduleOrdinal || !scheduleDay) {
          recurrence = '';
        }
        const everyString = `every ${every} month${+every > 1 ? 's' : ''}`;
        recurrence = daySelectorMode === 'numeric'
          ? `${everyString}, on ${dayNumber}${getOrdinalSuffix(dayNumber)} day.`
          : `${everyString}, on ${scheduleOrdinal.title} ${scheduleDay.title}`;
        break;
      case ruleModes.yearly:
        if (!scheduleOrdinal || !scheduleDay || !scheduleMonth) {
          recurrence = '';
        }
        recurrence = daySelectorMode === 'numeric'
          ? `every ${scheduleMonth.title}, on ${dayNumber}${getOrdinalSuffix(dayNumber)} day.`
          : `every ${scheduleMonth.title}, on ${scheduleOrdinal.title} ${scheduleDay.title}`;
        break;
    }
    const time = `${`0${hours}`.slice(-2)}:${`0${minutes}`.slice(-2)}`;
    if (!recurrence) {
      return '';
    }
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

RunSchedulingList.defaultProps = {
  availableActions: [
    RunScheduleDialog.Actions.pause,
    RunScheduleDialog.Actions.resume
  ]
};

export default RunSchedulingList;
