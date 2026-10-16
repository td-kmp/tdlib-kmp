import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/** kotlinx.serialization DTOs for every td_api.tl type and function. */
@CacheableTask
abstract class GenerateTdApi : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val schema: ConfigurableFileCollection

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val tl = parseTlSchema(schema.singleFile)
        val pkg = packageName.get()
        val root = outputDirectory.get().asFile.apply { deleteRecursively() }
        val dir = root.resolve(pkg.replace('.', '/')).apply { mkdirs() }
        TdApiWriter(tl, pkg).writeTo(dir)
    }
}

private class TdApiWriter(private val tl: TlSchema, private val pkg: String) {

    fun writeTo(dir: File) {
        file(dir, "TdObject") {
            appendLine("@Serializable")
            appendLine("public sealed interface TdObject")
            appendLine()
            appendLine("/** A TDLib function; [R] is the object TDLib answers with. */")
            appendLine("@Serializable")
            appendLine("public sealed interface TdFunction<out R : TdObject>")
        }
        tl.constructorsByType.forEach { (type, constructors) ->
            file(dir, type) {
                if (tl.isConcrete(type)) {
                    writeClass(constructors.single(), supertype = "TdObject")
                } else {
                    doc(tl.classDocs[type].orEmpty(), emptyList())
                    appendLine("@Serializable")
                    appendLine("public sealed interface $type : TdObject")
                    constructors.forEach {
                        appendLine()
                        writeClass(it, supertype = type)
                    }
                }
            }
        }
        // One file per first letter keeps ~900 functions to a few dozen files.
        tl.functions.groupBy { it.name.first().uppercaseChar() }.forEach { (letter, functions) ->
            file(dir, "Functions$letter") {
                functions.forEachIndexed { i, function ->
                    if (i > 0) appendLine()
                    writeClass(function, supertype = "TdFunction<${function.result}>")
                }
            }
        }
    }

    private fun StringBuilder.writeClass(c: TlConstructor, supertype: String) {
        doc(c.doc, c.fields)
        appendLine("@Serializable")
        appendLine("@SerialName(\"${c.name}\")")
        val name = c.name.pascal()
        if (c.fields.isEmpty()) {
            appendLine("public data object $name : $supertype")
            return
        }
        appendLine("public data class $name(")
        c.fields.forEach { f ->
            if (f.name.camel() != f.name) appendLine("    @SerialName(\"${f.name}\")")
            appendLine("    val ${f.name.camel().escaped()}: ${kotlinType(f.type, f.nullable)} = ${default(f)},")
        }
        appendLine(") : $supertype")
    }

    private fun kotlinType(type: TlType, nullable: Boolean): String {
        val base = when (type) {
            is TlType.Builtin -> when (type.name) {
                "int32" -> "kotlin.Int"
                // TDLib writes int64 as a string; kotlinx reads that into Long, and TDLib accepts the number back.
                "int53", "int64" -> "kotlin.Long"
                "double" -> "kotlin.Double"
                "string" -> "kotlin.String"
                "bytes" -> "@Serializable(TdBytesSerializer::class) kotlin.ByteArray"
                "Bool" -> "kotlin.Boolean"
                else -> error("Unknown builtin ${type.name}")
            }
            is TlType.Ref -> type.name.pascal()
            is TlType.Vector -> "kotlin.collections.List<${kotlinType(type.element, nullable = false)}>"
        }
        return if (nullable) "$base?" else base
    }

    private fun default(f: TlField): String {
        if (f.nullable) return "null"
        return when (val type = f.type) {
            is TlType.Builtin -> when (type.name) {
                "int32" -> "0"
                "int53", "int64" -> "0L"
                "double" -> "0.0"
                "string" -> "\"\""
                "bytes" -> "kotlin.ByteArray(0)"
                "Bool" -> "false"
                else -> error("Unknown builtin ${type.name}")
            }
            is TlType.Vector -> "kotlin.collections.emptyList()"
            // Required: TDLib never omits a non-nullable object.
            is TlType.Ref -> return requiredMarker
        }
    }

    private fun StringBuilder.doc(description: String, fields: List<TlField>) {
        val lines = buildList {
            if (description.isNotEmpty()) add(description)
            fields.filter { it.doc.isNotEmpty() }.forEach { add("@property ${it.name.camel()} ${it.doc}") }
        }
        if (lines.isEmpty()) return
        appendLine("/**")
        lines.forEach { appendLine(" * ${it.replace("*/", "* /")}") }
        appendLine(" */")
    }

    private fun file(dir: File, name: String, body: StringBuilder.() -> Unit) {
        val text = buildString {
            appendLine("// Generated from td_api.tl by :build-logic GenerateTdApi. Do not edit.")
            appendLine("@file:Suppress(\"ArrayInDataClass\", \"RedundantVisibilityModifier\", \"unused\")")
            appendLine()
            appendLine("package $pkg")
            appendLine()
            appendLine("import kotlinx.serialization.SerialName")
            appendLine("import kotlinx.serialization.Serializable")
            appendLine()
            body()
        }
        dir.resolve("$name.kt").writeText(text.replace(" = $requiredMarker,", ","))
    }

    private companion object {
        const val requiredMarker = "<required>"

        val KEYWORDS = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
            "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
            "true", "try", "typealias", "typeof", "val", "var", "when", "while",
        )

        fun String.escaped(): String = if (this in KEYWORDS) "`$this`" else this
    }
}
