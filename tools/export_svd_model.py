"""
Script OFFLINE de exportação do modelo SVD (rodar 1x, e de novo apenas se retreinar)

Lê o svd_model.pkl (scikit-surprise) e exporta somente o que a inferência em Java
precisa: media global (mu), vieses dos itens (bi), fatores latentes dos itens (qi)
e o mapa inner_id -> movie_id. Os fatores de USUARIO (pu/bu) do treino NAO sao
exportados: os usuarios do Cactoflix nao existem no treino (cold-start), entao o
vetor do usuario e' sempre recalculado em runtime via fold-in

Uso:
    pip install numpy scikit-surprise   # surprise so' e' necessario se quiser dump.load oficial
    python export_svd_model.py <caminho/svd_model.pkl> <saida/svd_model.json>

Este script nao usa scikit-surprise: abre o pickle com stubs, o que evita instalar
a lib so' para ler atributos.
"""
import json, pickle, sys
import numpy as np


class _Stub:
    def __init__(self, *a, **k): pass
    def __setstate__(self, state):
        self.__dict__.update(state if isinstance(state, dict) else {"_state": state})


class _Unpickler(pickle.Unpickler):
    def find_class(self, module, name):
        if module.startswith("surprise"):
            return type(name, (_Stub,), {})
        return super().find_class(module, name)


def main(pkl_path, out_path):
    with open(pkl_path, "rb") as f:
        obj = _Unpickler(f).load()
    algo = obj["algo"] if isinstance(obj, dict) else obj
    ts = algo.trainset

    raw2inner = ts._raw2inner_id_items          # {movie_id -> inner_id}
    n_items = ts.n_items
    inner2raw = [0] * n_items
    for raw, inner in raw2inner.items():
        inner2raw[inner] = int(raw)

    qi = np.asarray(algo.qi, dtype=float)        # (n_items, n_factors)
    bi = np.asarray(algo.bi, dtype=float)        # (n_items,)
    mu = float(ts._global_mean)

    assert qi.shape[0] == n_items == bi.shape[0]

    payload = {
        "globalMean": mu,
        "nFactors": int(qi.shape[1]),
        "regularization": 0.02,                  # mesmo lambda do treino (reg_pu)
        "itemIds": inner2raw,                    # posicao k = movie_id do inner_id k
        "bi": [round(v, 7) for v in bi.tolist()],
        "qi": [[round(v, 7) for v in row] for row in qi.tolist()],
    }
    with open(out_path, "w") as f:
        json.dump(payload, f, separators=(",", ":"))
    print(f"ok: {n_items} itens, {qi.shape[1]} fatores, mu={mu:.6f} -> {out_path}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
