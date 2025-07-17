import PropTypes from 'prop-types';
import LaunchFormStringParameterInput from './default-parameter-input';
import LaunchFormBoolParameterInput from './bool-parameter-input';
import LaunchFormPathParameterInput from './path-parameter-input';
import LaunchFormEnumParameterInput from './enum-parameter-input';
import LaunchFormMetadataParameterInput from './metadata-parameter-input';
import LaunchFormSchemeParameterInput from './scheme-parameter-input/scheme-parameter-input';
import {registerRenderer, renderParameter} from './renderers';

registerRenderer('string', LaunchFormStringParameterInput, true);
registerRenderer('enum', LaunchFormEnumParameterInput);
registerRenderer('enumeration', LaunchFormEnumParameterInput);
registerRenderer('boolean', LaunchFormBoolParameterInput);
registerRenderer('bool', LaunchFormBoolParameterInput);
registerRenderer('metadata', LaunchFormMetadataParameterInput);
registerRenderer('object', LaunchFormSchemeParameterInput);
registerRenderer('array', LaunchFormSchemeParameterInput);
registerRenderer('path', LaunchFormPathParameterInput);
registerRenderer('common', LaunchFormPathParameterInput);
registerRenderer('input', LaunchFormPathParameterInput);
registerRenderer('output', LaunchFormPathParameterInput);

function LaunchFormParameterInput (props) {
  return renderParameter(props);
}

LaunchFormParameterInput.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameter: PropTypes.object,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  rawEdit: PropTypes.bool,
  currentProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  currentProjectMetadata: PropTypes.object,
  currentMetadataEntity: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  rootEntityId: PropTypes.string,
  metadataAutoComplete: PropTypes.bool
};

export default LaunchFormParameterInput;
