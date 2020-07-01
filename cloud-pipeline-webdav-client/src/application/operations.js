import React from 'react';
import PropTypes from 'prop-types';
import Operation from './components/operation';

function Operations({className, operations}) {
  return (
    <div className={className}>
      {
        operations.map((operation) => (
          <Operation key={operation.identifier} operation={operation} />
        ))
      }
    </div>
  );
}

Operations.propTypes = {
  className: PropTypes.string,
  operations: PropTypes.array,
};

export default Operations;
