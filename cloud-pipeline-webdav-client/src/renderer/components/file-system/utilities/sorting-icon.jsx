import React from 'react';
import PropTypes from 'prop-types';
import { CaretDownOutlined, CaretUpOutlined } from '@ant-design/icons';
import { PropertySorters, SortingProperty } from './sorting';

function SortingIcon({ sorting, property }) {
  const index = PropertySorters[property].indexOf(sorting);
  if (index === -1) {
    return (
      <span>{'\u00A0'}</span>
    );
  }
  if (index === 0) {
    return (
      <CaretUpOutlined />
    );
  }
  return (
    <CaretDownOutlined />
  );
}

SortingIcon.propTypes = {
  sorting: PropTypes.string,
  property: PropTypes.string.isRequired,
};

SortingIcon.defaultProps = {
  sorting: undefined,
};

export default SortingIcon;
