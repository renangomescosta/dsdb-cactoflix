# Especificação Técnica — Serviço de Recomendação de Filmes (SVD)

## 1. Arquivos necessários

| Arquivo | Descrição |
|---|---|
| `svd_model.pkl` | Modelo SVD treinado (scikit-surprise), inclui matrizes de fatores latentes de itens (`qi`), vieses de itens (`bi`) e a média global (`global_mean`) |
| `movies.csv` | Metadados dos filmes (`movie_id`, `title`, `genres`) — necessário para exibir nomes na resposta da API |

## 2. Instalação

```bash
pip install scikit-surprise numpy pandas
```

## 3. Carregando o modelo

```python
from surprise import dump

_, algo = dump.load('svd_model.pkl')
trainset = algo.trainset  # já vem junto no objeto algo, não precisa carregar separado

GLOBAL_MEAN = trainset.global_mean
```

Carregar isso **uma única vez no startup da aplicação** NUNCA a cada request.

## 4. Dois cenários de uso

### 4.1 Usuário conhecido (já existia no treino)

Uso direto, sem pré-processamento:

```python
prediction = algo.predict(uid=user_id, iid=movie_id)
nota_estimada = prediction.est
```

### 4.2 Usuário novo (cold-start), ou seja, que vai passar pelas avaliações iniciais no começo da sessão

**Contexto:** o usuário acabou de avaliar 10 filmes (estrelas de 1 a 5) no onboarding, e ainda não existe no modelo treinado.

**Problema:** o `algo.predict()` para um `uid` desconhecido ignora as notas recebidas e retorna a média global, não é o que queremos.

**Solução: fold-in.** Calculamos o vetor latente do novo usuário em tempo real, usando os fatores dos itens já aprendidos pelo modelo (que ficam fixos) e as 10 notas recebidas. É uma regressão ridge de baixíssimo custo computacional, então não deve afetar com múltiplas requests simultaneas para os sistemas.

Fórmula do fold-in (equivalente a um passo de ALS do lado do usuário):

```
pu = (Qiᵀ · Qi + λI)⁻¹ · Qiᵀ · (r - μ - bi)
```

Onde:
- `Qi`: matriz com os vetores latentes dos 10 filmes avaliados
- `r`: vetor com as notas dadas pelo usuário
- `bi`: vieses dos 10 filmes avaliados
- `μ`: média global do dataset de treino
- `λ`: termo de regularização (usar 0.02, mesmo valor padrão do treino)

## 5. Código completo

```python
import numpy as np

LAMBDA = 0.02

def fold_in_new_user(rated_items):
    """
    rated_items: lista de tuplas (movie_id, rating), ex: [(1, 5), (32, 3), ...]
    Retorna: vetor latente (pu) do novo usuário, ou None se nenhum filme for reconhecido
    """
    qi_rows, r_adjusted = [], []

    for movie_id, rating in rated_items:
        try:
            inner_iid = trainset.to_inner_iid(movie_id)
        except ValueError:
            continue  # filme não existe no modelo treinado (ignorar)

        qi_rows.append(algo.qi[inner_iid])
        bi = algo.bi[inner_iid]
        r_adjusted.append(rating - GLOBAL_MEAN - bi)

    if not qi_rows:
        return None  # nenhum filme reconhecido -> fallback necessário

    Qi = np.array(qi_rows)
    r = np.array(r_adjusted)
    n_factors = Qi.shape[1]

    A = Qi.T @ Qi + LAMBDA * np.eye(n_factors)
    b = Qi.T @ r
    pu = np.linalg.solve(A, b)
    return pu


def recommend_top_k(rated_items, k=10):
    """
    rated_items: lista de tuplas (movie_id, rating), as 10 avaliações do onboarding
    Retorna: lista de até k tuplas (movie_id, score_previsto), ordenada da maior para menor
    """
    pu = fold_in_new_user(rated_items)

    if pu is None:
        return []  # usar fallback de popularidade (ver seção 7)

    rated_ids = {mid for mid, _ in rated_items}
    scores = []

    for inner_iid in range(trainset.n_items):
        raw_id = trainset.to_raw_iid(inner_iid)
        if raw_id in rated_ids:
            continue  # não recomendar filme já avaliado

        score = GLOBAL_MEAN + algo.bi[inner_iid] + np.dot(algo.qi[inner_iid], pu)
        scores.append((raw_id, score))

    scores.sort(key=lambda x: x[1], reverse=True)
    return scores[:k]
```

