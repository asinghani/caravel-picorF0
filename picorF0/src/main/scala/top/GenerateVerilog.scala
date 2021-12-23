package top

import chisel3.stage.ChiselStage
import top.CoreTop

object GenerateVerilog extends App {
    (new ChiselStage).emitVerilog(new CoreTop)
}
