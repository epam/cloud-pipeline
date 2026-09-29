# [MANUAL] Groups of instance types validation on launch form

Test verifies that the **Type** combo-box on the launch form groups the available instances by instance family.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as a user who has permissions to launch the pipeline |  |
| 2 | Click the pipeline |  |
| 3 | Click the **Launch** button |  |
| 4 | Click **Exec environment** |  |
| 5 | Click the **Type** combo-box | A dropdown list opens. The instances in it are grouped as shown in the table below (the list may not include every instance from the table). |

**Instance type groups**:

| Instance Family | Current Generation Instance Types |
|---|---|
| General purpose | t2.nano; t2.micro; t2.small; t2.medium; t2.large; t2.xlarge; t2.2xlarge; m4.large; m4.xlarge; m4.2xlarge; m4.4xlarge; m4.10xlarge; m4.16xlarge; m5.large; m5.xlarge; m5.2xlarge; m5.4xlarge; m5.12xlarge; m5.24xlarge |
| Compute optimized | c4.large; c4.xlarge; c4.2xlarge; c4.4xlarge; c4.8xlarge; c5.large; c5.xlarge; c5.2xlarge; c5.4xlarge; c5.9xlarge; c5.18xlarge |
| Memory optimized | r4.large; r4.xlarge; r4.2xlarge; r4.4xlarge; r4.8xlarge; r4.16xlarge; x1.16xlarge; x1.32xlarge; x1e.xlarge; x1e.2xlarge; x1e.4xlarge; x1e.8xlarge; x1e.16xlarge; x1e.32xlarge |
| Storage optimized | d2.xlarge; d2.2xlarge; d2.4xlarge; d2.8xlarge; h1.2xlarge; h1.4xlarge; h1.8xlarge; h1.16xlarge; i3.large; i3.xlarge; i3.2xlarge; i3.4xlarge; i3.8xlarge; i3.16xlarge |
| Accelerated computing | f1.2xlarge; f1.16xlarge; g3.4xlarge; g3.8xlarge; g3.16xlarge; p2.xlarge; p2.8xlarge; p2.16xlarge; p3.2xlarge; p3.8xlarge; p3.16xlarge |
