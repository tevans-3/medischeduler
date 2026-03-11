# Issue 8: WebSocket Protocol — STOMP over SockJS

## The Problem

The backend was configured for STOMP over SockJS:

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/upload");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/assignments").setAllowedOriginPatterns("*").withSockJS();
    }
}
```

But the frontend used the raw WebSocket API:

```javascript
const ws = new WebSocket(`ws://${window.location.host}/assignments`);
ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    setAssignments(data);
};
```

These are incompatible protocols. STOMP (Simple Text-Oriented Messaging Protocol) adds a framing layer on top of WebSocket — messages have commands (`CONNECT`, `SUBSCRIBE`, `MESSAGE`), headers, and a body. A raw WebSocket client doesn't speak STOMP, so it would receive unintelligible frames and fail silently.

Additionally, the SockJS fallback endpoint changes the URL scheme. SockJS doesn't connect directly to `/assignments` — it negotiates via `/assignments/info` and then uses transport-specific URLs like `/assignments/websocket` or `/assignments/xhr-streaming`.

## The Fix

### Install Client Libraries

```bash
npm install @stomp/stompjs sockjs-client
```

- **@stomp/stompjs**: A modern STOMP client for JavaScript. Handles the STOMP protocol framing, connection lifecycle, heartbeats, and reconnection.
- **sockjs-client**: A WebSocket emulation library that provides fallback transports (XHR streaming, long polling) for environments where native WebSocket is unavailable.

### Rewrite the Connection Code

```javascript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

useEffect(() => {
    const stompClient = new Client({
        // Use SockJS as the transport layer
        webSocketFactory: () => new SockJS('/assignments'),

        // Automatically reconnect after 5 seconds
        reconnectDelay: 5000,

        onConnect: () => {
            setConnected(true);

            // Subscribe to the client-specific topic
            stompClient.subscribe(
                `/topic/${clientId}/upload/assignments`,
                (message) => {
                    const data = JSON.parse(message.body);
                    if (Array.isArray(data)) {
                        setAssignments(data);
                    }
                }
            );
        },

        onStompError: (frame) => {
            setError('WebSocket error: ' + frame.headers?.message);
        },
    });

    stompClient.activate();
    return () => stompClient.deactivate();
}, [clientId]);
```

### Add REST Fallback

The WebSocket connection is now complemented by a REST fetch on page load:

```javascript
useEffect(() => {
    fetch('/api/assignments', { credentials: 'include' })
        .then((res) => res.ok ? res.json() : [])
        .then((data) => {
            if (data.length > 0) setAssignments(data);
        });
}, [clientId]);
```

This ensures assignments are available even if the WebSocket connection fails (see Issue 5).

## Key Concept: The WebSocket Protocol Stack

### Layer Model

```
┌─────────────────────────┐
│     Application         │  Your code: assignments, subscriptions
├─────────────────────────┤
│     STOMP               │  Framing: CONNECT, SUBSCRIBE, MESSAGE
├─────────────────────────┤
│     SockJS              │  Transport negotiation & fallbacks
├─────────────────────────┤
│     WebSocket / XHR     │  Actual network transport
├─────────────────────────┤
│     TCP                 │  Reliable byte stream
└─────────────────────────┘
```

Each layer solves a different problem:

### WebSocket (RFC 6455)

A full-duplex communication channel over a single TCP connection. Unlike HTTP (request-response), WebSocket allows both sides to send messages at any time. The connection starts as an HTTP request with an `Upgrade: websocket` header, then "upgrades" to the WebSocket protocol.

Limitation: WebSocket only provides raw message framing. It has no concept of topics, subscriptions, or message routing.

### STOMP (Simple Text-Oriented Messaging Protocol)

STOMP adds messaging semantics on top of WebSocket:

```
SUBSCRIBE
destination:/topic/client-123/upload/assignments
id:sub-0

^@
```

```
MESSAGE
destination:/topic/client-123/upload/assignments
content-type:application/json

[{"student":{...},"teacher":{...}}]
^@
```

Key STOMP concepts:
- **Destinations**: Topic-like addresses (e.g., `/topic/client-123/upload/assignments`)
- **SUBSCRIBE**: Register interest in a destination
- **SEND**: Publish a message to a destination
- **MESSAGE**: Server pushes a message to subscribed clients
- **Heartbeats**: Keep-alive frames to detect dead connections

Spring's `SimpMessagingTemplate.convertAndSend()` creates a STOMP `MESSAGE` frame — which a raw WebSocket client cannot parse.

### SockJS

Not all environments support WebSocket:
- Some corporate proxies strip the `Upgrade` header.
- Some load balancers don't forward WebSocket connections.
- Some older browsers lack WebSocket support.

SockJS provides transparent fallback transports:

1. **WebSocket** (preferred) — native full-duplex
2. **XHR streaming** — long-lived HTTP response that streams data
3. **XHR polling** — repeated short HTTP requests
4. **EventSource** — server-sent events (unidirectional)

The SockJS client negotiates the best available transport automatically. Your STOMP code doesn't need to change — it works identically regardless of the underlying transport.

### Why `webSocketFactory` Instead of a URL

The `@stomp/stompjs` client normally connects via `brokerURL: 'ws://host/path'`. But when using SockJS, you provide a `webSocketFactory` function instead:

```javascript
webSocketFactory: () => new SockJS('/assignments')
```

This tells the STOMP client to use SockJS (which handles its own URL negotiation) instead of creating a native `WebSocket` directly.

## Vite Proxy Configuration

During development, the frontend (port 5173) and backend (port 8082) run on different origins. The Vite dev server proxies WebSocket and REST requests:

```javascript
proxy: {
    '/assignments': {
        target: 'http://localhost:8082',
        ws: true,           // proxy WebSocket upgrade requests
        changeOrigin: true
    },
    '/api/assignments': {
        target: 'http://localhost:8082',
        changeOrigin: true
    }
}
```

The `ws: true` flag is critical — without it, Vite only proxies HTTP requests and drops the WebSocket upgrade handshake.
