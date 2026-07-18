# Cactoflix — Back-end

Plataforma de recomendação de filmes (um "Netflix simplificado"), com catálogo de filmes, avaliações de usuários e um motor de recomendação por fatoração de matriz (SVD).
Como projeto de Bancos de Dados Distribuídos, o sistema é distribuído em duas camadas: a persistência usa um MongoDB em replica set de até 5 nós, e a API roda em múltiplas réplicas atrás de um load balancer com failover.

- **Java:** 21
- **Build:** Maven (`./mvnw`)
- **Framework:** Spring Boot 4.1 (Spring Web, Spring Data MongoDB, Bean Validation)
- **Banco:** MongoDB 7+ (replica set, 5 nós)
- **Empacotamento:** Docker / Docker Compose
- **Porta da API:** `8080`

---

## 1. O projeto

### 1.1. Modelo de negócio

O Cactoflix tem três entidades de domínio:

- **Movie** — um filme (`id`, `name`, `genres`). O catálogo vem populado com um dataset
  no estilo MovieLens: ~3.900 filmes.
- **User** — um usuário cadastrado (`id`, `name`, `email`).
- **Rating** — a nota (`1`–`5`, ou `null` para "avaliado sem nota") que um usuário deu a
  um filme. O catálogo já vem com ~500 avaliações de exemplo cruzando usuários e filmes.

Em cima disso, o **motor de recomendação** (`recommendation/SvdRecommender`) usa um
modelo SVD **pré-treinado** (`recommendation/svd_model.json`, 3.675 filmes, 50 fatores
latentes) para prever, a partir das notas de um usuário, quais outros filmes ele
provavelmente gostaria — é o clássico *collaborative filtering*. Duas situações:

- **Usuário já cadastrado**, com notas salvas no banco → `GET /recommendations`.
- **Onboarding** (usuário novo, ainda sem notas persistidas, manda ~10 notas iniciais no
  corpo da requisição) → `POST /recommendations`.

Se o usuário tem **menos de 3 avaliações válidas**, o modelo SVD não tem informação
suficiente pra um *fold-in* confiável — nesse caso o serviço cai automaticamente num
**fallback de popularidade**: ranqueia os filmes por uma média bayesiana (amortecida pela
média global do sistema), excluindo o que o usuário já avaliou.

### 1.2. Estrutura / arquitetura

O código é um **único artefato Java** (`cactoflix-0.0.1-SNAPSHOT.jar`), mas ele se
comporta de dois jeitos diferentes dependendo do **profile** do Spring com que é
iniciado (`SPRING_PROFILES_ACTIVE`):

| Profile | O que ativa | Papel |
|---|---|---|
| `app` | `MovieController`, `RatingController`, `UserController`, `RecommendationController` e seus services/repositories | uma **réplica da API de negócio**, conectada ao banco |
| `lb`  | só `LoadBalancerController` | um **load balancer**, sem tocar no banco |

Ou seja: o mesmo `.jar` roda como "app" em três servidores diferentes (`app1`, `app2`,
`app3`) e como "load balancer" num quarto servidor — o load balancer é quem fica exposto
pro mundo, e distribui as requisições entre as três réplicas de app (mais detalhes na
seção 4).

Camadas dentro do profile `app` (cada uma só conhece a de baixo):

```
com.dsdb.cactoflix/
├── CactoflixApplication          ponto de entrada
├── controller/                   recebe HTTP, valida entrada, devolve JSON
├── service/                      regra de negócio (filtros, fallback, publish/update)
├── repository/                   acesso ao MongoDB via MongoTemplate
├── model/                        Movie, Rating, User
├── recommendation/               motor SVD + RecommendedMovie (não conhece HTTP nem banco)
└── infrastructure/loadBalancer/  LoadBalancerController (profile "lb")
```

O banco (perfil `app`) não é um MongoDB único: é um **replica set com 5 membros**
(`banco1`...`banco5`), pensado pra rodar **um nó por servidor físico/VM** — detalhes na
seção 5.

