package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import com.bmuschko.gradle.docker.TestConfiguration
import org.gradle.api.GradleException
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

import java.nio.file.Files
import java.nio.file.Paths

class DockerfileFunctionalTest extends AbstractFunctionalTest {
    static final String DOCKERFILE_TASK_NAME = 'dockerfile'

    private void setupDockerTemplateFile() {
        File source = new File(TestConfiguration.class.getClassLoader().getResource("Dockerfile.template").toURI())
        if (source.exists()) {
            File resourcesDir = new File(projectDir, 'src/main/docker/')
            if (resourcesDir.mkdirs()) {
                if (Files.copy(source.toPath(),Paths.get(projectDir.path, 'src/main/docker/Dockerfile.template')).toFile().length() != source.length()) {
                    throw new GradleException("File could not be successfully copied")
                }
            } else {
                throw new IOException("can not create the directory ${resourcesDir.absolutePath}")
            }
        }
    }

    def "Executing a Dockerfile task without specified instructions throws exception"() {
        given:
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile)
"""
        when:
        BuildResult buildResult = buildAndFail(DOCKERFILE_TASK_NAME)

        then:
        buildResult.output.contains('Please specify instructions for your Dockerfile')
        !new File(projectDir, 'build/docker/Dockerfile').exists()
    }

    def "Specifying FROM instruction as first statement is mandatory"() {
        given:
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
    maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
}

task ${DOCKERFILE_TASK_NAME}simple(type: Dockerfile) {
    from '$TEST_IMAGE_WITH_TAG'
}

task ${DOCKERFILE_TASK_NAME}1705(type: Dockerfile) {
    arg 'from=$TEST_IMAGE_WITH_TAG'
    from '\$from'
}
"""
        def outcome = new File(projectDir, 'build/docker/Dockerfile')

        when:
        BuildResult buildResult = buildAndFail(DOCKERFILE_TASK_NAME)

        then:
        buildResult.output.contains('The first instruction of a Dockerfile has to be FROM (or ARG for Docker later than 17.05)')
        !outcome.exists()

        when:
        buildResult = build("${DOCKERFILE_TASK_NAME}1705")

        then:
        TaskOutcome.SUCCESS == buildResult.tasks.first().outcome
        outcome.exists()
        outcome.text == "ARG from=$TEST_IMAGE_WITH_TAG\nFROM \$from\n"

        when:
        buildResult = build("${DOCKERFILE_TASK_NAME}simple")

