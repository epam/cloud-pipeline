/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

/**
 * Semantic theme palette for Cloud Pipeline.
 *
 * Each entry maps a CSS custom property (--cp-*) to its source legacy LESS
 * variable (@key) and, when applicable, to the corresponding Ant Design v6
 * design token. The list is the single source of truth for:
 *   - inject-css-variables.js (runtime CSS vars on body)
 *   - antd-theme-config.js (ConfigProvider token)
 *   - documentation (css-variables.reference.css)
 */

export const TokenGroups = {
  brand: 'brand',
  status: 'status',
  text: 'text',
  surface: 'surface',
  border: 'border',
  interactive: 'interactive',
  table: 'table',
  form: 'form',
  button: 'button',
  navigation: 'navigation',
  alert: 'alert',
  tooltip: 'tooltip',
  card: 'card',
  codeEditor: 'code-editor',
  tag: 'tag',
  vsDiff: 'vs-diff',
  extended: 'extended',
  providerIcon: 'provider-icon',
  regionIcon: 'region-icon',
  asset: 'asset'
};

export const SemanticTokens = [
  // -- Brand -----------------------------------------------------------------
  {
    cssVar: '--cp-color-primary',
    legacy: '@primary-color',
    group: TokenGroups.brand,
    antd: 'colorPrimary',
    description: 'Primary brand color (links, primary buttons)'
  },
  {
    cssVar: '--cp-color-primary-hover',
    legacy: '@primary-hover-color',
    group: TokenGroups.brand,
    antd: 'colorPrimaryHover',
    description: 'Hover state of primary actions'
  },
  {
    cssVar: '--cp-color-primary-active',
    legacy: '@primary-active-color',
    group: TokenGroups.brand,
    antd: 'colorPrimaryActive',
    description: 'Active/pressed state of primary actions'
  },
  {
    cssVar: '--cp-color-primary-on',
    legacy: '@primary-text-color',
    group: TokenGroups.brand,
    antd: 'colorTextLightSolid',
    description: 'Text color on top of primary backgrounds'
  },
  {
    cssVar: '--cp-color-primary-muted',
    legacy: '@primary-color-semi-transparent',
    group: TokenGroups.brand,
    antd: 'colorPrimaryBg',
    description: 'Translucent primary fill (selection, badges)'
  },

  // -- Status ----------------------------------------------------------------
  {
    cssVar: '--cp-color-success',
    legacy: '@color-success',
    group: TokenGroups.status,
    antd: 'colorSuccess',
    description: 'Success status color'
  },
  {
    cssVar: '--cp-color-error',
    legacy: '@color-error',
    group: TokenGroups.status,
    antd: 'colorError',
    description: 'Error status color'
  },
  {
    cssVar: '--cp-color-warning',
    legacy: '@color-warning',
    group: TokenGroups.status,
    antd: 'colorWarning',
    description: 'Warning status color'
  },
  {
    cssVar: '--cp-color-info',
    legacy: '@color-info',
    group: TokenGroups.status,
    antd: 'colorInfo',
    description: 'Info status color'
  },

  // -- Text ------------------------------------------------------------------
  {
    cssVar: '--cp-color-text',
    legacy: '@application-color',
    group: TokenGroups.text,
    antd: 'colorText',
    description: 'Default body text color'
  },
  {
    cssVar: '--cp-color-text-secondary',
    legacy: '@application-color-faded',
    group: TokenGroups.text,
    antd: 'colorTextSecondary',
    description: 'Secondary/faded text'
  },
  {
    cssVar: '--cp-color-text-disabled',
    legacy: '@application-color-disabled',
    group: TokenGroups.text,
    antd: 'colorTextDisabled',
    description: 'Disabled text'
  },
  {
    cssVar: '--cp-color-text-accent',
    legacy: '@application-color-accent',
    group: TokenGroups.text,
    antd: 'colorTextHeading',
    description: 'Accented (headings, emphasis) text'
  },

  // -- Surfaces / layout -----------------------------------------------------
  {
    cssVar: '--cp-color-bg-layout',
    legacy: '@application-background-color',
    group: TokenGroups.surface,
    antd: 'colorBgLayout',
    description: 'Application page background'
  },
  {
    cssVar: '--cp-color-bg-layout-dark',
    legacy: '@application-dark-background-color',
    group: TokenGroups.surface,
    description: 'Dark variant of layout background (e.g. console)'
  },
  {
    cssVar: '--cp-color-bg-container',
    legacy: '@panel-background-color',
    group: TokenGroups.surface,
    antd: 'colorBgContainer',
    description: 'Default panel/container background'
  },
  {
    cssVar: '--cp-color-bg-elevated',
    legacy: '@card-background-color',
    group: TokenGroups.surface,
    antd: 'colorBgElevated',
    description: 'Elevated surface (cards, popovers, modals)'
  },
  {
    cssVar: '--cp-color-bg-elevated-header',
    legacy: '@card-header-background',
    group: TokenGroups.surface,
    description: 'Card header strip background'
  },
  {
    cssVar: '--cp-color-bg-input',
    legacy: '@input-background',
    group: TokenGroups.surface,
    description: 'Input control background'
  },
  {
    cssVar: '--cp-color-bg-input-disabled',
    legacy: '@input-background-disabled',
    group: TokenGroups.surface,
    antd: 'colorBgContainerDisabled',
    description: 'Disabled input background'
  },
  {
    cssVar: '--cp-color-bg-code',
    legacy: '@code-background-color',
    group: TokenGroups.surface,
    description: 'Code editor / pre background'
  },
  {
    cssVar: '--cp-color-bg-striped',
    legacy: '@even-element-background',
    group: TokenGroups.surface,
    description: 'Even rows / striped backgrounds'
  },
  {
    cssVar: '--cp-color-bg-overlay',
    legacy: '@modal-mask-background',
    group: TokenGroups.surface,
    antd: 'colorBgMask',
    description: 'Modal / drawer overlay'
  },

  // -- Borders ---------------------------------------------------------------
  {
    cssVar: '--cp-color-border',
    legacy: '@panel-border-color',
    group: TokenGroups.border,
    antd: 'colorBorder',
    description: 'Default border'
  },
  {
    cssVar: '--cp-color-border-secondary',
    legacy: '@panel-border-color-light',
    group: TokenGroups.border,
    antd: 'colorBorderSecondary',
    description: 'Subtle border / divider'
  },
  {
    cssVar: '--cp-color-border-card',
    legacy: '@card-border-color',
    group: TokenGroups.border,
    description: 'Card border'
  },
  {
    cssVar: '--cp-color-border-input',
    legacy: '@input-border',
    group: TokenGroups.border,
    description: 'Input border'
  },
  {
    cssVar: '--cp-color-border-table',
    legacy: '@table-border-color',
    group: TokenGroups.border,
    description: 'Table border'
  },

  // -- Interactive (lists, hovers, selection) --------------------------------
  {
    cssVar: '--cp-color-interactive-hover-text',
    legacy: '@element-hover-color',
    group: TokenGroups.interactive,
    description: 'Hovered element text'
  },
  {
    cssVar: '--cp-color-interactive-hover-bg',
    legacy: '@element-hover-background-color',
    group: TokenGroups.interactive,
    antd: 'controlItemBgHover',
    description: 'Hovered element background'
  },
  {
    cssVar: '--cp-color-interactive-selected-text',
    legacy: '@element-selected-color',
    group: TokenGroups.interactive,
    description: 'Selected element text'
  },
  {
    cssVar: '--cp-color-interactive-selected-bg',
    legacy: '@element-selected-background-color',
    group: TokenGroups.interactive,
    antd: 'controlItemBgActive',
    description: 'Selected element background'
  },

  // -- Tables ----------------------------------------------------------------
  {
    cssVar: '--cp-color-table-hover-text',
    legacy: '@table-element-hover-color',
    group: TokenGroups.table,
    description: 'Table row hover text'
  },
  {
    cssVar: '--cp-color-table-hover-bg',
    legacy: '@table-element-hover-background-color',
    group: TokenGroups.table,
    description: 'Table row hover background'
  },
  {
    cssVar: '--cp-color-table-selected-text',
    legacy: '@table-element-selected-color',
    group: TokenGroups.table,
    description: 'Table row selected text'
  },
  {
    cssVar: '--cp-color-table-selected-bg',
    legacy: '@table-element-selected-background-color',
    group: TokenGroups.table,
    description: 'Table row selected background'
  },
  {
    cssVar: '--cp-color-table-head-text',
    legacy: '@table-head-color',
    group: TokenGroups.table,
    description: 'Table header text'
  },

  // -- Forms -----------------------------------------------------------------
  {
    cssVar: '--cp-color-input-text',
    legacy: '@input-color',
    group: TokenGroups.form,
    description: 'Input text color'
  },
  {
    cssVar: '--cp-color-input-placeholder',
    legacy: '@input-placeholder-color',
    group: TokenGroups.form,
    antd: 'colorTextPlaceholder',
    description: 'Placeholder text color'
  },
  {
    cssVar: '--cp-color-input-addon-bg',
    legacy: '@input-addon',
    group: TokenGroups.form,
    description: 'Input addon (prefix/suffix) background'
  },
  {
    cssVar: '--cp-color-input-border-hover',
    legacy: '@input-border-hover-color',
    group: TokenGroups.form,
    description: 'Input border on hover'
  },
  {
    cssVar: '--cp-color-input-focus-ring',
    legacy: '@input-shadow-color',
    group: TokenGroups.form,
    description: 'Input focus shadow / ring'
  },

  // -- Buttons ---------------------------------------------------------------
  {
    cssVar: '--cp-color-btn-disabled-text',
    legacy: '@btn-disabled-color',
    group: TokenGroups.button,
    description: 'Disabled button text'
  },
  {
    cssVar: '--cp-color-btn-disabled-bg',
    legacy: '@btn-disabled-background-color',
    group: TokenGroups.button,
    description: 'Disabled button background'
  },
  {
    cssVar: '--cp-color-btn-danger-text',
    legacy: '@btn-danger-color',
    group: TokenGroups.button,
    description: 'Danger button text'
  },
  {
    cssVar: '--cp-color-btn-danger-bg',
    legacy: '@btn-danger-background-color',
    group: TokenGroups.button,
    description: 'Danger button background'
  },
  {
    cssVar: '--cp-color-btn-danger-active-text',
    legacy: '@btn-danger-active-color',
    group: TokenGroups.button,
    description: 'Danger button active text'
  },
  {
    cssVar: '--cp-color-btn-danger-active-bg',
    legacy: '@btn-danger-active-background',
    group: TokenGroups.button,
    description: 'Danger button active background'
  },

  // -- Navigation ------------------------------------------------------------
  {
    cssVar: '--cp-color-nav-bg',
    legacy: '@navigation-panel-color',
    group: TokenGroups.navigation,
    description: 'Navigation panel background'
  },
  {
    cssVar: '--cp-color-nav-bg-active',
    legacy: '@navigation-panel-highlighted-color',
    group: TokenGroups.navigation,
    description: 'Navigation panel active item background'
  },
  {
    cssVar: '--cp-color-nav-bg-impersonated',
    legacy: '@navigation-panel-color-impersonated',
    group: TokenGroups.navigation,
    description: 'Navigation panel background when impersonating'
  },
  {
    cssVar: '--cp-color-nav-bg-impersonated-active',
    legacy: '@navigation-panel-highlighted-color-impersonated',
    group: TokenGroups.navigation,
    description: 'Navigation panel active item (impersonated)'
  },
  {
    cssVar: '--cp-color-nav-text',
    legacy: '@navigation-item-color',
    group: TokenGroups.navigation,
    description: 'Navigation item text'
  },
  {
    cssVar: '--cp-color-nav-runs',
    legacy: '@navigation-item-runs-color',
    group: TokenGroups.navigation,
    description: 'Navigation: active runs accent'
  },

  // -- Alerts ----------------------------------------------------------------
  {
    cssVar: '--cp-color-alert-success-bg',
    legacy: '@alert-success-background',
    group: TokenGroups.alert,
    description: 'Success alert background'
  },
  {
    cssVar: '--cp-color-alert-success-border',
    legacy: '@alert-success-border',
    group: TokenGroups.alert,
    description: 'Success alert border'
  },
  {
    cssVar: '--cp-color-alert-success-icon',
    legacy: '@alert-success-icon',
    group: TokenGroups.alert,
    description: 'Success alert icon'
  },
  {
    cssVar: '--cp-color-alert-warning-bg',
    legacy: '@alert-warning-background',
    group: TokenGroups.alert,
    description: 'Warning alert background'
  },
  {
    cssVar: '--cp-color-alert-warning-border',
    legacy: '@alert-warning-border',
    group: TokenGroups.alert,
    description: 'Warning alert border'
  },
  {
    cssVar: '--cp-color-alert-warning-icon',
    legacy: '@alert-warning-icon',
    group: TokenGroups.alert,
    description: 'Warning alert icon'
  },
  {
    cssVar: '--cp-color-alert-error-bg',
    legacy: '@alert-error-background',
    group: TokenGroups.alert,
    description: 'Error alert background'
  },
  {
    cssVar: '--cp-color-alert-error-border',
    legacy: '@alert-error-border',
    group: TokenGroups.alert,
    description: 'Error alert border'
  },
  {
    cssVar: '--cp-color-alert-error-icon',
    legacy: '@alert-error-icon',
    group: TokenGroups.alert,
    description: 'Error alert icon'
  },
  {
    cssVar: '--cp-color-alert-info-bg',
    legacy: '@alert-info-background',
    group: TokenGroups.alert,
    description: 'Info alert background'
  },
  {
    cssVar: '--cp-color-alert-info-border',
    legacy: '@alert-info-border',
    group: TokenGroups.alert,
    description: 'Info alert border'
  },
  {
    cssVar: '--cp-color-alert-info-icon',
    legacy: '@alert-info-icon',
    group: TokenGroups.alert,
    description: 'Info alert icon'
  },

  // -- Extended (charts, diff viewer, console, search highlight) -------------
  {
    cssVar: '--cp-color-spinner',
    legacy: '@spinner',
    group: TokenGroups.extended,
    description: 'Loading indicator color'
  },
  {
    cssVar: '--cp-color-green',
    legacy: '@color-green',
    group: TokenGroups.extended,
    description: 'Generic green'
  },
  {
    cssVar: '--cp-color-red',
    legacy: '@color-red',
    group: TokenGroups.extended,
    description: 'Generic red'
  },
  {
    cssVar: '--cp-color-yellow',
    legacy: '@color-yellow',
    group: TokenGroups.extended,
    description: 'Generic yellow'
  },
  {
    cssVar: '--cp-color-blue',
    legacy: '@color-blue',
    group: TokenGroups.extended,
    description: 'Generic blue'
  },
  {
    cssVar: '--cp-color-violet',
    legacy: '@color-violet',
    group: TokenGroups.extended,
    description: 'Generic violet'
  },
  {
    cssVar: '--cp-color-aqua',
    legacy: '@color-aqua',
    group: TokenGroups.extended,
    description: 'Generic aqua'
  },
  {
    cssVar: '--cp-color-aqua-light',
    legacy: '@color-aqua-light',
    group: TokenGroups.extended,
    description: 'Generic aqua light'
  },
  {
    cssVar: '--cp-color-pink',
    legacy: '@color-pink',
    group: TokenGroups.extended,
    description: 'Generic pink'
  },
  {
    cssVar: '--cp-color-grey',
    legacy: '@color-grey',
    group: TokenGroups.extended,
    description: 'Generic grey'
  },
  {
    cssVar: '--cp-color-blue-dimmed',
    legacy: '@color-blue-dimmed',
    group: TokenGroups.extended,
    description: 'Dimmed blue (e.g. links secondary)'
  },
  {
    cssVar: '--cp-color-sensitive',
    legacy: '@color-sensitive',
    group: TokenGroups.extended,
    description: 'Sensitive object accent'
  },
  {
    cssVar: '--cp-color-search-highlight-bg',
    legacy: '@search-highlight-text-background-color',
    group: TokenGroups.extended,
    description: 'Search match highlight background'
  },
  {
    cssVar: '--cp-color-search-highlight-bg-inactive',
    legacy: '@search-highlight-text-inactive-background-color',
    group: TokenGroups.extended,
    description: 'Search match (inactive) background'
  },
  {
    cssVar: '--cp-color-search-highlight-text',
    legacy: '@search-highlight-text-color',
    group: TokenGroups.extended,
    description: 'Search match text color'
  },
  {
    cssVar: '--cp-color-console-bg',
    legacy: '@application-console-background-color',
    group: TokenGroups.extended,
    description: 'Console background'
  },
  {
    cssVar: '--cp-color-console-text',
    legacy: '@application-console-color',
    group: TokenGroups.extended,
    description: 'Console text'
  },
  {
    cssVar: '--cp-color-console-text-details',
    legacy: '@application-console-color-details',
    group: TokenGroups.extended,
    description: 'Console muted text'
  },
  {
    cssVar: '--cp-color-menu-active',
    legacy: '@menu-active-color',
    group: TokenGroups.extended,
    description: 'Active/hovered menu item color'
  },
  {
    cssVar: '--cp-color-card-actions-active-bg',
    legacy: '@card-actions-active-background',
    group: TokenGroups.extended,
    description: 'Card action panel active background'
  },
  {
    cssVar: '--cp-color-green-faint',
    legacy: '@color-green-semi-transparent',
    group: TokenGroups.extended,
    description: 'Translucent green'
  },
  {
    cssVar: '--cp-color-red-faint',
    legacy: '@color-red-semi-transparent',
    group: TokenGroups.extended,
    description: 'Translucent red'
  },
  {
    cssVar: '--cp-color-grey-faint',
    legacy: '@color-grey-semi-transparent',
    group: TokenGroups.extended,
    description: 'Translucent grey'
  },
  {
    cssVar: '--cp-color-grey-light',
    legacy: '@color-grey-light',
    group: TokenGroups.extended,
    description: 'Light grey'
  },
  {
    cssVar: '--cp-color-blue-soft',
    legacy: '@color-blue-soft',
    group: TokenGroups.extended,
    description: 'Soft blue accent'
  },
  {
    cssVar: '--cp-color-green-soft',
    legacy: '@color-green-soft',
    group: TokenGroups.extended,
    description: 'Soft green accent'
  },
  {
    cssVar: '--cp-color-aqua-accent',
    legacy: '@color-aqua-accent',
    group: TokenGroups.extended,
    description: 'Aqua accent'
  },
  {
    cssVar: '--cp-color-pink-light',
    legacy: '@color-pink-light',
    group: TokenGroups.extended,
    description: 'Light pink'
  },
  {
    cssVar: '--cp-color-pink-dusty',
    legacy: '@color-pink-dusty',
    group: TokenGroups.extended,
    description: 'Dusty pink'
  },
  {
    cssVar: '--cp-color-deleted-row-bg',
    legacy: '@deleted-row-accent',
    group: TokenGroups.extended,
    description: 'Deleted row background tint'
  },
  {
    cssVar: '--cp-color-nfs-icon',
    legacy: '@nfs-icon-color',
    group: TokenGroups.extended,
    description: 'NFS icon tint'
  },

  // -- Tooltips --------------------------------------------------------------
  {
    cssVar: '--cp-color-tooltip-bg',
    legacy: '@application-tooltip-background-color',
    group: TokenGroups.tooltip,
    description: 'Tooltip background'
  },
  {
    cssVar: '--cp-color-tooltip-border',
    legacy: '@application-tooltip-border-color',
    group: TokenGroups.tooltip,
    description: 'Tooltip border'
  },
  {
    cssVar: '--cp-color-tooltip-text',
    legacy: '@application-tooltip-color',
    group: TokenGroups.tooltip,
    description: 'Tooltip text color'
  },
  {
    cssVar: '--cp-color-tooltip-accent',
    legacy: '@application-color-tooltip',
    group: TokenGroups.tooltip,
    description: 'Tooltip accent (icons, secondary text)'
  },

  // -- Card extras and service cards ----------------------------------------
  {
    cssVar: '--cp-color-card-shadow',
    legacy: '@card-hovered-shadow-color',
    group: TokenGroups.card,
    description: 'Card hover shadow'
  },
  {
    cssVar: '--cp-color-bg-elevated-opaque',
    legacy: '@card-background-color-not-faded',
    group: TokenGroups.card,
    description: 'Elevated surface forced opaque'
  },
  {
    cssVar: '--cp-color-bg-service-card',
    legacy: '@card-service-background-color',
    group: TokenGroups.card,
    description: 'Service card background'
  },
  {
    cssVar: '--cp-color-border-service-card',
    legacy: '@card-service-border-color',
    group: TokenGroups.card,
    description: 'Service card border'
  },
  {
    cssVar: '--cp-color-service-card-shadow',
    legacy: '@card-service-hovered-shadow-color',
    group: TokenGroups.card,
    description: 'Service card hover shadow'
  },
  {
    cssVar: '--cp-color-service-card-actions-active-bg',
    legacy: '@card-service-actions-active-background',
    group: TokenGroups.card,
    description: 'Service card actions active background'
  },
  {
    cssVar: '--cp-color-service-card-header-bg',
    legacy: '@card-service-header-background',
    group: TokenGroups.card,
    description: 'Service card header background'
  },

  // -- Code editor (CodeMirror) and console --------------------------------
  {
    cssVar: '--cp-color-codemirror-selection-bg',
    legacy: '@codemirror-selected-background-color',
    group: TokenGroups.codeEditor,
    description: 'CodeMirror selection background'
  },
  {
    cssVar: '--cp-color-codemirror-selection-focused-bg',
    legacy: '@codemirror-focused-selected-background-color',
    group: TokenGroups.codeEditor,
    description: 'CodeMirror focused selection background'
  },

  // -- Tags ------------------------------------------------------------------
  {
    cssVar: '--cp-color-tag-key-bg',
    legacy: '@tag-key-background-color',
    group: TokenGroups.tag,
    description: 'Tag key chip background'
  },
  {
    cssVar: '--cp-color-tag-divider',
    legacy: '@tag-key-value-divider-color',
    group: TokenGroups.tag,
    description: 'Tag key/value divider'
  },
  {
    cssVar: '--cp-color-tag-value-bg',
    legacy: '@tag-value-background-color',
    group: TokenGroups.tag,
    description: 'Tag value chip background'
  },

  // -- Input search icons ----------------------------------------------------
  {
    cssVar: '--cp-color-input-search-icon',
    legacy: '@input-search-icon-color',
    group: TokenGroups.form,
    description: 'Search input icon'
  },
  {
    cssVar: '--cp-color-input-search-icon-hover',
    legacy: '@input-search-icon-hovered-color',
    group: TokenGroups.form,
    description: 'Search input icon (hover)'
  },

  // -- Versioned-storage diff viewer (vs-*) ---------------------------------
  {
    cssVar: '--cp-color-vs-conflict-bg',
    legacy: '@vs-color-conflict-background',
    group: TokenGroups.vsDiff,
    description: 'VS diff conflict background'
  },
  {
    cssVar: '--cp-color-vs-conflict-border',
    legacy: '@vs-color-conflict-border',
    group: TokenGroups.vsDiff,
    description: 'VS diff conflict border'
  },
  {
    cssVar: '--cp-color-vs-conflict-applied-bg',
    legacy: '@vs-color-conflict-applied-background',
    group: TokenGroups.vsDiff,
    description: 'VS diff conflict applied background'
  },
  {
    cssVar: '--cp-color-vs-insertion-bg',
    legacy: '@vs-color-insertion-background',
    group: TokenGroups.vsDiff,
    description: 'VS diff insertion background'
  },
  {
    cssVar: '--cp-color-vs-insertion-border',
    legacy: '@vs-color-insertion-border',
    group: TokenGroups.vsDiff,
    description: 'VS diff insertion border'
  },
  {
    cssVar: '--cp-color-vs-insertion-applied-bg',
    legacy: '@vs-color-insertion-applied-background',
    group: TokenGroups.vsDiff,
    description: 'VS diff insertion applied background'
  },
  {
    cssVar: '--cp-color-vs-deletion-bg',
    legacy: '@vs-color-deletion-background',
    group: TokenGroups.vsDiff,
    description: 'VS diff deletion background'
  },
  {
    cssVar: '--cp-color-vs-deletion-border',
    legacy: '@vs-color-deletion-border',
    group: TokenGroups.vsDiff,
    description: 'VS diff deletion border'
  },
  {
    cssVar: '--cp-color-vs-deletion-applied-bg',
    legacy: '@vs-color-deletion-applied-background',
    group: TokenGroups.vsDiff,
    description: 'VS diff deletion applied background'
  },
  {
    cssVar: '--cp-color-vs-change-bg',
    legacy: '@vs-color-change-background',
    group: TokenGroups.vsDiff,
    description: 'VS diff change background'
  },
  {
    cssVar: '--cp-color-vs-change-border',
    legacy: '@vs-color-change-border',
    group: TokenGroups.vsDiff,
    description: 'VS diff change border'
  },
  {
    cssVar: '--cp-color-vs-change-applied-bg',
    legacy: '@vs-color-change-applied-background',
    group: TokenGroups.vsDiff,
    description: 'VS diff change applied background'
  },

  // -- Provider icons --------------------------------------------------------
  {
    cssVar: '--cp-asset-aws-icon',
    legacy: '@aws-icon',
    group: TokenGroups.providerIcon,
    description: 'AWS provider icon'
  },
  {
    cssVar: '--cp-asset-aws-icon-contrast',
    legacy: '@aws-icon-contrast',
    group: TokenGroups.providerIcon,
    description: 'AWS provider icon (contrast)'
  },
  {
    cssVar: '--cp-asset-gcp-icon',
    legacy: '@gcp-icon',
    group: TokenGroups.providerIcon,
    description: 'GCP provider icon'
  },
  {
    cssVar: '--cp-asset-gcp-icon-contrast',
    legacy: '@gcp-icon-contrast',
    group: TokenGroups.providerIcon,
    description: 'GCP provider icon (contrast)'
  },
  {
    cssVar: '--cp-asset-azure-icon',
    legacy: '@azure-icon',
    group: TokenGroups.providerIcon,
    description: 'Azure provider icon'
  },
  {
    cssVar: '--cp-asset-azure-icon-contrast',
    legacy: '@azure-icon-contrast',
    group: TokenGroups.providerIcon,
    description: 'Azure provider icon (contrast)'
  },
  {
    cssVar: '--cp-asset-local-icon',
    legacy: '@local-icon',
    group: TokenGroups.providerIcon,
    description: 'Local provider icon'
  },
  {
    cssVar: '--cp-asset-local-icon-contrast',
    legacy: '@local-icon-contrast',
    group: TokenGroups.providerIcon,
    description: 'Local provider icon (contrast)'
  },

  // -- Region icons ----------------------------------------------------------
  {
    cssVar: '--cp-asset-eu-region-icon',
    legacy: '@eu-region-icon',
    group: TokenGroups.regionIcon,
    description: 'EU region icon'
  },
  {
    cssVar: '--cp-asset-us-region-icon',
    legacy: '@us-region-icon',
    group: TokenGroups.regionIcon,
    description: 'US region icon'
  },
  {
    cssVar: '--cp-asset-sa-region-icon',
    legacy: '@sa-region-icon',
    group: TokenGroups.regionIcon,
    description: 'SA region icon'
  },
  {
    cssVar: '--cp-asset-cn-region-icon',
    legacy: '@cn-region-icon',
    group: TokenGroups.regionIcon,
    description: 'CN region icon'
  },
  {
    cssVar: '--cp-asset-ca-region-icon',
    legacy: '@ca-region-icon',
    group: TokenGroups.regionIcon,
    description: 'CA region icon'
  },
  {
    cssVar: '--cp-asset-ap-northeast-1-region-icon',
    legacy: '@ap-northeast-1-region-icon',
    group: TokenGroups.regionIcon,
    description: 'AP North-East 1 region icon'
  },
  {
    cssVar: '--cp-asset-ap-northeast-2-region-icon',
    legacy: '@ap-northeast-2-region-icon',
    group: TokenGroups.regionIcon,
    description: 'AP North-East 2 region icon'
  },
  {
    cssVar: '--cp-asset-ap-northeast-3-region-icon',
    legacy: '@ap-northeast-3-region-icon',
    group: TokenGroups.regionIcon,
    description: 'AP North-East 3 region icon'
  },
  {
    cssVar: '--cp-asset-ap-south-1-region-icon',
    legacy: '@ap-south-1-region-icon',
    group: TokenGroups.regionIcon,
    description: 'AP South 1 region icon'
  },
  {
    cssVar: '--cp-asset-ap-southeast-1-region-icon',
    legacy: '@ap-southeast-1-region-icon',
    group: TokenGroups.regionIcon,
    description: 'AP South-East 1 region icon'
  },
  {
    cssVar: '--cp-asset-ap-southeast-2-region-icon',
    legacy: '@ap-southeast-2-region-icon',
    group: TokenGroups.regionIcon,
    description: 'AP South-East 2 region icon'
  },
  {
    cssVar: '--cp-asset-taiwan-region-icon',
    legacy: '@taiwan-region-icon',
    group: TokenGroups.regionIcon,
    description: 'Taiwan region icon'
  },

  // -- Assets ----------------------------------------------------------------
  {
    cssVar: '--cp-asset-logo',
    legacy: '@logo-image',
    group: TokenGroups.asset,
    description: 'Application logo image'
  },
  {
    cssVar: '--cp-asset-bg-image',
    legacy: '@background-image',
    group: TokenGroups.asset,
    description: 'Application background image'
  },
  {
    cssVar: '--cp-asset-nav-bg-image',
    legacy: '@navigation-background-image',
    group: TokenGroups.asset,
    description: 'Navigation panel background image'
  }
];

/**
 * Subset of legacy keys that are recommended for custom-theme authors when
 * they extend a predefined theme. Everything else is computed from these
 * (or inherited via `extends`).
 */
export const RecommendedCustomThemeKeys = [
  '@primary-color',
  '@application-color',
  '@application-background-color',
  '@panel-background-color',
  '@card-background-color',
  '@color-success',
  '@color-error',
  '@color-warning'
];

export const SemanticTokensByCssVar = SemanticTokens.reduce((acc, token) => {
  acc[token.cssVar] = token;
  return acc;
}, {});

export const SemanticTokensByLegacy = SemanticTokens.reduce((acc, token) => {
  acc[token.legacy] = token;
  return acc;
}, {});
