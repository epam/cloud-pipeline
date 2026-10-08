# Validation of Singularity functionality

Test verifies building and running a Singularity instance from an SSH session inside a tool run with the `CP_CAP_SINGULARITY` capability.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-3174](EPMCMBIBPC-3174.md) case |  |
| 2 | In the SSH web-terminal, run the command: `singularity build <IMAGE_NAME>.sif library://<IMAGE_NAME>` where `<IMAGE_NAME>` is an existing image name (e.g. `ubuntu`) | The command output contains `<...> Build complete: <IMAGE_NAME>.sif` |
| 3 | Run the command: `singularity instance start <IMAGE_NAME>.sif instance1` where `<IMAGE_NAME>` is the image name from step 2 | The command output contains `<...> instance started successfully` |
| 4 | Run the command: `singularity instance list` | A table appears with columns **INSTANCE NAME**, **PID**, **IP**, **IMAGE**, with 1 row of data (**INSTANCE NAME** = `instance1`, **IMAGE** = `home/<USERNAME>/<IMAGE_NAME>.sif`, where `<USERNAME>` is the name of the user who launched the run at step 1, `<IMAGE_NAME>` is the image name from step 2) |

**After**:
- Stop the run launched at step 1
