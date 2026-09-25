plugins {
    base
}

tasks {
    val npmInstall = register<PixiExecTask>("npmInstall") {
        // `--frozen-lockfile` gives reproducible installs from pnpm-lock.yaml;
        // hoisted keeps a flat node_modules (npm-identical layout) while still
        // hardlinking from pnpm's shared global store.
        command=listOf("pnpm", "install", "--frozen-lockfile", "--config.node-linker=hoisted")
        doNotTrackState("pnpm already tracks its own state")
        reinstallLocallyBuiltPackages(LocallyBuiltPackage.KSON, LocallyBuiltPackage.KSON_TOOLING)
    }

    register<PixiExecTask>("npm_run_compile") {
        command=listOf("pnpm", "run", "compile")
        dependsOn(npmInstall)
    }

    register<PixiExecTask>("npm_run_test") {
        command=listOf("pnpm", "run", "test")
        dependsOn("npm_run_compile")
    }

    check {
        dependsOn("npm_run_test")
    }

    clean {
        delete("out")
        delete("node_modules")
    }
}