---

## 2. Endpoints da API

Todas as respostas são JSON. Quando a API é acessada **através do load balancer** (o
caminho normal, porta `8080` do servidor do LB), **toda requisição precisa do header**
`X-Client-Email` — é ele que o load balancer usa pra decidir pra qual réplica de app
encaminhar (veja seção 4). Sem esse header, o load balancer responde `400` antes mesmo de
tentar encaminhar. Se você estiver testando uma réplica de app **diretamente** (sem passar
pelo LB), esse header não é necessário.

### Resumo

| Método | Rota | Descrição |
|---|---|---|
| `GET`  | `/movies` | lista filmes, com filtros opcionais `name` e `genre` |
| `GET`  | `/movies/{id}` | busca um filme por id |
| `GET`  | `/ratings` | lista todas as avaliações |
| `POST` | `/ratings` | publica uma avaliação nova ou atualiza uma existente |
| `GET`  | `/users?email=` | busca um usuário por email (`404` se não achar) |
| `GET`  | `/recommendations?userId=&k=` | recomendações para um usuário já cadastrado |
| `POST` | `/recommendations?k=` | recomendações de onboarding (notas enviadas no corpo) |

### `GET /movies`

| Parâmetro | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `name`  | texto | não | filtra por nome (case-insensitive, substring) |
| `genre` | lista | não | filtra por gênero; repete o parâmetro pra mais de um |

```bash
curl -H "X-Client-Email: renan@gmail.com" "http://localhost:8080/movies"
curl -H "X-Client-Email: renan@gmail.com" "http://localhost:8080/movies?name=Toy%20Story"
curl -H "X-Client-Email: renan@gmail.com" "http://localhost:8080/movies?genre=Comedy&genre=Romance"
```

Resposta:

```json
[
  { "id": 1, "name": "Toy Story (1995)", "genres": ["Animation", "Children's", "Comedy"] }
]
```

### `GET /movies/{id}`

```bash
curl -H "X-Client-Email: renan@gmail.com" "http://localhost:8080/movies/1"
```

### `GET /ratings`

```bash
curl -H "X-Client-Email: renan@gmail.com" "http://localhost:8080/ratings"
```

```json
[
  { "userId": 1, "movieId": 1193, "rating": 5 },
  { "userId": 3, "movieId": 661,  "rating": null }
]
```

`rating` pode ser `null` — representa uma avaliação sem nota (não entra na média).

### `POST /ratings`

Se já existir uma avaliação daquele `userId` para aquele `movieId`, ela é **atualizada**;
senão, é **criada**.

| Campo | Tipo | Descrição |
|---|---|---|
| `userId`  | número | id do usuário |
| `movieId` | número | id do filme |
| `rating`  | número (ou `null`) | nota de 1 a 5 |

```bash
curl -X POST "http://localhost:8080/ratings" \
  -H "X-Client-Email: renan@gmail.com" \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "movieId": 3, "rating": 5}'
```

### `GET /users?email=`

| Parâmetro | Tipo | Obrigatório |
|---|---|---|
| `email` | texto | sim |

```bash
curl -H "X-Client-Email: renan@gmail.com" "http://localhost:8080/users?email=renan@gmail.com"
# -> 200 + { "id": 1, "name": "Renan", "email": "renan@gmail.com" }

curl -H "X-Client-Email: renan@gmail.com" "http://localhost:8080/users?email=naoexiste@x.com"
# -> 404
```

### `GET /recommendations?userId=&k=`

Recomendações pra um usuário **já cadastrado** — as notas dele são lidas do banco.

| Parâmetro | Tipo | Obrigatório | Padrão |
|---|---|---|---|
| `userId` | número | sim | — |
| `k` | número | não | `10` |

```bash
curl -H "X-Client-Email: renan@gmail.com" "http://localhost:8080/recommendations?userId=1&k=5"
```

