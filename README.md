# Cactoflix — Back-end

Back-end de um site de filmes, construído como um **monólito** em Java com Spring Boot.
Expõe uma **API REST**: outras aplicações fazem requisições HTTP e recebem os dados em JSON.
Os dados estão **mocados** (fixos no código) — sem banco de dados por enquanto.

- **Java:** 21
- **Build:** Maven
- **Stack:** Spring Web, Spring Boot DevTools, Validation
- **Porta:** `8080`

---

## Como rodar

Pela IDE, execute a classe `CactoflixApplication` (o ponto de entrada, com o método `main`).

Pelo terminal, na raiz do módulo `cactoflix`:

```bash
./mvnw spring-boot:run
```

A aplicação sobe em `http://localhost:8080`.

> **Dica:** ao criar ou alterar um controller, reinicie a aplicação se a rota nova
> não responder. Um `404 "No static resource"` normalmente significa que o Spring
> ainda não registrou o endpoint (app não recarregou).

---

## Estrutura de pastas

O código vive no pacote raiz `com.dsdb.cactoflix`, dividido por camadas. Cada
requisição percorre as camadas na ordem: **controller → service → repository → model**.
Cada camada só conhece a de baixo.

```
com.dsdb.cactoflix/
├── CactoflixApplication      ponto de entrada (liga o Spring)
├── controller/               recebe as requisições HTTP (a "porta de entrada")
├── service/                  lógica de negócio (filtros, cruzamentos, regras)
├── repository/               fornece os dados (hoje mocados; amanhã, banco)
└── model/                    as entidades do domínio (Movie, Rating, User)
```

### `controller`
As classes que expõem os **endpoints REST**. Traduzem requisições HTTP em chamadas ao
service e devolvem a resposta em JSON. São finas de propósito: recebem e encaminham.
Contém `MovieController`, `RatingController`, `UserController`.

### `service`
A **lógica de negócio** do Cactoflix — filtrar filmes, buscar por critério, publicar
avaliações, cruzar notas. Fica isolada tanto do HTTP (controller) quanto da origem dos
dados (repository), o que a torna fácil de testar. Contém `MovieService`,
`RatingService`, `UserService`.

### `repository`
As classes que **fornecem os dados**. Hoje devolvem listas mocadas (fixas no código).
Quando plugarmos um banco, a mudança acontece só aqui — o resto da aplicação não muda.
Contém `MovieRepository`, `RatingRepository`, `UserRepository`.

### `model`
As **entidades** do domínio: `Movie`, `Rating`, `User`. São objetos Java simples, com os
campos do negócio e nada de framework. É o núcleo, reutilizado por todas as outras camadas.

---

## Rotas da API

### Movies — `/movies`

#### `GET /movies` — lista filmes (com filtros opcionais)

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `name`  | texto | não | filtra por nome do filme |
| `genre` | lista | não | filtra por gênero (pode repetir) |

```bash
# todos os filmes
curl -i "http://localhost:8080/movies"

# filtrar por nome
curl -i "http://localhost:8080/movies?name=Toy%20Story%201"

# filtrar por um gênero
curl -i "http://localhost:8080/movies?genre=Teste"

# filtrar por vários gêneros (repete o parâmetro)
curl -i "http://localhost:8080/movies?genre=Ação&genre=Comédia"

# combinar nome + gênero
curl -i "http://localhost:8080/movies?name=Avatar&genre=Teste"
```

Resposta (exemplo):

```json
[
  {
    "id": 1,
    "name": "Toy Story 1",
    "genres": ["Romance","Terror","Ação"],
    "numberOfNotes": 100,
    "note": 6.0
  }
]
```

> O campo `note` é a média calculada (`sumOfNotes / numberOfNotes`). Ele aparece no JSON
> por causa do método `getNote()`, mesmo não sendo um atributo da classe.

>OBS:. NOTAS e NUMERO DE NOTAS não implementado (TODO)
#### `GET /movies/{id}` — busca por id

```bash
curl -i "http://localhost:8080/movies/1"
curl -i "http://localhost:8080/movies/3"
```

---

### Ratings — `/ratings`

#### `GET /ratings` — lista todas as avaliações

```bash
curl -i "http://localhost:8080/ratings"
```

Resposta (exemplo):

```json
[
  { "userId": 1, "movieId": 1, "rating": 5 },
  { "userId": 3, "movieId": 2, "rating": null }
]
```

> `rating` pode ser `null` — representa uma avaliação sem nota (não computa na média).

#### `POST /ratings` — publica ou atualiza uma avaliação

Envia um `Rating` no corpo (JSON). Se já existir avaliação daquele usuário para aquele
filme, ela é atualizada; senão, é criada.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `userId`  | número | id do usuário |
| `movieId` | número | id do filme |
| `rating`  | número | nota (pode ser `null`) |

```bash
curl -i -X POST "http://localhost:8080/ratings" \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "movieId": 3, "rating": 5}'
```

Depois de publicar, confira na listagem:

```bash
curl -i "http://localhost:8080/ratings"
```

---

### Users — `/users`

#### `GET /users?email=...` — busca usuário por email

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `email` | texto | sim | email do usuário |

```bash
# usuário existente → 200 + dados
curl -i "http://localhost:8080/users?email=renan@gmail.com"

# usuário inexistente → 404
curl -i "http://localhost:8080/users?email=naoexiste@x.com"
```

Resposta (exemplo, existente):

```json
{
  "id": 1,
  "name": "Renan",
  "email": "renan@gmail.com"
}
```

---

## Resumo das rotas

| Método | Rota | O que faz |
|--------|------|-----------|
| GET  | `/movies` | lista filmes (filtros: `name`, `genre`) |
| GET  | `/movies/{id}` | busca filme por id |
| GET  | `/ratings` | lista avaliações |
| POST | `/ratings` | publica/atualiza avaliação |
| GET  | `/users?email=` | busca usuário por email (404 se não achar) |

---

## Dados mocados disponíveis

**Usuários:** Renan, Derick, Becky, Dhener, Alisson (ids 1 a 5, emails `nome@gmail.com`).

**Filmes:** Toy Story 1, Vingadores, Avatar, Titanic, Tá Chovendo Hambúrguer (ids 1 a 5).

**Avaliações:** alguns ratings de exemplo cruzando usuários e filmes (um deles com nota `null`).

---

## Observações

- **Sem banco de dados:** os dados são fixos e voltam ao original a cada reinício da aplicação.
  Os pontos marcados com `//TODO` nos repositories indicam onde a integração com banco entraria.
- **Formato dos parâmetros na URL:** o `?` inicia a query string; pares `chave=valor` se separam
  com `&`; espaços viram `%20`; listas repetem o mesmo parâmetro (`genre=A&genre=B`).
- **Testando POST:** o navegador só faz GET na barra de endereço. Para `POST /ratings`, use
  `curl` (exemplos acima) ou uma ferramenta como o Postman.