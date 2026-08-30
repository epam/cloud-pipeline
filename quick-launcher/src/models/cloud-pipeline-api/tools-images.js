import getToolImage from './tool-image';

export default function getToolsImages(tools) {
  return new Promise((resolve) => {
    const getToolImageWrapper = (id) => new Promise((r) => {
      getToolImage(id)
        .then(b => r({id, blob: b}))
        .catch(() => r({id, blob: undefined}));
    });
    Promise.all(tools.map(getToolImageWrapper))
      .then(images => {
        resolve(
          images
            .filter(i => i.blob)
            .reduce((r, c) => ({...r, [c.id]: c.blob}), {})
        )
      })
      .catch(() => resolve({}));
  });
}
