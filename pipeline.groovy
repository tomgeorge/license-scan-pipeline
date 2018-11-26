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
          env.NEXUS_URL = 'http://nexus-foss-pipeline-scan.apps.d3.casl.rht-labs.com'
          env.RC_URL = 'https://chat.consulting.redhat.com'
          println "RC_USER ${env.RC_USER}"
          println "RC_TOKEN ${env.RC_TOKEN}"
          if (!env.RC_USER?.trim()) { // string is null or empty
            println "RC_USER was not passed in"
            env.RC_USER = 'PjphuNaSETH4mnGNf'
          } 
          if (!env.RC_TOKEN?.trim()) {
            println "RC_TOKEN was not passed in"
            env.RC_TOKEN = 'Lk1ydlfcaOadQqgmH4qqJgpqU_WkQkl62EPMy-32aIt'
          }
          //env.HUB_URL = 'https://bizdevhub.blackducksoftware.com'
          //env.HUB_TOKEN = 'NDM2ODEwN2MtMWZkMC00MTAwLTgyNDItMzViMGY1ZDQ2YzdkOjM4OTVlMTA0LTk3ZjMtNDEzYS05ZjdiLWExYjhkNjgwYWY0Mg=='
          env.HUB_URL = 'https://redhathub.blackducksoftware.com'
          env.HUB_TOKEN = 'NDM2ODEwN2MtMWZkMC00MTAwLTgyNDItMzViMGY1ZDQ2YzdkOjM4OTVlMTA0LTk3ZjMtNDEzYS05ZjdiLWExYjhkNjgwYWY0Mg=='
        }
      }
    }


    // Checkout source code
    stage('Git Checkout') {
      steps {
        // sh 'git config --global http.sslVerify false'
        git url: "${APPLICATION_SOURCE_REPO}"
        //print "GIT URL:${APPLICATION_SOURCE_REPO}" 
      }
    }

    // Run Maven build, skipping tests
  stage('Scan') {
     steps {
    
        print "USING DIR: ${CONTEXT_DIR}"
        sh "ls -lrt ${CONTEXT_DIR}"  
    
        dir("${CONTEXT_DIR}")
        {

          sh "mkdir ./scanreports"
          hub_detect '--blackduck.hub.url="${HUB_URL}" \
            --blackduck.hub.api.token="${HUB_TOKEN}" \
            --detect.project.name="RHLMDEMO-${ARTIFACT_NAME}" \
            --detect.policy.check.fail.on.severities=BLOCKER,CRITICAL --detect.risk.report.pdf=true \
            --detect.risk.report.pdf.path="./scanreports/" \
            --blackduck.hub.trust.cert=true'

          sh 'pwd'
          sh 'ls -lrt'
          sh 'find . -name "*.pdf" > repfilepath'
          archiveArtifacts(artifacts: '**/scanreports/**')
       }
     }
  
   }

   stage('Verify Report') 
   {
      steps {
        script {
            def message = "Please review ${ARTIFACT_NAME} located at ${HUB_URL} and proceed to Openshift to approve/reject the requrest"
           sh """
            curl -H "X-Auth-Token: ${RC_TOKEN}" -H "X-User-Id: ${RC_USER}" -H "Content-type:application/json" ${RC_URL}/api/v1/chat.postMessage -d '{ "channel": "#foss-compliance-pipeline", "text": "${message}" }'
              """
        }
        input( message: "Approve ${ARTIFACT_NAME}?")
      }
   }

   stage('Sign Artifact')
   {
      steps {
        script {
          dir ("${CONTEXT_DIR}")
          {
            print "Add signature to ${ARTIFACT_NAME}.zip"
          }
        }
      }
   }

   stage('Push to Nexus')
   {
      steps {
        script {
          dir ("${CONTEXT_DIR}")
          {
            def nexusurl = "${NEXUS_URL}/repository/lm-approved/"
            def todaysdate = new Date()
            uploadPath = todaysdate.format("YYYY/MM/dd/HH-mm-ss");
            print uploadPath
            reportPath = readFile('repfilepath').trim()
            print "rep:" + reportPath
            sh "curl -k -u admin:admin123 -X PUT " + nexusurl + uploadPath + "/scan-report.pdf" + " -T " + reportPath
        
            sh "find /home/jenkins/.m2/repository -name '*${ARTIFACT_NAME}*' > uploadfiles"
            packagePath = readFile('uploadfiles').trim()
            sh "zip -r ${ARTIFACT_NAME}.zip ."
            sh "curl -k -u admin:admin123 -X PUT " + nexusurl + uploadPath + "/${ARTIFACT_NAME}.zip" + " -T ${ARTIFACT_NAME}.zip" 

            env.NEXUS_ARTIFACT_URL = nexusurl + uploadPath 
          }
        }
      }
   }

   stage('Notify Users')
   {
      steps {
        script {
          dir ("${CONTEXT_DIR}")
          {
            def message = "The following artifacts have been approved: ${ARTIFACT_NAME}. They can be accessed at ${NEXUS_URL}"
           sh """
            curl -H "X-Auth-Token: ${RC_TOKEN}" -H "X-User-Id: ${RC_USER}" -H "Content-type:application/json" ${RC_URL}/api/v1/chat.postMessage -d '{ "channel": "#foss-compliance-pipeline", "text": "${message}" }'
              """
          }
        }
      }
   }


 }
}
