from pydantic import BaseModel, Field


class ClusterInfo(BaseModel):
    mode: str | None = None
    node_count: int | None = None
    default_nodes_count: int | None = None
    sge: bool | None = None
    slurm: bool | None = None
    spark: bool | None = None
    kubernetes: bool | None = None
    hybrid: bool | None = None

    @property
    def is_cluster(self) -> bool:
        if self.mode:
            return self.mode.lower() in {"cluster", "auto-scaled", "autoscaled", "auto-scale", "autoscale"}
        return False

    @property
    def is_auto_scaled(self) -> bool:
        if self.mode:
            return self.mode.lower() in {"auto-scaled", "autoscaled", "auto-scale", "autoscale"}
        return False


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
    node_count: int | None = None

    def to_json(self) -> dict:
        return self.model_dump(mode="json", by_alias=True)