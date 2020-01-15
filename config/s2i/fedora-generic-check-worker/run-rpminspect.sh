#!/bin/bash
set -e

echo "starting run-rpminspect.sh script"

CURRENTDIR=$(pwd)
if [ ${CURRENTDIR} == "/" ] ; then
    cd /home
    CURRENTDIR=/home
fi

export TEST_ARTIFACTS=${CURRENTDIR}/logs

# The test artifacts must be an empty directory
rm -rf ${TEST_ARTIFACTS}
mkdir -p ${TEST_ARTIFACTS}

# output some relevant information
INSTALLED_RPMINSPECT=`rpm -qa | egrep "^rpminspect.*"`
echo "rpminspect version: $INSTALLED_RPMINSPECT"
echo ""

echo "running against $TARGET_ENVR"

# invoke rpminspect

#rpminspect -c /tmp/rpminspect-fedora.conf -o $TEST_ARTIFACTS/rpminspect.json -F json $TARGET_ENVR

# the config issues should be fixed with the new rpminspect-data-fedora package
rpminspect -v -o $TEST_ARTIFACTS/rpminspect.json -F json $TARGET_ENVR

EXIT_CODE=`echo $?`


echo "Execution complete, exit code $EXIT_CODE"

echo "Converting json output into results.yaml"
python3 /tmp/convert_result_json_yaml.py -o $TEST_ARTIFACTS/results.yaml $TEST_ARTIFACTS/rpminspect.json

echo "Contents of converted yaml (see rpminspect.json for more details):"
cat $TEST_ARTIFACTS/results.yaml

exit $EXIT_CODE
