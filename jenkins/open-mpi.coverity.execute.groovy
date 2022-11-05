// -*- groovy -*-
//
// Build an Open MPI dist release
//
//
// WORKSPACE Layout:
//   scratch/
//   ompi-scripts/         ompi-scripts master checkout
//   build/                build root

/////////////////////////////// Local Configuration Options ///////////////////////
// project name; the prefix of the tarball before the version number
def project_prefix = "openmpi"
// Directory prefix for tarball downloads / latest_snapshot.txt
def project_download_url = "https://download.open-mpi.org/nightly/open-mpi/main/"
// Location of 
// coverity registered email address
def project_email = "jsquyres@cisco.com"
// Jenkins credentials id for the project name / token in username:password
// format
//def project_creds = "b47cf375-6e78-4f1f-b215-18a7903a4763"
def project_creds = ""

/////////////////////////////// General Configuration Options ///////////////////////
def coverity_tool_s3_bucket = "ompi-jenkins-config"
def coverity_tool_s3_path = "coverity/coverity_tools.tgz"

def snapshot_version = ""
def tarball_name = ""

currentBuild.displayName = "#${currentBuild.number}"
currentBuild.description = "Coverity Nightly Build for Open MPI\n"

node("ubuntu_20.04") {
    def build_root = "${WORKSPACE}/build"
    def tool_dir = "${WORKSPACE}/coverity-tool"

    stage('Tools Checkout') {
        checkout(changelog: false, poll: false, scm: scm)
    }

    stage('Coverity Tools Download') {
         sh("mkdir -p ${tool_dir}")
	    s3Download(file: "${tool_dir}/coverity_tools.tgz",
                    bucket: "${coverity_tool_s3_bucket}",
                    path: "${coverity_tool_s3_path}", force: true)
    }

    stage('Tarball Download') {
        sh("curl --fail -O ${project_download_url}latest_snapshot.txt")
        snapshot_version = sh(script: "cat latest_snapshot.txt", returnStdout: true).trim()

        currentBuild.displayName = "${currentBuild.displayName} - ${snapshot_version}"
        currentBuild.description = "${currentBuild.description} for version ${snapshot_version}"

        tarball_name = "${project_prefix}-${snapshot_version}.tar.gz"
        sh("curl --fail -O ${project_download_url}${tarball_name}")
    }

    stage('Coverity Build') {
        withCredentials([usernamePassword(credentialsId: "${project_creds}",
                                          passwordVariable: 'token',
                                          usernameVariable: 'project_name')]) {
            sh("""echo ${token} > ${WORKSPACE}/token-file &&  python ${WORKSPACE}/ompi-scripts/nightly-tarball/Coverity.py --log-level DEBUG --build-root ${build_root} --source-tarball ${WORKSPACE}/${tarball_name} --tool-dir ${tool_dir} --tool-url /dev/null --project-name "${project_name}" --project-prefix ${project_prefix} --token-file ${WORKSPACE}/token-file --email ${project_email} && rm ${WORKSPACE}/token-file""")
        }
    }
}
