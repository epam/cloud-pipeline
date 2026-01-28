docker_image_prompt = """Determine the docker image or base OS image that should be launched.

Guidelines:
- Analyze "User request", "Action", "Previous launch payload", and the context (conversation) if any.
- Extract OS/distribution names: ubuntu, centos, rocky, debian, alpine, fedora, etc.
- Extract specific versions if mentioned: ubuntu:20.04, centos:7, rocky:8, or ubuntu 22.04, centos version 7, rocky v8 etc.
- If docker image version is extracted, provide it in ":<version>" format, even if user provided in another format (space, vX, etc).
- Extract docker image references: docker.io/ubuntu-novnc:latest, myrepo/custom:v1, etc.
- If docker image / OS not specified, respond with "None"

Examples:
- "Launch ubuntu-novnc with 32GB RAM" → ubuntu-novnc
- "Start m5.2xlarge ubuntu instance" → ubuntu
- "Launch centos 7 with 200GB disk" → centos:7
- "Change image to rocky v8" → rocky:8
- "Use docker.io/myimage:v2" → docker.io/myimage:v2
- "Launch compute instance with 64GB RAM" → None
- "Start m5.2xlarge instance" → None

Respond with ONLY the image name or "None" (no quotes, no other text):
"""

instance_type_prompt_old = """Determine whether the user specifies a concrete instance type
or hardware requirements, and extract them if present.

Guidelines:
- Analyze "User request", "Action", "Previous launch payload", and the context (conversation) if any.
- Extract explicit instance types from cloud providers:
  - AWS: m5.2xlarge, t3.large, g5.12xlarge, etc.
  - GCP: n2-standard-8, a2-highgpu-1g, etc.
  - Azure: Standard_D8s_v3, Standard_NC6s_v3, etc.
- Extract hardware requirements if mentioned, even without a named instance:
  - CPU cores (e.g. "8 CPUs", "16 vCPUs")
  - RAM / memory (e.g. "32GB RAM", "64 GB memory")
  - GPU count or model (e.g. "1 GPU", "A100", "T4")
- If both instance type AND hardware specs are mentioned, extract both.
- Normalize values where possible:
  - cpu: integer number of vCPUs
  - ram: integer GB
  - gpu: integer count (ignore model unless explicitly required)
- If user only mentions OS, disk size, region, or generic "compute instance"
  with no instance type or hardware requirements, respond with None.

Output format:

- If any instance type or hardware requirement is found, respond with EXACTLY this JSON schema:
  `{
    "instance_type": <string or null>,
    "cpu": <int or null>,
    "ram": <int or null>,
    "gpu": <int or null>
  }`

- If no instance type AND no hardware requirements are specified, respond with:
  None

Examples:
- "Start m5.2xlarge ubuntu instance"
  → `{"instance_type": "m5.2xlarge", "cpu": null, "ram": null, "gpu": null}`
- "Launch instance with 16 vCPUs and 64GB RAM"
  → `{"instance_type": null, "cpu": 16, "ram": 64, "gpu": null}`
- "Need 1 GPU machine with 32GB RAM"
  → `{"instance_type": null, "cpu": null, "ram": 32, "gpu": 1}`
- "Use n2-standard-8 with 1 T4 GPU"
  → `{"instance_type": "n2-standard-8", "cpu": null, "ram": null, "gpu": 1}`
- "Launch compute instance with ubuntu"
  → None
- "Create VM in us-east-1"
  → None

Respond with ONLY the JSON object (without outer quotes) or None (no markdown, no explanation, no extra text):
"""

instance_type_prompt = """Determine whether the user specifies a concrete instance type
or hardware requirements, and extract them if present.

Guidelines:
- Analyze "User request", "Action", "Previous launch payload", and the context (conversation) if any.
- Extract explicit instance types from cloud providers:
  - AWS: m5.2xlarge, t3.large, g5.12xlarge, etc.
  - GCP: n2-standard-8, a2-highgpu-1g, etc.
  - Azure: Standard_D8s_v3, Standard_NC6s_v3, etc.
- Extract hardware requirements if mentioned, even without a named instance:
  - CPU cores (e.g. "8 CPUs", "16 vCPUs")
  - RAM / memory (e.g. "32GB RAM", "64 GB memory")
  - GPU count or model (e.g. "1 GPU", "A100", "T4")
- If both instance type AND hardware specs are mentioned, extract both.
- Cluster-aware hardware calculation:
  - If user specifies "total CPU", "total RAM", or "total memory" for a cluster:
    - Divide the total value by the number of nodes (from "Previous launch payload" cluster configuration)
    - Store the per-node value in cpu/ram fields
  - Examples: "100 total CPUs" with 10 nodes → cpu: 10
  - Examples: "256GB total RAM" with 8 nodes → ram: 32
- Normalize values where possible:
  - cpu: integer number of vCPUs per node
  - ram: integer GB per node
  - gpu: integer count (ignore model unless explicitly required)
- If user only mentions OS, disk size, region, or generic "compute instance"
  with no instance type or hardware requirements, respond with None.

Output format:

- If any instance type or hardware requirement is found, respond with EXACTLY this JSON schema:
  `{
    "instance_type": <string or null>,
    "cpu": <int or null>,
    "ram": <int or null>,
    "gpu": <int or null>
  }`

- If no instance type AND no hardware requirements are specified, respond with:
  None

Examples:
- "Start m5.2xlarge ubuntu instance"
  → `{"instance_type": "m5.2xlarge", "cpu": null, "ram": null, "gpu": null}`
- "Launch instance with 16 vCPUs and 64GB RAM"
  → `{"instance_type": null, "cpu": 16, "ram": 64, "gpu": null}`
- "Need 1 GPU machine with 32GB RAM"
  → `{"instance_type": null, "cpu": null, "ram": 32, "gpu": 1}`
- "Use n2-standard-8 with 1 T4 GPU"
  → `{"instance_type": "n2-standard-8", "cpu": null, "ram": null, "gpu": 1}`
- "Launch compute instance with ubuntu"
  → None
- "Create VM in us-east-1"
  → None
- "Create 10-node cluster with 100 total CPUs" (assuming 10 nodes from context)
  → `{"instance_type": null, "cpu": 10, "ram": null, "gpu": null}`
- "8-node cluster with 256GB total memory" (assuming 8 nodes from context)
  → `{"instance_type": null, "cpu": null, "ram": 32, "gpu": null}`
- "Cluster with 32 CPUs per node"
  → `{"instance_type": null, "cpu": 32, "ram": null, "gpu": null}`

Respond with ONLY the JSON object (without outer quotes) or None (no markdown, no explanation, no extra text):
"""

