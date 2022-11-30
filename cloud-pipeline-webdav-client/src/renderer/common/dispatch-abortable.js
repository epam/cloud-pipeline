/**
 * @param {function} dispatch
 * @param {function(dispatch: function, aborted: function, *):Promise} fn
 * @param {*} options
 * @returns {(function(): void)}
 */
export default function dispatchAbortable(dispatch, fn, ...options) {
  let aborted = false;
  const dispatchIfNotAborted = (action) => {
    if (!aborted) {
      dispatch(action);
    }
  };
  (fn)(dispatchIfNotAborted, () => aborted, ...options);
  return () => { aborted = true; };
}
