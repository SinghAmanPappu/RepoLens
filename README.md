# 🔍 RepoLens: AI-Powered Codebase Architect

RepoLens transforms **any** GitHub repo into an interactive knowledge base. It indexes source code into pgvector and answers architecture questions with Google Gemini — grounded in retrieved code, with file paths.

> **Works for every repo/language** — Java, Python, JavaScript/TypeScript, Go, Rust, C/C++, C#, Ruby, PHP, Swift, HTML/CSS, Kotlin, shell, config/docs, etc. Binaries and vendored dirs are skipped automatically.

## 🚀 What it does
- Crawls a GitHub repo via the GitHub API (accepts `owner/repo` or full URL, including private repos with a token).
- Indexes text files (`.java`, `.xml`, `.properties`, `.md`, `.kt`, `.py`, …) — with size/file-count guards.
- Embeds chunks with Gemini, stores in PostgreSQL+pgvector, retrieves top matches (`minScore 0.6`), and prompts Gemini as a Senior Architect: "answer only from context, cite file paths, else say you don't know."
- Modern dark UI (Bootstrap 5 + Thymeleaf): hero input with client validation, loading states, chat with Markdown.

## 🛠️ Stack
| Layer | Tech |
|---|---|
| Core | Java 21+, Spring Boot 3.5, Maven |
| AI | LangChain4j 1.12.2, Gemini chat (`gemini-2.5-flash-lite`), Gemini embeddings (`gemini-embedding-001`) |
| Vector DB | PostgreSQL + pgvector (Docker, `ankane/pgvector`) |
| GitHub | `org.kohsuke:github-api` 1.330 |
| UI | Thymeleaf + Bootstrap 5 + Marked/DOMPurify/highlight.js + vanilla fetch JS (no-reload chat) |

## 🏗️ Pipeline
1. **Ingest:** `CrawlerService` clears old vectors, walks the tree, reads files, `IngestionService` splits (`500/50`) → embeds → stores (metadata: `file_path`).
2. **Retrieve:** `EmbeddingStoreContentRetriever` embedded query → top matches.
3. **Augment:** `RetrievalAugmentor` injects retrieved code into the `RepolensAgent` prompt (system prompt enforces grounding + file paths).

## ▶️ Run locally
Prerequisites: JDK 21+, Docker, Gemini API key, GitHub PAT (optional for public repos, required for private).

```bash
cp .env.example .env   # fill GEMINI_API_KEY, GITHUB_TOKEN
docker compose up -d   # starts pgvector on host 5433 → container 5432
docker exec pgvector psql -U repolens -d repolens -c "CREATE EXTENSION IF NOT EXISTS vector;"
mvn clean test         # contextLoads should pass
mvn spring-boot:run
# open http://localhost:8080/api/
```

Notes:
- Defaults use host port **5433** to avoid clashing with a local Windows Postgres on 5432. Override with `DB_PORT`.
- `.env` is gitignored. Never commit secrets.
- First ingest downloads nothing big; embedding small repos takes ~1–2s/file.

## 📝 Usage
1. Paste `owner/repo` → **Analyze Repository** (indexing runs synchronously; larger repos take longer).
2. Ask questions, e.g. "How does the ATM simulation handle withdrawals?" or "Summarize the project structure."
3. Health check: `GET /api/health` → `{"status":"UP","service":"RepoLens"}`.

## 📁 Layout (relevant)
- `src/main/java/com/S_aman/RepoLens/config/` — `GeminiConfig`, `GithubConfig`, `VectorConfig` (embedding model, pgvector store, retriever, augmentor)
- `.../service/CrawlerService.java` — GitHub crawl + `normalizeRepoName`
- `.../service/IngestionService.java` — split/embed/store
- `.../Agent/RepolensAgent.java` — `@AiService` + RAG wiring
- `.../Controller/RepoController.java` — web routes (`/api/`, `/api/Repository`, `/api/ask`, `/api/chat`)
- `src/main/resources/templates/` — `index.html`, `chat.html`, `error.html`
