export default {
  'ShrinkToObjectCenters': `
__ShrinkToObjectCenters__ will transform a set of objects into a label image with single points representing each object. The location of each point corresponds to the centroid of the input objects.

Note that if the object is not sufficiently <em>round</em>, the resulting single pixel will reside outside the original object. For example, a ‘U’ shaped object, perhaps a <em>C. Elegans</em>, could potentially lead to this special case. This could be a concern if these points are later used as seeds or markers for a __Watershed__ operation further in the pipeline.

Supports 2D? YES

Supports 3D? YES

Respects masks? NO`
};
