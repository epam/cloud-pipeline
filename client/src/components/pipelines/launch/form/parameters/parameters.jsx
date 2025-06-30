import React, {Component} from 'react';
import PropTypes from 'prop-types';
import LaunchFormParameter from './parameter';
import Divider from './divider';
import styles from './parameters.css';
import {inject, observer} from 'mobx-react';
import LoadingView from '../../../../special/LoadingView';
import {
  hasResolvedValues,
  isCapabilityParameter,
  isGPUScalingParameter, isReservationRequestParameter,
  isReservedParameter,
  mapSystemParameter, toggleResolvedValues
} from '../utilities/parameter-utilities';
import RootEntityTypeParameter from './parameter/root-entity-type-input';
import {Checkbox} from 'antd';
import classNames from 'classnames';

function getParameterSection (parameter) {
  const {config = {}} = parameter;
  const {section} = config;
  return section && !/^other$/i.test(section) ? section : 'Other';
}

function getSections (parameters) {
  const sections = [];
  const set = new Set();
  for (const param of parameters) {
    const section = getParameterSection(param);
    if (!set.has(section.toLowerCase())) {
      sections.push(section);
      set.add(section.toLowerCase());
    }
  }
  return sections.sort((a, b) => {
    const aScore = /^other$/i.test(a) ? 1 : -1;
    const bScore = /^other$/i.test(b) ? 1 : -1;
    return aScore - bScore;
  });
}

function filterParameterBySection (parameter, section) {
  const parameterSection = getParameterSection(parameter);
  return parameterSection.toLowerCase() === section.toLowerCase();
}

function parameterIsVisible (parameter) {
  const {config = {}} = parameter;
  const {visible = true} = config;
  return visible;
}

@inject(
  'preferences',
  'runDefaultParameters'
)
@observer
class Parameters extends Component {
  state = {
    useResolvedValues: false,
    highlightedSection: undefined
  };

  sectionDivs = {};

