# Add several keys to attributes

Test verifies adding attributes with various key/value string shapes.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-858](EPMCMBIBPC-858.md) case with a **Key** that is a short string without spaces and a **Value** that is a short string without spaces | All values are displayed correctly |
| 2 | Perform the [EPMCMBIBPC-858](EPMCMBIBPC-858.md) case with a **Key** that is a very long string with spaces and a **Value** that is a one-symbol string | All values are displayed correctly |
| 3 | Perform the [EPMCMBIBPC-858](EPMCMBIBPC-858.md) case with a **Key** that is a one-symbol string and a **Value** that is a string with spaces | All values are displayed correctly |
