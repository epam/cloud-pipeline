export default {
  'MaskObjects': `
__MaskObjects__ removes objects outside of a specified region or regions.

This module allows you to delete the objects or portions of objects that are outside of a region (mask) you specify. For example, after identifying nuclei and tissue regions in previous __Identify__ modules, you might want to exclude all nuclei that are outside of a tissue region.

Supports 2D? YES

Supports 3D? NO

Respects masks? YES

__Measurements made by this module__

Parent object measurements:
  * <em>Count</em>: the number of new masked objects created from each parent object.

Masked object measurements:
  * <em>Parent</em>: the label number of the parent object.
  * <em>Location_X</em>, <em>Location_Y</em>: the pixel (X,Y) coordinates of the center of mass of the masked objects.`,

  'MaskObjects.method': `
If using a masking image, the mask is composed of the foreground (white portions); if using a masking object, the mask is composed of the area within the object. You can choose to remove only the portion of each object that is outside of the region, remove the whole object if it is partially or fully outside of the region, or retain the whole object unless it is fully outside of the region.`
};
