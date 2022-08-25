export default {
  'ConvertObjectsToImage': `
__ConvertObjectsToImage__ converts objects you have identified into an image.

This module allows you to take previously identified objects and convert them into an image according to a colormap you select, which can then be saved with the __SaveImages__ module.

This module does not support overlapping objects, such as those produced by the UntangleWorms module. Overlapping regions will be lost during saving.

Supports 2D? YES

Supports 3D? YES

Respects masks? YES`
};
