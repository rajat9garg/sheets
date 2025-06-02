@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  stress-test startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and STRESS_TEST_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\stress-test-0.0.1-SNAPSHOT.jar;%APP_HOME%\lib\jackson-dataformat-yaml-2.16.1.jar;%APP_HOME%\lib\gatling-charts-highcharts-3.10.3.jar;%APP_HOME%\lib\gatling-app-3.10.3.jar;%APP_HOME%\lib\gatling-http-java-3.10.3.jar;%APP_HOME%\lib\gatling-recorder-3.10.3.jar;%APP_HOME%\lib\gatling-http-3.10.3.jar;%APP_HOME%\lib\gatling-jms-java-3.10.3.jar;%APP_HOME%\lib\gatling-mqtt-java-3.10.3.jar;%APP_HOME%\lib\gatling-jdbc-java-3.10.3.jar;%APP_HOME%\lib\gatling-redis-java-3.10.3.jar;%APP_HOME%\lib\gatling-core-java-3.10.3.jar;%APP_HOME%\lib\gatling-jms-3.10.3.jar;%APP_HOME%\lib\gatling-mqtt-3.10.3.jar;%APP_HOME%\lib\gatling-jdbc-3.10.3.jar;%APP_HOME%\lib\gatling-redis-3.10.3.jar;%APP_HOME%\lib\gatling-graphite-3.10.3.jar;%APP_HOME%\lib\gatling-charts-3.10.3.jar;%APP_HOME%\lib\gatling-core-3.10.3.jar;%APP_HOME%\lib\gatling-jsonpath-3.10.3.jar;%APP_HOME%\lib\jmespath-jackson-0.6.0.jar;%APP_HOME%\lib\jackson-databind-2.16.1.jar;%APP_HOME%\lib\jackson-annotations-2.16.1.jar;%APP_HOME%\lib\jackson-core-2.16.1.jar;%APP_HOME%\lib\jackson-module-kotlin-2.16.1.jar;%APP_HOME%\lib\kotlin-reflect-1.9.25.jar;%APP_HOME%\lib\kotlin-stdlib-1.9.25.jar;%APP_HOME%\lib\gatling-commons-3.10.3.jar;%APP_HOME%\lib\gatling-http-client-3.10.3.jar;%APP_HOME%\lib\logback-classic-1.4.14.jar;%APP_HOME%\lib\akka-slf4j_2.13-2.6.20.jar;%APP_HOME%\lib\pebble-3.2.2.jar;%APP_HOME%\lib\jodd-lagarto-6.0.6.jar;%APP_HOME%\lib\scala-logging_2.13-3.9.5.jar;%APP_HOME%\lib\redisclient_2.13-3.42.jar;%APP_HOME%\lib\slf4j-api-2.0.12.jar;%APP_HOME%\lib\akka-actor_2.13-2.6.20.jar;%APP_HOME%\lib\config-1.4.3.jar;%APP_HOME%\lib\annotations-13.0.jar;%APP_HOME%\lib\gatling-netty-util-3.10.3.jar;%APP_HOME%\lib\gatling-shared-enterprise-3.10.3.jar;%APP_HOME%\lib\gatling-shared-model_2.13-0.0.4.jar;%APP_HOME%\lib\scala-parser-combinators_2.13-2.3.0.jar;%APP_HOME%\lib\scopt_2.13-3.7.1.jar;%APP_HOME%\lib\quicklens_2.13-1.9.6.jar;%APP_HOME%\lib\gatling-shared-util_2.13-0.0.5.jar;%APP_HOME%\lib\cfor_2.13-0.3.jar;%APP_HOME%\lib\boopickle_2.13-1.3.3.jar;%APP_HOME%\lib\scala-java8-compat_2.13-1.0.0.jar;%APP_HOME%\lib\scala-swing_2.13-3.0.0.jar;%APP_HOME%\lib\scala-collection-compat_2.13-2.11.0.jar;%APP_HOME%\lib\scala-reflect-2.13.10.jar;%APP_HOME%\lib\scala-library-2.13.12.jar;%APP_HOME%\lib\lightning-csv-8.2.3.jar;%APP_HOME%\lib\caffeine-3.1.8.jar;%APP_HOME%\lib\netty-handler-proxy-4.1.104.Final.jar;%APP_HOME%\lib\netty-codec-http2-4.1.104.Final.jar;%APP_HOME%\lib\netty-codec-http-4.1.104.Final.jar;%APP_HOME%\lib\netty-resolver-dns-native-macos-4.1.104.Final-osx-x86_64.jar;%APP_HOME%\lib\netty-resolver-dns-native-macos-4.1.104.Final-osx-aarch_64.jar;%APP_HOME%\lib\netty-resolver-dns-classes-macos-4.1.104.Final.jar;%APP_HOME%\lib\netty-resolver-dns-4.1.104.Final.jar;%APP_HOME%\lib\netty-handler-4.1.104.Final.jar;%APP_HOME%\lib\netty-tcnative-boringssl-static-2.0.62.Final.jar;%APP_HOME%\lib\netty-tcnative-boringssl-static-2.0.62.Final-linux-x86_64.jar;%APP_HOME%\lib\netty-tcnative-boringssl-static-2.0.62.Final-linux-aarch_64.jar;%APP_HOME%\lib\netty-tcnative-boringssl-static-2.0.62.Final-osx-x86_64.jar;%APP_HOME%\lib\netty-tcnative-boringssl-static-2.0.62.Final-osx-aarch_64.jar;%APP_HOME%\lib\netty-tcnative-boringssl-static-2.0.62.Final-windows-x86_64.jar;%APP_HOME%\lib\netty-tcnative-classes-2.0.62.Final.jar;%APP_HOME%\lib\Saxon-HE-10.6.jar;%APP_HOME%\lib\jodd-util-6.2.2.jar;%APP_HOME%\lib\spotbugs-annotations-4.8.3.jar;%APP_HOME%\lib\logback-core-1.4.14.jar;%APP_HOME%\lib\snakeyaml-2.2.jar;%APP_HOME%\lib\netty-transport-native-epoll-4.1.104.Final-linux-x86_64.jar;%APP_HOME%\lib\netty-transport-native-epoll-4.1.104.Final-linux-aarch_64.jar;%APP_HOME%\lib\netty-transport-classes-epoll-4.1.104.Final.jar;%APP_HOME%\lib\netty-incubator-transport-native-io_uring-0.0.24.Final-linux-x86_64.jar;%APP_HOME%\lib\netty-incubator-transport-native-io_uring-0.0.24.Final-linux-aarch_64.jar;%APP_HOME%\lib\netty-incubator-transport-classes-io_uring-0.0.24.Final.jar;%APP_HOME%\lib\netty-transport-native-unix-common-4.1.104.Final.jar;%APP_HOME%\lib\netty-codec-mqtt-4.1.104.Final.jar;%APP_HOME%\lib\netty-codec-socks-4.1.104.Final.jar;%APP_HOME%\lib\netty-codec-dns-4.1.104.Final.jar;%APP_HOME%\lib\netty-codec-4.1.104.Final.jar;%APP_HOME%\lib\netty-transport-4.1.104.Final.jar;%APP_HOME%\lib\netty-buffer-4.1.104.Final.jar;%APP_HOME%\lib\sfm-util-8.2.3.jar;%APP_HOME%\lib\checker-qual-3.37.0.jar;%APP_HOME%\lib\error_prone_annotations-2.21.1.jar;%APP_HOME%\lib\unbescape-1.1.6.RELEASE.jar;%APP_HOME%\lib\netty-resolver-4.1.104.Final.jar;%APP_HOME%\lib\netty-common-4.1.104.Final.jar;%APP_HOME%\lib\jmespath-core-0.6.0.jar;%APP_HOME%\lib\brotli4j-1.13.0.jar;%APP_HOME%\lib\native-linux-x86_64-1.13.0.jar;%APP_HOME%\lib\native-linux-aarch64-1.13.0.jar;%APP_HOME%\lib\native-osx-x86_64-1.13.0.jar;%APP_HOME%\lib\native-osx-aarch64-1.13.0.jar;%APP_HOME%\lib\native-windows-x86_64-1.13.0.jar;%APP_HOME%\lib\typetools-0.6.3.jar;%APP_HOME%\lib\javax.jms-api-2.0.1.jar;%APP_HOME%\lib\fast-uuid-0.2.0.jar;%APP_HOME%\lib\HdrHistogram-2.1.12.jar;%APP_HOME%\lib\t-digest-3.1.jar;%APP_HOME%\lib\gatling-recorder-bc-shaded-1.77.0.jar;%APP_HOME%\lib\service-1.13.0.jar;%APP_HOME%\lib\jsr305-3.0.2.jar;%APP_HOME%\lib\commons-pool2-2.8.0.jar


@rem Execute stress-test
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %STRESS_TEST_OPTS%  -classpath "%CLASSPATH%" com.stress.test.StressTestEngineKt %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable STRESS_TEST_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%STRESS_TEST_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
