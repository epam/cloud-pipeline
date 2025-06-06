# GUI plugins development

- [Overview](#overview)
- [Requirements](#requirements)
    - [1. ES6 modules](#1-es6-modules)
    - [2. Framework compatibility](#2-framework-compatibility)
    - [3. Exported functions interfaces](#3-exported-functions-interfaces)
        - [3.1 Common types & interfaces](#31-common-types--interfaces)
        - [3.2 `renderLaunchForm`](#32-renderlaunchform)
        - [3.3 `renderLogForm`](#33-renderlogform)
    - [4. Additional resources](#4-additional-resources)
    - [5. Bundle format](#5-bundle-format)
    - [6. Plugin examples](#6-plugin-examples)
        - [6.1 Launch Form plugin example](#61-launch-form-plugin-example)
        - [6.2 Log Form plugin example](#62-log-form-plugin-example)

This document contains requirements for developing GUI plugin bundles.  
These plugins can be used to customize the GUI of specific platform pages for different tools/pipelines and users.  
Usage example of the prepared GUI plugin see [here](../manual/11_Manage_Runs/11.7._Plugins_framework.md).

## Overview

The plugin bundle should be a self-contained JavaScript bundle that adheres to the following specifications.  
The bundle **should always include** a single JavaScript file that exports the necessary functions as **ES6 modules**.

> **_Note_**: below, requirements for bundles developing for two base platform forms are described - **Log form** and **Launch form**.

## Requirements

### 1. ES6 modules

The plugin bundle must be written as an ES6 module, exporting functions that can be imported using the `import` syntax.

- The JavaScript file(s) should be compatible with modern JavaScript runtimes supporting ES6 module syntax.
- The module should export the following two functions (defined below):
    - `renderLaunchForm`
    - `renderLogForm`

### 2. Framework compatibility

The plugin bundle **must include all used frameworks and libraries** (`React` / `Vue`/ others, component libraries, etc.).

If a framework is included, the necessary libraries and dependencies should be bundled with the plugin (using tools like `Webpack`, `Rollup`, or similar bundlers). For example:

- if `React` is used, `React` and `ReactDOM` must be bundled
- if `Vue` is used, `Vue` must be bundled

### 3. Exported functions interfaces

#### 3.1 Common types & interfaces

```typescript
/**
 * A Cloud Pipeline API wrapper for interaction with RESTApi and main application
 */
export interface CloudPipelineApi {
  /**
   * RESTApi url
   */
  url: string;
  /**
   * Helper function for opening a storage browser modal and objects selection
   * @param {CloudPipelineStorageSelectionOptions} options
   */
  selectStorageItems(options?: CloudPipelineStorageSelectionOptions): Promise<string[]>;

  /**
   * This function should be called for launching a job in a main application
   * (using default confirmation flow)
   * @param {RunPayload} payload
   */
  launch(payload: RunPayload): Promise<void>;

  /**
   * This function should be called if the job was launched within the plugin itself;
   * a launched job id should be passed.
   * @param {number} runId
   */
  onLaunched(runId: number): void;
}

/**
 * General plugin configuration options
 */
export interface PluginConfiguration<Options> {
  /**
   * A div container for rendering plugin contents
   */
  container: HTMLElement;
  /**
   * Cloud Pipeline API wrapper object
   */
  api: CloudPipelineApi;
  /**
   * Plugin-specific options
   */
  options: Options;
}

/**
 * Plugin instance
 */
export interface Plugin<Options> {
  /**
   * This method will be invoked if plugin options were changed
   * @param options - plugin-specific options
   */
  setOptions(options: Options): void;

  /**
   * This method will be invoked when plugin instance should be destroyed
   */
  destroy(): void;
}
```

Each plugin (**Launch form** or **Log form**) will be initialized using the corresponding function call (i.e., `renderLaunchForm` or `renderLogForm`, see below) with plugin's configuration object passed (i.e., nested from `PluginConfiguration<...>` and specific for each plugin type).  
This function should return a `Plugin<...>` instance.

If the plugin options change, the `setOptions(...)` method of plugin instance will be invoked.  
The plugin implementation should re-render corresponding views, if necessary.

#### 3.2 `renderLaunchForm`

This function is used for initialization and rendering of the **Launch form** plugin:

```typescript
/**
 * "Launch Form" plugin options
 */
export type LaunchFormOptions = {
  /**
   * A run identifier or Run instance (optional) - if the launch form should be rendered for re-launching
   * an existing job.
   */
  run?: string | number | Run;
  /**
   * A pipeline identifier (optional)
   */
  pipelineId?: string | number;
  /**
   * A pipeline version (optional)
   */
  pipelineVersion?: string;
  /**
   * A pipeline configuration (optional)
   */
  pipelineConfiguration?: string;
  /**
   * A docker image (optional)
   */
  dockerImage?: number | string;
  /**
   * A docker image version (optional)
   */
  toolVersion?: string;
};

export type LaunchFormPluginConfiguration = PluginConfiguration<LaunchFormOptions>;

export type LaunchFormPlugin = Plugin<LaunchFormOptions>;
```

The `renderLaunchForm` function should return a `LaunchFormPlugin` instance.

#### 3.3 `renderLogForm`

This function is used for initialization and rendering of the **Log form** plugin:

```typescript
/**
 * "Log Form" plugin options
 */
export type LogFormOptions = {
  /**
   * A run identifier or run instance
   */
  run?: string | number | Run;
};

export type LogFormPluginConfiguration = PluginConfiguration<LogFormOptions>;

export type LogFormPlugin = Plugin<LogFormOptions>;
```

The `renderLogForm` function should return a `LogFormPlugin` instance.

### 4. Additional resources

The plugin bundle may include additional resources such as:

- **CSS files**: style files for custom plugin UI
- **images**: any assets (icons, logos, etc.) that the plugin may use
- other sources

All these resources should be injected in JavaScript file, i.e., for styles use `css-in-jss` or bundlers plugins (`vite`, `Webpack`) for bundling styles in JavaScript.

### 5. Bundle format

- The plugin bundle should contain a single JavaScript file that exports the required functions. All dependencies should be included and bundled in such a way that no external dependencies are needed.
- Use a bundler such as `Webpack` or `Rollup` to package the resources into a single file.
- The bundle should be compatible with modern browsers.

### 6. Plugin examples

#### 6.1 Launch Form plugin example

```typescript
import { createRoot } from 'react-dom/client';
import type { LaunchFormPluginConfiguration, LaunchFormPlugin, LaunchFormOptions } from './types.ts';

export function renderLaunchForm(configuration: LaunchFormPluginConfiguration): LaunchFormPlugin {
  const { container, options, api } = configuration;
  const root = createRoot(container);
  function setOptions(options: LaunchFormOptions) {
    root.render(
      <div>
        <div>Launch form logic goes here. Options:</div>
        <code>
          {JSON.stringify(options)}
        </code>
        <div>API endpoint: {api}</div>
      </div>,
    );
  }
  setOptions(options);
  return {
    setOptions,
    destroy() {
      root.unmount();
    },
  };
}
```

#### 6.2 Log Form plugin example

```typescript
import { createRoot } from 'react-dom/client';
import type { LogFormPluginConfiguration, LogFormPlugin, LogFormOptions } from './types.ts';

export function renderLogForm(configuration: LogFormPluginConfiguration): LogFormPlugin {
  const { container, options, api } = configuration;
  const root = createRoot(container);
  function setOptions(options: LogFormOptions) {
    root.render(
      <div>
        <div>Log form logic goes here. Options:</div>
        <code>
          {JSON.stringify(options)}
        </code>
        <div>API endpoint: {api}</div>
      </div>,
    );
  }
  setOptions(options);
  return {
    setOptions,
    destroy() {
      root.unmount();
    },
  };
}
```
