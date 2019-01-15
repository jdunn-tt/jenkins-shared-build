/*
* Cleanup Workspace
*/

def call() {
    stage('Cleanup Workspace') {
        step([$class: 'WsCleanup'])
    }
}