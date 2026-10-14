# [MANUAL] Clear selection button validation

Test verifies the **Clear selection** button behavior on the path parameter browse pop-up.

**Preparations**:
1. Complete the [EPMCMBIBPC-1461](EPMCMBIBPC-1461.md) case for any path parameter

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Check some checkbox at one hierarchy level |  |
| 2 | Press **Clear selection** button | <li> the button edges are highlighted blue on hover <li> after the button is pressed, the checks made at step 1 disappear |
| 3 | Move the cursor off the button | The button edges are no longer highlighted |
