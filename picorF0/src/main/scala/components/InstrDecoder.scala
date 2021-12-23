package components

import chisel3._
import chisel3.experimental.ChiselEnum
import utils.MathUtils.BinStrToInt

object CCMode extends ChiselEnum {
    // ALU = use CC values from ALU
    // WB = use ZN from write-back value, C = 0, V = 0
    // NONE = don't change CC flags
    val ALU, WB, NONE = Value
}

class Instruction extends Bundle {
    val rs1 = UInt(3.W)
    val rs2 = UInt(3.W)
    val rd = UInt(3.W)
    val imm = UInt(16.W)

    val cc_mode = CCMode()
    val alu_op = ALUOp()
    val bra_mask = new ZCNV // Which CC bits to branch on

    val has_jump = Bool()
    val has_stop = Bool()
    val has_branch = Bool()
    val has_mem = Bool()
    val has_wb = Bool()
    val has_imm = Bool()

    val is_valid = Bool()
}

object OpCode {
    val ADD  = b"0000 000"
    val ADDI = b"0011 000"
    val AND  = b"1001 000"
    val BRA  = b"1111 100"
    val BRC  = b"1010 100"
    val BRN  = b"1001 100"
    val BRNZ = b"1101 100"
    val BRV  = b"1011 100"
    val BRZ  = b"1100 100"
    // LI = ADDI
    val LW   = b"0010 100"
    val MV   = b"0010 000"
    val NOT  = b"1000 000"
    val OR   = b"1010 000"
    val SLL  = b"1100 000"
    val SLLI = b"1100 001"
    val SLT  = b"0101 000"
    val SLTI = b"0101 001"
    val SRA  = b"1111 000"
    val SRAI = b"1111 001"
    val SRL  = b"1110 000"
    val SRLI = b"1110 001"
    val STOP = b"1111 111"
    val SUB  = b"0001 000"
    val SW   = b"0011 100"
    val XOR  = b"1011 000"
}

class InstrDecoder extends Module {
    val io = IO(new Bundle {
        val instr   = Input(UInt(16.W))
        val imm     = Input(UInt(16.W))

        val decoded = Output(new Instruction)
    })

