# Fluxo de branches

O projeto segue um Git Flow simplificado:

| Branch | Papel | Deploy |
|---|---|---|
| `develop` | integração contínua do dia a dia (branch padrão) | CI: build + testes |
| `release` | estabilização da próxima versão | CI + imagem Docker `:rc` no GHCR |
| `master` | produção, sempre estável | CI + imagem Docker `:latest` e release no GitHub |

1. Crie a branch `feature/<nome>` a partir de `develop`.
2. Abra o PR para `develop`. O CI precisa passar.
3. Para lançar, abra o PR `develop` → `release` e, depois de validado, `release` → `master`.
4. Correções urgentes saem de `master` em `hotfix/<nome>` e voltam para `master` e `develop`.

Commits seguem [Conventional Commits](https://www.conventionalcommits.org/pt-br/) (`feat:`, `fix:`, `docs:`, `test:`, `chore:`).
