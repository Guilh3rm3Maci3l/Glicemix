# Glicemix

Aplicativo Android para controle glicêmico pessoal: registre medições, refeições, doses de insulina
ou medicamentos e atividades físicas, acompanhe gráficos, descubra padrões e leve um relatório para a consulta.

## Funcionalidades
- **Painel**: última glicemia, média, HbA1c estimada (GMI), tempo no alvo, variabilidade (CV), hipoglicemias e linha do tempo.
- **Registros**: diário por dia com busca e filtros; a refeição pode incluir a dose de insulina; aviso da regra dos 15 em glicemias baixas.
- **Gráficos** (toque para ver detalhes): linha do tempo, perfil diário (AGP), tempo nas faixas por dia, distribuição,
  resposta às refeições, carboidratos × elevação, médias por momento/turno e efeito do exercício.
- **Padrões**: hipoglicemias recorrentes, hipoglicemia noturna, fenômeno do alvorecer, picos pós-refeição,
  relação carboidrato/insulina praticada, hipoglicemia tardia após exercício, tendência, fim de semana e regularidade da basal.
- **Lembretes** com notificação (*Registrar* / *Adiar 15 min*) e lembrete automático para medir 2h após cada refeição.
- **Relatório em PDF** do período, **planilha CSV** e **backup** (exporta/importa sem duplicar; compatível com o app desktop).

> Os padrões e estimativas são informativos e não substituem orientação médica.

## Executar
1. Abra a pasta do projeto no **Android Studio** e aguarde o *Gradle sync*.
2. Conecte um celular com **Android 8.0 ou superior** e a **Depuração USB** ativada (ou use um emulador).
3. Clique em **Run ▶**.

Para gerar um instalador: *Build › Build App Bundle(s) / APK(s) › Build APK(s)*. As versões são assinadas com a
mesma chave (`app/glicemix.keystore`), então uma nova versão instala por cima da anterior sem apagar os dados.

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
