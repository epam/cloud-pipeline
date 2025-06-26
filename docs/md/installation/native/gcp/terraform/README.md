# Cloud Pipeline based on GCP GKE deployment guide

This document provides a guidance how to deploy infrastructure using `Terraform` and install Cloud Pipeline on Google Cloud.  

- [Overview](#overview)
- [Prerequisites](#prerequisites)
    - [Pre-created network](#pre-created-network)
    - [Environment variables](#environment-variables)
    - [Terraform backend](#terraform-backend)
- [Authentication and access setup](#authentication-and-access-setup)
    - [SSH key](#ssh-key-jump-host-access)
    - [Service account key](#service-account-key)
- [Module structure](#module-structure)
- [Usage](#usage)
    - [Terraform workflow](#terraform-workflow)
- [Additional notes](#additional-notes)

## Overview

This Terraform setup provisions the core infrastructure required to deploy **Cloud Pipeline** in **Google Cloud Platform (GCP)**.

It sets up:

- **GKE cluster**
- **Filestore (NFS)**
- **Cloud SQL (Private IP)**
- **Cloud Storage bucket**
- **Artifact Registry**
- **Firewall rules**
- **Jump Host** (Compute Engine VM used to install Cloud Pipeline)

Once the infrastructure is deployed, Cloud Pipeline is installed by executing scripts from your local machine to the Jump Host via SSH.

> **_Note_**: the same machine that runs Terraform must be able to SSH into the Jump Host.

## Prerequisites

### Pre-created network

An existing **VPC** and **subnet** must be defined using data blocks in your Terraform root:

```hcl
data "google_compute_network" "shared_network" {
  name    = "network-xxxxxxxx"
  project = "project-xxxxxxxx"
}

data "google_compute_subnetwork" "shared_subnet" {
  name    = "subnet-xxxxxxxx"
  region  = "<region>"
  project = "project-xxxxxxxx"
}
```

> Replace `xxxxxxxx` and `<region>` with actual values for your network, subnet, project and region.

### Environment variables

Each environment should have a corresponding `.tfvars` file located in:

```
env/<environment>/terraform.tfvars
```

Examples:

- `env/dev/terraform.tfvars`
- `env/prod/terraform.tfvars`

Define here:

- Project ID, region
- CIDR ranges
- Filestore size
- GKE settings
- Any flags

### Terraform backend

Terraform state shall be stored in a Google Cloud Storage bucket.

```hcl
terraform {
  backend "gcs" {
    bucket = "gke-main-tfstate"
    prefix = "clusters/"
  }

  required_version = ">= 1.12.1"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.37.0"
    }
  }
}
```

> **_Note_**: it is recommended to use `terraform workspace` to isolate environments, though not strictly required.

## Authentication and access setup

### SSH key (Jump Host access)

Generate a key pair before running Terraform:

```bash
ssh-keygen -t rsa -f ./gcp-key -C <cloud-pipeline@youremail.com> -b 2048
```

- `gcp-key` - private key (keep local)
- `gcp-key.pub` - public key (used in Jump Host metadata)

> **_Note_**: file must be named `gcp-key`.

### Service account key

Place the service account key in:

```
scripts/key.json
```

This service account must have the following roles:

| Role | Purpose |
|---|---|
| **`roles/compute.admin`** | Manage Compute Engine, firewall, VMs |
| **`roles/container.admin`** | Create/manage GKE clusters |
| **`roles/container.clusterAdmin`** | Cluster-wide control |
| **`roles/storage.admin`** | Manage Google Cloud Storage buckets |
| **`roles/iam.serviceAccountUser`** | Bind and impersonate service accounts |
| **`roles/iam.serviceAccountTokenCreator`** | Generate OAuth tokens |

## Module structure

| Module | Description |
|---|---|
| **`gcp-network`** | Creates VPC and subnet configuration |
| **`gke`** | Deploys the Kubernetes Engine cluster |
| **`filestore`** | NFS storage for persistent data |
| **`gcp-sql`** | Cloud SQL (PostgreSQL) with private IP |
| **`gcp-bucket`** | Cloud Storage bucket |
| **`gateway-vm`** | Jump Host VM with SSH key injected |
| **`gcp-iam`** | IAM roles, bindings, service accounts |
| **`gcp-artifact-registry`** | Artifact Registry for Docker images and artifacts |

## Usage

### Terraform workflow

```bash
# Initialize Terraform
terraform init

# (Optional) Create and select workspace
terraform workspace new dev
terraform workspace select dev

# Apply using environment tfvars
terraform plan -var-file="env/dev/terraform.tfvars"
terraform apply -var-file="env/dev/terraform.tfvars"
```

Launch described workflow for each environment you need (`dev`, `prod`, etc.).

## Additional notes

- Ensure your local machine has SSH access to the Jump Host.
- Installation scripts are automatically uploaded and executed after provisioning.
- You can disable or extend modules as needed per environment.
