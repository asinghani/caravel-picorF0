package core

import chisel3._
import chisel3.util.{is, switch}
import components.{ALU, CCMode, InstrDecoder, Instruction, RegFile, ZCNV}
import ControlPath._

class Datapath(val INIT_PC: Int = 0) extends Module {
    val io = IO(new Bundle {
        val instr = Output(new Instruction)

        val issue_if = Input(Bool())
        val if_done = Output(Bool())
        val issue_mem = Input(Bool())
        val mem_we = Input(Bool())
        val mem_done = Output(Bool())
        val state = Input(S())

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
        val restart = Input(Bool())
    })

    val pc_reg = RegInit(INIT_PC.U(16.W))
    val ifetch_reg = RegInit(0.U(32.W))
    val instr_reg = RegInit(0.U.asTypeOf(new Instruction))
    val rs1_reg = RegInit(0.U(16.W))
    val rs2_reg = RegInit(0.U(16.W))
    val cc_reg = RegInit(0.U(4.W).asTypeOf(new ZCNV))

    val result_reg = RegInit(0.U(16.W))
    val result_cc_reg = RegInit(0.U(4.W).asTypeOf(new ZCNV))

    io.instr := instr_reg

    // Next PC
    when (io.restart) {
        // When rising edge of restart, set the PC to restart PC
        // otherwise don't let the PC change
        when (!RegNext(io.restart)) {
            pc_reg := io.restart_pc
        }
    } .elsewhen (io.state === S.EXEC) {
        pc_reg := pc_reg + Mux(io.instr.has_imm, 4.U, 2.U)

        val take_branch = (io.instr.bra_mask.asUInt() & cc_reg.asUInt()).orR()
        when (io.instr.has_jump || (io.instr.has_branch && take_branch)) {
            pc_reg := io.instr.imm
        }
    }

    // IFetch
    io.ibus_addr := pc_reg
    io.ibus_stb := io.issue_if
    io.if_done := io.ibus_ack
    when (io.ibus_ack) { ifetch_reg := io.ibus_data_rd }

    // Decoder
    val decoder = Module(new InstrDecoder)
    decoder.io.instr := ifetch_reg(31, 16)
    decoder.io.imm := ifetch_reg(15, 0)
    var instr_decoded = decoder.io.decoded
    when (io.state === S.DECODE) {
        instr_reg := instr_decoded
    }

    // ALU
    val alu = Module(new ALU)
    alu.io.in1 := rs1_reg
    alu.io.in2 := Mux(instr_reg.has_imm, instr_reg.imm, rs2_reg)
    alu.io.op := instr_reg.alu_op
    when (io.state === S.EXEC) {
        result_reg := alu.io.out
        result_cc_reg := alu.io.cc
    }

    // Data memory
    io.dbus_addr := result_reg
    io.dbus_we := io.mem_we
    io.dbus_data_wr := rs2_reg
    io.dbus_stb := io.issue_mem
    io.mem_done := io.dbus_ack
    when (io.state === S.MEM_WAIT && !io.mem_we && io.dbus_ack) {
        result_reg := io.dbus_data_rd
    }

    // Register file (in decode / WB stages)
    // RF read happens in decode stage so it should use instr_decoded instead of instr_reg
    val regfile = Module(new RegFile)
    regfile.io.rs1 := instr_decoded.rs1
    regfile.io.rs2 := instr_decoded.rs2
    regfile.io.rd := instr_reg.rd

    regfile.io.rd_en := false.B
    regfile.io.cc_en := false.B

    regfile.io.cc_mask.z := true.B
    regfile.io.cc_mask.c := true.B
    regfile.io.cc_mask.n := true.B
    regfile.io.cc_mask.v := true.B

    regfile.io.rd_dat := result_reg

    when (io.state === S.DECODE) {
        rs1_reg := regfile.io.rs1_dat
        rs2_reg := regfile.io.rs2_dat
        cc_reg := regfile.io.cc_out
    }

    when (io.state === S.WB) {
        regfile.io.rd_en := true.B
        regfile.io.cc_en := (instr_reg.cc_mode =/= CCMode.NONE)
    }

    when (instr_reg.cc_mode === CCMode.ALU) {
        regfile.io.cc_in.z := result_cc_reg.z
        regfile.io.cc_in.c := result_cc_reg.c
        regfile.io.cc_in.n := result_cc_reg.n
        regfile.io.cc_in.v := result_cc_reg.v
    } .otherwise {
        regfile.io.cc_in.z := (result_reg === 0.U)
        regfile.io.cc_in.c := false.B
        regfile.io.cc_in.n := result_reg(15)
        regfile.io.cc_in.v := false.B
    }

}