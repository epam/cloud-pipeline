# Check code after enabled "Use another docker image"

Test verifies that selecting a different docker image for a task adds the container-init code.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-608](EPMCMBIBPC-608.md) case |  |
| 2 | Set the **Use another docker image** checkbox |  |
| 3 | Click on the appeared field |  |
| 4 | In the appeared pop-up, select a docker registry, group and tool, click **OK** button |  |
| 5 | Click **Save** button |  |
| 6 | Specify a commit message in the appeared pop-up, click **Commit** button |  |
| 7 | Click **CODE** tab |  |
| 8 | Click on the `*.wdl` file | The code of the Docker container initiation is displayed at the task code section |
