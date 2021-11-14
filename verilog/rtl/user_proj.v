// SPDX-FileCopyrightText: 2021 Anish Singhani
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// SPDX-License-Identifier: Apache-2.0

`default_nettype none

`include "CoreTop.v"
`include "game.v"

module user_proj #(
    parameter BITS = 32
)(
`ifdef USE_POWER_PINS
    inout vccd1,	// User area 1 1.8V supply
    inout vssd1,	// User area 1 digital ground
`endif

    // Wishbone Slave ports (WB MI A)
    input wb_clk_i,
    input wb_rst_i,
    input wbs_stb_i,
    input wbs_cyc_i,
    input wbs_we_i,
    input [3:0] wbs_sel_i,
    input [31:0] wbs_dat_i,
    input [31:0] wbs_adr_i,
    output wbs_ack_o,
    output [31:0] wbs_dat_o,

    // Logic Analyzer Signals
    input  [127:0] la_data_in,
    output [127:0] la_data_out,
    input  [127:0] la_oenb,

    // IOs
    input  [`MPRJ_IO_PADS-1:0] io_in,
    output [`MPRJ_IO_PADS-1:0] io_out,
    output [`MPRJ_IO_PADS-1:0] io_oeb,

    // IRQ
    output [2:0] irq
);

    wire [`MPRJ_IO_PADS-1:0] io_in;
    wire [`MPRJ_IO_PADS-1:0] io_out;
    wire [`MPRJ_IO_PADS-1:0] io_oeb;

    // IO
    assign io_out[`MPRJ_IO_PADS-1:22] = '0;
    assign io_out[7:0] = '0;
    assign io_oeb = '0;

    // Buttons: 27, 26, 25, 24, 23, 22
    assign io_oeb[27:22] = '1;

    // IRQ
    assign irq = 3'b000;	// Unused

    game_wrapper pong (
        .VGA_R3(io_out[19]),
        .VGA_R2(io_out[18]),
        .VGA_R1(io_out[17]),
        .VGA_R0(io_out[16]),

        .VGA_G3(io_out[15]),
        .VGA_G2(io_out[14]),
        .VGA_G1(io_out[13]),
        .VGA_G0(io_out[12]),

        .VGA_B3(io_out[11]),
        .VGA_B2(io_out[10]),
        .VGA_B1(io_out[9]),
        .VGA_B0(io_out[8]),

        .VGA_VS(io_out[20]),
        .VGA_HS(io_out[21]),

        .btn_serve(io_in[27]),
        .btn_rst(io_in[26]),
        .btn0_n(~io_in[25]),
        .btn1_n(~io_in[24]),
        .btn2_n(~io_in[23]),
        .btn3_n(~io_in[22]),

        .clk_25mhz(wb_clk_i)
    );

    CoreTop core (
        .clock(wb_clk_i),
        .reset(wb_rst_i),
        .io_la_data_in(la_data_in),
        .io_la_data_out(la_data_out),
        .io_la_oenb(la_oenb)
    );



endmodule

`default_nettype wire
