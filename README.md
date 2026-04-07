# 🔍 RepoLens: AI-Powered Codebase Architect

RepoLens is an advanced RAG (Retrieval-Augmented Generation) application built with Spring Boot 3.2 and Java 21. It transforms static GitHub repositories into interactive knowledge bases, allowing developers to perform semantic queries and receive architectural insights directly from Google Gemini AI.

## 🚀 Key Features
- **Smart Repository Crawling:** Recursively traverses GitHub file trees using the GitHub API to extract and clean Java source logic.
- **Vectorized Semantic Search:** Implements PGVector to store high-dimensional code embeddings, enabling the AI to "find" relevant logic without keyword matching.
- **Context-Aware AI Chat:** Integrated with LangChain4j to provide a "Senior Architect" experience, answering complex questions about code flow, design patterns, and logic.
- **Real-time Markdown Rendering:** A clean web interface that styles AI responses with bold text, code blocks, and bullet points for better readability.

## 🛠️ Technologies Used
| Category | Technologies |
|---|---|
| Core | Java 21, Spring Boot 3.x, Maven |
| AI Orchestration | LangChain4j, Google Gemini Pro (LLM) |
| Vector Database | PostgreSQL with PGVector extension |
| External APIs | GitHub API (org.kohsuke.github) |
| Frontend | Thymeleaf, Bootstrap 5, Marked.js (Markdown parsing) |

## 🏗️ Technical Architecture (The RAG Pipeline)
1. **Ingestion:** The CrawlerService fetches `.java` files and passes them to the IngestionService.
2. **Embedding:** Code is split into TextSegments and converted into vectors using Gemini's embedding model.
3. **Storage:** Vectors are persisted in a PGVector store.
4. **Retrieval:** When a user asks a question, the system retrieves the most mathematically similar code segments.
5. **Augmentation:** The AI Agent receives the user query + the retrieved code context to generate a hallucination-free answer.

## 📁 Project Structure
- `com.S_aman.RepoLens.controller`: REST/Web endpoints for repository ingestion and AI chat.
- `com.S_aman.RepoLens.service`
  - `CrawlerService.java`: GitHub API integration and file tree traversal.
  - `IngestionService.java`: Manages the vector database and text segmentation.
- `com.S_aman.RepoLens.agent`: Contains the AiService interface for LangChain4j and Gemini communication.

## 🚦 Getting Started
### Prerequisites
- JDK 21 or later
- Docker (to run the PGVector/PostgreSQL container)
- Google AI (Gemini) API Key

### Environment Configuration
Create a `.env` file or set the following in your environment:

- export GEMINI_API_KEY=your_gemini_key
- export GITHUB_TOKEN=your_github_pat
- export DB_URL=jdbc:postgresql://localhost:5432/vector_db

### Running with Docker (Database)
To get the vector store running quickly:

- "docker run --name pgvector -e POSTGRES_PASSWORD=password -p 5432:5432 -d ankane/pgvector"

## 📝 Usage
- **Analyze:** Send a POST request with a GitHub URL (e.g., `SinghAmanPappu/Java-Console-Tasks`).**
- **Ask:** Use the Chat UI to ask questions like:
  - "How does the ATM simulation handle withdrawals?"
  - "Summarize the relationship between the Controller and the Service."
  - "Where is the database connection logic located?"
