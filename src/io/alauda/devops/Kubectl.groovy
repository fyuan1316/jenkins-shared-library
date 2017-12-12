// @Grapes(
//     @Grab(group='org.yaml', module='snakeyaml', version='1.19')
// )
package io.alauda.devops

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import groovy.time.*
import groovy.json.JsonBuilder
// import org.yaml.snakeyaml.Yaml
// import com.cloudbees.groovy.cps.NonCPS

def setupKubectl(String environment, String address, String cert_credential, String key_credential) {
    def ADDRESS=address
    def ENVIRONMENT=environment
    def KEY_FILE=environment + "--key.txt"
    def CERT_FILE=environment+ "--crt.txt"
    withCredentials([file(credentialsId: key_credential, variable:"KEY")]) {
      sh "cat $KEY > ${KEY_FILE}"
    
      withCredentials([file(credentialsId: cert_credential, variable:"CERT")]) {
          sh "cat $CERT > ${CERT_FILE}"
          sh """
            kubectl config set-cluster ${ENVIRONMENT} --server=${ADDRESS} --insecure-skip-tls-verify=true
            kubectl config set-credentials ${ENVIRONMENT} --client-key=${KEY_FILE} --client-certificate=${CERT_FILE}
            kubectl config set-context ${ENVIRONMENT} --cluster=${ENVIRONMENT} --user=${ENVIRONMENT}
            kubectl config use-context ${ENVIRONMENT}
          """
        }
    }
}

def setupKubectlToken(String environment, String crendential) {
    def ENVIRONMENT=environment
    def token
    withCredentials([usernamePassword(credentialsId: crendential, usernameVariable: 'ADDRESS', passwordVariable: 'PASSWORD')]) {
      token = "${PASSWORD}"
      sh """
        kubectl config set-cluster ${ENVIRONMENT} --server=${ADDRESS} --insecure-skip-tls-verify=true
        kubectl config set-credentials ${ENVIRONMENT} --token=${PASSWORD}
        kubectl config set-context ${ENVIRONMENT} --cluster=${ENVIRONMENT} --user=${ENVIRONMENT}
        kubectl config use-context ${ENVIRONMENT}
      """
    }
    return token
}

// def deployNewApp(String namespace = "default", String name, String image, int port = 80) {
//   sh "kubectl run ${name} -n ${namespace} --image=${image} --port=${port} --record"
// }

def isDeploymentReady(deployJson) {
  def status = deployJson.status
  def replicas = status.replicas
  def unavailable = status['unavailableReplicas']
  def ready = status['readyReplicas']
  if (unavailable != null) {
    return false
  }
  def deployReady = (ready != null && ready == replicas)
  // get pod information
  if (deployJson.spec.template.metadata != null && deployReady) {
    if (deployJson.spec.template.metadata.labels != null) {
      def labels=""
      def namespace = deployJson.metadata.namespace
      def name = deployJson.metadata.name
      deployJson.spec.template.metadata.labels.each { k, v ->
        labels = "${labels} -l=${k}=${v}"
      }
      if (labels != "") {
        sh "kubectl get pods -n ${namespace} ${labels} -o json > ${namespace}-${name}-json.json"
        def jsonStr = readFile "${namespace}-${name}-json.json"
        def jsonSlurper = new JsonSlurperClassic()
        def jsonObj = jsonSlurper.parseText(jsonStr)
        def isReady = false
        def totalCount = 0
        def readyCount = 0
        jsonObj.items.each { k, v ->
            echo "pod phase ${k.status.phase}"
            if (k.status.phase != "Terminating") {
              totalCount++;
              if (k.status.phase == "Running") {
                readyCount++;
              }
            }
        }
        echo "Pod running count ${totalCount} == ${readyCount}"
        return totalCount > 0 && totalCount == readyCount
      }
    }
  }
  return deployReady
}

def printContainerLogs(deployJson) {
  if (deployJson == null) {
    return;
  }
  def namespace = deployJson.metadata.namespace
  def name = deployJson.metadata.name
  def labels=""
  deployJson.spec.template.metadata.labels.each { k, v ->
    labels = "${labels} -l=${k}=${v}"
  }
  sh "kubectl describe pods -n ${namespace} ${labels}"
}

def updateImage(String namespace, String name, String container, String image, String kind="deployment") {
  sh "kubectl set image -n ${namespace} ${kind} ${name} ${container}=${image}"
}

def exposeDeploy(String namespace = "default", String name, int port = 80, String type = "NodePort") {
  sh "kubectl expose deployment ${name} -n ${namespace} --port=${port} --type=${type}"
}

def getDeployment(String namespace = "default", String name, String kind="deployment") {
  sh "kubectl get ${kind} -n ${namespace} ${name} -o json > ${namespace}-${name}-yaml.yml"
  def jsonStr = readFile "${namespace}-${name}-yaml.yml"
  def jsonSlurper = new JsonSlurperClassic()
  def jsonObj = jsonSlurper.parseText(jsonStr)
  return jsonObj
}


def getVersion(String namespace = "default", String name) {
  def js = this.getDeployment(namespace, name)
  return this.getVersionFromJson(js)
}

/*
"metadata": {
        "annotations": {
            "deployment.kubernetes.io/revision": "2",
            "kubernetes.io/change-cause": "kubectl set image deployment/hello-world hello-world=redis"
        },
}
*/
def getVersionFromJson(jsonObject) {
  def version = 1
  def meta = jsonObject.metadata
  if (meta != null) {
    def ann = meta.annotations
    if (ann != null) {
      echo "got version from yaml"
      def strVersion = ann["deployment.kubernetes.io/revision"]
      if (strVersion != null) {
        version = strVersion.toInteger()
      }
    }
  }
  return version
}

