package core

import chisel3._
import components._
import _root_.core.ControlPath._

class Core(val INIT_PC: Int = 0) extends Module {
    val io = IO(new Bundle {
        val ibus_addr = Output(UInt(16.W))
        val ibus_data_rd = Input(UInt(32.W))
        val ibus_stb = Output(Bool())
        val ibus_ack = Input(Bool())

        val dbus_addr = Output(UInt(16.W))
        val dbus_we = Output(Bool())
        val dbus_data_rd = Input(UInt(16.W))
        val dbus_data_wr = Output(UInt(16.W))
        val dbus_stb = Output(Bool())
        val dbus_ack = Input(Bool())

        val restart_pc = Input(UInt(16.W))
        val restart_core = Input(Bool())

        val waiting = Output(Bool())
        val halted = Output(Bool())
    })

    val dpath = Module(new Datapath(INIT_PC))
    dpath.io.ibus_addr <> io.ibus_addr
    dpath.io.ibus_data_rd <> io.ibus_data_rd
    dpath.io.ibus_stb <> io.ibus_stb
    dpath.io.ibus_ack <> io.ibus_ack

    dpath.io.dbus_addr <> io.dbus_addr
    dpath.io.dbus_we <> io.dbus_we
    dpath.io.dbus_data_rd <> io.dbus_data_rd
    dpath.io.dbus_data_wr <> io.dbus_data_wr
    dpath.io.dbus_stb <> io.dbus_stb
    dpath.io.dbus_ack <> io.dbus_ack

    dpath.io.restart_pc <> io.restart_pc

    val cpath = Module(new ControlPath)
    cpath.io.instr <> dpath.io.instr

    cpath.io.issue_if <> dpath.io.issue_if
    cpath.io.if_done <> dpath.io.if_done
    cpath.io.issue_mem <> dpath.io.issue_mem
    cpath.io.mem_we <> dpath.io.mem_we
    cpath.io.mem_done <> dpath.io.mem_done

    cpath.io.waiting <> io.waiting
    cpath.io.halted <> io.halted
    cpath.io.state <> dpath.io.state

    cpath.io.restart_core <> io.restart_core
    cpath.io.restart <> dpath.io.restart

}


