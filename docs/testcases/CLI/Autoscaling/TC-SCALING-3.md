# Kill node by timeout

**Actions**:

1. Launch a pipeline
2. Wait once the pipeline finished with `SUCCESS` state
3. Wait 1 hour from the time the pipeline was launched at step 1

***

**Expected result**:

- The node created for the pipeline launched at step 1 is removed
- Cloud instance booted for the pipeline is terminated
