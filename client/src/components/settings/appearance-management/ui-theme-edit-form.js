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
  Input,
  Select
} from 'antd';
import {validateName} from './utilities/theme-validation';
import {ColorVariables, VariableNames} from './utilities/variable-descriptions';
import {getThemeConfiguration, parseConfiguration} from '../../../themes/themes';
import ColorVariable from './color-variable';
import {ParseConfigurationError} from '../../../themes/utilities/parse-configuration';
import getBaseThemes from './utilities/get-base-themes';
import {sections, sectionsConfiguration} from './utilities/variable-sections';
import ElementPreview from './element-preview';
import styles from './ui-theme-edit-form.css';

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
    title,
    required,
    property,
    validation = {},
    flex = false,
    control,
    titleClassName,
    hidden
  }
) => (
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
      {
        control && React.cloneElement(
          children,
          {
            ...(children.props || {}),
            ...(
              children.type === ColorVariable
                ? {error: property && !!validation[property]}
                : {}
            ),
            className: classNames(
              (children.props || {}).className,
              styles.control,
              {
                'cp-error': property && !!validation[property]
              }
            )
          }
        )
      }
      {
        !control && {children}
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

const Section = (
  {
    expandable = {},
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
    expandedState: {
      background: false
    }
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
      validation: {}
    }, () => this.build());
  };

  build = () => {
    return new Promise((resolve) => {
      const {
        theme = {},
        themes = []
      } = this.props;
      const {
        identifier
      } = theme;
      const {
        themeProperties = {},
        extends: baseThemeIdentifier
      } = this.state;
      const {
        configuration: mergedProperties
      } = getThemeConfiguration(
        {
          identifier,
          extends: baseThemeIdentifier,
          configuration: themeProperties
        },
        themes
      );
      let parsed = {};
      let propertiesLoop = [];
      try {
        parsed = parseConfiguration(mergedProperties);
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
          loops: propertiesLoop
        }));
      }
    });
  };

  onChange = (valid) => {
    const {
      name,
      extends: baseTheme,
      themeProperties = {}
    } = this.state;
    const {
      onChange
    } = this.props;
    if (onChange) {
      onChange(
        {
          name,
          extends: baseTheme,
          properties: {...themeProperties}
        },
        valid
      );
    }
  };

  validate = (loops = []) => {
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
      const validation = {
        name: validateName(name, themes.filter(o => o.identifier !== identifier))
      };
      loops.forEach(variable => {
        validation[variable] = 'Properties loop detected';
      });
      this.setState({
        validation
      }, () => resolve(!Object.values(validation).some(Boolean)));
    });
  };

  commitChange = () => {
    this.build()
      .then(({loops = []} = {}) => this.validate(loops))
      .then((valid) => this.onChange(valid));
  };

  onChangeName = (e) => {
    this.setState({
      name: e.target.value
    }, this.commitChange);
  };

  onChangeBaseTheme = (e) => {
    this.setState({
      extends: e
    }, this.commitChange);
  };

  onChangeConfigurationVariable = (variable) => (value) => {
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
    }, this.commitChange);
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

  render () {
    const {
      name,
      extends: baseTheme,
      validation = {},
      themeProperties = {},
      mergedProperties = {},
      parsedValues = {},
      expandedState = {}
    } = this.state;
    const {
      theme,
      themes = [],
      readOnly,
      previewClassName
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
        </Section>
        {
          sections.map(section => (
            <Section
              key={section}
              identifier={section}
              title={section}
              expandable={expandedState}
              toggleExpanded={this.expandCollapseSection}
            >
              <div
                className={styles.sectionContent}
              >
                <div className={styles.properties}>
                  {
                    sectionsConfiguration[section]
                      .map(({key: variable, advanced = false}) => (
                        <FormItem
                          key={variable}
                          title={VariableNames[variable] || variable}
                          flex
                          control
                          titleClassName={styles.extended}
                          hidden={advanced && !expandedState[section]}
                          property={variable}
                          validation={validation}
                        >
                          <ColorVariable
                            disabled={readOnly}
                            value={themeProperties[variable] || mergedProperties[variable]}
                            modified={!!themeProperties[variable]}
                            parsedValues={parsedValues}
                            parsedValue={parsedValues[variable]}
                            variables={ColorVariables}
                            onChange={this.onChangeConfigurationVariable(variable)}
                          />
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
  theme: PropTypes.object,
  themes: PropTypes.array,
  previewClassName: PropTypes.string,
  readOnly: PropTypes.bool
};

export default UIThemeEditForm;
