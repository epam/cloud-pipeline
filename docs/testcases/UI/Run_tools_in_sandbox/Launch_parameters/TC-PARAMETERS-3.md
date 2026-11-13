# [MANUAL] Check the configure cluster aws ebs volume type for docker images

Test verifies that `cluster.aws.ebs.type` preference has value specified at deploy, and that the volumes of a new node get this type.

**Prerequisites**:
- Admin user
- The platform runs in AWS
- The `cluster.aws.ebs.type` preference value that should be specified at deploy
- Read access to the EC2 volumes of the platform's AWS account (the AWS console or `aws ec2 describe-volumes`): on this line the run log does not show the volume type

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Settings** page | |
| 3 | Click the **PREFERENCES** tab | |
| 4 | Click the **Cluster** tab | |
| 5 | Find the `cluster.aws.ebs.type` preference | <li> The `cluster.aws.ebs.type` preference contains the value from Prerequisites <li> The eye-icon near the preference is checked. |
| 6 | Open the **Tools** page | |
| 7 | Select the test tool | |
| 8 | Hover over the **v** button next to **Run**, click **Custom settings** in the list that appears | |
| 9 | Expand the **Exec environment** section, set **Disk (Gb)** to `60` | |
| 10 | Expand the **Advanced** section. Tick the **Do not mount storages** checkbox, set **Price type** to `On-demand` | |
| 11 | Click the **Add parameter** button. Input `CP_CREATE_NEW_NODE` into the name field of the added parameter, and `true` into its value field | The run will get a new node, not a free one |
| 12 | Click **Launch**, then click **Launch** in the pop-up that appears | |
| 13 | At the **Runs** page, click the just-launched run | |
| 14 | Wait until the SSH hyperlink appears. Expand the **Instance** section and save the instance ID of the **IP** row: `<instance ID> (<IP address>)` | |
| 15 | In the AWS console, open **EC2** > **Instances**, select the instance saved at step 14 and open its **Storage** tab (or run `aws ec2 describe-volumes --filters Name=attachment.instance-id,Values=<instance ID>`) | Every EBS volume of the instance has the volume type from the Prerequisites |

**After:**
- Stop the run launched at step 12
