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
        if (args != null && !(args instanceof PipeTunnelInfo))
          throw new Error(
            `Unexpected args '${this.props.action}' onStart action`,
          );
        const onStartV = await cpExt.cpExtConfig.getOnStart();
        await cpExt.cpExtConfig.setOnStart([
          ...onStartV,
          {
            when: OnStartWhen.onWillResolve,
            action: OnStartAction.useTunnel,
            data: args as PipeTunnelInfo,
          },
        ]);
        vscode.commands.executeCommand("vscode.openFolder").then(() => {
          // void vscode.commands.executeCommand(Commands.config.save);
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
