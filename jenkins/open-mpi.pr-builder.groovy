// -*- groovy -*-
//
// Build an Open MPI Pull Request
//
//
// WORKSPACE Layout:
//   autotools-install/    Autotools install for the builder
//   src/                 Open MPI source tree
//   ompi-scripts/         ompi-scripts master checkout

node('linux') {
  stage('Initialize') {
    check_stages = prepare_check_stages()
    println("Initialized pipeline.")
  }

  // Today, we only expect to have one stage (do everything), but
  // allow that we may split build and test stages in the future.
  for (check_stage in check_stages) {
    parallel(check_stage)
  }

  stage('Finish') {
    println('Tests Completed')
  }
}

// returns a list of build stages ("build Open MPI", "Build Tests",
// etc.), although currently we only support the one stage of
// "everything", where each build stage is a map of different
// configurations to test.
def prepare_check_stages() {
  def autogen_options = ["--no-oshmem"]
  def compilers = ["clang37", "clang38", "gcc5", "gcc6"]
  def platforms = ["ARMv8", "amazon_linux_1", "amazon_linux_2", "cray_cle", "rhel7",
                "rhel8", "sles_12", "ubuntu_16.04", "ubuntu_18.04"]

  def check_stages_list = []

  // build everything stage
  def build_parallel_map = [:]
  for (autogen_option in autogen_options) {
    def name = "Autogen: ${autogen_option}".replaceAll("-", "")
    build_parallel_map.put(name, prepare_build(name, "linux", "--autogen-arg \"${autogen_option}\""))
  }
  for (platform in platforms) {
    def name = "Platform: ${platform}".replaceAll("-", "")
    build_parallel_map.put(name, prepare_build(name, platform, ""))
  }
  for (compiler in compilers) {
    def name = "Compiler: ${compiler}".replaceAll("-", "")
    build_parallel_map.put(name, prepare_build(name, compiler, "--compiler \"${compiler}\""))
  }
  build_parallel_map.put("32-bit", prepare_build("32-bit", "32bit_builds", "--32bit-build"))
  build_parallel_map.put("distcheck", prepare_build("distcheck", "ubuntu_16.04", "--distcheck"))

  check_stages_list.add(build_parallel_map)

  return check_stages_list
}

def prepare_build(build_name, label, build_arg) {
  return {
    stage("${build_name}") {
      node(label) {
        checkout_code()
	def build_env = ["PATH+AUTOTOOLS=${WORKSPACE}/autotools-install/bin",
                         "LD_LIBRARY_PATH+AUTOTOOLS=${WORKSPACE}/autotools-install/lib"]
	build_env << build_arg
	sh "printenv"
	checkout_code()
	sh '/bin/bash ompi-scripts/jenkins/open-mpi-autotools-build.sh ompi'
	withEnv(build_env) {
	  sh "printenv ; /bin/bash ompi-scripts/jenkins/open-mpi.pr-builder.build.sh ompi"
	}
      }
    }
  }
}

def checkout_code() {
  checkout(changelog: false, poll: false,
  // BWB: change name to $REF when ready
	   scm: [$class: 'GitSCM', branches: [[name: 'origin/master']],
		 doGenerateSubmoduleConfigurations: false,
		 extensions: [[$class: 'SubmoduleOption',
                               disableSubmodules: false,
                               parentCredentials: true,
                               recursiveSubmodules: true,
                               reference: '',
                               trackingSubmodules: false],
                              [$class: 'WipeWorkspace'],
			      [$class: 'RelativeTargetDirectory',
			       relativeTargetDir: 'ompi']],
		 submoduleCfg: [],
		 userRemoteConfigs: [[credentialsId: '6de58bf1-2619-4065-99bb-8d284b4691ce',
				      url: 'https://github.com/open-mpi/ompi/']]])
  // scm is a provided global variable that points to the repository
  // configured in the Jenkins job for the pipeline source.  Since the
  // pipeline and the helper scripts live in the same place, this is
  // perfect for us.  We check this out on the worker nodes so that
  // the helper scripts are always available.
  checkout(changelog: false, poll: false, scm: scm)
}
