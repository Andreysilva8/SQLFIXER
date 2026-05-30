# MySQLFixer

Projeto Spring Boot organizado no pacote `br.com.empresa.intranet`, com camadas de configuracao, controllers, DTOs, entidades, repositorios, seguranca, servicos e utilitarios.

## Analise de anomalias

A antiga rotina Python foi migrada para Java em `DataAnomalyService`.

Endpoint principal:

`POST /api/analyzer/data`

Exemplo de corpo:

```json
{
  "sourceName": "clientes",
  "rows": [
    {"id": 1, "saldo": 25, "latencyMs": 10},
    {"id": 2, "saldo": -8, "latencyMs": 900}
  ]
}
```

O motor detecta valores ausentes, valores negativos invalidos e outliers por Z-score, retornando um `AnalysisReport` igual aos demais modulos do projeto.

## Monitoramento continuo do banco

A tela `analyze.html` possui controles para iniciar e parar uma varredura continua do banco conectado. O monitor roda no backend Spring, entao continua ativo enquanto a aplicacao estiver ligada.

O endpoint `POST /api/analyzer/database` aceita os campos:

```json
{
  "targetType": "table",
  "targetName": "usuarios",
  "monitor": true,
  "settings": {}
}
```

Valores de `targetType`: `full`, `tables`, `table`, `views`, `routines` e `performance`.

Endpoints do monitor:

- `POST /api/monitor/database/start`
- `POST /api/monitor/database/stop`
- `GET /api/monitor/database/status`
