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

import {Variables} from './variable-descriptions';

const VariableTypes = {
  color: 'color',
  image: 'image',
  divider: 'divider'
};

const Divider = {type: VariableTypes.divider, advanced: true};

function mapConfiguration (item) {
  if (typeof item === 'string') {
    return {
      key: item,
      advanced: false,
      type: VariableTypes.color
    };
  }
  return item;
}

const alerts = [
  Variables.alertErrorBackground,
  {
    key: Variables.alertErrorBorder,
    advanced: true
  },
  Variables.alertErrorIcon,
  Variables.alertInfoBackground,
  {
    key: Variables.alertInfoBorder,
    advanced: true
  },
  Variables.alertInfoIcon,
  Variables.alertWarningBackground,
  {
    key: Variables.alertWarningBorder,
    advanced: true
  },
  Variables.alertWarningIcon,
  Variables.alertSuccessBackground,
  {
    key: Variables.alertSuccessBorder,
    advanced: true
  },
  Variables.alertSuccessIcon
];

const buttons = [
  Variables.primaryColor,
  {
    key: Variables.primaryTextColor,
    advanced: true
  },
  {
    key: Variables.primaryHoverColor,
    advanced: true
  },
  {
    key: Variables.primaryActiveColor,
    advanced: true
  },
  Divider,
  Variables.btnDangerColor,
  {
    key: Variables.btnDangerBackgroundColor,
    advanced: true
  },
  {
    key: Variables.btnDangerActiveColor,
    advanced: true
  },
  {
    key: Variables.btnDangerActiveBackground,
    advanced: true
  },
  Divider,
  Variables.btnDisabledColor,
  {
    key: Variables.btnDisabledBackgroundColor,
    advanced: true
  }
];

const input = [
  Variables.inputColor,
  Variables.inputBackground,
  Variables.inputBorder,
  Divider,
  {
    key: Variables.inputPlaceholderColor,
    advanced: true
  },
  {
    key: Variables.inputBackgroundDisabled,
    advanced: true
  },
  Divider,
  {
    key: Variables.inputBorderHoverColor,
    advanced: true
  },
  {
    key: Variables.inputShadowColor,
    advanced: true
  },
  Divider,
  {
    key: Variables.inputAddon,
    advanced: true
  },
  {
    key: Variables.inputSearchIconColor,
    advanced: true
  },
  {
    key: Variables.inputSearchIconHoveredColor,
    advanced: true
  }
];

const main = [
  Variables.applicationBackgroundColor,
  {
    key: Variables.backgroundImage,
    advanced: true,
    type: VariableTypes.image
  },
  Divider,
  Variables.applicationColor,
  {
    key: Variables.applicationColorAccent,
    advanced: true
  },
  {
    key: Variables.applicationColorFaded,
    advanced: true
  },
  {
    key: Variables.applicationColorDisabled,
    advanced: true
  },
  Divider,
  Variables.primaryColor,
  {
    key: Variables.menuActiveColor,
    advanced: true
  },
  Divider,
  Variables.panelBackgroundColor,
  {
    key: Variables.panelBorderColor,
    advanced: true
  },
  Divider,
  Variables.cardBackgroundColor,
  {
    key: Variables.cardHeaderBackground,
    advanced: true
  },
  {
    key: Variables.cardBorderColor,
    advanced: true
  },
  {
    key: Variables.cardHoveredShadowColor,
    advanced: true
  },
  {
    key: Variables.cardActionsActiveBackground,
    advanced: true
  },
  Divider,
  {
    key: Variables.cardServiceBackgroundColor,
    advanced: true
  },
  {
    key: Variables.cardServiceHeaderBackground,
    advanced: true
  },
  {
    key: Variables.cardServiceBorderColor,
    advanced: true
  },
  {
    key: Variables.cardServiceHoveredShadowColor,
    advanced: true
  },
  {
    key: Variables.cardServiceActionsActiveBackground,
    advanced: true
  }
];

const navigation = [
  Variables.navigationPanelColor,
  {key: Variables.navigationItemColor, advanced: true},
  {key: Variables.navigationPanelHighlightedColor, advanced: true},
  Variables.navigationItemRunsColor,
  Divider,
  Variables.navigationPanelColorImpersonated,
  {key: Variables.navigationPanelHighlightedColorImpersonated, advanced: true},
  Divider,
  {
    key: Variables.logoImage,
    advanced: true,
    type: VariableTypes.image
  },
  {
    key: Variables.navigationBackgroundImage,
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
    key: Variables.colorSensitive,
    advanced: true
  },
  {
    key: Variables.nfsIconColor,
    advanced: true
  },
  {
    key: Variables.spinner,
    advanced: true
  },
  Divider,
  {
    key: Variables.searchHighlightTextColor,
    advanced: true
  },
  {
    key: Variables.searchHighlightTextBackgroundColor,
    advanced: true
  },
  Divider,
  {
    key: Variables.modalMaskBackground,
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
  ].map(key => ({key, advanced: true}))
];

const tables = [
  Variables.elementHoverColor,
  Variables.elementHoverBackgroundColor,
  Variables.elementSelectedColor,
  Variables.elementSelectedBackgroundColor,
  {
    key: Variables.evenElementBackground,
    advanced: true
  },
  Divider,
  {
    key: Variables.tableBorderColor,
    advanced: true
  },
  {
    key: Variables.tableHeadColor,
    advanced: true
  },
  {
    key: Variables.tableElementHoverColor,
    advanced: true
  },
  {
    key: Variables.tableElementHoverBackgroundColor,
    advanced: true
  },
  {
    key: Variables.tableElementSelectedColor,
    advanced: true
  },
  {
    key: Variables.tableElementSelectedBackgroundColor,
    advanced: true
  },
  Divider,
  {
    key: Variables.primaryColorSemiTransparent,
    advanced: true
  }
];

const sections = {
  alerts: 'Alerts',
  buttons: 'Buttons',
  input: 'Forms',
  main: 'Main',
  navigation: 'Navigation panel',
  colors: `Colors`,
  tables: 'Tables & Lists'
};

const sectionsConfiguration = {
  [sections.alerts]: alerts.map(mapConfiguration),
  [sections.buttons]: buttons.map(mapConfiguration),
  [sections.input]: input.map(mapConfiguration),
  [sections.main]: main.map(mapConfiguration),
  [sections.navigation]: navigation.map(mapConfiguration),
  [sections.colors]: colors.map(mapConfiguration),
  [sections.tables]: tables.map(mapConfiguration)
};

const orderedSections = [
  sections.main,
  sections.navigation,
  sections.tables,
  sections.buttons,
  sections.input,
  sections.colors,
  sections.alerts
];

export {
  orderedSections as sections,
  sections as sectionNames,
  sectionsConfiguration,
  VariableTypes
};
