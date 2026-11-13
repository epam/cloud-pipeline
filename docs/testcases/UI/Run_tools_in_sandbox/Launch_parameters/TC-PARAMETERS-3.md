# Check the configure cluster aws ebs volume type for docker images

Test verifies that `cluster.aws.ebs.type` preference has value specified at deploy.

**Prerequisites**:
- Admin user
- The platform runs in AWS
- The `cluster.aws.ebs.type` preference value that should be specified at deploy

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
| 14 | Wait until the SSH hyperlink appears. Click the ***InitializeNode*** task. | Log contains next text: <ul> `The requested EBS volume type for <...> device is <volume_type>`, where `<volume_type>` is value from the Prerequisites |

**After:**
- Stop the run launched at step 12
