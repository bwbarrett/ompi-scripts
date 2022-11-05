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

node("ubuntu_20.04") {
	stage('Hello') {
              def version = ""

	      sh "curl https://download.open-mpi.org/nightly/open-mpi/main/latest_snapshot.txt -O"
  	      version = sh(script: "cat latest_snapshot.txt", returnStdout: true).trim()
  	      tarball_name = "openmpi-${version}.tar.gz"
  	      sh "curl https://download.open-mpi.org/nightly/open-mpi/main/${tarball_name} -O"
  	      sh "mkdir scratch"
	      sh "mkdir tools"
	      sh "python3 ompi-scripts/nightly/Coverity.py --log-level DEBUG --build-root scratch --source-tarball ${tarball_name} --tool-dir tools --project-name OpenMPI --project-prefix openmpi --token-file token"
        }
}
