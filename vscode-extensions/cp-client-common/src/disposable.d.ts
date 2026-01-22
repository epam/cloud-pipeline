export interface IDisposable {
    dispose(): void;
}
export declare function disposeAll(disposables: IDisposable[]): void;
export declare abstract class Disposable implements IDisposable {
    private _isDisposed;
    protected _disposables: IDisposable[];
    dispose(): void;
    protected _register<T extends IDisposable>(value: T): T;
    protected get isDisposed(): boolean;
}
