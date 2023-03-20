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
import classNames from 'classnames';
import {Alert, Checkbox, Select} from 'antd';
import {inject, observer} from 'mobx-react';
import {observable} from 'mobx';
import FileSaver from 'file-saver';
import LoadingView from '../../../LoadingView';
import {generateResourceUrl} from '../../model/analysis/output-utilities';
import {fetchContents} from '../analysis-output-table';
import {getWellRowName} from '../../../hcs-image/hcs-cell-selector/utilities';
import HcsSequenceSelector from '../../../hcs-image/hcs-sequence-selector';
import HCSInfo from '../../../hcs-image/utilities/hcs-image-info';
import HcsCellSelector, {colorToVec4} from '../../../hcs-image/hcs-cell-selector';
import CellProfilerJobZScoreGradient from './cell-profiler-job-z-score-gradient';
import displayDate from '../../../../../utils/displayDate';
import styles from './cell-profiler-job-z-score.css';

const NonReadOutParameters = [
  'TimePoint',
  'WellColumn',
  'WellRow',
  'Well',
  'Plate',
  'Plane',
  'Number of Fields'
];

const nonReadOutParametersRegExp = new RegExp(`^(${NonReadOutParameters.join('|')})$`, 'i');

function getWellName (well) {
  const {
    column,
    row
  } = well;
  const columnName = !Number.isNaN(Number(column)) && Number(column) < 10
    ? '0'.concat(column)
    : column;
  if (!Number.isNaN(Number(row))) {
    return `${getWellRowName(Number(row) - 1)}${columnName}`;
  }
  return `${row}${columnName}`;
}

function getGradient (color1, color2, value) {
  return color1.map((channel, index) => {
    if (index === 3) {
      return 1;
    }
    return channel + value * (color2[index] - channel);
  });
}

function getColorForScore (score, colorNegative, colorZero, colorPositive) {
  if (score < 0) {
    return getGradient(colorZero, colorNegative, Math.abs(score));
  }
  return getGradient(colorZero, colorPositive, score);
}

@inject('themes')
@observer
class CellProfilerJobZScore extends React.Component {
  state = {
    data: undefined,
    readOutParameter: undefined,
    zPlane: undefined,
    timePoint: undefined,
    negative: false,
    negativeWells: [],
    readOutData: undefined,
    zScores: undefined,
    pending: false,
    error: undefined
  };

  token = 0;

  @observable primaryColor = [0, 0, 0, 1];
  @observable selectedColor = [0, 0, 0, 1];
  @observable backgroundColor = [0, 0, 0, 1];
  @observable defaultColor = [0, 0, 0, 1];
  @observable negativeColor = [0, 0, 1, 1];
  @observable positiveColor = [1, 0, 0, 1];
  @observable zeroColor = [1, 1, 1, 1];

  componentDidMount () {
    this.updateFromProps();
    const {themes} = this.props;
    if (themes) {
      themes.addThemeChangedListener(this.updateColors);
    }
    this.updateColors();
  }

