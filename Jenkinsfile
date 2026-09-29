pipeline {
    agent any

    stages {
        stage('Build & Test') {
            steps {
                sh 'mvn -B clean verify'
            }
        }
    }

    post {
        always {
            junit 'target/surefire-reports/*.xml'
        }
    }
}
