// -*- groovy -*-
//
// Build an Open MPI dist release
//
//
// WORKSPACE Layout:
//   scratch/
//   ompi/                 Open MPI source tree
//   ompi-scripts/         ompi-scripts master checkout

def coverity_tool = "https://scan.coverity.com/download/cxx/linux64"

def snapshot_version = ""
def tarball_name = ""

currentBuild.displayName = "#${currentBuild.number}"
currentBuild.description = "Coverity Nightly Build for Open MPI\n"

node("ubuntu_20.04") {
        stage('Tools Checkout') {
              checkout(changelog: false, poll: false, scm: scm)
        }

        stage('Coverity Tools Download') {
              sh("mkdir -p ${WORKSPACE}/coverity-tool")
	      echo 'To Do'
        }

	stage('Tarball Download') {
              def snapshot_version = ""

	      sh "curl https://download.open-mpi.org/nightly/open-mpi/main/latest_snapshot.txt -O"
  	      snapshot_version = sh(script: "cat latest_snapshot.txt", returnStdout: true).trim()

	      currentBuild.displayName = "${currentBuild.displayName} - ${snapshot_version}"
	      currentBuild.description = "${currentBuild.description} for version ${snapshot_version}"

  	      tarball_name = "openmpi-${snapshot_version}.tar.gz"
  	      sh "curl https://download.open-mpi.org/nightly/open-mpi/main/${tarball_name} -O"
	      sh "ls -lR ${WORKSPACE}"
        }

        stage('Coverity Build') {
			
        }
}
