export default {
  'RelateObjects': `
__RelateObjects__ assigns relationships; all objects (e.g., speckles) within a parent object (e.g., nucleus) become its children.

This module allows you to associate <em>child</em> objects with <em>parent</em> objects. This is useful for counting the number of children associated with each parent, and for calculating mean measurement values for all children that are associated with each parent.

An object will be considered a child even if the edge is the only partly touching a parent object. If a child object is touching multiple parent objects, the object will be assigned to the parent with maximal overlap. For an alternate approach to assigning parent/child relationships, consider using the __MaskObjects__ module.

If you want to include child objects that lie outside but still near parent objects, you might want to expand the parent objects using __ExpandOrShrink__ or __IdentifySecondaryObjects__.

Supports 2D? YES

Supports 3D? YES

Respects masks? YES

__Measurements made by this module__

Parent object measurements:
  * <em>Count</em>: the number of child sub-objects for each parent object.
  * <em>Mean measurements</em>: the mean of the child object measurements, calculated for each parent object.

Child object measurements:
  * <em>Parent</em>: the label number of the parent object, as assigned by an __Identify__ or __Watershed__ module.
  * <em>Distances</em>: the distance of each child object to its respective parent.`
};
