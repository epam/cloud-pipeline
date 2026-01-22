import { ILogger } from "cp-client-common";
import { ITunnelInfo } from "cp-client-common";
/**
 * Find all active tunnel processes.
 * Uses process iteration to detect tunnel start commands.
 * Based on Python pipe-cli find_tunnels algorithm.
 */
export declare function findExistingTunnels(logger?: ILogger): Promise<ITunnelInfo[]>;
