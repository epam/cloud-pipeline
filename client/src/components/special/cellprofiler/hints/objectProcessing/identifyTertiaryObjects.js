export default {
  'IdentifyTertiaryObjects': `
__IdentifyTertiaryObjects__ identifies tertiary objects (e.g., cytoplasm) by removing smaller primary objects (e.g., nuclei) from larger secondary objects (e.g., cells), leaving a ring shape.

Supports 2D? YES

Supports 3D? NO

Respects masks? YES

__Measurements made by this module__

Image measurements:
  * <em>Count</em>: the number of tertiary objects identified.

Object measurements:
  * <em>Parent</em>: the identity of the primary object and secondary object associated with each tertiary object.
  * <em>Location_X</em>, <em>Location_Y</em>: the pixel (X,Y) coordinates of the center of mass of the identified tertiary objects.
`,

  'IdentifyTertiaryObjects.large': `
This module will take the smaller identified objects and remove them from the larger identified objects. For example, “subtracting” the nuclei from the cells will leave just the cytoplasm, the properties of which can then be measured by downstream __Measure__ modules. The larger objects should therefore be equal in size or larger than the smaller objects and must completely contain the smaller objects; __IdentifySecondaryObjects__ will produce objects that satisfy this constraint. Ideally, both inputs should be objects produced by prior __Identify__ modules.`,

  'IdentifyTertiaryObjects.small': `
This module will take the smaller identified objects and remove them from the larger identified objects. For example, “subtracting” the nuclei from the cells will leave just the cytoplasm, the properties of which can then be measured by downstream __Measure__ modules. The larger objects should therefore be equal in size or larger than the smaller objects and must completely contain the smaller objects; __IdentifySecondaryObjects__ will produce objects that satisfy this constraint. Ideally, both inputs should be objects produced by prior __Identify__ modules.`,

  'IdentifyTertiaryObjects.output': `
A set of objects are produced by this module, which can be used in downstream modules for measurement purposes or other operations. Because each tertiary object is produced from primary and secondary objects, there will always be at most one tertiary object for each larger object.

Note that if the smaller objects are not completely contained within the larger objects, creating subregions using this module can result in objects with a single label (that is, identity) that nonetheless are not contiguous. This may lead to unexpected results when running measurement modules such as __MeasureObjectSizeShape__ because calculations of the perimeter, aspect ratio, solidity, etc. typically make sense only for contiguous objects. Other modules, such as __MeasureImageIntensity__, are not affected and will yield expected results.

<em>Note on saving images</em>: You can pass the objects along to the <em>Object Processing</em> module __ConvertObjectsToImage__ to create an image. This image can be saved with the __SaveImages__ module. Additionally, you can use the __OverlayOutlines__ or __OverlayObjects__ module to overlay outlines or objects, respectively, on a base image. The resulting image can also be saved with the __SaveImages__ module.`
};
