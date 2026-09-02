import * as vscode from "vscode";
import * as fs from "fs";
import * as path from "path";
import * as dotenv from "dotenv";
import { DateTime } from "luxon";

import { ICpClientConfig } from "./cp-client/cp-client-config";
import { ILogger, IOutputLogger, LogLevelName } from "./common/logger";
import { mirrorKeys } from "./common/types";
import { subscribeAllEvents } from "./tools/vscode-events";
import { Commands } from "./commands";
import { OnStartProps } from "./cp-ext/on-start";
import {
  fsFileExistsSync,
  fsReadJsonFileSync,
  fsWriteJsonFileSync,
} from "./common/files/file";
import { IPackageJson, PackageJsonData } from "./common/files/packageJson";

export const CpExtConfigKeyValues = [
  "prefix",
  "platformUrl",

  "apiEndpoint",
  "authEndpoint",

  "pipeApiUri",
  "pipeApiToken",
  "pipeSnoozeUpdate",

  "logLevel",
];
export type CpExtConfigKey = (typeof CpExtConfigKeyValues)[number];
export const CpExtConfigKeys = mirrorKeys(CpExtConfigKeyValues);

export interface ICpExtConfigData {
  readonly globalStoragePath: string;

  platformUrl: string;
  prefix: string;
  apiEndpoint: string;
  authEndpoint: string;

  pipeApiUri: string | null;
  pipeApiToken: string | null;
  pipeSnoozeUpdate: DateTime | null;

  logLevel: LogLevelName;

  getOnStart(): ReadonlyArray<OnStartProps>;
  setOnStart(value: ArrayLike<OnStartProps>): void;
}

export interface ICpExtConfig extends ICpExtConfigData {
  getClientConfig(): Promise<ICpClientConfig | null>;
  setClientConfig(resConfig: ICpClientConfig | null): Promise<void>;

  save(reason: string): Promise<void>;
}

type DefaultsDict = { [key: string]: any };

export class CpExtConfig implements ICpExtConfig {
  private static objCounter = 0;
  private objId = CpExtConfig.objCounter++;

  protected toLog(): string {
    return `${this.constructor.name}<${this.objId}>`;
  }

  private data: Partial<ICpExtConfigData> = {};
  private configData!: vscode.WorkspaceConfiguration;
  private defaults: DefaultsDict = {};
  private logger!: IOutputLogger;

  public packageJson!: PackageJsonData;

  private readonly onStartFilePath: string;

  constructor(private readonly context: vscode.ExtensionContext) {
    this.onStartFilePath = path.join(this.globalStoragePath, "onStart.json");
    this.updateConfigData();
  }

  protected updateConfigData(): void {
    const logPfx = `${this.toLog()}.updateConfigData()`;
    this.configData = vscode.workspace.getConfiguration("remote-cp");

    if (Object.keys(this.data).length > 0) {
      void this.save(logPfx);
    }
  }

  readonly cfgTarget = vscode.ConfigurationTarget.Global;

  public async save(reason: string): Promise<void> {
    const logPfx = `${this.toLog()}.save()`;
    this.logger.trace(`${logPfx}, start, reason: ${reason}`);
    const dataToSave = Object.assign({}, this.data);
    try {
      for (const [key, value] of Object.entries(dataToSave)) {
        if (this.configData.get<string>(key) !== value) {
          let valueToSave: any = value;
          if (value instanceof DateTime) {
            valueToSave = value.toISO();
          } else if (value instanceof Array) {
            valueToSave = JSON.stringify(value);
          }
          this.logger.info(
            `${logPfx}, key: '${key}', saving...\n` +
              `  value: '${valueToSave}'`,
          );
          await this.configData.update(key, valueToSave, this.cfgTarget);
          this.logger.info(`${logPfx}, key: '${key}' saved.`);
          // @ts-expect-error any
          delete this.data[key];
        }
      }
    } catch (err) {
      const errMsg = err instanceof Error ? err.message : err.toString();
      vscode.window.showErrorMessage(errMsg);
      this.logger.error(err);
    }
    this.logger.trace(`${logPfx}, end`);
  }

  public get globalStoragePath(): string {
    return this.context.globalStoragePath;
  }

  public get platformUrl(): string {
    let res = this.data.platformUrl;
    if (res === undefined)
      res =
        /* this.data.platformUrl = */
        this.configData.get<string | undefined>(
          CpExtConfigKeys.platformUrl,
          process.env.CP_PLATFORM_URL,
        ) ??
        this.defaults[CpExtConfigKeys.platformUrl] ??
        "";

    return res!.trim().replace(/\/+$/, "");
  }

