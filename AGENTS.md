# BE Subject Pathway

Follow the repository-level instructions in `../AGENTS.md`.

- Use Java 17 and Spring Boot 4.1.0 as declared in `pom.xml`.
- Use the supplied Maven Wrapper: `./mvnw test` on POSIX shells or `.\mvnw.cmd test` on Windows. No project-specific lint command is configured in `pom.xml`.
- Keep controllers limited to HTTP concerns and validation. Put orchestration and deterministic ranking in services. Keep graph access in repositories or gateways, DTOs at API boundaries, and configuration separate.
- Use parameterized Cypher only. Never concatenate user input into Cypher.
- Keep PostgreSQL persistence responsibilities separate from Neo4j graph-query responsibilities.
- Configure PostgreSQL, Neo4j, and 9Router values through environment variables; never log credentials or raw sensitive prompts.
- Treat LLM responses as untrusted JSON. Do not use an LLM to determine official admissions eligibility.
- Add tests for validation, ranking, graph queries, and error handling when changing those behaviors.
