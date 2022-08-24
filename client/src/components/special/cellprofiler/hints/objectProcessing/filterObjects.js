export default {
  'FilterObjects': `
__FilterObjects__ eliminates objects based on their measurements (e.g., area, shape, texture, intensity).

This module removes selected objects based on measurements produced by another module (e.g., __MeasureObjectSizeShape__, __MeasureObjectIntensity__, __MeasureTexture__, etc). All objects that do not satisfy the specified parameters will be discarded.

This module also may remove objects touching the image border or edges of a mask. This is useful if you would like to unify images via __SplitOrMergeObjects__ before deciding to discard these objects.

Please note that the objects that pass the filtering step comprise a new object set, and hence do not inherit the measurements associated with the original objects. Any measurements on the new object set will need to be made post-filtering by the desired measurement modules.

Supports 2D? YES

Supports 3D? YES

Respects masks? YES

__Measurements made by this module__

Image measurements:
* <em>Count</em>: the number of objects remaining after filtering.

Object measurements:
* <em>Parent</em>: the identity of the input object associated with each filtered (remaining) object.
* <em>Location_X</em>, <em>Location_Y</em>, <em>Location_Z</em>: the pixel (X,Y,Z) coordinates of the center of mass of the filtered (remaining) objects.`
};
