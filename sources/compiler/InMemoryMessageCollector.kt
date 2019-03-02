package com.github.fluidsonic.fluid.compiler

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector


internal class InMemoryMessageCollector : MessageCollector {

	private var hasErrors = false
	private val _messages = mutableListOf<CompilationMessage>()


	override fun clear() {
		_messages.clear()
	}


	override fun hasErrors() =
		hasErrors


	val messages
		get() = _messages.toList()


	override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {
		if (severity.isError)
			hasErrors = true

		_messages += CompilationMessage(location = location, message = message, severity = severity)
	}
}
