package io.xbot.tdlib.worker

/** What a request asks the worker to do; which fields are set depends on [cmd]. */
internal external interface WorkerCommand : JsAny {
    var cmd: String
    var clientId: Int
    var request: String
    var maxVerbosityLevel: Int
    var enabled: Boolean
}

private fun newWorkerCommand(): WorkerCommand = js("({})")

internal fun WorkerCommand(cmd: String, build: WorkerCommand.() -> Unit = {}): WorkerCommand =
    newWorkerCommand().apply { this.cmd = cmd; build() }