  public set platformUrl(value: string) {
    this.data.platformUrl = value;
  }

  public get prefix(): string {
    let res = this.data.prefix;
    if (res === undefined)
      res = /* this.data.prefix = */ this.configData.get<string>(
        CpExtConfigKeys.prefix,
        this.defaults[CpExtConfigKeys.prefix] ?? "CP",
      );
    return res;
  }

  public set prefix(value: string) {
    this.data.prefix = value;
  }

  public get apiEndpoint(): string {
    let res = this.data.apiEndpoint;
    if (res === undefined)
      res =
        /*this.data.apiEndpoint = */
        this.configData.get<string | undefined>(
          CpExtConfigKeys.apiEndpoint,
          process.env.CP_API_ENDPOINT,
        ) ??
        this.defaults[CpExtConfigKeys.apiEndpoint] ??
        "";
    return res!;
  }

  public set apiEndpoint(value: string) {
    this.data.apiEndpoint = value;
  }

  public get authEndpoint(): string {
    let res = this.data.authEndpoint;
    if (res === undefined)
      res =
        /* this.data.authEndpoint = */
        this.configData.get<string | undefined>(
          CpExtConfigKeys.authEndpoint,
          process.env.CP_AUTH_ENDPOINT,
        ) ??
        this.defaults[CpExtConfigKeys.authEndpoint] ??
        "";
    return res!;
  }

  public set authEndpoint(value: string) {
    this.data.authEndpoint = value;
  }

  public get pipeApiUri(): string | null {
    let res = this.data.pipeApiUri;
    if (res === undefined)
      res =
        /* this.data.pipeApiUri = */
        this.configData.get<string | null>(
          CpExtConfigKeys.pipeApiUri,
          process.env.CP_API ?? null,
        ) ??
        this.defaults[CpExtConfigKeys.pipeApiUri] ??
        null;
    return res!;
  }

  public set pipeApiUri(value: string | null) {
    this.data.pipeApiUri = value;
  }

  public get pipeApiToken(): string | null {
    let res = this.data.pipeApiToken;
    if (res === undefined)
      res =
        /* this.data.pipeApiToken = */
        this.configData.get<string | null>(
          CpExtConfigKeys.pipeApiToken,
          process.env.CP_API_TOKEN ?? null,
        ) ??
        this.defaults[CpExtConfigKeys.pipeApiToken] ??
        null;
    return res!;
  }

  public set pipeApiToken(value: string | null) {
    this.data.pipeApiToken = value;
  }

  public get pipeSnoozeUpdate(): DateTime | null {
    let res = this.data.pipeSnoozeUpdate;
    if (res === undefined) {
      const str = this.configData.get<string | null>(
        CpExtConfigKeys.pipeSnoozeUpdate,
        null,
      );
      try {
        res = /* this.data.pipeSnoozeUpdate = */ str
          ? DateTime.fromISO(str) /* ISO-8601 */
          : null;
      } catch (err) {
        this.logger.error(`Config 'pipeSnoozeUpdate' error: '${str}'.`, err);
        res = null;
      }
    }
    return res!;
  }

  public set pipeSnoozeUpdate(value: DateTime | null) {
    this.data.pipeSnoozeUpdate = value;
  }

  public get logLevel(): LogLevelName {
    let res = this.data.logLevel;
    if (res === undefined)
      res =
        /* this.data.logLevel = */
        this.configData.get<LogLevelName | undefined>(
          CpExtConfigKeys.logLevel,
          process.env.CP_LOG_LEVEL as LogLevelName,
        ) ??
        this.defaults[CpExtConfigKeys.logLevel] ??
        "info";

    return res!;
  }

  public set logLevel(value: LogLevelName) {
    this.data.logLevel = value;
  }

  public getOnStart(): ReadonlyArray<OnStartProps> {
    if (!fsFileExistsSync(this.onStartFilePath)) return [];

    const res = fsReadJsonFileSync<OnStartProps[]>(this.onStartFilePath);
    return res;
  }

  public setOnStart(value: ArrayLike<OnStartProps>): void {
    try {
      fs.mkdirSync(path.dirname(this.onStartFilePath), {
        recursive: true,
      });
    } catch {
      // ignore
    }
    fsWriteJsonFileSync(this.onStartFilePath, value);
  }

