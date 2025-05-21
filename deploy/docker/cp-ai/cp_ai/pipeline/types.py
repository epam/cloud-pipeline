import json
from typing import Union, List, Dict, TypeVar, Type, Any, Literal
from pydantic import BaseModel, Field
from cp_ai.common.utilities import get_application_entity_url


_PipelineType = TypeVar("_PipelineType", bound="Pipeline")
_PipelineVersionType = TypeVar("_PipelineVersionType", bound="PipelineVersion")
_ConfigurationEntryType = TypeVar("_ConfigurationEntryType", bound="ConfigurationEntry")
_DockerRegistryType = TypeVar("_DockerRegistryType", bound="DockerRegistry")
_CloudRegionType = TypeVar("_CloudRegionType", bound="CloudRegion")


class Pipeline(BaseModel):
    id: int
    name: str
    description: str | None = None

    @classmethod
    def from_payload(cls: Type[_PipelineType], data: Any) -> _PipelineType | None:
        try:
            if isinstance(data, dict):
                pipeline_id = data.get('id')
                pipeline_name = data.get('name')
                if isinstance(pipeline_id, str):
                    pipeline_id = int(pipeline_id)
                if isinstance(pipeline_id, int) and isinstance(pipeline_name, str):
                    return cls(id=pipeline_id,
                               name=pipeline_name,
                               description=data.get('description'))
        except BaseException as e:
            print(e.__str__())
        return None

    @property
    def url(self) -> str | None:
        return get_application_entity_url(f'#/{self.id}')

    @property
    def md_title(self) -> str:
        """Returns pipeline name in Markdown format (with a url, if any)"""
        url = self.url
        return f'[{self.name}]({url})' if url is not None else f'{self.name} (#{self.id})'

    def get_version_url(self, version: str) -> str | None:
        return get_application_entity_url(f'#/{self.id}/{version}')

    def to_json(self) -> dict:
        m = self.model_dump(mode='json', exclude_none=True)
        url = self.url
        if url is not None:
            m.update({'url': url})
        return m

    def to_json_string(self) -> str:
        return json.dumps(self.to_json())


class PipelineVersion(BaseModel):
    id: int
    name: str
    commit_id: str
    draft: bool = False

    @classmethod
    def from_payload(cls: Type[_PipelineVersionType], data: Any) -> _PipelineVersionType | None:
        try:
            if isinstance(data, dict):
                version_id = data.get('id')
                version_name = data.get('name')
                draft = data.get('draft', False)
                commit_id = data.get('commitId', data.get('commit_id', None))
                if isinstance(version_id, str):
                    version_id = int(version_id)
                if (
                        isinstance(version_id, int) and
                        isinstance(version_name, str) and
                        isinstance(commit_id, str)
                ):
                    return cls(id=version_id,
                               name=version_name,
                               commit_id=commit_id,
                               draft=draft)
        except BaseException as e:
            print(e.__str__())
        return None


ParameterType = Union[Literal['input', 'path', 'common', 'output', 'string', 'boolean'], str]
ParameterValue = Union[str, int, float, bool]
ParametersCondition = Union[bool, str]


class ParameterValidationRule(BaseModel):
    condition: str  # alias for "throw"
    message: str


class ParameterEnumEntry(BaseModel):
    name: str
    visible: ParametersCondition


class ParameterConfig(BaseModel):
    type: ParameterType
    description: str | None = None
    value: ParameterValue | None = None
    resolved_value: ParameterValue | None = None
    section: str | None = None
    required: ParametersCondition | None = None
    visible: ParametersCondition | None = None
    no_override: ParametersCondition | None = None
    enum: List[Union[str, ParameterEnumEntry]] | None = None
    pretty_name: str | None = None

    @property
    def is_required(self) -> ParametersCondition:
        if self.required is not None:
            return self.required
        return (self.value is None) and self.no_override


_DefaultParameter = TypeVar("_DefaultParameter", bound="DefaultParameter")


class DefaultParameter(BaseModel):
    name: str
    description: str | None = None
    type: str = 'string'
    value: str | bool | int | float | None = None

    @classmethod
    def from_payload(cls: Type[_DefaultParameter], payload: Any) -> _DefaultParameter | None:
        if isinstance(payload, dict):
            try:
                name = payload.get('name')
                description = payload.get('description')
                p_type = payload.get('type', 'string')
                value = payload.get('defaultValue', payload.get('value', None))
                return cls(name=name,
                           description=description,
                           type=p_type,
                           value=value)
            except:
                pass
        return None


class CommonAutoScalingConfiguration(BaseModel):
    parameters: Dict[str, str | float | int | bool] = {}


_AutoScalingParameterConfiguration = TypeVar("_AutoScalingParameterConfiguration",
                                             bound="AutoScalingParameterConfiguration")


