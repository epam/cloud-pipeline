/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Button,
  Input,
  Select,
  Icon,
  Tooltip, Modal
} from 'antd';
import {validateName} from './utilities/theme-validation';
import {
  ColorVariables,
  VariableNames,
  VariableDescriptions
} from './utilities/variable-descriptions';
import {getThemeConfiguration, parseConfiguration} from '../../../themes/themes';
import ColorVariable from './color-variable';
import {ParseConfigurationError} from '../../../themes/utilities/parse-configuration';
import getBaseThemes from './utilities/get-base-themes';
import {
  sections,
  sectionsConfiguration,
  VariableTypes
} from './utilities/variable-sections';
import ElementPreview from './element-preview';
import styles from './ui-theme-edit-form.css';
import ImageUploader from './image-uploader';

const Title = ({className, title, required}) => (
  <span
    className={
      classNames(
        styles.title,
        className,
        'cp-settings-form-item-label',
        {
          'required': required
        }
      )
    }
  >
    {title}
  </span>
);

const FormItem = (
  {
    children,
    divider = false,
    title,
    required,
    property,
    validation = {},
    flex = false,
    titleClassName,
    hidden,
    hint
  }
) => {
  if (divider) {
    return (
      <div
        className={
          classNames(
            styles.formItemDivider,
            {[styles.hidden]: hidden}
          )
        }
      >
        {'\u00A0'}
      </div>
    );
  }
  return (
    <div
      className={
        classNames(
          styles.formItemContainer,
          {
            [styles.error]: property && !!validation[property],
            [styles.hidden]: hidden
          }
        )
      }
    >
      <div
        className={
          classNames(
            styles.formItem,
            {
              [styles.flex]: flex
            }
          )
        }
      >
        <Title className={titleClassName} title={title} required={required} />
        {children}
        {
          hint && (
            <Tooltip
              title={hint}
              placement="left"
            >
              <Icon
                type="question-circle"
                style={{marginLeft: 5}}
              />
            </Tooltip>
          )
        }
      </div>
      <div
        className={
          classNames(
            styles.errorContainer,
            styles.formItem
          )
        }
      >
        <Title className={titleClassName} title={title} required={required} />
        <span
          className={
            classNames(
              styles.errorDescription,
              'cp-error'
            )
          }
        >
          {!!property && validation[property]}
        </span>
      </div>
    </div>
  );
};

const Section = (
  {
    expandable,
    children,
    identifier,
    title,
    description,
    toggleExpanded = () => {}
  }
) => (
  <section
    className={
      classNames(
        styles.section,
        'cp-divider',
        'bottom'
      )
    }
  >
    {
      title && (
        <div className={styles.sectionTitle}>
          <h2>{title}</h2>
          {
            expandable && identifier && (
              <a
                className={
                  classNames(
                    styles.sectionExpandButton,
                    'underline'
                  )
                }
                onClick={() => toggleExpanded(identifier)}
              >
                {
                  expandable[identifier] ? 'Show less' : 'Show more'
                }
              </a>
            )
          }
        </div>
      )
    }
    {
      description && (
        <div>
          {description}
        </div>
      )
    }
    {children}
  </section>
);