```json
[
  { "movieId": 2571, "name": "Matrix, The (1999)", "score": 4.62 },
  { "movieId": 1196, "name": "Star Wars: Episode V (1980)", "score": 4.51 }
]
```

### `POST /recommendations?k=`

Recomendações de **onboarding**: o cliente ainda não tem notas salvas, então manda as
notas iniciais (idealmente ≥ 3) direto no corpo — elas **não são persistidas**, só usadas
pra calcular a recomendação na hora.

```bash
curl -X POST "http://localhost:8080/recommendations?k=5" \
  -H "X-Client-Email: novo@exemplo.com" \
  -H "Content-Type: application/json" \
  -d '{
    "ratings": [
      { "userId": 0, "movieId": 1, "rating": 5 },
      { "userId": 0, "movieId": 1193, "rating": 4 },
      { "userId": 0, "movieId": 260, "rating": 5 }
    ]
  }'
```

> Se sobrarem menos de 3 notas válidas (nulas ou de filmes fora do modelo não contam), a
> resposta vem do fallback de popularidade em vez do SVD — funciona igual, só muda o
> critério por trás do ranking.

---

## 3. Como subir o ambiente

### Pré-requisitos

- Docker + Docker Compose instalados em cada máquina que vai rodar um serviço.
- O código-fonte (ou pelo menos este diretório `cactoflix/`) presente em cada máquina —
  cada serviço builda sua própria imagem localmente (`build: .`), não tem registry
  envolvido.

Existem **dois jeitos** de subir o projeto:

- **Modo rápido** (`docker-compose.yaml`): tudo — 5 bancos, 3 apps e o load balancer — na
  mesma máquina, mesma rede Docker. Bom pra testar localmente.
- **Modo distribuído** (`docker-compose.database.yaml`, `docker-compose.app.yaml`,
  `docker-compose.loadBalancer.yaml` + `docker-compose.mongo-setup.yaml`): cada serviço
  numa máquina/VM diferente, comunicando por IP real — é o cenário que a disciplina pede,
  e é o que a seção abaixo detalha passo a passo.

### 3.1. Modo rápido (um host só)

```bash
cd cactoflix
docker compose up -d --build
```

Isso sobe, na ordem certa (`depends_on`), os 5 bancos → o `mongo-setup` (inicializa o
replica set e popula o catálogo) → as 3 réplicas de app → o load balancer, todo mundo se
enxergando pelo nome do serviço Docker (`banco1`, `app1`, etc.). A API fica em
`http://localhost:8080`. Pra derrubar: `docker compose stop` (mantém os dados) ou
`docker compose down` (remove os containers, mantém os volumes).

### 3.2. Modo distribuído (um serviço por servidor)

Primeiro, em **cada** servidor, copie o `.env.example` pra `.env` e preencha com os
valores daquele servidor específico — o Docker Compose carrega o `.env` automaticamente
se ele estiver na mesma pasta do arquivo compose que você rodar.

```bash
cp .env.example .env
```

#### Passo 1 — subir os bancos (um servidor por nó, 5 vezes)

Em cada um dos 5 servidores de banco, edite o `.env` só com o nome daquele nó:

```env
BANCO_NAME=banco1   # no servidor 1
```
```env
BANCO_NAME=banco2   # no servidor 2
```
...e assim até `banco5`. Depois, em cada um:

```bash
docker compose -f docker-compose.database.yaml up -d
```

Isso sobe um único `mongod` (`--replSet cenAdm --bind_ip_all`), publicando a porta
`27017` no host. Repita nos 5 servidores antes de ir pro próximo passo — o replica set só
pode ser inicializado depois que os 5 `mongod` já estão de pé.

#### Passo 2 — inicializar o replica set e popular o catálogo (uma vez só)

Esse passo roda **uma única vez**, de qualquer máquina que tenha acesso de rede aos 5 IPs
dos bancos (pode ser um dos servidores de app, o do load balancer, ou até sua própria
máquina) — não precisa ser um dos servidores de banco.

