# Terminate instance during pipeline work

**Actions**:

1. Launch a pipeline
2. Wait for a new node assigned to the pipeline launched at step 1
3. Terminate the Cloud instance booted for the pipeline from the step 1

***

**Expected result**:

- Pipeline launched at step 1 is stopped with the status `FAILURE`
- The node from step 2 is removed