  componentWillUnmount () {
    const {themes} = this.props;
    if (themes) {
      themes.removeThemeChangedListener(this.updateColors);
    }
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.dataStorageId !== this.props.dataStorageId ||
      prevProps.dataPath !== this.props.dataPath ||
      prevProps.hcsFileStorageId !== this.props.hcsFileStorageId ||
      prevProps.hcsFilePath !== this.props.hcsFilePath
    ) {
      this.updateFromProps();
    }
  }

  updateColors = () => {
    const {themes} = this.props;
    let primaryColor = '#108ee9';
    let selectedColor = '#ff8818';
    let textColor = 'rgba(0, 0, 0, 0.65)';
    if (themes && themes.currentThemeConfiguration) {
      primaryColor = themes.currentThemeConfiguration['@primary-color'] || primaryColor;
      selectedColor = themes.currentThemeConfiguration['@color-warning'] || selectedColor;
      textColor = themes.currentThemeConfiguration['@application-color'] || textColor;
    }
    this.primaryColor = colorToVec4(primaryColor);
    this.selectedColor = colorToVec4(selectedColor);
    const background = colorToVec4(textColor);
    this.defaultColor = background;
    this.backgroundColor = [...background.slice(0, 3), background[3] / 2.0];
  };

  updateFromProps = () => {
    const {
      dataPath,
      dataStorageId,
      hcsFilePath,
      hcsFileStorageId
    } = this.props;
    if (dataPath && dataStorageId) {
      this.setState({
        pending: true,
        error: undefined,
        data: undefined,
        readOutParameter: undefined,
        zPlane: undefined,
        timePoint: undefined,
        readOutData: undefined,
        zScores: undefined,
        negative: false,
        negativeWells: []
      }, async () => {
        this.token += 1;
        const token = this.token;
        const state = {
          pending: false
        };
        try {
          const url = await generateResourceUrl({path: dataPath, storageId: dataStorageId});
          const {
            columns = [],
            rows = []
          } = await fetchContents(url);
          let plateWidth = 0;
          let plateHeight = 0;
          let allWells = [];
          if (hcsFilePath && hcsFileStorageId) {
            try {
              const info = await HCSInfo.fetch({path: hcsFilePath, storageId: hcsFileStorageId});
              const sequence = info.sequences[0];
              if (sequence) {
                plateWidth = sequence.plateWidth;
                plateHeight = sequence.plateHeight;
                const wells = await sequence.fetchWellsStructure();
                allWells = wells.map((well) => ({
                  id: `${well.x + 1}|${well.y + 1}`,
                  x: well.x,
                  y: well.y
                }));
              }
            } catch (_) {
              // empty
            }
          }
          const wellColumnIndex = columns.findIndex((aColumn) => /^wellcolumn$/i.test(aColumn));
          if (wellColumnIndex === -1) {
            throw new Error('"WellColumn" field is missing');
          }
          const wellRowIndex = columns.findIndex((aColumn) => /^wellrow/i.test(aColumn));
          if (wellRowIndex === -1) {
            throw new Error('"WellRow" field is missing');
          }
          const extractUniqueValues = (columnIndex) => ([
            ...new Set(rows.map((row) => row[columnIndex]))
          ]);
          const timePointIndex = columns.findIndex((aColumn) => /^timepoint/i.test(aColumn));
          const timePoints = timePointIndex >= 0 ? extractUniqueValues(timePointIndex) : [];
          const zPlaneIndex = columns.findIndex((aColumn) => /^plane/i.test(aColumn));
          const zPlanes = zPlaneIndex >= 0 ? extractUniqueValues(zPlaneIndex) : [];
          const wells = [
            ...new Set(rows.map((row) => `${row[wellColumnIndex]}|${row[wellRowIndex]}`))
          ]
            .map((identifier) => {
              const [column, row] = identifier.split('|');
              return {
                row,
                column,
                identifier
              };
            });
          if (!plateWidth || !plateHeight) {
            plateWidth = Math.max(0, ...wells
              .map((well) => Number(well.column))
              .filter((n) => !Number.isNaN(n))
            );
            plateHeight = Math.max(0, ...wells
              .map((well) => Number(well.row))
              .filter((n) => !Number.isNaN(n))
            );
          }
          if (allWells.length === 0) {
            allWells = wells.map((well) => ({
              id: well.identifier,
              x: Number(well.column) - 1,
              y: Number(well.row) - 1
            }));
          }
          allWells.forEach((aWell) => {
            aWell.processed = !!wells.find((w) => w.identifier === aWell.id);
            aWell.selectable = aWell.processed;
          });
          const readOutParameters = columns
            .filter((aColumn) => !nonReadOutParametersRegExp.test(aColumn));
          if (readOutParameters.length === 0) {
            throw new Error('Output data does not contain read-out parameters');
          }
          if (wells.length === 0) {
            throw new Error('Output data is empty');
          }
          state.readOutParameter = readOutParameters[0];
          state.timePoint = timePoints[0];
          state.zPlane = zPlanes[0];
          state.data = {
            columns,
            rows,
            wellColumnIndex,
            wellRowIndex,
            timePointIndex,
            timePoints,
            zPlaneIndex,
            zPlanes,
            readOutParameters,
            wells,
            allWells,
            plateWidth,
            plateHeight
          };
        } catch (error) {
          state.error = error.message;
        } finally {
          if (token === this.token) {
            this.setState(state, this.readData);
          }
        }
      });
    } else {
      this.setState({
        data: undefined,
        pending: false,
        error: undefined,
        readOutParameter: undefined,
        zPlane: undefined,
        timePoint: undefined,
        readOutData: undefined,
        zScores: undefined,
        negative: false,
        negativeWells: []
      });
    }
  };

  calculateZScores = () => {
    const {
      readOutData = [],
      negative,
      negativeWells = [],
      data = {},
      zPlane,
      timePoint
    } = this.state;
    const {
      wells = [],
      timePoints = [],
      zPlanes = []
    } = data;
    if (
      wells.length === 0 ||
      (negative && negativeWells.length < 2) ||
      (timePoints.length > 1 && !timePoint) ||
      (zPlanes.length > 1 && !zPlane)
    ) {
      this.setState({
        zScores: undefined
      });
      return;
    }
    const checkTimePoint = timePoints.length > 1;
    const checkZPlane = zPlanes.length > 1;
    const filteredData = readOutData
      .filter((item) => (!checkTimePoint || item.timePoint === timePoint) &&
        (!checkZPlane || item.zPlane === zPlane));
    const selectedWells = wells
      .filter((aWell) => !negative || negativeWells.includes(aWell.identifier));
    const statisticsData = filteredData
      .filter((item) => !!selectedWells.find(
        (aWell) => aWell.column === item.column && aWell.row === item.row)
      );
    const mean = statisticsData
      .reduce((total, item) => total + item.value, 0) / statisticsData.length;
    const stDev = Math.sqrt(
      statisticsData
        .map((item) => (item.value - mean) ** 2)
        .reduce((total, current) => total + current, 0) / statisticsData.length
    );
    const zScore = filteredData.map((item) => ({
      ...item,
      identifier: `${item.column}|${item.row}`,
      zScore: stDev !== 0 ? (item.value - mean) / stDev : 0
    }));
    const scores = zScore.map((item) => item.zScore);
    const minimum = Math.min(...scores);
    const maximum = Math.max(...scores);
    const getScore = (value) => {
      if (minimum * maximum <= 0) {
        if (value < 0) {
          return minimum === 0 ? 0 : -(value / minimum);
        }
        return maximum === 0 ? 0 : value / maximum;
      }
      const d = maximum - minimum;
      if (d === 0) {
        return 0;
      }
      return (value - minimum) / (value - maximum);
    };
    zScore.forEach((score) => {
      score.relativeZScore = getScore(score.zScore);
    });
    this.setState({
      zScores: zScore
    });
  };

  onChangeUseNegativeControlWell = (event) => {
    this.setState({
      negative: event.target.checked
    }, this.calculateZScores);
  };

  readData = () => {
    const {
      readOutParameter,
      data = {}
    } = this.state;
    const {
      columns = [],
      rows = [],
      wellColumnIndex,
      wellRowIndex,
      timePointIndex,
      zPlaneIndex
    } = data;
    const index = columns.indexOf(readOutParameter);
    if (index >= 0) {
      const isNumber = (value) => value !== undefined &&
        value !== '' &&
        !Number.isNaN(Number(value));
      const inputData = rows
        .map((row) => ({
          column: row[wellColumnIndex],
          row: row[wellRowIndex],
          timePoint: timePointIndex >= 0 ? row[timePointIndex] : undefined,
          zPlane: zPlaneIndex >= 0 ? row[zPlaneIndex] : undefined,
          value: row[index]
        }))
        .filter((item) => isNumber(item.value))
        .map((item) => ({
          ...item,
          value: Number(item.value)
        }));
      this.setState({
        readOutData: inputData
      }, this.calculateZScores);
    }
  };

  onChangeReadOutParameter = (value) => {
    this.setState({
      readOutParameter: value,
      readOutData: undefined,
      zScores: undefined
    }, this.readData);
  };

  onChangeNegativeControlWells = (wells) => {
    this.setState({
      negativeWells: wells.slice()
    }, this.calculateZScores);
  };

  renderNegativeControlToggle = () => {
    const {
      data,
      negative
    } = this.state;
    if (!data) {
      return null;
    }
    const {
      wells = []
    } = data;
    if (!wells || wells.length === 0) {
      return null;
    }
    return (
      <div
        className={styles.negativeToggleContainer}
      >
        <Checkbox
          checked={negative}
          onChange={this.onChangeUseNegativeControlWell}
        >
          Use negative control well
        </Checkbox>
      </div>
    );
  };

  renderNegativeControlWellsSelector = () => {
    const {
      data,
      negative,
      negativeWells
    } = this.state;
    if (!data || !negative) {
      return null;
    }
    const {
      wells = []
    } = data;
    if (!wells || wells.length === 0) {
      return null;
    }
    return (
      <div
        className={styles.wellsSelectorContainer}
      >
        <span className={styles.title}>
          Negative wells:
        </span>
        <Select
          mode="multiple"
          className={styles.input}
          onChange={this.onChangeNegativeControlWells}
          value={negativeWells}
          filterOption={(input, option) =>
            option.props.displayName.toLowerCase().indexOf((input || '').toLowerCase()) >= 0
          }
          placeholder="Select negative wells (at least 2)"
          notFoundContent="Nothing found"
          getPopupContainer={o => o.parentNode}
        >
          {
            wells.map((well) => (
              <Select.Option
                key={well.identifier}
                value={well.identifier}
                displayName={getWellName(well)}
              >
                {getWellName(well)}
              </Select.Option>
            ))
          }
        </Select>
      </div>
    );
  };

  renderReadOutSelector = () => {
    const {
      readOutParameter,
      data
    } = this.state;
    if (!data) {
      return null;
    }
    const {
      readOutParameters = []
    } = data;
    if (!readOutParameters || readOutParameters.length === 0) {
      return null;
    }
    return (
      <div
        className={styles.readOutSelectorContainer}
      >
        <span className={styles.title}>
          Readout value:
        </span>
        <Select
          allowClear
          showSearch
          className={styles.input}
          value={readOutParameter}
          onChange={this.onChangeReadOutParameter}
          notFoundContent={(<span>Nothing found</span>)}
          filterOption={(input, option) => (option.props.value || '')
            .toLowerCase()
            .indexOf((input || '')
              .toLowerCase()) >= 0}
        >
          {
            readOutParameters.map((parameter) => (
              <Select.Option key={parameter} value={parameter}>
                {parameter}
              </Select.Option>
            ))
          }
        </Select>
      </div>
    );
  };

  renderValueSelector = (
    value,
    values = [],
    onChangeValue,
    title
  ) => {
    if (values.length < 2) {
      return null;
    }
    const sequence = {
      id: '0',
      sequence: '0',
      timeSeries: values.map((v) => ({id: v, name: v}))
    };
    const selection = [{sequence: '0', timePoint: value}];
    const onChange = (newSelection) => {
      const newValue = newSelection && newSelection.length > 0
        ? newSelection[0].timePoint
        : undefined;
      if (typeof onChangeValue === 'function') {
        onChangeValue(newValue);
      }
    };
    return (
      <HcsSequenceSelector
        onChange={onChange}
        multiple={false}
        showCollapseForSingleSequence={false}
        sequences={[sequence]}
        selection={selection}
        title={title}
      />
    );
  };

  renderTimePointsSelector = () => {
    const {
      data,
      timePoint
    } = this.state;
    if (!data) {
      return null;
    }
    const {
      timePoints = []
    } = data;
    const onChange = (newTimePoint) => this.setState({
      timePoint: newTimePoint
    }, this.calculateZScores);
    return this.renderValueSelector(
      timePoint,
      timePoints,
      onChange,
      'Time point'
    );
  };

  renderZPlaneSelector = () => {
    const {
      data,
      zPlane
    } = this.state;
    if (!data) {
      return null;
    }
    const {
      zPlanes = []
    } = data;
    const onChange = (newZPlane) => this.setState({
      zPlane: newZPlane
    }, this.calculateZScores);
    return this.renderValueSelector(
      zPlane,
      zPlanes,
      onChange,
      'Z plane'
    );
  };

  renderExportButton = () => {
    const {
      data,
      zScores,
      readOutParameter
    } = this.state;
    const {
      hcsFilePath,
      analysisDate,
      analysisName
    } = this.props;
    if (!data || !zScores || !hcsFilePath) {
      return null;
    }
    const input = hcsFilePath.split('/').pop().split('.').slice(0, -1).join('.');
    const hcsFileName = input
      .split(/[\\/]/)
      .pop()
      .split('.')
      .slice(0, -1)
      .join('.')
      .replace(/\s/g, '_');
    const dateTime = analysisDate
      ? displayDate(analysisDate, 'YYYYMMDD_HHmmss')
      : undefined;
    let fileName = [
      hcsFileName,
      analysisName ? analysisName.replace(/\s/g, '_') : undefined,
      dateTime
    ].filter(Boolean).join('-');
    const {
      timePointIndex,
      zPlaneIndex
    } = data;
    const header = [
      'WellRow',
      'WellColumn',
      timePointIndex >= 0 ? 'Timepoint' : false,
      zPlaneIndex >= 0 ? 'Plane' : false,
      readOutParameter,
      'Z-Score'
    ].filter(Boolean);
    const exportData = [header].concat(
      zScores.map((item) => ([
        item.row,
        item.column,
        timePointIndex >= 0 ? item.timePoint : false,
        zPlaneIndex >= 0 ? item.zPlane : false,
        item.value,
        item.zScore
      ].filter(Boolean)))).map((line) => line.join(',')).join('\n');
    const handleDownload = () => {
      FileSaver.saveAs(new Blob([exportData]), `${fileName}-z-scores.csv`);
    };
    return (
      <div style={{marginTop: 10}}>
        <a onClick={handleDownload}>
          Download z-scores table
        </a>
      </div>
    );
  };

  renderPlate = () => {
    const {
      data,
      negative,
      negativeWells,
      zScores
    } = this.state;
    if (!data) {
      return null;
    }
    const {
      allWells,
      plateWidth,
      plateHeight
    } = data;
    const getCellZScore = (cell) => {
      if (!zScores) {
        return undefined;
      }
      return zScores.find((s) => s.identifier === cell.id);
    };
    const getCellInfo = (cell) => {
      const score = getCellZScore(cell);
      if (!score) {
        return undefined;
      }
      return {
        'Z-Score': score.zScore
      };
    };
    const getCellScoreColor = (cell) => {
      const score = getCellZScore(cell);
      if (!score) {
        return undefined;
      }
      return getColorForScore(
        score.relativeZScore,
        this.negativeColor,
        this.zeroColor,
        this.positiveColor
      );
    };
    const cells = allWells.map((well) => ({
      ...well,
      x: well.x,
      y: well.y,
      selectable: negative && well.selectable,
      info: getCellInfo(well)
    }));
    const selected = cells.filter((cell) => negative && negativeWells.includes((cell.id)));
    const onSelectionChanged = (newSelection = []) => {
      if (negative) {
        this.onChangeNegativeControlWells(newSelection.map((cell) => cell.id));
      }
    };
    const getColorConfigurationForCell = (cell, options) => {
      const {
        selected
      } = options;
      if (cell.processed) {
        const color = getCellScoreColor(cell) || this.primaryColor;
        return {
          stroke: this.defaultColor,
          fill: color,
          thickStroke: selected ? this.selectedColor : undefined
        };
      }
      return {
        stroke: this.defaultColor,
        fill: this.backgroundColor
      };
    };
    return (
      <HcsCellSelector
        width={plateWidth}
        height={plateHeight}
        cells={cells}
        selected={selected}
        onChange={onSelectionChanged}
        selectionEnabled={negative}
        showRulers
        showElementHint
        getColorConfigurationForCell={getColorConfigurationForCell}
        colorsConfigurationForCell={(cell) => {
          if (cell.processed) {
            return {
              stroke: this.primaryColor,
              fill: getCellScoreColor(cell) || this.primaryColor
            };
          }
          return {
            stroke: this.defaultColor,
            fill: this.backgroundColor
          };
        }}
      />
    );
  };

  onChangeGradientColors = (colors) => {
    const {
      positive = [1, 0, 0, 1],
      negative = [0, 0, 1, 1],
      zero = [1, 1, 1, 1]
    } = colors || {};
    this.negativeColor = negative;
    this.positiveColor = positive;
    this.zeroColor = zero;
  };

  renderContent = () => {
    const {
      pending,
      error
    } = this.state;
    if (error) {
      return (
        <Alert message={error} type="error" />
      );
    }
    if (pending) {
      return (<LoadingView />);
    }
    return [
      <div
        key="plate"
        className={styles.plateContainer}
      >
        <div
          className={styles.plate}
        >
          {this.renderPlate()}
        </div>
        <CellProfilerJobZScoreGradient
          className={styles.gradient}
          colors={{
            negative: this.negativeColor,
            positive: this.positiveColor,
            zero: this.zeroColor
          }}
          onChange={this.onChangeGradientColors}
        />
      </div>,
      <div
        key="configuration"
        className={styles.configuration}
      >
        {this.renderReadOutSelector()}
        {this.renderNegativeControlToggle()}
        {this.renderNegativeControlWellsSelector()}
        {this.renderTimePointsSelector()}
        {this.renderZPlaneSelector()}
        {this.renderExportButton()}
      </div>
    ];
  };

  render () {
    const {
      className,
      style,
      dataPath,
      dataStorageId
    } = this.props;
    if (!dataPath || !dataStorageId) {
      return null;
    }
    return (
      <div
        className={
          classNames(
            className,
            styles.zScoreContainer
          )
        }
        style={style}
      >
        {this.renderContent()}
      </div>
    );
  }
}

CellProfilerJobZScore.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  hcsFileStorageId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  hcsFilePath: PropTypes.string,
  dataStorageId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  dataPath: PropTypes.string,
  analysisDate: PropTypes.string,
  analysisName: PropTypes.string
};

export default CellProfilerJobZScore;
