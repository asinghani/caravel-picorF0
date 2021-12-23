package core

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.{switch, is}
import components.Instruction

import ControlPath._

object ControlPath {
    object S extends ChiselEnum {
        val IF, IF_WAIT, DECODE, EXEC, MEM, MEM_WAIT, WB, HALT = Value
    }
}

class ControlPath extends Module {

    val io = IO(new Bundle {
        val instr = Input(new Instruction)

        val issue_if = Output(Bool())
        val if_done = Input(Bool())

        val issue_mem = Output(Bool())
        val mem_we = Output(Bool())
        val mem_done = Input(Bool())

        val waiting = Output(Bool())
        val halted = Output(Bool())

        val state = Output(S())

        val restart_core = Input(Bool())
        val restart = Output(Bool()) // to datapath
    })

    val restart_reg = RegInit(false.B)
    io.restart := io.restart_core || restart_reg

    val state = RegInit(S.IF)
    io.state := state

    val is_store_instr = (io.instr.has_mem && !io.instr.has_wb)

    io.issue_if := (state === S.IF)
    io.issue_mem := (state === S.MEM)
    io.mem_we := is_store_instr

    io.waiting := (state === S.IF_WAIT || state === S.MEM_WAIT)
    io.halted := (state === S.HALT)

    // State-transition logic
    switch (state) {
        is (S.IF) {
            state := S.IF_WAIT
        }
        is (S.IF_WAIT) {
            state := Mux(io.if_done, S.DECODE, S.IF_WAIT)
        }
        is (S.DECODE) {
            state := S.EXEC
        }
        is (S.EXEC) {
            when (io.instr.has_stop) {
                state := S.HALT
            }.elsewhen (io.instr.has_jump || io.instr.has_branch) {
                state := S.IF
            }.elsewhen (io.instr.has_mem) {
                state := S.MEM
            }.otherwise {
                state := S.WB
            }
        }
        is (S.MEM) {
            state := S.MEM_WAIT
        }
        is (S.MEM_WAIT) {
            state := Mux(io.mem_done,
                Mux(is_store_instr, S.IF, S.WB),
                S.MEM_WAIT)
        }
        is (S.WB) {
            state := S.IF
        }
        is (S.HALT) {
            state := S.HALT
        }
    }

    when (io.restart_core) {
        restart_reg := true.B
    }

    when (restart_reg && (state =/= S.IF) && (state =/= S.IF_WAIT) && (state =/= S.MEM) && (state =/= S.MEM_WAIT)) {
        restart_reg := false.B
        state := S.IF
    }

}