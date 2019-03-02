package com.github.fluidsonic.fluid.compiler

import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.base.kapt3.AptMode
import org.jetbrains.kotlin.base.kapt3.KaptFlag
import org.jetbrains.kotlin.base.kapt3.KaptOptions
import org.jetbrains.kotlin.base.kapt3.logString
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.config.JavaSourceRoot
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.kapt3.Kapt3ComponentRegistrar
import org.jetbrains.kotlin.kapt3.base.Kapt
import org.jetbrains.kotlin.kapt3.base.util.KaptLogger
import org.jetbrains.kotlin.kapt3.util.MessageCollectorBackedKaptLogger
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import java.io.File


private val kaptConfiguration = ThreadLocal<KaptConfiguration?>()


@Suppress("RemoveEmptyPrimaryConstructor")
internal class KaptComponentRegistrar() : ComponentRegistrar {

	// https://github.com/JetBrains/kotlin/blob/d6459e6c49227be11323ce59310407b17005c117/plugins/kapt3/kapt3-compiler/src/org/jetbrains/kotlin/kapt3/Kapt3Plugin.kt#L150
	override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
		val kaptConfiguration = kaptConfiguration.get() ?: return
		if (kaptConfiguration.processors.isEmpty()) return

		val contentRoots = configuration[CLIConfigurationKeys.CONTENT_ROOTS] ?: emptyList()

		val optionsBuilder = kaptConfiguration.options.toBuilder().apply {
			projectBaseDir = project.basePath?.let(::File)
			compileClasspath.addAll(contentRoots.filterIsInstance<JvmClasspathRoot>().map { it.file })
			javaSourceRoots.addAll(contentRoots.filterIsInstance<JavaSourceRoot>().map { it.file })
			classesOutputDir = classesOutputDir ?: configuration.get(JVMConfigurationKeys.OUTPUT_DIRECTORY)
		}

		val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY)
			?: PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, optionsBuilder.flags.contains(KaptFlag.VERBOSE))

		val logger = MessageCollectorBackedKaptLogger(
			optionsBuilder.flags.contains(KaptFlag.VERBOSE),
			optionsBuilder.flags.contains(KaptFlag.INFO_AS_WARNINGS),
			messageCollector
		)

		if (!optionsBuilder.checkOptions(project, logger, configuration)) {
			return
		}

		if (!optionsBuilder.processingOptions.containsKey(ProcessingOptions.kotlinSourcesOutputDirectory)) {
			optionsBuilder.processingOptions[ProcessingOptions.kotlinSourcesOutputDirectory] = optionsBuilder.sourcesOutputDir?.absolutePath
				?: error("sourcesOutputDir should be set at this point")
		}

		val options = optionsBuilder.build()

		options.sourcesOutputDir.mkdirs()

		if (options[KaptFlag.VERBOSE]) {
			logger.info(options.logString())
		}

		val kapt3AnalysisCompletedHandlerExtension = KaptExtension(
			compilerConfiguration = configuration,
			logger = logger,
			options = options,
			processors = kaptConfiguration.processors
		)

		AnalysisHandlerExtension.registerExtension(project, kapt3AnalysisCompletedHandlerExtension)
		StorageComponentContainerContributor.registerExtension(project, Kapt3ComponentRegistrar.KaptComponentContributor())
	}


	private object ProcessingOptions {

		const val kotlinSourcesOutputDirectory = "kapt.kotlin.generated"
	}


	// https://github.com/JetBrains/kotlin/blob/d6459e6c49227be11323ce59310407b17005c117/plugins/kapt3/kapt3-compiler/src/org/jetbrains/kotlin/kapt3/Kapt3Plugin.kt#L186
	private fun KaptOptions.Builder.checkOptions(project: MockProject, logger: KaptLogger, configuration: CompilerConfiguration): Boolean {
		fun abortAnalysis() = AnalysisHandlerExtension.registerExtension(project, AbortAnalysisHandlerExtension())

		if (classesOutputDir == null) {
			if (configuration.get(JVMConfigurationKeys.OUTPUT_JAR) != null) {
				logger.error("Kapt does not support specifying JAR file outputs. Please specify the classes output directory explicitly.")
				abortAnalysis()
				return false
			}
			else {
				classesOutputDir = configuration.get(JVMConfigurationKeys.OUTPUT_DIRECTORY)
			}
		}

		// not used as we pass Processor instances directly
//		if (processingClasspath.isEmpty()) {
//			// Skip annotation processing if no annotation processors were provided
//			if (mode != AptMode.WITH_COMPILATION) {
//				abortAnalysis()
//			}
//			return false
//		}

		if (sourcesOutputDir == null || classesOutputDir == null || stubsOutputDir == null) {
			if (mode != AptMode.WITH_COMPILATION) {
				val nonExistentOptionName = when {
					sourcesOutputDir == null -> "Sources output directory"
					classesOutputDir == null -> "Classes output directory"
					stubsOutputDir == null -> "Stubs output directory"
					else -> throw IllegalStateException()
				}
				val moduleName = configuration.get(CommonConfigurationKeys.MODULE_NAME)
					?: configuration.get(JVMConfigurationKeys.MODULES).orEmpty().joinToString()

				logger.warn("$nonExistentOptionName is not specified for $moduleName, skipping annotation processing")
				abortAnalysis()
			}
			return false
		}

		if (!Kapt.checkJavacComponentsAccess(logger)) {
			abortAnalysis()
			return false
		}

		return true
	}


	// https://github.com/JetBrains/kotlin/blob/d6459e6c49227be11323ce59310407b17005c117/plugins/kapt3/kapt3-compiler/src/org/jetbrains/kotlin/kapt3/Kapt3Plugin.kt#L243
	/* This extension simply disables both code analysis and code generation.
     * When aptOnly is true, and any of required kapt options was not passed, we just abort compilation by providing this extension.
     * */
	private class AbortAnalysisHandlerExtension : AnalysisHandlerExtension {
		override fun doAnalysis(
			project: Project,
			module: ModuleDescriptor,
			projectContext: ProjectContext,
			files: Collection<KtFile>,
			bindingTrace: BindingTrace,
			componentProvider: ComponentProvider
		): AnalysisResult? {
			return AnalysisResult.success(bindingTrace.bindingContext, module, shouldGenerateCode = false)
		}

		override fun analysisCompleted(
			project: Project,
			module: ModuleDescriptor,
			bindingTrace: BindingTrace,
			files: Collection<KtFile>
		): AnalysisResult? {
			return AnalysisResult.success(bindingTrace.bindingContext, module, shouldGenerateCode = false)
		}
	}
}


internal inline fun <R> withKaptConfiguration(configuration: KaptConfiguration?, block: () -> R): R {
	configuration ?: return block()

	val previousConfiguration = kaptConfiguration.get()
	kaptConfiguration.set(configuration)

	try {
		return block()
	}
	finally {
		kaptConfiguration.set(previousConfiguration)
	}
}
