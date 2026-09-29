// -*- groovy -*-
//
// Build an Open MPI dist release
//
//
// WORKSPACE Layout:
//   ompi-scripts/         ompi-scripts master checkout
//   coverity-tool/        Coverity tool build

def snapshot_version = ""
def ompi_tarball_name = ""
def ompi_dir = ""

currentBuild.displayName = "#${currentBuild.number}"
currentBuild.description = "Coverity Nightly Build for Open MPI\n"

node("ubuntu_26.04-x86_64") {
    stage('Tools Checkout') {
        checkout(changelog: false, poll: false, scm: scm)
    }

    stage('Fetch Coverity Tool') {
        sh("mkdir -p ${WORKSPACE}/coverity-tool")
	s3Download(file:'coverity-tool/coverity_tools.tgz', bucket:'ompi-jenkins-config',
                   path: 'coverity/coverity_tools.tgz', force: true)
	sh('cd coverity-tool ; tar xf coverity_tools.tgz')
	cov_bin = sh(script: 'find ${WORKSPACE}/coverity-tool -name "cov-build" -print | xargs  basename',
		     returnStdout: true).trim()
	echo "path: ${cov_bin}"
    }

    stage('Fetch Open MPI') {
        sh("curl --fail -O https://download.open-mpi.org/nightly/open-mpi/main/latest_snapshot.txt")
        snapshot_version = sh(script: "cat latest_snapshot.txt", returnStdout: true).trim()

        currentBuild.displayName = "${currentBuild.displayName} - ${snapshot_version}"
        currentBuild.description = "${currentBuild.description} for version ${snapshot_version}"

        ompi_tarball_name = "openmpi-${snapshot_version}.tar.gz"
        sh("curl --fail -O https://download.open-mpi.org/nightly/open-mpi/main/${ompi_tarball_name}")
        sh("tar xf ${ompi_tarball_name}")

	def matcher = ("${ompi_tarball_name}" =~ /(.*)\.tar\..*/)
	if (matcher) {
	    ompi_dir = matcher[0][1]
	    echo "ompi_dir: ${ompi_dir}"
	} else{
	    error "Cannot find ompi directory from ${ompi_tarball_name}"
	}
    }

    stage('Configure Open MPI') {
	sh("cd ${WORKSPACE}/${ompi_dir} && ./configure")
    }

    stage('Building Open MPI') {
	environment {
	    PATH = "PATH+EXTRA=${cov_bin}"
	    sh("cd ${WORKSPACE}/${ompi_dir} && cov-build --dir cov-int make")
	}
    }

    stage('Cleanup') {
        sh("rm -rf ${WORKSPACE}/*")
    }
}
