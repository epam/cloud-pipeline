/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import styles from './define-results.css';
import {
  getObjectProperties,
  ObjectProperty,
  ObjectPropertyName
} from '../object-properties';
import {
  getObjectPropertyFunction,
  PropertyFunctionHints,
  PropertyFunctionNames
} from '../property-functions';
import {Button, Icon, Select} from 'antd';

function Statistics (props) {
  const {
    property,
    stats = [],
    onChange = () => {}
  } = props;
  const functions = getObjectPropertyFunction(property);
  if (functions.length === 0) {
    return;
  }
  return (
    <Select
      mode="multiple"
      style={{flex: 1}}
      value={(stats || []).slice()}
      onChange={onChange}
    >
      {
        functions.map((aFunction) => (
          <Select.Option
            key={aFunction}
            value={aFunction}
            title={PropertyFunctionHints[aFunction]}
          >
            {PropertyFunctionNames[aFunction]}
          </Select.Option>
        ))
      }
    </Select>
  );
}

Statistics.propTypes = {
  property: PropTypes.string,
  stats: PropTypes.arrayOf(PropTypes.string),
  onChange: PropTypes.func
};

function OutputProperty (props) {
  const {
    outputProperty = {},
    pipeline,
    onChange: onChangeOutputProperty = () => {},
    onRemove: onRemoveOutputProperty = () => {},
    objects = []
  } = props || {};
  const {
    object,
    properties = []
  } = outputProperty;
  if (!pipeline) {
    return null;
  }
  const isSpot = pipeline.getObjectIsSpot(object);
  const isSpotWithParent = isSpot && pipeline.getObjectIsSpotWithParent(object);
  const hasSpots = pipeline.getObjectHasSpots(object);
  const objectProperties = getObjectProperties({
    spot: isSpot,
    spotWithParent: isSpotWithParent,
    hasChild: hasSpots
  });
  const changeObject = newObject => {
    if (newObject !== object) {
      const newObjectIsSpot = pipeline.getObjectIsSpot(newObject);
      const newObjectHasSpots = pipeline.getObjectHasSpots(newObject);
      const newProperties = newObjectIsSpot === isSpot && newObjectHasSpots === hasSpots
        ? properties
        : [{name: ObjectProperty.all}];
      onChangeOutputProperty({
        ...outputProperty,
        object: newObject,
        properties: newProperties
      });
    }
  };
  const blurEvent = e => {
    if (e && e.target && typeof e.target.blur === 'function') {
      e.target.blur();
    }
  };
  const removeObject = (e) => {
    blurEvent(e);
    onRemoveOutputProperty(outputProperty);
  };
  const addProperty = (e) => {
    blurEvent(e);
    const newProperties = properties.slice();
    newProperties.push({});
    onChangeOutputProperty({
      ...outputProperty,
      properties: newProperties
    });
  };
  const changeProperty = (index, newProperty) => {
    const newProperties = properties.slice();
    newProperties.splice(index, 1, newProperty);
    onChangeOutputProperty({
      ...outputProperty,
      properties: newProperties
    });
  };
  const changePropertyName = (index) => (newPropertyName) => {
    const prop = properties[index] || {};
    if (prop && prop.name === newPropertyName) {
      return;
    }
    changeProperty(index, {...prop, name: newPropertyName});
  };
  const changePropertyFunction = (index) => (newStats) => {
    const prop = properties[index] || {};
    changeProperty(index, {...prop, stats: newStats});
  };
  const removeProperty = (index) => (e) => {
    blurEvent(e);
    const newProperties = properties.slice();
    newProperties.splice(index, 1);
    onChangeOutputProperty({
      ...outputProperty,
      properties: newProperties
    });
  };
  const canConfigureStatistics = (property) => {
    return property.name !== ObjectProperty.all &&
      getObjectPropertyFunction(property.name).length > 0;
  };
  return (
    <div>
      <div
        className={styles.row}
      >
        <span className={styles.title}>Object:</span>
        <Select
          className={styles.value}
          value={object}
          onChange={changeObject}
        >
          {
            objects.map(anObject => (
              <Select.Option
                key={anObject}
                value={anObject}
              >
                {anObject}
              </Select.Option>
            ))
          }
        </Select>
        <Button
          type="danger"
          size="small"
          style={{marginLeft: 5}}
          onClick={removeObject}
        >
          <Icon type="delete" />
        </Button>
      </div>
      {
        properties.map((property, index) => ([
          (
            <div
              key={`${outputProperty.id}_property_${index}`}
              className={styles.row}
            >
              <span className={styles.title}>Property:</span>
              <Select
                className={styles.value}
                value={property.name}
                onChange={changePropertyName(index)}
              >
                <Select.Option
                  key={ObjectProperty.all}
                  value={ObjectProperty.all}
                >
                  {ObjectPropertyName[ObjectProperty.all]}
                </Select.Option>
                <Select.Option
                  disabled
                  key="divider"
                  value="divider"
                >
                  <div
                    className="cp-divider horizontal"
                  >
                    {'\u00A0'}
                  </div>
                </Select.Option>
                {
                  objectProperties.map(aPropertyName => (
                    <Select.Option
                      key={aPropertyName}
                      value={aPropertyName}
                    >
                      {ObjectPropertyName[aPropertyName]}
                    </Select.Option>
                  ))
                }
              </Select>
              <Button
                type="danger"
                size="small"
                style={{marginLeft: 5}}
                onClick={removeProperty(index)}
              >
                <Icon type="close" />
              </Button>
            </div>
          ),
          canConfigureStatistics(property)
            ? (
              <div
                key={`${outputProperty.id}_property_${index}_functions`}
                className={styles.row}
                style={{
                  paddingLeft: 54,
                  paddingRight: 33
                }}
              >
                <span
                  className={styles.title}
                >
                  Statistics:
                </span>
                <Statistics
                  property={property.name}
                  stats={(property.stats || []).slice()}
                  onChange={changePropertyFunction(index)}
                />
              </div>
            )
            : false
        ]))
          .reduce((r, c) => ([...r, ...c]), [])
          .filter(Boolean)
      }
      {
        object && (
          <div
            className={styles.row}
            style={{
              paddingRight: 33,
              justifyContent: 'flex-end'
            }}
          >
            <Button
              size="small"
              onClick={addProperty}
            >
              <Icon type="plus" />
              <span>Add {object} property</span>
            </Button>
          </div>
        )
      }
    </div>
  );
}

OutputProperty.propTypes = {
  outputProperty: PropTypes.object,
  pipeline: PropTypes.object,
  objects: PropTypes.arrayOf(PropTypes.string),
  onChange: PropTypes.func,
  onRemove: PropTypes.func
};

export default observer(OutputProperty);
