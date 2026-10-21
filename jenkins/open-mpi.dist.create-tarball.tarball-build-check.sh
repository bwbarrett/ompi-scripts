#!/bin/bash
#
# Build a release tarball from a pristine tarball environment and run
# "make all install check".
#
# usage: tarball-build-check.sh <build_prefix> <tarball>
#
# Expected filesystem layout:
#    ${WORKSPACE}/ompi-scripts/         ompi-scripts checkout
#    ${WORKSPACE}/dist-files/           output of build
#
# A release tarball must build WITHOUT any of the tooling that is only needed
# to build from a git checkout: the Autotools, m4, flex, and pandoc.  The
# release-builder image has all of those installed (the Autotools, and pandoc
# in /usr/local/bin), so this script deliberately builds in an environment
# where none of them are reachable, to catch a tarball that has regressed into
# needing them:
#
#   * It does NOT source ${HOME}/ompi-setup-python.sh, so the Python venv is
#     never activated (unlike the other dist steps).
#   * It does NOT add the Autotools install to PATH (unlike the other dist
#     steps), so configure/make cannot find autoconf/automake/libtool/m4/etc.
#   * The caller (the Groovy job) strips /usr/local/bin out of PATH via
#     withEnv before invoking this script, so pandoc (the only useful thing in
#     /usr/local/bin on these images) is not found.
#
# configure is run against the system XPMEM and the libfabric/UCX installed
# under ${HOME}/packages by customize-ami.sh, matching the flags used elsewhere
# in CI (see open-mpi.coverity.execute.groovy).

set -euo pipefail

build_prefix="$1"
tarball="$2"

echo "build_prefix: ${build_prefix}"
echo "tarball: ${tarball}"
echo "--> Tarball build PATH: ${PATH}"

aws s3 cp "${build_prefix}/${tarball}" "${WORKSPACE}/dist-files/${tarball}"
directory=`echo ${tarball} | sed -e 's/\(.*\)\.tar\..*/\1/'`
rm -rf "${directory}"
tar xf "${WORKSPACE}/dist-files/${tarball}"

cd "${directory}"

./configure \
    --prefix="${WORKSPACE}/${directory}-install" \
    --with-xpmem \
    --with-libfabric=${HOME}/packages/libfabric \
    --with-ucx=${HOME}/packages/ucx

set +e
make all install check
status=$?
if test ${status} -ne 0 ; then
    # Jenkins doesn't clean up properly if there's a bunch of unwriteable
    # directories, so help it out with cleanup.
    chmod -R u+w * || true
    cd "${WORKSPACE}"
    rm -rf "${directory}" "${directory}-install"
fi
set -e

exit ${status}
