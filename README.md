fluid-compiler
==============

[![Kotlin 1.3.21](https://img.shields.io/badge/Kotlin-1.3.21-blue.svg)](http://kotlinlang.org)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.fluidsonic/fluid-compiler.svg)](https://search.maven.org/search?q=g:com.github.fluidsonic%20a:fluid-compiler)
[![#fluid-libraries Slack Channel](https://img.shields.io/badge/slack-%23fluid--libraries-543951.svg)](https://kotlinlang.slack.com/messages/C7UDFSVT2/)

Compile Kotlin code and run Kapt annotation processing directly from Kotlin, for example to unit test your annotation processors!



Installation
------------

This library is [available in Maven Central](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.github.fluidsonic%22%20a%3A%22fluid-compiler%22) as `fluid-compiler`
in the group `com.github.fluidsonic`.

`build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.github.fluidsonic:fluid-compiler:0.9.2")
}
```



Example
-------

```kotlin
import com.github.fluidsonic.fluid.compiler.*

val result = KotlinCompiler()
    .includesCurrentClasspath()
    .jvmTarget(KotlinJvmTarget.v1_8)
    .processors(MyAnnotationProcessor())
    .sources("sources", "more-sources/Example.kt")
    .compile()

// result.exitCode contains the exit code of the compiler
// result.messages contains all messages printed during compilation and annotation processing
// result.generatedFiles contains all files generated by annotation processors
```


### Additional configuration

```kotlin
    .destination("output")
    .kotlinHome("/path/to/kotlin/home")
    .moduleName("my-module")
```


### Manually setting compiler arguments

```kotlin
    .arguments {
        apiVersion = "1.2"
        languageVersion = "1.2"
        newInference = true
    }
```

For a complete list of all compiler arguments check out
[K2JVMCompilerArguments](https://github.com/JetBrains/kotlin/blob/master/compiler/cli/cli-common/src/org/jetbrains/kotlin/cli/common/arguments/K2JVMCompilerArguments.kt).


### Manually setting Kapt options

```kotlin
    .kaptOptions {
        flags += KaptFlag.CORRECT_ERROR_TYPES
        mode = AptMode.STUBS_AND_APT
    }
```

For a complete list of all Kapt options check out
[KaptOptions](https://github.com/JetBrains/kotlin/blob/master/plugins/kapt3/kapt3-base/src/org/jetbrains/kotlin/kapt3/base/KaptOptions.kt).



License
-------

Apache 2.0
