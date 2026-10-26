# Catálogo de Filmes — Redis como cache de uma aplicação com PostgreSQL

Estudo dirigido da disciplina **ADS1253 — Programação Orientada a Objetos com Banco de Dados**
(PUC Goiás · Escola Politécnica · Tecnologia em Análise e Desenvolvimento de Sistemas).

API REST de catálogo de filmes em **Spring Boot 3.5 / Java 21**, com **PostgreSQL** como fonte da
verdade e **Redis** como cache, tudo executado em contêineres Docker.

---

## Sumário

1. [O problema e a solução](#1-o-problema-e-a-solução)
2. [Arquitetura](#2-arquitetura)
3. [Requisitos](#3-requisitos)
4. [Como executar](#4-como-executar)
5. [Endpoints da API](#5-endpoints-da-api)
6. [Estrutura do projeto](#6-estrutura-do-projeto)
7. [Como o cache funciona no código](#7-como-o-cache-funciona-no-código)
8. [Etapas do estudo dirigido](#8-etapas-do-estudo-dirigido)
9. [Atividades complementares](#9-atividades-complementares)
10. [Reproduzindo os experimentos](#10-reproduzindo-os-experimentos)
11. [Problemas frequentes](#11-problemas-frequentes)
12. [Encerrando o ambiente](#12-encerrando-o-ambiente)

---

## 1. O problema e a solução

Sem cache, toda requisição repete a mesma consulta ao banco, mesmo que o dado não tenha mudado.
O usuário espera e o banco é sobrecarregado sem necessidade.

Com cache, o resultado da primeira consulta é guardado no Redis (memória). As consultas seguintes
são respondidas por essa cópia, sem tocar no PostgreSQL.

- **falta (miss):** a informação não está no cache → consulta o PostgreSQL e grava uma cópia no Redis.
- **acerto (hit):** a informação está no cache → devolve a cópia; o PostgreSQL não é consultado.

> **Princípio do projeto:** o PostgreSQL é a fonte da verdade. O Redis guarda apenas cópias
> temporárias. Se o Redis for esvaziado, nada se perde: as cópias são refeitas a partir do banco.

## 2. Arquitetura

```
                         1. consulta o cache
   Cliente  --->  Aplicação  ---------------->  Redis (memória, rápido)
   (curl)        Spring Boot <----------------
                      |        2. acerto: devolve a cópia
                      |
                      |  3. falta: consulta o banco
                      +--------------------->  PostgreSQL (disco, fonte da verdade)
                         4. grava a cópia no Redis e devolve
```

Três contêineres, definidos em [`docker-compose.yml`](docker-compose.yml):

| Serviço    | Contêiner           | Imagem        | Porta no computador |
|------------|---------------------|---------------|---------------------|
| `app`      | `catalogo-app`      | build local   | **8080**            |
| `postgres` | `catalogo-postgres` | `postgres:17` | — (só rede interna) |
| `redis`    | `catalogo-redis`    | `redis:8`     | — (só rede interna) |

Dentro da rede do Compose, a aplicação acessa `postgres:5432` e `redis:6379` (o nome do serviço
funciona como nome de máquina). O PostgreSQL e o Redis são acessados por `docker exec`, o que evita
conflito com instalações locais.

## 3. Requisitos

- **Docker Desktop** (ou Docker Engine com o plugin Compose) em execução.
- Um terminal e o comando `curl`. No **PowerShell** use `curl.exe` (`curl` é apelido de outro comando).

Não é necessário instalar Java, Maven, PostgreSQL ou Redis.

## 4. Como executar

```bash
git clone https://github.com/jvfsccp/atividade-redis.git
cd atividade-redis

# 1) sobe os servidores e espera ficarem "healthy"
docker compose up -d postgres redis
docker compose ps

# 2) compila e sobe a aplicação (a 1ª vez demora: o Maven baixa as dependências)
docker compose up -d --build app

# 3) primeira requisição
curl http://localhost:8080/filmes/1
```

Resposta esperada:

```json
{"id":1,"titulo":"Cidade de Deus","diretor":"Fernando Meirelles","ano":2002,"nota":8.6}
```

Para acompanhar o que a aplicação faz (mensagens de consulta ao PostgreSQL, SQL do Hibernate e
tempo de cada requisição), mantenha um segundo terminal aberto:

```bash
docker compose logs -f app
```

> **Regra importante:** sempre que um arquivo Java for alterado, reconstrua a imagem com
> `docker compose up -d --build app`. Sem `--build`, o contêiner continua com a versão anterior.

## 5. Endpoints da API

| Método   | Rota                        | Descrição                              | Efeito no cache                         |
|----------|-----------------------------|----------------------------------------|-----------------------------------------|
| `GET`    | `/filmes/{id}`              | Busca um filme                         | `@Cacheable` — lê/grava `filmes::{id}`  |
| `PUT`    | `/filmes/{id}/nota?valor=X` | Altera a nota do filme                 | `@CachePut` — atualiza `filmes::{id}`   |
| `DELETE` | `/filmes/{id}`              | Remove o filme (204)                   | `@CacheEvict` — remove `filmes::{id}`   |

Filme inexistente → **HTTP 404**.

Dados iniciais (criados por [`banco/init.sql`](banco/init.sql)):

| id | título                | diretor            | ano  | nota |
|----|-----------------------|--------------------|------|------|
| 1  | Cidade de Deus        | Fernando Meirelles | 2002 | 8.6  |
| 2  | Central do Brasil     | Walter Salles      | 1998 | 8.0  |
| 3  | O Auto da Compadecida | Guel Arraes        | 2000 | 8.6  |
| 4  | Ainda Estou Aqui      | Walter Salles      | 2024 | 8.2  |
| 5  | Carandiru             | Hector Babenco     | 2003 | 7.6  |

## 6. Estrutura do projeto

```
atividade-redis/
├── docker-compose.yml        os três contêineres (postgres, redis, app)
├── Dockerfile                build em dois estágios (Maven → JRE)
├── pom.xml                   dependências (web, data-jpa, postgresql, data-redis, cache)
├── banco/
│   └── init.sql              cria a tabela e insere os 5 filmes
└── src/main/
    ├── resources/application.properties
    └── java/br/pucgoias/ads/filmes/
        ├── CatalogoApplication.java           ponto de entrada
        ├── Filme.java                         entidade JPA
        ├── FilmeResposta.java                 record devolvido pela API e gravado no Redis (JSON)
        ├── FilmeRepository.java               acesso ao PostgreSQL
        ├── FilmeNaoEncontradoException.java   → HTTP 404
        ├── FilmeService.java                  regra de negócio + anotações de cache
        ├── FilmeController.java               endpoints REST + contador de acessos
        ├── CacheConfig.java                   TTL, serialização JSON, nome do cache
        └── CacheResilienteConfig.java         tolerância a falhas do Redis (Etapa 10)
```

## 7. Como o cache funciona no código

**`CacheConfig`** — habilita o cache (`@EnableCaching`) e configura o cache `filmes`:
cada entrada expira em **5 minutos** (`entryTtl`), valores nulos não são guardados
(`disableCachingNullValues`) e o valor é gravado como **JSON** legível no `redis-cli`
(`Jackson2JsonRedisSerializer`).

**`FilmeService`** — as anotações que implementam o padrão *cache-aside*:

```java
@Cacheable(cacheNames = "filmes", key = "#id")   // leitura: procura no cache antes de executar
public FilmeResposta buscar(Long id) { ... }

@CachePut(cacheNames = "filmes", key = "#id")    // escrita: atualiza a cópia com o valor novo
public FilmeResposta alterarNota(Long id, BigDecimal novaNota) { ... }

@CacheEvict(cacheNames = "filmes", key = "#id")  // exclusão: remove a cópia
public void remover(Long id) { ... }
```

A chave no Redis é o nome do cache + `::` + o id, por exemplo `filmes::1`.

O método `buscar` contém um `Thread.sleep(1000)` (`simularConsultaCustosa`) que representa uma
consulta pesada de um sistema real. É ele que torna o ganho do cache visível: **falta ≈ 1 s,
acerto ≈ poucos ms**.

**`CacheResilienteConfig`** — implementa `CachingConfigurer` com um `CacheErrorHandler` que apenas
registra o aviso quando o Redis falha. Assim o Spring trata a falha como uma *falta* e segue para
o PostgreSQL: o cache é só um acelerador, nunca um ponto de falha.

## 8. Etapas do estudo dirigido

O roteiro original tem 10 etapas. Este repositório contém o projeto no **estado final**. Abaixo,
o que cada etapa pede e o que foi **verificado na execução real** (Docker, em 26/10/2026).

| Etapa | Conteúdo | Resultado observado |
|-------|----------|---------------------|
| 1 | Estrutura do projeto e `pom.xml` | Projeto criado conforme o roteiro. |
| 2 | PostgreSQL e Redis em contêineres | Contêineres `healthy`; `SELECT` retornou os 5 filmes; `redis-cli PING` → `PONG`. |
| 3 | Aplicação sem cache | Imagem compilada; `GET /filmes/1` devolveu o JSON esperado. |
| 4 | Medição do problema (sem cache) | Reproduzível desativando o `@Cacheable` (ver [seção 10](#10-reproduzindo-os-experimentos)); sem cache, toda consulta custa ≈ 1 s e gera SQL. |
| 5 | Redis pela linha de comando | Comandos `GET`, `TTL`, `EXPIRE`, `DEL`, `KEYS` e `EXISTS` usados nos experimentos das etapas seguintes (o `SET … EX` manual desta etapa é o exercício de prática do aluno). |
| 6 | Inclusão do cache | 1ª consulta (falta): **≈ 1,4 s**, com SQL no log. 2ª e 3ª (acertos): **26 ms e 6 ms**, sem SQL. Chave `filmes::1` criada com JSON e TTL de ≈ 300 s. |
| 7 | Dado desatualizado | Após `UPDATE` direto no banco (nota 1.0), a API continuou devolvendo **8.6**. Depois de `DEL filmes::1`, devolveu **1.0**. |
| 8 | Invalidação | `PUT` alterou a nota para 9.5; `EXISTS filmes::1` logo após o `PUT` → `0`; a consulta seguinte devolveu **9.5** e a chave voltou a existir. *(Com `@CacheEvict`; ver a atividade 3 abaixo.)* |
| 9 | Expiração e reinício | Após o TTL vencer, a consulta voltou a levar ≈ 1 s (nova falta). Após `docker compose restart app`, o filme 3 foi atendido em **≈ 0,4 s sem SQL** — o cache sobrevive ao reinício da aplicação. |
| 10 | Indisponibilidade do Redis | Com o Redis parado, a API **continuou respondendo HTTP 200** (≈ 5 s, pelos timeouts de leitura e gravação do cache), com avisos `Cache indisponível…` no log. Religado o Redis, voltou a ≈ 5 ms. |

### Conceitos observados

| Conceito | Onde foi observado | Recurso |
|----------|--------------------|---------|
| Falta e acerto | Etapa 6 | `@Cacheable` |
| Chave e valor | Etapa 6: `filmes::1` contendo JSON | `cacheNames`, `key`, serializador JSON |
| Dado desatualizado | Etapa 7: alteração direta no banco | — |
| Invalidação | Etapa 8: o `PUT` remove a cópia | `@CacheEvict` |
| Prazo de validade | Etapas 5 e 9: `TTL` e `EXPIRE` | `entryTtl` |
| Cache externo à aplicação | Etapa 9: cópia sobrevive ao reinício | Contêiner próprio do Redis |
| Degradação controlada | Etapa 10: a aplicação funciona sem o Redis | `CacheErrorHandler` |

## 9. Atividades complementares

### 9.1 Consulta de filme inexistente

`GET /filmes/99` duas vezes → **HTTP 404 nas duas**, e `KEYS` no Redis **não mostra** `filmes::99`.

*Explicação:* o método lança `FilmeNaoEncontradoException`. Com a exceção, o método não retorna
valor algum, então não há o que gravar. Além disso, `disableCachingNullValues` impediria o
armazenamento de um `null`. Consequência: cada consulta a um id inexistente vai ao banco.

### 9.2 Exclusão

Consultou-se o filme 5 (criando `filmes::5`), executou-se `DELETE /filmes/5` → **HTTP 204**.
`EXISTS filmes::5` → `0` (chave removida pelo `@CacheEvict`) e a nova consulta devolveu **HTTP 404**.

### 9.3 Atualização da cópia (`@CachePut`)

Em `alterarNota`, o `@CacheEvict` foi substituído por `@CachePut(cacheNames = "filmes", key = "#id")`.

Resultado: após `PUT /filmes/1/nota?valor=7.7`, `EXISTS filmes::1` → **`1`**, o `GET filmes::1`
já continha **nota 7.7** e o TTL foi renovado para 300 s. A consulta seguinte levou **≈ 25 ms e não
gerou SQL**.

| | `@CacheEvict` (Etapa 8) | `@CachePut` (esta atividade) |
|---|---|---|
| O que faz com a cópia | Remove | Substitui pelo valor novo |
| Próxima leitura após a escrita | Falta (≈ 1 s, vai ao banco) | Acerto (poucos ms) |
| Risco | Praticamente nenhum | O que o método devolve vira o cache; se for diferente do que a leitura devolveria, o cache fica inconsistente |
| Quando usar | Padrão seguro; dado raramente relido logo após a escrita | Dado relido imediatamente após ser alterado |

> O código do repositório está com `@CachePut`. Para voltar ao comportamento da Etapa 8, troque a
> anotação em `alterarNota` por `@CacheEvict(cacheNames = "filmes", key = "#id")` (há um comentário
> no próprio código) e reconstrua a imagem.

### 9.4 Contador de acessos

O `FilmeController` recebe um `StringRedisTemplate` e, a cada `GET /filmes/{id}`, executa
`opsForValue().increment("filme:" + id + ":acessos")`.

Resultado: quatro consultas ao filme 2 → `GET filme:2:acessos` = **4**, mas o PostgreSQL foi
consultado **apenas 1 vez**.

*Por que o incremento fica no controlador:* em um acerto, o Spring devolve a cópia e **o método
anotado com `@Cacheable` nem chega a ser executado**. Um contador colocado dentro dele só contaria
as faltas, ignorando exatamente os acessos mais frequentes. O controlador é executado em todas as
requisições, haja acerto ou falta.

O incremento está dentro de um `try/catch`: se o Redis estiver fora do ar, o contador é ignorado
(com aviso no log) e a API continua respondendo, preservando a tolerância da Etapa 10.

```bash
docker exec -it catalogo-redis redis-cli GET filme:1:acessos
```

## 10. Reproduzindo os experimentos

Os comandos abaixo usam `curl` (no PowerShell, `curl.exe`).

**Medir falta × acerto (Etapa 6)**

```bash
docker exec catalogo-redis redis-cli FLUSHALL      # começa com o cache vazio
curl -s -w "\nTempo: %{time_total} s\n" http://localhost:8080/filmes/1   # falta
curl -s -w "\nTempo: %{time_total} s\n" http://localhost:8080/filmes/1   # acerto
docker exec catalogo-redis redis-cli GET "filmes::1"
docker exec catalogo-redis redis-cli TTL "filmes::1"
```

**Medir o problema sem cache (Etapa 4):** comente `@Cacheable` em `FilmeService.buscar`, reconstrua
(`docker compose up -d --build app`) e repita as consultas: todas levam ≈ 1 s e geram SQL.

**Dado desatualizado (Etapa 7)**

```bash
curl http://localhost:8080/filmes/1
docker exec catalogo-postgres psql -U catalogo -d catalogo -c "UPDATE filme SET nota = 1.0 WHERE id = 1;"
curl http://localhost:8080/filmes/1                # ainda mostra a nota antiga
docker exec catalogo-redis redis-cli DEL "filmes::1"
curl http://localhost:8080/filmes/1                # agora mostra 1.0
```

**Expiração (Etapa 9)**

```bash
curl http://localhost:8080/filmes/2
docker exec catalogo-redis redis-cli EXPIRE "filmes::2" 15
# aguarde 15 s
curl -s -w "\nTempo: %{time_total} s\n" http://localhost:8080/filmes/2   # ≈ 1 s de novo
```

**Reinício da aplicação (Etapa 9)**

```bash
curl http://localhost:8080/filmes/3
docker compose restart app                          # aguarde alguns segundos
curl -s -w "\nTempo: %{time_total} s\n" http://localhost:8080/filmes/3   # poucos ms, sem SQL
```

**Redis fora do ar (Etapa 10)**

```bash
docker compose stop redis
curl -s -w "\nTempo: %{time_total} s\n" http://localhost:8080/filmes/1   # 200, mais lento
docker compose logs app --tail 20                   # avisos "Cache indisponível…"
docker compose start redis
```

> Observação: ao ser parado e religado, o Redis grava e recarrega seus dados, então uma cópia
> existente antes da parada pode continuar disponível. Para ver uma falta, consulte outro filme ou
> use `FLUSHALL`.

## 11. Problemas frequentes

| Sintoma | Causa provável | Correção |
|---------|----------------|----------|
| `curl` exibe erro de parâmetro no PowerShell | `curl` é apelido de outro comando | Usar `curl.exe` |
| Porta 8080 em uso | Outro programa ocupa a porta | Trocar para `"8081:8080"` no `docker-compose.yml` |
| Alteração no código sem efeito | Imagem não reconstruída | `docker compose up -d --build app` |
| `relation "filme" does not exist` | O volume foi criado antes do `init.sql` | `docker compose down -v` e subir de novo |
| `catalogo-app` encerra logo após iniciar | Erro de compilação ou configuração | Ver `docker compose logs app` |
| `Connection refused` logo após o `up` | Aplicação ainda iniciando | Aguardar alguns segundos |
| Todas as consultas levam 1 s | Falta `@EnableCaching`/`@Cacheable`, ou imagem não reconstruída | Conferir `CacheConfig` e `FilmeService`; reconstruir |

## 12. Encerrando o ambiente

```bash
docker compose down        # remove os contêineres; os dados do PostgreSQL são mantidos
docker compose down -v     # idem, removendo também o volume (o init.sql roda de novo)
```
