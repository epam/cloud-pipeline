import React from 'react';
import PropTypes from 'prop-types';
import {Prompt} from 'react-router-dom';
import {Modal} from 'antd';

export class RouteBlocker extends React.Component {
  static propTypes = {
    when: PropTypes.bool.isRequired,
    message: PropTypes.string.isRequired,
    shouldBlockNavigation: PropTypes.func,
    onCancel: PropTypes.func,
    navigate: PropTypes.func.isRequired
  };

  state = {
    lastLocation: null,
    confirmedNavigation: false
  }

  showModal = (location) => {
    const {message, onCancel} = this.props;
    this.setState({
      lastLocation: location
    }, () => {
      Modal.confirm({
        title: message,
        okText: 'Yes',
        okType: 'danger',
        cancelText: 'No',
        onOk: () => this.handleConfirmNavigationClick(),
        onCancel: () => onCancel && onCancel()
      });
    });
  }

  handleBlockedNavigation = (nextLocation) => {
    const {confirmedNavigation} = this.state;
    const {shouldBlockNavigation} = this.props;

    if (!confirmedNavigation && (!shouldBlockNavigation || shouldBlockNavigation(nextLocation))) {
      this.showModal(nextLocation);

      return false;
    }

    return true;
  }

  handleConfirmNavigationClick = () => {
    const {navigate} = this.props;
    const {lastLocation} = this.state;

    if (lastLocation) {
      this.setState({
        confirmedNavigation: true
      }, () => {
        navigate(lastLocation.pathname);
      });
    }
  }

  render () {
    const {when} = this.props;

    return (
      <Prompt
        when={when}
        message={this.handleBlockedNavigation} />
    );
  }
}

export default RouteBlocker;
