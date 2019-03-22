workflow HelloWorld {
  call HelloWorld_print
  scatter (scattername in) {
  }
}

task HelloWorld_print {
  command {
    pipe_log SUCCESS "Running WDL pipeline" "Task1"
  }
}