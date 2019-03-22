# CentOS

CentOS Linux is a community-supported distribution derived from sources freely provided to the public by [Red Hat](ftp://ftp.redhat.com/pub/redhat/linux/enterprise/) for Red Hat Enterprise Linux (RHEL). 

As such, CentOS Linux aims to be functionally compatible with RHEL. 

The CentOS Project mainly changes packages to remove upstream vendor branding and artwork. 

CentOS Linux is no-cost and free to redistribute. Each CentOS Linux version is maintained for up to 10 years (by means of security updates -- the duration of the support interval by Red Hat has varied over time with respect to Sources released). 

A new CentOS Linux version is released approximately every 2 years and each CentOS Linux version is periodically updated (roughly every 6 months) to support newer hardware. This results in a secure, low-maintenance, reliable, predictable, and reproducible Linux environment.

[wiki.centos.org](https://wiki.centos.org/FrontPage)

# How to use this image

This image can be run on its own with SSH access or can be used as a base image for custom environments.

To run this tool perform the following steps:
1. Click `Run` button in the top-right corner and agree on a tool launch
2. Wait for the container to be initialized (`InitializeEnvironment` task shall be marked as finished)
3. Click `SSH` link to navigate to the terminal session

In addition to the base `centos:7` a number of packages are installed:
* `python`
* `wget`
* `curl`
* `git`
* `java`
