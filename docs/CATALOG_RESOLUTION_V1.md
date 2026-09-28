# Capability, parts and power resolution v1

This milestone removes more beginner-facing technical decisions.

## Capability mapping

`DefaultCapabilityMapper` converts ordinary project intent into normalized capabilities.

For the practical ventilation reference, this includes:
- measure_temperature
- measure_humidity
- actuate_fan
- automation_rules
- logging
- manual_override
- persistent_settings
- generated_ui
- self_test
- failsafe

## Engineering catalog

`parts-db` introduces an engineering catalog separate from supplier inventory.

The initial verified reference catalog contains:
- XIAO ESP32S3
- ESP32-S3 DevKitC-1-N8R8
- AE-SHT31
- TBD62003APG
- YDM2510C05 5V fan
- AD-T50P200 5V/2A supply

The catalog stores engineering capabilities and constraints, not live stock/price.

## Component resolution

`CatalogComponentResolver` uses a deterministic greedy coverage algorithm.

A component may satisfy multiple capabilities. For example AE-SHT31 satisfies both temperature and humidity. Required support components are added recursively; the fan pulls in a verified low-side driver automatically.

## Board selection

`CatalogBoardSelector` derives required interfaces and transport, then picks a compatible design-ready board. Beginner preference is encoded as engineering priority, not exposed as a manual selection.

## Power planning

`CatalogPowerPlanner` groups selected components into voltage domains and selects a verified external supply when a load requires it.

Unknown current data is explicitly carried in the PowerDomain instead of being silently guessed.
