# Validation of DinD functionality

Test verifies pulling and running a Docker container from an SSH session inside a tool run with the `CP_CAP_DIND_CONTAINER` capability.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-3172](EPMCMBIBPC-3172.md) case |  |
| 2 | In the SSH web-terminal, run the command: `docker pull <IMAGE_NAME>` where `<IMAGE_NAME>` is an existing docker image name (e.g. `debian`) |  |
| 3 | Run the command: `docker create <IMAGE_NAME>` where `<IMAGE_NAME>` is the image name from step 2 |  |
| 4 | Run the command: `docker image ls` | A table appears with columns **REPOSITORY**, **TAG**, **IMAGE ID**, **CREATED**, **SIZE**, with 1 row of data (**REPOSITORY** = `<IMAGE_NAME>`, **TAG** = `latest`, where `<IMAGE_NAME>` is the image name from step 2) |
| 5 | Run the command: `docker container ls -a` | A table appears with columns **CONTAINER ID**, **IMAGE**, **COMMAND**, **CREATED**, **STATUS**, **PORTS**, **NAMES**, with 1 row of data (**IMAGE** = `<IMAGE_NAME>`, **STATUS** = `Created`, where `<IMAGE_NAME>` is the image name from step 2) |

**After**:
- Stop the run launched at step 1
