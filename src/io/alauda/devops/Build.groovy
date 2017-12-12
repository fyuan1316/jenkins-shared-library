package io.alauda.devops

def build(String dockerfile, String context, String address, String tag, String credentialsId) {
    this.dockerfile = dockerfile
    this.context = context
    this.address = address
    this.tag = tag
    this.credentialsId = credentialsId
    this.ready = false
    this.args = ""
    this.isLoggedIn = false
    return this
}

def setArg(String name, String value) {
    this.args = this.args +" --build-arg ${name}=${value} "
    return this
}

def setFullAddress(address) {
    this.address = address
    return this
}

def getRegistry() {
    def sp = this.address.split("/")
    if (sp.size() > 1) {
        return sp[0]
    }
    return this.address
}


def start() {
    def FULL_ADDRESS = "${this.address}:${this.tag}"
    if (this.args != "") {
        echo "build args: ${this.args}"
    }
    this.login()
    retry(3) {
        sh "docker build -t ${FULL_ADDRESS} -f ${this.dockerfile} ${this.args} ${this.context}"
    }
    return this
}

def push(String tag = "") {
    if (tag == "") {
        tag = this.tag
    }
    def FULL_ADDRESS = "${this.address}:${tag}"
    def ORIG_ADDRESS = "${this.address}:${this.tag}"
    this.login()
    try {
        if (tag != this.tag) {
            sh "docker tag ${ORIG_ADDRESS} ${FULL_ADDRESS}"
        }
    } catch (Exception exc) {
        echo "error: ${exc}.. will try to pull the image..."
        sh "docker pull ${ORIG_ADDRESS}"
        sh "docker tag ${ORIG_ADDRESS} ${FULL_ADDRESS}"
    }
    retry(3) {
        sh "docker push ${FULL_ADDRESS}"
    }
    return this
}

def pull(String tag="") {
    if (tag == "") {
        tag = this.tag
    }
    def FULL_ADDRESS = "${this.address}:${tag}"
    this.login()
    retry(3) {    
        try {
            sh "docker pull ${FULL_ADDRESS}"
        } catch (Exception exc) {
            sleep(5)
            throw exc
        }
    }
}

def login() {
    if (this.isLoggedIn || this.credentialsId == "") {
        return this;
    }
     withCredentials([usernamePassword(credentialsId: this.credentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
        def regs = this.getRegistry()
        retry(3) {
            sh "docker login ${regs} -u $USERNAME -p $PASSWORD"
        }
     }
     this.isLoggedIn = true;
     return this;
}

def getImage(String tag = "") {
    if (tag == "") {
        tag = this.tag
    }
    return "${this.address}:${tag}"
}