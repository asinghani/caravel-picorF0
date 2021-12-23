package components

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

class RegFile extends Module {
    val io = IO(new Bundle {
        val rs1     = Input(UInt(3.W))
        val rs2     = Input(UInt(3.W))

        val rs1_dat = Output(UInt(16.W))
        val rs2_dat = Output(UInt(16.W))

        val rd      = Input(UInt(3.W))
        val rd_dat  = Input(UInt(16.W))
        val rd_en   = Input(Bool())

        val cc_in   = Input(new ZCNV)
        val cc_mask = Input(new ZCNV)
        val cc_en   = Input(Bool())

        val cc_out  = Output(new ZCNV)
    })

    val rf = Reg(Vec(8, UInt(16.W)))

    // Handle reads
    io.rs1_dat := Mux(io.rs1 === 0.U, 0.U, rf(io.rs1))
    io.rs2_dat := Mux(io.rs2 === 0.U, 0.U, rf(io.rs2))

    // Handle writes
    when (io.rd_en) { rf(io.rd) := io.rd_dat }

    // Handle ZCNV
    val cc_reg = Reg(new ZCNV)
    io.cc_out := cc_reg

    when (io.cc_en) {
        when (io.cc_mask.z) { cc_reg.z := io.cc_in.z }
        when (io.cc_mask.c) { cc_reg.c := io.cc_in.c }
        when (io.cc_mask.n) { cc_reg.n := io.cc_in.n }
        when (io.cc_mask.v) { cc_reg.v := io.cc_in.v }
    }
}
