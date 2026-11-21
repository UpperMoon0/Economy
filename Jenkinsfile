// Economy native Jenkins CI candidate (KAN-71). Reviewed main only.
// GitHub Actions cannot run. Release publication and webhook authority are
// separately protected host-side cutover tasks, not Jenkinsfile credentials.
pipeline {
    agent { label 'ci-role-general && ci-os-linux' }

    options {
        timestamps()
        skipDefaultCheckout()
        disableConcurrentBuilds()
        timeout(time: 180, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '25', artifactNumToKeepStr: '15'))
    }

    parameters {
        choice(name: 'CI_MODE', choices: ['full', 'dependency', 'harness', 'live-join'],
               description: 'Host-admitted workflow route. Only full can become a release prerequisite.')
        string(name: 'SOURCE_SHA', defaultValue: '',
               description: 'Optional exact main commit from authenticated host event; mandatory for non-main')
    }

    stages {
        stage('Prove trusted exact source') {
            steps {
                checkout scm
                script {
                    if (env.CHANGE_ID) {
                        error('PR admission is not enabled: never execute unreviewed PR code on private Jenkins workers')
                    }
                    if (env.BRANCH_NAME && env.BRANCH_NAME != 'main') {
                        error('Economy candidate only admits reviewed main, not branch or tag Jenkinsfiles')
                    }
                    def head = sh(script: 'git rev-parse --verify HEAD', returnStdout: true).trim()
                    if (!(head ==~ /[0-9a-f]{40}/)) {
                        error('Invalid checkout commit')
                    }
                    def requested = params.SOURCE_SHA.trim()
                    if (requested && (!(requested ==~ /[0-9a-f]{40}/) || requested != head)) {
                        error('Host-requested source SHA differs from checked out immutable source')
                    }
                    env.ECONOMY_SOURCE_SHA = head
                    currentBuild.description = 'Economy ' + params.CI_MODE + ' @ ' + head.take(12)
                    sh 'mkdir -p ci-evidence'
                    writeFile file: 'ci-evidence/source.txt',
                              text: 'repository=UpperMoon0/Economy\nsha=' + head +
                                    '\nmode=' + params.CI_MODE +
                                    '\nworker=' + env.NODE_NAME + '\nbuild=' + env.BUILD_NUMBER + '\n'
                }
            }
        }

        stage('Python verification-harness') {
            when { expression { params.CI_MODE in ['full', 'harness'] } }
            steps {
                sh '''#!/bin/bash
set -euo pipefail
python3 --version
python3 -m unittest discover -s tools -p 'test_*.py' -v 2>&1 | tee ci-evidence/verification-harness.log
python3 -m py_compile tools/ci_policy.py tools/live_join_test.py tools/verify.py
'''
            }
        }

        stage('OpenUI dependency metadata') {
            when { expression { params.CI_MODE in ['full', 'dependency'] } }
            steps {
                sh '''#!/bin/bash
set -euo pipefail
python3 -m unittest discover -s tools -p test_openui_dependency.py -v 2>&1 | tee ci-evidence/openui-dependency.log
'''
            }
        }

        stage('Prepare pinned OpenUI Maven') {
            when { expression { params.CI_MODE in ['full', 'live-join'] } }
            steps {
                timeout(time: 50, unit: 'MINUTES') {
                    sh '''#!/bin/bash
set -euo pipefail
java -version 2>&1 | tee ci-evidence/java.log
test "$(java -version 2>&1 | head -1 | grep -oE '[0-9]+' | head -1)" = '21' ||
  { echo 'General Linux worker must provide a Java 21 Gradle runtime' >&2; exit 1; }
export GRADLE_USER_HOME="$WORKSPACE/.ci-gradle"
export JAVA_TOOL_OPTIONS="-Duser.home=$WORKSPACE/.ci-home"
mkdir -p "$WORKSPACE/.ci-home/.m2/repository"
# Pin the upstream dependency to the exact revision used by the legacy CI.
mkdir -p .ci-openui
git -C .ci-openui init -q
git -C .ci-openui remote add origin https://github.com/UpperMoon0/OpenUI-MC.git
git -C .ci-openui fetch -q --depth 1 origin 1c44920fcc40e486ec5edf1cf82b98c1b633d5bd
git -C .ci-openui checkout -q --detach FETCH_HEAD
test "$(git -C .ci-openui rev-parse HEAD)" = 1c44920fcc40e486ec5edf1cf82b98c1b633d5bd
required="$(sed -n 's/^openui_version[[:space:]]*=[[:space:]]*//p' gradle.properties | head -1 | xargs)"
actual="$(sed -n 's/^mod_version[[:space:]]*=[[:space:]]*//p' .ci-openui/gradle.properties | head -1 | xargs)"
test -n "$required" && test -n "$actual"
test "$(printf '%s\\n%s\\n' "$required" "$actual" | sort -V | head -1)" = "$required" ||
  { echo "Pinned OpenUI version $actual is below Economy requirement $required" >&2; exit 1; }
printf 'openui_sha=%s\\nopenui_version=%s\\nrequired=%s\\n' \
  1c44920fcc40e486ec5edf1cf82b98c1b633d5bd "$actual" "$required" > ci-evidence/openui-source.txt
chmod +x .ci-openui/gradlew gradlew
(
  cd .ci-openui
  ./gradlew :common-1.20.1:publishToMavenLocal \
    :fabric-1.20.1:publishToMavenLocal :forge-1.20.1:publishToMavenLocal \
    :common-1.21.1:publishToMavenLocal :fabric-1.21.1:publishToMavenLocal \
    :neoforge-1.21.1:publishToMavenLocal :neoforge-26.1.2:publishToMavenLocal \
    --no-parallel --no-daemon --console=plain
) 2>&1 | tee ci-evidence/openui-maven.log
find "$WORKSPACE/.ci-home/.m2/repository/com/nstut" -type f -print0 |
  sort -z | xargs -0 -r sha256sum > ci-evidence/openui-maven-sha256.txt
test -s ci-evidence/openui-maven-sha256.txt
'''
                }
            }
        }

        stage('Shared JVM tests') {
            when { expression { params.CI_MODE == 'full' } }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    sh '''#!/bin/bash
set -euo pipefail
export GRADLE_USER_HOME="$WORKSPACE/.ci-gradle"
export JAVA_TOOL_OPTIONS="-Duser.home=$WORKSPACE/.ci-home"
./gradlew :common:test :common-1.20.1:test :common-1.21.1:test \
  --build-cache --no-daemon --console=plain 2>&1 | tee ci-evidence/shared-tests.log
'''
                }
            }
        }

        stage('Five loader targets and real client/server joins') {
            when { expression { params.CI_MODE in ['full', 'live-join'] } }
            steps {
                script {
                    def failures = []
                    // Deliberately finish all five targets instead of failing fast.
                    // The single bounded Linux runner avoids Gradle mapping contention.
                    for (String target in [
                        'fabric-1.20.1', 'forge-1.20.1', 'fabric-1.21.1',
                        'neoforge-1.21.1', 'neoforge-26.1.2'
                    ]) {
                        def name = target
                        stage('Economy ' + name) {
                            int status = 0
                            try {
                                timeout(time: 45, unit: 'MINUTES') {
                                    // Build lifecycle includes check/test. Manual live join
                                    // preserves its separate GameTest + join-only contract.
                                    def command = (params.CI_MODE == 'full')
                                        ? './gradlew :' + name + ':build' +
                                          (name == 'forge-1.20.1' ? ' :forge-1.20.1:runGameTestServer' : '') +
                                          ' --build-cache --no-daemon --console=plain'
                                        : (name == 'forge-1.20.1'
                                           ? './gradlew :forge-1.20.1:runGameTestServer --build-cache --no-daemon --console=plain'
                                           : 'true')
                                    status = sh(
                                        script: 'set -euo pipefail; export GRADLE_USER_HOME="$WORKSPACE/.ci-gradle"; ' +
                                                'export JAVA_TOOL_OPTIONS="-Duser.home=$WORKSPACE/.ci-home"; ' +
                                                '{ ' + command + '; python3 tools/live_join_test.py --target ' + name +
                                                '; } 2>&1 | tee ci-evidence/' + name + '.log',
                                        returnStatus: true
                                    )
                                }
                            } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException ex) {
                                throw ex
                            } catch (Exception ex) {
                                echo('Target ' + name + ' failed: ' + ex.getMessage())
                                status = 1
                            }
                            if (status != 0) {
                                failures.add(name)
                            }
                        }
                    }
                    if (failures) {
                        error('Economy failed native targets: ' + failures.join(', '))
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts(
                artifacts: 'ci-evidence/**,**/build/libs/economy-*.jar,**/run/gametest/logs/**,**/run/live-join/client/logs/**,**/run/live-join/server/logs/**',
                allowEmptyArchive: true, fingerprint: true
            )
            junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml'
        }
    }
}
