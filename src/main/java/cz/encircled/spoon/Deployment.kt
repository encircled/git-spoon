package cz.encircled.spoon

import cz.encircled.spoon.Log.error
import cz.encircled.spoon.Log.info
import cz.encircled.spoon.Log.warn
import kotlinx.coroutines.async
import java.time.Duration
import java.util.*
import kotlin.concurrent.fixedRateTimer

class Deployment(val params: DeploymentParams) {

    private val timer: Timer

    init {
        validateParams(params)

        info("", "Let the deployment begin - ${params.name}",
                "source repo with manifests: ${params.repository}",
                "re-scan interval: ${params.interval}", "")

        timer = fixedRateTimer(name = "deploy-${params.name}", period = parseDuration(params.interval), initialDelay = 5000) {
            kotlinx.coroutines.GlobalScope.async {
                try {
                    perform()
                } catch (e: Exception) {
                    info("Whoops, something went wrong: ${e.message}")
                }
            }
        }

    }

    private fun validateParams(params: DeploymentParams) {
        if (params.repository.isEmpty()) Log.fatal("Source repository must be set")
        if (params.namespace.isEmpty()) Log.fatal("Kubernetes namespace must be set")
        if (params.workingDir.isEmpty()) Log.fatal("Working dir must be set")
    }

    private fun parseDuration(source: String): Long =
            Duration.parse("PT$source".toUpperCase()).toMillis()

    fun terminate() = timer.cancel()

    fun perform(): ExecResult {
        val currDir = FileUtils.nestedPath(params.workingDir, params.name)

        return FileUtils.prepareCleanDirectory(currDir)
                .onError { error(it) }

                .then { info("${params.name} - Performing git clone") }
                .thenExec("git clone --depth=1 ${params.repository} $currDir")
                .onError { error("${params.name} - Failed to clone git repository", it) }
                .onSuccess { info("${params.name} - Git repository successfully cloned") }

                .then { info("${params.name} - Applying changes to the cluster...") }
                .thenExec("kubectl apply --namespace=${params.namespace} -R --filename=$currDir")
                .onError { warn("${params.name} - Errors during applying changes", it) }
                .onSuccess { info("Done:", it) }

                .then { info("Zzzz...", "") }
    }

}