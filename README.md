# license-scan-pipeline





1. Create a Jenkins instance in OpenShift

2. Install  [Black Duck Detect Plugin](https://wiki.jenkins.io/display/JENKINS/Black+Duck+Detect+Plugin)
3. `ansible-galaxy install -r requirements.yml -p galaxy`
4. Login to your openshift cluster so `ansible-applier` can connect
5. `ansible-playbook -i .applier galaxy/openshift-applier/playbooks/openshift-cluster-seed.yml -e filter_tags=all -e k8s_namespace${YOUR NAMESPACE}`



The manifests create the following:

+ A nexus server deployment

`${NAMESPACE}/license-scan-demo-pipeline`



`${NAMESPACE}/plugin-license-scan-demo-pipeline`



`${NAMESPACE}/docker-license-scan-demo-pipeline`