disk_size_prompt = """Determine whether the user specifies a disk size or storage requirement,
and extract it if present.

Guidelines:
- Extract explicit disk size values:
  - Examples: "100GB disk", "256 GB storage", "1TB disk", "500G volume"
- Accept both GB and TB units:
  - Normalize all values to integer GB
  - 1 TB = 1024 GB
- Accept common variations:
  - GB, G, gigabytes
  - TB, T, terabytes
- If multiple disk sizes are mentioned, extract the largest one.
- Ignore disk type unless it affects size (e.g. SSD, HDD are irrelevant here).
- Ignore OS disk vs data disk distinction; just extract size.
- If user only mentions disk type (SSD/HDD), IOPS, throughput, or "add storage"
  without a size, respond with None.
- The "request" field must contain the original user request verbatim.

Output format:

- If a disk size is found, respond with EXACTLY this JSON schema:
  `{
    "disk_gb": <int>,
    "request": <original request string>
  }`

- If no disk size is specified, respond with:
  None

Examples:
- "Create VM with 100GB disk"
  → `{"disk_gb": 100, "request": "Create VM with 100GB disk"}`
- "Need 1TB storage volume"
  → `{"disk_gb": 1024, "request": "Need 1TB storage volume"}`
- "Attach 256 GB SSD"
  → `{"disk_gb": 256, "request": "Attach 256 GB SSD"}`
- "Add storage to instance"
  → None
- "Use premium SSD"
  → None

Respond with ONLY the JSON object (without outer quotes) or None (no markdown, no explanation, no extra text):
"""

parameters_prompt = """Determine whether the user specifies any custom configuration
parameters for a job, workflow, or pipeline, and extract them if present.

Definition:
- Parameters are job- or pipeline-level configuration values.
- These are NOT infrastructure or instance configuration.

Extractable parameter types include (but are not limited to):
- Input files or datasets:
  - Examples: FASTQ, BAM, VCF, CSV, JSON, Parquet, etc.
  - Local paths, mounted paths, or cloud URIs (s3://, gs://, abfs://, https://)

- Output locations:
  - Examples: "output to s3://bucket/path", "write results to /data/output"

- Reference data:
  - Examples: reference genome, model path, index files

- Named parameters / flags:
  - Examples: "--threads=8", "--paired", "--mode fast", "--dry-run"
  - Key=value pairs, CLI-style flags, or JSON-style parameters

- Other pipeline configuration values:
  - Examples: batch size, epochs, sample name, run mode, boolean switches

Explicitly exclude:
- Infrastructure or instance configuration:
  - instance types, CPU, RAM, GPU, disk size, OS, region, zone
- Pricing, quotas, scaling, networking
- Generic phrases like "run the pipeline" with no parameters

Rules:
- Extract ONLY parameters explicitly mentioned by the user.
- Preserve parameter names as written by the user when possible.
- Normalize values lightly:
  - Booleans → true / false
  - Numbers → int or float when unambiguous
- Paths and URIs must be extracted verbatim.
- If the same parameter appears multiple times, keep the last value.
- The "request" field must contain the original user request verbatim.

Output format:

- If any parameters are found, respond with EXACTLY this JSON schema:
  `{
    "parameters": {
      "<parameter_name>": <value>,
      ...
    },
    "request": <original request string>
  }`

- If no custom parameters are specified, respond with:
  None

Examples:
- "Run pipeline with fastq1=s3://data/a.fq fastq2=s3://data/b.fq output=/results"
  → `{"parameters": {"fastq1": "s3://data/a.fq", "fastq2": "s3://data/b.fq", "output": "/results"}, "request": "Run pipeline with fastq1=s3://data/a.fq fastq2=s3://data/b.fq output=/results"}`

- "Execute with --paired --mode fast --dry-run"
  → `{"parameters": {"paired": true, "mode": "fast", "dry_run": true}, "request": "Execute with --paired --mode fast --dry-run"}`

- "Run workflow"
  → None

Respond with ONLY the JSON object (without outer quotes) or None (no markdown, no explanation, no extra text):
"""


