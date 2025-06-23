from pydantic import BaseModel, Field


class LaunchPayload(BaseModel):
    cloud_region_id: int | None = Field(None, alias='cloudRegionId')
    docker_image: str | None = Field(None, alias='dockerImage')
    instance_type: str | None = Field(None, alias='instanceType')
    disk: str | float | int | None = Field(None, alias='disk')
    cmd: str | None = Field(None, alias='cmd')
    is_spot: bool | str | None = Field(None, alias='is_spot')
    parameters: dict | None = Field(None)
    pipeline_id: int | None = Field(None, alias='pipelineId')
    version: str | None = Field(None)