class AutoScalingParameterConfiguration(BaseModel):
    parameter: str
    default_value: str

    @classmethod
    def from_payload(cls: Type[_AutoScalingParameterConfiguration],
                     payload: Any) -> _AutoScalingParameterConfiguration | None:
        if isinstance(payload, dict):
            param = payload.get('parameter', payload.get('Parameter', payload.get('param', payload.get('Param'))))
            default_value = payload.get('DefaultValue', payload.get('defaultValue'))
            if isinstance(param, str) and isinstance(default_value, str | float | int | bool):
                return cls(parameter=param,
                           default_value=default_value)
        return None


_AutoScalingConfiguration = TypeVar("_AutoScalingConfiguration", bound="AutoScalingConfiguration")


class AutoScalingConfiguration(CommonAutoScalingConfiguration):
    cpu: AutoScalingParameterConfiguration
    gpu: AutoScalingParameterConfiguration

    @classmethod
    def from_payload(cls: Type[_AutoScalingConfiguration], payload: Any, /, is_hybrid = False) -> _AutoScalingConfiguration | None:
        if isinstance(payload, dict):
            cpu_key = 'FamilyTypeCPU' if is_hybrid else 'InstanceTypeCPU'
            gpu_key = 'FamilyTypeGPU' if is_hybrid else 'InstanceTypeGPU'
            params = payload.get('parameters', payload.get('Parameters', {}))
            cpu = AutoScalingParameterConfiguration.from_payload(payload.get(cpu_key))
            gpu = AutoScalingParameterConfiguration.from_payload(payload.get(gpu_key))
            if cpu is not None and gpu is not None and isinstance(params, dict):
                return cls(cpu=cpu,
                           gpu=gpu,
                           parameters=params)
        return None

    @property
    def all_parameters(self) -> list[str]:
        return list({*self.parameters.keys(), self.cpu.parameter, self.gpu.parameter})


_ProviderAutoScalingConfiguration = TypeVar("_ProviderAutoScalingConfiguration",
                                            bound="ProviderAutoScalingConfiguration")


class ProviderAutoScalingConfiguration(BaseModel):
    provider: str
    general: AutoScalingConfiguration
    hybrid: AutoScalingConfiguration

    @classmethod
    def from_payload(cls: Type[_ProviderAutoScalingConfiguration],
                     provider: str,
                     payload: Any) -> _ProviderAutoScalingConfiguration | None:
        if isinstance(payload, dict):
            general = AutoScalingConfiguration.from_payload(
                payload.get('general', payload.get('General')),
                is_hybrid=False
            )
            hybrid = AutoScalingConfiguration.from_payload(
                payload.get('hybrid', payload.get('Hybrid')),
                is_hybrid=True
            )
            if general is not None and hybrid is not None:
                return cls(provider=provider, general=general, hybrid=hybrid)
        return None

    @classmethod
    def configurations_from_payload(cls: Type[_ProviderAutoScalingConfiguration],
                                    payload: Any) -> list[_ProviderAutoScalingConfiguration]:
        result: list[_ProviderAutoScalingConfiguration] = []
        if isinstance(payload, dict):
            for provider, p in payload.items():
                try:
                    o = cls.from_payload(provider, p)
                    if o is not None:
                        result.append(o)
                except:
                    pass
        return result

    @property
    def all_parameters(self) -> list[str]:
        return list({*self.general.all_parameters, *self.hybrid.all_parameters})


ParametersMap = Dict[str, ParameterConfig]
ConditionalParameters = Dict[str, ParametersMap]


class Configuration(BaseModel):
    nonPause: bool | None = False
    cloudRegionId: int | None = None
    main_file: str | None = None
    instance_size: str | None = None
    instance_disk: Union[str, int, float] | None = None
    docker_image: str | None = None
    timeout: int | None = None
    cmd_template: str | None = None
    language: str | None = None
    parameters: ParametersMap | None = None
    conditional_parameters: ConditionalParameters | None = None
    is_spot: bool | None = None


class ConfigurationEntry(BaseModel):
    name: str
    default: bool | None = False
    description: str | None = None
    configuration: Configuration

    @classmethod
    def from_payload(cls: Type[_ConfigurationEntryType], data: Any) -> _ConfigurationEntryType | None:
        try:
            if isinstance(data, dict):
                return cls(**data)
        except BaseException as e:
            print(e.__str__())
        return None


class Tool(BaseModel):
    id: int
    image: str
    description: str | None = None
    shortDescription: str | None = None


class DockerImage(Tool):
    registry: "DockerRegistry"
    group: "ToolsGroup"

    @property
    def full_image(self) -> str:
        return f'{self.registry.path}/{self.image}'

    @property
    def url(self) -> str | None:
        return get_application_entity_url(f'#/tool/{self.id}')


class ToolsGroup(BaseModel):
    id: int
    name: str
    tools: list[Tool] = []
    privateGroup: bool = False


