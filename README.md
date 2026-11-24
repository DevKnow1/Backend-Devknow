# DevKnow – Backend (Java / Spring Boot)

Backend del tutor inteligente **DevKnow** (RAG sobre documentación técnica privada), basado en la
*Matriz de Trazabilidad* (RF-01…RF-30, RNF-01…RNF-27) y en el flujo de pantallas del prototipo.

**Stack:** Java 21 · Spring Boot 3.3 · Spring Security + JWT · Spring Data JPA · Flyway · PostgreSQL + pgvector · Apache PDFBox · Docker Compose.

## Arranque rápido

```bash
cp .env.example .env            # opcional; los valores por defecto sirven para desarrollo
docker compose up --build       # PostgreSQL+pgvector y la API en http://localhost:8080
```

Sin Docker: levanta un PostgreSQL con la extensión `vector` (imagen `pgvector/pgvector:pg16`) y ejecuta `mvn spring-boot:run`.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Administrador inicial: `ADMIN_EMAIL` / `ADMIN_PASSWORD` (por defecto `admin@devknow.local` / `ChangeMe123`). **Cámbialo.**
- El registro público (`/api/auth/register`) siempre crea usuarios con rol `USER`.

## Proveedor de IA (RNF-22, RNF-25/26/27)

Toda la IA pasa por las interfaces `EmbeddingClient` y `LlmClient`; el proveedor se elige por configuración:

| `AI_PROVIDER` | Uso |
|---|---|
| `mock` (por defecto) | Desarrollo/pruebas sin costo ni claves. Embeddings por hashing y respuestas extractivas: **no** es calidad real. |
| `openai-compatible` | Cualquier API compatible con OpenAI (`/embeddings`, `/chat/completions`): Ollama local, Groq, Gemini (endpoint OpenAI-compatible), OpenAI, etc. |

Ejemplo con Ollama local (costo S/0): `AI_PROVIDER=openai-compatible`, `AI_BASE_URL=http://localhost:11434/v1`,
`AI_EMBEDDING_MODEL=nomic-embed-text`, `AI_CHAT_MODEL=llama3.1`, `EMBEDDING_DIMENSION=768`.

> **Importante:** `EMBEDDING_DIMENSION` define el tipo `vector(N)` al crear la tabla (migración V1). Si cambias de modelo con
> otra dimensión, debes recrear la base (o añadir una migración) y reindexar los documentos.

Se aplican timeouts (`AI_TIMEOUT`), reintentos limitados solo para fallos transitorios (`AI_MAX_RETRIES`) y los errores
(timeout, indisponibilidad, cuota 429) se devuelven como `503/429` con mensaje controlado, sin simular una respuesta válida.

## API

Autenticación: `Authorization: Bearer <token>`.

| Método | Ruta | Rol | Descripción | RF |
|---|---|---|---|---|
| POST | `/api/auth/register` | público | Registro `{fullName,email,password}` | RF-01 |
| POST | `/api/auth/login` | público | Devuelve `accessToken` y usuario | RF-02 |
| POST | `/api/auth/logout` | auth | Revoca el token | RF-03 |
| GET | `/api/auth/me` | auth | Usuario actual y rol | RF-04 |
| POST | `/api/chat` | auth | `{conversationId?, question}` → respuesta + fuentes | RF-18…21, 23 |
| GET | `/api/conversations` | auth | "Mis conversaciones" | RF-22 |
| GET | `/api/conversations/{id}` | auth | Historial con fuentes | RF-22 |
| DELETE | `/api/conversations/{id}` | auth | Elimina una conversación propia | RNF-09 |
| GET | `/api/documents/{id}/file` | auth | "Abrir documento" (usuarios: solo `AVAILABLE`) | RF-20 |
| POST | `/api/documents` | ADMIN | Carga multipart (`files`, uno o varios) | RF-06, 07, 12 |
| GET | `/api/documents?q=&status=&page=&size=` | ADMIN | Listado y filtros | RF-25 |
| GET | `/api/documents/stats` | ADMIN | Tarjetas: total/disponibles/procesando/errores | RF-25 |
| GET | `/api/documents/processing` | ADMIN | Pantalla "Procesamiento" (pendientes + procesando) | RF-12 |
| GET | `/api/documents/{id}` | ADMIN | Metadatos | RF-26 |
| PATCH | `/api/documents/{id}` | ADMIN | Edita `name`, `description` | RF-27 |
| PUT | `/api/documents/{id}/file` | ADMIN | Reemplaza archivo y reindexa | RF-28, 29 |
| POST | `/api/documents/{id}/reprocess` | ADMIN | Reintenta un documento con error | RF-13 |
| DELETE | `/api/documents/{id}` | ADMIN | Elimina documento, chunks y embeddings | RF-30 |

