// -*- groovy -*-
//
// Build an Open MPI dist release
//
//
// WORKSPACE Layout:
//   scratch/
//   ompi/                 Open MPI source tree
//   ompi-scripts/         ompi-scripts master checkout

currentBuild.displayName = "#${currentBuild.number}"
currentBuild.description = "description\n"

pipeline {
    agent any

    stages {
        stage('Hello') {
            steps {
                echo 'Hello World'
            }
        }
    }
}
