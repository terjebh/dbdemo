pipeline {
   environment {
          imagename = 'terjebh/dbdemo'
          registryCredential = 'dockerhub'
          dockerImage = ''
          JAVA_HOME = '/usr/lib/jvm/java-20-openjdk'
        }

   agent {
     label "master || maven"
   }

  stages {

    stage("Maven Package") {

      steps {
        sh './mvnw clean package'
      }
    }

     stage("Save Artifact") {
        steps {
         archiveArtifacts artifacts: "target/dbdemo-0.0.1-SNAPSHOT.jar", fingerprint: true
        }

     }

     stage('Build docker image') {
              steps{
                script {
                  dockerImage = docker.build imagename
                }
              }
           }

     stage('Deploy Docker Image to Dockerhub') {
              steps{
                script {
                  docker.withRegistry( '', registryCredential ) {
                    dockerImage.push("$BUILD_NUMBER")
                     dockerImage.push('latest')

                  }
                }
              }
            }
            stage('Remove Unused docker image') {
              steps{
                sh "docker rmi $imagename:$BUILD_NUMBER"
                 sh "docker rmi $imagename:latest"

              }
            }

          stage('Upload jar to Nexus') {
               steps {
                 nexusArtifactUploader artifacts: [[artifactId: 'DBDemo', classifier: '', file: 'target/dbdemo-0.0.1-SNAPSHOT.jar', type: 'jar']],
                 credentialsId: '72654080-f2e8-42cf-b93d-b38038fbb381',
                 groupId: 'no.itfakultetet',
                 nexusUrl: 'noderia.com:8081',
                 nexusVersion: 'nexus3',
                 protocol: 'http',
                 repository: 'DBDemo',
                 version: '0.0.1-SNAPSHOT'

               }
              }

              stage('Message Mattermost') {
                 steps {

                mattermostSend channel: '@itfakultetet,town-square', endpoint: 'http://mattermost.itfakultetet.no:8065/hooks/zn9prjnrptn6zxp8jnr3gi61gc', message: "### Bare hyggelig! \n- Jenkins sier:  \nJob:  ${env.JOB_NAME}   \nByggnummer:  ${env.BUILD_NUMBER}  :tada:", text: '### Ny versjon på Nexus og hub.docker.com  :white_check_mark:'
                emailext body: "Dette er en mail fra Jenkins pipeline\nJenkins sier:  Jobb: ${env.JOB_NAME}\nByggnummer:  ${env.BUILD_NUMBER} gikk bra!", subject: 'DBDEMO - Ny versjon!', to: 'terje@itfakultetet.no'
               }
              }
          }

}
