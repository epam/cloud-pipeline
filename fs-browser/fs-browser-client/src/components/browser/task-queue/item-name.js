export default function (path) {
  return (path || '').split('/').pop();
}
