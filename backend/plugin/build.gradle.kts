/*
 * Copyright 2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

val kotlinLoggingVersion: String by project
val mockitoKotlinVersion: String by project
val valtimoVersion: String by project
val apacheCxfVersion: String by project

// The modules with a ZGW authentication interface; the application decides which it has
val zgwModules =
    listOf(
        "zaken-api",
        "documenten-api",
        "catalogi-api",
        "besluiten-api",
        "notificaties-api",
        "objecten-api",
        "objecttypen-api",
    )

// The other auth plugins whose category interface this plugin implements, see CoveredInterfaces. Without their
// dependencies: each brings its own plugin, and the application decides which it has
// The Valtimo 12 (Java 17) versions; the interfaces are the same in the Valtimo 13 versions
val suwinetAuth = "com.ritense.valtimoplugins:suwinet-auth:1.2.0"
val coveredPlugins =
    listOf(
        "com.ritense.valtimoplugins:haal-centraal-authentication:0.13",
        "com.ritense.valtimoplugins:haal-centraal-authentication-plugin:1.0.1-V12",
        suwinetAuth,
        "com.ritense.valtimoplugins:token-authentication:0.9.1",
        "com.ritense.valtimoplugins:token-exchange-authentication:0.0.3-V12",
        "com.ritense.valtimoplugins:mTLS-SSLContext:1.0.1",
    )

// What the implementations of those interfaces use: SOAP (CXF) and reactor-netty
val cxfModules =
    listOf("org.apache.cxf:cxf-core:$apacheCxfVersion", "org.apache.cxf:cxf-rt-transports-http:$apacheCxfVersion")
val reactorNettyModules = listOf("io.projectreactor.netty:reactor-netty-http", "io.netty:netty-handler")
// A SOAP client for the tests, on top of cxfModules
val cxfTestModules = listOf("org.apache.cxf:cxf-rt-frontend-jaxws:$apacheCxfVersion")

// Shared by the test source sets, each of which adds its own modules
val integrationTestCommon by configurations.creating

dependencies {
    compileOnly("com.ritense.valtimo:plugin-valtimo")
    compileOnly("com.ritense.valtimo:process-document")
    compileOnly("com.ritense.valtimo:contract")
    zgwModules.forEach { compileOnly("com.ritense.valtimo:$it") }
    coveredPlugins.forEach { compileOnly(it) { isTransitive = false } }
    cxfModules.forEach { compileOnly(it) }
    reactorNettyModules.forEach { compileOnly(it) }
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework:spring-webflux")
    compileOnly("org.springframework.data:spring-data-jpa")
    compileOnly("org.apache.httpcomponents.client5:httpclient5")
    // Generates the plugin class at startup; every Valtimo application has it (Hibernate)
    compileOnly("net.bytebuddy:byte-buddy")

    // Valtimo 12 doesn't provide it
    implementation("io.github.oshai:kotlin-logging:$kotlinLoggingVersion")

    // Testing
    integrationTestCommon(platform("com.ritense.valtimo:valtimo-dependency-versions:$valtimoVersion"))
    integrationTestCommon("com.ritense.valtimo:plugin-valtimo")
    integrationTestCommon("com.ritense.valtimo:process-document")
    integrationTestCommon("com.ritense.valtimo:local-resource")
    integrationTestCommon("com.ritense.valtimo:test-utils-common")
    integrationTestCommon("com.ritense.valtimo:authorization")

    integrationTestCommon("org.springframework.boot:spring-boot-starter-web")
    integrationTestCommon("org.springframework:spring-webflux")
    integrationTestCommon("org.apache.httpcomponents.client5:httpclient5")
    integrationTestCommon("org.springframework.boot:spring-boot-starter-data-jpa")
    integrationTestCommon("org.springframework.boot:spring-boot-starter-security")
    integrationTestCommon("org.springframework.boot:spring-boot-starter-test")
    integrationTestCommon("net.bytebuddy:byte-buddy")

    integrationTestCommon("org.mockito:mockito-core")
    integrationTestCommon("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")

    integrationTestCommon("org.postgresql:postgresql")

    integrationTestCommon("org.jetbrains.kotlin:kotlin-test")
    integrationTestCommon("org.jetbrains.kotlin:kotlin-test-junit5")

    zgwModules.forEach { testImplementation("com.ritense.valtimo:$it") }
    // Every covered plugin next to this one, with what they need at runtime
    coveredPlugins.forEach { testImplementation(it) { isTransitive = false } }
    (cxfModules + cxfTestModules).forEach { testImplementation(it) }
    reactorNettyModules.forEach { testImplementation(it) }
    // To compare the ZGW JWT with Valtimo's
    testImplementation("com.ritense.valtimo:openzaak-plugin-authentication")
}

configurations.testImplementation { extendsFrom(integrationTestCommon) }

// The support interfaces implement the covered interfaces with default methods, which the generated class inherits
kotlin { compilerOptions { freeCompilerArgs.add("-Xjvm-default=all") } }

/*
 * Integration tests on the classpath of an application with other modules than the test source set, which has all
 * ZGW modules and covered plugins.
 * They reuse the test source set's base classes, fixtures and configuration.
 */
fun combinationTest(
    name: String,
    modules: List<String>,
): TaskProvider<Test> {
    val sourceSet =
        sourceSets.create(name) {
            compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
            runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        }
    // The test source set gets the main dependencies through testImplementation, these source sets don't
    configurations.named(sourceSet.implementationConfigurationName) {
        extendsFrom(integrationTestCommon, configurations.implementation.get())
    }
    dependencies {
        modules.forEach { add(sourceSet.implementationConfigurationName, it) }
    }
    val task =
        tasks.register<Test>(name) {
            description = "Runs the integration tests for an application with $modules."
            group = "verification"
            testClassesDirs = sourceSet.output.classesDirs
            classpath = sourceSet.runtimeClasspath
            shouldRunAfter(tasks.test)
        }
    tasks.check { dependsOn(task) }
    return task
}

// Nothing covered, not even CXF
val testWithoutZgw = combinationTest("testWithoutZgw", emptyList())
val testWithObjectenApi =
    combinationTest(
        "testWithObjectenApi",
        listOf("com.ritense.valtimo:objecten-api", "com.ritense.valtimo:objecttypen-api"),
    )
// SOAP only: no ZGW module, no other covered plugin
val testWithSuwinet =
    combinationTest(
        "testWithSuwinet",
        listOf(suwinetAuth) + cxfModules + cxfTestModules,
    )

val integrationTestTasks = listOf(tasks.test, testWithoutZgw, testWithObjectenApi, testWithSuwinet)

dockerCompose {
    setProjectName("http-client-authentication")
    useComposeFiles.addAll("$rootDir/docker-resources/docker-compose-base-test.yml", "docker-compose-override.yml")
    integrationTestTasks.forEach { isRequiredBy(it) }
}

// The database is started once for all test tasks, so it may only stop after the last
tasks.named("composeDown") { mustRunAfter(integrationTestTasks) }

apply(from = "gradle/publishing.gradle")
