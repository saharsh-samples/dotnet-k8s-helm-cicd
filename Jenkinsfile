String getBranchName() {
    def branch = env.GIT_BRANCH

    if (branch.startsWith("origin/")) {
        branch = branch.replace("origin/", "")
    }

    return branch
}

String getBuildVersion() {
    def version = readFile 'version.txt'
    def branchName = getBranchName()
    
    if(!"master".equals(branchName)) {
        version = version + '-' + branchName
    }
    return version
}

pipeline {

    environment {

        imageRepo = "saharshsingh/sample-dotnet-app"

        ocpClusterUrl = "https://192.168.99.100:8443"
        tillerNS = "tiller"

        appDevelopmentNS = "sample-projects-dev"
        appQaNS = "sample-projects-qa"
        appProductionNS = "sample-projects-dev"
    }

    // no default agent/pod to stand up
    agent none 

    stages {

        // Build and deliver application container image
        stage('Build and deliver container image') {

            // define environment
            environment {
                branch = "${getBranchName()}"
                imageTag = "${getBuildVersion()}"
            }

            // 'Build and deliver' agent pod template
            agent {
                kubernetes {
                    cloud 'openshift'
                    label 'dotnet'
                    yaml """
apiVersion: v1
kind: Pod
spec:
    containers:
      - name: jnlp
        image: 'jenkinsci/jnlp-slave:alpine'
      - name: dotnet
        image: 'microsoft/dotnet:2.2-sdk'
        imagePullPolicy: IfNotPresent
        command:
          - /bin/cat
        tty: true
      - name: dind
        image: 'docker:18.09.2-dind'
        imagePullPolicy: IfNotPresent
        securityContext:
          privileged: true
        volumeMounts:
          - mountPath: /var/lib/docker
            name: dind-storage
    volumes:
      - name: dind-storage
        emptyDir: {}
"""
                }
            }

            steps {

                // build dotnet binaries
                container('dotnet') {
                    sh 'dotnet publish -c Release -o out sample-dotnet-app'
                }

                // build container image
                container('dind') {

                    script {

                        sh 'docker build -t "${imageRepo}:${imageTag}" sample-dotnet-app'

                        if("master".equals(branch) || "develop".equals(branch)) {
                            withCredentials([usernamePassword(credentialsId:'image-registry-auth', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                                sh '''
                                echo "$PASS" | docker login --username "$USER" --password-stdin
                                docker push "${imageRepo}:${imageTag}"
                                '''
                            }
                        }
                    }
                }
            }
        }

        stage('Deploy') {

            when { anyOf { branch 'master'; branch 'develop' } }

            // define environment
            environment {
                imageTag = "${getBuildVersion()}"
            }

            // 'Deploy' agent pod template
            agent {
                kubernetes {
                    cloud 'openshift'
                    label 'helm'
                    yaml """
apiVersion: v1
kind: Pod
spec:
    containers:
      - name: jnlp
        image: 'jenkinsci/jnlp-slave:alpine'
      - name: helm
        image: 'saharshsingh/helm:2.12.3'
        imagePullPolicy: IfNotPresent
        command:
          - /bin/cat
        tty: true
"""
                }
            }

            steps {

                // Deploy to K8s using helm install
                container('helm') {

                    withCredentials([string(credentialsId:'ocp-cluster-auth-token', variable: 'TOKEN')]) {
                        sh '''

                        export HOME="`pwd`"
                        export TILLER_NAMESPACE=${tillerNS}

                        kubectl config set-cluster development --server="${ocpClusterUrl}" --insecure-skip-tls-verify
                        kubectl config set-credentials jenkins --token="$TOKEN"
                        kubectl config set-context helm --cluster=development --namespace="${tillerNS}" --user=jenkins
                        kubectl config use-context helm

                        helm install \
                            --name sample-dotnet-app \
                            --namespace "${appNS}" \
                            --set image.repository="${imageRepo}" \
                            --set image.tag="${imageTag}" \
                            deployment/helm
                        '''
                    }

                    
                }
            }
        }

    }
}
