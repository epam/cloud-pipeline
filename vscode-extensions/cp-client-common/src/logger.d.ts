import * as fs from "fs";
import { Disposable } from "./disposable";
export declare enum LogLevel {
    error = 0,
    warn = 1,
    info = 2,
    debug = 3,
    trace = 4
}
export type LogLevelName = keyof typeof LogLevel;
export interface ILogger {
    error(message?: any, ...optionalParams: any[]): void;
    warn(message?: any, ...optionalParams: any[]): void;
    info(message?: any, ...optionalParams: any[]): void;
    debug(message?: any, ...optionalParams: any[]): void;
    trace(message?: any, ...optionalParams: any[]): void;
}
export declare class LoggerBase extends Disposable implements ILogger {
    private readonly base?;
    constructor(base?: LoggerBase | undefined);
    dispose(): void;
    error(message?: any, ...optionalParams: any[]): string;
    warn(message?: any, ...optionalParams: any[]): void;
    info(message?: any, ...optionalParams: any[]): void;
    debug(message?: any, ...optionalParams: any[]): void;
    trace(message?: any, ...optionalParams: any[]): void;
    private errorToText;
}
type WriteStreamOptions = Parameters<typeof fs.createWriteStream>[1];
export declare class FileLogger extends LoggerBase {
    private readonly filePath;
    protected levelValue: LogLevel;
    private stream;
    get level(): LogLevelName;
    set level(value: LogLevelName);
    constructor(filePath: string, level: LogLevelName, options?: WriteStreamOptions, base?: LoggerBase);
    dispose(): void;
    private logToFile;
    error(message?: any, ...optionalParams: any[]): string;
    warn(message?: any, ...optionalParams: any[]): void;
    info(message?: any, ...optionalParams: any[]): void;
    debug(message?: any, ...optionalParams: any[]): void;
    trace(message?: any, ...optionalParams: any[]): void;
}
export {};
