/**
 * Common types and interfaces for CLI
 */

export interface GlobalOptions {
  logLevel?: string;
  user?: string;
  noclean?: boolean;
  debug?: boolean;
  trace?: boolean;
}

export interface TunnelStartCommandOptions extends GlobalOptions {
  localPort?: string;
  remotePort?: string;
  connectionTimeout?: string;
  ssh?: boolean;
  sshPath?: string;
  sshHost?: string;
  sshUser?: string;
  sshKeep?: boolean;
  direct?: boolean;
  foreground?: boolean;
  keepExisting?: boolean;
  keepSame?: boolean;
  replaceExisting?: boolean;
  replaceDifferent?: boolean;
  ignoreExisting?: boolean;
  ignoreOwner?: boolean;
  region?: string;
  logFile?: string;
  timeout?: string;
  timeoutStop?: string;
}

export interface TunnelStopCommandOptions extends GlobalOptions {
  localPort?: string;
  force?: boolean;
  timeoutStop?: string;
}
