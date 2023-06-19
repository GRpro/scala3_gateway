# Gateway router 

to limit and balance requests among workers, see [TASK_README.md](TASK_README.md) for more information


# How to run

1. Start worker containers with docker compose

   ```bash
   docker-compose up -d
   ```
2. Build and run `gateway` with SBT
   ```bash
   sbt run
   ```
3. In a separate terminal execute 100 parallel requests
   ```bash
   ./requests.sh 100
   ```
4. In a separate terminal check `gateway` stats
   ```bash
   $ curl localhost:8080/stats
   {"waitingRequests":81,"activeRequests":3}
   ```