Estados de documento: `PENDING`, `PROCESSING`, `AVAILABLE`, `ERROR`. La ingesta es **asíncrona**: la carga responde enseguida y el
front consulta el estado (polling a `/api/documents` o `/processing`).

Ejemplo de respuesta de `/api/chat`:

```json
{
  "conversationId": 12, "messageId": 40, "insufficientEvidence": false,
  "answer": "El proceso consta de tres fases: preparación, integración y seguimiento [1].",
  "sources": [{ "index": 1, "documentId": 3, "documentName": "Guía de incorporación.md",
                "page": null, "section": "2.1 Fases", "fragment": "…", "chunkId": 88, "score": 0.81 }]
}
```

Errores: `{timestamp, status, error, message, fieldErrors?, conversationId?}`. Si falla el proveedor de IA, `conversationId`
permite al front conservar la conversación (la pregunta ya quedó guardada).

## Decisiones de diseño

- **Pipeline de ingesta (RF-08…11):** PDFBox (texto por página; OCR fuera del MVP) → Markdown por secciones → chunking configurable
  (`CHUNK_SIZE`, `CHUNK_OVERLAP`) → embeddings por lotes → persistencia atómica en `document_chunks` (pgvector, índice HNSW coseno).
- **Consistencia (RF-29, RNF-15):** la recuperación solo considera documentos `AVAILABLE`. Al reemplazar, el documento pasa a
  `PROCESSING` de inmediato; los chunks viejos se sustituyen en una sola transacción solo cuando los nuevos están listos. Si falla,
  queda en `ERROR` (se conserva la versión anterior pero no es consultable) y no se mezclan versiones.
- **Evidencia suficiente (RF-17, RF-21, RNF-14):** umbral `RAG_MIN_SCORE` (similitud coseno) antes de llamar al LLM + instrucción
  `NO_EVIDENCE` en el prompt. Sin evidencia: mensaje explícito, sin fuentes inventadas.
- **Citas (RF-16, RF-20):** cada fragmento conserva documento, página (PDF) o sección (MD). Se devuelven las fuentes citadas por el
  modelo con su fragmento exacto.
- **Seguimiento (RF-23):** se incluyen los últimos `RAG_HISTORY_MESSAGES` mensajes de la misma conversación y, para preguntas cortas,
  la recuperación se enriquece con la pregunta previa del usuario.
- **Contexto acotado (RNF-03):** `RAG_MAX_CONTEXT_CHARS` y `RAG_TOP_K`.
- **Seguridad:** BCrypt (RNF-04); autorización por rol en URL y por método (RNF-05); validación de extensión, tamaño y firma real
  del archivo, nombres de almacenamiento aleatorios (RNF-06); secretos solo por variables de entorno (RNF-07); conversaciones
  filtradas por propietario en servidor y 404 ante ajenas (RNF-09); mensajes de error sin trazas (RNF-19); logs sin secretos ni
  contenido de documentos (RNF-17). HTTPS (RNF-08) se termina en el proxy/plataforma de despliegue.

## Pruebas

```bash
mvn test        # Chunker, FileValidator y JwtService (no requieren base de datos)
```

## Pendiente / a validar

- Pruebas de rendimiento P95 ≤ 8 s y 30 s para 10 MB (RNF-01/02) y banco de 30 preguntas (RNF-11/12/13): dependen del proveedor elegido.
- Pruebas de integración con PostgreSQL (p. ej. Testcontainers con `pgvector/pgvector:pg16`) y de autorización por endpoint.
- Matriz de compatibilidad de navegadores (RNF-20) y documentación de privacidad/proveedor (RNF-10) son entregables del equipo.
- Ajustar `RAG_MIN_SCORE` con el modelo real: los valores de similitud varían entre modelos de embeddings.
