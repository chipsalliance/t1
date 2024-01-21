// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.script

import mainargs.{main, arg, ParserForMethods, Leftover, Flag, TokensReader}

object Main:
  implicit object PathRead extends TokensReader.Simple[os.Path]:
    def shortName = "path"

    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))

  // Should be configed via Nix.
  def t1BaseDir = sys.env("T1_ROOT_DIR").map(os.Path(_))
  @main def ipemu(
    @arg(name = "case", doc = "name alias for loading test case") testCase: String,
    @arg(name = "dramsim3-cfg", short = 'd', doc = "enable dramsim3, and specify its configuration file") dramsim3Config: String,
    @arg(name = "frequency", short = 'f', doc = "frequency for the vector processor (in MHz)") dramsim3Frequency: Double = 2000,
    @arg(name = "config", short = 'c', doc = "configuration name") config: String,
    @arg(name = "trace", short = 't', doc = "use emulator with trace support") trace: Flag,
    @arg(name = "run-config", short = 'r', doc = "run configuration name") runConfig: String,
    @arg(name = "verbose", short = 'v', doc = "set loglevel to debug") verbose: Flag,
    @arg(name = "no-log", doc = "prevent emulator produce log (both console and file)") noLog: Flag,
    @arg(name = "no-console-log", short = 'q', doc = "prevent emulator print log to console") noConsoleLog: Flag,
    @arg(name = "out-dir", doc = "path to save wave file and perf result file") outDir: os.Path = os.pwd,
    @arg(name = "base-out-dir", doc = "save result files in {base_out_dir}/{config}/{case}/{run_config}") baseOutDir: os.Path = os.pwd,
    @arg(name = "emulator-path", doc = "path to emulator") emulatorPath: os.Path = os.pwd,
                ) = println("TODO")
  @main def subsystememu(
    @arg(short = 'c', doc = "name alias for loading test case") testCase: String,
                   ) = println("TODO")
  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)