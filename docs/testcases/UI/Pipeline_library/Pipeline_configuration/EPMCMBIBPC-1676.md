# [MANUAL] Validation of "Add system parameter" button on Pipeline configuration tab

Test verifies adding, saving and re-displaying a system parameter on the pipeline's Configuration tab.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | After performing the [EPMCMBIBPC-795](EPMCMBIBPC-795.md) case |  |
| 2 | Click the collapse header **Advanced** |  |
| 3 | Click the **Add system parameter** button | A pop-up opens containing: <li> the header `Select system parameter to override` <li> a search field <li> a list of system variable names and types <li> **OK** (inactive) and **Cancel** buttons |
| 4 | Enter `param1` into the search field | One system variable with the name entered at step 4 is displayed |
| 5 | Click on `param1` | <li> the icon `anticon anticon-check-circle` is displayed opposite the selected variable <li> the **OK** button becomes active, showing the number of selected variables (1) |
| 6 | Click the **OK** button | The pop-up closes; above the **Add system parameters** button, a new variable appears with a non-editable name equal to the one selected at step 4 and an empty value |
| 7 | Click the **Save** button | The variable's value input field is highlighted red and a `Required` hint appears |
| 8 | Enter a value into the appeared field |  |
| 9 | Click the **Save** button | The configuration is saved with the new parameters, the collapse header **Advanced** collapses |
| 10 | Click the collapse header **Advanced** | The variable with the name selected at step 4 and the value entered at step 8 is displayed |
