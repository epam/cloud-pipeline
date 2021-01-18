# Kill node manually

**Actions**:

1. Launch a pipeline
2. Wait for a new node assigned to the pipeline launched at step 1
3. Stop the pipeline launched at step 1
4. Terminate the node from step 2

***

**Expected result**:

- The node created for the pipeline launched at step 2 is removed
- Cloud instance booted for the pipeline is terminated
