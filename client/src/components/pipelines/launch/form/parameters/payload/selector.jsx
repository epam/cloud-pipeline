import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './parameters-payload.css';
import {Button, Checkbox, Icon, Popover} from 'antd';

function payloadIsInvalid (payload) {
  const {parameters = []} = payload;
  return parameters.some((p) => !p.valid);
}

function payloadIsEnabled (payload) {
  const {enabled = true} = payload;
  return enabled;
}

function ParametersPayloadName (props) {
  const {
    className,
    style,
    payload,
    onClick,
    idx = undefined,
    onRemove
  } = props;
  const {
    id
  } = payload;
  const invalid = payloadIsInvalid(payload);
  const onRemoveClick = (e) => {
    e.stopPropagation();
    if (onRemove) {
      onRemove();
    }
  };
  return (
    <div
      className={classNames(styles.parametersPayloadName, className, {
        'cp-error': invalid
      })}
      style={style}
      onClick={onClick}
    >
      {typeof idx === 'number' && (
        <span
          className={styles.parametersPayloadNamePart}
          style={{marginRight: 5}}
        >
          #{idx + 1}
        </span>
      )}
      <span
        className={styles.parametersPayloadNamePart}>
        {id}
      </span>
      {
        invalid && (
          <Icon
            type="exclamation-circle-o"
            className={classNames('cp-error', styles.parametersPayloadInvalidIcon)}
          />
        )
      }
      {
        typeof onRemove === 'function' && (
          <Button
            size="small"
            style={{marginLeft: 'auto'}}
            onClick={onRemoveClick}
          >
            <Icon type="delete" />
          </Button>
        )
      }
    </div>
  );
}

ParametersPayloadName.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  payload: PropTypes.object,
  payloadIdx: PropTypes.number,
  onClick: PropTypes.func,
  onRemove: PropTypes.func
};

class ParametersPayloadSelector extends React.PureComponent {
  state = {
    opened: false
  };

  onOpen = () => this.setState({opened: true});
  onClose = () => this.setState({opened: false});
  onOpenChange = (opened) => this.setState({opened});

  renderPayload = (payload, idx = undefined) => {
    const {
      onChangeActive,
      onChange,
      onRemovePayload,
      payloads = []
    } = this.props;
    const {
      id
    } = payload;
    const enabled = payloadIsEnabled(payload);
    const onClick = () => {
      if (onChangeActive) {
        onChangeActive(id);
      }
    };
    const onRemoveClick = () => {
      if (onRemovePayload) {
        onRemovePayload(id);
      }
    };
    const onEnabledChange = (e) => {
      e.stopPropagation();
      const result = payloads.slice();
      const pIdx = result.findIndex((p) => p.id === id);
      if (pIdx >= 0) {
        result.splice(pIdx, 1, {
          ...payload,
          enabled: e.target.checked
        });
      }
      if (onChange) {
        onChange(result);
      }
    };
    return (
      <div className={styles.parametersPayload} key={id}>
        <Checkbox
          checked={enabled}
          onChange={onEnabledChange}
          style={{marginRight: 10}}
        />
        <ParametersPayloadName
          style={{flex: 1}}
          payload={payload}
          idx={idx}
          onClick={onClick}
          onRemove={onRemoveClick}
        />
      </div>
    );
  };

  renderSelectorContent = () => {
    const {
      payloads = [],
      linkActions = false,
      onReset
    } = this.props;
    const enabledPayloads = payloads.filter(payloadIsEnabled);
    const allEnabled = enabledPayloads.length === payloads.length && payloads.length > 0;
    const allDisabled = enabledPayloads.length === 0 && payloads.length > 0;
    const enableAllButton = (
      <Button
        size="small"
        disabled={allEnabled}
        onClick={this.enableAll}
      >
        Enable all
      </Button>
    );
    const enableAllLink = allEnabled
      ? (<span className="cp-text-not-important">Enable all</span>)
      : (<a onClick={this.enableAll}>Enable all</a>);
    const disableAllButton = (
      <Button
        size="small"
        disabled={allDisabled}
        onClick={this.disableAll}
      >
        Disable all
      </Button>
    );
    const disableAllLink = allDisabled
      ? (<span className="cp-text-not-important">Disable all</span>)
      : (<a onClick={this.disableAll}>Disable all</a>);
    const resetButton = (
      <Button
        size="small"
        disabled={payloads.length === 0}
        onClick={onReset}
        style={{marginLeft: 'auto'}}
      >
        Reset
      </Button>
    );
    const resetLink = payloads.length === 0
      ? (<span className="cp-text-not-important">Reset</span>)
      : (<a onClick={onReset}>Reset</a>);
    return (
      <div className={styles.parametersPayloadSelectorContent}>
        <div className={styles.parametersPayloads}>
          {
            payloads.map(this.renderPayload)
          }
        </div>
        {
          payloads.length > 0 && (
            <div className={styles.actions}>
              {
                linkActions ? enableAllLink : enableAllButton
              }
              {
                linkActions ? disableAllLink : disableAllButton
              }
              {
                linkActions ? resetLink : resetButton
              }
            </div>
          )
        }
      </div>
    );
  };

  enableAll = () => {
    const {
      payloads = [],
      onChange
    } = this.props;
    if (onChange) {
      onChange(payloads.map((p) => ({
        ...p,
        enabled: true
      })));
    }
  };

  disableAll = () => {
    const {
      payloads = [],
      onChange
    } = this.props;
    if (onChange) {
      onChange(payloads.map((p) => ({
        ...p,
        enabled: false
      })));
    }
  };

  render () {
    const {
      className,
      style,
      payloads = [],
      active
    } = this.props;
    const {
      opened
    } = this.state;
    const activePayload = payloads.find((p) => p.id === active);
    const enabledPayloads = payloads.filter(payloadIsEnabled);
    const hasInvalidPayloads = enabledPayloads.some(payloadIsInvalid);
    const currentlyViewing = (() => {
      if (activePayload) {
        return <span>(<b>{activePayload.id}</b> selected)</span>;
      }
      return undefined;
    })();
    const enabledPayloadsText = (() => {
      if (enabledPayloads.length === 1) {
        return '1 payload enabled';
      }
      return `${enabledPayloads.length} payloads enabled`;
    })();
    const triggerText = (() => {
      if (currentlyViewing) {
        return <span>{enabledPayloadsText}{' '}{currentlyViewing}</span>;
      }
      return enabledPayloadsText;
    })();
    return (
      <Popover
        visible={opened}
        onVisibleChange={this.onOpenChange}
        content={this.renderSelectorContent()}
        trigger="click"
        placement="bottomLeft"
      >
        <div
          className={classNames(className, styles.parametersPayloadSelectorContainer)}
          style={style}
        >
          <a
            className={classNames(
              styles.parametersPayloadTrigger,
              'cp-text'
            )}
          >
            <span style={{textDecoration: 'underline'}}>
              {triggerText}
            </span>
            {hasInvalidPayloads && (
              <Icon
                type="exclamation-circle-o"
                className="cp-warning"
                style={{marginLeft: 5, textDecoration: 'none'}}
              />
            )}
          </a>
        </div>
      </Popover>
    );
  }
}

ParametersPayloadSelector.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  active: PropTypes.string,
  onChangeActive: PropTypes.func,
  payloads: PropTypes.array,
  onChange: PropTypes.func,
  linkActions: PropTypes.bool,
  onReset: PropTypes.func,
  onRemovePayload: PropTypes.func
};

export default ParametersPayloadSelector;
