# Additional requirements v1

Existing projects can now accept requirement changes after the first design has been generated.

## User flow

1. Open any active project screen.
2. Tap **追加要望を伝える**.
3. Enter the new request or changed condition in ordinary language.
4. Tap **追加要望を反映して再設計**.
5. The request is appended to the durable project intent and the full deterministic pipeline runs again.

The revision path returns to Design after a successful compile.

## Safety and regeneration

A revision never patches wiring or firmware directly.

The updated intent runs through:

intent interpretation -> requirement resolution -> component/board/power/pin selection -> circuit graph -> electrical validation -> behavior -> diagrams -> manifest -> UI -> diagnostics

Electrical BLOCK still stops deployment artifacts.

A previously deployed project is marked as requiring device reconfiguration after a revision. The BLE connection is cleared so stale runtime state cannot be mistaken for the new design.

## Persistence compatibility

No new database column is required in v1. The durable `goalText` stores revisions as appended `【追加要望】` sections, so projects created by the previous SQLite schema continue to load.

Clarification values are cleared when a revision is submitted. This avoids old answers silently overriding changed conditions; the resolver asks again only when the revised design still needs user input.

## Precedence

For numeric temperature and humidity conditions, the latest matching statement wins. This lets a request such as:

- initial: 30℃以上でON / 28℃以下でOFF
- revision: 32℃以上でON / 29℃以下でOFF

compile with 32℃ / 29℃ as the current thresholds.

Unchanged generated connection IDs keep their guided-build completion state. Connections that no longer exist in the regenerated CircuitGraph are discarded from completed progress.
