# RUNBOOK — Deploy distribuído do Cactoflix com Tailscale

Guia para subir o sistema em **9 máquinas em redes diferentes**, conectadas por uma "LAN virtual" via Tailscale.

Os arquivos `docker-compose.*.yaml` já leem tudo de `.env` — **não é preciso alterar nenhum compose**. Basta preencher o `.env` de cada máquina com os IPs da Tailscale.

---

## Visão geral

```
9 máquinas, redes diferentes → todas entram numa mesma LAN virtual via Tailscale (100.x.x.x)
Fluxo: Cliente → LB:8080 → App:8080 → Banco:27017 (replica set nos 5 bancos)
```

| Papel      | Qtd | Porta | Compose file                        | Precisa no `.env`          |
|------------|-----|-------|-------------------------------------|----------------------------|
| Banco (mongo) | 5 | 27017 | `docker-compose.database.yaml`      | `BANCO_NAME`               |
| mongo-setup   | 1x| —     | `docker-compose.mongo-setup.yaml`   | `BANCO1_IP`..`BANCO5_IP`   |
| App           | 3 | 8080  | `docker-compose.app.yaml`           | `BANCO1_IP`..`BANCO5_IP`   |
| Load Balancer | 1 | 8080  | `docker-compose.loadBalancer.yaml`  | `APP1_IP`..`APP3_IP`       |

---

## FASE 0 — Instalar Tailscale nas 9 máquinas

### Linux (cada máquina)
```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
# abre um link → faça login com a MESMA conta em todas as 9 máquinas
tailscale ip -4      # anota o IP 100.x.x.x desta máquina
```

### Windows (cada máquina)
1. Baixe e instale: https://tailscale.com/download/windows
2. Abra o Tailscale na bandeja → **Log in** → mesma conta das outras.
3. Pegue o IP no PowerShell:
   ```powershell
   tailscale ip -4
   ```

> Dica: no admin console (login.tailscale.com), **desative a expiração de chave** (Disable key expiry) de cada máquina, senão o acesso cai depois de uns meses.

---

## FASE 1 — Montar o mapa de IPs

Anote os 9 IPs da Tailscale e o papel de cada um. Exemplo (use os SEUS IPs reais):

| Papel  | Tailscale IP |
|--------|--------------|
| banco1 | 100.101.0.1  |
| banco2 | 100.101.0.2  |
| banco3 | 100.101.0.3  |
| banco4 | 100.101.0.4  |
| banco5 | 100.101.0.5  |
| app1   | 100.101.0.11 |
| app2   | 100.101.0.12 |
| app3   | 100.101.0.13 |
| lb     | 100.101.0.20 |

---

## FASE 2 — Subir os 5 BANCOS

Em **cada** máquina de banco, dentro da pasta `cactoflix/`:

**`.env`** (muda só o nome em cada uma: banco1…banco5)
```bash
BANCO_NAME=banco1
```

**Firewall — Linux** (confia em todo o tráfego da VPN)
```bash
sudo firewall-cmd --permanent --zone=trusted --add-interface=tailscale0
sudo firewall-cmd --reload
```

**Firewall — Windows** (PowerShell como Admin)
```powershell
New-NetFirewallRule -DisplayName "Mongo 27017" -Direction Inbound -Protocol TCP -LocalPort 27017 -Action Allow
```

**Subir** (igual nos dois SOs)
```bash
docker compose -f docker-compose.database.yaml up -d
```

Repita nas 5 máquinas trocando `BANCO_NAME`.

---

## FASE 3 — Configurar o Replica Set (roda UMA vez só)

Em **qualquer** máquina que enxergue os bancos (pode ser a do banco1):

**`.env`** — os 5 IPs da Tailscale
```bash
BANCO1_IP=100.101.0.1
BANCO2_IP=100.101.0.2
BANCO3_IP=100.101.0.3
BANCO4_IP=100.101.0.4
BANCO5_IP=100.101.0.5
```

**Rodar** (sem `-d`, pra ver o log até "Tudo pronto!")
```bash
docker compose -f docker-compose.mongo-setup.yaml up
```

Espere a mensagem `Tudo pronto! O banco do Cactoflix está configurado e populado.`
Isso grava os IPs da Tailscale como membros do replica set e popula o banco.
Esse container **não abre porta** (só conecta de saída).

---

## FASE 4 — Subir os 3 APPS

Em **cada** máquina de app, na pasta `cactoflix/`:

**`.env`** — os 5 IPs dos bancos
```bash
BANCO1_IP=100.101.0.1
BANCO2_IP=100.101.0.2
BANCO3_IP=100.101.0.3
BANCO4_IP=100.101.0.4
BANCO5_IP=100.101.0.5
```

**Firewall — Linux** (se ainda não confiou a interface nesta máquina)
```bash
sudo firewall-cmd --permanent --zone=trusted --add-interface=tailscale0
sudo firewall-cmd --reload
```

**Firewall — Windows** (PowerShell Admin)
```powershell
New-NetFirewallRule -DisplayName "App 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow
```

**Subir**
```bash
docker compose -f docker-compose.app.yaml up -d
```

Repita nas 3 máquinas (o `.env` é idêntico nas três).

---

## FASE 5 — Subir o LOAD BALANCER

Na máquina do LB:

**`.env`** — os 3 IPs dos apps
```bash
APP1_IP=100.101.0.11
APP2_IP=100.101.0.12
APP3_IP=100.101.0.13
```

**Firewall** — igual à Fase 4 (`tailscale0` trusted no Linux / regra 8080 no Windows).

**Subir**
```bash
docker compose -f docker-compose.loadBalancer.yaml up -d
```

---

## Testar

```bash
# de uma máquina de APP, testar um banco:
nc -zv 100.101.0.1 27017                        # Linux
Test-NetConnection 100.101.0.1 -Port 27017      # Windows PowerShell

# do LB, testar um app:
curl http://100.101.0.11:8080/actuator/health

# de QUALQUER máquina do tailnet, testar o sistema todo:
curl http://100.101.0.20:8080/...               # IP do LB
```

Ver logs se algo falhar:
```bash
docker compose -f docker-compose.app.yaml logs -f
```

---

## Pontos críticos

1. **Login único:** as 9 máquinas têm que estar na **mesma conta** Tailscale, senão não se enxergam.
2. **IP da Tailscale é estável** — é o que mantém o replica set válido. Não use os IPs locais (`192.168.x`) em lugar nenhum do `.env`.
3. **Segurança:** o mongo sobe **sem senha**, mas com a Tailscale ele só é alcançável **dentro do túnel** (não na internet aberta). **Não** faça port-forward de 27017 no roteador.
4. **Ordem importa:** bancos (Fase 2) → mongo-setup (Fase 3) → apps (Fase 4) → LB (Fase 5).
5. **Reboot:** `up -d` não religa sozinho. Para resiliência, adicione `restart: unless-stopped` nos serviços dos compose files.