def updateDeployment(String namespace, String name, String image, String container, Boolean watch = true, int timeoutMinutes = 10, int sleepTime = 5, String kind ="deployment") {
  def current = this.getDeployment(namespace, name, kind)
  if (!this.isDeploymentReady(current)) {
    throw new Exception("Deployment is not in a running state... please fix and try again")
  }
  if (container == null) {
    container = name
  }
  def error = null
  try {
    if (this.isInitContainer(current, container)) {
      def updated = this.updateContainerImage(current, container, image)
      this.applyJson(updated)
    } else {
      this.updateImage(namespace, name, container, image, kind)
    }
    if (watch) {
      sleep sleepTime
      this.monitorDeployment(namespace, name, timeoutMinutes, sleepTime, kind)
    }
  } catch (Exception exc) {
    echo "error while deploying: ${exc}"
    error = exc
  }
  if (error != null) {
    echo "will start rollback now"
    this.rollbackDeployment(namespace, name, container, current, watch, timeoutMinutes, sleepTime)
    throw error
  }
  return current
}

def rollbackDeployment(String namespace, String name, String container, previousVersion = null, Boolean watch = true, int timeoutMinutes = 10, int sleepTime = 5, String kind = "deployment") {
  def image = this.getContainerImage(previousVersion, container)
  echo "rolling back to image ${image} ..."
  if (this.isInitContainer(previousVersion, container)) {
    def updated = this.updateContainerImage(previousVersion, container, image)
    this.applyJson(updated)
  } else {
    this.updateImage(namespace, name, container, image, kind)
  }
  if (watch) {
    sleep sleepTime
    this.monitorDeployment(namespace, name, timeoutMinutes, sleepTime, kind)
  }
}

def monitorDeployment(String namespace, String name, int timeoutMinutes = 10, sleepTime = 2, String kind = "deployment") {
  def readyCount = 0
  def readyTarget = 5
  use( groovy.time.TimeCategory ) {
    def endTime = TimeCategory.plus(new Date(), TimeCategory.getMinutes(timeoutMinutes))
    def lastRolling
    while (true) {
      // checking timeout
      if (new Date() >= endTime) {
        echo "timeout, printing logs..."
        this.printContainerLogs(lastRolling)
        throw new Exception("deployment timed out...")
      }
      // checking deployment status
      try {
        def rolling = this.getDeployment(namespace, name, kind)
        lastRolling = rolling
        if (this.isDeploymentReady(rolling)) {
          
          readyCount++
          echo "ready total count: ${readyCount}"
          if (readyCount >= readyTarget) {
            break
          }
          
        } else {
          readyCount = 0
          echo "reseting ready total count: ${readyCount}"
          sh "kubectl get pod -n ${namespace} -a -o wide"
        }
      } catch (Exception exc) {
        echo "error: ${exc}"
      }
      sleep(sleepTime)
    }
  }
}

def getContainerImage(deploymentMap, String container) {
  def image = ""
  if (container == null && deploymentMap.spec.template.spec.containers.length == 1) {
    image = deploymentMap.spec.template.spec.containers[0].image
  } else {
    for (it in deploymentMap.spec.template.spec.containers) {
      if (it.name == container) {
        image = it.image
        break
      }
    }
  }
  if (image == ""&& deploymentMap.spec.template.spec.initContainers != null) {
    deploymentMap.spec.template.spec.initContainers.eachWithIndex { it, idx ->
        if (it.name == container) {
            image = it.image
            break
        }
    }
  }
  return image
}


def isInitContainer(jsonObject, container = null) {
  if (container == null) {
      return false;
  }
  def found = false;
  jsonObject.spec.template.spec.containers.eachWithIndex { it, idx ->
    if (it.name == container) {
      found = true
    }
  }
  if (!found && jsonObject.spec.template.spec.initContainers != null) {
      jsonObject.spec.template.spec.initContainers.eachWithIndex { it, idx ->
          if (it.name == container) {
              found = true
          }
      }
  }
  return found;
}

def updateContainerImage(jsonObject, container, image) {
  if (container == null) {
      jsonObject.spec.template.spec.containers[0].image = image;
      return false;
  }
  def found = false;
  jsonObject.spec.template.spec.containers.eachWithIndex { it, idx ->
    if (it.name == container) {
      jsonObject.spec.template.spec.containers[idx].image = image;
      found = true
    }
  }
  if (!found && jsonObject.spec.template.spec.initContainers != null) {
    jsonObject.spec.template.spec.initContainers.eachWithIndex { it, idx ->
      if (it.name == container) {
        jsonObject.spec.template.spec.initContainers[idx].image = image;
          found = true
      }
    }
  }
  return jsonObject;
}

def applyJson(jsonObj) {
  if (jsonObj.metadata.resourceVersion != null) {
    jsonObj.metadata.resourceVersion = null;
    jsonObj.metadata.generation = null;
  }
  writeYaml(file: 'tmpjson-file.yaml', data: jsonObj)
  sh "kubectl apply -f tmpjson-file.yaml && rm -rf tmpjson-file.yaml"
}



// def getYaml(filePath) {
//   def fileContent = readFile filePath
//   def yaml = new Yaml()
//   def content = yaml.load(fileContent)
//   return content
// }


