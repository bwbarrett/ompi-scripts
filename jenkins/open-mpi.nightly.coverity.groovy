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
	    	  sh "curl https://download.open-mpi.org/nightly/open-mpi/main/latest_snapshot.txt -O latest_snapshot.txt"
		  version = sh(script: "cat latest_snapshot.txt", returnStdout: true).trim()
		  tarball_name = "openmpi-${version}.tar.gz"
		  sh "curl https://download.open-mpi.org/nightly/open-mpi/main/${tarball_name} -O ${tarball_name}"
		  sh "tar -xf ${tarball_name}"
		  sh ls -lR
        }
    }
}