## 6. Fluxo ponta a ponta (onboarding → recomendação)

1. Frontend exibe 10 filmes com campo de estrelas (1-5), usuário avalia (pode pular algum).
2. Frontend envia ao backend: `{"ratings": [{"movie_id": 1, "rating": 5}, {"movie_id": 32, "rating": 3}, ...]}`
3. Backend chama `recommend_top_k(ratings, k=10)`.
4. Backend cruza os `movie_id` retornados com `movies.csv` para obter título/gênero.
5. Backend responde ao frontend com a lista final.

### Endpoint sugerido (FastAPI)

```python
POST /onboarding/recommend
Body:  { "ratings": [ { "movie_id": int, "rating": int (1-5) }, ... ] }
Resp:  { "recommendations": [ { "movie_id": int, "title": str, "score": float }, ... ] }
```

## 7. Regras de fallback (importante)

- **Menos de 3 avaliações válidas** (ex: usuário pulou quase tudo, ou os filmes não existem mais no modelo): não usar fold-in, retornar recomendações por **popularidade** (ex: filmes com maior número de avaliações e média alta no `ratings.csv`).
- **Filme não reconhecido** (`ValueError` no `to_inner_iid`): apenas ignorar aquela avaliação silenciosamente, não quebrar a chamada.
- **Modelo desatualizado**: se o catálogo de filmes mudar bastante (filmes novos, sem histórico de rating), eles nunca aparecerão nas recomendações do SVD, isso é uma limitação conhecida de fatoração de matriz e não é resolvido pelo fold-in. Vale considerar re-treinar o modelo periodicamente (ex: semanal/mensal) conforme o catálogo cresce.

## 8. Notas de performance

- `fold_in_new_user`: O(n_avaliações × n_fatores²), irrelevante em termos de latência (milissegundos).
- `recommend_top_k`: percorre todos os filmes (~3.900 no MovieLens 1M) fazendo um produto escalar, trivial computacionalmente, mas se o catálogo real for muito maior (dezenas de milhares+), vale otimizar com operações vetorizadas via `numpy` (matriz `algo.qi` inteira × `pu` de uma vez, em vez de loop) ou usar uma lib de busca por similaridade (ex: `faiss`) para approximate top-k.

## 9. Escolha dos 10 filmes do onboarding

Os 10 filmes exibidos no onboarding para novos usuários foram selecionados a partir do modelo SVD treinado com a biblioteca Surprise (n_factors=50). Os vetores de fatores latentes de cada item (algo.qi) foram agrupados em 10 clusters via k-means, garantindo cobertura de diferentes regiões do espaço latente aprendido pelo modelo. Dentro de cada cluster, foi selecionado o filme com maior número de avaliações no conjunto de treino, priorizando itens que o usuário provavelmente já assistiu e cujos fatores latentes foram estimados com maior confiança estatística. Essa abordagem evita concentrar as escolhas em filmes de nicho ou muito similares entre si, produzindo um conjunto diversificado e adequado para estimar o vetor latente inicial do usuário (p_u) via fold-in (regressão ridge).

   movie_id                                      title                           genres  cluster  n_ratings
0      2396                 Shakespeare in Love (1998)                   Comedy|Romance        0       1909
1      3623               Mission: Impossible 2 (2000)                  Action|Thriller        1       1056
2      3793                               X-Men (2000)                    Action|Sci-Fi        2       1233
3      1193     One Flew Over the Cuckoo's Nest (1975)                            Drama        3       1361
4      2657      Rocky Horror Picture Show, The (1975)     Comedy|Horror|Musical|Sci-Fi        4        976
5       260  Star Wars: Episode IV - A New Hope (1977)  Action|Adventure|Fantasy|Sci-Fi        5       2401
6      2858                     American Beauty (1999)                     Comedy|Drama        6       2740
7       589          Terminator 2: Judgment Day (1991)           Action|Sci-Fi|Thriller        7       2128
8      1097          E.T. the Extra-Terrestrial (1982)  Children's|Drama|Fantasy|Sci-Fi        8       1790
9      3755                  Perfect Storm, The (2000)        Action|Adventure|Thriller        9        819
