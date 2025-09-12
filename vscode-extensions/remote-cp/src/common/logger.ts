import { info } from "console";
import * as vscode from "vscode";

export interface ILogger {
  // log(message?: any, ...optionalParams: any[]): void;
  warn(message?: any, ...optionalParams: any[]): void;
  error(message?: any, ...optionalParams: any[]): void;
  info(message?: any, ...optionalParams: any[]): void;
  debug(message?: any, ...optionalParams: any[]): void;
  trace(message?: any, ...optionalParams: any[]): void;
}

export class Logger implements ILogger {
  private outputChannel!: vscode.LogOutputChannel;

  constructor(private readonly label: string) {}

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

  public trace(message?: any, ...optionalParams: any[]): void {
    this._ensureOutputChannel();
    this.outputChannel.trace(message, ...optionalParams);
  }

  public debug(message?: any, ...optionalParams: any[]): void {
    this._ensureOutputChannel();
    this.outputChannel.debug(message, ...optionalParams);
  }

  public info(message?: any, ...optionalParams: any[]): void {
    this._ensureOutputChannel();
    this.outputChannel.info(message, ...optionalParams);
  }

  public warn(message?: any, ...optionalParams: any[]): void {
    this._ensureOutputChannel();
    this.outputChannel.warn(message, ...optionalParams);
  }

  public error(message?: any, ...optionalParams: any[]): void {
    this._ensureOutputChannel();
    this.outputChannel.error(message, ...optionalParams);
  }
}
