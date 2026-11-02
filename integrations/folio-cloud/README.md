# Folio Cloud

Private HTTPS relay and authenticated MCP server for Folio 1.17.0-preview.1.
Android and desktop initiate outbound connections through their normal VPN/proxy
route; there is no inbound tablet port or shared Wi-Fi requirement.

Open https://folio-canvas-link.mikhailllm.chatgpt.site in your ChatGPT account.
Create and copy the connection, then import it in Folio under ChatGPT / Codex.
Enable access and leave the notebook open. Install the Site's Folio plugin for
cloud MCP, or import the same connection into desktop plugin 0.2.0.

Pairing keys are secret. The Site is owner-private. Device and desktop requests
add a Sites service header and a channel-specific role HMAC. Queued payloads use
AES-256-GCM; the service can decrypt them to execute cloud MCP operations. This
is not end-to-end encryption against the relay. Requests expire after 45 seconds,
results after 120 seconds; encrypted expired objects are cleaned on later traffic.
Personal handwriting samples persist until removed or the service is deleted.

The host applies drizzle migrations to D1 and provides DB and BUCKET bindings.
FOLIO_SERVICE_ACCESS is set as a Sites secret, never in the source checkout.
Use Sites source/build/publication helpers. Dependencies: Node 22.13+, npm, Git.
node --test tests/relay.test.mjs exercises SQLite, encrypted queues, authorization,
revocation, expiry and MCP. Physical tablet and user VPN testing is separate.
