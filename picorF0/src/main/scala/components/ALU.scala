package components

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

object ALUOp extends ChiselEnum {
    val ADD, AND, NOT, OR, SLL, SLT, SRA, SRL, SUB, XOR = Value
}

class ZCNV extends Bundle {
    val z = Bool()
    val c = Bool()
    val n = Bool()
    val v = Bool()
}

class ALU extends Module {
    val io = IO(new Bundle {
        val in1 = Input(UInt(16.W))
        val in2 = Input(UInt(16.W))
        val op  = Input(ALUOp())

        val out = Output(UInt(16.W))
        val cc  = Output(new ZCNV)
    })
    
    val in1 = Wire(UInt(17.W))
    val in2 = Wire(UInt(17.W))
    in1 := io.in1
    in2 := io.in2

    val result = Wire(UInt(17.W))
    io.out := result(15, 0)

    val in1_minus_in2 = Wire(UInt(17.W))
    in1_minus_in2 := in1 - in2

    result := 0.U

    // Compute result
    switch (io.op) {
        is (ALUOp.ADD) { result := in1 + in2 }
        is (ALUOp.AND) { result := in1 & in2 }
        is (ALUOp.NOT) { result := ~in1 }
        is (ALUOp.OR)  { result := in1 | in2 }
        is (ALUOp.SLL) { result := in1 << in2(3, 0) }
        is (ALUOp.SLT) { result := Cat(0.U(15.W), (in1(15, 0).asSInt() < in2(15, 0).asSInt())(0)) }
        is (ALUOp.SRA) { result := (in1(15, 0).asSInt() >> in2(3, 0)).asUInt()(15,0) }
        is (ALUOp.SRL) { result := in1 >> in2(3, 0) }
        is (ALUOp.SUB) { result := in1_minus_in2 }
        is (ALUOp.XOR) { result := in1 ^ in2 }
    }

    // Compute flags
    when (io.op === ALUOp.ADD) {
        io.cc.z := (io.out === 0.U)
        io.cc.c := result(16)
        io.cc.n := result(15)
        io.cc.v := (in1(15) && in2(15) && !io.out(15)) ||
          (!in1(15) && !in2(15) && io.out(15))
    }
    .elsewhen (io.op === ALUOp.SUB) {
        io.cc.z := (io.out === 0.U)
        io.cc.c := (in2 >= in1) // Weird but works
        io.cc.n := result(15)
        io.cc.v := (in1(15) && !in2(15) && !io.out(15)) ||
          (!in1(15) && in2(15) && io.out(15))
    }
    .elsewhen (io.op === ALUOp.SLT) {
        io.cc.z := (in1_minus_in2 === 0.U)
        io.cc.c := (in2 >= in1)
        io.cc.n := in1_minus_in2(15)
        io.cc.v := (in1(15) && !in2(15) && !in1_minus_in2(15)) ||
          (!in1(15) && in2(15) && in1_minus_in2(15))
    }
    .otherwise {
        io.cc.z := (io.out === 0.U)
        io.cc.c := false.B
        io.cc.n := result(15)
        io.cc.v := false.B
    }
}
