import { IApiOptions } from "cp-client-api";
import { GlobalCommandOptions } from "../types";

export class ApiOptions implements IApiOptions {
    protected constructor(
        public readonly url: string,
        public readonly token: string
    ) {
    }

    /**
     * Create APIOptions from global command options and environment variables.
     * Precedence: cmdOpts properties > environment variables > undefined
     */
    static fromCommandOptions(cmdOpts: GlobalCommandOptions): ApiOptions {
        const url = cmdOpts?.apiUrl || process.env.CP_API!;
        const token = cmdOpts?.apiToken || process.env.CP_API_TOKEN!;
        return new ApiOptions(url, token);
    }
}