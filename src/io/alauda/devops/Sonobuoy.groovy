package io.alauda.devops

import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput
import groovy.time.*


def create(token, mustPassPlugins=[], timeoutMinutes=60, url="http://sonobuoy-alaudak8s.myalauda.cn/", sleepTime=5, verbose=false) {
    this.timeoutMinutes = timeoutMinutes
    this.url = url
    this.sleepTime = sleepTime
    this.token = token
    this.verbose = verbose
    this.mustPassPlugins = mustPassPlugins
    return this
}

def monitor() {
    use( groovy.time.TimeCategory ) {
        def endTime = TimeCategory.plus(new Date(), TimeCategory.getMinutes(this.timeoutMinutes))
        while (true) {
            // checking timeout
            if (new Date() >= endTime) {
                echo "test timeout"
                throw new Exception("test timed out...")
            }
            // checking deployment status
            def data = null
            try {
                data = this.getStatus()
            } catch (Exception exc) {
                echo "error: ${exc}"
            }
            if (data != null) {
                echo "data: ${data}"
                if (this.isCompleted(data)) {
                    echo "completed"
                    try {
                        this.archiveFiles(this.url, data)
                    } catch (Exception exc) {
                        echo "Error archiving files: ${exc}"
                    }
                    
                    if (!this.succeded(data)) {
                        throw new Exception("Tests failed: ${data.tests}")
                    }
                    return data
                }
                if (this.isProcessing(data) || this.isRunning(data)) {
                }
            }
            sleep(this.sleepTime)
        }
    }
}

def succeded(data) {
    echo "these must pass: ${this.mustPassPlugins}"
    for (it in this.mustPassPlugins) {
        echo "plugin ${it}: ${data.report.success[it]}"
        if (data.report.success[it] == null) {
             return false
        }
        if (data.report.success[it] != true) {
            return false
        }
    }
    return true
}

def isRunning(data) {
    return data.status == "running"
}

def isProcessing(data) {
    return data.status == "processing"
}

def isCompleted(data) {
    return data.status == "completed"
}

def isNotFound(data) {
    return data.status == "notfound"
}


def getStatus() {
    def finalUrl = "${this.url}/status/"
    def data = this.sendRequest(finalUrl, "GET", this.token, this.verbose)
    return data
}

def sendRequest(url, method, token, Boolean verbose=false, codes="100:499") {
    def headers = [[name:'Authorization', value:"Token ${token}"]]
    def response = httpRequest(
        httpMode:method, url: url, 
        customHeaders: headers,
        validResponseCodes: codes,
        contentType: "APPLICATION_JSON",
        quiet: !verbose
    )
    if(response.content == null || response.content == ""){
        return [:]
    }
    def jsonSlurper = new JsonSlurperClassic()
    def json = jsonSlurper.parseText(response.content)
    return json
}

def cleanUp() {
    def finalUrl = "${this.url}/status/"
    this.sendRequest(finalUrl, "DELETE", this.token, this.verbose)
}

def archiveFiles(url, data, prefix="files/", replacePrefix="/tmp/", fileDir="logfiles", printFile=false) {
    if (data == null || data.report == null || data.report.files == null) {
        return;
    }
    sh "mkdir ${fileDir} || true"
    dir("${fileDir}") {
        data.report.files.eachWithIndex { k, idx ->
            def fileToDownload = "${url}"+k.replace(replacePrefix, prefix)
            // echo "${fileToDownload}"
            sh """
                curl ${fileToDownload} -O
            """
        }
    }
    archiveArtifacts artifacts: "${fileDir}/**", fingerprint: true
    try {
        junit allowEmptyResults: true, testResults: '${fileDir}/*.xml'
        junit allowEmptyResults: true, testResults: '${fileDir}/**/*.xml'
    } catch (Exception exc) {
        echo "error fetchin junit tests: ${exc}"
    }
    sh "rm -rf ${fileDir}"
    
}