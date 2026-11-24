package glicemix.data

import glicemix.model.Alimento
import glicemix.model.Origem
import java.text.Normalizer

/** Busca tolerante a acento, maiúscula e vírgula: "feijao carioca" encontra "Feijão, carioca, cozido". */
object BuscaAlimentos {
    private val acentos = Regex("\\p{M}+")

    fun normalizar(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(acentos, "").lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /**
     * Todas as palavras digitadas precisam começar alguma palavra do nome. Vêm primeiro os que começam pela primeira palavra
     * e, entre eles, os de nome mais curto; os alimentos da própria pessoa passam na frente.
     */
    private val gruposQueEngordamNaBalanca = setOf("Cereais e derivados", "Leguminosas e derivados")

    /** Arroz, feijão, macarrão etc. crus têm de 2 a 3 vezes mais carboidrato por grama do que cozidos. */
    fun ehCruPerigoso(a: Alimento): Boolean {
        if (a.grupo !in gruposQueEngordamNaBalanca) return false
        val p = normalizar(a.nome).split(" ")
        return "cru" in p || "crua" in p || "crus" in p || "cruas" in p
    }

    fun buscar(consulta: String, base: List<Alimento>, limite: Int = 40): List<Alimento> {
        val tokens = normalizar(consulta).split(" ").filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return base.mapNotNull { a ->
            val palavras = normalizar(a.nome).split(" ")
            if (!tokens.all { t -> palavras.any { it.startsWith(t) } }) return@mapNotNull null
            // "Arroz, tipo 1, cozido" antes de "Arroz carreteiro"; cru de cereal/leguminosa só no fim, a menos que se peça "cru"
            val pedeCru = tokens.any { it == "cru" || it == "crua" || it == "crus" }
            val pontos = (if (palavras.first().startsWith(tokens.first())) 0 else 100) +
                (if (normalizar(a.nome.substringBefore(",")) == tokens.first()) 0 else 20) +
                (if (a.origem == Origem.TACO) 50 else 0) +
                (if (!pedeCru && ehCruPerigoso(a)) 200 else 0) + a.nome.length
            a to pontos
        }.sortedBy { it.second }.take(limite).map { it.first }
    }
}

/** A TACO embarcada (assets/taco.tsv). Linhas que começam com # são comentário. */
class BaseAlimentos(val alimentos: List<Alimento>) {

    fun buscar(consulta: String, extras: List<Alimento> = emptyList(), limite: Int = 40) =
        BuscaAlimentos.buscar(consulta, extras + alimentos, limite)

    companion object {
        const val FONTE = "Tabela Brasileira de Composição de Alimentos (TACO), 4ª ed. rev. e ampl., NEPA/UNICAMP, 2011."

        fun deTsv(texto: String): BaseAlimentos {
            val lista = texto.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { l ->
                val c = l.split("\t")
                if (c.size < 7) return@mapNotNull null
                val estado = c[6].trim()
                val carbo = c[3].toDoubleOrNull()
                Alimento(
                    id = "taco-${c[0]}", nome = c[1], grupo = c[2],
                    carboidratos100 = if (estado == "SEM_DADO") null else carbo,
                    fibra100 = c[4].toDoubleOrNull(), kcal100 = c[5].toIntOrNull(),
                    origem = Origem.TACO,
                    aviso = when (estado) {
                        "SEM_DADO" -> "A TACO não informa os carboidratos deste alimento. Use o rótulo da embalagem."
                        "ZERO" -> "Sem carboidratos."
                        else -> ""
                    },
                ).let { a ->
                    if (BuscaAlimentos.ehCruPerigoso(a)) a.copy(aviso = "CRU: o valor vale para o alimento antes de cozinhar. Se você pesou já cozido, escolha a versão cozida.") else a
                }
            }.toList()
            return BaseAlimentos(lista)
        }
    }
}