    val instructions = Map(
        // Opcode       ALU op           CC mode      BRA mask  jmp  stp  bra  mem  wb  imm
        OpCode.ADD  -> (Some(ALUOp.ADD), CCMode.ALU,  b"0000",  0,   0,   0,   0,   1,  0  ), // ADD
        OpCode.ADDI -> (Some(ALUOp.ADD), CCMode.ALU,  b"0000",  0,   0,   0,   0,   1,  1  ), // ADDI
        OpCode.AND  -> (Some(ALUOp.AND), CCMode.ALU,  b"0000",  0,   0,   0,   0,   1,  0  ), // AND
        OpCode.BRA  -> (None,            CCMode.NONE, b"0000",  1,   0,   0,   0,   0,  1  ), // BRA
        OpCode.BRC  -> (None,            CCMode.NONE, b"0100",  0,   0,   1,   0,   0,  1  ), // BRC
        OpCode.BRN  -> (None,            CCMode.NONE, b"0010",  0,   0,   1,   0,   0,  1  ), // BRN
        OpCode.BRNZ -> (None,            CCMode.NONE, b"1010",  0,   0,   1,   0,   0,  1  ), // BRNZ
        OpCode.BRV  -> (None,            CCMode.NONE, b"0001",  0,   0,   1,   0,   0,  1  ), // BRV
        OpCode.BRZ  -> (None,            CCMode.NONE, b"1000",  0,   0,   1,   0,   0,  1  ), // BRZ
        // LI does not have its own encoding, it uses ADDI with rs1 = 0
        OpCode.LW   -> (Some(ALUOp.ADD), CCMode.WB,   b"0000",  0,   0,   0,   1,   1,  1  ), // LW
        OpCode.MV   -> (Some(ALUOp.ADD), CCMode.NONE, b"0000",  0,   0,   0,   0,   1,  0  ), // MV (ADD, rs2 = 0)
        OpCode.NOT  -> (Some(ALUOp.NOT), CCMode.ALU,  b"0000",  0,   0,   0,   0,   1,  0  ), // NOT
        OpCode.OR   -> (Some(ALUOp.OR),  CCMode.ALU,  b"0000",  0,   0,   0,   0,   1,  0  ), // OR
        OpCode.SLL  -> (Some(ALUOp.SLL), CCMode.ALU,  b"0000",  0,   0,   0,   0,   1,  0  ), // SLL
        OpCode.SLLI -> (Some(ALUOp.SLL), CCMode.ALU,  b"0000",  0,   0,   0,   0,   1,  1  ), // SLLI
        OpCode.SLT  -> (Some(ALUOp.SLT), CCMode.ALU,  b"0000",  0,   0,   0,   0,   1,  0  ), // SLT
        OpCode.SLTI -> (Some(ALUOp.SLT), CCMode.ALU,  b"0000",  0,   0,   0,   0,   1,  1  ), // SLTI
        OpCode.SRA  -> (Some(ALUOp.SRA), CCMode.ALU,  b"0000",  0,   0,   0,   0,   1,  0  ), // SRA
        OpCode.SRAI -> (Some(ALUOp.SRA), CCMode.ALU,  b"0000",  0,   0,   0,   0,   1,  1  ), // SRAI
        OpCode.SRL  -> (Some(ALUOp.SRL), CCMode.ALU,  b"0000",  0,   0,   0,   0,   1,  0  ), // SRL
        OpCode.SRLI -> (Some(ALUOp.SRL), CCMode.ALU,  b"0000",  0,   0,   0,   0,   1,  1  ), // SRLI
        OpCode.STOP -> (None,            CCMode.NONE, b"0000",  0,   1,   0,   0,   0,  0  ), // STOP
        OpCode.SUB  -> (Some(ALUOp.SUB), CCMode.ALU,  b"0000",  0,   0,   0,   0,   1,  0  ), // SUB
        OpCode.SW   -> (Some(ALUOp.ADD), CCMode.NONE, b"0000",  0,   0,   0,   1,   0,  1  ), // SW
        OpCode.XOR  -> (Some(ALUOp.XOR), CCMode.ALU,  b"0000",  0,   0,   0,   0,   1,  0  ), // XOR
    )

    val opcode = io.instr(15, 9)

    // Operands
    io.decoded.imm := io.imm
    io.decoded.rd := io.instr(8, 6) // When instruction has no write-back it's always zero - useful feature of the ISA
    io.decoded.rs1 := io.instr(5, 3)
    io.decoded.rs2 := io.instr(2, 0)

    // Default values
    io.decoded.cc_mode := CCMode.NONE
    io.decoded.alu_op := ALUOp.ADD
    io.decoded.bra_mask.z := false.B
    io.decoded.bra_mask.c := false.B
    io.decoded.bra_mask.n := false.B
    io.decoded.bra_mask.v := false.B
    io.decoded.has_jump := false.B
    io.decoded.has_stop := false.B
    io.decoded.has_branch := false.B
    io.decoded.has_mem := false.B
    io.decoded.has_wb := false.B
    io.decoded.has_imm := false.B
    io.decoded.is_valid := false.B

    for ((inst_opcode, (aluop, ccmode, bramask, jmp, stp, bra, mem, wb, imm)) <- instructions) {
        when (inst_opcode.U === opcode) {
            io.decoded.cc_mode := ccmode
            io.decoded.alu_op := aluop getOrElse ALUOp.ADD

            io.decoded.bra_mask.z := ((bramask & b"1000") != 0).B
            io.decoded.bra_mask.c := ((bramask & b"0100") != 0).B
            io.decoded.bra_mask.n := ((bramask & b"0010") != 0).B
            io.decoded.bra_mask.v := ((bramask & b"0001") != 0).B

            io.decoded.has_jump := jmp.B
            io.decoded.has_stop := stp.B
            io.decoded.has_branch := bra.B
            io.decoded.has_mem := mem.B
            io.decoded.has_wb := wb.B
            io.decoded.has_imm := imm.B

            io.decoded.is_valid := true.B
        }
    }

}