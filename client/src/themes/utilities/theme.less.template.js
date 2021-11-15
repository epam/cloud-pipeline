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

export default `
@THEME .theme-preview {
  border-color: @card-border-color;
}
@THEME .theme-preview.selected {
  border-color: @primary-color;
}
@THEME .cp-themes-group {
  color: @application-color;
  background-color: @card-background-color;
  border: 1px solid @card-border-color;
}
@THEME .cp-themes-group .cp-themes-group-header {
  background-color: @card-header-background;
  border-bottom: 1px solid @card-border-color;
}
@THEME .cp-themes-group-active-tag {
  background-color: @primary-color;
  color: @primary-text-color;
}
@THEME.theme-preview .cp-theme-preview-navigation-panel {
  background-color: @navigation-panel-color;
}
@THEME.theme-preview .cp-theme-preview-layout {
  background-color: @application-background-color;
  color: @application-color;
}
@THEME.theme-preview .cp-theme-preview-panel {
  border: 1px solid @panel-border-color;
  background-color: @panel-background-color;
  color: @application-color;
}
@THEME.theme-preview .cp-theme-priview-panel-card {
  border: 1px solid @card-border-color;
  background-color: @card-background-color;
  margin: 2px;
}
@THEME.theme-preview .cp-theme-preview-button-primary {
  color: @btn-color;
  background-color: @primary-color;
  border-color: @primary-color;
}
@THEME.theme-preview .cp-theme-preview-button-danger {
  background-color: @btn-danger-color;;
  border-color: @btn-danger-color;
}
@THEME.theme-preview .cp-theme-preview-button {
  color: @primary-color;
  background-color: @panel-background-color;
}
@THEME.theme-preview .cp-theme-priview-runs-table-icon-blue {
  color: @run-icon-blue;
}
@THEME.theme-preview .cp-theme-priview-runs-table-icon-yellow {
  color: @run-icon-yellow;
}
@THEME.theme-preview .cp-theme-priview-runs-table-icon-green {
  color: @run-icon-green;
}
@THEME.theme-preview .cp-theme-preview-navigation-menu-item.selected {
  background-color: @navigation-panel-highlighted-color;
}
@THEME.theme-preview .cp-theme-preview-navigation-menu-item i {
  text-align: center;
  padding: 5px;
}
@THEME.theme-preview .cp-runs-menu-item.active {
  color: @navigation-item-runs-color;
}
@THEME.theme-preview .cp-theme-preview-navigation-menu-item,
@THEME.theme-preview .cp-runs-menu-item {
  color: @navigation-item-color;
  background-color: transparent;
  display: flex;
  justify-content: center;
  align-items: center;
}
@THEME.theme-preview .cp-theme-preview-text {
  background-color: @application-color;
}

@THEME .cp-title {
  font-weight: bold;
}
@THEME a,
@THEME a:visited,
@THEME .cp-link {
  color: @primary-color;
  cursor: pointer;
}
@THEME a:active {
  color: @primary-active-color;
}
@THEME .cp-link[disabled] {
  color: @btn-disabled-color;
}
@THEME a:hover,
@THEME a:focus,
@THEME .cp-link:hover,
@THEME .cp-link:focus {
  color: @primary-hover-color;
}
@THEME .cp-divider.top,
@THEME .cp-divider.horizontal {
  border-top: 1px solid @panel-border-color;
}
@THEME .cp-divider.bottom {
  border-bottom: 1px solid @panel-border-color;
}
@THEME .cp-divider.left,
@THEME .cp-divider.vertical {
  border-left: 1px solid @panel-border-color;
}
@THEME .cp-divider.right {
  border-right: 1px solid @panel-border-color;
}
@THEME .cp-divider.horizontal {
  width: 100%;
  height: 1px;
}
@THEME .cp-divider.vertical {
  width: 1px;
  height: 100%;
}
@THEME .cp-primary {
  color: @primary-color;
}
@THEME .cp-error,
@THEME .cp-danger {
  color: @color-red;
}
@THEME .cp-sensitive {
  color: @color-sensitive;
}
@THEME .cp-sensitive-tag {
  background-color: @color-sensitive;
  color: @btn-color;
}
@THEME .cp-versioned-storage {
  color: @primary-color;
}
@THEME .ant-layout-sider {
  background-color: @navigation-panel-color;
}
@THEME .ant-layout {
  background-color: @application-background-color;
  color: @application-color;
}
@THEME h1,
@THEME h2,
@THEME h3,
@THEME h4,
@THEME h5 {
  color: @application-color;
}
@THEME .ant-input {
  background-color: @input-background;
  border-color: @input-border;
  color: @input-color;
}
@THEME .ant-input-disabled {
  background-color: @input-background-disabled;
  color: @application-color-disabled;
}
@THEME .ant-input-group-addon,
@THEME .cp-input-group-addon {
  background-color: @input-addon;
  border-color: @input-border;
  color: @input-color;
}
@THEME .ant-input::placeholder {
  color: @input-placeholder-color;
}
@THEME .ant-input-affix-wrapper .ant-input-suffix {
  color: @input-search-icon-color;
}
@THEME .ant-input-affix-wrapper .ant-input-suffix .ant-input-search-icon:hover {
  color: @input-search-icon-hovered-color;
}
@THEME .ant-input:hover:not(.ant-input-disabled),
@THEME .ant-input-affix-wrapper:hover .ant-input:not(.ant-input-disabled) {
  border-color: @input-border-hover-color;
  box-shadow: 0 0 0 2px @input-shadow-color;
}
@THEME .ant-input-affix-wrapper:focus .ant-input:not(.ant-input-disabled),
@THEME .ant-input:focus:not(.ant-input-disabled),
@THEME .ant-input:active:not(.ant-input-disabled) {
  border-color: @input-border-hover-color;
  box-shadow: 0 0 0 2px @input-shadow-color;
}
@THEME .ant-input.cp-error {
  border-color: @color-red;
  color: @color-red;
}
@THEME .ant-input.cp-error:hover:not(.ant-input-disabled) {
  border-color: @color-red;
  box-shadow: 0 0 0 2px fade(@color-red, 20%);
}
@THEME .ant-input.cp-error:focus:not(.ant-input-disabled),
@THEME .ant-input.cp-error:active:not(.ant-input-disabled) {
  border-color: @color-red;
  box-shadow: 0 0 0 2px fade(@color-red, 20%);
}
@THEME .cp-text-not-important {
  color: @application-color-faded;
}
@THEME .ant-modal-mask {
  background-color: @modal-mask-background;
}
@THEME .ant-modal-content {
  background-color: @card-background-color;
  color: @application-color;
}
@THEME .ant-confirm-body,
@THEME .ant-confirm-body .ant-confirm-title {
  color: @application-color;
}
@THEME .ant-modal-header,
@THEME .ant-modal-footer {
  background-color: @card-background-color;
  color: @application-color;
  border-color: @card-border-color;
}
@THEME .ant-modal-title {
  color: @application-color;
}
@THEME .ant-modal-close {
  color: fadeout(@application-color, 20%);
}
@THEME .ant-modal-close:focus,
@THEME .ant-modal-close:hover {
  color: @application-color;
}
@THEME .cp-even-odd-element:nth-child(even) {
  background-color: @even-element-background;
}
@THEME .ant-tree li .ant-tree-node-content-wrapper {
  color: @application-color;
}
@THEME .ant-tree li .ant-tree-node-content-wrapper:hover {
  background-color: @table-element-hover-background-color;
  color: @table-element-hover-color;
}
@THEME .ant-tree li .ant-tree-node-content-wrapper.ant-tree-node-selected {
  background-color: @table-element-selected-background-color;
  color: @table-element-selected-color;
}
@THEME .ant-menu,
@THEME .ant-menu-submenu > .ant-menu {
  background-color: transparent;
  color: @menu-color;
  border-color: @menu-border-color;
}
@THEME .ant-menu > .ant-menu-item > a,
@THEME .ant-menu > .ant-menu-submenu > a {
  color: currentColor;
}
@THEME .ant-menu > .ant-menu-item > a:hover,
@THEME .ant-menu > .ant-menu-submenu > a:hover {
  color: @menu-active-color;
}
@THEME .ant-menu:not(.ant-menu-horizontal) > .ant-menu-item-selected {
  background-color: transparent;
}
@THEME .ant-menu > .ant-menu-item:hover,
@THEME .ant-menu > .ant-menu-submenu:hover,
@THEME .ant-menu > .ant-menu-item-active,
@THEME .ant-menu > .ant-menu-submenu-active,
@THEME .ant-menu > .ant-menu-item-open,
@THEME .ant-menu > .ant-menu-submenu-open,
@THEME .ant-menu > .ant-menu-item-selected,
@THEME .ant-menu > .ant-menu-submenu-selected,
@THEME .ant-menu-inline .ant-menu-item::after,
@THEME .ant-menu-vertical .ant-menu-item::after,
@THEME .ant-menu-submenu-title:hover,
@THEME .ant-menu-item:active,
@THEME .ant-menu-submenu-title:active {
  border-color: @menu-active-color;
  color: @menu-active-color;
  background-color: transparent;
}
@THEME .ant-tabs {
  color: @menu-color;
}
@THEME .ant-tabs .ant-tabs-bar {
  border-color: @menu-border-color;
}
@THEME .ant-tabs .ant-tabs-bar .ant-tabs-ink-bar {
  background-color: @menu-active-color;
}
@THEME .ant-tabs .ant-tabs-bar .ant-tabs-tab-active,
@THEME .ant-tabs .ant-tabs-bar .ant-tabs-tab:hover {
  color: @menu-active-color;
}
@THEME .ant-tabs.ant-tabs-card > .ant-tabs-bar .ant-tabs-tab {
  border-color: @card-border-color;
  background-color: @card-background-color;
}
@THEME .ant-tabs.ant-tabs-card > .ant-tabs-bar .ant-tabs-tab-active {
  color: @menu-active-color;
}
@THEME .ant-tabs.ant-tabs-card .ant-tabs-bar {
  margin-bottom: 0;
}
@THEME .ant-tabs.ant-tabs-card .ant-tabs-bar + .ant-tabs-content {
  padding: 16px 5px 5px;
  background-color: @card-background-color;
  border-left: 1px solid @card-border-color;
  border-right: 1px solid @card-border-color;
  border-bottom: 1px solid @card-border-color;
}
@THEME .ant-checkbox-wrapper + span {
  color: @application-color;
}
@THEME .ant-checkbox-disabled + span {
  color: @application-color-disabled;
}
@THEME .cp-ellipsis-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
@THEME .cp-icon-larger {
  font-size: larger;
}
@THEME .cp-icon-large {
  font-size: large;
}
@THEME .cp-split-panel-header {
  background-color: @card-header-background;
  border-bottom: 1px solid @card-border-color;
  border-top: 1px solid @card-border-color;
  color: @application-color;
}
@THEME .cp-split-panel {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: row;
  background-color: @application-background-color;
}
@THEME .cp-split-panel-panel {
  color: @application-color;
  background-color: @panel-background-color;
}
@THEME .cp-split-panel.vertical {
  flex-direction: column;
}
@THEME .cp-split-panel.vertical > .cp-split-panel-panel {
  padding: 2px 0;
}
@THEME .cp-split-panel.horizontal > .cp-split-panel-panel {
  padding: 0 2px;
}
@THEME .cp-split-panel-resizer {
  background-color: @application-background-color;
  height: 100%;
  width: 100%;
}
@THEME .cp-button {
  padding: 0 7px;
  border-radius: 4px;
  height: 22px;
  margin-bottom: 0;
  text-align: center;
  touch-action: manipulation;
  cursor: pointer;
  border: 1px solid transparent;
  white-space: nowrap;
  line-height: 22px;
  user-select: none;
  transition: all 0.3s cubic-bezier(0.645, 0.045, 0.355, 1);
  position: relative;
  color: currentColor;
  background-color: transparent;
  text-decoration: none;
}
@THEME .cp-button i,
@THEME .cp-button span {
  line-height: 1;
  text-decoration: none;
}
@THEME .cp-button:hover {
  text-decoration: none;
}
@THEME .ant-btn,
@THEME .cp-button {
  color: @application-color;
  background-color: @panel-background-color;
  border-color: @btn-border-color;
}
@THEME .ant-btn-clicked::after {
  border: 0 solid @primary-color;
}
@THEME .ant-btn:hover,
@THEME .ant-btn:focus,
@THEME .ant-btn:active,
@THEME .ant-btn.active,
@THEME .cp-button:hover,
@THEME .cp-button:focus,
@THEME .cp-button:active {
  color: @primary-color;
  background-color: @panel-background-color;
  border-color: @primary-color;
}
@THEME .ant-btn-primary {
  color: @btn-color;
  background-color: @primary-color;
  border-color: @primary-color;
}
@THEME .ant-btn-primary:hover,
@THEME .ant-btn-primary:focus {
  color: @btn-color;
  background-color: @btn-primary-hover;
  border-color: @btn-primary-hover;
}
@THEME .ant-btn-primary.active,
@THEME .ant-btn-primary:active,
@THEME .ant-btn-primary.ant-btn-clicked {
  color: @btn-color;
  background-color: @btn-primary-active;
  border-color: @btn-primary-active;
}
@THEME .ant-btn-danger {
  color: @btn-danger-color;
  background-color: @btn-danger-background-color;
  border-color: @btn-border-color;
}
@THEME .ant-btn-danger:focus,
@THEME .ant-btn-danger:hover {
  color: @btn-color;
  background-color: @btn-danger-color;
  border-color: @btn-danger-color;
}
@THEME .ant-btn-danger.active,
@THEME .ant-btn-danger:active {
  color: @btn-color;
  background-color: @btn-danger-active-color;
  border-color: @btn-danger-active-color;
}
@THEME .ant-btn-group .ant-btn-primary:first-child:not(:last-child) {
  border-right-color: @btn-primary-active;
}
@THEME .ant-btn-group .ant-btn-primary:last-child:not(:first-child),
@THEME .ant-btn-group .ant-btn-primary + .ant-btn-primary {
  border-left-color: @btn-primary-active;
}
@THEME .ant-btn-group .ant-btn-primary:not(:first-child):not(:last-child) {
  border-right-color: @btn-primary-active;
  border-left-color: @btn-primary-active;
}
@THEME .ant-btn.disabled,
@THEME .ant-btn.disabled.active,
@THEME .ant-btn.disabled:active,
@THEME .ant-btn.disabled:focus,
@THEME .ant-btn.disabled:hover,
@THEME .ant-btn[disabled],
@THEME .ant-btn[disabled].active,
@THEME .ant-btn[disabled]:active,
@THEME .ant-btn[disabled]:focus,
@THEME .ant-btn[disabled]:hover,
@THEME .cp-button[disabled],
@THEME .cp-button[disabled].active,
@THEME .cp-button[disabled]:active,
@THEME .cp-button[disabled]:focus,
@THEME .cp-button[disabled]:hover {
  color: @btn-disabled-color;
  background-color: @btn-disabled-background-color;
  border-color: @btn-border-color;
}
@THEME .ant-radio-button-wrapper {
  background-color: @panel-background-color;
  color: @application-color;
  border-color: @btn-border-color;
}
@THEME .ant-radio-button-wrapper:first-child {
  border-left-color: @btn-border-color;
}
@THEME .ant-radio-button-wrapper-focused,
@THEME .ant-radio-button-wrapper:hover {
  color: @primary-color;
}
@THEME .ant-radio-button-wrapper-checked {
  border-color: @primary-color;
  color: @primary-color;
  box-shadow: -1px 0 0 0 @primary-color;
}
@THEME .ant-radio-button-wrapper-checked:first-child {
  border-color: @primary-color;
  box-shadow: none !important;
}
@THEME .ant-radio-button-wrapper-checked:hover {
  border-color: @primary-hover-color;
  box-shadow: -1px 0 0 0 @primary-hover-color;
  color: @primary-hover-color;
}
@THEME .ant-radio-button-wrapper-checked:active {
  border-color: @primary-active-color;
  box-shadow: -1px 0 0 0 @primary-active-color;
  color: @primary-active-color;
}
@THEME .ant-radio-button-wrapper-checked::before {
  background-color: @primary-color !important;
  opacity: 0.1;
}
@THEME .ant-table,
@THEME .ant-table-placeholder,
@THEME tr.ant-table-expanded-row,
@THEME tr.ant-table-expanded-row:hover {
  color: @application-color;
  border-color: @table-border-color;
  background: @card-background-color;
}
@THEME .ant-table-small .ant-table-thead > tr > th {
  color: @table-head-color;
  background-color: @card-background-color;
  border-color: @table-border-color;
}
@THEME .ant-table-small .ant-table-title {
  border-bottom-color: @table-border-color;
}
@THEME .ant-table-thead > tr > th .anticon-filter:hover,
@THEME .ant-table-thead > tr > th .ant-table-filter-icon:hover,
@THEME .ant-table-column-sorter-up:hover .anticon,
@THEME .ant-table-column-sorter-down:hover .anticon {
  color: @application-color;
}
@THEME .ant-table-column-sorter-up.on .anticon-caret-up,
@THEME .ant-table-column-sorter-down.on .anticon-caret-up,
@THEME .ant-table-column-sorter-up.on .anticon-caret-down,
@THEME .ant-table-column-sorter-down.on .anticon-caret-down {
  color: @primary-color;
}
@THEME .ant-table-tbody > tr > td {
  border-color: @table-border-color;
}
@THEME .ant-table-tbody > tr:hover > td,
@THEME .cp-table-element-hover {
  background-color: @table-element-hover-background-color;
  color: @table-element-hover-color;
}
@THEME .cp-table-element-selected {
  font-weight: bold;
  background-color: @table-element-selected-background-color;
  color: @table-element-selected-color;
}
@THEME .cp-table-element-disabled {
  cursor: default;
  background-color: @card-background-color;
  color: @application-color-disabled;
}
@THEME .ant-table-row-expand-icon {
  border-color: @table-border-color;
  background: inherit;
}
@THEME .ant-checkbox-inner {
  border-color: @border-color;
  background-color: inherit;
}
@THEME .ant-checkbox-wrapper:hover .ant-checkbox-inner,
@THEME .ant-checkbox:hover .ant-checkbox-inner,
@THEME .ant-checkbox-input:focus + .ant-checkbox-inner {
  border-color: @primary-color;
}
@THEME .ant-checkbox-checked .ant-checkbox-inner,
@THEME .ant-checkbox-indeterminate .ant-checkbox-inner {
  background-color: @primary-color;
  border-color: @primary-color;
}
@THEME .ant-checkbox-checked .ant-checkbox-inner::after,
@THEME .ant-checkbox-inner::after {
  border-color: @card-background-color;
}
@THEME .ant-checkbox-checked::after {
  border-color: @primary-color;
}
@THEME .ant-breadcrumb a,
@THEME .ant-breadcrumb > span:last-child {
  color: @application-color;
}
@THEME .ant-breadcrumb a:hover {
  color: @primary-color;
}
@THEME .ant-breadcrumb-separator,
@THEME .ant-breadcrumb {
  color: @application-color;
}
@THEME .ant-calendar-picker-clear,
@THEME .ant-calendar-picker-icon,
@THEME .ant-calendar-picker-icon::after {
  color: @input-color;
  background-color: @input-background;
}
@THEME .ant-select {
  color: @input-color;
}
@THEME .ant-select-selection {
  color: @input-color;
  background-color: @input-background;
  border-color: @input-border;
}
@THEME .ant-select-selection:hover {
  border-color: @primary-color;
}
@THEME .ant-select-focused .ant-select-selection,
@THEME .ant-select-selection:focus,
@THEME .ant-select-selection:active {
  border-color: @primary-color;
  box-shadow: 0 0 0 2px @input-shadow-color;
}
@THEME .ant-select-dropdown,
@THEME .rc-menu,
@THEME .rc-dropdown-menu {
  background-color: @panel-background-color;
  color: @application-color;
  box-shadow: 0 1px 6px @card-hovered-shadow-color;
}
@THEME .rc-menu,
@THEME .rc-dropdown-menu {
  border-color: @card-border-color;
}
@THEME .rc-menu > .rc-menu-item-divider,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item-divider {
  background-color: @card-border-color;
}
@THEME .ant-select-selection__placeholder,
@THEME .ant-select-selection__clear,
@THEME .ant-select-arrow,
@THEME .ant-select-dropdown.ant-select-dropdown--multiple .ant-select-dropdown-menu-item-selected::after,
@THEME .ant-select-dropdown.ant-select-dropdown--multiple .ant-select-dropdown-menu-item-selected:hover::after {
  color: @input-placeholder-color;
  background-color: transparent;
}
@THEME .ant-select-dropdown-menu-item {
  color: @application-color;
  background-color: @panel-background-color;
}
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item {
  color: @application-color;
}
@THEME .ant-select-dropdown-menu-item.cp-danger,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item.cp-danger {
  color: @color-red;
}
@THEME .ant-select-dropdown-menu-item-active,
@THEME .ant-select-dropdown-menu-item:hover,
@THEME .rc-menu-item-active,
@THEME .rc-menu-submenu-active > .rc-menu-submenu-title,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item:hover,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item-active,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item-selected {
  color: @element-hover-color;
  background-color: @element-hover-background-color;
}
@THEME .ant-select-dropdown-menu-item-active.cp-danger:hover,
@THEME .ant-select-dropdown-menu-item.cp-danger:hover,
@THEME .rc-menu-item-active.cp-danger,
@THEME .rc-menu-submenu-active.cp-danger > .rc-menu-submenu-title,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item.cp-danger:hover,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item-active.cp-danger,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item-selected.cp-danger {
  color: @btn-color;
  background-color: @btn-danger-active-color;
}
@THEME .rc-menu-item.rc-menu-item-disabled,
@THEME .rc-menu-submenu-title.rc-menu-item-disabled,
@THEME .rc-menu-item.rc-menu-submenu-disabled,
@THEME .rc-menu-submenu-title.rc-menu-submenu-disabled,
@THEME .rc-dropdown-menu-item-disabled {
  color: @application-color-disabled;
}
@THEME .ant-select-dropdown-menu-item-selected,
@THEME .ant-select-dropdown-menu-item-selected:hover {
  color: @element-selected-color;
  background-color: @element-selected-background-color;
}
@THEME .ant-select-selection--multiple .ant-select-selection__choice {
  color: @element-selected-color;
  background-color: @element-selected-background-color;
  border-color: @element-hover-background-color;
}
@THEME .ant-select-selection--multiple .ant-select-selection__choice__remove {
  color: @application-color-faded;
}
@THEME .ant-select-selection--multiple .ant-select-selection__choice__remove:hover {
  color: @application-color;
}
@THEME .ant-select-dropdown-menu-item-group-title {
  color: @application-color-faded;
}
@THEME .ant-spin {
  color: @spinner;
}
@THEME .ant-spin-dot i {
  background-color: currentColor;
}
@THEME .ant-spin-blur::after {
  background-color: @application-background-color;
}
@THEME .ant-pagination-item,
@THEME .ant-pagination-item-link,
@THEME .ant-pagination-prev .ant-pagination-item-link,
@THEME .ant-pagination-next .ant-pagination-item-link {
  background-color: inherit;
  color: @application-color;
}
@THEME .ant-pagination-item > a {
  color: @application-color;
}
@THEME .ant-pagination-item:focus a,
@THEME .ant-pagination-item:hover a,
@THEME .ant-pagination-prev:focus .ant-pagination-item-link,
@THEME .ant-pagination-next:focus .ant-pagination-item-link,
@THEME .ant-pagination-prev:hover .ant-pagination-item-link,
@THEME .ant-pagination-next:hover .ant-pagination-item-link {
  color: @primary-color;
}
@THEME .ant-pagination-disabled.ant-pagination-prev .ant-pagination-item-link,
@THEME .ant-pagination-disabled.ant-pagination-next .ant-pagination-item-link {
  color: @application-color-disabled;
}
@THEME .ant-pagination-item-active {
  background-color: @primary-color;
}
@THEME .ant-pagination-item-active:hover,
@THEME .ant-pagination-item-active:focus {
  background-color: @primary-hover-color;
}
@THEME .ant-pagination-item.ant-pagination-item-active > a {
  color: @primary-text-color;
}
@THEME .ant-pagination-jump-prev::after,
@THEME .ant-pagination-jump-next::after {
  color: @application-color-faded;
}
@THEME .ant-pagination-jump-prev:focus::after,
@THEME .ant-pagination-jump-next:focus::after,
@THEME .ant-pagination-jump-prev:hover::after,
@THEME .ant-pagination-jump-next:hover::after {
  color: @primary-color;
}
@THEME .ant-calendar {
  background-color: @panel-background-color;
  box-shadow: 0 1px 6px @card-hovered-shadow-color;
  border-color: @panel-background-color;
  color: @application-color;
}
@THEME .ant-calendar-input {
  color: @application-color;
  background-color: @panel-background-color;
}
@THEME .ant-calendar-input-wrap,
@THEME .ant-calendar-header,
@THEME .ant-calendar-month-panel-header,
@THEME .ant-calendar-year-panel-header,
@THEME .ant-calendar-decade-panel-header {
  border-color: @input-border;
}
@THEME .ant-calendar-month-panel,
@THEME .ant-calendar-year-panel,
@THEME .ant-calendar-decade-panel {
  background: @panel-background-color;
}
@THEME .ant-calendar-date,
@THEME .ant-calendar-month-panel-month,
@THEME .ant-calendar-year-panel-year,
@THEME .ant-calendar-decade-panel-decade {
  color: @application-color;
}
@THEME .ant-calendar-date:hover {
  background-color: @primary-hover-color;
}
@THEME .ant-calendar-date:hover {
  background: @element-hover-background-color;
}
@THEME .ant-calendar-date:active {
  color: @primary-text-color;
  background: @primary-hover-color;
}
@THEME .ant-calendar-today .ant-calendar-date {
  border-color: @primary-color;
  color: @primary-color;
}
@THEME .ant-calendar-selected-day .ant-calendar-date {
  background: @primary-color;
  color: @primary-text-color;
}
@THEME .ant-calendar-selected-day .ant-calendar-date:hover {
  background: @primary-color;
}
@THEME .ant-calendar-last-month-cell .ant-calendar-date,
@THEME .ant-calendar-next-month-btn-day .ant-calendar-date {
  color: @application-color-disabled;
}
@THEME .ant-calendar-disabled-cell .ant-calendar-date {
  background-color: @btn-disabled-background-color;
  color: @application-color-disabled;
}
@THEME .ant-calendar-disabled-cell .ant-calendar-date:hover {
  background-color: @btn-disabled-background-color;
  color: @application-color-disabled;
}
@THEME .ant-calendar-footer {
  border-color: @input-border;
}
@THEME .ant-calendar-picker:hover .ant-calendar-picker-input:not(.ant-input-disabled) {
  border-color: @primary-color;
}
@THEME .ant-calendar-picker-clear {
  background-color: @panel-background-color;
  color: @application-color-faded;
}
@THEME .ant-calendar-picker-icon::after,
@THEME .ant-calendar-picker-clear:hover,
@THEME .ant-calendar-header a[role="button"],
@THEME .ant-calendar-header .ant-calendar-next-month-btn,
@THEME .ant-calendar-header .ant-calendar-next-year-btn,
@THEME a[role="button"].ant-calendar-century-select,
@THEME a[role="button"].ant-calendar-decade-select,
@THEME a[role="button"].ant-calendar-month-select,
@THEME a[role="button"].ant-calendar-year-select,
@THEME a[role="button"].ant-calendar-month-panel-century-select,
@THEME a[role="button"].ant-calendar-month-panel-decade-select,
@THEME a[role="button"].ant-calendar-month-panel-year-select,
@THEME a[role="button"].ant-calendar-month-panel-month-select,
@THEME a[role="button"].ant-calendar-year-panel-century-select,
@THEME a[role="button"].ant-calendar-year-panel-decade-select,
@THEME a[role="button"].ant-calendar-year-panel-year-select,
@THEME a[role="button"].ant-calendar-year-panel-month-select {
  color: @application-color;
}
@THEME .ant-calendar-year-panel-last-decade-cell .ant-calendar-year-panel-year,
@THEME .ant-calendar-year-panel-next-decade-cell .ant-calendar-year-panel-year,
@THEME .ant-calendar-decade-panel-last-century-cell .ant-calendar-decade-panel-decade,
@THEME .ant-calendar-decade-panel-next-century-cell .ant-calendar-decade-panel-decade {
  color: @application-color-faded;
}
@THEME .ant-calendar-month-panel-month:hover,
@THEME .ant-calendar-year-panel-year:hover,
@THEME .ant-calendar-decade-panel-decade:hover {
  background-color: @calendar-bg-hover-color;
}
@THEME .ant-calendar-header a:hover {
  color: @primary-hover-color;
}
@THEME .ant-calendar-month-panel-selected-cell .ant-calendar-month-panel-month,
@THEME .ant-calendar-month-panel-selected-cell .ant-calendar-month-panel-month:hover,
@THEME .ant-calendar-year-panel-selected-cell .ant-calendar-year-panel-year,
@THEME .ant-calendar-year-panel-selected-cell .ant-calendar-year-panel-year:hover,
@THEME .ant-calendar-decade-panel-selected-cell .ant-calendar-decade-panel-decade,
@THEME .ant-calendar-decade-panel-selected-cell .ant-calendar-decade-panel-decade:hover {
  background: @primary-color;
  color: @primary-text-color;
}
@THEME .ant-popover-placement-right > .ant-popover-content > .ant-popover-arrow {
  border-right-color: @card-border-color;
}
@THEME .ant-popover-placement-right > .ant-popover-content > .ant-popover-arrow::after {
  border-right-color: @card-background-color;
}
@THEME .ant-popover-placement-left > .ant-popover-content > .ant-popover-arrow {
  border-left-color: @card-border-color;
}
@THEME .ant-popover-placement-left > .ant-popover-content > .ant-popover-arrow::after {
  border-left-color: @card-background-color;
}
@THEME .ant-popover-placement-top > .ant-popover-content > .ant-popover-arrow,
@THEME .ant-popover-placement-topLeft > .ant-popover-content > .ant-popover-arrow,
@THEME .ant-popover-placement-topRight > .ant-popover-content > .ant-popover-arrow {
  border-top-color: @card-border-color;
}
@THEME .ant-popover-placement-top > .ant-popover-content > .ant-popover-arrow::after,
@THEME .ant-popover-placement-topLeft > .ant-popover-content > .ant-popover-arrow::after,
@THEME .ant-popover-placement-topRight > .ant-popover-content > .ant-popover-arrow::after {
  border-top-color: @card-background-color;
}
@THEME .ant-popover-placement-bottom > .ant-popover-content > .ant-popover-arrow,
@THEME .ant-popover-placement-bottomLeft > .ant-popover-content > .ant-popover-arrow,
@THEME .ant-popover-placement-bottomRight > .ant-popover-content > .ant-popover-arrow {
  border-bottom-color: @card-border-color;
}
@THEME .ant-popover-placement-bottom > .ant-popover-content > .ant-popover-arrow::after,
@THEME .ant-popover-placement-bottomLeft > .ant-popover-content > .ant-popover-arrow::after,
@THEME .ant-popover-placement-bottomRight > .ant-popover-content > .ant-popover-arrow::after {
  border-bottom-color: @card-background-color;
}
@THEME .ant-popover-inner {
  background-color: @card-background-color;
  border-color: @card-border-color;
  box-shadow: 0 1px 6px @card-hovered-shadow-color;
}
@THEME .ant-popover-inner-content {
  color: @application-color;
}
@THEME .ant-calendar .ant-calendar-ok-btn {
  color: @btn-color;
  background-color: @primary-color;
  border-color: @primary-color;
}
@THEME .ant-calendar .ant-calendar-ok-btn:focus,
@THEME .ant-calendar .ant-calendar-ok-btn:hover {
  color: @btn-color;
  background-color: @btn-primary-hover;
  border-color: @btn-primary-hover;
}
@THEME .ant-calendar .ant-calendar-ok-btn.active,
@THEME .ant-calendar .ant-calendar-ok-btn:active {
  color: @btn-color;
  background-color: @btn-primary-active;
  border-color: @btn-primary-active;
}
@THEME .ant-time-picker-input {
  color: @application-color;
  background-color: @panel-background-color;
  border-color: @border-color;
}
@THEME .ant-time-picker-icon::after {
  color: @application-color;
}
@THEME .ant-time-picker-panel-inner {
  background-color: @panel-background-color;
  box-shadow: 0 1px 6px @card-hovered-shadow-color;
}
@THEME .ant-time-picker-input:hover {
  border-color: @primary-hover-color;
}
@THEME .ant-time-picker-input:focus {
  border-color: @primary-hover-color;
  box-shadow: 0 0 0 2px @input-shadow-color;
}
@THEME .ant-time-picker-panel-input-wrap {
  border-bottom-color: @calendar-border-color;
}
@THEME .ant-time-picker-panel-input {
  background-color: @panel-background-color;
  color: @application-color;
}
@THEME .ant-time-picker-panel-clear-btn::after {
  color: @application-color-faded;
}
@THEME .ant-time-picker-panel-clear-btn:hover::after {
  color: @application-color;
}
@THEME .ant-time-picker-panel-combobox {
  color: @application-color;
}
@THEME li.ant-time-picker-panel-select-option-selected {
  background-color: @element-selected-background-color;
}
@THEME .ant-time-picker-panel-select li:hover {
  background-color: @calendar-bg-hover-color;
}
@THEME .ant-time-picker-panel-select {
  border-left-color: @calendar-border-color;
}
@THEME .ant-select-dropdown.ant-select-dropdown--multiple .ant-select-dropdown-menu-item-selected::after,
@THEME .ant-select-dropdown.ant-select-dropdown--multiple .ant-select-dropdown-menu-item-selected:hover::after {
  color: @primary-color;
}

@THEME .cp-panel {
  border: 1px solid @panel-border-color;
  background-color: @panel-background-color;
  color: @application-color;
}
@THEME .cp-panel.cp-panel-transparent {
  background-color: @panel-transparent-color;
  border-color: transparent;
}
@THEME .cp-panel.cp-panel-transparent:hover {
  box-shadow: none;
  border-color: transparent;
}
@THEME .cp-panel.cp-panel-no-hover:hover {
  box-shadow: none;
}
@THEME .cp-panel.cp-panel-borderless,
@THEME .cp-panel.cp-panel-borderless:hover {
  border-color: transparent;
}
@THEME .cp-panel .cp-panel-card {
  border: 1px solid @card-border-color;
  background-color: @card-background-color;
  margin: 2px;
}
@THEME .cp-panel .cp-panel-card.cp-card-service {
  border: 1px solid @card-service-border-color;
  background-color: @card-service-background-color;
}
@THEME .cp-panel .cp-panel-card:hover {
  box-shadow: 0 1px 6px @card-hovered-shadow-color;
  border-color: @card-hovered-shadow-color;
}
@THEME .cp-panel .cp-panel-card.cp-card-service:hover {
  box-shadow: 0 1px 6px @card-service-hovered-shadow-color;
  border-color: @card-service-hovered-shadow-color;
}
@THEME .cp-panel .cp-panel-card .cp-panel-card-title,
@THEME .cp-panel .cp-panel-card .cp-panel-card-text {
  color: @application-color;
}
@THEME .cp-panel .cp-panel-card .cp-panel-card-sub-text {
  color: @application-color-faded;
}
@THEME .cp-panel .cp-panel-card .cp-panel-card-title {
  font-weight: bold;
}
@THEME .cp-panel .cp-panel-card .cp-card-action-button {
  color: @primary-color;
}
@THEME .cp-panel .cp-panel-card .cp-card-action-button.cp-danger {
  color: @btn-danger-color;
}
@THEME .cp-panel .cp-panel-card .cp-panel-card-actions .cp-panel-card-actions-background {
  background-color: @card-background-color;
}
@THEME .cp-panel .cp-panel-card.cp-card-service .cp-panel-card-actions .cp-panel-card-actions-background {
  background-color: @card-service-background-color;
}
@THEME .cp-panel .cp-panel-card.cp-card-service .cp-panel-card-actions:hover .cp-panel-card-actions-background,
@THEME .cp-panel .cp-panel-card.cp-card-service .cp-panel-card-actions.hovered .cp-panel-card-actions-background {
  background-color: @card-service-actions-active-background;
}
@THEME .cp-panel .cp-panel-card .cp-panel-card-actions:hover .cp-panel-card-actions-background,
@THEME .cp-panel .cp-panel-card .cp-panel-card-actions.hovered .cp-panel-card-actions-background {
  background-color: @card-actions-active-background;
}
@THEME .cp-panel .cp-panel-card.cp-card-service .cp-panel-card-actions:hover .cp-panel-card-actions-background,
@THEME .cp-panel .cp-panel-card.cp-card-service .cp-panel-card-actions.hovered .cp-panel-card-actions-background {
  background-color: @card-service-actions-active-background;
}

@THEME .cp-navigation-panel {
  background-color: @navigation-panel-color;
}
@THEME .cp-navigation-panel.impersonated {
  background-color: @navigation-panel-color-impersonated;
}
@THEME .cp-navigation-panel .cp-navigation-menu-item {
  color: @navigation-item-color;
  background-color: transparent;
  text-transform: uppercase;
  border: 0 solid transparent;
  width: 100%;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: larger;
  text-decoration: none;
}
@THEME .cp-navigation-panel .cp-navigation-menu-item.selected {
  background-color: @navigation-panel-highlighted-color;
}
@THEME .cp-navigation-panel.impersonated .cp-navigation-menu-item.selected {
  background-color: @navigation-panel-highlighted-color-impersonated;
}
@THEME .cp-navigation-panel .cp-navigation-menu-item i {
  font-weight: bold;
  font-size: large;
}
@THEME .cp-navigation-panel .cp-navigation-menu-item.cp-runs-menu-item.active {
  color: @navigation-item-runs-color;
}

@THEME .cp-dashboard-sticky-panel {
  background-color: @application-background-color;
}
@THEME .cp-project-tag-key {
  background-color: @tag-key-background-color;
  border-bottom: 1px solid @tag-key-value-divider-color;
}
@THEME .cp-project-tag-value {
  background-color: @tag-value-background-color;
}
@THEME .cp-dashboard-panel-card-header {
  background-color: @card-header-background;
}
@THEME .cp-nfs-storage-type {
  color: @nfs-icon-color;
}
@THEME .cp-notification-status-info {
  color: @status-good-color;
}
@THEME .cp-notification-status-warning {
  color: @status-warning-color;
}
@THEME .cp-notification-status-critical {
  color: @status-bad-color;
}
@THEME .cp-new-notification {
  border-color: @color-blue;
  box-shadow: 0 0 1em @color-blue;
}

@THEME .provider.aws {
  background-image: @aws-icon;
}
@THEME .provider.gcp {
  background-image: @gcp-icon;
  background-color: transparent;
  box-shadow: 0 0 0 1px transparent;
}
@THEME .provider.azure {
  background-image: @azure-icon;
}
@THEME .flag.eu {
  background-image: @eu-region-icon;
}
@THEME .flag.us {
  background-image: @us-region-icon;
}
@THEME .flag.sa {
  background-image: @sa-region-icon;
}
@THEME .flag.cn {
  background-image: @cn-region-icon;
}
@THEME .flag.ca {
  background-image: @ca-region-icon;
}
@THEME .flag.ap.ap-northeast-1 {
  background-image: @ap-northeast-1-region-icon;
}
@THEME .flag.ap.ap-northeast-2 {
  background-image: @ap-northeast-2-region-icon;
}
@THEME .flag.ap.ap-northeast-3 {
  background-image: @ap-northeast-3-region-icon;
}
@THEME .flag.ap.ap-south-1 {
  background-image: @ap-south-1-region-icon;
}
@THEME .flag.ap.ap-southeast-1 {
  background-image: @ap-southeast-1-region-icon;
}
@THEME .flag.ap.ap-southeast-2 {
  background-image: @ap-southeast-2-region-icon;
}
@THEME .flag.taiwan {
  background-image: @taiwan-region-icon;
}

@THEME @fn: @theme-transition-function;
@ms: @theme-transition-duration;

.cp-theme-transition-background {
  transition: background-color @fn @ms;
}
@THEME .cp-theme-transition-color {
  transition: color @fn @ms;
}
@THEME .cp-theme-transition {
  transition: color @fn @ms, background-color @fn @ms;
}
@THEME .ant-layout-sider,
@THEME .ant-layout,
@THEME .ant-input,
@THEME .cp-panel,
@THEME .cp-panel-card,
@THEME .cp-panel-card-actions-background,
@THEME .cp-navigation-panel,
@THEME .cp-navigation-panel .cp-navigation-menu-item {
  .cp-theme-transition();
}

@THEME .ant-alert {
  color: @application-color;
}
@THEME .ant-alert.ant-alert-success {
  background-color: @alert-success-background;
  border-color: @alert-success-border;
}
@THEME .ant-alert.ant-alert-info {
  background-color: @alert-info-background;
  border-color: @alert-info-border;
}
@THEME .ant-alert.ant-alert-error {
  background-color: @alert-error-background;
  border-color: @alert-error-border;
}
@THEME .ant-alert.ant-alert-warning {
  background-color: @alert-warning-background;
  border-color: @alert-warning-border;
}
@THEME .ant-alert.ant-alert-success .ant-alert-icon,
@THEME .ant-message .ant-message-success .anticon {
  color: @alert-success-icon;
}
@THEME .ant-alert.ant-alert-info .ant-alert-icon,
@THEME .ant-message .ant-message-info .anticon {
  color: @alert-info-icon;
}
@THEME .ant-alert.ant-alert-error .ant-alert-icon,
@THEME .ant-message .ant-message-error .anticon {
  color: @alert-error-icon;
}
@THEME .ant-alert.ant-alert-warning .ant-alert-icon,
@THEME .ant-message .ant-message-warning .anticon {
  color: @alert-warning-icon;
}
@THEME .ant-message .ant-message-loading .anticon {
  color: @alert-info-icon;
}
@THEME .ant-message-notice .ant-message-notice-content {
  background-color: @card-background-color;
  color: @application-color;
  box-shadow: 0 2px 8px @card-hovered-shadow-color;
}

@THEME .cp-tool-header {
  border-color: @menu-border-color;
}
@THEME .cp-tool-panel + .cp-tool-panel {
  margin-left: 10px;
}
@THEME .cp-tool-panel .cp-tool-panel-header {
  padding: 10px;
  background-color: @card-header-background;
  border: 1px solid @card-border-color;
  border-radius: 5px 5px 0 0;
}
@THEME .cp-tool-panel .cp-tool-panel-body {
  margin-top: -1px;
  padding: 10px;
  border: 1px solid @card-border-color;
  background-color: @card-background-color;
  border-radius: 0 0 5px 5px;
}
@THEME .cp-tool-no-description {
  color: @application-color-faded;
}
@THEME .cp-tool-icon-container {
  background-color: @application-background-color;
  color: @application-color;
}

@THEME .cp-runs-table-service-url-run {
  background-color: @card-service-background-color;
}
@THEME .cp-runs-table-icon-green {
  color: @run-icon-green;
}
@THEME .cp-runs-table-icon-blue {
  color: @run-icon-blue;
}
@THEME .cp-runs-table-icon-red {
  color: @run-icon-red;
}
@THEME .cp-runs-table-icon-yellow {
  color: @run-icon-yellow;
}
@THEME .cp-filter-popover-container {
  background-color: @card-background-color;
  box-shadow: 0 1px 6px @card-hovered-shadow-color;
}
@THEME .cp-runs-day-picker .DayPicker-Weekday {
  color: @application-color-faded;
}
@THEME .cp-runs-day-picker .DayPicker-Weekday abbr[title] {
  text-decoration: none;
}
@THEME .cp-runs-day-picker .DayPicker-Day--outside {
  color: @application-color-faded;
}
@THEME .cp-runs-day-picker .DayPicker-Day {
  border-color: @date-picker-day-border-color;
}
@THEME .cp-runs-day-picker .DayPicker-Day:hover {
  color: @primary-color;
}
@THEME .cp-runs-day-picker .DayPicker-Day--today {
  color: @primary-active-color;
}
@THEME .DayPicker-Day--selected:not(.DayPicker-Day--disabled):not(.DayPicker-Day--outside) {
  background-color: @primary-color;
  color: @primary-text-color;
}
@THEME .DayPicker-Day--selected:not(.DayPicker-Day--disabled):not(.DayPicker-Day--outside):hover {
  background-color: @primary-active-color;
  color: @primary-text-color;
  outline: none;
}

@THEME .cp-billing-menu {
  width: fit-content;
  margin-left: 0;
  min-height: 100%;
}
@THEME .cp-billing-menu .ant-menu-submenu-title,
@THEME .cp-billing-menu .ant-menu-item,
@THEME .cp-billing-menu .cp-billing-sub-menu .ant-menu-item {
  height: 32px;
  line-height: 32px;
}
@THEME .cp-billing-menu .cp-billing-sub-menu .ant-menu-submenu-title {
  position: relative;
  left: 1px;
  margin-left: -1px;
  z-index: 1;
  height: 32px;
  line-height: 32px;
}
@THEME .cp-billing-menu .cp-billing-sub-menu.ant-menu-submenu-open .ant-menu-submenu-title {
  background-color: transparent;
  color: @menu-color;
}
@THEME .cp-billing-menu .cp-billing-sub-menu.cp-billing-sub-menu-selected .ant-menu-submenu-title {
  background-color: transparent;
  color: @menu-active-color;
}
@THEME .cp-billing-menu .cp-billing-sub-menu .ant-menu-submenu-title::after {
  content: "";
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  border-right: 3px solid @menu-active-color;
  transform: scaleY(0.0001);
  opacity: 0;
  transition: transform 0.15s cubic-bezier(0.215, 0.61, 0.355, 1), opacity 0.15s cubic-bezier(0.215, 0.61, 0.355, 1);
}
@THEME .cp-billing-menu .cp-billing-sub-menu.cp-billing-sub-menu-selected .ant-menu-submenu-title::after {
  transition: transform 0.15s cubic-bezier(0.645, 0.045, 0.355, 1), opacity 0.15s cubic-bezier(0.645, 0.045, 0.355, 1);
  opacity: 1;
  transform: scaleY(1);
}
@THEME .cp-billing-table td {
  border: 1px solid @panel-border-color;
}
@THEME .cp-billing-table td.cp-billing-table-pending {
  background-color: @card-background-color;
}
@THEME .cp-billing-calendar-container {
  background-color: @panel-background-color;
  box-shadow: 0 0 2px 2px @cp-calendar-shadow-color;
  color: @application-color;
}
@THEME .cp-billing-calendar-years-container {
  border-bottom: 1px solid @panel-border-color;
}
@THEME .cp-billing-calendar-navigation:hover {
  color: @primary-color;
}
@THEME .cp-billing-calendar-navigation.disabled {
  color: @application-color-disabled;
}
@THEME .cp-billing-calendar-row-item {
  border: 1px solid @cp-calendar-border-color;
}
@THEME .cp-billing-calendar-row-item.selected {
  color: @primary-color;
  background-color: @panel-background-color;
}
@THEME .cp-billing-calendar-row-item:hover {
  color: @primary-text-color;
  background-color: @primary-color;
}
@THEME .cp-billing-calendar-row-item.disabled {
  color: @application-color-disabled;
  background-color: @panel-background-color;
}
@THEME .cp-billing-calendar-set-value {
  color: @application-color;
}
@THEME .cp-billing-calendar-set-value-close {
  color: @application-color-faded;
}
@THEME .cp-billing-calendar-icon {
  color: @application-color;
}
@THEME .cp-billing-divider {
  border-left: 1px solid @border-color;
}
@THEME .cp-billing-button-link:hover {
  color: @primary-color;
}
@THEME .cp-billing-layout-panel-move {
  background-color: @even-element-background;
}
@THEME .cp-billing-layout .react-resizable-handle::after {
  border-right-color: @application-color;
  border-bottom-color: @application-color;
}



@THEME .cp-search-clear-filters-button {
  background: @primary-color;
  color: @primary-text-color;
}
@THEME .cp-search-actions > * {
  border-right: 1px solid @panel-border-color;
}
@THEME .cp-search-clear-button {
  color: @application-color-faded;
}
@THEME .cp-search-clear-button:hover {
  color: @application-color;
}
@THEME .cp-search-filter .cp-search-filter-header {
  border-bottom: 1px dashed transparent;
}
@THEME .cp-search-filter .cp-search-filter-header:hover {
  background-color: @card-header-background;
}
@THEME .cp-search-filter .cp-search-filter-header.cp-search-filter-header-expanded {
  border-bottom: 1px dashed @panel-border-color;
}
@THEME .cp-search-filter .cp-search-filter-header .cp-search-filter-header-caret {
  color: @application-color;
}
@THEME .cp-search-filter .cp-search-filter-header.cp-search-filter-header-expanded .cp-search-filter-header-caret {
  transform: rotate(90deg);
}
@THEME .cp-search-results-table-header {
  background-color: @card-background-color;
  color: @application-color;
  position: sticky;
  top: 0;
  font-weight: bold;
}
@THEME .cp-search-results-table-header-cell {
  margin: 0;
  padding: 5px;
  user-select: none;
  cursor: default;
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  border-bottom: 1px solid transparent;
}
@THEME .cp-search-results-table-cell,
@THEME .cp-search-results-table-divider,
@THEME .cp-search-results-table-header .cp-search-results-table-header-cell {
  background-color: @card-background-color;
}
@THEME .cp-search-results-table-header .cp-search-results-table-header-cell {
  border-color: @card-border-color;
}
@THEME .cp-search-result-list-item {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
}
@THEME .cp-search-results-table-cell {
  margin: 0;
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
}
@THEME .cp-search-results-table-divider {
  height: 100%;
  user-select: none;
  width: 4px;
}
@THEME .cp-search-results-table-header .cp-search-results-table-divider {
  cursor: col-resize;
  border-left: 1px solid @card-border-color;
}
@THEME .cp-search-results-table-header .cp-search-results-table-divider:not(:last-child) {
  border-bottom: 1px solid @card-border-color;
}
@THEME .cp-search-results-table-header .cp-search-results-table-divider:hover,
@THEME .cp-search-results-table-divider.active {
  border-left: 1px solid @primary-color;
}
@THEME .cp-search-result-item {
  color: @application-color;
}
@THEME .cp-search-result-item-main {
  color: currentColor;
}
@THEME .cp-search-result-table-item {
  cursor: pointer;
  color: inherit;
  text-decoration: none !important;
  display: grid;
  transition: background 0.2s ease;
}
@THEME .cp-search-result-item:not(.disabled):hover .cp-search-result-item-main {
  color: @primary-color;
}
@THEME .cp-search-result-item-sub {
  color: @application-color-faded;
}
@THEME .cp-search-result-item.disabled,
@THEME .cp-search-result-item.disabled .cp-search-result-item-main,
@THEME .cp-search-result-item.disabled .cp-search-result-item-sub {
  color: @application-color-disabled;
}
@THEME .cp-search-result-item-actions {
  display: inline-flex;
  align-items: center;
  flex-wrap: nowrap;
  align-self: stretch;
}
@THEME .cp-search-result-item-action {
  margin-right: 0;
  padding: 0 8px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
@THEME .cp-search-result-item:not(.disabled) .cp-search-result-item-action:hover {
  background-color: @primary-color;
  color: @primary-text-color;
}
@THEME .cp-search-result-item-tag .cp-search-result-item-tag-key,
@THEME .cp-search-result-item-tag .cp-search-result-item-tag-value {
  border: 1px solid @card-border-color;
}
@THEME .cp-search-result-item-tag .cp-search-result-item-tag-key {
  background-color: @card-header-background;
}
@THEME .cp-search-result-item-tag .cp-search-result-item-tag-value {
  background-color: @card-background-color;
}
@THEME .cp-search-preview-wrapper {
  background: @input-placeholder-color;
}
@THEME .cp-search-preview-light {
  background-color: @panel-background-color;
  border: 1px solid @panel-border-color;
}
@THEME .cp-search-preview {
  background-color: @search-preview-background-color;
}
@THEME .cp-search-preview-button {
  color: @application-color;
}
@THEME .cp-search-preview-button:hover {
  color: @application-color-accent;
}
@THEME .cp-search-preview-error {
  color: @color-red; //#ff556b;
}
@THEME .cp-search-preview-error-light-mode {
  color: @color-pink; //#ff0000;
}
@THEME .cp-search-first-row {
  color: @search-first-row-color;   //'#999'
}
@THEME .cp-search-container {
  color: @card-border-color;
}
@THEME .cp-search-container-light {
  color: @application-color;
}
@THEME .cp-search-header-title {
  color: @application-color;
  
}
@THEME .cp-search-header-description {
  color: @panel-border-color; //#ccc;
}
@THEME .cp-search-header-image {
  background-color: @application-color-faded; // rgba(255, 255, 255, 0.05);
}
@THEME .cp-search-container .cp-search-header-title,
@THEME .cp-search-container .cp-search-header-description,
@THEME .cp-search-container .cp-search-content {
  color: @search-preview-text-color;
}
@THEME .cp-search-container-light .cp-search-header-title,
@THEME .cp-search-container-light .cp-search-header-description,
@THEME .cp-search-container-light .cp-search-content {
  color:  @application-color;
 
}
@THEME .cp-search-container .cp-search-content-preview .cp-search-content-preview-run,
@THEME .cp-search-container .cp-search-content-preview .cp-search-content-preview-run-task,
@THEME .cp-search-content-preview .cp-search-content-preview-run,
@THEME .cp-search-content-preview .cp-search-content-preview-run-task {
  color: @search-preview-text-color;
}
@THEME .cp-search-container-light .cp-search-content-preview .cp-search-content-preview-run,
@THEME .cp-search-container-light .cp-search-content-preview .cp-search-content-preview-run-task,
@THEME .cp-search-content-preview .cp-search-content-preview-run,
@THEME .cp-search-content-preview .cp-search-content-preview-run-task {
  color:  @application-color;
}
@THEME .cp-search-preview-iframe {
  background-color:  @search-preview-text-color;
}
@THEME .cp-search-description-tag {
  background-color: @application-color-faded; // rgba(255, 255, 255, 0.1);
  color: @application-background-color;
}
@THEME .cp-search-container .cp-search-attribute-name {
  background-color: @card-service-header-background; // rgba(0, 0, 0, 0.1);
}
@THEME .cp-search-container .cp-search-attribute-value {
  background-color: @card-border-color; // rgba(0, 0, 0, 0.05);
}
@THEME .cp-search-container .cp-search-attribute-value:hover {
  background-color: @application-color-disabled; // rgba(0, 0, 0, 0.15);
}
@THEME .cp-search-container-light .cp-search-description-tag {
  background-color: @code-background-color; //rgba(0, 0, 0, 0.1);
  color: @application-color;
}
@THEME .cp-search-container-light .cp-search-attribute-name {
  background-color: @search-preview-attributes-name; //rgba(255, 255, 255, 0.1);
}
@THEME .cp-search-container-light .cp-search-attribute-value {
  background-color: @search-preview-attributes-value; //rgba(255, 255, 255, 0.05);
}
@THEME .cp-search-container-light .cp-search-attribute-value:hover {
  background-color: @search-preview-attributes-value-hovered; //rgba(255, 255, 255, 0.75);
  color: @application-color;
}
@THEME .cp-search-even-table-row {
  background-color: @application-background-color; // rgba(255, 255, 255, 0.05);
}
@THEME .cp-search-highlight {
  background-color: @application-background-color; // rgba(255, 255, 255, 0.1);
  color: @panel-border-color; // #ccc;
}
@THEME .cp-search-highlight-text {
  background-color: @alert-warning-border; // rgb(249, 255, 0);
  color: @application-color; // black;
}
@THEME .cp-search-container-light .cp-search-highlight {
  background-color: transparent;
  color: @search-header-light-color; //#555;
}
@THEME .cp-search-csv-table {
  border: 1px solid @application-color-accent; //#666;
  background-color: @application-background-color; //rgba(255, 255, 255, 0.05);
}
@THEME .cp-search-container-light .cp-search-csv-table {
  background-color: @application-color; //rgba(0, 0, 0, 0.05);
}
@THEME .cp-versioned-storage-breadcrumb {
  cursor: pointer;
  transition: all 0.2s ease;
  line-height: 20px;
  padding: 5px;
  margin: 0 -5px;
}
@THEME .cp-versioned-storage-breadcrumb:not(.last):hover {
  color: @primary-color;
}
@THEME .cp-versioned-storage-breadcrumb.last {
  cursor: default;
}
@THEME .cp-versioned-storage-table-header {
  border-color: @card-border-color;
  background-color: @card-header-background;
  color: @application-color;
}

@THEME .cp-library-metadata-item-key {
  background-color: @tag-key-background-color;
  border-bottom: 1px solid @tag-key-value-divider-color;
}
@THEME .cp-library-metadata-item-value {
  background-color: @tag-value-background-color;
}
@THEME .cp-library-metadata-additional-actions {
  background-color: inherit;
}

@THEME .cp-node-tag {
  border-color: @application-color-disabled;
  color: @application-color;
}
@THEME .cp-node-tag.cp-node-tag-run,
@THEME .cp-node-tag.cp-node-tag-pool,
@THEME .cp-node-tag.cp-node-tag-master,
@THEME .cp-node-tag.cp-node-tag-cp-role,
@THEME .cp-node-tag.cp-node-tag-pipeline-info {
  border-color: currentColor;
}
@THEME .cp-node-tag.cp-node-tag-run {
  color: @color-green;
  font-weight: bold;
}
@THEME .cp-node-tag.cp-node-tag-run:hover {
  background-color: @color-green;
  border-color: @color-green;
  color: @primary-text-color;
}
@THEME .cp-node-tag.cp-node-tag-pipeline-info {
  color: @application-color-faded;
}
@THEME .cp-node-tag.cp-node-tag-pool {
  color: @color-violet;
}
@THEME .cp-node-tag.cp-node-tag-master,
@THEME .cp-node-tag.cp-node-tag-cp-role {
  color: @primary-color;
}

@THEME .cp-settings-sidebar-element {
  cursor: pointer;
}
@THEME .cp-settings-sidebar-element td {
  padding: 6px;
}

@THEME .text-highlight {
  color: @application-color;
  background-color: @code-background-color;
  border: 1px solid @card-border-color;
  border-radius: 4px;
}
@THEME .text-highlight code {
  color: @color-pink;
  background-color: @alert-error-background;
  box-shadow: inset 0 -1px 0 rgba(0, 0, 0, 0.25);
}
@THEME .hljs {
  color: @application-color;
  background: @card-background-color;
}
@THEME .hljs-comment,
@THEME .hljs-quote {
  color: @application-color-faded;
}
@THEME .hljs-keyword,
@THEME .hljs-selector-tag,
@THEME .hljs-subst {
  color: @application-color;
}
@THEME .hljs-number,
@THEME .hljs-literal,
@THEME .hljs-variable,
@THEME .hljs-template-variable,
@THEME .hljs-tag .hljs-attr {
  color: @color-aqua;
}
@THEME .hljs-string,
@THEME .hljs-doctag {
  color: @color-pink;
}
@THEME .hljs-title,
@THEME .hljs-section,
@THEME .hljs-selector-id {
  color: @color-red;
}
@THEME .hljs-type,
@THEME .hljs-class .hljs-title {
  color: @color-blue-dimmed;
}
@THEME .hljs-tag,
@THEME .hljs-name,
@THEME .hljs-attribute {
  color: @primary-color;
  font-weight: normal;
}
@THEME .hljs-regexp,
@THEME .hljs-link {
  color: @color-green;
}
@THEME .hljs-symbol,
@THEME .hljs-bullet {
  color: @color-violet;
}
@THEME .hljs-built_in,
@THEME .hljs-builtin-name {
  color: @primary-color;
}
@THEME .hljs-meta {
  color: @application-color-faded;
  font-weight: bold;
}
@THEME .hljs-deletion {
  background: @alert-error-background;
}
@THEME .hljs-addition {
  background: @alert-success-background;
}

`;
