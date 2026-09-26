# Physical design v1

This milestone makes the wiring graph deterministic enough to become the source for graphical assembly instructions.

## Pin allocation

The initial XIAO ESP32S3 reference allocation is produced by `CatalogPinAllocator`:

- AE-SHT31 SDA -> D4 / GPIO5
- AE-SHT31 SCL -> D5 / GPIO6
- TBD62003APG I1 -> D3 / GPIO4

The user does not select these pins in beginner mode.

## Circuit compilation

`CatalogCircuitCompiler` combines:
- signal pin assignments;
- logic power rails;
- load power domains;
- driver/load topology;
- external supply;
- mandatory common ground.

For the current Golden design it produces ten authoritative physical connections. The SHT31 ADR pin is intentionally not represented as a connection because the verified design uses it open for address 0x45.

## Electrical validation

`CatalogElectricalValidator` currently blocks:
- supply voltage outside a component's verified range;
- unknown current on an external-load domain;
- insufficient external supply current margin;
- missing required load driver;
- driver current rating below load current;
- MCU logic voltage below driver HIGH threshold;
- I2C address collisions;
- direct GPIO drive of a protected high-current load;
- missing common ground between external supply and MCU.

A broken circuit never proceeds to Diagram/UI/Manifest generation because DefaultProjectCompiler short-circuits on ValidationState.BLOCKED.

## Next

The next renderer consumes CircuitGraph and verified ComponentAssets/pin anchors to produce:
- physical wiring overview;
- one-connection solder steps;
- power-check view;
- optional schematic view for advanced users.
