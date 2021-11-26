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

import {
  Variables,
  VariableTypes
} from './variable-descriptions';

const Divider = {variable: {type: VariableTypes.divider}, advanced: true};

function mapConfiguration (item) {
  if (!item) {
    return undefined;
  }
  if (item.variable) {
    return item;
  }
  return {variable: item, advanced: false};
}

const alerts = [
  Variables.alertErrorBackground,
  {
    variable: Variables.alertErrorBorder,
    advanced: true
  },
  Variables.alertErrorIcon,
  Variables.alertInfoBackground,
  {
    variable: Variables.alertInfoBorder,
    advanced: true
  },
  Variables.alertInfoIcon,
  Variables.alertWarningBackground,
  {
    variable: Variables.alertWarningBorder,
    advanced: true
  },
  Variables.alertWarningIcon,
  Variables.alertSuccessBackground,
  {
    variable: Variables.alertSuccessBorder,
    advanced: true
  },
  Variables.alertSuccessIcon
];

const buttons = [
  Variables.primaryColor,
  {
    variable: Variables.primaryTextColor,
    advanced: true
  },
  {
    variable: Variables.primaryHoverColor,
    advanced: true
  },
  {
    variable: Variables.primaryActiveColor,
    advanced: true
  },
  Divider,
  Variables.btnDangerColor,
  {
    variable: Variables.btnDangerBackgroundColor,
    advanced: true
  },
  {
    variable: Variables.btnDangerActiveColor,
    advanced: true
  },
  {
    variable: Variables.btnDangerActiveBackground,
    advanced: true
  },
  Divider,
  Variables.btnDisabledColor,
  {
    variable: Variables.btnDisabledBackgroundColor,
    advanced: true
  }
];

const input = [
  Variables.inputColor,
  Variables.inputBackground,
  Variables.inputBorder,
  Divider,
  {
    variable: Variables.inputPlaceholderColor,
    advanced: true
  },
  {
    variable: Variables.inputBackgroundDisabled,
    advanced: true
  },
  Divider,
  {
    variable: Variables.inputBorderHoverColor,
    advanced: true
  },
  {
    variable: Variables.inputShadowColor,
    advanced: true
  },
  Divider,
  {
    variable: Variables.inputAddon,
    advanced: true
  },
  {
    variable: Variables.inputSearchIconColor,
    advanced: true
  },
  {
    variable: Variables.inputSearchIconHoveredColor,
    advanced: true
  }
];

const main = [
  Variables.applicationBackgroundColor,
  {
    variable: Variables.backgroundImage,
    advanced: true,
    type: VariableTypes.image
  },
  Divider,
  Variables.applicationColor,
  {
    variable: Variables.applicationColorAccent,
    advanced: true
  },
  {
    variable: Variables.applicationColorFaded,
    advanced: true
  },
  {
    variable: Variables.applicationColorDisabled,
    advanced: true
  },
  Divider,
  Variables.primaryColor,
  {
    variable: Variables.menuActiveColor,
    advanced: true
  },
  Divider,
  Variables.panelBackgroundColor,
  {
    variable: Variables.panelBorderColor,
    advanced: true
  },
  Divider,
  Variables.cardBackgroundColor,
  {
    variable: Variables.cardHeaderBackground,
    advanced: true
  },
  {
    variable: Variables.cardBorderColor,
    advanced: true
  },
  {
    variable: Variables.cardHoveredShadowColor,
    advanced: true
  },
  {
    variable: Variables.cardActionsActiveBackground,
    advanced: true
  },
  Divider,
  {
    variable: Variables.cardServiceBackgroundColor,
    advanced: true
  },
  {
    variable: Variables.cardServiceHeaderBackground,
    advanced: true
  },
  {
    variable: Variables.cardServiceBorderColor,
    advanced: true
  },
  {
    variable: Variables.cardServiceHoveredShadowColor,
    advanced: true
  },
  {
    variable: Variables.cardServiceActionsActiveBackground,
    advanced: true
  }
];

const navigation = [
  Variables.navigationPanelColor,
  {variable: Variables.navigationItemColor, advanced: true},
  {variable: Variables.navigationPanelHighlightedColor, advanced: true},
  Variables.navigationItemRunsColor,
  Divider,
  Variables.navigationPanelColorImpersonated,
  {variable: Variables.navigationPanelHighlightedColorImpersonated, advanced: true},
  Divider,
  {
    variable: Variables.logoImage,
    advanced: true,
    type: VariableTypes.image
  },
  {
    variable: Variables.navigationBackgroundImage,
    advanced: true,
    type: VariableTypes.image
  }
];

