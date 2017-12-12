#!/usr/bin/env groovy

import io.alauda.devops.Kubectl
import io.alauda.devops.Deployment
import io.alauda.devops.Build
import io.alauda.devops.Sonar
import io.alauda.devops.Ding
import io.alauda.devops.Sonobuoy
import io.alauda.devops.YamlFile
import io.alauda.devops.Utils

Kubectl kubectl = new Kubectl()

def setup(String name, String address, String certCredentialId, String keyCredentialId) {
  kubectl = new Kubectl()
  kubectl.setupKubectl(name, address, certCredentialId, keyCredentialId)
}

def setupToken(String name, String credentialId) {
  kubectl = new Kubectl()
  return kubectl.setupKubectlToken(name, credentialId)
}

def setupProd() {
  // setup("production", "https://192.144.188.99:6443", "prod-crt", "prod-key")
  return setupToken("production", "prod-token")
}

def setupInt() {
  // setup("integration", "https://140.143.182.24:6443", "int-crt", "int-key")
  return setupToken("integration", "int-token")
}

def setupStaging() {
  // setup("staging", "https://154.8.211.39:6443", "staging-crt", "staging-key")
  return setupToken("staging", "staging-token")
}

def getDeployment(String namespace, String name, String kind="deployment") {
  kubectl = new Kubectl()
  return kubectl.getDeployment(namespace, name, kind)
}

def getVersion(String name = "hello-world") {
  kubectl = new Kubectl()
  def version = kubectl.getVersion("default", name)
  echo "version is ${version}"
}

def updateDeployment(String namespace, String name, String image, String container, Boolean watch = true, int timeoutMinutes = 5, int sleepTime = 5, String kind = "deployment") {
  if (namespace == null) {
    namespace = "default"
  }
  kubectl = new Kubectl()
  return kubectl.updateDeployment(namespace, name, image, container, watch, timeoutMinutes, sleepTime, kind)
}

def updateImage(String namespace, String name, String container, String image, Boolean watch = true, int timeoutMinutes = 5, int sleepTime = 5, String kind = "deployment") {
  if (namespace == null) {
    namespace = "default"
  }
  kubectl = new Kubectl()
  return kubectl.updateDeployment(namespace, name, image, container, watch, timeoutMinutes, sleepTime, kind)
}

def rollbackDeployment(String namespace, String name, String container, previousVersion, Boolean watch = true, int timeoutMinutes = 5, int sleepTime = 5, String kind = "deployment") {
  if (namespace == null) {
    namespace = "default"
  }
  kubectl = new Kubectl()
  kubectl.rollbackDeployment(namespace, name, container, previousVersion, watch, timeoutMinutes, sleepTime, kind)
}

def getYaml(filePath) {
  // kubectl = new Kubectl()
  // return new Deployment().setContent(kubectl.getYaml(filePath))
  return new Deployment().parse(filePath)
}

// def build(String dockerfile = "Dockerfile", String context = ".", String address = "index.alauda.cn", String tag = "latest", String credentialsId = "alaudakk8s") {
//   return new Build().build(dockerfile, context, address, tag, credentialsId)
// }

def dockerBuild(String dockerfile = "Dockerfile", String context = ".", String address = "index.alauda.cn", String tag = "latest", String credentialsId = "alaudak8s") {
  return new Build().build(dockerfile, context, address, tag, credentialsId)
}


def scan(String repo, String branch, String credentialsId, String folder = ".", Boolean debug = false, String owner = "mathildetech", String actionUser = "", Boolean waitScan = true) {
  return new Sonar().scan(repo, branch, credentialsId, folder, debug, owner, actionUser, waitScan)
}


def notificationCard(String title, String text, String buttonText, String buttonUrl, String credentialsId, Boolean verbose=false) {
  new Ding().actionCard(title, text, buttonText, buttonUrl, credentialsId, verbose)
}

def notificationLink(String title, String text, String url, String credentialsId, picUrl="", Boolean verbose=false) {
  new Ding().sendLink(title, text, url, credentialsId, picUrl, verbose)
}


// https://www.flaticon.com/packs/science-and-technology-9/2

def notificationFailed(project, credentialsId, title="", version="", isEnvironment = false) {
  // msg = "查看Jenkins流水线历史记录"
  msg = "🛑 ${title} 🛑"
  // if (version != "") {
    // msg = "version: ${version} --- ${msg}"
  //   msg = "${msg} - version: ${version}"
  // }
  if (title == "") {
    title = "流水线失败了！"
  }
  // title = "${project}：${version}"
  title = "${project}：${version}"
  // https://image.flaticon.com/icons/png/512/752/752640.png
  // new Ding().sendLink(title, msg, "${env.BUILD_URL}", credentialsId, "https://image.flaticon.com/icons/png/128/148/148766.png")
  // msg = genNotificationMessage(msg, "https://image.flaticon.com/icons/png/128/148/148766.png")
  // new Ding().actionCard(title, msg, "查看", "${env.BUILD_URL}", credentialsId, genButtons())
  msg = genNotificationMessage(msg, "https://danielfbm.github.io/images/failed-large.png", title)
  def buttons = getButtonLinks(isEnvironment)
  msg = "${msg}${buttons}"
  new Ding().markDown(title, msg, false, credentialsId)
}

