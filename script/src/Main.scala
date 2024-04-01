// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.script

import mainargs.{main, arg, ParserForMethods, Leftover, Flag, TokensReader}
import scala.io.AnsiColor._

object Logger {
  // 0: trace
  // 1: error
  // 2: warn
  // 3: info
  val level = sys.env.getOrElse("LOG_LEVEL", "3").toInt

  def info(message: String) = println(s"${BOLD}${GREEN}[INFO]${RESET} ${message}")
  def trace(message: String) = if level <= 0 then println(s"${BOLD}${GREEN}[INFO]${RESET} ${message}")
}

object Main:
  implicit object PathRead extends TokensReader.Simple[os.Path]:
    def shortName = "path"

    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))

  def resolveTestElfPath(
      config: String,
      caseName: String,
      forceX86: Boolean = false
  ): os.Path =
    val casePath = os.Path(caseName, os.pwd)
    val caseAttrRoot = if (forceX86) then "cases-x86" else "cases"

    val finalPath = if (os.exists(casePath)) then
      casePath
    else
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

    Logger.info(s"Using test ELF: ${finalPath}")
    finalPath
  end resolveTestElfPath

  def resolveEmulatorPath(
      config: String,
      emuType: String,
      isTrace: Boolean = false
  ): os.Path =
    val target = if (isTrace) then s"${emuType}.emu-trace" else s"${emuType}.emu"
    val nixArgs = Seq(
      "nix",
      "build",
      "--no-link",
      "--print-out-paths",
      "--no-warn-dirty",
      s".#t1.${config}.${target}"
    )
    Logger.info(s"Running `${nixArgs.mkString(" ")}` to get emulator")

    val finalPath = os.Path(os.proc(nixArgs).call().out.trim()) / "bin" / "emulator"
    Logger.info(s"Using emulator: ${finalPath}")

    finalPath
  end resolveEmulatorPath

  def resolveElaborateConfig(
      outputDir: os.Path,
      configName: String
  ): os.Path =
    if (os.exists(outputDir / "config.json")) then
      os.remove.all(outputDir / "config.json")

    val cfgPath = os.Path(configName, os.pwd)
    if (os.exists(cfgPath)) then return cfgPath

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
  end resolveElaborateConfig

  def prepareOutputDir(
      outputDir: Option[String],
      outputBaseDir: Option[String],
      config: String,
      emuType: String,
      caseName: String
  ): os.Path =
    val pathTail = if (os.exists(os.Path(caseName, os.pwd))) then
      // It is hard to canoncalize user specify path, so here we use date time instead
      java.time.LocalDateTime
        .now()
        .format(
          java.time.format.DateTimeFormatter.ofPattern("yy-MM-dd-HH-mm-ss")
        )
    else
      caseName

    val path = if (outputDir.isEmpty) then
      if (outputBaseDir.isEmpty) then
        os.pwd / "testrun" / s"${emuType}emu" / config / pathTail
      else
        os.Path(outputBaseDir.get, os.pwd) / config / pathTail
    else
      os.Path(outputDir.get)

    os.makeDir.all(path)
    path
  end prepareOutputDir

  def optionals(cond: Boolean, input: Seq[String]): Seq[String] =
    if (cond) then input else Seq()

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
  ): Unit =
    val caseElfPath = resolveTestElfPath(config, testCase, forceX86)
    val outputPath =
      prepareOutputDir(outDir, baseOutDir, config, "ip", testCase)
    val emulator = if (!emulatorPath.isEmpty) then
      val emuPath = os.Path(emulatorPath.get, os.pwd)
      if (!os.exists(emuPath)) then
        sys.error(s"No emulator found at path: ${emulatorPath.get}")

      emuPath
    else
      resolveEmulatorPath(config, "ip", trace.value)

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

    if (!noFileLog.value) then
      Logger.info(s"Emulator log save to ${outputPath / "emulator.log"}")

    if (trace.value) then
      Logger.info(s"Trace file save to ${outputPath} / trace.fst")
  end ipemu

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
  ): Unit =
    val caseElfPath = resolveTestElfPath(config, testCase, forceX86)
    val outputPath =
      prepareOutputDir(outDir, baseOutDir, config, "subsystem", testCase)
    val emulator = if (!emulatorPath.isEmpty) then
      val emuPath = os.Path(emulatorPath.get, os.pwd)
      if (!os.exists(emuPath)) then
        sys.error(s"No emulator found at path: ${emulatorPath.get}")
      emuPath
    else
      resolveEmulatorPath(config, "subsystem", trace.value)

    val emuArgs =
      Seq(s"+init_file=${caseElfPath}") ++ optionals(
        trace.value,
        Seq(s"+trace_file=${
            if (traceFile.isDefined) then
              os.Path(traceFile.get, os.pwd)
            else outputPath / "trace.fst"
          }")
      )
    os.proc(emuArgs).call()

    if (trace.value) then
      Logger.info(s"Trace file save to ${outputPath} / trace.fst")
  end subsystememu

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

  //
  // CI
  //
  // The below script will try to read all the tests in ../../.github/cases/**/default.json,
  // arranging them together by their required cycle, and using GitHub "matrix" feature to manage
  // and separate those test job to multiple machines.
  //
  // We define that, the term "bucket" refers to a list of test job, concating by ';' into text.
  // "Bucket" will be recorded in "matrix" payload field "jobs". Each machine will run a "bucket" of tests.
  //
  // Function `generateMatrix` will be used to produce necessary information to feed the GitHub matrix.
  // Function `runTests` will parse the GitHub matrix, run a "bucket" of tests and generate GitHub CI report.
  //
  // The final "matrix" will have json data like: { include: [ { jobs: "taskA;taskB", id: 1 }, { jobs: "taskC;taskD", id: 2 } ] }.
  //

  // Merge Seq( "A", "B", "C", "D" ) into Seq( "A;B", "C;D" )
  //
  // @param allTests The original Seq
  // @param bucketSize Specify the size of the output Seq
  def buckets(alltests: Seq[String], bucketSize: Int): Seq[String] =
    scala.util.Random.shuffle(alltests).grouped(
      math.ceil(alltests.size.toDouble / bucketSize).toInt
    ).toSeq.map(_.mkString(";"))

  case class Bucket(buffer: Seq[String] = Seq(), totalCycle: Int = 0):
    def cons(data: (String, Int)): Bucket =
      val (testName, cycle) = data
      Bucket(buffer ++ Seq(testName), totalCycle + cycle)

    def mkString(sep: String = ";") = buffer.mkString(sep)


  // Read test case and their cycle data from the given paths.
  // Test cases will be grouped into a single bucket, and then partitioned into given `bucketSize` of sub-bucket.
  // Each sub-bucket will have similar weight, so that the time cost will be similar between each runners.
  //
  // For example:
  //
  //   [ {A: 100}, {B: 180}, {C: 300}, {D:200} ] => [[A,C], [B,D]]
  //
  // @param allTasksFile List of the default.json file path
  // @param bucketSize Specify the size of the output Seq
  def scheduleTasks(allTasksFile: Seq[os.Path], bucketSize: Int): Seq[String] =
    // Produce a list of ("config,testName", cycle) pair
    val allCycleData = allTasksFile.flatMap: file =>
      Logger.trace(s"Generate tests from file: $file")
      val config = file.segments.toSeq.reverse.apply(1)
      ujson
        .read(os.read(file))
        .obj
        .map { case (caseName, cycle) =>
          (s"$config,$caseName", cycle.num.toInt)
        }
        .toSeq

    // _2 is the cycle number
    val (unProcessedData, normalData) = allCycleData.partition(_._2 <= 0)
    // Initialize a list of buckets
    val cargoInit = (0 until bucketSize).map(_ => Bucket())
    // Group tests that have cycle data into subset by their cycle size
    val cargoStaged = normalData
      .sortBy(_._2)(Ordering[Int].reverse)
      .foldLeft(cargoInit): (cargo, elem) =>
        val smallest = cargo.minBy(_.totalCycle)
        cargo.updated(cargo.indexOf(smallest), smallest.cons(elem))

    // For unprocessed data, just split them into subset that have equal size
    val cargoFinal = (0 until bucketSize).foldLeft(cargoStaged)((cargo, i) =>
      val startIdx = i * bucketSize
      val endIdx = math.min((i + 1) * bucketSize, unProcessedData.length)
      val newBucket = unProcessedData.slice(startIdx, endIdx)
        .foldLeft(cargo.apply(i)): (bucket, data) =>
          bucket.cons(data)
      cargo.updated(i, newBucket)
    )

    cargoFinal.map(_.buffer.mkString(";")).toSeq
  end scheduleTasks

  // Turn Seq( "A;B", "C;D" ) to GitHub Action matrix style json: { "include": [ { "jobs": "A;B", id: 1 }, { "jobs": "C;D", id: 2 } ] }
  //
  // @param buckets Seq of String that is already packed into bucket using the `buckets` function
  // @param outputFile Path to the output json file
  def toMatrixJson(buckets: Seq[String]) =
    ujson.Obj("include" -> buckets.zipWithIndex.map: (bucket, i) =>
      ujson.Obj(
        "jobs" -> ujson.Str(bucket),
        "id" -> ujson.Num(i + 1)
      )
    )

  // Read default tests information from '.github/cases/default.txt' file, and use that information to generate GitHub CI matrix.
  // The result will be printed to stdout, and should be pipe into $GITHUB_OUTPUT
  @main
  def generateCiMatrix(
      runnersAmount: Int,
  ) = {
    val testPlans = os.walk(os.pwd / ".github" / "cases").filter(_.last == "default.json")
    println(toMatrixJson(scheduleTasks(testPlans, runnersAmount)))
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
