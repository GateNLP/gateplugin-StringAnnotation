#!/bin/bash

if [ "${GATE_HOME}" == "" ]
then
  echo Environment variable GATE_HOME not set
  exit 1
fi

PRG="$0"
CURDIR="`pwd`"
# need this for relative symlinks
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`"/$link"
  fi
done
SCRIPTDIR=`dirname "$PRG"`
ROOTDIR=`cd "$SCRIPTDIR/.."; pwd -P`

pluginDir="$ROOTDIR"

if [[ ! -f "$ROOTDIR/runner/stringannotation-runner.classpath" ]]
then
  echo Generating the classpath file "$ROOTDIR/runner/stringannotation-runner.classpath" 
  curdir=`pwd`
  cd "$OOTDIR"
  mvn install -DskipTests
  cd "$ROOTDIR/runner/" 
  mvn install -DskipTests
fi
classpath=`cat "$ROOTDIR/runner/stringannotation-runner.classpath"`
# echo classpath is $classpath
java -cp "$classpath" com.jpetrak.gate.stringannotation.extendedgazetteer.GenerateCache "$@"