  componentDidMount () {
    this.checkResolvedValues();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.parameters !== this.props.parameters) {
      this.checkResolvedValues();
    }
  }

  componentWillUnmount () {
    clearTimeout(this.highlightSectionTimeout);
  }

  checkResolvedValues = () => {
    const {useResolvedValues} = this.state;
    const {parameters = []} = this.props;
    if (useResolvedValues && !hasResolvedValues(parameters)) {
      this.onChangeUseResolvedValues(false);
    }
  };

  onChangeUseResolvedValues = (useResolvedValues) => {
    const {
      parameters = [],
      onChange
    } = this.props;
    this.setState({useResolvedValues}, () => {
      if (onChange) {
        const result = toggleResolvedValues(parameters, useResolvedValues);
        onChange(result);
      }
    });
  }

  initSectionDiv = (div, section) => {
    this.sectionDivs[section] = div;
  }

  scrollToSection = (section) => {
    const div = this.sectionDivs[section];
    if (div) {
      div.scrollIntoView({behavior: 'smooth', block: 'center'});
    }
    this.highlightSection(section);
  };

  highlightSection = (section) => {
    clearTimeout(this.highlightSectionTimeout);
    this.setState({
      highlightedSection: section
    });
    this.highlightSectionTimeout = setTimeout(() => this.setState({
      highlightedSection: undefined
    }), 1000);
  }

  render () {
    const {
      className,
      style,
      disabled,
      parameters = [],
      onChange,
      preferences,
      runDefaultParameters,
      system,
      rawEdit = false,
      editConfiguration = false,
      currentProjectId,
      currentProjectMetadata,
      currentMetadataEntity,
      rootEntityId,
      onChangeRootEntityId,
      showRootEntityId,
      metadataAutoComplete,
      navigationClassName,
      navigationStyle,
      navigationRef,
      detached,
      pipeline,
      parameterRowClassName
    } = this.props;
    if (!preferences.loaded || !runDefaultParameters.loaded) {
      return (<LoadingView />);
    }
    const filtered = parameters
      .filter((parameter) => rawEdit || parameterIsVisible(parameter))
      .filter((parameter) => system
        ? parameter.system &&
        !isReservedParameter(parameter.name) &&
        !isCapabilityParameter(parameter.name) &&
        !isReservationRequestParameter(parameter.name) &&
        !isGPUScalingParameter(parameter.name)
        : !parameter.system)
      .map((parameter) => system ? mapSystemParameter(parameter) : parameter);
    const sections = getSections(filtered);
    const grouped = sections.map((section) => ({
      section,
      parameters: filtered
        .filter((p) => filterParameterBySection(p, section))
    })).filter((g) => g.parameters.length > 0);
    const onParameterChanged = (parameter) => {
      const modified = parameters.slice();
      const idx = modified.findIndex((p) => p.key === parameter.key);
      if (idx >= 0) {
        modified.splice(idx, 1, parameter);
      } else {
        modified.push(parameter);
      }
      if (onChange) {
        onChange(modified);
      }
    };
    const onParameterRemoved = (parameter) => {
      const modified = parameters.slice();
      const idx = modified.findIndex((p) => p.key === parameter.key);
      if (idx >= 0) {
        modified.splice(idx, 1);
        if (onChange) {
          onChange(modified);
        }
      }
    };
    const {useResolvedValues} = this.state;
    const longestSection = grouped
      .reduce((acc, cur) => acc.length < cur.section.length ? cur.section : acc, '');
    const showSections = !system && grouped.length > 1;
    return (
      <div
        className={classNames(styles.parametersContainer, className)}
        style={style}
      >
        {
          showSections && (
            <div className={styles.parametersNavigation} ref={navigationRef}>
              <div className={navigationClassName} style={navigationStyle}>
                {
                  grouped.map((group) => (
                    <div key={group.section}>
                      <a onClick={() => this.scrollToSection(group.section)}>
                        {group.section}
                      </a>
                    </div>
                  ))
                }
              </div>
              <div style={{opacity: 0, userSelect: 'none', pointerEvents: 'none'}}>
                <span>
                  {longestSection}
                </span>
              </div>
            </div>
          )
        }
        <div className={styles.parametersContent}>
          {
            system && (
              <div
                key={`system-parameters-divider-section`}
                className={styles.parametersGroupContainer}
              >
                <Divider>System parameters</Divider>
              </div>
            )
          }
          {
            hasResolvedValues(filtered) && !system && (
              <div key={`use-resolved-values-section`} className={styles.parametersGroupContainer}>
                <div className={styles.parameterRow}>
                  <Checkbox
                    disabled={disabled}
                    checked={useResolvedValues}
                    onChange={(event) => this.onChangeUseResolvedValues(event.target.checked)}
                  >
                    Use resolved values
                  </Checkbox>
                </div>
              </div>
            )
          }
          {
            showRootEntityId && (
              <div key={`root-entity-type-section`} className={styles.parametersGroupContainer}>
                <RootEntityTypeParameter
                  key="root-entity-type-parameter"
                  className={styles.parameterRow}
                  disabled={disabled}
                  currentProjectId={currentProjectId}
                  currentMetadataEntity={currentMetadataEntity}
                  rootEntityId={rootEntityId}
                  onChange={onChangeRootEntityId}
                />
              </div>
            )
          }
          {
            grouped.map(({section, parameters: sectionParameters}) => {
              return (
                <div
                  key={`section-${section}`}
                  className={styles.parametersGroupContainer}
                  ref={(div) => this.initSectionDiv(div, section)}
                >
                  {
                    (grouped.length > 1 || !/^other$/i.test(section)) && (
                      <Divider
                        highlighted={this.state.highlightedSection === section}
                      >
                        {section}
                      </Divider>
                    )
                  }
                  {sectionParameters.map((p) => (
                    <LaunchFormParameter
                      key={p.key}
                      className={classNames(styles.parameterRow, parameterRowClassName)}
                      disabled={disabled}
                      parameter={p}
                      onChange={onParameterChanged}
                      onRemoveParameter={onParameterRemoved}
                      editConfiguration={editConfiguration}
                      rawEdit={rawEdit}
                      currentProjectId={currentProjectId}
                      currentProjectMetadata={currentProjectMetadata}
                      currentMetadataEntity={currentMetadataEntity}
                      rootEntityId={rootEntityId}
                      metadataAutoComplete={metadataAutoComplete}
                      detached={detached}
                      pipeline={pipeline}
                    />
                  ))}
                </div>
              );
            })
          }
        </div>
      </div>
    );
  }
}

Parameters.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  parameters: PropTypes.array,
  onChange: PropTypes.func,
  system: PropTypes.bool,
  rawEdit: PropTypes.bool,
  editConfiguration: PropTypes.bool,
  currentProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  currentProjectMetadata: PropTypes.object,
  currentMetadataEntity: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  rootEntityId: PropTypes.string,
  onChangeRootEntityId: PropTypes.func,
  showRootEntityId: PropTypes.bool,
  metadataAutoComplete: PropTypes.bool,
  navigationClassName: PropTypes.string,
  navigationStyle: PropTypes.object,
  navigationRef: PropTypes.func,
  detached: PropTypes.bool,
  pipeline: PropTypes.bool,
  parameterRowClassName: PropTypes.string
};

export default Parameters;
