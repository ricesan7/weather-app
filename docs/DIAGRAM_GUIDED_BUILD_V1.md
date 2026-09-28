# Diagram and guided build v1

This milestone implements the deterministic visual layer.

## Source of truth

The renderer never invents electrical connections.

CircuitGraph Connection IDs are copied 1:1 into DiagramWire objects. The compiler fails if an entity or pin lacks a verified visual anchor.

## Golden visual assets

The initial asset catalog contains vector geometry and verified anchors for:
- XIAO ESP32S3
- AE-SHT31
- TBD62003APG
- YDM2510C05 fan
- AD-T50P200 5V/2A supply

The assets are schematic-style illustrations designed for reliable pin placement. Later artwork can become more photorealistic without changing the anchor contract.

## Views

Generated DiagramSpec includes:
- system overview
- authoritative physical wiring
- power/GND check
- one solder-step view per Connection

The current Golden circuit has 10 Connections, therefore 10 solder steps.

## SVG

SvgDiagramRenderer produces deterministic SVG suitable for:
- Android WebView/ImageVector conversion
- zoom/pan
- connection highlighting
- offline storage/export

Each wire carries data-connection-id so the Android screen can select the same wire used by BuildStep and diagnostics.

## Guided build

GuidedBuildStateMachine shows one connection at a time, supports back/navigation, stores snapshots, and resumes from prior progress.
