A real-time, multi-room chat system built from raw TCP sockets in Java 21+, using NIO2 channels, virtual threads, and production-hardened infrastructure. Gradle multi-module build.

---

## High-Level Architecture

```mermaid
graph TB
    subgraph Clients
        C1[CLI Client 1]
        C2[CLI Client 2]
        C3[CLI Client n]
    end

    subgraph "TCP Server (NIO2 + Virtual Threads)"
        ACC[Acceptor Loop]
        TP[Virtual Thread Pool]
        
        subgraph "Core Pipeline"
            CODEC[Codec / Framer]
            AUTH[Auth Handler]
            RL[Rate Limiter]
            ROUTER[Message Router]
        end
        
        subgraph "Services"
            RM[Room Manager]
            SM[Session Manager]
            CM[Cache Manager]
            MM[Metrics Manager]
        end
    end

    C1 & C2 & C3 -->|TCP| ACC
    ACC --> TP
    TP --> CODEC --> AUTH --> RL --> ROUTER
    ROUTER --> RM
    ROUTER --> SM
    RM --> CM
    SM --> CM
    RM --> MM
```

---