const colors = [
  Variables.colorInfo,
  Variables.colorSuccess,
  Variables.colorWarning,
  Variables.colorError,
  Divider,
  {
    variable: Variables.colorSensitive,
    advanced: true
  },
  {
    variable: Variables.nfsIconColor,
    advanced: true
  },
  {
    variable: Variables.spinner,
    advanced: true
  },
  Divider,
  {
    variable: Variables.searchHighlightTextColor,
    advanced: true
  },
  {
    variable: Variables.searchHighlightTextBackgroundColor,
    advanced: true
  },
  Divider,
  {
    variable: Variables.modalMaskBackground,
    advanced: true
  },
  Divider,
  {
    variable: Variables.tagKeyBackgroundColor,
    advanced: true
  },
  {
    variable: Variables.tagValueBackgroundColor,
    advanced: true
  },
  {
    variable: Variables.tagKeyValueDividerColor,
    advanced: true
  }
];

const tables = [
  Variables.elementHoverColor,
  Variables.elementHoverBackgroundColor,
  Variables.elementSelectedColor,
  Variables.elementSelectedBackgroundColor,
  {
    variable: Variables.evenElementBackground,
    advanced: true
  },
  Divider,
  {
    variable: Variables.tableBorderColor,
    advanced: true
  },
  {
    variable: Variables.tableHeadColor,
    advanced: true
  },
  {
    variable: Variables.tableElementHoverColor,
    advanced: true
  },
  {
    variable: Variables.tableElementHoverBackgroundColor,
    advanced: true
  },
  {
    variable: Variables.tableElementSelectedColor,
    advanced: true
  },
  {
    variable: Variables.tableElementSelectedBackgroundColor,
    advanced: true
  },
  Divider,
  {
    variable: Variables.primaryColorSemiTransparent,
    advanced: true
  }
];

const other = [
  Variables.awsIcon,
  {
    variable: Variables.awsIconContrast,
    advanced: true
  },
  Variables.gcpIcon,
  {
    variable: Variables.gcpIconContrast,
    advanced: true
  },
  Variables.azureIcon,
  {
    variable: Variables.azureIconContrast,
    advanced: true
  },
  Divider,
  ...[
    Variables.codeBackgroundColor,
    Variables.colorGreen,
    Variables.colorRed,
    Variables.colorYellow,
    Variables.colorBlue,
    Variables.colorViolet,
    Variables.colorAqua,
    Variables.colorAquaLight,
    Variables.colorPink,
    Variables.colorPinkDusty,
    Variables.colorPinkLight,
    Variables.colorBlueDimmed,
    Variables.colorGrey
  ].map(variable => ({variable, advanced: true}))
];

const sections = {
  alerts: 'Alerts',
  buttons: 'Buttons',
  input: 'Forms',
  main: 'Main',
  navigation: 'Navigation panel',
  colors: `Colors`,
  tables: 'Tables & Lists',
  other: 'Other'
};

const sectionsConfiguration = {
  [sections.alerts]: alerts.map(mapConfiguration),
  [sections.buttons]: buttons.map(mapConfiguration),
  [sections.input]: input.map(mapConfiguration),
  [sections.main]: main.map(mapConfiguration),
  [sections.navigation]: navigation.map(mapConfiguration),
  [sections.colors]: colors.map(mapConfiguration),
  [sections.tables]: tables.map(mapConfiguration),
  [sections.other]: other.map(mapConfiguration)
};

const orderedSections = [
  sections.main,
  sections.navigation,
  sections.tables,
  sections.buttons,
  sections.input,
  sections.colors,
  sections.alerts,
  sections.other
];

function variableNameSorter (a, b) {
  const aName = a.name;
  const bName = b.name;
  return aName.localeCompare(bName);
}

const grouped = orderedSections
  .map(section => ({
    name: sections[section] || section,
    variables: (sectionsConfiguration[section] || [])
      .map(o => o.variable)
      .filter(o => o && (!o.type || o.type === VariableTypes.color))
      .sort(variableNameSorter)
  }));
const plain = grouped
  .reduce((result, group) => ([...result, ...group.variables]), []);

const variablesWithoutSection = Object.values(Variables)
  .filter(o => !plain.includes(o) && (!o.type || o.type === VariableTypes.color));
console.log('VARIABLES WITHOUT SECTIONS', variablesWithoutSection);
grouped.push(
  {
    name: 'Other properties',
    variables: variablesWithoutSection.sort(variableNameSorter)
  }
);

const groupedColorVariables = grouped.filter(group => group.variables.length > 0);

export {
  groupedColorVariables,
  orderedSections as sections,
  sections as sectionNames,
  sectionsConfiguration,
  VariableTypes
};
