# Validation of commit docker image with tag

Test verifies committing a versioned/tagged docker image and finding it in the unscanned versions list.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-692](EPMCMBIBPC-692.md) case |  |
| 2 | Check that the **Docker image** field shows the name equal to the tool selected at step 4 of the [EPMCMBIBPC-692](EPMCMBIBPC-692.md) case |  |
| 3 | Input a valid version name for the docker image into the **Version** field |  |
| 4 | Click **COMMIT** button |  |
| 5 | Wait until the commit finishes |  |
| 6 | Perform steps 1-4 of the [EPMCMBIBPC-692](EPMCMBIBPC-692.md) case |  |
| 7 | Click **VERSIONS** tab |  |
| 8 | Click **VIEW UNSCANNED VERSIONS** button | The tool version with the name specified at step 3 appears in the versions list |

**After**:
- Stop the run
