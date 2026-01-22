"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.FileLogger = exports.LoggerBase = exports.LogLevel = void 0;
const fs = __importStar(require("fs"));
const path = __importStar(require("path"));
const disposable_1 = require("./disposable");
var LogLevel;
(function (LogLevel) {
    LogLevel[LogLevel["error"] = 0] = "error";
    LogLevel[LogLevel["warn"] = 1] = "warn";
    LogLevel[LogLevel["info"] = 2] = "info";
    LogLevel[LogLevel["debug"] = 3] = "debug";
    LogLevel[LogLevel["trace"] = 4] = "trace";
})(LogLevel || (exports.LogLevel = LogLevel = {}));
// Base logger implementation - framework-agnostic
class LoggerBase extends disposable_1.Disposable {
    constructor(base) {
        super();
        this.base = base;
    }
    dispose() {
        if (this.base) {
            this.base.dispose();
            // @ts-expect-error readonly cleanup
            this.base = undefined;
        }
        super.dispose();
    }
    error(message, ...optionalParams) {
        const errMsg = this.errorToText(message);
        if (this.base)
            this.base.error(errMsg, ...optionalParams);
        return errMsg;
    }
    warn(message, ...optionalParams) {
        if (this.base)
            this.base.warn(message, ...optionalParams);
    }
    info(message, ...optionalParams) {
        if (this.base)
            this.base.info(message, ...optionalParams);
    }
    debug(message, ...optionalParams) {
        if (this.base)
            this.base.debug(message, ...optionalParams);
    }
    trace(message, ...optionalParams) {
        if (this.base)
            this.base.trace(message, ...optionalParams);
    }
    errorToText(err) {
        if (typeof err === "string") {
            return err;
        }
        else if (err instanceof Error) {
            return err.stack ?? err.message;
        }
        else if (err && typeof err.toString === "function") {
            return err.toString();
        }
        else {
            return JSON.stringify(err);
        }
    }
}
exports.LoggerBase = LoggerBase;
// File logger - writes to filesystem
class FileLogger extends LoggerBase {
    get level() {
        return LogLevel[this.levelValue];
    }
    set level(value) {
        this.levelValue = LogLevel[value];
    }
    constructor(filePath, level, options = { flags: "a", flush: true }, base) {
        super(base);
        this.filePath = filePath;
        const dir = path.dirname(filePath);
        fs.mkdirSync(dir, { recursive: true });
        this.stream = fs.createWriteStream(filePath, options);
        this.levelValue = LogLevel[level];
    }
    dispose() {
        if (!this.isDisposed) {
            this.trace("Logger dispose.");
            this.stream.end();
        }
        super.dispose();
    }
    logToFile(level, message, ...optionalParams) {
        const timestamp = new Date().toISOString();
        const parts = [message, ...optionalParams].map((item) => typeof item === "string" ? item : JSON.stringify(item));
        const line = `${timestamp} [${level}] ${parts.join(" ")}\n`;
        this.stream.write(line);
    }
    error(message, ...optionalParams) {
        const errMsg = super.error(message, ...optionalParams);
        if (this.levelValue >= LogLevel.error)
            this.logToFile("ERROR", errMsg, ...optionalParams);
        return errMsg;
    }
    warn(message, ...optionalParams) {
        super.warn(message, ...optionalParams);
        if (this.levelValue >= LogLevel.warn)
            this.logToFile("WARN ", message, ...optionalParams);
    }
    info(message, ...optionalParams) {
        super.info(message, ...optionalParams);
        if (this.levelValue >= LogLevel.info)
            this.logToFile("INFO ", message, ...optionalParams);
    }
    debug(message, ...optionalParams) {
        super.debug(message, ...optionalParams);
        if (this.levelValue >= LogLevel.debug)
            this.logToFile("DEBUG", message, ...optionalParams);
    }
    trace(message, ...optionalParams) {
        super.trace(message, ...optionalParams);
        if (this.levelValue >= LogLevel.trace)
            this.logToFile("TRACE", message, ...optionalParams);
    }
}
exports.FileLogger = FileLogger;
//# sourceMappingURL=logger.js.map