No `.env` dessa máquina, preencha `BANCO1_IP`...`BANCO5_IP` com os IPs reais dos 5
servidores do passo 1:

```env
BANCO1_IP=192.168.0.101
BANCO2_IP=192.168.0.102
BANCO3_IP=192.168.0.103
BANCO4_IP=192.168.0.104
BANCO5_IP=192.168.0.105
```

```bash
docker compose -f docker-compose.mongo-setup.yaml up
```

Ele roda `rs.initiate()` (com `banco1` de prioridade mais alta — candidato preferencial a
primário), espera a eleição, e popula o banco (`db_dsid`) com o catálogo de filmes,
avaliações e usuários a partir de `Mongodb.js`. Confirme no log a mensagem final:
`Tudo pronto! O banco do Cactoflix está configurado e populado.`

#### Passo 3 — subir os apps (um servidor por réplica, 3 vezes)

Em cada um dos 3 servidores de app, preencha o `.env` com os mesmos `BANCO1_IP`...`BANCO5_IP`
do passo 2 (os apps falam com o cluster inteiro, não com um banco específico), e suba:

```bash
docker compose -f docker-compose.app.yaml up -d --build
```

Isso builda a imagem, sobe com `SPRING_PROFILES_ACTIVE=app`, monta a
`SPRING_MONGODB_URI` apontando pros 5 IPs com `replicaSet=cenAdm&readPreference=nearest`,
e publica a porta `8080` no host (precisa estar publicada porque o load balancer, num
servidor diferente, vai bater nela por IP). Repita nos 3 servidores. Confira o log de cada
um: deve aparecer `Started CactoflixApplication` e nenhum erro de conexão com o Mongo.

#### Passo 4 — subir o load balancer (um servidor, uma vez)

No servidor do load balancer, preencha o `.env` com os IPs dos 3 apps do passo 3:

```env
APP1_IP=192.168.0.111
APP2_IP=192.168.0.112
APP3_IP=192.168.0.113
```

```bash
docker compose -f docker-compose.loadBalancer.yaml up -d --build
```

Sobe com `SPRING_PROFILES_ACTIVE=lb`, monta `LOADBALANCER_BACKENDS` com os 3 endereços, e
publica `8080`. A partir daqui, `http://<ip-do-load-balancer>:8080` é o único ponto de
entrada da API — teste com qualquer endpoint da seção 2, sempre com o header
`X-Client-Email`.

### 3.3. Como parar

Em cada servidor, no diretório do compose file usado pra subir aquele serviço:

```bash
docker compose -f <arquivo>.yaml stop     # para, mantém dados/volumes
docker compose -f <arquivo>.yaml down     # remove os containers (mantém volumes nomeados)
```

---

## 4. Como funciona o load balancer

O `LoadBalancerController` (profile `lb`) é a única porta de entrada da API no modo
distribuído. Ele não faz round-robin simples — o roteamento é **por chave** (o email do
cliente) e tem **failover**:

1. Exige o header `X-Client-Email` em toda requisição (`400` se faltar ou vier em branco)
   — é essa chave que decide o roteamento.
2. Calcula um índice a partir do hash do email (`Math.floorMod(email.hashCode(), N)`,
   `N` = número de backends configurados). Isso é **determinístico**: o mesmo email
   sempre calcula o mesmo índice primário — não é aleatório nem round-robin, então o
   mesmo cliente tende a bater sempre na mesma réplica.
3. Antes de encaminhar, verifica se esse backend está de pé (`isBackendUp`: tenta abrir um
   socket TCP real no host:porta configurado, timeout de 1s). Se **não** estiver, tenta o
   próximo backend na sequência (índice + 1, +2, ...) até achar um saudável — isso é o
   **failover**: uma réplica caída não derruba o cliente, só desloca ele pra outra.
4. Se **todos** os backends estiverem fora do ar, responde `500` com
   `"Todos os servidores estão desligados!"` em vez de travar ou devolver erro genérico.
