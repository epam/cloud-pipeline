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

// Core logger interface
export interface ILogger {
  error(message?: any, ...optionalParams: any[]): void;
  warn(message?: any, ...optionalParams: any[]): void;
  info(message?: any, ...optionalParams: any[]): void;
  debug(message?: any, ...optionalParams: any[]): void;
  trace(message?: any, ...optionalParams: any[]): void;
}

// Base logger implementation - framework-agnostic
export class LoggerBase extends Disposable implements ILogger {
  constructor(private readonly base?: ILogger & (Disposable | object)) {
    super();
  }

  override dispose(): void {
    if (this.base instanceof Disposable) {
      this.base.dispose();
      // @ts-expect-error readonly cleanup
      this.base = undefined;
    }
    super.dispose();
  }

  public error(message?: any, ...optionalParams: any[]): string {
    const errMsg = this.errorToText(message);
    if (this.base) this.base.error(errMsg, ...optionalParams);
    return errMsg;
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

  private errorToText(err: any): string {
    if (typeof err === "string") {
      return err;
    } else if (err instanceof Error) {
      return err.stack ?? err.message;
    } else if (err && typeof err.toString === "function") {
      return err.toString();
    } else {
      return JSON.stringify(err);
    }
  }
}

type WriteStreamOptions = Parameters<typeof fs.createWriteStream>[1];

// File logger - writes to filesystem
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
    options: WriteStreamOptions = { flags: "a", flush: true },
    base?: ILogger,
  ) {
    super(base);
    const dir = path.dirname(filePath);
    fs.mkdirSync(dir, { recursive: true });
    this.stream = fs.createWriteStream(filePath, options);
    this.levelValue = LogLevel[level];
  }

  override dispose(): void {
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

  override error(message?: any, ...optionalParams: any[]): string {
    const errMsg = super.error(message, ...optionalParams);
    if (this.levelValue >= LogLevel.error)
      this.logToFile("ERROR", errMsg, ...optionalParams);
    return errMsg;
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

// Console logger - writes to console with level filtering
export class ConsoleLogger extends LoggerBase {
  protected levelValue: LogLevel;

  public get level(): LogLevelName {
    return LogLevel[this.levelValue] as LogLevelName;
  }

  public set level(value: LogLevelName) {
    this.levelValue = LogLevel[value];
  }

  constructor(
    private readonly console: Console,
    level: LogLevelName,
    base?: ILogger,
  ) {
    super(base);
    this.levelValue = LogLevel[level];
  }

  override error(message?: any, ...optionalParams: any[]): string {
    const errMsg = super.error(message, ...optionalParams);
    if (this.levelValue >= LogLevel.error)
      this.console.error(errMsg, ...optionalParams);
    return errMsg;
  }

  override warn(message?: any, ...optionalParams: any[]): void {
    super.warn(message, ...optionalParams);
    if (this.levelValue >= LogLevel.warn)
      this.console.warn(message, ...optionalParams);
  }

  override info(message?: any, ...optionalParams: any[]): void {
    super.info(message, ...optionalParams);
    if (this.levelValue >= LogLevel.info)
      this.console.info(message, ...optionalParams);
  }

  override debug(message?: any, ...optionalParams: any[]): void {
    super.debug(message, ...optionalParams);
    if (this.levelValue >= LogLevel.debug)
      this.console.debug(message, ...optionalParams);
  }

  override trace(message?: any, ...optionalParams: any[]): void {
    super.trace(message, ...optionalParams);
    if (this.levelValue >= LogLevel.trace)
      this.console.trace(message, ...optionalParams);
  }
}
