@Grapes(
    @Grab(group='org.yaml', module='snakeyaml', version='1.19')
)

package io.alauda.devops

import groovy.json.JsonSlurperClassic
import org.yaml.snakeyaml.Yaml

def loadFile(filePath, Boolean debug = false) {
    this.debug = debug
    this.ech("filePath: ${filePath}")
    def fileContent = readFile filePath
    this.ech("${fileContent}")
    def yaml = new Yaml()
    def content = yaml.load(fileContent)
    this.content = content
    this.filePath = filePath
    return this
}

def ech(text) {
    if (this.debug) {
        echo "${text}"
    }
}

def setVal(String key, String value) {
    this.ech("will set ${key} = ${value}")
    def (current, lastKey) = this.findParent(key)
    this.ech("got ${current} and ${lastKey}")
    current["${lastKey}"] = value
    this.ech("after set ${this.content}")
    this.ech("after curr ${current}")
    return this
}

def getVal(String key) {
    def (current, lastKey) = this.findParent(key)
    return current["${lastKey}"]
}

def findParent(String key) {
    def keys = key.split('\\.')
    if (keys.length == 1) {
        return [this.content, key]
    }
    def current = this.content
    def failed = false
    def lastIndex = keys.length - 1
    def lastKey = null
    this.ech("initial content: ${current}")
    this.ech("${key} - ${keys} - ${keys.length}")
    this.ech("${lastIndex}")
    keys.eachWithIndex { k, idx ->
        this.ech("[${idx}] navigating to ${k}")
        lastKey = k
        if (idx < lastIndex) {
            if (current[k] != null) {
                current = current[k]
            } else {
                failed = true
            }
        }
    }
    this.ech("current: ${current}")
    this.ech("lastKey: ${lastKey}")
    return [current, lastKey]
}

def save(String newFilePath = null) {
    if (newFilePath == null) {
        newFilePath = this.filePath
    }
    def str = new Yaml().dump(this.content)
    writeFile file: "${newFilePath}", text: "${str}", encoding: "UTF-8"
    return this
}