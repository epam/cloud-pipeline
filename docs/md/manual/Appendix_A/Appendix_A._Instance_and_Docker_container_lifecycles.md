# Appendix A. Instance and Docker container lifecycles

- [Overview](#overview)
    - [Instance lifecycle stages](#instance-lifecycle-stages)
    - [Docker container lifecycle stages](#docker-container-lifecycle-stages)

## Overview

In the context of **Cloud Pipeline**, both life cycles are tied together. A user is charged for instance usage.  
**_Note_**: to run a container you need a launched instance.  
In the **Cloud Pipeline**, **instances** are bought for hourly rates with a minimum of one hour.  
This is due to the following reasons:

- It helps to decrease the time for node relaunch.
- It helps to decrease the time for Docker image and data ("type": "common") download.
- Most pipelines will take much longer than 1 hour to complete.  
    **_Note_**: same instance configuration must be used in order to reuse currently active nodes.

If the node has no running jobs 10 minutes before the new hour of payment begins, it will be terminated.

### Instance lifecycle stages

> **_Note_**: instance lifecycle stages are presented on the example of one of the supported instances - `EC2` of `AWS` Cloud Provider.

The general overview of the **EC2 instance lifecycle** - <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-lifecycle.html>.  
In the Cloud Pipeline there are 3 stages:

- **Pending** state - no billing.
- **Running** state - you're billed for each second, with a one-minute minimum, that you keep the instance running.
- **Terminated** state - no billing.

### Docker container lifecycle stages

A general overview of the **Docker container lifecycle** - <https://medium.com/@nagarwal/lifecycle-of-docker-container-d2da9f85959>.  
In the Cloud Pipeline there are 2 stages:

- **Running** state - possible to execute some commands inside the container.
- **Terminated** state - the container is not accessible.
