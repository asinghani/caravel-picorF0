package top

import chisel3._
import chisel3.util._
import _root_.core.Core

// Connect the core to the caravel logic analyzer to use as it's memory interface
class CoreTop extends Module {
    val INIT_PC: Int = 0

    val io = IO(new Bundle {
        val la_data_in = Input(UInt(128.W))
        val la_data_out = Output(UInt(128.W))
        val la_oenb = Input(UInt(128.W))
    })

    val la_data_in = RegNext(Mux(io.la_oenb(127, 64).orR, 0.U(128.W), Cat(io.la_data_in(127, 64), 0.U(64.W))))

    val ibus_stb_latch = RegInit(false.B)
    val ibus_ack_in = Wire(Bool())
    val ibus_stb_clear = Wire(Bool())

    val dbus_stb_latch = RegInit(false.B)
    val dbus_ack_in = Wire(Bool())
    val dbus_stb_clear = Wire(Bool())

    val core = Module(new Core(INIT_PC))

    when (core.io.ibus_stb) { ibus_stb_latch := true.B }
    when (ibus_stb_clear && !RegNext(ibus_stb_clear)) { ibus_stb_latch := false.B }
    core.io.ibus_ack := ibus_ack_in && !RegNext(ibus_ack_in) // Rising edge

    when (core.io.dbus_stb) { dbus_stb_latch := true.B }
    when (dbus_stb_clear && !RegNext(dbus_stb_clear)) { dbus_stb_latch := false.B }
    core.io.dbus_ack := dbus_ack_in && !RegNext(dbus_ack_in) // Rising edge

    // LA[15:0] = ibus_addr
    // LA[31:16] = dbus_addr
    // LA[47:32] = dbus_data_wr
    // LA[48] = ibus_stb
    // LA[49] = dbus_stb
    // LA[50] = dbus_we
    // LA[51] = waiting
    // LA[52] = halted
    // LA[63:53] = reserved (core -> caravel)
    io.la_data_out := RegNext(Cat(
        core.io.halted,
        core.io.waiting,
        core.io.dbus_we,
        dbus_stb_latch,
        ibus_stb_latch,
        core.io.dbus_data_wr,
        core.io.dbus_addr,
        core.io.ibus_addr
    ))

    // LA[95:64] = data_rd / restart_pc
    core.io.ibus_data_rd := la_data_in(95, 64)
    core.io.dbus_data_rd := la_data_in(79, 64)
    core.io.restart_pc := la_data_in(95, 80)

    // LA[96] = ibus_ack
    ibus_ack_in := la_data_in(96)

    // LA[97] = ibus_stb_clear
    ibus_stb_clear := la_data_in(97)

    // LA[98] = dbus_ack
    dbus_ack_in := la_data_in(98)

    // LA[99] = dbus_stb_clear
    dbus_stb_clear := la_data_in(99)

    // LA[100] = restart_core
    core.io.restart_core := la_data_in(100) && !RegNext(la_data_in(100))

    // LA[127:101] = reserved (caravel -> core)
}


