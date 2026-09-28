# Conversational revisions v2

Existing electronics projects can now be revised through a multi-turn requirements conversation.

## Goal

A user can open an existing project, tap **追加要望を伝える**, and continue describing additions or changed conditions in ordinary Japanese.

The app does not require the whole revision to be correct in one message. It keeps a draft conversation, asks only the questions still needed, and recompiles automatically when the requirements are sufficient.

## Runtime flow

1. The user enters a new request or answers the current question.
2. The language assistant consolidates the current goal plus the latest turn.
3. If the language assistant detects a semantic ambiguity, it asks one non-technical clarification question.
4. The Android app passes the consolidated goal to the deterministic `BeginnerIntentInterpreter` and `DefaultRequirementResolver`.
5. Any remaining physical/safety requirement is asked in the same chat.
6. Only when the deterministic resolver is ready does the normal compiler run:
   - capabilities
   - components
   - board
   - power
   - pins
   - CircuitGraph
   - electrical validation
   - behavior
   - diagrams
   - manifest
   - UI
   - diagnostics
7. A successful compile atomically replaces the current project design.
8. A blocked or failed revision leaves the previous safe design intact and the conversation remains open for correction.

## AI gateway

The Android app never contains an OpenAI API key.

Configure the Android build with:

```text
AI_GATEWAY_URL=https://your-gateway.example.com/v1/revision-chat
AI_GATEWAY_TOKEN=optional-gateway-access-token
```

Both values may be supplied as Gradle properties or environment variables.

The repository contains `ai-gateway/`, a minimal Node.js gateway. Configure the server with:

```text
OPENAI_API_KEY=...
OPENAI_MODEL=gpt-5.6-luna
AI_GATEWAY_TOKEN=optional-gateway-access-token
PORT=8787
```

Then run:

```bash
cd ai-electronics-builder/ai-gateway
npm test
npm start
```

`AI_GATEWAY_TOKEN` is only a lightweight gateway access control and is not a replacement for production app authentication. A distributed mobile client cannot keep such a client token secret. Production deployment should put the gateway behind normal user/device authentication, rate limiting, request-size limits, monitoring, and TLS.

## OpenAI contract

The gateway calls the Responses API with:

- server-side `OPENAI_API_KEY`;
- `store: false`;
- configurable model, defaulting to `gpt-5.6-luna`;
- low reasoning effort;
- strict JSON Schema Structured Outputs.

The model returns exactly:

- `assistant_message`
- `updated_goal`
- `needs_clarification`
- `clarification_question`

The model is explicitly instructed not to finalize GPIO, pin mappings, wiring, voltage/current ratings, power topology, protection, libraries, or framework choices.

## Fallback behavior

If `AI_GATEWAY_URL` is not configured, the chat remains usable with the local deterministic assistant.

If a configured gateway fails during a turn, the app falls back to the local path for that turn and tells the user that it switched modes.

This means AI availability is not allowed to break the deterministic electrical design path.

## Revision safety semantics

A revision is a draft until a full compile succeeds.

During the draft:

- the persisted `goalText` remains unchanged;
- the previous `ReleaseBundle` remains active;
- the previous deployed state is not invalidated yet.

After successful compilation:

- the revised goal and clarification values replace the prior durable intent;
- generated artifacts are replaced together;
- completed wiring IDs are preserved only when they still exist in the new CircuitGraph;
- `deployed` becomes false;
- the BLE connection is cleared;
- the user is told that the device must be configured again.

This prevents a half-complete conversation from corrupting a previously valid project.
