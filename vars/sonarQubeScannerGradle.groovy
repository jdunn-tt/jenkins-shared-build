/*
*  SonarQube Scanner for Gradle provides an easy way to start SonarQube analysis of a Gradle project.
*  requires SonarQube Scanner for Gradle 2.1+
*/

def call() {
    withSonarQubeEnv('SonarQube') {
        // requires SonarQube Scanner for Gradle 2.1+
        sh './gradlew sonarqube'
    }
}