class UIThemeEditForm extends React.PureComponent {
  state = {
    name: undefined,
    themeProperties: {},
    mergedProperties: {},
    parsedValues: {},
    validation: {},
    propertiesValidation: {},
    expandedState: {}
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.theme !== this.props.theme ||
      prevProps.themes !== this.props.themes
    ) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {
      theme = {}
    } = this.props;
    const {
      name,
      extends: baseThemeIdentifier
    } = theme;
    this.setState({
      name,
      extends: baseThemeIdentifier,
      themeProperties: {...(theme.properties || {})},
      initialProperties: {...(theme.properties || {})},
      validation: {},
      propertiesValidation: {},
      expandedState: {}
    }, () => this.build());
  };

  build = (skipProperties = false) => new Promise((resolve) => {
    const {
      theme = {},
      themes = []
    } = this.props;
    const {
      identifier
    } = theme;
    const {
      themeProperties = {},
      extends: baseThemeIdentifier,
      mergedProperties: prevMergedProperties,
      parsedValues: prevParsedValues
    } = this.state;
    let mergedProperties = prevMergedProperties;
    let parsed = prevParsedValues;
    let propertiesLoop = [];
    try {
      if (!skipProperties) {
        mergedProperties = getThemeConfiguration(
          {
            identifier,
            extends: baseThemeIdentifier,
            configuration: themeProperties
          },
          themes
        ).configuration;
        parsed = parseConfiguration(mergedProperties);
      }
    } catch (e) {
      console.warn(e.message);
      if (e instanceof ParseConfigurationError) {
        propertiesLoop = e.variables.slice();
      }
    } finally {
      this.setState({
        parsedValues: parsed,
        mergedProperties
      }, () => resolve({
        parsed,
        merged: mergedProperties,
        loops: propertiesLoop,
        skipProperties
      }));
    }
  });

  onChange = (valid) => {
    const {
      name,
      extends: baseTheme,
      themeProperties = {},
      parsedValues
    } = this.state;
    const {
      onChange
    } = this.props;
    if (onChange) {
      onChange(
        {
          name,
          extends: baseTheme,
          properties: {...themeProperties},
          parsed: parsedValues
        },
        valid
      );
    }
  };

  validate = (loops = [], skipProperties = false) => {
    return new Promise((resolve) => {
      const {
        name
      } = this.state;
      const {
        theme = {},
        themes = []
      } = this.props;
      const {
        identifier
      } = theme;
      const state = {
        validation: {
          name: validateName(name, themes.filter(o => o.identifier !== identifier))
        }
      };
      if (!skipProperties) {
        state.propertiesValidation = {};
        loops.forEach(variable => {
          state.propertiesValidation[variable] = 'Properties loop detected';
        });
      }
      this.setState(state, () => {
        const {
          validation = {},
          propertiesValidation = {}
        } = this.state;
        resolve(
          !Object.values(validation).some(Boolean) &&
          !Object.values(propertiesValidation || {}).some(Boolean)
        );
      });
    });
  };

  commitChange = (skipProperties = false) => {
    this.build(skipProperties)
      .then(({loops = [], skipProperties} = {}) => this.validate(loops, skipProperties))
      .then((valid) => this.onChange(valid));
  };

  onChangeName = (e) => {
    this.setState({
      name: e.target.value
    }, () => this.commitChange(true));
  };

  onChangeBaseTheme = (e) => {
    this.setState({
      extends: e
    }, () => this.commitChange());
  };

  onChangeConfigurationVariable = (variable, value) => {
    const {
      themeProperties = {}
    } = this.state;
    const newProperties = {
      ...(themeProperties),
      [variable]: value
    };
    if (value === undefined) {
      delete newProperties[variable];
    }
    this.setState({
      themeProperties: newProperties
    }, () => this.commitChange());
  };

  expandCollapseSection = (section) => {
    const {expandedState = {}} = this.state;
    const expanded = !expandedState[section];
    this.setState({
      expandedState: {
        ...expandedState,
        [section]: expanded
      }
    });
  };

  confirmRemoval = () => {
    const {onRemove} = this.props;
    Modal.confirm({
      title: 'Are you sure you want to remove theme?',
      body: 'This operation cannot be undone',
      okText: 'Yes',
      onOk: () => onRemove ? onRemove() : {}
    });
  };

  render () {
    const {
      name,
      extends: baseTheme,
      validation = {},
      propertiesValidation = {},
      themeProperties = {},
      initialProperties = {},
      mergedProperties = {},
      parsedValues = {},
      expandedState = {}
    } = this.state;
    const {
      theme,
      themes = [],
      readOnly,
      previewClassName,
      removable
    } = this.props;
    if (!theme) {
      return null;
    }
    return (
      <div>
        <Section
          title="General"
        >
          <FormItem
            key="name"
            required
            property="name"
            title="Name"
            validation={validation}
            flex
            control
          >
            <Input
              className={classNames(
                styles.control,
                {
                  'cp-error': name && !!validation.name
                }
              )}
              value={name}
              disabled={readOnly}
              onChange={this.onChangeName}
            />
          </FormItem>
          <FormItem
            key="extends"
            required
            property="extends"
            title="Based on theme"
            validation={validation}
            flex
            control
          >
            <Select
              className={classNames(
                styles.control,
                {
                  'cp-error': baseTheme && !!validation.baseTheme
                }
              )}
              value={baseTheme}
              onChange={this.onChangeBaseTheme}
              disabled={readOnly}
            >
              {
                getBaseThemes(themes, theme)
                  .map(baseTheme => (
                    <Select.Option
                      key={baseTheme.identifier}
                      value={baseTheme.identifier}
                    >
                      {baseTheme.name}
                    </Select.Option>
                  ))
              }
            </Select>
          </FormItem>
          {
            removable && (
              <div className={styles.remove}>
                <Button
                  disabled={readOnly}
                  type="danger"
                  style={{lineHeight: 1}}
                  onClick={this.confirmRemoval}
                >
                  Remove theme
                </Button>
              </div>
            )
          }
        </Section>
        {
          sections.map(section => (
            <Section
              key={section}
              identifier={section}
              title={section}
              expandable={
                sectionsConfiguration[section].some(o => o.advanced)
                  ? expandedState
                  : undefined
              }
              toggleExpanded={this.expandCollapseSection}
            >
              <div
                className={styles.sectionContent}
              >
                <div className={styles.properties}>
                  {
                    sectionsConfiguration[section]
                      .map(({key: variable, advanced, type = VariableTypes.color}, index) => (
                        <FormItem
                          key={variable || `${type}-${index}`}
                          divider={type === VariableTypes.divider}
                          title={variable ? (VariableNames[variable] || variable) : undefined}
                          flex
                          control
                          titleClassName={styles.extended}
                          hidden={advanced && !expandedState[section]}
                          property={variable}
                          validation={propertiesValidation}
                          hint={variable ? VariableDescriptions[variable] : undefined}
                        >
                          {
                            type === VariableTypes.color && (
                              <ColorVariable
                                className={classNames(
                                  styles.control,
                                  {
                                    'cp-error': variable && !!propertiesValidation[variable]
                                  }
                                )}
                                variable={variable}
                                error={variable && !!propertiesValidation[variable]}
                                disabled={readOnly}
                                value={themeProperties[variable] || mergedProperties[variable]}
                                modifiedValue={themeProperties[variable]}
                                initialValue={initialProperties[variable]}
                                extended={!!themeProperties[variable]}
                                parsedValue={parsedValues[variable]}
                                parsedValues={parsedValues}
                                variables={ColorVariables}
                                onChange={this.onChangeConfigurationVariable}
                              />
                            )
                          }
                          {
                            type === VariableTypes.image && (
                              <ImageUploader
                                className={classNames(
                                  styles.control,
                                  {
                                    'cp-error': variable && !!propertiesValidation[variable]
                                  }
                                )}
                                disabled={readOnly}
                                variable={variable}
                                maxSize={null}
                                value={themeProperties[variable] || mergedProperties[variable]}
                                modifiedValue={themeProperties[variable]}
                                initialValue={initialProperties[variable]}
                                extended={!!themeProperties[variable]}
                                onChange={this.onChangeConfigurationVariable}
                              />
                            )
                          }
                        </FormItem>
                      ))
                  }
                </div>
                <ElementPreview
                  className={
                    classNames(
                      styles.previews,
                      previewClassName,
                      'themes-management'
                    )
                  }
                  section={section}
                />
              </div>
            </Section>
          ))
        }
      </div>
    );
  }
}

UIThemeEditForm.propTypes = {
  onChange: PropTypes.func,
  onRemove: PropTypes.func,
  theme: PropTypes.object,
  themes: PropTypes.array,
  previewClassName: PropTypes.string,
  readOnly: PropTypes.bool,
  removable: PropTypes.bool
};

export default UIThemeEditForm;
