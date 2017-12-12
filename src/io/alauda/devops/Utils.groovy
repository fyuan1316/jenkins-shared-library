@Grapes(
    @Grab(group='org.yaml', module='snakeyaml', version='1.19')
)

package io.alauda.devops

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.yaml.snakeyaml.Yaml

def updateImgVersionInAppYml(ymlMap, imgTagMap){
  imgTagMap.each{ img, tag ->
    if (ymlMap.get(img)!=null){
      def originImg = ymlMap[img]["image"]
      ymlMap[img]["image"] = originImg.split(":")[0] + ":" + tag
    }
  }

  return ymlMap
}

def gitTag(credentialsId, version, owner, repository,  platform = 'bitbucket.org') {
  withCredentials([usernamePassword(credentialsId: TAG_CREDENTIALS, passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
    // sh "git tag -l | xargs git tag -d" // clean local tags
    sh """
        git config --global user.email "alaudabot@alauda.io"
        git config --global user.name "Alauda Bot"
    """
    def repo = "https://${GIT_USERNAME}:${GIT_PASSWORD}@${platform}/${owner}/${repository}.git"
    sh "git fetch --tags ${repo}" // retrieve all tags
    sh("git tag -a ${version} -m 'auto add release tag by jenkins'")
    sh("git push ${repo} --tags")
  }
}