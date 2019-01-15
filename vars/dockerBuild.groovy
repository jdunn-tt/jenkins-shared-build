def call(image) {
    defineImage(image)
    prepImage()
    buildImage()
} 

def defineImage(image) {
    try {
            
        // Build job should be run on clean workspace, unless there is a reason not to clean it up.
        step([$class: 'WsCleanup'])
        
        // Providing credentials for TimeTrade Docker registry.
        withDockerRegistry([credentialsId: 'cd78e3ef-ca0f-4eb8-94c6-336ba858e125', url: 'https://docker.ttops.net']) {
            
            // Defining image from TimeTrade Docker registry for container that we're building Java components with.
            // Selecting 'jenkins' user and mounting SSH keys directory from host under 'jenkins' user's home directory
            // in container to be able create release tag on GitHub via SSH.
            docker.image("docker.ttops.net/${image}").inside('--user jenkins ' +
                                                                '--volume /var/lib/jenkins/.ssh:/var/lib/jenkins/.ssh:ro') {
                
                // Providing credentials for uploading artifacts to Artifactory using 'uploadArchives' Gradle task.
                withCredentials([[$class          : 'UsernamePasswordMultiBinding',
                                credentialsId   : 'jenkins-artifactory-access-token',
                                usernameVariable: 'ARTIFACTORY_USERNAME',
                                passwordVariable: 'ARTIFACTORY_TOKEN']]) {
                    
                    // Since 'Jenkinsfile' is in a GitHub repo, repository URL is implicit so can simply call checkout scm.
                    checkoutSCM()

                    // Build stage according git workflow
                    // If version already released (gitWorkflowGradleBuild(FEATURE_NAME) returned false) don't build and push docker images
                    // if Sonar Scaner stage needed call  gitWorkflowGradleBuild with sonarScaner = true (gitWorkflowGradleBuild(FEATURE_NAME, true))
                    if (!gitWorkflowGradleBuild(FEATURE_NAME, true)) {
                        shouldBuildImage = false
                    }
                }
            }
        }
    } catch (e) {
        echo "WARNING: defineImage FAILED: ${e.getMessage()}"
    }
}

def prepImage() {
    try {
        withDockerRegistry([credentialsId: 'cd78e3ef-ca0f-4eb8-94c6-336ba858e125', url: 'https://docker.ttops.net']) {

            // Obtaining version information from generated 'pom.xml' file. 
            // This requires that either 'install' or 'uploadArchives' task was called in a prior stage.
            // 'readMavenPom' requires 'Pipeline Utility Steps Plugin': https://github.com/jenkinsci/pipeline-utility-steps-plugin
            def pom = readMavenPom file: 'build/poms/pom-default.xml'
            def appName = pom.artifactId
            def version = pom.version
            def imageName = "docker.ttops.net/${appName}"
            globalImageName = "${imageName}:${version}"

            // Building Docker image containing application using 'Dockerfile' in project root directory.
            buildPushDockerImages(appName, version, imageName, FEATURE_NAME)

            // Removing built images from the local Docker context so we don't hog disk space.
            removeDockerImage(imageName)

        }
    } catch (e) {
        echo "WARNING: prepImage FAILED: ${e.getMessage()}"
    }
}

def buildImage() {
    try {
        def image = docker.build(imageName, "--build-arg APP_NAME=${appName} --build-arg APP_VERSION=${version} . ")

        // Image tagged 'latest' should be pushed only from master branch to prevent confusions.
        if (BRANCH_NAME == 'master') {
            image.push()
        }

        if (BRANCH_NAME == 'master' ||
        BRANCH_NAME.startsWith('release') ||
        !BRANCH_NAME.startsWith('PR') && FEATURE_NAME != '') {
            image.push(version)
        }
    } catch (e) {
        echo "WARNING: buildImage FAILED: ${e.getMessage()}"
    }
}