import java.io.File

class TlField(val name: String, val type: TlType, val doc: String, val nullable: Boolean)

sealed interface TlType {
    data class Builtin(val name: String) : TlType
    data class Ref(val name: String) : TlType
    data class Vector(val element: TlType) : TlType
}

class TlConstructor(
    val name: String,
    val fields: List<TlField>,
    val result: String,
    val doc: String,
    val isFunction: Boolean,
)

class TlSchema(
    val constructors: List<TlConstructor>,
    val functions: List<TlConstructor>,
    /** Abstract type name → its //@class description. */
    val classDocs: Map<String, String>,
) {
    val constructorsByType: Map<String, List<TlConstructor>> = constructors.groupBy { it.result }

    /** A type with one constructor of the same name is a plain class rather than a sealed interface. */
    fun isConcrete(type: String): Boolean =
        constructorsByType[type]?.singleOrNull()?.let { it.name.pascal() == type } == true
}

private val BUILTINS = setOf("double", "string", "int32", "int53", "int64", "bytes", "Bool")
private val SKIPPED = setOf("double", "string", "int32", "int53", "int64", "bytes", "boolFalse", "boolTrue", "vector")
private val CONSTRUCTOR = Regex("""^(\w+)((?:\s+\w+:[\w<>]+)*)\s*=\s*(\w+);$""")

fun parseTlSchema(file: File): TlSchema {
    val constructors = mutableListOf<TlConstructor>()
    val functions = mutableListOf<TlConstructor>()
    val classDocs = mutableMapOf<String, String>()
    var inFunctions = false
    val doc = StringBuilder()

    for (raw in file.readLines()) {
        val line = raw.trim()
        when {
            line.isEmpty() -> Unit
            line == "---functions---" -> inFunctions = true
            line.startsWith("//@class ") -> {
                val match = Regex("""^//@class (\w+) @description (.*)$""").find(line) ?: continue
                classDocs[match.groupValues[1]] = match.groupValues[2]
            }
            line.startsWith("//-") -> doc.append(' ').append(line.removePrefix("//-").trim())
            line.startsWith("//@") -> doc.append(' ').append(line.removePrefix("//"))
            line.startsWith("//") -> Unit
            line.substringBefore(' ') in SKIPPED -> doc.clear()
            else -> {
                val match = CONSTRUCTOR.find(line) ?: error("Cannot parse TL line: $line")
                val (name, fieldsText, result) = match.destructured
                if (name !in SKIPPED) {
                    val rawFields = fieldsText.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
                    val names = rawFields.map { it.substringBefore(':') }
                    // Split only on "@description" and this constructor's field names: prose contains "@username" etc.
                    val docs = splitDoc(doc.toString(), names + "description")
                    val fields = rawFields.map { field ->
                        val fieldName = field.substringBefore(':')
                        val type = parseType(field.substringAfter(':'))
                        val fieldDoc = docs[fieldName].orEmpty()
                        val maybeNull = fieldDoc.contains("may be null", ignoreCase = true) ||
                            (inFunctions && fieldDoc.contains("; pass null", ignoreCase = true))
                        TlField(fieldName, type, fieldDoc, maybeNull && type !is TlType.Builtin)
                    }
                    val constructor = TlConstructor(name, fields, result, docs["description"].orEmpty(), inFunctions)
                    if (inFunctions) functions += constructor else constructors += constructor
                }
                doc.clear()
            }
        }
    }
    return TlSchema(constructors, functions, classDocs)
}

private fun splitDoc(text: String, keys: List<String>): Map<String, String> {
    val marker = Regex("""(?:^|\s)@(${keys.joinToString("|") { Regex.escape(it) }})\s""")
    val matches = marker.findAll(text).toList()
    return matches.mapIndexed { i, m ->
        val end = matches.getOrNull(i + 1)?.range?.first ?: text.length
        m.groupValues[1] to text.substring(m.range.last + 1, end).trim()
    }.toMap()
}

private fun parseType(text: String): TlType = when {
    text.startsWith("vector<") -> TlType.Vector(parseType(text.removePrefix("vector<").removeSuffix(">")))
    text in BUILTINS -> TlType.Builtin(text)
    else -> TlType.Ref(text)
}

fun String.pascal(): String = replaceFirstChar(Char::uppercase)

fun String.camel(): String = split('_').mapIndexed { i, part -> if (i == 0) part else part.pascal() }.joinToString("")