cluster_info_prompt = """Determine the cluster configuration requested by the user,
including mode, node count, and orchestration frameworks.

Guidelines:
- Analyze "User request", "Action", "Previous launch payload", and the context (conversation) if any.
- Determine cluster mode:
  - "single": Default mode, single node (no cluster keywords present)
  - "cluster": Multi-node cluster with fixed node count
  - "auto-scaled": Cluster with autoscaling capability (user explicitly mentions autoscale variants)
- Extract node count specifications:
  - node_count: Requested/target number of worker nodes (for cluster/auto-scaled modes)
  - default_nodes_count: Default or minimum node count (for auto-scaled mode)
- Detect orchestration framework flags:
  - SGE: Sun Grid Engine, GridEngine, Grid Engine
  - SLURM: SLURM workload manager
  - SPARK: Apache Spark cluster
  - Kubernetes: K8s, Kubernetes cluster
- Framework availability by mode:
  - mode="cluster": All frameworks available (SGE, SLURM, SPARK, Kubernetes)
  - mode="auto-scaled": Only SGE, SLURM, and Kubernetes available (SPARK not supported)
  - If user requests SPARK with autoscaling, treat as mode="cluster" (fixed nodes)
- Detect HYBRID flag: hybrid cluster configuration
- Auto-scaled mode detection requires explicit keywords:
  - "autoscale", "auto-scale", "auto scale", "autoscaling", "auto-scaling"
  - If these keywords are present AND framework is not SPARK, mode is "auto-scaled"
- Node count rules:
  - For "cluster" mode: extract node_count only
  - For "auto-scaled" mode: extract both node_count (target/max) and default_nodes_count (min) if mentioned
  - For "single" mode: both should be null
- Ignore infrastructure specs (CPU, RAM, GPU, disk, instance type) - focus only on cluster topology

Output format:

- Always respond with EXACTLY this JSON schema:
  `{
    "mode": "<single|cluster|auto-scaled>",
    "node_count": <int or null>,
    "default_nodes_count": <int or null>,
    "sge": <bool>,
    "slurm": <bool>,
    "spark": <bool>,
    "kubernetes": <bool>,
    "hybrid": <bool>
  }`

Examples:
- "Launch single ubuntu instance"
  → `{"mode": "single", "node_count": null, "default_nodes_count": null, "sge": false, "slurm": false, "spark": false, "kubernetes": false, "hybrid": false}`

- "Create cluster with 5 worker nodes"
  → `{"mode": "cluster", "node_count": 5, "default_nodes_count": null, "sge": false, "slurm": false, "spark": false, "kubernetes": false, "hybrid": false}`

- "Launch auto-scaled SLURM cluster with 10 nodes"
  → `{"mode": "auto-scaled", "node_count": 10, "default_nodes_count": null, "sge": false, "slurm": true, "spark": false, "kubernetes": false, "hybrid": false}`

- "Start autoscaling GridEngine cluster, default 2 nodes, max 8 nodes"
  → `{"mode": "auto-scaled", "node_count": 8, "default_nodes_count": 2, "sge": true, "slurm": false, "spark": false, "kubernetes": false, "hybrid": false}`

- "Deploy Spark cluster with 4 workers"
  → `{"mode": "cluster", "node_count": 4, "default_nodes_count": null, "sge": false, "slurm": false, "spark": true, "kubernetes": false, "hybrid": false}`

- "Create autoscaling Spark cluster with 3 to 12 nodes"
  → `{"mode": "cluster", "node_count": 12, "default_nodes_count": null, "sge": false, "slurm": false, "spark": true, "kubernetes": false, "hybrid": false}`

- "Create hybrid Kubernetes cluster with auto-scaling, 3 to 12 nodes"
  → `{"mode": "auto-scaled", "node_count": 12, "default_nodes_count": 3, "sge": false, "slurm": false, "spark": false, "kubernetes": true, "hybrid": true}`

- "Launch m5.2xlarge instance with 64GB RAM"
  → `{"mode": "single", "node_count": null, "default_nodes_count": null, "sge": false, "slurm": false, "spark": false, "kubernetes": false, "hybrid": false}`

- "Start 20-node SGE cluster"
  → `{"mode": "cluster", "node_count": 20, "default_nodes_count": null, "sge": true, "slurm": false, "spark": false, "kubernetes": false, "hybrid": false}`

- "Launch auto-scaled Kubernetes cluster with 5 nodes"
  → `{"mode": "auto-scaled", "node_count": 5, "default_nodes_count": null, "sge": false, "slurm": false, "spark": false, "kubernetes": true, "hybrid": false}`

Respond with ONLY the JSON object (without outer quotes, no markdown, no explanation, no extra text):
"""
