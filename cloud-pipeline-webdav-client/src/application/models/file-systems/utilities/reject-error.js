export default function rejectError (reject, extraMessage) {
  return error => {
    if (!extraMessage) {
      reject(error.message);
    } else {
      reject(`${error.message}. ${extraMessage}`);
    }
  }
}