class DockerRegistry(BaseModel):
    id: int
    name: str
    path: str
    groups: list[ToolsGroup] = []

    @classmethod
    def from_payload(cls: Type[_DockerRegistryType], data: Any) -> _DockerRegistryType | None:
        try:
            if isinstance(data, dict):
                return cls(**data)
        except BaseException as e:
            print(e.__str__())
        return None


_DockerImageSettings = TypeVar("_DockerImageSettings", bound="DockerImageSettings")


class DockerImageSettings(BaseModel):
    version: str
    platform: str | None = None
    settings: list[ConfigurationEntry] = []

    @classmethod
    def from_payload(cls: Type[_DockerImageSettings], payload: Any) -> _DockerImageSettings | None:
        try:
            if isinstance(payload, dict):
                settings = [ConfigurationEntry.from_payload(p) for p in payload.get('settings', [])]
                settings = [s for s in settings if s is not None]
                version = payload.get('version')
                platform = payload.get('platform')
                if isinstance(version, str):
                    return cls(version=version, platform=platform, settings=settings)
        except BaseException as e:
            print(e.__str__())
        return None


class CloudRegion(BaseModel):
    id: int
    provider: str
    name: str
    regionId: str
    default: bool = False

    @classmethod
    def from_payload(cls: Type[_CloudRegionType], data: Any) -> _CloudRegionType | None:
        try:
            if isinstance(data, dict):
                return cls(**data)
        except BaseException as e:
            print(e.__str__())
        return None


_RunCapabilityConfig = TypeVar("_RunCapabilityConfig", bound="RunCapabilityConfig")
_RunCapability = TypeVar("_RunCapability", bound="RunCapability")


class RunCapabilityConfig(BaseModel):
    description: str | None = None
    commands: list[str] | None = None
    params: Dict[str, str | bool | int | float] | None = None
    cloud: list[str] | None = None
    platforms: list[str] | None = None
    os: list[str] | None = None
    visible: bool = True
    multiple: bool = False
    capabilities: list["RunCapability"] | None = None

    @staticmethod
    def parse_list(value: Any) -> list[str]:
        if value is None:
            return []
        if isinstance(value, str):
            value = value.split(',')
            value = [v.strip() for v in value if len(v.strip()) > 0]
        if isinstance(value, list):
            value = [v.strip() for v in value if len(v.strip()) > 0]
            if 'all' in value:
                return []
            return value
        return []

    @classmethod
    def from_payload(cls: Type[_RunCapabilityConfig], payload: Any) -> _RunCapabilityConfig | None:
        if isinstance(payload, dict):
            try:
                description = payload.get('description', None)
                commands = cls.parse_list(payload.get('commands', None))
                params = payload.get('params', None)
                visible = payload.get('visible', True)
                cloud = cls.parse_list(payload.get('cloud', 'all'))
                platforms = cls.parse_list(payload.get('platforms', ''))
                os = cls.parse_list(payload.get('os', ''))
                disclaimer = payload.get('disclaimer', None)
                multiple = payload.get('multiple', False)
                capabilities = payload.get('capabilities', None)
                return cls(
                    description=description,
                    commands=commands,
                    params=params,
                    cloud=cloud,
                    platforms=platforms,
                    os=os,
                    visible=visible,
                    multiple=multiple,
                    capabilities=RunCapability.from_payload(capabilities)
                )
            except:
                pass
        return None


class RunCapability(RunCapabilityConfig):
    name: str
    parameter: str

    @classmethod
    def capabilities_list_from_payload(cls: Type[_RunCapability], payload: Any) -> list[_RunCapability]:
        result: list[_RunCapability] = []
        if isinstance(payload, dict):
            for name, cfg in payload.items():
                capability_cfg = RunCapabilityConfig.from_payload(cfg)
                if capability_cfg is not None:
                    try:
                        capability = cls(
                            **capability_cfg.model_dump(),
                            name=name,
                            parameter=f'CP_CAP_CUSTOM_{name}'
                        )
                        result.append(capability)
                    except:
                        pass
        return result

    @property
    def all_parameters(self) -> list[str]:
        p = [self.parameter]
        p.extend((self.params or {}).keys())
        if self.capabilities:
            for c in self.capabilities:
                p.extend(c.all_parameters)
        return list(set(p))


_Preference = TypeVar("_Preference", bound="Preference")


class Preference(BaseModel):
    name: str
    type: str
    value: str | bool | int | float | None = None

    @classmethod
    def from_payload(cls: Type[_Preference], payload: Any) -> _Preference | None:
        if isinstance(payload, dict):
            try:
                name = payload.get('name')
                pref_type = payload.get('type', 'string')
                value = payload.get('value')
                if name is not None:
                    return cls(name=name,
                               type=pref_type,
                               value=value)
            except:
                pass
        return None


class InstanceType(BaseModel):
    sku: str
    name: str
    instance_family: str | None = Field(None, alias='instanceFamily')
    vcpu: int = 0
    memory: int = 0
    gpu: int = 0
    region_id: int | None = Field(None, alias='regionId')

