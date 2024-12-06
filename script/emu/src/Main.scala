// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.script

import mainargs.{arg, main, Flag, Leftover, ParserForMethods, TokensReader}
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

  def resolveNixPath(attr: String, extraArgs: Seq[String] = Seq()): String =
    Logger.trace(s"Running nix build ${attr}")
    val args = Seq(
      "nix",
      "build",
      "--no-link",
      "--no-warn-dirty",
      "--print-out-paths",
      attr
    ) ++ extraArgs
    os.proc(args).call().out.trim()

  def resolveTestElfPath(
    ip:       String,
    config:   String,
    caseName: String,
    forceX86: Boolean = false
  ): os.Path =
    val casePath = os.Path(caseName, os.pwd)
    if (os.exists(casePath)) then return casePath

    val caseAttrRoot =
      if (forceX86) then ".#legacyPackages.x86_64-linux."
      else ".#"

    val nixStorePath = resolveNixPath(
      s"${caseAttrRoot}t1.${config}.${ip}.cases.${caseName}"
    )
    val elfFilePath  = os.Path(nixStorePath) / "bin" / s"${caseName}.elf"

    elfFilePath
  end resolveTestElfPath

  def resolveTestBenchPath(
    ip:      String,
    config:  String,
    emuType: String
  ): os.Path =
    val emuPath = os.Path(emuType, os.pwd)
    if (os.exists(emuPath)) then return emuPath

    val nixStorePath = resolveNixPath(s".#t1.${config}.${ip}.${emuType}", Seq("--impure"))

    val elfFilePath = os
      .walk(os.Path(nixStorePath) / "bin")
      .filter(path => os.isFile(path))
      .lastOption
      .getOrElse(Logger.fatal("no simulator found in given attribute"))

    elfFilePath
  end resolveTestBenchPath

  def prepareOutputDir(
    outputDir: String
  ): os.Path =
    val outputPath  = os.Path(outputDir, os.pwd)
    val currentDate = java.time.LocalDateTime
      .now()
      .format(
        java.time.format.DateTimeFormatter.ofPattern("yy-MM-dd-HH-mm-ss")
      )
    val resultPath  = outputPath / "all" / currentDate

    os.makeDir.all(resultPath)

    val userPath = outputPath / "result"
    os.remove(userPath, checkExists = false)
    os.symlink(userPath, resultPath)

    userPath
  end prepareOutputDir

  def tryRestoreFromCache(key: String, value: Option[String]): Option[String] =
    val xdgDir  = sys.env.get("XDG_CACHE_HOME")
    val homeDir = sys.env.get("HOME")

    val cacheDir =
      if xdgDir != None then os.Path(xdgDir.get) / "chipsalliance-t1"
      else if homeDir != None then os.Path(homeDir.get) / ".cache" / "chipsalliance-t1"
      else os.pwd / ".cache"

    os.makeDir.all(cacheDir)

    val cacheFile = cacheDir / "helper-cache.json"
    val cache     =
      if os.exists(cacheFile) then ujson.read(os.read(cacheFile))
      else ujson.Obj()

    val ret = if value.isDefined then
      cache(key) = value.get
      value
    else cache.obj.get(key).map(v => v.str)

    os.write.over(cacheFile, ujson.write(cache))

    ret
  end tryRestoreFromCache

  def optionals(cond: Boolean, input: Seq[String]): Seq[String] =
    if (cond) then input else Seq()

  def optionalMap[K, V](cond: Boolean, input: Map[K, V]): Map[K, V] =
    if (cond) then input else null

  @main def run(
    @arg(
      name = "ip",
      short = 'i',
      doc = "IP type for emulator, Eg. t1emu, t1rocketemu"
    ) ip:       Option[String],
    @arg(
      name = "emu",
      short = 'e',
      doc = "Type for emulator, Eg. vcs-emu, verilator-emu-trace"
    ) emuType:  Option[String],
    @arg(
      name = "config",
      short = 'c',
      doc = "configuration name"
    ) config:   Option[String],
    @arg(
      name = "verbose",
      short = 'v',
      doc = "set loglevel to debug"
    ) verbose:  Flag = Flag(false),
    @arg(
      name = "out-dir",
      doc = "path to save wave file and perf result file"
    ) outDir:   Option[String] = None,
    @arg(
      doc = "Cross compile RISC-V test case with x86-64 host tools"
    ) forceX86: Boolean = false,
    @arg(
      name = "dry-run",
      doc = "Print the final emulator command line and exit"
    ) dryRun:   Flag = Flag(false),
    @arg(
      name = "timeout",
      doc = "Specify maximum cycle count limit"
    ) timeout:  Option[Int] = None,
    leftOver:   Leftover[String]
  ): Unit =
    if leftOver.value.isEmpty then Logger.fatal("No test case name")
    val caseName = leftOver.value.head

    val finalIp = tryRestoreFromCache("ip", ip)
    if finalIp.isEmpty then
      Logger.fatal(
        s"No cached IP selection nor --ip argument was provided"
      )

    val finalEmuType = tryRestoreFromCache("emulator", emuType)
    if finalEmuType.isEmpty then
      Logger.fatal(
        s"No cached emulator selection nor --emu argument was provided"
      )

    val isTrace = finalEmuType.get.contains("-trace")
    val isCover = finalEmuType.get.contains("-cover")

    val finalConfig = tryRestoreFromCache("config", config)
    if finalConfig.isEmpty then
      Logger.fatal(
        s"No cached config selection nor --config argument was provided"
      )

    Logger.info(
      s"Using config=${BOLD}${finalConfig.get}${RESET} emulator=${BOLD}${finalEmuType.get}${RESET} case=${BOLD}$caseName${RESET}"
    )

    val caseElfPath =
      resolveTestElfPath(finalIp.get, finalConfig.get, caseName, forceX86)
    val outputPath  = prepareOutputDir(outDir.getOrElse("t1-sim-result"))
    val emulator    = resolveTestBenchPath(finalIp.get, finalConfig.get, finalEmuType.get)

    val leftOverArguments = leftOver.value.dropWhile(arg => arg != "--")

    val processArgs = Seq(
      emulator.toString(),
      s"+t1_elf_file=${caseElfPath}"
    )
      ++ optionals(timeout.isDefined, Seq(s"+t1_timeout=${timeout.getOrElse("unreachable")}"))
      ++ optionals(isTrace, Seq(s"+t1_wave_path=${outputPath / "wave.fsdb"}"))
      ++ optionals(isCover, Seq(s"-cm assert"))
      ++ optionals(!leftOverArguments.isEmpty, leftOverArguments)

    if dryRun.value then return

    val rtlEventPath = outputPath / "rtl-event.jsonl.zst"
    val journalPath  = outputPath / "online-drive-emu-journal"
    val statePath    = outputPath / "driver-state.json"

    // Save information of this run, so that user can start offline check without arguments
    os.write(
      statePath,
      ujson.write(
        ujson.Obj(
          "config" -> finalConfig.get,
          "elf"    -> caseElfPath.toString,
          "event"  -> rtlEventPath.toString,
          "ip"     -> finalIp.get
        )
      )
    )

    // For vcs trace simulator, we need daidir keep at same directory as the wave.fsdb file
    if finalEmuType.get == "vcs-emu-trace" then
      val libPath    = emulator / os.up / os.up / "lib"
      val daidirPath =
        os.walk(libPath)
          .filter(path => os.isDir(path))
          .filter(path => path.segments.toSeq.last.endsWith(".daidir"))
          .last
      val daidirName = daidirPath.segments.toSeq.last
      os.symlink(outputPath / daidirName, daidirPath)

    Logger.info(s"Starting IP emulator: `${processArgs.mkString(" ")}`")

    val driverProc = os
      .proc(processArgs)
      .spawn(
        stdout = journalPath,
        stderr = os.Pipe,
        env = optionalMap(verbose.value, Map("RUST_LOG" -> "TRACE"))
      )
    val zstdProc   = os
      .proc(Seq("zstd", "-o", s"${rtlEventPath}"))
      .spawn(
        stdin = driverProc.stderr,
        stdout = os.Inherit,
        stderr = os.Inherit
      )

    zstdProc.join(-1)
    driverProc.join(-1)
    if zstdProc.exitCode() != 0 then Logger.fatal("fail to compress data")
    if driverProc.exitCode() != 0 then Logger.fatal("online driver run failed")

    Logger.info("Driver finished")

    if isCover then
      if os.exists(os.pwd / "cm.vdb") && os.exists(os.pwd / "cm.log") then
        os.move(os.pwd / "cm.vdb", outputPath / "cm.vdb", replaceExisting = true)
        os.move(os.pwd / "cm.log", outputPath / "cm.log", replaceExisting = true)
        Logger.info(s"Coverage database saved under ${outputPath}/cm.vdb")
      else if !finalEmuType.get.startsWith("verilator-emu") then Logger.error("No cm.vdb cm.log found")

    if os.exists(os.pwd / "perf.json") then
      os.move(os.pwd / "perf.json", outputPath / "perf.json", replaceExisting = true)

    Logger.info(s"Output saved under ${outputPath}")
  end run

  @main
  def check(
    @arg(
      name = "ip",
      short = 'i',
      doc = "IP type for emulator, Eg. t1emu, t1rocketemu"
    ) ip:        Option[String],
    @arg(
      name = "config",
      short = 'c',
      doc = "specify the elaborate config for running test case"
    ) config:    Option[String],
    @arg(
      name = "case-attr",
      short = 'C',
      doc = "Specify test case attribute to run diff test"
    ) caseAttr:  Option[String],
    @arg(
      name = "event-path",
      short = 'e',
      doc = "Specify the event log path to examinate"
    ) eventPath: Option[String],
    @arg(
      name = "verbose",
      short = 'v',
      doc = "Verbose output"
    ) verbose:   Flag = Flag(false),
    @arg(
      name = "out-dir",
      doc = "path to save wave file and perf result file"
    ) outDir:    Option[String] = None
  ): Unit =
    val logLevel =
      if verbose.value then "trace"
      else "info"

    val resultPath =
      os.Path(outDir.getOrElse("t1-sim-result"), os.pwd) / "result"
    val lastState  =
      if os.exists(resultPath) then ujson.read(os.read(resultPath / "driver-state.json"))
      else ujson.Obj()

    val finalIp =
      if ip.isDefined then ip.get
      else
        lastState.obj
          .get("ip")
          .getOrElse(Logger.fatal("No driver-state.json nor --ip"))
          .str

    val finalConfig =
      if config.isDefined then config.get
      else
        lastState.obj
          .get("config")
          .getOrElse(Logger.fatal("No driver-state.json nor --config"))
          .str

    val offlineChecker = os.Path(
      resolveNixPath(s".#t1.${finalConfig}.${finalIp}.offline-checker")
    ) / "bin" / "offline"

    val elfFile =
      if caseAttr.isDefined then resolveTestElfPath(finalIp, finalConfig, caseAttr.get).toString
      else
        lastState.obj
          .get("elf")
          .getOrElse(Logger.fatal("No driver-state.json nor --case-attr"))
          .str

    val eventFile    =
      if eventPath.isDefined then os.Path(eventPath.get, os.pwd)
      else os.Path(lastState.obj.get("event").getOrElse(Logger.fatal("")).str)
    val decEventFile = resultPath / "rtl-event.jsonl"
    Logger.info(s"Decompressing ${eventFile}")
    os.proc("zstd", s"${eventFile}", "-d", "-f", "-o", s"${decEventFile}")
      .call()

    val driverArgs: Seq[String] =
      Seq(
        offlineChecker.toString,
        "--elf-file",
        elfFile,
        "--log-level",
        logLevel,
        "--log-file",
        decEventFile.toString
      )
    Logger.info(s"Running offline checker: ${driverArgs.mkString(" ")}")

    val ret = os.proc(driverArgs).call(stdout = os.Inherit, stderr = os.Inherit, check = false)
    if (ret.exitCode != 0) then Logger.fatal("offline checker run failed")
  end check

  @main
  def listCases(
    @arg(
      name = "ip",
      short = 'i',
      doc = "specify the IP, such as t1emu, t1rocketemu"
    ) ip:     String,
    @arg(
      name = "config",
      short = 'c',
      doc = "specify the config for test cases"
    ) config: String,
    pattern:  Leftover[String]
  ): Unit =
    if pattern.value.length != 1 then
      Logger.fatal("invalid pattern was given, plz run 't1-helper listCases -c <config> <regexp>'")

    val regexp = pattern.value.head.r
    Logger.info("Fetching current test cases")
    val args   = Seq(
      "nix",
      "--no-warn-dirty",
      "eval",
      s".#t1.${config}.${ip}.cases",
      "--apply",
      """cases: with builtins;
        | (map
        |   (drv: drv.pname)
        |   (filter
        |     (drv: drv ? type && drv.type == "derivation" && drv ? pname)
        |     (concatMap attrValues
        |       (filter
        |         (v: typeOf v == "set")
        |         (attrValues cases)))))""".stripMargin,
      "--json"
    )
    val output = os.proc(args).call().out.trim()
    println()
    ujson
      .read(output)
      .arr
      .map(v => v.str)
      .filter(eachCase => regexp.findAllIn(eachCase).nonEmpty)
      .foreach(p => println(s"* ${p}"))
  end listCases

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args.toSeq)
end Main