  // --

  public async getClientConfig(): Promise<ICpClientConfig | null> {
    const logPfx = `${this.toLog()}.getClientConfig()`;
    this.logger.trace(`${logPfx}, start`);
    try {
      if (!this.pipeApiUri)
        this.pipeApiUri = vscode.Uri.parse(this.platformUrl)
          .with({
            path: this.apiEndpoint,
          })
          .toString();
      if (!this.pipeApiUri || !this.pipeApiToken) {
        this.logger.warn("Pipe client configuration not found in extension.");
        return null;
      } else
        return {
          apiUri: this.pipeApiUri,
          apiToken: this.pipeApiToken,
        };
    } finally {
      this.logger.trace(`${logPfx}, end`);
    }
  }

  // prettier-ignore
  public async setClientConfig(config: ICpClientConfig | null): Promise<void> {
    const logPfx = `${this.toLog()}.setClientConfig()`;
    this.logger.trace(`${logPfx}, start`);
    try{
      this.pipeApiUri = config?.apiUri ?? null;
      this.pipeApiToken = config?.apiToken ?? null;
    } finally {
      this.logger.trace(`${logPfx}, end`);
     }
  }

  public async activate(logger: IOutputLogger): Promise<void> {
    this.logger = logger;
    await applyDotEnv(this.context, this.logger);

    const logPfx = `${this.toLog()}.activate()`;
    this.logger.trace(`${logPfx}, start`);

    this.logger.info(
      `${logPfx}, config onStart\n` + `  file: ${this.onStartFilePath}`,
    );
    this.context.subscriptions.push(
      vscode.workspace.onDidChangeConfiguration(
        this.onDidChangeConfiguration,
        this,
      ),
    );

    this.packageJson = await PackageJsonData.create(this.context, this.logger);

    //
    // subscribeAllEvents(this.context, this.logger);

    this.defaults = await readDefaultsFromPackageJson(
      this.context,
      this.packageJson.obj,
    );

    this.context.subscriptions.push(
      vscode.commands.registerCommand(Commands.config.reset, this.reset, this),
    );

    if (!this.platformUrl) {
      const inputPlatformUrl = await vscode.window.showInputBox({
        title: `${this.prefix} platform URL`,
        // prompt: `${this.prefix} platform URL`,
        placeHolder: "https://cora.company.com",
      });
      // const inputPlatformUrl = await vscode.window.showQuickPick(options, {
      //   placeHolder: "Select an option to save",
      // });
      if (!inputPlatformUrl) {
        throw new Error(`${this.prefix} platform URL is not specified.`);
      }
      this.platformUrl = inputPlatformUrl;
      void this.save(logPfx);
    }

    this.logger.trace(`${logPfx}, end`);
  }

  async reset(): Promise<void> {
    const logPfx = `${this.toLog()}.reset()`;
    for (const [k, v] of Object.entries(this.defaults)) {
      // @ts-expect-error any
      this.data[k] = v;
    }
    await this.save(logPfx);
  }

  // -- Events' handlers --

  async onDidChangeConfiguration(
    _event: vscode.ConfigurationChangeEvent, // _event is always an empty object
  ): Promise<any> {
    const logPfx = `${this.toLog()}.onDidChangeConfiguration()`;
    this.logger.trace(`${logPfx}, start`);
    this.updateConfigData();
    this.logger.trace(`${logPfx}, end`);
  }
}

async function readDefaultsFromPackageJson(
  context: vscode.ExtensionContext,
  packageJsonObj: IPackageJson,
): Promise<DefaultsDict> {
  const resDefaults: DefaultsDict = {};
  const keyRe = /remote-cp\.(.*)/;
  for (const [key, value] of Object.entries<any>(
    packageJsonObj.contributes.configuration.properties,
  )) {
    const keyM = keyRe.exec(key);
    if (keyM) {
      const keyStr = keyM[1];
      if (value.default) {
        resDefaults[keyStr] = value.default;
      }
    }
  }
  return resDefaults;
}

async function applyDotEnv(
  context: vscode.ExtensionContext,
  logger: ILogger,
): Promise<void> {
  const envFileUri = vscode.Uri.joinPath(context.extensionUri, ".env");
  logger.info(`Apply dotenv:\n` + `  file: ${envFileUri.fsPath}`);
  dotenv.config({ path: envFileUri.fsPath });
}
