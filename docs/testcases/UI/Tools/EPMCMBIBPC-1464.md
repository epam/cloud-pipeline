# [MANUAL] Cancel and OK buttons validation

Test verifies the **OK** and **Cancel** buttons behavior of the path parameter browse pop-up.

**Preparations**:
1. Complete the [EPMCMBIBPC-1461](EPMCMBIBPC-1461.md) case for any path parameter

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Press **OK** button | **OK** button is unavailable and nothing changes |
| 2 | Check any file or directory | **OK** button becomes available |
| 3 | Press **Cancel** button | Pop-up disappears, nothing is written in the path field |
| 4 | Press the browse button once more |  |
| 5 | Press **Cancel** button | Pop-up disappears, nothing is written in the path field |
| 6 | Press the browse button |  |
| 7 | Check any folder or file |  |
| 8 | Press **OK** button | Pop-up disappears, the selected address is written in the path field |
