pipeline {
  // Use Jenkins Maven slave
  // Jenkins will dynamically provision this as OpenShift Pod
  // All the stages and steps of this Pipeline will be executed on this Pod
  // After Pipeline completes the Pod is killed so every run will have clean
  // workspace
  agent {
    label 'maven'
  }

  // Pipeline Stages start here
  // Requeres at least one stage
  stages {
    stage ('init')
    {
      steps {
        script {
          env.NEXUS_URL = 'http://nexus-cicd.apps.mikelacourse.com'
          env.RC_URL = 'http://rocketchat-rocket-chat.apps.mikelacourse.com'
          env.RC_USER = 'D69DWkjdqW6QdmNmy'
          env.RC_TOKEN = 'mphgaHUUq701k8_zsZSe7vNSYa9iaUxlX3yORXJtqH6'
          //env.HUB_URL = 'https://bizdevhub.blackducksoftware.com'
          //env.HUB_TOKEN = 'NDM2ODEwN2MtMWZkMC00MTAwLTgyNDItMzViMGY1ZDQ2YzdkOjM4OTVlMTA0LTk3ZjMtNDEzYS05ZjdiLWExYjhkNjgwYWY0Mg=='
          env.HUB_URL = 'https://redhathub.blackducksoftware.com'
          env.HUB_TOKEN = 'NDM2ODEwN2MtMWZkMC00MTAwLTgyNDItMzViMGY1ZDQ2YzdkOjM4OTVlMTA0LTk3ZjMtNDEzYS05ZjdiLWExYjhkNjgwYWY0Mg=='
          env.DOCKER_IMAGE = "docker-registry.default.svc:5000/cicd/nexus3"
        }
      }
    }
  stage('Build') {
    steps {
      sh "git clone ${params.TOOL_REPOSITORY_URL} tool_to_scan"
      dir("tool_to_scan") {
        sh "mvn clean install -DskipTests"
      }
    }
  }

  // Run Maven build, skipping tests
  stage('Scan') {
     steps {
    
        print "USING DOCKER IMAGE: ${DOCKER_IMAGE}"

        sh "mkdir -p ./scanreports"

        echo "artifact name ${ARTIFACT_NAME}"
        echo "artifact name ${params.ARTIFACT_NAME}"
        
        withCredentials([string(credentialsId: params.CREDENTIALS_ID, variable: "HUB_TOKEN")]) {
          hub_detect '--blackduck.hub.url="${HUB_URL}" \
              --blackduck.hub.api.token="${HUB_TOKEN}" \
              --detect.project.name="RHLMDEMO-${ARTIFACT_NAME}" \
              --detect.docker.image="${DOCKER_IMAGE}" --detect.policy.check.fail.on.severities=BLOCKER,CRITICAL --detect.risk.report.pdf=true \
              --detect.risk.report.pdf.path="./scanreports/" \
              --blackduck.hub.trust.cert=true'
        }

        sh 'find . -name "*.pdf" > repfilepath'
        archiveArtifacts(artifacts: '**/scanreports/**')
     }
  
   }

   stage('Verify Report') 
   {
      steps {
        script {
            def message = "Please review ${ARTIFACT_NAME} located at ${HUB_URL} and proceed to Openshift to approve/reject the requrest"
           sh 
             """
               curl -H "X-Auth-Token: ${RC_TOKEN}" -H "X-User-Id: ${RC_USER}" -H "Content-type:application/json" ${RC_URL}/api/v1/chat.postMessage -d '{ "channel": "#needs-approval", "text": "${message}" }'
             """
        }
        input( message: "Approve ${ARTIFACT_NAME}?")
      }
   }

   stage('Push to Nexus')
   {
      steps {
        script {
            def nexusurl = "${NEXUS_URL}/repository/lm-approved/"
            def todaysdate = new Date()
            uploadPath = todaysdate.format("YYYY/MM/dd/HH-mm-ss");
            print uploadPath
            reportPath = readFile('repfilepath').trim()
            print "rep:" + reportPath
            sh "curl -k -u admin:admin123 -X PUT " + nexusurl + uploadPath + "/scan-report.pdf" + " -T " + reportPath
        
            //sh "find /home/jenkins/.m2/repository -name '*${ARTIFACT_NAME}*' > uploadfiles"
            //packagePath = readFile('uploadfiles').trim()
            //sh "zip ${ARTIFACT_NAME}.zip -r ${packagePath}"
            //sh "curl -k -u admin:admin123 -X PUT " + nexusurl + uploadPath + "/${ARTIFACT_NAME}.zip" + " -T ${ARTIFACT_NAME}.zip" 
        }
      }
   }

 }
}
