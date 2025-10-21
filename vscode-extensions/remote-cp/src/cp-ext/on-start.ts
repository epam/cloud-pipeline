import * as vscode from "vscode";

import { CpExtension } from ".";
import { PipeTunnelInfo } from "../cp-client";

export enum OnStartWhen {
  onDidResolve = "onDidResolve",
  onWillResolve = "onWillResolve",
}

export enum OnStartAction {
  openFolder = "openFolder",
  useTunnel = "useTunnel",
}

export interface OnStartProps {
  when: OnStartWhen;
  action: OnStartAction;
  data?: any;
}

export class OnStartOption {
  constructor(private readonly props: OnStartProps) {}

  public async run(cpExt: CpExtension, args: any): Promise<any> {
    switch (this.props.action) {
      case OnStartAction.openFolder: {
        vscode.commands.executeCommand("vscode.openFolder");
        if (args != null && !(args instanceof PipeTunnelInfo))
          throw new Error(
            `Unexpected args '${this.props.action}' onStart action`,
          );
        cpExt.cpExtConfig.onStart.push({
          when: OnStartWhen.onWillResolve,
          action: OnStartAction.useTunnel,
          data: args as PipeTunnelInfo,
        });
        break;
      }

      case OnStartAction.useTunnel: {
        return this.props.data as PipeTunnelInfo;
        break;
      }
    }
  }
}
