// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.script

import mainargs.{main, arg, ParserForMethods, Leftover, Flag, TokensReader}
import scala.io.AnsiColor._

object Logger {
  val level = sys.env.getOrElse("LOG_LEVEL", "INFO") match
    case "TRACE" | "trace" => 0
    case "ERROR" | "error" => 1
    case "INFO" | "info"   => 2
    case _                 => 4

  def info(message: String) =
    if level <= 2 then println(s"${BOLD}${GREEN}[INFO]${RESET} ${message}")

  def trace(message: String) =
    if level <= 0 then println(s"${BOLD}${GREEN}[TRACE]${RESET} ${message}")

  def error(message: String) =
    if level <= 2 then println(s"${BOLD}${RED}[ERROR]${RESET} ${message}")

  def fatal(message: String) =
    println(s"${BOLD}${RED}[FATAL]${RESET} ${message}")
    sys.exit(1)
}

object Main:
  implicit object PathRead extends TokensReader.Simple[os.Path]:
    def shortName = "path"

    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))

  def nixResolvePath(attr: String): String =
    os.proc(
      "nix",
      "build",
      "--no-link",
      "--no-warn-dirty",
      "--print-out-paths",
      attr
    ).call()
      .out
      .trim()

  def resolveTestElfPath(
      config: String,
      caseName: String,
      forceX86: Boolean = false
  ): os.Path =
    val casePath = os.Path(caseName, os.pwd)
    val caseAttrRoot = if (forceX86) then "cases-x86" else "cases"

    val finalPath =
      if (os.exists(casePath)) then casePath
      else
        val nixArgs = Seq(
          "nix",
          "build",
          "--no-link",
          "--print-out-paths",
          "--no-warn-dirty",
          s".#t1.${config}.ip.${caseAttrRoot}.${caseName}"
        )
        Logger.trace(
          s"Running `${nixArgs.mkString(" ")}` to get test case ELF file"
        )
        os.Path(os.proc(nixArgs).call().out.trim()) / "bin" / s"${caseName}.elf"

    Logger.trace(s"Using test ELF: ${finalPath}")
    finalPath
  end resolveTestElfPath

  def resolveEmulatorPath(
      config: String,
      emuType: String,
      isTrace: Boolean = false
  ): os.Path =
    // FIXME: replace with actual trace emulator here
    val target =
      if (isTrace) then s"${emuType}.verilator-emu" else s"${emuType}.verilator-emu"
    val nixArgs = Seq(
      "nix",
      "build",
      "--no-link",
      "--print-out-paths",
      "--no-warn-dirty",
      s".#t1.${config}.${target}"
    )
    Logger.trace(s"Running `${nixArgs.mkString(" ")}` to get emulator")

    val finalPath =
      os.Path(os.proc(nixArgs).call().out.trim()) / "bin" / "online_drive"
    Logger.trace(s"Using emulator: ${finalPath}")

    finalPath
  end resolveEmulatorPath

  def resolveElaborateConfig(
      configName: String
  ): os.Path =
    if os.exists(os.Path(configName, os.pwd)) then os.Path(configName)
    else os.pwd / "configgen" / "generated" / s"$configName.json"
  end resolveElaborateConfig

  def prepareOutputDir(
      outputDir: Option[String],
      outputBaseDir: Option[String],
      config: String,
      emuType: String,
      caseName: String
  ): os.Path =
    val pathTail =
      if os.exists(os.Path(caseName, os.pwd)) || os.exists(
          os.Path(config, os.pwd)
        )
      then
        // It is hard to canoncalize user specify path, so here we use date time instead
        val now = java.time.LocalDateTime
          .now()
          .format(
            java.time.format.DateTimeFormatter.ofPattern("yy-MM-dd-HH-mm-ss")
          )
        os.RelPath(now)
      else os.RelPath(s"$config/$caseName")

    val path =
      if (outputDir.isEmpty) then
        if (outputBaseDir.isEmpty) then
          os.pwd / "testrun" / s"${emuType}emu" / pathTail
        else os.Path(outputBaseDir.get, os.pwd) / pathTail
      else os.Path(outputDir.get)

    os.makeDir.all(path)
    path
  end prepareOutputDir

  def optionals(cond: Boolean, input: Seq[String]): Seq[String] =
    if (cond) then input else Seq()

  // Should be configed via Nix
  @main def ipemu(
      @arg(
        name = "case",
        short = 'C',
        doc = "name alias for loading test case"
      ) testCase: String,
      @arg(
        name = "dramsim3-cfg",
        short = 'd',
        doc = "enable dramsim3, and specify its configuration file"
      ) dramsim3Config: Option[String] = None,
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
      ) trace: Flag = Flag(false),
      @arg(
        name = "verbose",
        short = 'v',
        doc = "set loglevel to debug"
      ) verbose: Flag = Flag(false),
      @arg(
        name = "no-logging",
        doc = "prevent emulator produce log (both console and file)"
      ) noLog: Flag = Flag(false),
      @arg(
        name = "with-file-logging",
        doc = """enable file logging, default is false.
          |WARN: the emulator will write all the information in each cycle, which will produce a huge file log, use with care.
          |""".stripMargin
      ) withFileLog: Flag = Flag(false),
      @arg(
        name = "no-console-logging",
        short = 'q',
        doc = "prevent emulator print log to console"
      ) noConsoleLog: Flag = Flag(false),
      @arg(
        name = "emulator-log-level",
        doc = "Set the EMULATOR_*_LOG_LEVEL env"
      ) emulatorLogLevel: String = "INFO",
      @arg(
        name = "emulator-log-file-path",
        doc = "Set the logging output path"
      ) emulatorLogFilePath: Option[os.Path] = None,
      @arg(
        name = "event-log-path",
        doc = "Set the event log path"
      ) eventLogFilePath: Option[os.Path] = None,
      @arg(
        name = "program-output-path",
        doc = "Path to store the ELF stdout/stderr"
      ) programOutputFilePath: Option[os.Path] = None,
      @arg(
        name = "out-dir",
        doc = "path to save wave file and perf result file"
      ) outDir: Option[String] = None,
      @arg(
        name = "base-out-dir",
        doc = "save result files in {base_out_dir}/{config}/{case}/{run_config}"
      ) baseOutDir: Option[String] = None,
      @arg(
        name = "emulator-path",
        doc = "path to emulator"
      ) emulatorPath: Option[String] = None,
      @arg(
        doc = "Force using x86_64 as cross compiling host platform"
      ) forceX86: Boolean = false,
      @arg(
        name = "dump-from-cycle",
        short = 'D',
        doc = "Specify the dump starting point"
      ) dumpCycle: String = "0.0",
      @arg(
        name = "cosim-timeout",
        doc = "specify timeout cycle for cosim"
      ) cosimTimeout: Int = 400000,
      @arg(
        name = "dry-run",
        doc = "Print the final emulator command line"
      ) dryRun: Flag = Flag(false)
  ): Unit =
    val caseElfPath = resolveTestElfPath(config, testCase, forceX86)
    val outputPath =
      prepareOutputDir(outDir, baseOutDir, config, "ip", testCase)
    val emulator = if (!emulatorPath.isEmpty) then
      val emuPath = os.Path(emulatorPath.get, os.pwd)
      if (!os.exists(emuPath)) then
        sys.error(s"No emulator found at path: ${emulatorPath.get}")

      emuPath
    else resolveEmulatorPath(config, "ip", trace.value)

    import scala.util.chaining._
    val elaborateConfig = resolveElaborateConfig(config)
      .pipe(os.read)
      .pipe(text => ujson.read(text))
    val tck = scala.math.pow(10, 3) / dramsim3Frequency
    val emulatorLogPath =
      if emulatorLogFilePath.isDefined then emulatorLogFilePath.get
      else outputPath / "emulator.log"
    val eventLogPath =
      if eventLogFilePath.isDefined then eventLogFilePath.get
      else outputPath / "rtl-event.jsonl"
    val programOutputPath =
      if programOutputFilePath.isDefined then programOutputFilePath.get
      else outputPath / "mmio-store.txt"
    if os.exists(programOutputPath) then os.remove(programOutputPath)

    def dumpCycleAsFloat() =
      val ratio = dumpCycle.toFloat
      if ratio < 0.0 || ratio > 1.0 then
        Logger.error(
          s"Can't use $dumpCycle as ratio, use 0 as waveform dump start point"
        )
        0
      else if ratio == 0.0 then 0
      else
        val cycleRecordFilePath =
          os.pwd / ".github" / "cases" / config / "default.json"
        if !os.exists(cycleRecordFilePath) then
          Logger.error(
            s"$cycleRecordFilePath not found, please run this script at project root"
          )
          sys.exit(1)
        val cycleRecord = os
          .read(cycleRecordFilePath)
          .pipe(raw => ujson.read(raw))
          .obj(testCase)
        if cycleRecord.isNull then
          Logger.error(
            s"Using ratio to specify ratio is only supported in raw test case name"
          )
          sys.exit(1)
        val cycle = cycleRecord.num
        scala.math.floor(cycle * 10 * ratio).toInt

    val dumpStartPoint: Int =
      try dumpCycle.toInt
      catch
        case _ =>
          try dumpCycleAsFloat()
          catch
            case _ =>
              Logger.error(
                s"Unknown cycle $dumpCycle specified, using 0 as fallback"
              )
              0

    val processArgs = Seq(
      emulator.toString(),
      "--elf-file",
      caseElfPath.toString(),
      "--wave-path",
      (outputPath / "wave.fst").toString(),
      "--timeout",
      cosimTimeout.toString(),
      s"--log-file=${emulatorLogPath}",
      s"--log-level=${emulatorLogLevel}",
      "--vlen",
      elaborateConfig.obj("parameter").obj("vLen").toString(),
      "--dlen",
      elaborateConfig.obj("parameter").obj("dLen").toString()
      // "--tck",
      // tck.toString(),
      // "--perf",
      // (outputPath / "perf.txt").toString(),
      // "--tl_bank_number",
      // elaborateConfig
      //   .obj("parameter")
      //   .obj("lsuBankParameters")
      //   .arr
      //   .length
      //   .toString(),
      // "--beat_byte",
      // elaborateConfig
      //   .obj("parameter")
      //   .obj("lsuBankParameters")
      //   .arr(0)
      //   .obj("beatbyte")
      //   .toString(),
      // "--program-output-path",
      // programOutputPath.toString
    )
    // ++ optionals(noLog.value, Seq("--no-logging"))
    // ++ optionals((!withFileLog.value), Seq("--no-file-logging"))
    // ++ optionals(noConsoleLog.value, Seq("--no-console-logging"))
    // ++ optionals(
    //   dramsim3Config.isDefined,
    //   Seq(
    //     "--dramsim3-result",
    //     (outputPath / "dramsim3-logs").toString(),
    //     "--dramsim3-config",
    //     dramsim3Config.getOrElse("")
    //   )
    // )
    // ++ optionals(
    //   trace.value,
    //   Seq("--dump-from-cycle", dumpStartPoint.toString)
    // )

    Logger.info(s"Starting IP emulator: `${processArgs.mkString(" ")}`")
    if dryRun.value then return

    if os.exists(eventLogPath) then os.remove(eventLogPath)
    os.proc(processArgs)
      .call(
        stderr = eventLogPath
      )
    Logger.info(s"RTL event log saved to ${eventLogPath}")

    // if (!withFileLog.value) then
    //   Logger.info(s"Emulator log save to ${emulatorLogPath}")
    //
    // if (trace.value) then
    //   Logger.info(s"Trace file save to ${outputPath}/wave.fst")
  end ipemu

  @main def listConfig(): Unit =
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

  @main def subsystemrtl(
      @arg(
        name = "config",
        short = 'c',
        doc = "Config to be elaborated for the subsystem RTL"
      ) config: String,
      @arg(
        name = "out-link",
        short = 'o',
        doc =
          "Path to be a symlink to the RTL build output, default using $config_subsystem_rtl"
      ) outLink: Option[String] = None
  ): Unit =
    val finalOutLink = outLink.getOrElse(s"${config}_subsystem_rtl")
    os.proc(
      Seq(
        "nix",
        "build",
        "--print-build-logs",
        s".#t1.${config}.subsystem.rtl",
        "--out-link",
        finalOutLink
      )
    ).call(stdout = os.Inherit, stderr = os.Inherit, stdin = os.Inherit)
    Logger.info(s"RTLs store in $finalOutLink")

  @main
  def difftest(
      @arg(
        name = "config",
        short = 'c',
        doc = "specify the elaborate config for running test case"
      ) config: String,
      @arg(
        name = "case-attr",
        short = 'C',
        doc = "Specify test case attribute to run diff test"
      ) caseAttr: String,
      @arg(
        name = "log-level",
        short = 'L',
        doc = "Specify log level to run diff test"
      ) logLevel: String = "ERROR",
      @arg(
        name = "event-path",
        short = 'e',
        doc = "Specify the event log path to examinate"
      ) eventPath: Option[String],
      @arg(
        name = "trace",
        short = 'T',
        doc = "Use trace emulator result"
      ) trace: Flag
  ): Unit =
    val difftest = if trace.value then
      nixResolvePath(s".#t1.${config}.ip.difftest-trace")
    else
      nixResolvePath(s".#t1.${config}.ip.difftest")

    val fullCaseAttr = s".#t1.${config}.cases.${caseAttr}"
    val caseElf = nixResolvePath(fullCaseAttr)

    import scala.util.chaining._
    val configJson = nixResolvePath(s".#t1.${config}.elaborateConfigJson")
      .pipe(p => os.Path(p))
      .pipe(p => os.read(p))
      .pipe(text => ujson.read(text))
    val dLen = configJson.obj("parameter").obj("dLen").num.toInt
    val vLen = configJson.obj("parameter").obj("vLen").num.toInt

    Logger.trace(s"Running emulator to get event log")
    val eventLog = if eventPath.isDefined then
      eventPath.get
    else
      if trace.value then
        nixResolvePath(s"${fullCaseAttr}.emu-result.with-trace")
      else
        nixResolvePath(s"${fullCaseAttr}.emu-result")

    Logger.trace("Running zstd to get event log")
    os.proc(
      Seq(
        "zstd",
        "--decompress",
        "-f",
        s"${eventLog}/rtl-event.jsonl.zstd",
        "-o",
        s"${config}-${caseAttr}.event.jsonl"
      )
    ).call(stdout = os.Inherit, stderr = os.Inherit)
    Logger.info(
      s"Starting difftest with DLEN ${dLen}, VLEN ${vLen} for ${fullCaseAttr}"
    )
    os.proc(
      Seq(
        s"${difftest}/bin/offline",
        "--vlen",
        vLen.toString(),
        "--dlen",
        dLen.toString(),
        "--elf-file",
        s"${caseElf}/bin/${caseAttr}.elf",
        "--log-file",
        s"${config}-${caseAttr}.event.jsonl",
        // FIXME: offline difftest doesn't support timeout argument RN
        // "--timeout",
        // "40000",
        "--log-level",
        s"${logLevel}"
      )
    ).call(stdout = os.Inherit, stderr = os.Inherit)
    Logger.info(s"PASS: ${caseAttr} (${config})")
  end difftest

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
end Main
