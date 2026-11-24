# Glicemix

Aplicativo Android para controle glicêmico pessoal: registre medições, refeições, doses de insulina
ou medicamentos e atividades físicas, acompanhe gráficos, descubra padrões e leve um relatório para a consulta.

## Funcionalidades
- **Registro rápido**: Registrar → número → Salvar. A glicemia é digitada na própria folha, com o teclado já aberto.
- **Painel**: última glicemia com seta de tendência e mini-gráfico, uma frase de resumo, os indicadores principais (os demais num toque), o dia de hoje e os padrões.
- **Registros**: diário por dia com busca e filtros (inclusive por circunstância e "levar para a consulta"). A refeição leva a insulina ligada a ela e mostra o efeito: "112 → 198 mg/dL (+86) em 2 h". Refeições anteriores podem ser repetidas com um toque. Excluir tem **Desfazer**.
- **Contagem de carboidratos**: escolha o alimento na **TACO** (597 alimentos, offline), informe os gramas e veja os carboidratos na hora. O que não estiver na tabela, cadastre pelo rótulo (por 100 g ou por porção) ou como receita sua. A soma dos alimentos vira o total da refeição e fica guardada com a origem de cada número (TACO, rótulo ou próprio).
  - Alimentos **crus** de cereais e leguminosas aparecem depois dos cozidos e com aviso, porque têm 2 a 3 vezes mais carboidrato por grama.
  - Quando a TACO não traz o dado (leite, iogurte, coco-verde, aguardente), o app **não assume zero**: pede o rótulo.
  - Ao registrar a insulina, o app mostra **o que você aplicou em refeições parecidas e o resultado 2 h depois**. É consulta ao próprio histórico, não recomendação de dose, e não preenche nenhum campo.
- **Circunstâncias** (doença, estresse, álcool, viagem, sensor novo…) em qualquer registro; a análise compara os dias com e sem cada uma.
- **Análise**: Padrões, Gráficos, **Calendário** (cada dia colorido pelo tempo no alvo; toque para abrir o dia) e **Comparar** (período atual × anterior).
- Aviso da regra dos 15 em glicemias baixas.
- **Gráficos** (toque para ver detalhes): linha do tempo, perfil diário (AGP), tempo nas faixas por dia, distribuição,
  resposta às refeições, carboidratos × elevação, médias por momento/turno e efeito do exercício.
- **Padrões**: hipoglicemias recorrentes, hipoglicemia noturna, fenômeno do alvorecer, picos pós-refeição,
  relação carboidrato/insulina praticada, hipoglicemia tardia após exercício, tendência, fim de semana e regularidade da basal.
- **Próximas consultas e exames** no Painel (data, hora, local e aviso por notificação antes); **refeições favoritas** que aparecem primeiro ao repetir uma refeição; **relatório em PDF enviado ao médico com um toque**.
- **Lembretes** com notificação (*Registrar* / *Adiar 15 min*) e lembrete automático para medir 2h após cada refeição.
- **Relatório em PDF** do período (com os registros marcados com ★ e espaço para perguntas à equipe de saúde), **planilha CSV** e **backup** (exporta/importa sem duplicar; compatível com o app desktop).

> Os padrões e estimativas são informativos e não substituem orientação médica.

## Executar
1. Abra a pasta do projeto no **Android Studio** e aguarde o *Gradle sync*.
2. Conecte um celular com **Android 8.0 ou superior** e a **Depuração USB** ativada (ou use um emulador).
3. Clique em **Run ▶**.

Para gerar um instalador: *Build › Build App Bundle(s) / APK(s) › Build APK(s)*. As versões são assinadas com a
mesma chave (`app/glicemix.keystore`), então uma nova versão instala por cima da anterior sem apagar os dados.

## Fonte dos alimentos
Tabela Brasileira de Composição de Alimentos (TACO), 4ª edição revisada e ampliada, NEPA/UNICAMP, 2011 (reprodução permitida citando a fonte). O arquivo `app/src/main/assets/taco.tsv` foi gerado de conjuntos públicos em JSON/CSV e conferido entre três fontes independentes: os carboidratos dos 597 alimentos coincidem. Detalhes em `app/src/main/assets/TACO-FONTE.txt`.

## Dados
Ficam no banco **SQLite** do app (`glicemix.db`, na pasta privada do aparelho), com uma tabela por tipo de registro,
configuração e lembretes. Gravações em transação e índices por data. O banco entra no backup automático do Android.

## Estrutura
```
app/src/main/java/glicemix/
  model/       entidades (Medicao, Refeicao, Dose, Atividade, Configuracao)
  data/        repositório em memória, interface de persistência, CSV, backup e dados de exemplo
  analise/     estatísticas (TIR, GMI, CV, AGP) e detector de padrões
  relatorio/   layout do relatório (independente de plataforma)
  app/         Activity, ViewModel, banco SQLite e geração do PDF
  telas/       telas em Jetpack Compose
  graficos/    gráficos desenhados em Canvas
  lembretes/   agendamento (AlarmManager), notificações e reinício do aparelho
```
Os pacotes `model`, `data`, `analise` e `relatorio` não dependem do Android e são os mesmos do app desktop (Swing).

Os arquivos em `com/example/glicemixii` vieram do modelo do Android Studio e não são usados.
