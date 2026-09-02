import * as vscode from "vscode";
import { ILogger } from "../common/logger";

export interface ICpCodeContext {
  isUpdatingPipeClient: boolean;
}

export class CpCodeContext {
  static keys = {
    cpRunView: {
      isUpdatingPipeClient: "cpRunView:isUpdatingPipeClient",
    },
  };

  private _isUpdatingPipeClientCounter: number = 1;

  get isUpdatingPipeClient(): boolean {
    return this._isUpdatingPipeClientCounter > 0;
  }
  set isUpdatingPipeClient(value: boolean) {
    this._isUpdatingPipeClientCounter += value ? 1 : -1;

    this.setContext(
      CpCodeContext.keys.cpRunView.isUpdatingPipeClient,
      this._isUpdatingPipeClientCounter > 0,
    );
  }

  constructor(private readonly logger: ILogger) {
    this.isUpdatingPipeClient = false;
  }

  private setContext(key: string, value: any): Thenable<unknown> {
    return vscode.commands.executeCommand("setContext", key, value);
  }
}
