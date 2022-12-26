function copyTextToClipboard (text = '') {
  return new Promise((resolve, reject) => {
    // ckeck window.clipboardData for IE11 support
    if (window.clipboardData && window.clipboardData.setData) {
      window.clipboardData.clearData();
      window.clipboardData.setData('Text', text);
      resolve();
    } else if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(text).then(() => {
        resolve();
      });
    } else {
      reject(new Error('Copy to clipboard failed'));
    }
  });
};

export default copyTextToClipboard;
