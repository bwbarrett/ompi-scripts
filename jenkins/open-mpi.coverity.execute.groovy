// -*- groovy -*-
//
// Build an Open MPI dist release
//
//
// WORKSPACE Layout:
//   scratch/
//   ompi-scripts/         ompi-scripts master checkout
//   build/                build root

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
	      s3Download(file:'coverity-tool/coverity_tools.tgz', bucket:'ompi-jenkins-config', path: 'coverity/coverity_tools.tgz')
        }

	stage('Tarball Download') {
	      sh("curl --fail -O https://download.open-mpi.org/nightly/open-mpi/main/latest_snapshot.txt")
  	      snapshot_version = sh(script: "cat latest_snapshot.txt", returnStdout: true).trim()

	      currentBuild.displayName = "${currentBuild.displayName} - ${snapshot_version}"
	      currentBuild.description = "${currentBuild.description} for version ${snapshot_version}"

  	      tarball_name = "openmpi-${snapshot_version}.tar.gz"
  	      sh("curl --fail -O https://download.open-mpi.org/nightly/open-mpi/main/${tarball_name}")
	      sh("ls -lR ${WORKSPACE}")
        }

        stage('Coverity Build') {
	     sh("Python ompi-scripts/Coverity.py --log-level DEBUG --build-root ${WORKSPACE}/build --source-tarball ${WORKSPACE}/${tarball_name} --tool-dir ${WORKspace}/coverity-tool --tool-url https://scan.coverity.com/download/cxx/linux64 --project 'Open MPI' --project-prefix openmpi --token-file /dev/null --email foo@bar.com")
        }
}
