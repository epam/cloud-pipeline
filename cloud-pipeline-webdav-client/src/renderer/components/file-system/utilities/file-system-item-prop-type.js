import PropTypes from 'prop-types';

const FSItemPropType = PropTypes.shape({
  name: PropTypes.string.isRequired,
  path: PropTypes.string.isRequired,
  isDirectory: PropTypes.bool,
  isFile: PropTypes.bool,
  isSymbolicLink: PropTypes.bool,
  isBackLink: PropTypes.bool,
  isObjectStorage: PropTypes.bool,
  size: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  changed: PropTypes.number,
  displaySize: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  displayChanged: PropTypes.string,
});

export default FSItemPropType;
