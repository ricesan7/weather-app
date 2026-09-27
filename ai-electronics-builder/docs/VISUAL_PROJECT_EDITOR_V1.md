# Visual Project Editor V1

## Purpose

Visual Project is the primary visual editing surface for the electronics platform.
It is not a second design database.

The validated project compiler remains the source of truth for electrical design,
behavior, firmware/runtime artifacts, Base44 handoff contracts, and safety output.

## Editing semantics

### Canvas layout edits

Dragging a node changes only its normalized Visual Project canvas position.

- Positions are persisted per local project.
- Hardware, behavior, application, and runtime nodes stay in their semantic lane.
- A canvas move does not silently change GPIO, wiring, firmware, or electrical design.

### Application UI ordering

Application page and UI widget nodes have one additional meaning:

- the vertical order of UI_PAGE nodes controls Base44 page/tab order;
- the vertical order of UI_WIDGET nodes controls widget order within that page.

The Android Hardware Bridge applies this visual order as an override when sending
the AppHardwareIntegrationContract to CircuitFlow/Base44.

This allows visual reordering without creating a second UiSpec database.

### Semantic edits

Adding, changing, or deleting a design node is not performed by mutating the
compiled graph directly.

Visual Project creates a semantic revision request, then runs the existing
revision pipeline:

Visual action
→ revision request
→ requirement resolution
→ deterministic project compilation
→ electrical validation
→ Project Graph regeneration
→ firmware/runtime regeneration
→ Base44 contract regeneration

If clarification is required, the workflow opens the revision conversation.
If compilation or safety validation fails, the existing valid project is kept.

## Protected nodes

BOARD and RUNTIME nodes cannot be directly deleted.

They can be replaced through a semantic change request so board selection,
transport support, deployment, and firmware compatibility are recomputed.

## Bridge behavior

CircuitFlow/Base44 and the physical hardware connection have separate states.

- Android Bridge connected, physical hardware disconnected:
  - UiSpec / Project Contract continues to sync;
  - Base44 can render the generated application;
  - controls are disabled;
  - commands are not delivered.
- Physical hardware connected:
  - telemetry and current settings sync;
  - contract-approved controls are enabled;
  - semantic commands can be delivered and acknowledged.

Commands not present in the generated Project Contract are rejected by both
Base44 and Android.

## Persistence

Visual node positions are stored in the Android project database.
The database schema is migrated from v1 to v2 with graph_positions_json.

After recompilation, positions are retained only for node IDs that still exist.
New graph nodes receive deterministic default positions.

## Current supported visual operations

- select a node;
- drag a node;
- persist layout;
- add a hardware/software/app requirement from Visual Project;
- change a selected node through the revision pipeline;
- delete eligible nodes through the revision pipeline;
- reorder Base44 pages by dragging UI_PAGE nodes;
- reorder Base44 widgets by dragging UI_WIDGET nodes.

## Next extensions

Future visual operations should continue to mutate semantic source data or
validated revision requests rather than editing derived artifacts independently.

Candidate next layers:

- searchable component palette;
- drag-to-add components;
- explicit edge creation for supported semantic connections;
- property inspector with typed fields;
- undo/redo transaction history;
- diff preview before applying structural changes;
- multi-select/grouping;
- zoom/pan and large-project navigation;
- live runtime health overlays on graph nodes.
