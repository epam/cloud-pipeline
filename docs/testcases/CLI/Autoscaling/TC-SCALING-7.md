# Terminate instance before its assigning with pipeline ID

**Actions**:

1. Check that there are no empty nodes (except master-node)
2. Launch a pipeline
3. Once a new Cloud instance starts booting - terminate it

***

**Expected result**:

- Pipeline launched at step 2 still waits for the instance
- A new Cloud instance starts creating instead of the terminated at step 3
