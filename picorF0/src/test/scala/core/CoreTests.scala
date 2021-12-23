package core

import chisel3._
import chisel3.tester._
import components.OpCode._
import org.scalatest.FreeSpec
import utils.MathUtils.BinStrToInt

import scala.io.Source
import scala.util.Random
import scala.util.control.Breaks._

class CoreTests extends FreeSpec with ChiselScalatestTester {

    def asm(opcode: Int, rd: Int = 0, rs1: Int = 0, rs2: Int = 0): Int =
        ((opcode & b"1111 111") << 9) |
            ((rd  & b"111") << 6) |
            ((rs1 & b"111") << 3) |
            ((rs2 & b"111") << 0)

    // `program` is an array of 16-bit memory words
    def handleIFetch(dut: Core, program: Array[Int], verbose: Boolean = false): Unit = {
        val prog = program :+ 0 // Allows fetching last instruction in program if it is a short-style instruction

        breakable {
            while (true) {
                while (!dut.io.ibus_stb.peek().litToBoolean) {
                    dut.clock.step()
                    if (dut.io.halted.peek().litToBoolean) break
                }

                val addr = dut.io.ibus_addr.peek().litValue().toInt
                val result = (BigInt(prog(addr >> 1)) << 16) | BigInt(prog((addr >> 1) + 1)) // 32-bit fetch

                for (_ <- 0 until (Random.nextInt(3) + 2)) { // 2-5 cycle latency
                    dut.clock.step()
                    dut.io.ibus_stb.expect(false.B)
                }

                dut.io.ibus_data_rd.poke(result.U)
                dut.io.ibus_ack.poke(true.B)
                dut.clock.step()
                dut.io.ibus_ack.poke(false.B)

                if (verbose) {
                    println(f"IF[0x$addr%04X] => 0x$result%08X")
                }
            }
        }
    }

    // `data` is 64K-element array of 16-bit memory words
    // it will be modified upon stores
    def handleDMem(dut: Core, data: collection.mutable.Map[Int, Int], verbose: Boolean = false): Unit = {

        breakable {
            while (true) {
                while (!dut.io.dbus_stb.peek().litToBoolean) {
                    dut.clock.step()
                    if (dut.io.halted.peek().litToBoolean) break
                }

                val addr = (dut.io.dbus_addr.peek().litValue().toInt >> 1) << 1
                var result = 0
                if (dut.io.dbus_we.peek().litToBoolean) {
                    data.put(addr, dut.io.dbus_data_wr.peek().litValue().toInt)
                    if (verbose) {
                        println(f"M[0x$addr%04X] <= 0x${dut.io.dbus_data_wr.peek().litValue().toInt}%08X")
                    }

                } else {
                    result = data.getOrElse(addr, 0)

                    if (verbose) {
                        println(f"M[0x$addr%04X] => 0x$result%08X")
                    }
                }

                for (_ <- 0 until (Random.nextInt(3) + 2)) { // 2-5 cycle latency
                    dut.clock.step()
                    dut.io.dbus_stb.expect(false.B)
                }

                dut.io.dbus_data_rd.poke(result.U)
                dut.io.dbus_ack.poke(true.B)
                dut.clock.step()
                dut.io.dbus_ack.poke(false.B)
            }
        }
    }

    def waitForHalt(dut: Core): Unit = {
        while (!dut.io.halted.peek().litToBoolean) dut.clock.step()
    }

    "should fetch instructions" in {
        test(new Core(0x0000)) { dut =>
            val rand = new Random

            dut.io.ibus_ack.poke(false.B)
            dut.io.dbus_ack.poke(false.B)

            dut.io.restart_core.poke(false.B)

            dut.io.waiting.expect(false.B)
            dut.io.halted.expect(false.B)

            // Expect instruction fetch
            dut.io.ibus_stb.expect(true.B)
            dut.io.ibus_addr.expect(0.U)

            for (_ <- 0 until (Random.nextInt(10) + 2)) {
                dut.clock.step()
                dut.io.ibus_stb.expect(false.B)
            }

            dut.io.ibus_ack.poke(true.B)
            dut.io.ibus_data_rd.poke(0.U) // ADD r0, r0, r0

            var i = 0
            while (!dut.io.ibus_stb.peek().litToBoolean) {
                dut.clock.step()
                i += 1
            }

            println(s"ADD r0, r0, r0 took $i cycles")
        }
    }

