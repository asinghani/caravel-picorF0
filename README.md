# caravel-picoRF0

- CAN bus controller for autonomous vehicles, connected to caravel and with it's I/O and debug interfaces exposed on I/O pins (developed by Natalia Machado)
    - See [nxmq/Can_Core](https://github.com/nxmq/Can_Core) for CAN controller Chisel source code

- picoRF0 - a multicycle CPU core running a simplified RISC ISA (targeted for teaching purposes). Connected to caravel for memory interfacing and I/O usage
    - See `picoRF0` directory for CPU Chisel source code

- Tiny VGA pong game controller to test I/O interface speed
