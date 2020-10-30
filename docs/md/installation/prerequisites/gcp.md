# GCP 

**Note**: Account specific information shall be updated according to the environment:

* Project ID: `<project-id>`
* Region: `<region-id>`

## VPC

A new VPC in the `<project-id>` project with the default configuration

## Subnets

* A routable subnet with /26 CIDR or a subnet with the Public IPs allowed. It will be used to deploy user-facing services
* Non routable subnets(s) with any private CIDR range (e.g. /16) in the <region-id>. These subnets will be used to launch the worker nodes

```
Note: for small deployments or POCs - a single small routable subnet is enough
```
 
## Firewall rules

* CP-Cluster-Internal:
  * Targets: ALL
  * Ports:
    * TCP: 0-65535
    * UDP: 0-65535
    * ICMP
  * IP Ranges: `VPC Subnets CIDR`
  * Type: Ingress

* CP-HTTPS-Access:
  * Targets: ALL
  * Ports:
    * TCP: 443
  * IP Ranges: `Internal (on-prem) networks or 0.0.0.0 (for the Public IPs usage)`
  * Type: Ingress

* CP-Internet-Access:
  * Targets: ALL
  * Ports:
    * TCP: 3128
  * IP Ranges: `Egress HTTP proxy, if applicable`
  * Type: Egress

## IAM

The following service accounts shall be created:
* `cp-service`
  * Description: This account is used by the Cloud Pipeline to communicate to the GCP API (create VMs, manage data, etc.)
  * Roles:
    * `Compute Admin`
    * `Service Account Token Creator`
    * `Storage Admin`
* `cp-storage`
  * Description: This account is used by the end-users to communicate to the GCS. Users are not granted access to the account directly, instead - temporary tokens are generated to perform CLI/GUI operations
  * Roles:
    * `Storage Object Admin`
