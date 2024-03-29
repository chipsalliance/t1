// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.script

import mainargs.{main, arg, ParserForMethods, Leftover, Flag, TokensReader}
import scala.io.AnsiColor._

object Logger {
  def info(message: String) = {
    println(s"${BOLD}${GREEN}[INFO]${RESET} ${message}")
  }
}

object Main:
  implicit object PathRead extends TokensReader.Simple[os.Path]:
    def shortName = "path"

    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))

  def resolveTestElfPath(
      config: String,
      caseName: String,
      forceX86: Boolean = false
  ): os.Path = {
    val casePath = os.Path(caseName, os.pwd)
    val caseAttrRoot = if (forceX86) "cases-x86" else "cases"

    val finalPath = if (os.exists(casePath)) {
      casePath
    } else {
      val nixArgs = Seq(
        "nix",
        "build",
        "--no-link",
        "--print-out-paths",
        "--no-warn-dirty",
        s".#t1.${config}.${caseAttrRoot}.${caseName}"
      )
      Logger.info(
        s"Running `${nixArgs.mkString(" ")}` to get test case ELF file"
      )
      os.Path(os.proc(nixArgs).call().out.trim()) / "bin" / s"${caseName}.elf"
    }

    Logger.info(s"Using test ELF: ${finalPath}")
    finalPath
  }

  def resolveEmulatorPath(
      config: String,
      emuType: String,
      isTrace: Boolean = false
  ): os.Path = {
    val finalPath = {
      val target = if (isTrace) s"${emuType}.emu-trace" else s"${emuType}.emu"
      val nixArgs = Seq(
        "nix",
        "build",
        "--no-link",
        "--print-out-paths",
        "--no-warn-dirty",
        s".#t1.${config}.${target}"
      )
      Logger.info(s"Running `${nixArgs.mkString(" ")}` to get emulator")
      os.Path(os.proc(nixArgs).call().out.trim()) / "bin" / "emulator"
    }

    Logger.info(s"Using emulator: ${finalPath}")
    finalPath
  }

  def resolveElaborateConfig(
      outputDir: os.Path,
      configName: String
  ): os.Path = {
    if (os.exists(outputDir / "config.json")) {
      os.remove.all(outputDir / "config.json")
    }

    val cfgPath = os.Path(configName, os.pwd)
    val finalCfgPath = if (os.exists(cfgPath)) {
      cfgPath
    } else {
      val nixArgs = Seq(
        "nix",
        "run",
        "--no-warn-dirty",
        ".#t1.configgen",
        "--",
        configName,
        "-t",
        outputDir.toString
      )
      Logger.info(s"Runnning `${nixArgs.mkString(" ")}` to get config")
      os.proc(nixArgs).call()
      outputDir / "config.json"
    }

    finalCfgPath
  }

  def prepareOutputDir(
      outputDir: Option[String],
      outputBaseDir: Option[String],
      config: String,
      emuType: String,
      caseName: String
  ): os.Path = {
    val pathTail = if (os.exists(os.Path(caseName, os.pwd))) {
      // It is hard to canoncalize user specify path, so here we use date time instead
      java.time.LocalDateTime
        .now()
        .format(
          java.time.format.DateTimeFormatter.ofPattern("yy-MM-dd-HH-mm-ss")
        )
    } else {
      caseName
    }

    val path = if (outputDir.isEmpty) {
      if (outputBaseDir.isEmpty) {
        os.pwd / "testrun" / s"${emuType}emu" / config / pathTail
      } else {
        os.Path(outputBaseDir.get, os.pwd) / config / pathTail
      }
    } else {
      os.Path(outputDir.get)
    }

    os.makeDir.all(path)
    path
  }

  def optionals(cond: Boolean, input: Seq[String]): Seq[String] = {
    if (cond) input else Seq()
  }

  // Should be configed via Nix
  @main def ipemu(
      @arg(
        name = "case",
        doc = "name alias for loading test case"
      ) testCase: String,
      @arg(
        name = "dramsim3-cfg",
        short = 'd',
        doc = "enable dramsim3, and specify its configuration file"
      ) dramsim3Config: Option[String],
      @arg(
        name = "frequency",
        short = 'f',
        doc = "frequency for the vector processor (in MHz)"
      ) dramsim3Frequency: Double = 2000,
      @arg(
        name = "config",
        short = 'c',
        doc = "configuration name"
      ) config: String,
      @arg(
        name = "trace",
        short = 't',
        doc = "use emulator with trace support"
      ) trace: Flag,
      @arg(
        name = "verbose",
        short = 'v',
        doc = "set loglevel to debug"
      ) verbose: Flag,
      @arg(
        name = "no-logging",
        doc = "prevent emulator produce log (both console and file)"
      ) noLog: Flag,
      @arg(
        name = "no-file-logging",
        doc = "prevent emulator print log to console"
      ) noFileLog: Flag = Flag(true),
      @arg(
        name = "no-console-logging",
        short = 'q',
        doc = "prevent emulator print log to console"
      ) noConsoleLog: Flag,
      @arg(
        name = "out-dir",
        doc = "path to save wave file and perf result file"
      ) outDir: Option[String],
      @arg(
        name = "base-out-dir",
        doc = "save result files in {base_out_dir}/{config}/{case}/{run_config}"
      ) baseOutDir: Option[String],
      @arg(
        name = "emulator-path",
        doc = "path to emulator"
      ) emulatorPath: Option[String],
      @arg(
        doc = "Force using x86_64 as cross compiling host platform"
      ) forceX86: Boolean = false,
      @arg(
        name = "dump-cycle",
        doc = "Specify the dump starting point"
      ) dumpCycle: Int = 0,
      @arg(
        name = "cosim-timeout",
        doc = "specify timeout cycle for cosim"
      ) cosimTimeout: Int = 400000
  ) = {
    val caseElfPath = resolveTestElfPath(config, testCase, forceX86)
    val outputPath =
      prepareOutputDir(outDir, baseOutDir, config, "ip", testCase)
    val emulator = if (!emulatorPath.isEmpty) {
      val emuPath = os.Path(emulatorPath.get, os.pwd)
      if (!os.exists(emuPath)) {
        sys.error(s"No emulator found at path: ${emulatorPath.get}")
      }
      emuPath
    } else {
      resolveEmulatorPath(config, "ip", trace.value)
    }

    val elaborateConfig =
      ujson.read(os.read(resolveElaborateConfig(outputPath, config)))
    val tck = scala.math.pow(10, 3) / dramsim3Frequency
    val processArgs = Seq(
      emulator.toString(),
      "--elf",
      caseElfPath.toString(),
      "--wave",
      (outputPath / "wave.fst").toString(),
      "--timeout",
      cosimTimeout.toString(),
      "--tck",
      tck.toString(),
      "--perf",
      (outputPath / "perf.txt").toString(),
      "--vlen",
      elaborateConfig.obj("parameter").obj("vLen").toString(),
      "--dlen",
      elaborateConfig.obj("parameter").obj("dLen").toString(),
      "--tl_bank_number",
      elaborateConfig
        .obj("parameter")
        .obj("lsuBankParameters")
        .arr
        .length
        .toString(),
      "--beat_byte",
      elaborateConfig
        .obj("parameter")
        .obj("lsuBankParameters")
        .arr(0)
        .obj("beatbyte")
        .toString(),
      s"--log-path=${outputPath / "emulator.log"}"
    ) ++ optionals(noLog.value, Seq("--no-logging"))
      ++ optionals(noFileLog.value, Seq("--no-file-logging"))
      ++ optionals(noConsoleLog.value, Seq("--no-console-logging"))
      ++ optionals(
        dramsim3Config.isDefined,
        Seq(
          "--dramsim3-result",
          (outputPath / "dramsim3-logs").toString(),
          "--dramsim3-config",
          dramsim3Config.getOrElse("")
        )
      )
      ++ optionals(trace.value, Seq("--dump-from-cycle", dumpCycle.toString()))

    Logger.info(s"Starting IP emulator: `${processArgs.mkString(" ")}`")
    os.proc(processArgs).call()

    if (!noFileLog.value) {
      Logger.info(s"Emulator log save to ${outputPath / "emulator.log"}")
    }

    if (trace.value) {
      Logger.info(s"Trace file save to ${outputPath} / trace.fst")
    }
  }

  @main def subsystememu(
      @arg(
        name = "case",
        short = 'c',
        doc = "name alias for loading test case"
      ) testCase: String,
      @arg(
        name = "config",
        short = 'c',
        doc = "configuration name"
      ) config: String,
      @arg(
        name = "trace",
        short = 't',
        doc = "use emulator with trace support"
      ) trace: Flag,
      @arg(
        name = "trace-file",
        doc = "Output path of the trace file"
      ) traceFile: Option[String],
      @arg(
        doc = "Force using x86_64 as cross compiling host platform"
      ) forceX86: Boolean = false,
      @arg(
        name = "out-dir",
        doc = "path to save wave file and perf result file"
      ) outDir: Option[String],
      @arg(
        name = "base-out-dir",
        doc = "save result files in {base_out_dir}/{config}/{case}/{run_config}"
      ) baseOutDir: Option[String],
      @arg(
        name = "emulator-path",
        doc = "path to emulator"
      ) emulatorPath: Option[String]
  ) = {
    val caseElfPath = resolveTestElfPath(config, testCase, forceX86)
    val outputPath =
      prepareOutputDir(outDir, baseOutDir, config, "subsystem", testCase)
    val emulator = if (!emulatorPath.isEmpty) {
      val emuPath = os.Path(emulatorPath.get, os.pwd)
      if (!os.exists(emuPath)) {
        sys.error(s"No emulator found at path: ${emulatorPath.get}")
      }
      emuPath
    } else {
      resolveEmulatorPath(config, "subsystem", trace.value)
    }

    val emuArgs =
      Seq(s"+init_file=${caseElfPath}") ++ optionals(
        trace.value,
        Seq(s"+trace_file=${
            if (traceFile.isDefined) os.Path(traceFile.get, os.pwd)
            else outputPath / "trace.fst"
          }")
      )
    os.proc(emuArgs).call()

    if (trace.value) {
      Logger.info(s"Trace file save to ${outputPath} / trace.fst")
    }
  }

  @main def listConfig() = {
    os.proc(
      Seq(
        "nix",
        "run",
        "--no-warn-dirty",
        ".#t1.configgen",
        "--",
        "listConfigs"
      )
    ).call(cwd = os.pwd, stdout = os.Inherit, stderr = os.Inherit)
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
