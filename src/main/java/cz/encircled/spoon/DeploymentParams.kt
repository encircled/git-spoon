package cz.encircled.spoon

data class DeploymentConfiguration(var version: String = "", var deployments: Map<String, DeploymentParams> = mapOf())

data class DeploymentParams(var name: String = "default",
                            var repository: String = "",
                            var namespace: String = "",
                            var workingDir: String = "",
                            var interval: String = "") {

    override fun toString() =
            "\nname: $name\n" +
                    "repo: $repository\n" +
                    "namespace: $namespace\n" +
                    "interval: $interval\n"
}