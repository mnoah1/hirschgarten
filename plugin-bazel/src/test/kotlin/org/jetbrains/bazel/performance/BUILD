load("//plugin-bazel/src/test/kotlin/org/jetbrains/bazel/ideStarter:tests.bzl", "ide_starter_test")

ide_starter_test(
    name = "performance",
    size = "large",
    src = "PerformanceTest.kt",
    classname = "org.jetbrains.bazel.performance.PerformanceTest",
    tags = ["manual"],
    deps = [
        "//plugin-bazel/src/test/kotlin/org/jetbrains/bazel/ideStarter:baseTest",
    ],
)
