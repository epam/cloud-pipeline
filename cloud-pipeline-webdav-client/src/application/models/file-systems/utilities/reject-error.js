export default function rejectError (reject) {
  return error => reject(error.message);
}
