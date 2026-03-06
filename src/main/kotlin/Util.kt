import java.io.File
import java.util.HashMap
import kotlin.math.max

fun File.getMimeType(): String {
    return when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }
}
fun extractKieAIUrlFromResponse(responseBody: String): String? {
    return try {
        // Парсим JSON ответ от KIE AI API
        val json = io.vertx.core.json.JsonObject(responseBody)
        if (json.getBoolean("success", false)) {
            val data = json.getJsonObject("data")
            data?.getString("downloadUrl")
        } else {
            null
        }
    } catch (e: Exception) {
        println("Error parsing KIE AI response: ${e.message}")
        null
    }
}
fun getNextNoExistFile(folderName:String, fileBase:String, extension:String):File{
    val folder = File("${AiUsageConfig.storageFolder}/$folderName")
    val sameBaseFiles = folder.listFiles()?.filter { it.name.substringBeforeLast("_") == fileBase }
    val indexByFilesCount = sameBaseFiles?.size?:0
    val indexByNames = (sameBaseFiles?.maxOfOrNull { it.nameWithoutExtension.substringAfterLast("_").toIntOrNull() ?: -1 } ?: -1)+1
    val index = max(indexByFilesCount, indexByNames)
    val fileName = "${fileBase}_$index.$extension"
    val file = File("${folder.path}/$fileName")
    file.parentFile.mkdirs()
    return file
}


var _translitLetters : HashMap<Char, String>?=null
val translitLetters : HashMap<Char, String>
    get() {
        if(_translitLetters ==null){
            val result = HashMap<Char, String>()
            (0..9).forEach {
                val char = it.toString()[0]
                result[char] = it.toString()
            }
            ('a' .. 'z').forEach {  result[it] = it.toString() }
            ('A' .. 'Z').forEach {  result[it] = it.toString() }
            result[' '] = "_"
            result['а'] = "a"
            result['б'] =  "b"
            result['в'] =  "v"
            result['г'] =  "g"
            result['д'] =  "d"
            result['е'] =  "e"
            result['ё'] =  "e"
            result['ж'] =  "zh"
            result['з'] =  "z"
            result['и'] =  "i"
            result['й'] =  "y"
            result['к'] =  "k"
            result['л'] =  "l"
            result['м'] =  "m"
            result['н'] =  "n"
            result['о'] =  "o"
            result['п'] =  "p"
            result['р'] =  "r"
            result['с'] =  "s"
            result['т'] =  "t"
            result['у'] =  "u"
            result['ф'] =  "f"
            result['х'] =  "h"
            result['ц'] =  "c"
            result['ч'] =  "ch"
            result['ш'] =  "sh"
            result['щ'] =  "sch"
            result['ь'] =  ""
            result['ы'] =  "y"
            result['ъ'] =  ""
            result['э'] =  "e"
            result['ю'] =  "yu"
            result['я'] =  "ya"

            result['А'] =  "A"
            result['Б'] =  "B"
            result['В'] =  "V"
            result['Г'] =  "G"
            result['Д'] =  "D"
            result['Е'] =  "E"
            result['Ё'] =  "E"
            result['Ж'] =  "Zh"
            result['З'] =  "Z"
            result['И'] =  "I"
            result['Й'] =  "Y"
            result['К'] =  "K"
            result['Л'] =  "L"
            result['М'] =  "M"
            result['Н'] =  "N"
            result['О'] =  "O"
            result['П'] =  "P"
            result['Р'] =  "R"
            result['С'] =  "S"
            result['Т'] =  "T"
            result['У'] =  "U"
            result['Ф'] =  "F"
            result['Х'] =  "H"
            result['Ц'] =  "C"
            result['Ч'] =  "Ch"
            result['Ш'] =  "Sh"
            result['Щ'] =  "Sch"
            result['Ь'] =  ""
            result['Ы'] =  "Y"
            result['Ъ'] =  ""
            result['Э'] =  "E"
            result['Ю'] =  "Yu"
            result['Я'] =  "Ya"
            _translitLetters = result
        }
        return _translitLetters!!
    }
fun String.isEnglishOnly(): Boolean {
    if (this.isEmpty()) return true

    for (char in this) {
        when {
            char in 'a'..'z' -> continue  // английские строчные буквы
            char in 'A'..'Z' -> continue  // английские заглавные буквы
            char in '0'..'9' -> continue  // цифры
            char == ' ' -> continue       // пробел
            char.isWhitespace() -> continue // другие пробельные символы
            char.isLetterOrDigit() -> return false // не-английские буквы
            char.isLetter() -> return false // не-английские буквы
            else -> continue // знаки препинания и символы разрешены
        }
    }
    return true
}
fun String.toTranslit():String{
    val result = java.lang.StringBuilder()
    this.forEach {
        translitLetters[it]?.let{
            result.append(it)
        }
    }

    return result.toString()
}
fun String.cutJsonObject():String{
    return "{" + this.substringAfter("{").substringBeforeLast("}") + "}"
}
fun String.cutJsonArray():String{
    return "[" + this.substringAfter("[").substringBeforeLast("]") + "]"
}
