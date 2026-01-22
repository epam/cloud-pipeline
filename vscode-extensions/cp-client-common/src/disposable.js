"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Disposable = void 0;
exports.disposeAll = disposeAll;
// disposeAll utility - disposes all items in array
function disposeAll(disposables) {
    while (disposables.length) {
        const item = disposables.pop();
        if (item) {
            item.dispose();
        }
    }
}
// Base Disposable class - framework-agnostic (no VS Code dependency)
class Disposable {
    constructor() {
        this._isDisposed = false;
        this._disposables = [];
    }
    dispose() {
        if (this._isDisposed) {
            return;
        }
        this._isDisposed = true;
        disposeAll(this._disposables);
    }
    _register(value) {
        if (this._isDisposed) {
            value.dispose();
        }
        else {
            this._disposables.push(value);
        }
        return value;
    }
    get isDisposed() {
        return this._isDisposed;
    }
}
exports.Disposable = Disposable;
//# sourceMappingURL=disposable.js.map