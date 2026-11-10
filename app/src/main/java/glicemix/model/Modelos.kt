package glicemix.model

import java.time.LocalDateTime
import java.util.UUID

/** Gera um identificador único para cada registro. */
fun novoId(): String = UUID.randomUUID().toString()

enum class MomentoMedicao(val rotulo: String) {
    JEJUM("Jejum"),
    PRE_REFEICAO("Pré-refeição"),
    POS_REFEICAO("Pós-refeição"),
    ANTES_DORMIR("Antes de dormir"),
    MADRUGADA("Madrugada"),
    ALEATORIA("Aleatória");

    override fun toString() = rotulo
}

enum class TipoRefeicao(val rotulo: String) {
    CAFE_DA_MANHA("Café da manhã"),
    LANCHE_MANHA("Lanche da manhã"),
    ALMOCO("Almoço"),
    LANCHE_TARDE("Lanche da tarde"),
    JANTAR("Jantar"),
    CEIA("Ceia");

    override fun toString() = rotulo
}

enum class TipoDose(val rotulo: String, val ehBolus: Boolean) {
    ULTRARRAPIDA("Insulina ultrarrápida", true),
    RAPIDA("Insulina rápida (regular)", true),
    CORRECAO("Dose de correção", true),
    NPH("Insulina NPH", false),
    BASAL("Insulina basal / lenta", false),
    ORAL("Medicamento oral", false),
    OUTRO("Outro", false);

    override fun toString() = rotulo
}

enum class Intensidade(val rotulo: String, val fator: Double) {
    LEVE("Leve", 1.0),
    MODERADA("Moderada", 1.6),
    INTENSA("Intensa", 2.3);

    override fun toString() = rotulo
}

/** Base comum de tudo o que é registrado no diário. */
sealed interface Registro {
    val id: String
    val dataHora: LocalDateTime
    val observacao: String
}

/** Glicemia capilar ou de sensor, em mg/dL. */
data class Medicao(
    override val id: String = novoId(),
    override val dataHora: LocalDateTime,
    val valor: Int,
    val momento: MomentoMedicao,
    override val observacao: String = "",
) : Registro

data class Refeicao(
    override val id: String = novoId(),
    override val dataHora: LocalDateTime,
    val tipo: TipoRefeicao,
    val descricao: String,
    /** Carboidratos estimados, em gramas. */
    val carboidratos: Double,
    override val observacao: String = "",
) : Registro

data class Dose(
    override val id: String = novoId(),
    override val dataHora: LocalDateTime,
    val tipo: TipoDose,
    /** Nome comercial ou princípio ativo (ex.: "Fiasp", "Tresiba", "Metformina"). */
    val medicamento: String,
    val quantidade: Double,
    /** "UI" para insulina, "mg" ou "comp." para orais. */
    val unidade: String = "UI",
    override val observacao: String = "",
) : Registro

data class Atividade(
    override val id: String = novoId(),
    override val dataHora: LocalDateTime,
    val tipo: String,
    val duracaoMin: Int,
    val intensidade: Intensidade,
    override val observacao: String = "",
) : Registro {
    val fim: LocalDateTime get() = dataHora.plusMinutes(duracaoMin.toLong())
}

/** Faixas-alvo usadas em toda a análise. Padrões do consenso internacional de Tempo no Alvo. */
data class Configuracao(
    val nome: String = "",
    val hipoSevera: Int = 54,
    val alvoMin: Int = 70,
    val alvoMax: Int = 180,
    val hiperSevera: Int = 250,
) {
    fun classificar(valor: Int): Faixa = when {
        valor < hipoSevera -> Faixa.MUITO_BAIXA
        valor < alvoMin -> Faixa.BAIXA
        valor <= alvoMax -> Faixa.NO_ALVO
        valor <= hiperSevera -> Faixa.ALTA
        else -> Faixa.MUITO_ALTA
    }
}

enum class Faixa(val rotulo: String) {
    MUITO_BAIXA("Muito baixa"),
    BAIXA("Baixa"),
    NO_ALVO("No alvo"),
    ALTA("Alta"),
    MUITO_ALTA("Muito alta"),
}