        then:
        TaskOutcome.SUCCESS == buildResult.tasks.first().outcome
        outcome.exists()
        outcome.text == "FROM $TEST_IMAGE_WITH_TAG\n"
    }

    def "Can create minimal Dockerfile in default location"() {
        given:
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
    from '$TEST_IMAGE_WITH_TAG'
    maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
}
"""
        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM $TEST_IMAGE_WITH_TAG
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
"""
    }

    def "Can create Dockerfile using all instruction methods"() {
        given:
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
    from '$TEST_IMAGE_WITH_TAG'
    maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
    runCommand 'echo deb http://archive.ubuntu.com/ubuntu precise universe >> /etc/apt/sources.list'
    defaultCommand 'echo', 'some', 'command'
    exposePort 8080, 14500
    environmentVariable 'ENV_VAR_KEY', 'envVarVal'
    environmentVariable ENV_VAR_A: 'val_a'
    environmentVariable ENV_VAR_B: 'val_b', ENV_VAR_C: 'val_c'
    addFile 'http://mirrors.jenkins-ci.org/war/1.563/jenkins.war', '/opt/jenkins.war'
    copyFile 'http://hsql.sourceforge.net/m2-repo/com/h2database/h2/1.4.184/h2-1.4.184.jar', '/opt/h2.jar'
    entryPoint 'java', '-jar', '/opt/jenkins.war'
    volume '/jenkins', '/myApp'
    user 'root'
    workingDir '/tmp'
    onBuild 'RUN echo "Hello World"'
    label version: '1.0'
}
"""
        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM $TEST_IMAGE_WITH_TAG
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
RUN echo deb http://archive.ubuntu.com/ubuntu precise universe >> /etc/apt/sources.list
CMD ["echo", "some", "command"]
EXPOSE 8080 14500
ENV ENV_VAR_KEY envVarVal
ENV ENV_VAR_A=val_a
ENV ENV_VAR_B=val_b ENV_VAR_C=val_c
ADD http://mirrors.jenkins-ci.org/war/1.563/jenkins.war /opt/jenkins.war
COPY http://hsql.sourceforge.net/m2-repo/com/h2database/h2/1.4.184/h2-1.4.184.jar /opt/h2.jar
ENTRYPOINT ["java", "-jar", "/opt/jenkins.war"]
VOLUME ["/jenkins", "/myApp"]
USER root
WORKDIR /tmp
ONBUILD RUN echo "Hello World"
LABEL version=1.0
"""
    }

    def "Can create Dockerfile by adding instances of Instruction"() {
        given:
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
    instructions = [new Dockerfile.FromInstruction('$TEST_IMAGE_WITH_TAG'),
                    new Dockerfile.MaintainerInstruction('Benjamin Muschko "benjamin.muschko@gmail.com"')]
}
"""
        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM $TEST_IMAGE_WITH_TAG
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
"""
    }

    def "Can create Dockerfile by adding raw instructions"() {
        given:
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
    instruction 'FROM $TEST_IMAGE_WITH_TAG'
    instruction { 'MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"' }
}
"""
        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM $TEST_IMAGE_WITH_TAG
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
"""
    }

    def "Can create Dockerfile from template file"() {
        given:
        setupDockerTemplateFile()
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
    instructionsFromTemplate 'src/main/docker/Dockerfile.template'
}
"""
        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
            """FROM $TEST_IMAGE_WITH_TAG
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com
"""
    }

    def "Dockerfile task can be up-to-date"() {
        given:
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

ext.version = project.properties.getOrDefault('version', '1.0')

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
    instruction 'FROM $TEST_IMAGE_WITH_TAG'
    instruction { 'MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"' }
    label([ver: version])
}
"""
        when:
        def result = build(DOCKERFILE_TASK_NAME)

        then:
        TaskOutcome.SUCCESS == result.tasks.first().outcome

        when:
        result = build(DOCKERFILE_TASK_NAME)

        then:
        TaskOutcome.UP_TO_DATE == result.tasks.first().outcome

        when:
        result = build(DOCKERFILE_TASK_NAME, "-Pversion=1.1")

        then:
        TaskOutcome.SUCCESS == result.tasks.first().outcome
    }

    def "Dockerfile task can be up-to-date false by self defined rule"() {

        given: // … a simple docker file …
        buildFile << """
                import com.bmuschko.gradle.docker.tasks.image.Dockerfile

                task dockerFile(type: Dockerfile) {
                    from "$TEST_IMAGE"
                }
            """

        when:
        def result = build('dockerFile')

        then: // … it is generated the first time
        TaskOutcome.SUCCESS == result.tasks.first().outcome

        when: // … a upToDate spec returns true …
        super.setupBuildfile()
        buildFile << """
                import com.bmuschko.gradle.docker.tasks.image.Dockerfile

                task dockerFile(type: Dockerfile) {
                    outputs.upToDateWhen { true }
                    from "$TEST_IMAGE"
                }
            """
        result = build('dockerFile')

        then: // … this will have no effect
        TaskOutcome.UP_TO_DATE == result.tasks.first().outcome

        when: // … we change the definition so output changes …
        super.setupBuildfile()
        buildFile << """
                import com.bmuschko.gradle.docker.tasks.image.Dockerfile

                task dockerFile(type: Dockerfile) {
                    outputs.upToDateWhen { true }
                    from "$TEST_IMAGE"
                    environmentVariable "Foo", "Bar"
                }
            """
        result = build('dockerFile')

        then: // … it is compiled, the additional spec is not relevant …
        TaskOutcome.SUCCESS == result.tasks.first().outcome

        when: // … just running the same …
        result = build('dockerFile')

        then: // … we are up to date …
        TaskOutcome.UP_TO_DATE == result.tasks.first().outcome

        when: // … a user spec return false …
        super.setupBuildfile()
        buildFile << """
                import com.bmuschko.gradle.docker.tasks.image.Dockerfile

                task dockerFile(type: Dockerfile) {
                    outputs.upToDateWhen { false }
                    from "$TEST_IMAGE"
                    environmentVariable "Foo", "Bar"
                }
            """
        // println buildFile.text
        result = build('dockerFile')

        then: // … it succeeds …
        TaskOutcome.SUCCESS == result.tasks.first().outcome

        when: // … always …
        result = build('dockerFile')

        then: // …
        TaskOutcome.SUCCESS == result.tasks.first().outcome
    }

    def "Do not fail on configuration phase"() {

        new File("$projectDir/HelloWorld.txt").withWriter('UTF-8') {
            it.write('Hello, World!')
        }

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile

            task dockerFile(type: Dockerfile) {
                defaultCommand "foo"
                // expected missing from
            }

            task dockerFile2(type: Dockerfile) {
                from "$TEST_IMAGE"
                environmentVariable "", "value"
            }

            task clean {}
        """

        expect:
        build('clean')
    }

    def "Do fail on task execution phase"() {

        new File("$projectDir/HelloWorld.txt").withWriter('UTF-8') {
            it.write('Hello, World!')
        }

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile

            task dockerFile(type: Dockerfile) {
                defaultCommand "foo"
                // expected missing from
            }

            task dockerFile2(type: Dockerfile) {
                from "$TEST_IMAGE"
                environmentVariable "", "value"
            }

            task clean {}
        """

        expect:
        buildAndFail('dockerFile')

        and:
        buildAndFail('dockerFile2')
    }
}
