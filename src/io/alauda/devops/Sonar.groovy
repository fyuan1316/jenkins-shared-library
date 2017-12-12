
package io.alauda.devops

def scan(String repo, String branch, String credentialsId, String folder = ".", Boolean debug = false, String owner = "mathildetech", String actionUser = "", Boolean waitScan = true) {
    this.repo = repo
    this.branch = branch
    this.credentialsId = credentialsId
    this.owner = owner
    this.folder = folder
    this.debug = debug
    this.actionUser = actionUser
    this.waitScan = waitScan
    if (this.actionUser == "") {
        this.actionUser = this.owner
    }
    return this
}

def start(install=true) {
    // issue on scanner 
    return this
    try {
        this.startToBitbucket(install)
    }
    catch (Exception exc) {
        echo "error scan bitbucket: ${exc}"
    }
    this.startToSonar(install)
    return this
}

def startToBitbucket(install=true) {
    // issue on scanner 
    return this

    def scannerCLI = "sonar-scanner";
    if (install) {
        def scannerHome = tool 'sonarqube';
        scannerCLI = "${scannerHome}/bin/sonar-scanner"
    }
    
    withSonarQubeEnv('sonarqube') {
        withCredentials([usernamePassword(credentialsId: this.credentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            def isDebug = ""
            def branchScan = "-Dsonar.bitbucket.branchName=${this.branch}"
            if (this.debug) {
                isDebug = " -X "
            }
            // if branch is pull request it should use a PR ID
            if ("${this.branch}".startsWith("PR-")) {
                this.branch = this.branch.replace("PR-","")
                branchScan = "-Dsonar.bitbucket.pullRequestId=${this.branch}"
            }
            def cmd = """
                cd ${this.folder}
                ${scannerCLI} -Dsonar.analysis.mode=issues ${isDebug} \
                    -Dsonar.bitbucket.repoSlug=${this.repo} \
                    -Dsonar.bitbucket.accountName=${this.owner} \
                    -Dsonar.bitbucket.teamName=${this.actionUser} \
                    -Dsonar.bitbucket.oauthClientKey=${USERNAME} \
                    -Dsonar.bitbucket.oauthClientSecret=${PASSWORD} \
                    -Dsonar.bitbucket.buildStatusEnabled=true \
                    -Dsonar.bitbucket.approvalFeatureEnabled=true \
                    ${branchScan}
            """
            sh "${cmd}"
        }
    }
    return this
}
def startToSonar(install=true) {
    // issue on scanner 
    return this
    
    def scannerCLI = "sonar-scanner";
    if (install) {
        def scannerHome = tool 'sonarqube';
        scannerCLI = "${scannerHome}/bin/sonar-scanner"
    }
    def scannerHome = tool 'sonarqube';
    withSonarQubeEnv('sonarqube') {
        def isDebug = ""
        if (this.debug) {
            isDebug = " -X "
        }
        def gitbranch = ""
        try {
            gitbranch = env.BRANCH_NAME
            if (gitbranch == null) {
                sh "git branch | grep '*' > gitbranch.file"
                gitbranch = readFile "gitbranch.file"
                gitbranch = gitbranch.replace("*", "").replace(" ", "")
            }
        } catch (Exception exc) {}
        if (gitbranch != null && gitbranch != "") {
            isDebug = "-Dsonar.branch.name=${gitbranch} ${isDebug}"
        
        }
        if (env.CHANGE_TARGET != null && env.CHANGE_TARGET != "") {
            isDebug = "-Dsonar.branch.target=${env.CHANGE_TARGET} ${isDebug}"
        }

        sh """
            cd ${this.folder}
            ${scannerCLI} ${isDebug} 
            ls -la .scannerwork
        """
        if (this.folder != ".") {
            sh """
                cp -r ${this.folder}/.scannerwork .
                ls -la .scannerwork
            """
        }
        // try {
        //     sh "go get -u  github.com/alauda/bergamot/sonarqube/cmdclient "

        // } catch (Exception exc) {
        //     echo "Error downloading the scanner: ${exc} .. will continue"
        //     return this
        // }
        // try {
        //     sh "cmdclient taskmonitor --w ${this.folder} --host $SONAR_HOST_URL --token $SONAR_AUTH_TOKEN"
        // } catch (Exception exc) {
        //     if ((env.CHANGE_TARGET != null && env.CHANGE_TARGET != "") || (env.BRANCH != null && env.BRANCH.startsWith("PR-"))) {
        //         echo "It is a PR, ignoring scan...: ${exc}"
        //         return this
        //     }
        //     throw exc
        // }
        
        
    }
    return this
}