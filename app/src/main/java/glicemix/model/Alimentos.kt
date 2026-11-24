package glicemix.model

/** De onde veio o número de carboidratos: a tela sempre mostra isso, porque nem toda fonte merece a mesma confiança. */
enum class Origem(val rotulo: String) {
    TACO("TACO"),
    ROTULO("Rótulo"),
    PROPRIO("Próprio");
}

/** Alimento com carboidratos por 100 g. [carboidratos100] nulo = a fonte não informa (nunca assumir zero). */
data class Alimento(
    val id: String,
    val nome: String,
    val grupo: String,
    val carboidratos100: Double?,
    val fibra100: Double? = null,
    val kcal100: Int? = null,
    val origem: Origem = Origem.TACO,
    /** Aviso a mostrar junto ao alimento (ex.: "A TACO não informa carboidratos; use o rótulo"). */
    val aviso: String = "",
    /** Porção que a própria pessoa pesou (ex.: "1 concha = 90 g"). */
    val porcaoNome: String = "",
    val porcaoGramas: Double? = null,
) {
    val temCarboidratos get() = carboidratos100 != null
    fun carboidratosPara(gramas: Double): Double? = carboidratos100?.let { it * gramas / 100.0 }
}

/** Um alimento pesado dentro de uma refeição. Guarda o que foi usado na conta, para o histórico continuar verificável. */
data class ItemRefeicao(
    val nome: String,
    val gramas: Double,
    val carboidratos100: Double,
    val origem: Origem,
) {
    val carboidratos: Double get() = carboidratos100 * gramas / 100.0
}

/** Soma dos carboidratos, em gramas, com uma casa decimal. */
fun List<ItemRefeicao>.totalCarboidratos(): Double = Math.round(sumOf { it.carboidratos } * 10.0) / 10.0

/** Texto curto para a descrição da refeição: "Arroz, Feijão, Bife". */
fun List<ItemRefeicao>.resumo(): String = joinToString(", ") { it.nome.substringBefore(",").trim() }
