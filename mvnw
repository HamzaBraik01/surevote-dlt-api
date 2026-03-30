#!/bin/sh
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

set -e

basedir=$(dirname "$(echo "$0" | sed -e 's,\\,/,g')")

case `uname` in
  *CYGWIN*) basedir=`cygpath -w "$basedir"` ;;
esac

if [ -z "$JAVA_HOME" ]; then
  JAVA_EXE="java"
else
  JAVA_EXE="$JAVA_HOME/bin/java"
fi

if ! command -v $JAVA_EXE >/dev/null 2>&1; then
  echo "Error: JAVA_HOME is not set and no 'java' command could be found in your PATH."
  exit 1
fi

exec "$JAVA_EXE" \
  $MAVEN_OPTS \
  -classpath "${basedir}/.mvn/wrapper/maven-wrapper.jar" \
  "-Dmaven.home=${basedir}" \
  "-Dmaven.multiModuleProjectDirectory=${basedir}" \
  "org.apache.maven.wrapper.MavenWrapperMain" "$@"
