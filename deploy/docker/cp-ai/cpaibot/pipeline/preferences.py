import json
from typing import Any
from logging import Logger
from cpaibot.pipeline.parameters import (CP_CAP_DIND_CONTAINER,
                                         CP_CAP_SINGULARITY,
                                         CP_CAP_SYSTEMD_CONTAINER,
                                         CP_CAP_DESKTOP_NM,
                                         CP_CAP_MODULES,
                                         CP_DISABLE_HYPER_THREADING,
                                         CP_CAP_DCV,
                                         CP_CAP_DCV_DESKTOP,
                                         CP_CAP_DCV_WEB)
from cpaibot.pipeline.types import RunCapability, Preference, ProviderAutoScalingConfiguration
from cpaibot.pipeline.utilities import (perform_cp_api_request,
                                        timed_cache)
from cpaibot.pipeline.logger import cp_api_logger


RunCapabilityDinD = RunCapability(
    name='DinD',
    parameter=CP_CAP_DIND_CONTAINER,
    platforms=['not windows'],
)

RunCapabilitySingularity = RunCapability(
    name = 'Singularity',
    parameter = CP_CAP_SINGULARITY,
    platforms = ['not windows'],
)
RunCapabilitySystemD = RunCapability(
    name = 'SystemD',
    parameter = CP_CAP_SYSTEMD_CONTAINER,
    os = ['centos*', 'rocky*'],
    platforms = ['not windows'],
    cloud = [],
    visible = True,
    capabilities = [],
)
RunCapabilityNoMachine = RunCapability(
    name = 'NoMachine',
    parameter = CP_CAP_DESKTOP_NM,
    platforms = ['not windows'],
)
RunCapabilityModule = RunCapability(
    name = 'Module',
    parameter = CP_CAP_MODULES,
    platforms = ['not windows'],
)
RunCapabilityDisableHyperThreading = RunCapability(
    name = 'Disable Hyper-Threading',
    parameter = CP_DISABLE_HYPER_THREADING,
    platforms = ['not windows'],
)
RunCapabilityNiceDcv = RunCapability(
    name = 'NICE DCV',
    parameter = CP_CAP_DCV,
    params = {
        CP_CAP_DCV_DESKTOP: True,
        CP_CAP_DCV_WEB: True,
        CP_CAP_SYSTEMD_CONTAINER: True,
    },
    cloud = ['aws'],
    os = ['centos 7*', 'rocky*', 'ubuntu 18.04', 'ubuntu 20.04'],
    platforms = ['not windows'],
)

predefined_capabilities: list[RunCapability] = [
    RunCapabilityDinD,
    RunCapabilitySingularity,
    RunCapabilitySystemD,
    RunCapabilityNoMachine,
    RunCapabilityModule,
    RunCapabilityDisableHyperThreading,
    RunCapabilityNiceDcv,
]


def get_preference(
        name: str,
        logger: Logger | None = None
) -> Preference | None:
    if logger is None:
        logger = cp_api_logger
    try:
        data = perform_cp_api_request(f'preferences/{name}')
        return Preference.from_payload(data)
    except BaseException as e:
        logger.error(f'error fetching "{name}" preference',
                     exc_info=e)
        return None


def get_preference_value(name: str) -> Any:
    preference = get_preference(name)
    if preference is not None and preference.value is not None:
        value = preference.value
        if preference.type.lower() == 'string':
            return str(value)
        if preference.type.lower() in {'int', 'number', 'integer'}:
            try:
                return int(preference.value)
            except:
                return None
        if preference.type.lower() in {'float', 'decimal'}:
            try:
                return float(preference.value)
            except:
                return None
        if preference.type.lower() in {'object'}:
            try:
                return json.loads(str(preference.value))
            except:
                return None
        if preference.type.lower() in {'bool', 'boolean'}:
            return str(preference.value).lower() in {'true', 'yes'}
        return str(preference.value)
    return None


@timed_cache()
def get_run_capabilities() -> list[RunCapability]:
    payload = get_preference_value('launch.capabilities')
    result = predefined_capabilities[:]
    result.extend(RunCapability.capabilities_list_from_payload(payload))
    return result


def get_run_capabilities_parameters() -> list[str]:
    """Returns all parameters associated with run capabilities"""
    capabilities = get_run_capabilities()
    result: list[str] = []
    for cap in capabilities:
        result.extend(cap.all_parameters)
    return list(set(result))


@timed_cache()
def get_autoscaling_configuration() -> list[ProviderAutoScalingConfiguration]:
    payload = get_preference_value('ge.autoscaling.scale.multi.queues.template')
    return ProviderAutoScalingConfiguration.configurations_from_payload(payload)


def get_autoscaling_configuration_parameters() -> list[str]:
    autoscaling_cfg = get_autoscaling_configuration()
    result: list[str] = []
    for cfg in autoscaling_cfg:
        result.extend(cfg.all_parameters)
    return list(set(result))

