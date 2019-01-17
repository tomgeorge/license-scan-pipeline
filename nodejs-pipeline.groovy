pipeline {
  // Use Jenkins Maven slave
  // Jenkins will dynamically provision this as OpenShift Pod
  // All the stages and steps of this Pipeline will be executed on this Pod
  // After Pipeline completes the Pod is killed so every run will have clean
  // workspace
  agent {
    label 'nodejs'
  }

  stages {
    stage ('init') {
      steps {
        script {
          env.NEXUS_URL = 'http://nexus-foss-pipeline-scan.apps.d3.casl.rht-labs.com'
          env.RC_USER = 'tomgeorge'
          env.RC_URL = 'https://chat.consulting.redhat.com'
          env.HUB_URL = 'https://redhathub.blackducksoftware.com'
          env.NEXUS_USERNAME = 'admin'
          env.NEXUS_PASSWORD='admin123'
          env.NEXUS_DESTINATION_REPOSITORY = 'lm-approved'
        }
      }
    }
 
    stage('Git Checkout') {
      steps {
        git url: "${APPLICATION_SOURCE_REPO}"
        sh "npm install"
      }
    }
    
    stage('Scan') {
      steps {
        sh "mkdir -p ./scanreports"
          withCredentials([string(credentialsId: params.BLACK_DUCK_CREDENTIALS_ID, variable: "HUB_TOKEN")]) {
            hub_detect '--blackduck.hub.url="${HUB_URL}" \
              --blackduck.hub.api.token="${HUB_TOKEN}" \
              --detect.project.name="RHLMDEMO-${ARTIFACT_NAME}" \
              --detect.policy.check.fail.on.severities=BLOCKER,CRITICAL \
              --detect.risk.report.pdf=true \
              --detect.risk.report.pdf.path="./scanreports/" \
              --blackduck.hub.trust.cert=true \
              --detect.api.timeout=900000'
          }
        archiveArtifacts(artifacts: '**/scanreports/**')
        sh 'find . -name "*RiskReport.pdf" > ./repfilepath'
      }
    }
 
    stage('Verify Report') {
      steps {
        withCredentials([
          string(credentialsId: params.ROCKET_CHAT_TOKEN_CREDENTIALS_ID, variable: "RC_TOKEN"),
          string(credentialsId: params.ROCKET_CHAT_USERNAME_CREDENTIALS_ID, variable: "RC_USERNAME")]) {
          script {
            def message = "Please review ${ARTIFACT_NAME} located at ${HUB_URL} and proceed to ${env.BUILD_URL} to approve/reject the requrest"
            sh """
            curl -H "X-Auth-Token: ${RC_TOKEN}" -H "X-User-Id: ${RC_USERNAME}" -H "Content-type:application/json" ${RC_URL}/api/v1/chat.postMessage -d '{ "channel": "#foss-compliance-pipeline", "text": "${message}" }'
            """
          }
        }
      }
    }

    stage('Push to Nexus') {
      steps {
        script {
          def reportPath = readFile('./repfilepath').trim()
          def uploadUrl = "${NEXUS_URL}/repository/${env.NEXUS_DESTINATION_REPOSITORY}/"
          def uploadPath = new Date().format("YYYY/MM/dd/HH-mm-ss");
          sh "curl -k -u ${NEXUS_USERNAME}:${NEXUS_PASSWORD} -X PUT " + uploadUrl + uploadPath + "/scan-report.pdf" + " -T " + reportPath
          sh "find /home/jenkins/.m2/repository -name '*${ARTIFACT_NAME}*' > uploadfiles"
          packagePath = readFile('uploadfiles').trim()
          sh "zip -r ${ARTIFACT_NAME}.zip ."
          sh "curl -k -u ${NEXUS_USERNAME}:${NEXUS_PASSWORD} -X PUT " + uploadUrl + uploadPath + "/${ARTIFACT_NAME}.zip" + " -T ${ARTIFACT_NAME}.zip" 
          env.NEXUS_ARTIFACT_URL = uploadUrl + uploadPath 
          print "artifact URL ${env.NEXUS_ARTIFACT_URL}"
        }
      }
    }
 
    stage('Notify Users') {
      steps {
        withCredentials([string(credentialsId: params.ROCKET_CHAT_TOKEN_CREDENTIALS_ID, variable: "RC_TOKEN"),
          string(credentialsId: params.ROCKET_CHAT_USERNAME_CREDENTIALS_ID, variable: "RC_USERNAME")]) {
          script {
            def message = "The following artifacts have been approved: ${ARTIFACT_NAME}. They can be accessed at ${NEXUS_URL}"
            sh """
              curl -H "X-Auth-Token: ${ROCKET_CHAT_TOKEN_CREDENTIALS_ID}" -H "X-User-Id: ${ROCKET_CHAT_USERNAME_CREDENTIALS_ID}" -H "Content-type:application/json" ${RC_URL}/api/v1/chat.postMessage -d '{ "channel": "#foss-compliance-pipeline", "text": "${message}" }'
           """
          }
        }
      }
    }
  }
}