def notificationTest(project, credentialsId, title="", version="") {
  // msg = "查看Jenkins流水线历史记录"
  msg = ""
  if (version != "") {
    // msg = "version: ${version} --- ${msg}"
    msg = "version: ${version}"
  }
  if (title == "") {
    title = "开始手工测试"
  }
  // title = "${project}：${version}"
  title = "${project}：${version} - ${title}"
  // https://image.flaticon.com/icons/png/512/752/752541.png
  // https://image.flaticon.com/icons/png/512/752/752530.png
  // new Ding().sendLink(title, msg, "${env.BUILD_URL}", credentialsId, "https://image.flaticon.com/icons/png/128/148/148941.png")
  // msg = genNotificationMessage(msg, "https://image.flaticon.com/icons/png/128/148/148941.png")
  // new Ding().actionCard(title, msg, "查看", "${env.BUILD_URL}", credentialsId, genButtons())
  msg = genNotificationMessage(msg, "https://image.flaticon.com/icons/png/32/148/148941.png", title)
  def buttons = getButtonLinks()
  msg = "${msg}${buttons}"
  new Ding().markDown(title, msg, false, credentialsId)
}

def notificationSuccess(project, credentialsId, title="", version="") {
  // msg = "查看Jenkins流水线历史记录"
  msg = "✅ ${title} ✅"
  // if (version != "") {
    // msg = "version: ${version} --- ${msg}"
  //   msg = "${msg} - version: ${version}"
  // }
  if (title == "") {
    title = "流水线成功了"
  } else if (title == "上线啦") {
    msg = "${msg} 🎉🎊🎈"
  }
  title = "${project}：${version}"

  // new Ding().sendLink(title, msg, "${env.BUILD_URL}", credentialsId, "https://image.flaticon.com/icons/png/128/148/148767.png")
  msg = genNotificationMessage(msg, "https://danielfbm.github.io/images/success-large.png", title)
  def buttons = getButtonLinks()
  msg = "${msg}${buttons}"
  new Ding().markDown(title, msg, false, credentialsId)
  // new Ding().actionCard(title, msg, "查看", "${env.BUILD_URL}", credentialsId, genButtons())
}

def genNotificationMessage(msg, pictureURL, title="") {
  // msg = "![screenshot](${pictureURL})  ${msg}"
  if (title != "") {
    msg = "### ${title}  \n  ${msg}"
  }
  
  def gitlog = ""
  try {
    sh "git log --oneline -n 1 > gitlog.file"
    gitlog = readFile "gitlog.file"
  } catch (Exception exc) {}
  def gitbranch = ""
  try {
    gitbranch = env.BRANCH_NAME
    if (gitbranch != null && gitbranch.startsWith("PR-")) {
      sh "git branch | grep '*' > gitbranch.file"
      gitbranch = readFile "gitbranch.file"
      gitbranch = gitbranch.replace("*", "").replace(" ", "")
    } 
    if (gitbranch == null || gitbranch == "") {
      gitbranch = env.BRANCH_NAME
    }
  } catch (Exception exc) {}
  if (env.CHANGE_TITLE != null && env.CHANGE_TITLE != "") {
    msg = "${msg}  \n  **Change**: ${env.CHANGE_TITLE}"
  }
  if (env.CHANGE_AUTHOR_DISPLAY_NAME != null && env.CHANGE_AUTHOR_DISPLAY_NAME != "") {
    msg = "${msg}  \n  **Author**: ${env.CHANGE_AUTHOR_DISPLAY_NAME}"
  }
  if (env.CHANGE_AUTHOR != null && env.CHANGE_AUTHOR != "") {
    msg = "${msg}  \n  **Author**: ${env.CHANGE_AUTHOR}"
  }
  if (env.CHANGE_AUTHOR_EMAIL != null && env.CHANGE_AUTHOR_EMAIL != "") {
    msg = "${msg}  \n  **Author**: ${env.CHANGE_AUTHOR_EMAIL}"
  }
  if (gitlog != null && gitlog != "") {
    msg = "${msg}  \n  **Git log**: ${gitlog}"
  }
  if (gitbranch != null && gitbranch != "") {
    msg = "${msg}  \n  **Git branch**: ${gitbranch}"
  }
  if (env.CHANGE_TARGET != null && env.CHANGE_TARGET != "") {
    msg = "${msg}  \n  **Merge target**: ${env.CHANGE_TARGET}"
  }
  
  return msg
}

def genButtons(isEnvironment=false) {
  buttons = [
    [
       "title": "查看流水线",
      "actionURL": "${env.BUILD_URL}"
    ]
  ]
  if (env.CHANGE_URL != null && env.CHANGE_URL != "") {
    buttons.add([
      "title": "查看PR",
      "actionURL": "${env.CHANGE_URL}"
    ])
  }
  if (isEnvironment) {
    buttons.add([
      "title": "环境配置",
      "actionURL": "http://confluence.alaudatech.com/pages/viewpage.action?pageId=23388567"
    ])
  }
  return buttons
}

def getButtonLinks(isEnvironment=false) {
  def msg = ""
  def listT = genButtons(isEnvironment)
  listT.each() {
    if (msg == "") {
      msg = "  \n > "
    }
    msg = "${msg} --- ["+it["title"]+"]("+it["actionURL"]+") "
  }
  return msg
}

// https://image.flaticon.com/icons/png/512/752/752570.png
//https://image.flaticon.com/icons/png/512/752/752570.png


def monitorTests(token, mustPass=[], timeoutMinutes=60, url="http://sonobuoy-alaudak8s.myalauda.cn/", sleepTime=15, verbose=false) {
    return new Sonobuoy().create(token, mustPass, timeoutMinutes, url, sleepTime, verbose)
}

def yamlLoad(filepath, Boolean debug = false) {
  return new YamlFile().loadFile(filepath, debug)
}

def gitTag(credentialsId, version, owner, repository,  platform = 'bitbucket.org') {
  return new Utils().gitTag(credentialsId, version, owner, repository,  platform)
}