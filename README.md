# coreORM
ORM library in Java


## Folders
- api - public API
- core - internal package

## Get started

```bash
docker compose up --build
```

uruchomić dwa razy - za pierwszym się wyjebie bo postgresql długo się stawia xd

```bash
docker compose exec db psql -U orm_user -d orm_demo
```
żeby móc odpytywać bazę
```bash
\dt - tabele, \q - wyjście, normalne zapytnia sql
```