    "should issue loads" in {
        test(new Core(0x0000)) { dut =>
            dut.io.ibus_ack.poke(false.B)
            dut.io.dbus_ack.poke(false.B)
            dut.io.restart_core.poke(false.B)

            dut.io.waiting.expect(false.B)
            dut.io.halted.expect(false.B)

            val program = Array (
                b"0010 100 001 000 000", // LW r1, r0
                0x1234,
                b"1111 111 000 000 000", // STOP
            )

            fork { handleIFetch(dut, program, verbose = true) } .fork {

                while(!dut.io.dbus_stb.peek().litToBoolean) dut.clock.step(1)
                println(f"LOAD, 0x${dut.io.dbus_addr.peek().litValue().toInt}%04X")

                dut.clock.step(2)
                dut.io.dbus_data_rd.poke(0.U)
                dut.io.dbus_ack.poke(true.B)
                dut.clock.step(1)
                dut.io.dbus_ack.poke(false.B)

            }.join()
        }
    }

    "should issue loads and stores" in {
        test(new Core(0x0000)) { dut =>
            dut.io.ibus_ack.poke(false.B)
            dut.io.dbus_ack.poke(false.B)
            dut.io.restart_core.poke(false.B)

            dut.io.waiting.expect(false.B)
            dut.io.halted.expect(false.B)

            val program = Array (
                asm(LW, rd = 1, rs1 = 0),
                0x1000,
                asm(SW, rs1 = 0, rs2 = 1),
                0x1002,
                asm(STOP)
            )

            val dmem = collection.mutable.Map (
                0x1000 -> 0xABCD
            )

            fork { handleIFetch(dut, program, verbose = true) }
                .fork { handleDMem(dut, dmem, verbose = true) } .fork {

                waitForHalt(dut)
                assert(dmem(0x1002) == dmem(0x1000))

            }.join()
        }
    }

    "should pass a test suite" in {
        test(new Core(0x0000)) { dut =>
            dut.io.ibus_ack.poke(false.B)
            dut.io.dbus_ack.poke(false.B)
            dut.io.restart_core.poke(false.B)

            dut.io.waiting.expect(false.B)
            dut.io.halted.expect(false.B)

            val memfile = Source.fromFile("testsuite.hex")
            val program = memfile.getLines.map(x => Integer.parseInt(x, 16)).toArray
            memfile.close()

            val dmem = collection.mutable.Map (
                0x1000 -> 0xABCD,
                0x9000 -> 0x0000
            )

            fork { handleIFetch(dut, program, verbose = true) }
                .fork { handleDMem(dut, dmem, verbose = true) } .fork {

                waitForHalt(dut)

                if (dmem(0x9000) == 0xABCD) {
                    println("All tests passed")
                } else if (dmem(0x9000) == 0x0001) {
                    println(s"Failed at test ${dmem(0x9002)}")
                } else {
                    println("Failure: halted without reporting success or failure")
                }

            }.join()
        }
    }

    /*"Gcd should calculate proper greatest common denominator" in {
        test(new DecoupledGcd(16)) { dut =>
            dut.input.initSource()
            dut.input.setSourceClock(dut.clock)
            dut.output.initSink()
            dut.output.setSinkClock(dut.clock)

            val testValues = for { x <- 0 to 10; y <- 0 to 10} yield (x, y)
            val inputSeq = testValues.map { case (x, y) => (new GcdInputBundle(16)).Lit(_.value1 -> x.U, _.value2 -> y.U) }
            val resultSeq = testValues.map { case (x, y) =>
                (new GcdOutputBundle(16)).Lit(_.value1 -> x.U, _.value2 -> y.U, _.gcd -> BigInt(x).gcd(BigInt(y)).U)
            }

            fork {
                // push inputs into the calculator, stall for 11 cycles one third of the way
                val (seq1, seq2) = inputSeq.splitAt(resultSeq.length / 3)
                dut.input.enqueueSeq(seq1)
                dut.clock.step(11)
                dut.input.enqueueSeq(seq2)
            }.fork {
                // retrieve computations from the calculator, stall for 10 cycles one half of the way
                val (seq1, seq2) = resultSeq.splitAt(resultSeq.length / 2)
                dut.output.expectDequeueSeq(seq1)
                dut.clock.step(10)
                dut.output.expectDequeueSeq(seq2)
            }.join()

        }
    }*/
}
