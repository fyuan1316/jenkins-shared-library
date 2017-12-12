@Grapes(
    @Grab(group='org.yaml', module='snakeyaml', version='1.19')
)

package io.alauda.devops

import groovy.json.JsonSlurperClassic
import org.yaml.snakeyaml.Yaml

def parse(filePath) {
    def fileContent = readFile filePath
    def yaml = new Yaml()
    def content = yaml.load(fileContent)
    return this.setContent(content)
}

def setContent(content, Boolean backup = true) {
    this.content = content
    this.ignoreContainer = ""
    if (backup) {
        def str = new Yaml().dump(content)
        this.backup = new Yaml().load(str)
    }
    kind = this.content["kind"]
    if (kind == null || kind == "") {
        kind = "deployment"
    }
    kind = kind.toLowerCase()
    this.kind = kind
    return this
}

def ignoreContainer(String name) {
    this.ignoreContainer = name
    return this
}

def getCurrentState() {
    kubectl = new Kubectl()
    kind = this.content["kind"]
    if (kind == null || kind == "") {
        kind = "deployment"
    }
    kind = kind.toLowerCase()
    this.kind = kind
    this.backup = kubectl.getDeployment(this.getNamespace(), this.getName(), kind)
    return this
}

def saveState() {
    return this.getCurrentState()
}

def getImage() {
    return this.getImage(null)
}

def getImage(container) {
    if (container == null || this.content.spec.template.spec.containers.length == 1) {
        return this.content.spec.template.spec.containers[0].image
    }
    for (it in this.content.spec.template.spec.containers) {
      if (it.name == container) {
        return it.image
      }
    }
    return null
}

def setImage(image, container = null) {
    if (container == null) {
        this.content.spec.template.spec.containers[0].image = image
        return this
    }
    if (container == this.ignoreContainer) {
        return this
    }
    def found = false;
    this.content.spec.template.spec.containers.eachWithIndex { it, idx ->
      if (it.name == container) {
        this.content.spec.template.spec.containers[idx].image = image
        found = true
      }
    }
    if (!found && this.content.spec.template.spec.initContainers != null) {
        this.content.spec.template.spec.initContainers.eachWithIndex { it, idx ->
            if (it.name == container) {
                this.content.spec.template.spec.initContainers[idx].image = image
                found = true
            }
        }
    }
    // for (it in this.content.spec.template.spec.containers) {
    //     echo "${it}"
    //   if (it.name == container) {
    //     it.image = image
        
    //     break
    //   }
    // }
    return this
}

def getNamespace() {
    return this.content.metadata.namespace
}

def setNamespace(namespace) {
    this.content.metadata.namespace = namespace
    return this
}

def getName() {
    return this.content.metadata.name
}

def setName(name) {
    this.content.metadata.name = name
    return this
}

def apply(Boolean watch = true, int timeoutMinutes = 5, int sleepTime = 5) {
    def namespace = this.getNamespace()
    def name = this.name()
    kubectl = new Kubectl()
    for (cont in this.content.spec.template.spec.containers) {
        if (cont.name != this.ignoreContainer) {
            kubectl.updateImage(namespace, name, cont.name, cont.image, this.kind)
        }
    }
    if (watch) {
        kubectl.monitorDeployment(namespace, name, timeoutMinutes, sleepTime, this.kind)
    }
    return this
}

def rollbackConfig() {
    this.content.spec = this.backup.spec
    return this
}

