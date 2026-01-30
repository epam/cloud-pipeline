import { ILogger } from "cp-client-common";
import { GlobalCommandOptions } from "../types";
import { ApiOptions } from "./api-options";
import { ITunnelManagerConfig, parseProxyUrl, ProxyEndpoint } from "cp-client-tunnel";

export class TunnelManagerConfig implements ITunnelManagerConfig {

    protected constructor(
        public readonly api: ApiOptions,
        public readonly proxy?: ProxyEndpoint,
        public readonly connectionTimeout: number = 30,
    ) {
    }

    static fromCommandOptions(
        cmdOpts: GlobalCommandOptions,
        _logger: ILogger
    ): TunnelManagerConfig {
        const apiOpts = ApiOptions.fromCommandOptions(cmdOpts);

        // Proxy auth: mirror pipe-cli behavior (Basic base64 user:access_key)
        const envProxyUrl = process.env.CP_PROXY_URL
            || undefined;

        const proxy = parseProxyUrl(envProxyUrl, () => {
            return {
                username: process.env.CP_PROXY_USERNAME,
                password: process.env.CP_PROXY_PASSWORD
            }
        });

        return new this(
            apiOpts,
            proxy,
            cmdOpts.connectionTimeout ? parseInt(cmdOpts.connectionTimeout) : 30,
        );
    }
}
