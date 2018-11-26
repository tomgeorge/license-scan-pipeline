# license-scan-pipeline





1. Create a Jenkins instance in OpenShift
2. Install  [Black Duck Detect Plugin](https://wiki.jenkins.io/display/JENKINS/Black+Duck+Detect+Plugin)
3. `ansible-galaxy install -r requirements.yml -p galaxy`
4. Login to your openshift cluster via `oc login` so `ansible-applier` can connect
5. Before this, I had to get a nexus server image to my cluster because it didn't have `registry.connect.redhat.com` as a registry:
   1. `docker pull registry.connect.redhat.com/sonatype/nexus-repository-manager:latest `
   2. `docker tag registry.connect.redhat.com/sonatype/nexus-repository-manager ${DOCKER_REGISTRY_URL}/${OPENSHIFT_NAMESPACE}/nexus-repository-manager:latest`
   3. `docker push ${DOCKER_REGISTRY_URL}/${OPENSHIFT_NAMESPACE}/nexus-repository-manager:latest`
6. `ansible-playbook -i .applier galaxy/openshift-applier/playbooks/openshift-cluster-seed.yml -e filter_tags=all -e k8s_namespace${YOUR NAMESPACE}`
7. In the new Nexus instance, log in as `admin/admin123` .  Go to `Settings`-> `Repositories` -> `Create Repository` -> Set the type to `Raw (hosted)`-> Name the repository `lm-approved`.  Uncheck the `Strict Content Type Validation` checkbox.  Click `Create Repository`.



The manifests create the following:

+ A nexus server deployment

`${NAMESPACE}/license-scan-demo-pipeline`

`${NAMESPACE}/plugin-license-scan-demo-pipeline`

`${NAMESPACE}/docker-license-scan-demo-pipeline`

The pipelines perform are Jenkins jobs with the following build parameters:

1. APPROVAL_NOTES - Doesn't appear to be used yet
2. APPLICATION_SOURCE_REPO - The repository to do a blackduck scan on
3. CONTEXT_DIR - The relative path of context for the scan
4. ARTIFACT_NAME - The artifact name to be uploaded to nexus

The pipelines perform the following actions:

1. Run `hub_detect` to scan the contents of `${APPLICATION_SOURCE_REPO}`
2. If the scan completes, posts a message to rocket chat asking that somebody approve the build stage in Jenkins.  You have to go to the build URL and click the 'Approve' button in the 'Verify Report' pipeline stage
3. Push the artifact and scan report to nexus
4. Notify the users via rocket chat that there is a new artifact in Nexus

# To Do

Parameterize the following:

- [ ] Black Duck configuration
- [ ] Artifact destination repository
- [ ] Rocket chat credentials

It would be nice if there were ways to do this in the OpenShift manifests.

- [ ] Is there a way to give the OpenShift pipeline default parameters for jenkins?  The pod spec for the pipeline start pods alludes to this, but clicking 'Start Pipeline' in Openshift doesn't seem do pass in anything, nor is there an option in the configuration of the pods.

  ``` yaml
    spec:                  
      containers:                                                                                 - command:                                                                                    - '/bin/bash'                                                                              - '-c'
         - >                                                                                           oc start-build license-scan-demo-pipeline -e CONTEXT_DIR=${CONTEXT_DIR} -e APPLICATION_SOURCE_REPO="${SOURCE_GIT_REPO}" -e ARTIFACT_NAME="${ARTIFACT_NAME}" -e APPROVAL_NOTES="${APPROVAL_NOTES}" 
  ```

- [ ] Investigate the intermittent timeouts during the `hub_detect` phase





