import React from 'react';
import PropTypes from 'prop-types';
import Operation from './operation';
import useOperations from './hooks/use-operations';

function Operations(
  {
    className,
    style,
  },
) {
  const {
    operations,
    closeOperation,
  } = useOperations();
  if (!operations.length) {
    return null;
  }
  return (
    <div
      className={className}
      style={style}
    >
      {
        operations.map((operation) => (
          <Operation
            key={`operation-${operation.id}`}
            operation={operation}
            onClose={closeOperation}
          />
        ))
      }
    </div>
  );
}

Operations.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
};

Operations.defaultProps = {
  className: undefined,
  style: undefined,
};

export default Operations;
