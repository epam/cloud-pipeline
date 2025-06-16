#!/bin/bash
which node
node --version

source ~/.nvm/nvm.sh
nvm install 14
nvm use 14
nvm which 14
which node