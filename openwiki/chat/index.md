# Files

- [Cached Chat Requests](cached-chat.md) - The two cached chat paths - the CachingChatModel decorator that routes every ChatModel overload through doChat with a cache-hit short-circuit and immediate flush, and the ChatModelUtils single and n-fold cached request helpers with count-prefixed keys and caller-driven flushing.
- [Chat Models](chat-models.md) - How ChatModelProvider turns an LlmConfiguration into lazily-initialized LangChain4j ChatModel instances for OpenAI, Ollama, Blablador, DeepSeek, and Open WebUI — the configuration record, the platform enum, LazyChatModel's deferred client construction, per-platform credentials, and the bridge into the cache subsystem.
