# Validation of specific version launch

Test verifies launching a specific committed tool version and that the Docker image field reflects that tag.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1408](EPMCMBIBPC-1408.md) case |  |
| 2 | Perform steps 1-4 of the [EPMCMBIBPC-692](EPMCMBIBPC-692.md) case |  |
| 3 | Click **VERSIONS** tab |  |
| 4 | Click **VIEW UNSCANNED VERSIONS** button |  |
| 5 | Click **Run** button opposite the version whose name was specified at step 3 of the [EPMCMBIBPC-1408](EPMCMBIBPC-1408.md) case |  |
| 6 | Click **Launch** button in the appeared pop-up |  |
| 7 | Click on the just-launched pipeline on the **ACTIVE RUNS** tab of the **Runs** page to open the **RUN LOGS** page |  |
| 8 | Click on the **Instance** collapsed header | <li> the **Docker image** field shows the full docker image name in the form `{registry_name}/{group_name}/{docker_image_name}:{tag_name}` <li> `{tag_name}` equals the version name specified at step 3 of the [EPMCMBIBPC-1408](EPMCMBIBPC-1408.md) case |
