# Check the configure cluster aws ebs volume type for docker images

Test verifies that `cluster.aws.ebs.type` preference has value specified at deploy.

**Prerequisites**:
- Admin user
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
| 8 | Launch a tool with default settings | |
| 9 | At the **Runs** page, click the just-launched run | |
| 10 | Wait until the SSH hyperlink appears. Click the ***InitializeNode*** task. | Log contains next text: <ul> `The requested EBS volume type for <...> device is <volume_type>`, where `<volume_type>` is value from the Prerequisites |	

**After:**
- Stop the run launched at step 8
