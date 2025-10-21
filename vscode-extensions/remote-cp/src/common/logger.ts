import * as vscode from "vscode";
import { Disposable } from "./disposable";
import * as fs from "fs";
import * as path from "path";

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

export class LoggerBase extends Disposable implements ILogger {
  protected levelValue: LogLevel;

  constructor(
    level: LogLevelName,
    private readonly base?: LoggerBase,
  ) {
    super();
    this.levelValue = LogLevel[level];
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
    super(level, base);
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

  override trace(message?: any, ...optionalParams: any[]): void {
    super.trace(message, ...optionalParams);
    this._ensureOutputChannel();
    if (this.levelValue >= LogLevel.trace)
      this.outputChannel.trace(message, ...optionalParams);
  }

  override debug(message?: any, ...optionalParams: any[]): void {
    super.debug(message, ...optionalParams);
    this._ensureOutputChannel();
    if (this.levelValue >= LogLevel.debug)
      this.outputChannel.debug(message, ...optionalParams);
  }

  override info(message?: any, ...optionalParams: any[]): void {
    super.info(message, ...optionalParams);
    this._ensureOutputChannel();
    if (this.levelValue >= LogLevel.info)
      this.outputChannel.info(message, ...optionalParams);
  }

  override warn(message?: any, ...optionalParams: any[]): void {
    super.warn(message, ...optionalParams);
    this._ensureOutputChannel();
    if (this.levelValue >= LogLevel.warn)
      this.outputChannel.warn(message, ...optionalParams);
  }

  override error(message?: any, ...optionalParams: any[]): void {
    super.error(message, ...optionalParams);
    this._ensureOutputChannel();
    if (this.levelValue >= LogLevel.error)
      this.outputChannel.error(message, ...optionalParams);
  }
}

type CpWriteStreamOptions = Parameters<typeof fs.createWriteStream>[1];

export class FileLogger extends LoggerBase {
  private stream: fs.WriteStream;

  constructor(
    private readonly filePath: string,
    level: LogLevelName,
    options: CpWriteStreamOptions = { flags: "a", flush: true },
    base?: LoggerBase,
  ) {
    super(level, base);
    const dir = path.dirname(filePath);
    try {
      fs.mkdirSync(dir, { recursive: true });
    } catch {
      // ignore
    }
    this.stream = fs.createWriteStream(filePath, options);
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
    this.logToFile("ERROR", message, ...optionalParams);
  }

  override warn(message?: any, ...optionalParams: any[]): void {
    super.warn(message, ...optionalParams);
    this.logToFile("WARN ", message, ...optionalParams);
  }

  override info(message?: any, ...optionalParams: any[]): void {
    super.info(message, ...optionalParams);
    this.logToFile("INFO ", message, ...optionalParams);
  }

  override debug(message?: any, ...optionalParams: any[]): void {
    super.debug(message, ...optionalParams);
    this.logToFile("DEBUG", message, ...optionalParams);
  }

  override trace(message?: any, ...optionalParams: any[]): void {
    super.trace(message, ...optionalParams);
    this.logToFile("TRACE", message, ...optionalParams);
  }
}
