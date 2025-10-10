import * as vscode from "vscode";
import { Disposable } from "./disposable";

export enum LogLevel {
  error = 0,
  warn = 1,
  info = 2,
  debug = 3,
  trace = 4,
}

export type LogLevelName = keyof typeof LogLevel;

export interface ILogger {
  // log(message?: any, ...optionalParams: any[]): void;
  error(message?: any, ...optionalParams: any[]): void;
  warn(message?: any, ...optionalParams: any[]): void;
  info(message?: any, ...optionalParams: any[]): void;
  debug(message?: any, ...optionalParams: any[]): void;
  trace(message?: any, ...optionalParams: any[]): void;
}

export interface IOutputLogger extends ILogger {
  level: LogLevelName;
}

export class Logger extends Disposable implements IOutputLogger {
  private levelValue: LogLevel;
  private outputChannel!: vscode.LogOutputChannel;

  constructor(
    private readonly label: string,
    level: LogLevelName,
  ) {
    super();
    this.levelValue = LogLevel[level];
  }

  override dispose() {
    if (!this.isDisposed) {
      this.outputChannel.dispose();
    }
    super.dispose();
  }

  public get level(): LogLevelName {
    return LogLevel[this.levelValue] as LogLevelName;
  }

  public set level(value: LogLevelName) {
    this.levelValue = LogLevel[value];
  }

  public show(): void {
    this._ensureOutputChannel();
    return this.outputChannel?.show();
  }

  public clear() {
    this._ensureOutputChannel();
    this.outputChannel?.clear();
  }

  private _ensureOutputChannel() {
    this.outputChannel = vscode.window.createOutputChannel(this.label, {
      log: true,
    });
    vscode.commands.executeCommand(
      "setContext",
      "tunnelForwardingHasLog",
      true,
    );
  }

  public appendLine(line: string): void {
    this._ensureOutputChannel();
    this.outputChannel.appendLine(line);
  }

  public log(
    level: LogLevelName,
    message?: any,
    ...optionalParams: any[]
  ): void {
    this[level](message, ...optionalParams);
  }

  public trace(message?: any, ...optionalParams: any[]): void {
    this._ensureOutputChannel();
    if (this.levelValue >= LogLevel.trace)
      this.outputChannel.trace(message, ...optionalParams);
  }

  public debug(message?: any, ...optionalParams: any[]): void {
    this._ensureOutputChannel();
    if (this.levelValue >= LogLevel.debug)
      this.outputChannel.debug(message, ...optionalParams);
  }

  public info(message?: any, ...optionalParams: any[]): void {
    this._ensureOutputChannel();
    if (this.levelValue >= LogLevel.info)
      this.outputChannel.info(message, ...optionalParams);
  }

  public warn(message?: any, ...optionalParams: any[]): void {
    this._ensureOutputChannel();
    if (this.levelValue >= LogLevel.warn)
      this.outputChannel.warn(message, ...optionalParams);
  }

  public error(message?: any, ...optionalParams: any[]): void {
    this._ensureOutputChannel();
    if (this.levelValue >= LogLevel.error)
      this.outputChannel.error(message, ...optionalParams);
  }
}