5. Encontrado um backend saudável, encaminha a requisição original (mesma rota, mesma
   query string) via `RestTemplate` e devolve a resposta do backend como se fosse dele.

Os backends vêm da property `loadbalancer.backends` (env var `LOADBALANCER_BACKENDS`),
uma string com URLs separadas por vírgula — é assim que o mesmo `.jar` funciona tanto no
modo rápido (`http://app1:8080,http://app2:8080,http://app3:8080`, resolvido pelo DNS
interno do Docker) quanto no modo distribuído (os IPs reais de cada servidor).

---

## 5. Como funciona o banco distribuído

O banco não é um MongoDB único: é um **replica set** (`cenAdm`) com **5 membros**
(`banco1`...`banco5`), cada um pensado pra rodar num servidor/VM diferente.

- **Réplicas com prioridades diferentes.** Cada `mongod` sobe com `--replSet cenAdm
  --bind_ip_all`, e o `rs.initiate()` (rodado uma única vez pelo `mongo-setup`) registra
  os 5 com prioridades decrescentes (`banco1`=4, `banco2`=3, `banco3`=2, `banco4`=2,
  `banco5`=1). Prioridade mais alta = mais chance de vencer a eleição e virar **primário**
  — é `banco1` o candidato preferencial, mas se ele cair, o cluster reelege automaticamente
  outro membro com base na prioridade de quem sobrou.
- **Tolerância a falha por quórum.** Com 5 membros, uma eleição só é bem-sucedida com
  maioria simples (3 de 5) votando. Isso significa que o cluster continua operando
  (conseguindo eleger/manter um primário) com até **2 nós fora do ar** — só perde a
  capacidade de eleger primário se restarem 2 ou menos.
- **Escritas vs. leituras.** Toda escrita (`insert`, `save`, `update`) vai sempre pro
  **primário** — é assim que o driver garante uma ordem de escrita consistente. Leituras
  já usam `readPreference=nearest` na connection string: o driver escolhe o membro
  (primário ou secundário) com **menor latência de rede** até o cliente, não
  necessariamente o primário — espalha a carga de leitura entre as réplicas, ao custo de
  poder ler um dado com replicação levemente atrasada em relação ao primário.
- **Setup automatizado, mas em duas fases.** `docker-compose.database.yaml` só sobe o
  `mongod` cru — ele nasce **sem saber** que faz parte de um replica set até alguém rodar
  `rs.initiate()`. É pra isso que existe o `docker-compose.mongo-setup.yaml`: roda uma vez
  (de fora dos 5 servidores de banco), inicializa o replica set com os 5 membros e os pesos
  de prioridade, espera a eleição do primário, e por fim popula o banco `db_dsid`
  (collections `movies`, `ratings`, `users`) a partir do `Mongodb.js`.
- **Uma única URI, cinco hosts.** Tanto os apps quanto o `mongo-setup` se conectam com uma
  connection string listando os 5 hosts (`mongodb://banco1:27017,banco2:27017,.../db_dsid
  ?replicaSet=cenAdm&readPreference=nearest`) — o driver do MongoDB descobre sozinho, a
  partir desses "seeds", quem é primário e quem são secundários, e se adapta
  automaticamente se uma eleição trocar o primário no meio do caminho.

---

## Estrutura de pastas

```
com.dsdb.cactoflix/
├── CactoflixApplication
├── controller/                    MovieController, RatingController, UserController, RecommendationController
├── service/                       MovieService, RatingService, UserService, RecommendationService
├── repository/                    MovieRepository, RatingRepository, UserRepository (MongoTemplate)
├── model/                         Movie, Rating, User
├── recommendation/                SvdRecommender, RecommendedMovie
└── infrastructure/loadBalancer/   LoadBalancerController
```

Testes unitários (Mockito + AssertJ, sem depender de um Mongo real) cobrem repositories,
services, o motor SVD (contra o modelo real) e o load balancer (roteamento, failover e o
caso "todos fora do ar"). Rodar com:

```bash
./mvnw test
```
