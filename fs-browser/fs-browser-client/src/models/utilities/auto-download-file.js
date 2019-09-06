export default function (filename, url) {
  const element = document.createElement('a');
  element.setAttribute('href', decodeURIComponent(url));
  element.setAttribute('download', filename);
  element.style.display = 'none';
  document.body.appendChild(element);
  element.click();
  document.body.removeChild(element);
}
