import React from 'react';
import PropTypes from 'prop-types';
import {isObservableArray} from 'mobx';
import {Popover, Tag} from 'antd';
import {base64toString} from '../../../../../../utils/base64';
import {plural} from '../../../../../special/metadata/items-table/utilities';

class ParameterValueRepresentation extends React.PureComponent {
  state = {
    value: undefined,
    isBase64: false
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps) {
    if (prevProps.value !== this.props.value) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {value} = this.props;
    if (value !== undefined && value !== null) {
      let isBase64 = false;
      let val = value;
      try {
        val = base64toString(value);
        isBase64 = true;
      } catch {
        // noop
      }
      try {
        val = JSON.parse(val);
      } catch {
        // noop
      }
      this.setState({
        value: val,
        isBase64
      });
    } else {
      this.setState({
        value: undefined,
        isBase64: false
      });
    }
  };

  renderComponent () {
    const {
      className,
      style,
      missingLabel = false,
      showBase64Tag = false
    } = this.props;
    const {value, isBase64} = this.state;
    if (value === undefined || value === null) {
      return <span className={className} style={style}>{missingLabel}</span>;
    }
    const isBase64Tag = showBase64Tag && isBase64
      ? <Tag style={{marginLeft: 5}}>BASE64</Tag>
      : undefined;
    if (typeof value === 'object') {
      if (Array.isArray(value) || isObservableArray(value)) {
        const count = value.length;
        return (
          <span
            className={className}
            style={style}
          >
            {plural(count, 'record')}{isBase64Tag}
          </span>
        );
      }
      return (
        <span
          className={className}
          style={style}
        >
          <i>Object</i>{isBase64Tag}
        </span>
      );
    }
    return (
      <span
        className={className}
        style={style}
      >
        {String(value)}{isBase64Tag}
      </span>
    );
  }

  render () {
    const {value} = this.state;
    const comp = this.renderComponent();
    if (value === undefined || value === null || typeof value !== 'object') {
      return comp;
    }
    const repr = JSON.stringify(value, undefined, ' ');
    return (
      <Popover
        content={(
          <div
            style={{
              minWidth: 300,
              minHeight: 200,
              maxHeight: '50vh',
              maxWidth: '75vw',
              overflow: 'auto'
            }}
          >
            <code style={{whiteSpace: 'pre'}}>{repr}</code>
          </div>
        )}
      >
        {comp}
      </Popover>
    );
  }
}

ParameterValueRepresentation.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.any,
  missingLabel: PropTypes.node,
  showBase64Tag: PropTypes.bool
};

export default ParameterValueRepresentation;
