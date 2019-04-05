#!groovy

// Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at

//    http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import jenkins.model.*
import hudson.security.*
import jenkins.security.s2m.AdminWhitelistRule

// Configure admin account
def instance = Jenkins.getInstance()

def user = System.getenv('JENKINS_USER') 
def pass = System.getenv('JENKINS_PASS') 

def hudsonRealm = new HudsonPrivateSecurityRealm(false)
hudsonRealm.createAccount(user, pass)
instance.setSecurityRealm(hudsonRealm)

def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
instance.setAuthorizationStrategy(strategy)
instance.save()

Jenkins.instance.getInjector().getInstance(AdminWhitelistRule.class).setMasterKillSwitch(false)

// Allow only one executor
Jenkins.instance.setNumExecutors(2)
