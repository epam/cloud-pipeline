workflow HelloWorld {
  call HelloWorld_print
}

task HelloWorld_print {
  command {
    pipe_log SUCCESS "Running WDL pipeline" "Task1"
  }
}
