#!/usr/bin/env bash

pipe_log SUCCESS "Running shell pipeline" "Task1"
echo "this file should be in bucket" > storage_rules_test.test
echo "this file shouldn't be in bucket" > storage_rules_test.txt