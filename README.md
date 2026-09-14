# fiapx-gateway

Porta de entrada do FIAP X (Spring Cloud Gateway):

- serve a UI em `/`;
- roteia `/api/v1/auth/**` e `/.well-known/**` para o auth-service e
  `/api/v1/videos/**` para o video-api;
- valida o JWT antes de rotear (cada serviço revalida o mesmo token);
- limita o login a 10 tentativas por minuto por IP (`429` em problem+json).

## Rodar os testes

    ./mvnw verify

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `AUTH_SERVICE_URL` | `http://localhost:8081` | Destino das rotas de autenticação |
| `VIDEO_API_URL` | `http://localhost:8082` | Destino das rotas de vídeo |
| `JWKS_URI` | `http://localhost:8081/.well-known/jwks.json` | Chave pública do JWT |
