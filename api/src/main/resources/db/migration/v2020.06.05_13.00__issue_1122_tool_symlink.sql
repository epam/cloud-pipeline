ALTER TABLE pipeline.tool ADD link BIGINT REFERENCES pipeline.tool(id);
ALTER TABLE pipeline.pipeline_run ADD actual_docker_image TEXT NULL;
UPDATE pipeline.pipeline_run SET actual_docker_image = docker_image;