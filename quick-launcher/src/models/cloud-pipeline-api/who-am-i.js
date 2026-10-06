import apiGet from '../base/api-get';

let whoAmIPromise;

export default function whoAmI() {
  if (!whoAmIPromise) {
    whoAmIPromise = apiGet('whoami');
  }
  return whoAmIPromise;
}
