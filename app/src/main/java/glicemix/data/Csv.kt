package glicemix.data

/**
 * CSV mínimo (separador ';', compatível com Excel em português), com suporte a aspas,
 * separadores e quebras de linha dentro dos campos.
 */
object Csv {
    const val SEP = ';'

    fun escapar(campo: String): String =
        if (campo.any { it == SEP || it == '"' || it == '\n' || it == '\r' })
            "\"" + campo.replace("\"", "\"\"") + "\""
        else campo

    fun linha(campos: List<String>): String = campos.joinToString(SEP.toString()) { escapar(it) }

    /** Converte o texto completo em linhas de campos. */
    fun ler(texto: String): List<List<String>> {
        val linhas = mutableListOf<List<String>>()
        var campos = mutableListOf<String>()
        val atual = StringBuilder()
        var emAspas = false
        var i = 0
        while (i < texto.length) {
            val c = texto[i]
            if (emAspas) {
                if (c == '"') {
                    if (i + 1 < texto.length && texto[i + 1] == '"') { atual.append('"'); i++ }
                    else emAspas = false
                } else atual.append(c)
            } else when (c) {
                '"' -> emAspas = true
                SEP -> { campos.add(atual.toString()); atual.setLength(0) }
                '\r' -> {}
                '\n' -> {
                    campos.add(atual.toString()); atual.setLength(0)
                    if (campos.any { it.isNotEmpty() }) linhas.add(campos)
                    campos = mutableListOf()
                }
                else -> atual.append(c)
            }
            i++
        }
        if (atual.isNotEmpty() || campos.isNotEmpty()) {
            campos.add(atual.toString())
            if (campos.any { it.isNotEmpty() }) linhas.add(campos)
        }
        return linhas
    }
}
