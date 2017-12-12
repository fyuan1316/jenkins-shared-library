package io.alauda.devops

import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput

def linkMessage(title, url, credentialsId) {

}


def sendRequest(method, data, botUrlCredentialsId, Boolean verbose=false, codes="100:399") {
    def reqBody = new JsonOutput().toJson(data)
    withCredentials([usernamePassword(credentialsId: botUrlCredentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
        def response = httpRequest(
            httpMode:method, url: "${PASSWORD}", 
            requestBody:reqBody, 
            validResponseCodes: codes,
            contentType: "APPLICATION_JSON",
            quiet: !verbose
        )
    }
}


def sendLink(title, text, url, botUrlCredentialsId, picUrl="", verbose=false) {
    data = [
        "link": [
            "title": title,
            "text": text,
            "picUrl": picUrl,
            "messageUrl": url,
        ],
        "msgtype": "link"
    ]
    // data = [
    //     "markdown": [
    //         "title": title,
    //         "text": "#### ${title}\n>${text}\n>![icon](${picUrl})\n> ###### [查看](${url})",
    //     ],
    //     "msgtype": "markdown"
    // ]
    // http://icons.iconarchive.com/icons/paomedia/small-n-flat/1024/sign-error-icon.png
    this.sendRequest("POST", data, botUrlCredentialsId, verbose)
}

def actionCard(title, text, buttonText, buttonUrl, botUrlCredentialsId, buttons=null, Boolean verbose=false) {
    if (buttons == null) {
        buttons = [
                [
                    "title": buttonText,
                    "actionURL": buttonUrl
                ]
            ]
    }
    data = [
        "actionCard": [
            "title": title,
            "text": text,
            "hideAvatar": "0",
            "btnOrientation": "0",
            "btns": buttons
        ],
        "msgtype": "actionCard"
    ]
    this.sendRequest("POST", data, botUrlCredentialsId, verbose)
}

def markDown(title, text, isAtAll = false, botUrlCredentialsId, Boolean verbose=false) {
    data = [
        "msgtype": "markdown",
        "markdown": [
            "title": title,
            "text": text
        ],
        "at": [
            "isAtAll": isAtAll
        ]
    ]
    this.sendRequest("POST", data, botUrlCredentialsId, verbose)
}