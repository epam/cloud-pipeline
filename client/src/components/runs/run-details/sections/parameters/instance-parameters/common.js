import React from 'react';
import PropTypes from 'prop-types';

export function generateRunInstanceParameterValueComponent (
  parameter,
  options = {}
) {
  const {
    render = (o) => o,
    check = (r) => true
  } = options;
  function RunInstanceParameter (props) {
    const {
      className,
      style,
      run
    } = props;
    if (!run) {
      return null;
    }
    const {instance = {}} = run;
    if (parameter in instance) {
      return (
        <span className={className} style={style}>
          {render(instance[parameter], run)}
        </span>
      );
    }
  }

  RunInstanceParameter.propTypes = {
    className: PropTypes.string,
    style: PropTypes.object,
    run: PropTypes.object
  };

  RunInstanceParameter.available = function available (run) {
    return check(run) &&
      run &&
      run.instance &&
      parameter in run.instance;
  };

  return RunInstanceParameter;
}

export function generateRunValueComponent (
  parameter,
  options = {}
) {
  const {
    render = (o) => o,
    check = (r) => true
  } = options;
  function RunValue (props) {
    const {
      className,
      style,
      run
    } = props;
    if (!run) {
      return null;
    }
    if (parameter in run) {
      return (
        <span className={className} style={style}>
          {render(run[parameter], run)}
        </span>
      );
    }
  }

  RunValue.propTypes = {
    className: PropTypes.string,
    style: PropTypes.object,
    run: PropTypes.object
  };

  RunValue.available = function available (run) {
    return check(run) &&
      run &&
      parameter in run;
  };

  return RunValue;
}

export function generateRunInstanceParameterComponent (options = {}) {
  const {
    render = (o) => o,
    check = (r) => true
  } = options;
  function RunInstanceParameter (props) {
    const {
      className,
      style,
      run
    } = props;
    if (!run) {
      return null;
    }
    const {instance = {}} = run;
    if (instance) {
      return (
        <span className={className} style={style}>
          {render(run)}
        </span>
      );
    }
  }

  RunInstanceParameter.propTypes = {
    className: PropTypes.string,
    style: PropTypes.object,
    run: PropTypes.object
  };

  RunInstanceParameter.available = function available (run) {
    return check(run) && run && run.instance;
  };

  return RunInstanceParameter;
}
