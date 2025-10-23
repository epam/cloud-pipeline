import * as vscode from "vscode";
import * as fs from "fs";
import * as path from "path";

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

export type IOutputLogger = ILogger;
export class LoggerBase extends Disposable implements ILogger {
  constructor(private readonly base?: LoggerBase) {
    super();
  }

  override dispose(): any {
    if (this.base) {
      this.base.dispose();
      // @ts-expect-error readonly
      this.base = undefined;
    }
    super.dispose();
  }

  public error(message?: any, ...optionalParams: any[]): void {
    if (this.base) this.base.error(message, ...optionalParams);
  }

  public warn(message?: any, ...optionalParams: any[]): void {
    if (this.base) this.base.warn(message, ...optionalParams);
  }

  public info(message?: any, ...optionalParams: any[]): void {
    if (this.base) this.base.info(message, ...optionalParams);
  }

  public debug(message?: any, ...optionalParams: any[]): void {
    if (this.base) this.base.debug(message, ...optionalParams);
  }

  public trace(message?: any, ...optionalParams: any[]): void {
    if (this.base) this.base.trace(message, ...optionalParams);
  }
}

export class OutputLogger extends LoggerBase implements IOutputLogger {
  private outputChannel!: vscode.LogOutputChannel;

  constructor(
    private readonly label: string,
    level: LogLevelName,
    base?: LoggerBase,
  ) {
    super(base);
  }

  override dispose() {
    if (!this.isDisposed) {
      this.outputChannel.dispose();
    }
    super.dispose();
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

  override trace(message?: any, ...optionalParams: any[]): void {
    super.trace(message, ...optionalParams);
    this._ensureOutputChannel();
    this.outputChannel.trace(message, ...optionalParams);
  }

  override debug(message?: any, ...optionalParams: any[]): void {
    super.debug(message, ...optionalParams);
    this._ensureOutputChannel();
    this.outputChannel.debug(message, ...optionalParams);
  }

  override info(message?: any, ...optionalParams: any[]): void {
    super.info(message, ...optionalParams);
    this._ensureOutputChannel();
    this.outputChannel.info(message, ...optionalParams);
  }

  override warn(message?: any, ...optionalParams: any[]): void {
    super.warn(message, ...optionalParams);
    this._ensureOutputChannel();
    this.outputChannel.warn(message, ...optionalParams);
  }

  override error(message?: any, ...optionalParams: any[]): void {
    super.error(message, ...optionalParams);
    this._ensureOutputChannel();
    this.outputChannel.error(message, ...optionalParams);
  }
}

type CpWriteStreamOptions = Parameters<typeof fs.createWriteStream>[1];

export class FileLogger extends LoggerBase {
  protected levelValue: LogLevel;
  private stream: fs.WriteStream;

  public get level(): LogLevelName {
    return LogLevel[this.levelValue] as LogLevelName;
  }

  public set level(value: LogLevelName) {
    this.levelValue = LogLevel[value];
  }

  constructor(
    private readonly filePath: string,
    level: LogLevelName,
    options: CpWriteStreamOptions = { flags: "a", flush: true },
    base?: LoggerBase,
  ) {
    super(base);
    const dir = path.dirname(filePath);
    fs.mkdirSync(dir, { recursive: true });
    this.stream = fs.createWriteStream(filePath, options);
    this.levelValue = LogLevel[level];
  }

  override dispose(): any {
    if (!this.isDisposed) {
      this.trace("Logger dispose.");
      this.stream.end();
    }
    super.dispose();
  }

  private logToFile(
    level: string,
    message?: any,
    ...optionalParams: any[]
  ): void {
    const timestamp = new Date().toISOString();
    const parts = [message, ...optionalParams].map((item) =>
      typeof item === "string" ? item : JSON.stringify(item),
    );
    const line = `${timestamp} [${level}] ${parts.join(" ")}\n`;
    this.stream.write(line);
  }

  override error(message?: any, ...optionalParams: any[]): void {
    super.error(message, ...optionalParams);
    if (this.levelValue >= LogLevel.error)
      this.logToFile("ERROR", message, ...optionalParams);
  }

  override warn(message?: any, ...optionalParams: any[]): void {
    super.warn(message, ...optionalParams);
    if (this.levelValue >= LogLevel.warn)
      this.logToFile("WARN ", message, ...optionalParams);
  }

  override info(message?: any, ...optionalParams: any[]): void {
    super.info(message, ...optionalParams);
    if (this.levelValue >= LogLevel.info)
      this.logToFile("INFO ", message, ...optionalParams);
  }

  override debug(message?: any, ...optionalParams: any[]): void {
    super.debug(message, ...optionalParams);
    if (this.levelValue >= LogLevel.debug)
      this.logToFile("DEBUG", message, ...optionalParams);
  }

  override trace(message?: any, ...optionalParams: any[]): void {
    super.trace(message, ...optionalParams);
    if (this.levelValue >= LogLevel.trace)
      this.logToFile("TRACE", message, ...optionalParams);
  }
}
