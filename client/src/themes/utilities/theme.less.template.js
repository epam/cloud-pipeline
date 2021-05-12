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
@THEME .theme-preview:not(.read-only):hover {
  border-color: @primary-color;
  box-shadow: 0 0 2px 2px fade(@primary-color, 20%);
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
  background-image: @background-image;
  background-size: cover;
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
  color: @primary-text-color;
  background-color: @primary-color;
  border-color: @primary-color;
}
@THEME.theme-preview .cp-theme-preview-button-danger {
  background-color: @btn-danger-active-background;
  border-color: @btn-danger-active-background;
}
@THEME.theme-preview .cp-theme-preview-button {
  color: @primary-color;
  background-color: @panel-background-color;
}
@THEME.theme-preview .cp-theme-priview-runs-table-icon-blue {
  color: @color-info;
}
@THEME.theme-preview .cp-theme-priview-runs-table-icon-yellow {
  color: @color-warning;
}
@THEME.theme-preview .cp-theme-priview-runs-table-icon-green {
  color: @color-success;
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
@THEME .theme-preview-bordered {
  border: 1px solid @application-color;
}
@THEME .color-presenter {
  stroke: @application-color;
}
@THEME .image-uploader {
  border-color: @application-color;
  background-color: @application-background-color;
}
@THEME .image-uploader .uploader-thumb-image {
  background-color: fade(@application-background-color, 25%);
}

@THEME body[class*="dark"] {
  color-scheme: dark;
}
@THEME body[class*="light"] {
  color-scheme: light;
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
@THEME .cp-text,
@THEME a.cp-text,
@THEME a.cp-text:visited {
  color: @application-color;
}
@THEME a:hover,
@THEME a:focus,
@THEME .cp-link:hover,
@THEME .cp-link:focus {
  color: @primary-hover-color;
}
@THEME a.cp-text:hover,
@THEME a.cp-text:focus,
@THEME a.cp-text:active {
  color: @application-color-accent;
}
@THEME a.underline {
  text-decoration: underline;
}
@THEME a.cp-danger:hover,
@THEME a.cp-danger:focus,
@THEME .cp-link.cp-danger:hover,
@THEME .cp-link.cp-danger:focus {
  color: lighten(@color-red, 5%);
}
@THEME .cp-bordered {
  border: 1px solid @panel-border-color;
}
@THEME .cp-outline-bordered {
  outline: 1px solid @panel-border-color;
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
@THEME .cp-divider.top.dashed,
@THEME .cp-divider.horizontal.dashed {
  border-top: 1px dashed @panel-border-color;
}
@THEME .cp-divider.bottom.dashed {
  border-bottom: 1px dashed @panel-border-color;
}
@THEME .cp-divider.left.dashed,
@THEME .cp-divider.vertical.dashed {
  border-left: 1px dashed @panel-border-color;
}
@THEME .cp-divider.right.dashed {
  border-right: 1px dashed @panel-border-color;
}
@THEME .cp-divider.horizontal {
  width: 100%;
  height: 1px;
  background-color: transparent;
}
@THEME .cp-divider.vertical {
  width: 1px;
  height: 100%;
  background-color: transparent;
}
@THEME .cp-divider.inline {
  background: @panel-border-color;
}
@THEME .cp-primary {
  color: @primary-color;
}
@THEME .cp-primary.border {
  border-color: @primary-color;
}
@THEME .cp-disabled {
  color: @application-color-disabled;
}
@THEME .cp-accent {
  color: @application-color-accent;
}
@THEME .cp-warning {
  color: @color-yellow;
}
@THEME .cp-warning.border {
  border-color: @color-yellow;
}
@THEME .cp-success {
  color: @color-green;
}
@THEME .cp-success.border {
  border-color: @color-green;
}
@THEME .cp-error,
@THEME .cp-danger {
  color: @color-red;
}
@THEME .cp-error.border,
@THEME .cp-danger.border {
  border-color: currentColor;
}
@THEME .cp-sensitive {
  color: @color-sensitive;
}
@THEME .cp-sensitive-tag {
  background-color: @color-sensitive;
  color: @btn-danger-active-color;
}
@THEME .cp-sensitive-tag.bordered {
  background-color: transparent;
  color: @color-sensitive;
  border: 1px solid currentColor;
}
@THEME .cp-tag {
  background-color: transparent;
  border: 1px solid @card-border-color;
  color: @application-color;
}
@THEME .cp-tag.accent {
  border: 1px solid @application-color;
}
@THEME .cp-tag.filled,
@THEME .cp-tag.link:hover {
  background-color: @application-color;
  border: 1px solid @application-color;
  color: @application-background-color;
}
@THEME .cp-tag.disabled {
  color: @application-color-disabled;
}
@THEME .cp-tag.filled.disabled,
@THEME .cp-tag.link.disabled:hover {
  background-color: @application-color-disabled;
  border: 1px solid @application-color-disabled;
}
@THEME .cp-tag.warning {
  border-color: currentColor;
  color: @color-yellow;
}
@THEME .cp-tag.warning.filled,
@THEME .cp-tag.warning.link:hover {
  background-color: @color-yellow;
  border-color: @color-yellow;
  color: @primary-text-color;
}
@THEME .cp-tag.critical {
  border-color: currentColor;
  color: @color-red;
}
@THEME .cp-tag.critical.filled,
@THEME .cp-tag.critical.link:hover {
  background-color: @color-red;
  border-color: @color-red;
  color: @primary-text-color;
}
@THEME .cp-tag.success {
  border-color: currentColor;
  color: @color-green;
}
@THEME .cp-tag.success.filled,
@THEME .cp-tag.success.link:hover {
  background-color: @color-green;
  border-color: @color-green;
  color: @primary-text-color;
}
@THEME .cp-tag.cp-primary,
@THEME .cp-tag.primary {
  border-color: currentColor;
  color: @primary-color;
}
@THEME .cp-tag.primary.filled,
@THEME .cp-tag.cp-primary.filled,
@THEME .cp-tag.primary.link:hover,
@THEME .cp-tag.cp-primary.link:hover {
  background-color: @primary-color;
  border-color: @primary-color;
  color: @primary-text-color;
}
@THEME .cp-versioned-storage {
  color: @primary-color;
}
@THEME .cp-icon-button {
  cursor: pointer;
  color: @application-color;
  pointer-events: all;
  transition: all 100ms ease;
}
@THEME .cp-icon-button:not(.cp-disabled):hover {
  color: @application-color-accent;
  transform: scale(1.15);
}
@THEME .cp-icon-button.cp-disabled {
  cursor: default;
  color: @application-color-disabled;
}
@THEME .cp-icon-button:hover {
  color: @application-color-accent;
}
@THEME .ant-layout-sider {
  background-color: @navigation-panel-color;
  background-image: @navigation-background-image;
  background-size: cover;
}
@THEME .ant-layout {
  background-color: @application-background-color;
  color: @application-color;
  background-image: @background-image;
  background-size: cover;
}
@THEME .app-background {
  background-color: @application-background-color;
  color: @application-color;
}
@THEME .app-background:not(.no-image) {
  background-image: @background-image;
  background-size: cover;
}
@THEME h1,
@THEME h2,
@THEME h3,
@THEME h4,
@THEME h5,
@THEME h6 {
  color: @application-color;
}
@THEME .ant-input,
@THEME .ant-input-number,
@THEME .ant-input-number-input,
@THEME .ant-input-number-handler-wrap,
@THEME .cp-limit-mounts-input,
@THEME .cp-run-capabilities-input,
@THEME .ant-upload {
  background-color: @input-background;
  border-color: @input-border;
  color: @input-color;
}
@THEME .transparent-upload .ant-upload {
  background-color: transparent;
  border-color: transparent;
}
@THEME .chrome-picker input {
  background-color: @input-background !important;
  border-color: @input-border !important;
  color: @input-color !important;
}
@THEME .ant-input-number-handler:active {
  color: @primary-text-color;
  background-color: @primary-color;
}
@THEME .ant-input-number-handler-wrap .ant-input-number-handler-down {
  border-color: @input-border;
}
@THEME .ant-input-number-handler-wrap .ant-input-number-handler,
@THEME .ant-input-number-handler-up-inner,
@THEME .ant-input-number-handler-down-inner {
  color: @input-color;
}
@THEME .ant-input-number-handler:hover .ant-input-number-handler-up-inner,
@THEME .ant-input-number-handler:hover .ant-input-number-handler-down-inner {
  color: @input-border-hover-color;
}
@THEME .ant-input-number-handler-down-disabled .ant-input-number-handler-down-inner,
@THEME .ant-input-number-handler-up-disabled .ant-input-number-handler-down-inner,
@THEME .ant-input-number-disabled .ant-input-number-handler-down-inner,
@THEME .ant-input-number-handler-down-disabled .ant-input-number-handler-up-inner,
@THEME .ant-input-number-handler-up-disabled .ant-input-number-handler-up-inner,
@THEME .ant-input-number-disabled .ant-input-number-handler-up-inner {
  color: @application-color-disabled !important;
}
@THEME .ant-input-disabled,
@THEME .ant-input-number-disabled,
@THEME .ant-input-number-disabled .ant-input-number-handler-wrap,
@THEME .ant-input-number-input[disabled],
@THEME .cp-limit-mounts-input.disabled,
@THEME .cp-run-capabilities-input.disabled {
  background-color: @input-background-disabled;
  color: @application-color-disabled;
}
@THEME .ant-input-group-addon,
@THEME .cp-input-group-addon {
  background-color: @input-addon;
  border-color: @input-border;
  color: @input-color;
}
@THEME .ant-input::placeholder,
@THEME .ant-time-picker-input::placeholder {
  color: @input-placeholder-color;
}
@THEME .ant-input-affix-wrapper .ant-input-suffix {
  color: @input-search-icon-color;
}
@THEME .ant-input-affix-wrapper .ant-input-suffix .ant-input-search-icon:hover {
  color: @input-search-icon-hovered-color;
}
@THEME .ant-input:hover:not(.ant-input-disabled),
@THEME .ant-input-affix-wrapper:hover .ant-input:not(.ant-input-disabled),
@THEME .ant-input-number:hover:not(.ant-input-number-disabled),
@THEME .cp-run-capabilities-input:hover,
@THEME .cp-limit-mounts-input:not(.disabled):hover {
  border-color: @input-border-hover-color;
  box-shadow: 0 0 0 2px @input-shadow-color;
}
@THEME .ant-input-affix-wrapper:focus .ant-input:not(.ant-input-disabled),
@THEME .ant-input:focus:not(.ant-input-disabled),
@THEME .ant-input:active:not(.ant-input-disabled),
@THEME .ant-input-number:focus:not(.ant-input-number-disabled),
@THEME .ant-input-number.ant-input-number-focused:not(.ant-input-number-disabled),
@THEME .ant-input-number:active:not(.ant-input-number-disabled) {
  border-color: @input-border-hover-color;
  box-shadow: 0 0 0 2px @input-shadow-color;
}
@THEME .cp-error .ant-input,
@THEME .ant-input.cp-error,
@THEME .ant-select.cp-error .ant-select-selection,
@THEME .ant-input-number.cp-error,
@THEME .cp-run-capabilities-input.cp-error {
  border-color: @color-red;
  color: @color-red;
  box-shadow: none;
}
@THEME .cp-error .ant-input:hover:not(.ant-input-disabled),
@THEME .ant-input.cp-error:hover:not(.ant-input-disabled),
@THEME .ant-input-number.cp-error:hover:not(.ant-input-disabled),
@THEME .ant-select.cp-error:not(.ant-select-disabled) .ant-select-selection:hover {
  border-color: @color-red;
  box-shadow: 0 0 0 2px fade(@color-red, 20%);
}
@THEME .cp-error .ant-input:focus:not(.ant-input-disabled),
@THEME .cp-error .ant-input:active:not(.ant-input-disabled),
@THEME .ant-input.cp-error:focus:not(.ant-input-disabled),
@THEME .ant-input.cp-error:active:not(.ant-input-disabled),
@THEME .ant-input-number.cp-error:focus:not(.ant-input-disabled),
@THEME .ant-input-number.cp-error:active:not(.ant-input-disabled),
@THEME .ant-input-number.cp-error.ant-input-number-focused:not(.ant-input-number-disabled),
@THEME .ant-select:not(.ant-select-disabled) .ant-select-selection.cp-error:focus,
@THEME .ant-select:not(.ant-select-disabled) .ant-select-selection.cp-error:active {
  border-color: @color-red;
  box-shadow: 0 0 0 2px fade(@color-red, 20%);
}
@THEME .ant-form-item-control.has-error .ant-select-selection,
@THEME .ant-form-item-control.has-error .ant-input,
@THEME .ant-form-item-control.has-error .CodeMirror-wrap {
  border-color: @color-red;
}
@THEME .cp-text-not-important {
  color: @application-color-faded;
}
@THEME .cp-text-not-important-after:after {
  color: @application-color-faded;
}
@THEME .ant-modal-mask {
  background-color: @modal-mask-background;
}
@THEME .ant-modal-content {
  background-color: fade(@card-background-color, 100%);
  color: @application-color;
}
@THEME .ant-confirm-body,
@THEME .ant-confirm-body .ant-confirm-title,
@THEME .ant-confirm-body .ant-confirm-content {
  color: @application-color;
}
@THEME .ant-modal-header,
@THEME .ant-modal-footer {
  background-color: @card-background-color;
  color: @application-color;
  border-color: @card-border-color;
}
@THEME .ant-table-footer:before {
  background-color: @card-background-color;
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
@THEME .cp-even-odd-element:nth-child(even):not(.cp-table-element-selected) {
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
  color: @application-color;
  border-color: @panel-border-color;
}
@THEME .ant-menu > .ant-menu-item-divider {
  background-color: @panel-border-color;
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
  color: @application-color;
}
@THEME .ant-tabs .ant-tabs-bar {
  border-color: @panel-border-color;
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
@THEME .ant-tabs.cp-tabs-no-padding.ant-tabs-card .ant-tabs-bar + .ant-tabs-content {
  padding: 0;
}
@THEME .ant-tabs-tab-prev,
@THEME .ant-tabs-tab-next {
  color: @application-color;
}
@THEME .ant-tabs-tab-btn-disabled,
@THEME .ant-tabs-tab-btn-disabled:hover {
  color: @application-color-disabled;
}
@THEME .cp-tabs-no-content.ant-tabs.ant-tabs-card .ant-tabs-bar + .ant-tabs-content {
  border-bottom: 0;
}
@THEME .cp-tabs-content {
  background-color: @card-background-color;
  border-left: 1px solid @card-border-color;
  border-right: 1px solid @card-border-color;
  border-bottom: 1px solid @card-border-color;
}
@THEME .cp-tabs-no-padding .ant-tabs-bar {
  margin-bottom: 0;
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
  background-color: transparent;
}
@THEME .cp-transparent-background {
  background-color: transparent !important;
}
@THEME .cp-split-panel-panel {
  color: @application-color;
  background-color: transparent;
}
@THEME .cp-split-panel-background .cp-split-panel-panel {
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
@THEME .ant-modal-content .cp-split-panel,
@THEME .ant-modal-content .cp-split-panel-panel {
  background-color: @card-background-color;
}
@THEME .ant-modal-content .cp-split-panel-resizer {
  background-color: @panel-background-color;
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
  border-color: @input-border;
}
@THEME .cp-button.primary {
  background: @primary-color;
  color: @primary-text-color;
  border-color: @primary-color;
}
@THEME .cp-button.primary:hover {
  color: @primary-text-color;
  background-color: @primary-hover-color;
  border-color: @primary-hover-color;
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
  color: @primary-text-color;
  background-color: @primary-color;
  border-color: @primary-color;
}
@THEME .ant-btn-primary:hover,
@THEME .ant-btn-primary:focus {
  color: @primary-text-color;
  background-color: @primary-hover-color;
  border-color: @primary-hover-color;
}
@THEME .ant-btn-primary.active,
@THEME .ant-btn-primary:active,
@THEME .ant-btn-primary.ant-btn-clicked {
  color: @primary-text-color;
  background-color: @primary-active-color;
  border-color: @primary-active-color;
}
@THEME .ant-btn-background-ghost.ant-btn-primary {
  color: @primary-color;
  background-color: transparent;
  border-color: @primary-color;
}
@THEME .ant-btn-danger {
  color: @btn-danger-color;
  background-color: @btn-danger-background-color;
  border-color: @input-border;
}
@THEME .ant-btn-danger:focus,
@THEME .ant-btn-danger:hover,
@THEME .ant-btn-danger.active,
@THEME .ant-btn-danger:active {
  color: @btn-danger-active-color;
  background-color: @btn-danger-active-background;
  border-color: @btn-danger-active-background;
}
@THEME .ant-btn-group .ant-btn-primary:first-child:not(:last-child) {
  border-right-color: @primary-active-color;
}
@THEME .ant-btn-group .ant-btn-primary:last-child:not(:first-child),
@THEME .ant-btn-group .ant-btn-primary + .ant-btn-primary {
  border-left-color: @primary-active-color;
}
@THEME .ant-btn-group .ant-btn-primary:not(:first-child):not(:last-child) {
  border-right-color: @primary-active-color;
  border-left-color: @primary-active-color;
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
  border-color: @input-border;
}
@THEME .ant-radio-button-wrapper,
@THEME .ant-radio-inner {
  background-color: transparent;
  color: @application-color;
  border-color: @input-border;
}
@THEME .ant-radio-button-wrapper:first-child {
  border-left-color: @input-border;
}
@THEME .ant-radio-button-wrapper:not(:first-child)::before {
  background-color: @input-border;
}
@THEME .ant-radio-button-wrapper-focused,
@THEME .ant-radio-button-wrapper:hover,
@THEME .ant-radio-wrapper:hover .ant-radio .ant-radio-inner,
@THEME .ant-radio:hover .ant-radio-inner,
@THEME .ant-radio-focused .ant-radio-inner {
  color: @primary-color;
  border-color: @primary-color;
}
@THEME .ant-radio-inner::after {
  background-color: @primary-color;
}
@THEME .ant-radio-button-wrapper-checked {
  border-color: @primary-color;
  color: @primary-color;
  box-shadow: -1px 0 0 0 @primary-color;
}
@THEME .ant-radio-checked .ant-radio-inner {
  border-color: @primary-color;
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
@THEME .ant-radio-disabled .ant-radio-inner {
  border-color: @input-border !important;
  background-color: @input-background-disabled;
}
@THEME .ant-radio-disabled .ant-radio-inner::after {
  background-color: @input-border;
}
@THEME .ant-radio-disabled + span {
  color: @application-color-faded;
}
@THEME .ant-table,
@THEME .ant-table-placeholder,
@THEME .ant-table-footer,
@THEME tr.ant-table-expanded-row,
@THEME tr.ant-table-expanded-row:hover {
  color: @application-color;
  border-color: @table-border-color;
  background: @card-background-color;
}
@THEME .cp-table-cell {
  color: @application-color;
  border-color: darken(@card-border-color, 5%);
  background: @card-background-color;
}
@THEME .cp-table-cell.readonly-cell {
  color: @application-color;
  border-color: darken(@card-border-color, 5%);
  background: @card-header-background;
}
@THEME .ant-table-thead > tr > th {
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
@THEME .ant-table-row-hover,
@THEME .ant-table-row-hover > .cp-table-cell {
  color: @table-element-hover-color;
  background-color: @table-element-hover-background-color;
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
@THEME .cp-table-element {
  color: @application-color;
}
@THEME .ant-table-tbody > tr:hover > td,
@THEME .cp-table-element-hover,
@THEME .cp-table-element:hover {
  background-color: @table-element-hover-background-color;
  color: @table-element-hover-color;
}
@THEME .cp-table-element-selected {
  font-weight: bold;
  background-color: @table-element-selected-background-color;
  color: @table-element-selected-color;
}
@THEME .cp-table-element-selected.cp-table-element-selected-no-bold {
  font-weight: normal;
}
@THEME .cp-table-element-disabled,
@THEME .cp-table-element[disabled] {
  cursor: default;
  background-color: @card-background-color;
  color: @application-color-disabled;
}
@THEME .cp-table-element-dimmed {
  background-color: @card-background-color;
  color: @application-color-disabled;
}
@THEME .cp-table-element-dimmed:hover {
  background-color: @table-element-hover-background-color;
}
@THEME .ant-table-row-expand-icon {
  border-color: @table-border-color;
  background: inherit;
}
@THEME .ant-checkbox-inner {
  border-color: @input-border;
  background-color: inherit;
}
@THEME .ant-checkbox-disabled .ant-checkbox-inner {
  border-color: @input-border !important;
}
@THEME .ant-checkbox-disabled .ant-checkbox-inner,
@THEME .ant-checkbox-disabled.ant-checkbox-checked .ant-checkbox-inner {
  background-color: @input-background-disabled;
}
@THEME .ant-checkbox-wrapper:hover .ant-checkbox-inner,
@THEME .ant-checkbox:not(.ant-checkbox-disabled):hover .ant-checkbox-inner,
@THEME .ant-checkbox-input:focus + .ant-checkbox-inner {
  border-color: @input-border-hover-color;
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
@THEME .ant-checkbox-disabled.ant-checkbox-checked .ant-checkbox-inner::after {
  border-color: @application-color-disabled;
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
@THEME .ant-calendar-time-picker-inner {
  background-color: @panel-background-color;
  color: @application-color;
}
@THEME .ant-calendar-time-picker-select {
  border-color: @panel-border-color;
}
@THEME li.ant-calendar-time-picker-select-option-selected {
  background-color: @element-selected-background-color;
  color: @element-selected-color;
}
@THEME .ant-calendar-time-picker-select li:hover {
  background-color: @element-hover-background-color;
  color: @element-hover-color;
}
@THEME .ant-select {
  color: @input-color;
}
@THEME .ant-select.ant-select-disabled {
  color: @application-color-disabled;
}
@THEME .ant-select-selection {
  color: @input-color;
  background-color: @input-background;
  border-color: @input-border;
}
@THEME .ant-select.ant-select-disabled .ant-select-selection {
  color: @application-color-disabled;
  background-color: @input-background-disabled;
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
@THEME .ant-select-selection:hover .ant-select-selection__clear ~ .ant-select-arrow {
  opacity: 0;
}
@THEME .ant-dropdown,
@THEME .ant-select-dropdown,
@THEME .ant-dropdown-menu,
@THEME .rc-menu,
@THEME .rc-dropdown-menu,
@THEME .ant-mention-dropdown {
  background-color: fade(@panel-background-color, 100%);
  color: @application-color;
  box-shadow: 0 1px 6px @card-hovered-shadow-color;
}
@THEME .rc-menu-item-group-title {
  border-bottom: 1px solid @card-border-color;
}
@THEME .rc-menu,
@THEME .rc-dropdown-menu {
  border-color: @panel-border-color;
}
@THEME .rc-menu > .rc-menu-item-divider,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item-divider,
@THEME .ant-dropdown-menu > .ant-dropdown-menu-item-divider {
  background-color: @panel-border-color;
}
@THEME .ant-select-selection__placeholder,
@THEME .ant-select-selection__clear,
@THEME .ant-select-arrow,
@THEME .ant-select-dropdown.ant-select-dropdown--multiple .ant-select-dropdown-menu-item-selected::after,
@THEME .ant-select-dropdown.ant-select-dropdown--multiple .ant-select-dropdown-menu-item-selected:hover::after {
  color: @input-placeholder-color;
  background-color: transparent;
}
@THEME .ant-select-dropdown-menu-item,
@THEME .ant-mention-dropdown-item {
  color: @application-color;
  background-color: @panel-background-color;
}
@THEME .ant-mention-dropdown-item-hover,
@THEME .ant-mention-dropdown-item.focus,
@THEME .ant-mention-dropdown-item-active {
  color: @element-hover-color;
  background-color: @element-hover-background-color;
}
@THEME .ant-mention-dropdown-item-disabled,
@THEME .ant-mention-dropdown-item-disabled:hover {
  color: @application-color-disabled;
}
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item,
@THEME .ant-dropdown-menu > .ant-dropdown-menu-item {
  color: @application-color;
}
@THEME .ant-select-dropdown-menu-item.cp-danger,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item.cp-danger,
@THEME .ant-dropdown-menu > .ant-dropdown-menu-item.cp-danger {
  color: @color-red;
}
@THEME .ant-select-dropdown-menu-item-active,
@THEME .ant-select-dropdown-menu-item:hover,
@THEME .rc-menu-item-active,
@THEME .rc-menu-submenu-active > .rc-menu-submenu-title,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item:hover,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item-active,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item-selected,
@THEME .ant-dropdown-menu > .ant-dropdown-menu-item:hover,
@THEME .ant-dropdown-menu > .ant-dropdown-menu-item-active,
@THEME .ant-dropdown-menu > .ant-dropdown-menu-item-selected {
  color: @element-hover-color;
  background-color: @element-hover-background-color;
}
@THEME .ant-select-dropdown-menu-item-active.cp-danger:hover,
@THEME .ant-select-dropdown-menu-item.cp-danger:hover,
@THEME .rc-menu-item-active.cp-danger,
@THEME .rc-menu-submenu-active.cp-danger > .rc-menu-submenu-title,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item.cp-danger:hover,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item-active.cp-danger,
@THEME .rc-dropdown-menu > .rc-dropdown-menu-item-selected.cp-danger,
@THEME .ant-dropdown-menu > .ant-dropdown-menu-item.cp-danger:hover,
@THEME .ant-dropdown-menu > .ant-dropdown-menu-item-active.cp-danger,
@THEME .ant-dropdown-menu > .ant-dropdown-menu-item-selected.cp-danger {
  color: @btn-danger-active-color;
  background-color: @btn-danger-active-background;
}
@THEME .rc-menu-item.rc-menu-item-disabled,
@THEME .rc-menu-submenu-title.rc-menu-item-disabled,
@THEME .rc-menu-item.rc-menu-submenu-disabled,
@THEME .rc-menu-submenu-title.rc-menu-submenu-disabled,
@THEME .rc-dropdown-menu-item-disabled,
@THEME .ant-dropdown-menu-item-disabled {
  color: @application-color-disabled;
}
@THEME .rc-menu-item.rc-menu-item-selected {
  background-color: fade(@panel-background-color, 100%);
}
@THEME .rc-menu-item.rc-menu-item-selected:hover {
  background-color: @element-hover-background-color;
}
@THEME .ant-select-dropdown-menu-item-selected,
@THEME .ant-select-dropdown-menu-item-selected:hover {
  color: @element-selected-color;
  background-color: @element-selected-background-color;
}
@THEME .ant-select-selection--multiple .ant-select-selection__choice,
@THEME .cp-limit-mounts-input .cp-limit-mounts-input-tag,
@THEME .cp-run-capabilities-input .cp-run-capabilities-input-tag {
  color: @element-selected-color;
  background-color: @element-selected-background-color;
  border-color: @element-hover-background-color;
}
@THEME .cp-run-capabilities-input .cp-run-capabilities-input-tag.tag-placeholder {
  color: @application-color-disabled;
}
@THEME .cp-run-capabilities-input .cp-run-capabilities-input-tag.required {
  color: @color-red;
}
@THEME .ant-select-selection--multiple .ant-select-selection__choice__remove {
  color: @application-color-faded;
}
@THEME .ant-select-selection--multiple .ant-select-selection__choice__remove:hover {
  color: @application-color;
}
@THEME .ant-select-disabled .ant-select-selection--multiple .ant-select-selection__choice {
  color: currentColor;
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
@THEME .ant-pagination-item.ant-pagination-item-active > a,
@THEME span.ant-pagination-item.ant-pagination-item-active {
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
  background-color: fade(@panel-background-color, 100%);
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
  background-color: @element-hover-background-color;
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
@THEME .ant-popover-placement-right > .ant-popover-content > .ant-popover-arrow,
@THEME .ant-popover-placement-rightTop > .ant-popover-content > .ant-popover-arrow,
@THEME .ant-popover-placement-rightBottom > .ant-popover-content > .ant-popover-arrow {
  border-right-color: @card-border-color;
}
@THEME .ant-popover-placement-right > .ant-popover-content > .ant-popover-arrow::after,
@THEME .ant-popover-placement-rightTop > .ant-popover-content > .ant-popover-arrow::after,
@THEME .ant-popover-placement-rightBottom > .ant-popover-content > .ant-popover-arrow::after {
  border-right-color: @card-background-color;
}
@THEME .ant-popover-placement-left > .ant-popover-content > .ant-popover-arrow,
@THEME .ant-popover-placement-leftTop > .ant-popover-content > .ant-popover-arrow,
@THEME .ant-popover-placement-leftBottom > .ant-popover-content > .ant-popover-arrow {
  border-left-color: @card-border-color;
}
@THEME .ant-popover-placement-left > .ant-popover-content > .ant-popover-arrow::after,
@THEME .ant-popover-placement-leftTop > .ant-popover-content > .ant-popover-arrow::after,
@THEME .ant-popover-placement-leftBottom > .ant-popover-content > .ant-popover-arrow::after {
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
  background-color: fade(@card-background-color, 100%);
  border-color: @card-border-color;
  box-shadow: 0 1px 6px @card-hovered-shadow-color;
}
@THEME .ant-popover-inner-content {
  color: @application-color;
}
@THEME .ant-popover-title {
  border-bottom: 1px solid @panel-border-color;
  color: @application-color;
}
@THEME .ant-row.cp-metadata-dropdown-row {
  color: @application-color;
}
@THEME .ant-calendar .ant-calendar-ok-btn {
  color: @primary-text-color;
  background-color: @primary-color;
  border-color: @primary-color;
}
@THEME .ant-calendar .ant-calendar-ok-btn:focus,
@THEME .ant-calendar .ant-calendar-ok-btn:hover {
  color: @primary-text-color;
  background-color: @primary-hover-color;
  border-color: @primary-hover-color;
}
@THEME .ant-calendar .ant-calendar-ok-btn.active,
@THEME .ant-calendar .ant-calendar-ok-btn:active {
  color: @primary-text-color;
  background-color: @primary-active-color;
  border-color: @primary-active-color;
}
@THEME .ant-time-picker-input {
  color: @application-color;
  background-color: @panel-background-color;
  border-color: @input-border;
}
@THEME .cp-error .ant-time-picker-input {
  color: @color-red;
  border-color: @color-red;
}
@THEME .ant-time-picker-icon::after {
  color: @application-color;
}
@THEME .cp-error .ant-time-picker-icon::after {
  color: @color-red;
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
@THEME .cp-error .ant-time-picker-input:hover {
  border-color: @color-red;
}
@THEME .cp-error .ant-time-picker-input:focus {
  border-color: @color-red;
  box-shadow: 0 0 0 2px fade(@color-red, 20%);
}
@THEME .ant-time-picker-panel-input-wrap {
  border-bottom-color: @card-border-color;
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
  background-color: @element-hover-background-color;
}
@THEME .ant-time-picker-panel-select {
  border-left-color: @card-border-color;
}
@THEME .ant-select-dropdown.ant-select-dropdown--multiple .ant-select-dropdown-menu-item-selected::after,
@THEME .ant-select-dropdown.ant-select-dropdown--multiple .ant-select-dropdown-menu-item-selected:hover::after {
  color: @primary-color;
}
@THEME .ant-collapse {
  background-color: darken(@card-background-color, 3%);
  color: @application-color;
  border-color: @card-border-color;
}
@THEME .ant-collapse.ant-collapse-borderless {
  background-color: transparent;
}
@THEME .ant-collapse-content {
  background-color: @card-background-color;
  color: @application-color;
  border-color: @card-border-color;
}
@THEME .ant-collapse > .ant-collapse-item {
  border-color: @card-border-color;
}
@THEME .ant-collapse > .ant-collapse-item > .ant-collapse-header,
@THEME .ant-collapse > .ant-collapse-item > .ant-collapse-header .arrow {
  color: @application-color;
}
@THEME .ant-collapse > .ant-collapse-item:not(.ant-collapse-item-disabled) > .ant-collapse-header:active {
  background-color: @card-background-color;
}
@THEME .ant-collapse .ant-collapse-item-disabled > .ant-collapse-header,
@THEME .ant-collapse .ant-collapse-item-disabled > .ant-collapse-header > .arrow {
  color: @application-color-disabled;
  background-color: @card-background-color;
}
@THEME .ant-collapse-borderless > .ant-collapse-item > .ant-collapse-header:hover {
  background-color: darken(@card-background-color, 5%);
}
@THEME .ant-collapse-borderless > .ant-collapse-item > .ant-collapse-content {
  border-color: @card-border-color;
}
@THEME .ant-form-item {
  color: @application-color;
}
@THEME .ReactTable.-striped .rt-tr.-even {
  background-color: @card-background-color;
}
@THEME .ReactTable.-striped .rt-tr.-odd {
  background-color: @even-element-background;
}
@THEME .ReactTable.-highlight .rt-tbody .rt-tr:not(.-padRow):hover {
  background-color: @element-selected-background-color;
  color: @element-selected-color;
}
@THEME .ant-form-item-label label {
  color: @application-color-accent;
}
@THEME .ant-form-item-required::before {
  color: @color-red;
}
@THEME .ant-slider-rail {
  background-color: @card-header-background;
}
@THEME .ant-progress-circle-trail {
  stroke: @card-header-background;
}
@THEME .ant-slider-disabled .ant-slider-track {
  background-color: @application-color-disabled !important;
}
@THEME .ant-slider-dot {
  border-color: @card-header-background;
  background-color: @card-background-color;
}
@THEME .ant-slider-disabled .ant-slider-handle,
@THEME .ant-slider-disabled .ant-slider-dot {
  border-color: @application-color-disabled !important;
}
@THEME .ant-slider:hover .ant-slider-rail {
  background-color: @card-header-background;
}
@THEME .ant-slider-track {
  background-color: @primary-color;
}
@THEME .ant-progress-circle-path {
  stroke: @primary-color !important;
}
@THEME .ant-progress-status-success .ant-progress-circle-path {
  stroke: @color-green !important;
}
@THEME .ant-slider:hover .ant-slider-track {
  background-color: @primary-hover-color;
}
@THEME .ant-slider-handle {
  border-color: @primary-color;
  background-color: @card-background-color;
}
@THEME .ant-slider:hover .ant-slider-handle {
  border-color: @primary-hover-color;
}
@THEME .cp-notification {
  background-color: @card-background-color;
  color: @application-color;
  box-shadow: 0 1px 6px @card-hovered-shadow-color;
  border-color: @card-border-color;
}
@THEME .ant-mention-wrapper .ant-mention-editor {
  background-color: @input-background;
  border-color: @input-border;
  color: @input-color;
}
@THEME .ant-mention-wrapper .public-DraftEditorPlaceholder-root .public-DraftEditorPlaceholder-inner {
  color: @input-placeholder-color;
}
@THEME .cp-mention .ant-mention-editor,
@THEME .cp-mention .ant-mention-wrapper,
@THEME .cp-mention.ant-mention-active .ant-mention-editor,
@THEME .cp-mention.ant-mention-active .ant-mention-wrapper {
  box-shadow: none;
  border: none;
}
@THEME .ant-mention-dropdown {
  background-color: red;
}
@THEME .cp-overlay {
  background-color: @modal-mask-background;
}
@THEME .cp-close-button {
  color: @application-color-faded;
}
@THEME .cp-close-button:hover {
  color: @application-color;
}

@THEME .cp-panel {
  border: 1px solid @panel-border-color;
  background-color: @panel-background-color;
  color: @application-color;
}
@THEME .cp-panel.cp-panel-transparent {
  background-color: transparent;
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
@THEME .cp-card-background-color {
  background-color: @card-background-color;
}
@THEME .cp-panel .cp-panel-card {
  border: 1px solid @card-border-color;
  background-color: @card-background-color;
  margin: 2px;
}
@THEME .cp-panel .cp-panel-card.borderless {
  border: none;
}
@THEME .cp-panel .cp-panel-card.cp-launch-vs-tool {
  margin: 2px 0;
}
@THEME .cp-panel .cp-panel-card.cp-hot-node-pool {
  margin: 10px 0;
}
@THEME .cp-panel .cp-panel-card.cp-card-service {
  border: 1px solid @card-service-border-color;
  background-color: @card-service-background-color;
}
@THEME .cp-panel .cp-panel-card.cp-card-service.borderless {
  border: none;
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
@THEME .cp-panel .cp-panel-card .cp-card-action-button.cp-disabled {
  color: @btn-disabled-color;
  cursor: not-allowed;
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
@THEME .cp-panel .cp-panel-card.cp-card-no-hover:hover,
@THEME .cp-panel .cp-panel-card.cp-card-service.cp-card-no-hover:hover {
  box-shadow: none;
}
@THEME .cp-content-panel {
  background-color: @card-background-color;
  border-color: @card-border-color;
  color: @application-color;
}
@THEME .cp-content-panel-header {
  background-color: @card-header-background;
  border-color: @card-border-color;
  color: @application-color;
}
@THEME .cp-resizable-panel-anchor {
  background-color: @card-header-background;
}
@THEME .cp-card-border {
  border: 1px solid @card-border-color;
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
@THEME .cp-navigation-panel .cp-divider.cp-navigation-divider {
  background-color: @navigation-item-color;
  opacity: 0.5;
  margin: 0 4px;
  width: calc(100% - 8px);
  border: none;
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
@THEME .cp-navigation-item-logo {
  height: 26px;
  width: 26px;
  display: block;
  background-image: @logo-image;
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
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
@THEME .cp-dashboard-panel-card-header,
@THEME .cp-panel-card .cp-dashboard-panel-card-header {
  background-color: @card-header-background;
}
@THEME .cp-panel-card.cp-card-service .cp-dashboard-panel-card-header {
  background-color: @card-service-header-background;
}
@THEME .cp-nfs-storage-type {
  color: @nfs-icon-color;
}
@THEME .cp-notification-status-info {
  color: @color-green;
}
@THEME .cp-notification-status-warning {
  color: @color-warning;
}
@THEME .cp-notification-status-critical {
  color: @color-error;
}
@THEME .cp-new-notification {
  border-color: @color-info;
  box-shadow: 0 0 1em @color-info;
}

@THEME .provider.aws {
  background-image: @aws-icon;
  background-color: transparent;
}
@THEME .ant-tooltip-inner .provider.aws {
  background-image: @aws-icon-contrast;
}
@THEME .ant-tooltip-inner .provider.gcp {
  background-image: @gcp-icon-contrast;
}
@THEME .ant-tooltip-inner .provider.azure {
  background-image: @azure-icon-contrast;
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
  border-color: @panel-border-color;
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
@THEME .cp-tool-panel .cp-tool-panel-body.no-padding {
  padding: 0;
}
@THEME .cp-tool-no-description {
  color: @application-color-faded;
}
@THEME .cp-tool-icon-container {
  background-color: @application-background-color;
  color: @application-color;
}
@THEME .cp-tool-add-endpoint {
  background-color: @input-background-disabled;
}
@THEME .cp-scan-result.critical {
  color: @color-red;
}
@THEME .cp-scan-result.high {
  color: @color-sensitive;
}
@THEME .cp-scan-result.medium {
  color: @color-yellow;
}
@THEME .cp-scan-result.low {
  color: @color-aqua-light;
}
@THEME .cp-scan-result.negligible {
  color: @color-grey;
}
@THEME .cp-scan-result.bar {
  background-color: currentColor;
}
@THEME .cp-exec-env-summary {
  border: 1px solid @input-border;
  background-color: @input-background-disabled;
  box-shadow: 0 0 3px @card-border-color;
  color: @application-color;
}
@THEME .cp-exec-env-summary-item {
  border: 1px solid @card-header-background;
}
@THEME .cp-edit-permissions-selected-row {
  background-color: @element-selected-background-color;
}
@THEME .cp-tool-white-listed-version > td,
@THEME .cp-tool-white-listed-version:hover > td {
  background-color: fade(@color-success, 15%);
}
@THEME .cp-tool-select-option-divider::before,
@THEME .cp-divider.tool-settings {
  background: @panel-border-color;
}

@THEME .cp-runs-table-service-url-run {
  background-color: @card-service-background-color;
}
@THEME .cp-runs-table-icon-green {
  color: @color-success;
}
@THEME .cp-runs-table-icon-blue {
  color: @color-info;
}
@THEME .cp-runs-table-icon-red {
  color: @color-error;
}
@THEME .cp-runs-table-icon-yellow {
  color: @color-warning;
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
  border-color: fadeout(@card-border-color, 20%);
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
@THEME .cp-runs-advanced-filter-input {
  background-color: @input-background;
  border: 1px solid @input-border;
}
@THEME .cp-runs-advanced-filter-input-error {
  background-color: @input-background;
  border: 1px solid @color-red;
}
@THEME .cp-run-instance-tag {
  background-color: @card-header-background;
  border: 1px solid @card-border-color;
}
@THEME .cp-run-nested-run-link {
  color: @application-color-accent;
}
@THEME .cp-wdl-task[data-type=VisualStep] rect,
@THEME .cp-wdl-task[data-type=VisualGroup] rect,
@THEME .cp-wdl-task[data-type=VisualWorkflow] rect {
  fill: @card-background-color;
}
@THEME .cp-wdl-task[data-taskstatus=running] rect {
  fill: fade(@color-info, 20%);
  stroke: @color-info;
}
@THEME .cp-wdl-task[data-taskstatus=running] text {
  fill: @color-info;
}
@THEME .cp-wdl-task[data-taskstatus=success] rect {
  fill: fade(@color-success, 20%);
  stroke: @color-success;
  opacity: 1;
}
@THEME .cp-wdl-task[data-taskstatus=success] text {
  fill: @color-success;
}
@THEME .cp-wdl-task[data-taskstatus=stopped] rect {
  fill: @card-background-color;
  stroke: @application-color-faded;
  opacity: 1;
}
@THEME .cp-wdl-task[data-taskstatus=stopped] text {
  fill: @application-color-faded;
}
@THEME .cp-wdl-task[data-taskstatus=failure] rect {
  fill: fade(@color-error, 20%);
  stroke: @color-error;
  opacity: 1;
}
@THEME .cp-wdl-task[data-taskstatus=failure] text {
  fill: @color-error;
}
@THEME .cp-stop-run-modal-confirm-icon {
  color: @color-yellow;
}
@THEME .cp-maintenance-rule-deleted {
  background-color: fade(@color-error, 10%);
}
@THEME .ant-input.cp-system-parameter-name-input {
  color: @application-color-accent;
  border: none;
  background-color: transparent;
}
@THEME .ant-input.cp-parameter-name.disabled {
  border-color: transparent;
  background-color: transparent;
  color: @application-color;
}
@THEME .cp-runs-autocomplete-menu.ant-menu {
  background-color: fade(@panel-background-color, 100%);
  border: 1px solid @panel-border-color;
}
@THEME .cp-runs-autocomplete-menu-item {
  border-bottom: 1px solid @input-border;
}
@THEME .cp-runs-autocomplete-menu.ant-menu > .ant-menu-item:hover,
@THEME .cp-runs-autocomplete-menu.ant-menu > .ant-menu-item-active,
@THEME .cp-runs-autocomplete-menu.ant-menu > .ant-menu-item-open,
@THEME .cp-runs-autocomplete-menu.ant-menu > .ant-menu-item-selected,
@THEME .cp-runs-autocomplete-menu.ant-menu-item:active {
  background-color: @element-hover-background-color;
  border: @menu-active-color;
  color: @application-color;
}
@THEME .cp-run-name.editable {
  outline: 1px solid transparent;
  padding: 0 2px;
}
@THEME .cp-run-name.editable:hover {
  outline: 1px solid @panel-border-color;
  background-color: @element-selected-background-color !important;
}
@THEME .cp-run-timeline-table td,
@THEME .cp-run-timeline-table th {
  background-color: @panel-background-color;
  color: @application-color;
  border: 1px solid @panel-border-color;
}
@THEME .cp-console-output {
  background-color: @application-console-background-color;
  color: @application-console-color;
}
@THEME .cp-console-output-details {
  color: @application-console-color-details
}
@THEME .cp-console-follow-log {
  color: @application-console-color;
}
@THEME .cp-console-scroll-down-indicator {
  background-color: @panel-background-color;
  border: 1px solid @panel-border-color;
  color: @application-color;
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
  color: @application-color;
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
  background-color: fade(@panel-background-color, 100%);
  box-shadow: 0 0 2px 2px @card-hovered-shadow-color;
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
  border-right: 1px solid @card-border-color;
  border-bottom: 1px solid @card-border-color;
}
@THEME .cp-billing-calendar-row-item:last-child {
  border-right: none;
}
@THEME .cp-billing-calendar-row:last-child .cp-billing-calendar-row-item {
  border-bottom: none;
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
@THEME .cp-billing-layout .react-grid-item {
  transition: none;
}
@THEME .cp-quota-status-green {
  fill: @color-success;
  background-color: @color-success;
}
@THEME .cp-quota-status-green.hide {
  fill: transparent;
  background-color: transparent;
}
@THEME .cp-quota-status-yellow {
  fill: @color-warning;
  background-color: @color-warning;
}
@THEME .cp-quota-status-red {
  fill: @color-error;
  background-color: @color-error;
}
@THEME .cp-billing-action-tag .ant-tag-text {
  background-color: @color-warning;
}
@THEME .cp-report-table td,
@THEME .cp-report-table th {
  background-color: @panel-background-color;
  color: @application-color;
  border-bottom: 1px solid @card-border-color;
}
@THEME .cp-report-table tbody tr:not(:last-child) > *,
@THEME .cp-report-table th {
  border-bottom: 1px solid @card-border-color;
}
@THEME .cp-report-table tr > *.fixed-column {
  border-right: 1px solid @card-border-color;
}
@THEME .cp-report-table tr.cp-warning-row {
  background-color: fade(@color-error, 20%);
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
@THEME .cp-search-result-item,
@THEME a.cp-search-result-item {
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
@THEME a.cp-search-result-item:not(.disabled):hover,
@THEME .cp-search-result-item:not(.disabled):hover .cp-search-result-item-main {
  color: @primary-color;
}
@THEME .cp-search-result-item-sub {
  color: @application-color-faded;
}
@THEME a.cp-search-result-item.disabled,
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
  background: @modal-mask-background;
}
@THEME .cp-search-preview {
  background-color: fade(@panel-background-color, 100%);
  border: 1px solid fade(@panel-border-color, 100%);
}
@THEME .cp-search-preview-button {
  color: @application-color;
}
@THEME .cp-search-preview-button:hover {
  color: @application-color-accent;
}
@THEME .cp-search-preview-error {
  color: @color-red;
}
@THEME .cp-search-comment-header {
  color: @application-color-faded;
}
@THEME .cp-search-header-image {
  background-color: transparent;
}
@THEME .cp-search-header-title,
@THEME .cp-search-header-sub-title,
@THEME .cp-search-header-description,
@THEME .cp-search-container .cp-search-content {
  color: @application-color;
}
@THEME .cp-search-content-preview-run,
@THEME .cp-search-content-preview-run i,
@THEME .cp-search-content-preview-run-task {
  color: @application-color;
}
@THEME .cp-search-description-tag {
  background-color: darken(@card-background-color, 5%);
  color: @application-color;
}
@THEME .cp-search-attribute-name {
  background-color: @tag-key-background-color;
  border-bottom: 1px solid @tag-key-value-divider-color;
}
@THEME .cp-search-attribute-value {
  background-color: @tag-value-background-color;
}
@THEME .cp-search-highlight {
  background-color: @card-background-color;
}
@THEME .cp-search-highlight-text {
  background-color: @search-highlight-text-background-color;
  color: @search-highlight-text-color;
}
@THEME .cp-search-highlight-text.inactive {
  background-color: @search-highlight-text-inactive-background-color;
  color: @search-highlight-text-color;
}
@THEME .cp-search-csv-table {
  border: 1px solid @card-border-color;
  background-color: @card-background-color;
  color: @application-color;
}
@THEME .cp-search-csv-table-cell {
  border: 1px solid @card-border-color;
  color: @application-color;
}
@THEME .cp-search-faceted-button {
  color: @application-color;
  background-color: @panel-background-color;
  border: 1px solid @input-border;
}
@THEME .cp-search-faceted-button:hover,
@THEME .cp-search-faceted-button:focus {
  color: @primary-color;
  background-color: @panel-background-color;
  border-color: @primary-color;
}
@THEME .cp-search-faceted-button.selected {
  color: @primary-text-color;
  background-color: @primary-color;
  border-color: @primary-color;
}
@THEME .cp-search-faceted-button.selected:hover,
@THEME .cp-search-faceted-button.selected:focus {
  color: @primary-text-color;
  background-color: @primary-hover-color;
  border-color: @primary-hover-color;
}
@THEME .cp-search-faceted-button.disabled,
@THEME .cp-search-faceted-button:hover.disabled {
  color: @btn-disabled-color;
  background-color: transparent;
  border-color: transparent;
  cursor: default;
}
@THEME .cp-search-type-button {
  background-color: fade(@card-background-color, 50%);
  color: @application-color;
  border: 2px solid transparent;
}
@THEME .cp-search-type-button.selected {
  background-color: @card-background-color;
  color: @application-color;
  border: 2px solid @application-color;
}
@THEME .cp-search-type-button:hover {
  background-color: @card-background-color;
}
@THEME .cp-search-type-button.disabled {
  background-color: transparent;
  color: @application-color-disabled;
  cursor: not-allowed;
}
@THEME .cp-fast-search-result-item {
  background-color: @card-background-color;
  border-color: @card-border-color;
  color: @application-color;
}
@THEME .cp-fast-search-result-item:hover,
@THEME .cp-fast-search-result-item.cp-table-element-hover {
  background-color: @table-element-hover-background-color;
  color: @table-element-hover-color;
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
  background-color: @card-background-color;
  color: @application-color;
}
@THEME .cp-storage-deleted-row {
  background-color: @deleted-row-accent;
  cursor: default;
}
@THEME .cp-git-diff-collapse .ant-collapse-item.ant-collapse-item-active .ant-collapse-header {
  border-bottom: 1px solid @card-border-color;
}
@THEME .cp-branch-code-line-numbers {
  background-color: fadeout(@application-background-color, 40%);
  border-color: @panel-border-color;
}
@THEME .cp-branch-code-resize {
  background-color: fadeout(@application-background-color, 40%);
}
@THEME .cp-conflicts-resolve-area-container {
  background-color: @card-background-color;
}
@THEME .cp-conflicts-divider {
  background-color: fadeout(@card-border-color, 40%);
}
@THEME .cp-conflict-action {
  color: @application-color;
}
@THEME .cp-conflict-action:hover,
@THEME .cp-conflict-action:focus,
@THEME .cp-conflict-action:active {
  color: @application-color-accent;
}
@THEME .cp-conflict-scroller {
  background-color: fade(@application-color, 10%);
}
@THEME .cp-conflict-scroller .bar {
  background-color: fade(@application-color, 25%);
}
@THEME .cp-conflict-scroller:hover .bar,
@THEME .cp-conflict-scroller .bar:hover,
@THEME .cp-conflict-scroller .bar.hovered {
  background-color: fade(@application-color, 50%);
}

@THEME .cp-library-metadata-item-key {
  background-color: @tag-key-background-color;
  border-bottom: 1px solid @tag-key-value-divider-color;
}
@THEME .cp-library-metadata-item-value {
  background-color: @tag-value-background-color;
}
@THEME .cp-library-metadata-additional-actions {
  background-color: @card-background-color;
  border-color: @table-border-color;
}
@THEME .cp-metadata-item-row {
  color: @application-color;
}
@THEME .cp-metadata-item-row.key {
  background-color: @element-selected-background-color;
}
@THEME .cp-metadata-item-content-preview {
  background-color: @card-background-color;
  color: @application-color;
}
@THEME .cp-metadata-item-row.read-only {
  color: @application-color-faded;
  background-color: @element-selected-background-color;
}
@THEME .cp-metadata-item-row td {
  padding: 2px 5px;
  border: 1px solid transparent;
}
@THEME .cp-metadata-item-row.key.editable td,
@THEME .cp-metadata-item-row.key.editable td:hover,
@THEME .cp-metadata-item-row.value.editable td,
@THEME .cp-metadata-item-row.value.editable td:hover {
  padding: 0;
  border: none;
}
@THEME .cp-metadata-item-row.key.editable .ant-input,
@THEME .cp-metadata-item-row.value.editable .ant-input {
  padding: 2px 5px !important;
}
@THEME .cp-metadata-item-row.key td.cp-metadata-item-key:hover,
@THEME .cp-metadata-item-row.value td:hover {
  cursor: text;
  border: 1px dashed @panel-border-color;
}
@THEME .cp-metadata-fs-notification:nth-child(4n+3),
@THEME .cp-metadata-fs-notification:nth-child(4n) {
  background-color: @even-element-background;
}
@THEME .cp-metadata-item-json-table {
  background-color: @card-background-color;
  color: @application-color;
  border-color: @card-border-color;
  border-collapse: collapse;
}
@THEME .cp-metadata-item-json-table th,
@THEME .cp-metadata-item-json-table td {
  padding: 2px 4px;
  border: 1px solid @card-border-color;
}
@THEME .cp-library-breadcrumbs-editable-field:hover {
  border: 1px solid @panel-border-color;
  background-color: @element-selected-background-color !important;
}
@THEME .cp-library-breadcrumbs-editable-field-icon {
  color: @application-color-faded;
}
@THEME .cp-library-breadcrumbs-editable-field:hover .cp-library-breadcrumbs-editable-field-icon {
  color: @application-color;
}
@THEME .cp-library-path-components-container:hover {
  transition: background-color 0.5s ease;
  background-color: @element-selected-background-color;
}
@THEME .cp-library-metadata-table {
  border: 1px solid @table-border-color;
  background-color: @card-background-color;
}
@THEME .cp-library-metadata-table-cell {
  border-right: 1px solid fadeout(@application-color, 90%);
}
@THEME .cp-library-metadata-table-cell-selected {
  background-color: fadeout(@primary-hover-color, 70%);
  color: @application-color;
}
@THEME .cp-library-metadata-table-marker {
  background-color: @primary-color;
}
@THEME .cp-library-metadata-panel-placeholder {
  color: @application-color-faded;
}
@THEME .cp-library-metadata-spread-top-cell {
  border-top-color: @primary-color;
}
@THEME .cp-library-metadata-spread-bottom-cell {
  border-bottom-color: @primary-color;
}
@THEME .cp-library-metadata-table .cp-library-metadata-spread-right-cell.cp-library-metadata-spread-cell-selected,
@THEME .cp-library-metadata-table .cp-library-metadata-spread-right-cell.cp-library-metadata-spread-cell-hovered,
@THEME .cp-library-metadata-table .cp-library-metadata-spread-right-cell.cp-library-metadata-spread-selected {
  border-right: 1px solid @primary-color;
}
@THEME .cp-library-metadata-spread-left-cell {
  border-left-color: @primary-color;
}
@THEME .cp-library-metadata-spread-selected {
  background-color: fadeout(@primary-hover-color, 60%);
}
@THEME .cp-library-metadata-spread-cell-hovered {
  background-color: fadeout(@primary-hover-color, 60%);
}
@THEME .cp-library-metadata-spread-cell-selected {
  background-color: fadeout(@primary-hover-color, 90%);
}
@THEME .cp-metadata-dropdown-row {
  border-bottom: 1px solid @input-addon;
  background-color: @card-background-color;
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
@THEME .cp-cluster-node-even-row {
  background-color: @even-element-background;
}
@THEME .cp-filter-popover-item:hover {
  background-color: @element-hover-background-color;
  color: @application-color;
}
@THEME .cp-settings-sidebar-element:not(.cp-table-element-disabled) {
  cursor: pointer;
}
@THEME .cp-settings-sidebar-element td {
  padding: 6px;
}
@THEME .cp-setting-info {
  color: @color-green;
}
@THEME .cp-setting-warning {
  color: @color-yellow;
}
@THEME .cp-setting-critical {
  color: @color-red;
}
@THEME .cp-setting-message {
  color: @primary-color;
}
@THEME .cp-settings-table thead,
@THEME .cp-settings-table thead th {
  color: @table-head-color;
  background-color: @card-header-background;
  border-color: @table-border-color;
}
@THEME .cp-settings-table tbody tr,
@THEME .cp-settings-table tbody th {
  color: @application-color;
  background-color: @card-background-color;
}
@THEME .cp-settings-table tbody tr:nth-child(even),
@THEME .cp-settings-table tbody tr:nth-child(even) th {
  color: @application-color;
  background-color: @even-element-background;
}
@THEME .cp-settings-users-integrity-check.modified:not(.new),
@THEME .cp-settings-users-integrity-check.modified:not(.new) > div {
  background-color: @alert-warning-background;
}
@THEME .cp-settings-users-integrity-check.new > div {
  background-color: @alert-info-background;
}
@THEME .cp-even-row {
  background-color: @even-element-background;
}
@THEME .cp-settings-form-item-label {
  color: @application-color-accent;
}
@THEME .cp-settings-form-item-label.required::before {
  color: @color-red;
  content: '*';
}
@THEME .cp-settings-nat-table thead tr:first-child,
@THEME .cp-settings-nat-table thead tr:first-child th:first-child,
@THEME .cp-settings-nat-table thead tr th.external-column:nth-child(5),
@THEME .cp-settings-nat-table tbody tr td.external-column:nth-child(5),
@THEME .cp-settings-nat-table thead tr th.internal-column:nth-child(8),
@THEME .cp-settings-nat-table tbody tr td.internal-column:nth-child(8) {
  border-right: 2px solid @table-border-color;
}
@THEME .cp-settings-nat-table thead tr:first-child th {
  background: @card-header-background;
}
@THEME .cp-settings-nat-table .ant-table-thead > tr > th {
  border-bottom: 1px solid @table-border-color;
}
@THEME .cp-nat-route-removed .cp-nat-route-status {
  color: currentColor;
}
@THEME .cp-nat-route-port-control {
  border-bottom: 1px solid @table-border-color;
}
@THEME .cp-user-status-online {
  fill: @color-green;
  stroke: @color-green;
}
@THEME .cp-user-status-offline {
  stroke: @color-grey;
  fill: transparent;
}

@THEME .code-highlight {
  color: @application-color;
  background-color: @code-background-color;
  border: 1px solid @card-border-color;
  border-radius: 4px;
}
@THEME .markdown pre,
@THEME .markdown code {
  color: @application-color;
  background-color: @code-background-color;
  border: 1px solid @card-border-color;
  border-radius: 4px;
  padding: 10px;
  margin: 5px 0;
  white-space: pre-line;
}
@THEME .code-highlight code,
@THEME .markdown code {
  box-shadow: inset 0 -1px 0 rgba(0, 0, 0, 0.25);
}
@THEME .markdown pre > code {
  display: block;
  background-color: transparent;
  color: inherit;
  box-shadow: none;
  border: none;
}
@THEME .markdown code {
  padding: 1px 4px;
  border-radius: 4px;
}
@THEME .markdown h1,
@THEME .markdown h2,
@THEME .markdown h3 {
  margin: 5px 0;
}
@THEME .markdown h4,
@THEME .markdown h5,
@THEME .markdown h6 {
  margin: 2px 0;
}
@THEME .markdown:not(.no-margin-markdown) p {
  margin: 5px 0;
}
@THEME .markdown p a {
  margin: 0 2px;
}
@THEME .markdown ul,
@THEME .markdown ol {
  margin-top: 0;
  margin-bottom: 10px;
  display: block;
  list-style: disc inside;
}
@THEME .markdown ol {
  list-style-type: decimal;
}
@THEME .markdown li {
  display: list-item;
  margin: 5px;
}
@THEME .markdown ol ul {
  list-style: circle;
}
@THEME .markdown ol ul li {
  margin-left: 35px;
}
@THEME .markdown ol ul ul,
@THEME .markdown ul ul {
  list-style: square;
}
@THEME .markdown ul ul li {
  margin-left: 35px;
}
@THEME .markdown blockquote {
  margin: 0;
  padding: 10px 0;
  padding-left: 1rem;
  border-left: 4px solid @card-border-color;
  background-color: @code-background-color;
}
@THEME .markdown hr {
  margin: 5px 0;
  border-top: 1px solid @card-border-color;
  border-right: none;
  border-left: none;
  border-bottom: none;
}
@THEME .markdown table {
  border-collapse: collapse;
  border: 1px solid @card-border-color;
}
@THEME .markdown table td,
@THEME .markdown table th {
  border: 1px solid @card-border-color;
  padding: 5px;
}
@THEME .markdown table tr:nth-child(even) {
  background-color: @even-element-background;
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
@THEME .cp-code-editor {
  background-color: @card-background-color;
  color: @application-color;
  border-color: @card-border-color;
}
@THEME .CodeMirror {
  background-color: @card-background-color;
  color: @application-color;
  border: 1px solid @card-border-color;
}
@THEME .CodeMirror-cursor {
  border-color: @application-color;
}
@THEME .CodeMirror-gutters {
  background-color: @card-header-background;
  border-color: @card-border-color;
}
@THEME .CodeMirror-linenumber {
  color: @application-color-faded;
}
@THEME .cm-s-default .cm-header {
  color: @color-blue;
}
@THEME .cm-s-default .cm-quote {
  color: @color-green;
}
@THEME .cm-negative {
  color: @color-sensitive;
}
@THEME .cm-positive {
  color: @color-green;
}
@THEME .cm-s-default .cm-keyword {
  color: @color-violet;
}
@THEME .cm-s-default .cm-atom {
  color: @color-blue;
}
@THEME .cm-s-default .cm-number {
  color: @color-aqua;
}
@THEME .cm-s-default .cm-def {
  color: @color-blue-dimmed;
}
@THEME .cm-s-default .cm-variable-2 {
  color: @color-blue-dimmed;
}
@THEME .cm-s-default .cm-variable-3,
@THEME .cm-s-default .cm-type {
  color: @color-green;
}
@THEME .cm-s-default .cm-comment {
  color: @color-yellow;
}
@THEME .cm-s-default .cm-string {
  color: @color-red;
}
@THEME .cm-s-default .cm-string-2 {
  color: @color-yellow;
}
@THEME .cm-s-default .cm-meta,
@THEME .cm-s-default .cm-qualifier {
  color: @application-color-disabled;
}
@THEME .cm-s-default .cm-builtin {
  color: @color-blue-dimmed;
}
@THEME .cm-s-default .cm-bracket {
  color: @color-grey;
}
@THEME .cm-s-default .cm-tag {
  color: @color-green;
}
@THEME .cm-s-default .cm-hr {
  color: @application-color-faded;
}
@THEME .cm-s-default .cm-attribute,
@THEME .cm-s-default .cm-link {
  color: @primary-color;
}
@THEME .cm-s-default .cm-error,
@THEME .cm-invalidchar {
  color: @color-red;
}
@THEME div.CodeMirror span.CodeMirror-matchingbracket {
  color: @color-green;
}
@THEME div.CodeMirror span.CodeMirror-nonmatchingbracket {
  color: @color-red;
}
@THEME .CodeMirror-matchingtag { background: fadeout(@color-yellow, 50%); }
@THEME .CodeMirror-activeline-background {
  background: @card-header-background;
}
@THEME .handsontable {
  color: @application-color;
}
@THEME .handsontable th {
  background-color: @card-header-background;
}
@THEME .handsontable td {
  background-color: @card-background-color;
}
@THEME .handsontable tr th,
@THEME .handsontable tr td,
@THEME .handsontable tr:first-child th,
@THEME .handsontable tr:first-child td {
  color: @application-color;
}
@THEME .handsontable tr th,
@THEME .handsontable tr td,
@THEME .handsontable tr:first-child th,
@THEME .handsontable tr:first-child td,
@THEME .handsontable.htRowHeaders thead tr th:nth-child(2) {
  border-color: darken(@card-border-color, 5%);
}
@THEME .handsontable tbody th.ht__highlight,
@THEME .handsontable thead th.ht__highlight {
  background-color: @primary-color;
  color: @primary-text-color;
}
@THEME .handsontable td.area {
  background-image: linear-gradient(to bottom, @primary-color-semi-transparent 0%, @primary-color-semi-transparent 100%);
  background-color: @card-background-color;
}
@THEME .cp-pipeline-code-editor-readonly .handsontable td,
@THEME .cp-pipeline-code-editor-readonly .handsontable td.area,
@THEME .cp-pipeline-code-editor-readonly .CodeMirror {
  background-color: lighten(@card-header-background, 2%);
}
@THEME .joint-paper.joint-theme-default {
  background-color: @card-background-color;
}
@THEME .joint-type-visualstep .body,
@THEME .joint-type-visualgroup .body,
@THEME .joint-type-visualworkflow .body {
  fill: @card-background-color;
  stroke: @primary-color;
}
@THEME .joint-type-visualstep .port-body.empty,
@THEME .joint-type-visualgroup .port-body.empty,
@THEME .joint-type-visualworkflow .port-body.empty {
  fill: @card-background-color;
  stroke: @primary-color;
}
@THEME .joint-type-visualstep .port-body,
@THEME .joint-type-visualgroup .port-body,
@THEME .joint-type-visualworkflow .port-body {
  fill: @card-background-color;
  stroke: @color-aqua;
}
@THEME .joint-type-visualstep .port-label,
@THEME .joint-type-visualgroup .port-label,
@THEME .joint-type-visualworkflow .port-label,
@THEME .marker-source,
@THEME .marker-target,
@THEME .joint-type-visualdeclaration .label {
  fill: @color-aqua;
  stroke: @color-aqua;
}
@THEME .joint-link .marker-arrowheads .marker-arrowhead,
@THEME .joint-link .marker-vertex-group .marker-vertex,
@THEME .joint-link .marker-vertex-group .marker-vertex:hover {
  fill: @primary-color;
}
@THEME .marker-vertex-remove-area {
  fill: @application-color;
}
@THEME .joint-link.joint-theme-default .marker-vertex-remove {
  fill: @card-background-color;
}
@THEME .joint-link.joint-theme-default .connection-wrap,
@THEME .joint-link .connection {
  stroke: @color-aqua;
}
@THEME .joint-type-visualstep .label,
@THEME .joint-type-visualgroup .label,
@THEME .joint-type-visualworkflow .label {
  fill: @primary-color;
}
@THEME .joint-link .link-tools .link-tool .tool-remove circle {
  fill: @color-red;
}
@THEME .cp-pipeline-graph-side-panel:hover {
  background-color: @card-background-color;
  box-shadow: 5px 0 15px @card-hovered-shadow-color;
}
@THEME .cp-pipeline-graph-properties-panel {

}
@THEME .cp-issue-markdown-link {
  color: @primary-color;
  background-color: @card-background-color;
  box-shadow: inset 0 -1px 0 @card-hovered-shadow-color;
  transition: none;
  border-radius: 5px;
  padding: 2px 4px;
}
@THEME .cp-issue-markdown-link:hover {
  color: @primary-text-color;
  background-color: @primary-color;
}
@THEME .d2h-file-wrapper,
@THEME .d2h-file-header,
@THEME .d2h-diff-table,
@THEME .d2h-code-linenumber,
@THEME .d2h-code-line.d2h-info,
@THEME .d2h-code-linenumber.d2h-info {
  background-color: @card-background-color;
  color: @application-color;
  border-color: @card-border-color;
}
@THEME .d2h-tag {
  background-color: @card-background-color;
}
@THEME .d2h-info {
  background-color: @panel-background-color;
}
@THEME .d2h-file-diff .d2h-ins.d2h-change,
@THEME .d2h-ins {
  background-color: @color-green-semi-transparent;
}
@THEME .d2h-file-diff .d2h-del.d2h-change,
@THEME .d2h-del {
  background-color: @color-red-semi-transparent;
}
@THEME .d2h-code-line.d2h-del.d2h-change del {
  color: @card-background-color;
  background-color: @color-red;
}
@THEME .d2h-code-line.d2h-ins.d2h-change ins {
  color: @card-background-color;
  background-color: @color-green;
}

`;
