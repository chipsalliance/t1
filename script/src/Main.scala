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
          s".#t1.${config}.${caseAttrRoot}.${caseName}"
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
    val target =
      if (isTrace) then s"${emuType}.emu-trace" else s"${emuType}.emu"
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
      os.Path(os.proc(nixArgs).call().out.trim()) / "bin" / "emulator"
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
      else outputPath / "rtl-event.log"
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
      s"--log-path=${emulatorLogPath}",
      "--program-output-path",
      programOutputPath.toString
    ) ++ optionals(noLog.value, Seq("--no-logging"))
      ++ optionals((!withFileLog.value), Seq("--no-file-logging"))
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
      ++ optionals(
        trace.value,
        Seq("--dump-from-cycle", dumpStartPoint.toString)
      )

    Logger.info(s"Starting IP emulator: `${processArgs.mkString(" ")}`")
    if dryRun.value then return

    if os.exists(eventLogPath) then os.remove(eventLogPath)
    os.proc(processArgs)
      .call(
        env = Map(
          "EMULATOR_FILE_LOG_LEVEL" -> emulatorLogLevel,
          "EMULATOR_CONSOLE_LOG_LEVEL" -> emulatorLogLevel
        ),
        stderr = eventLogPath
      )
    Logger.info(s"RTL event log saved to ${eventLogPath}")

    if (!withFileLog.value) then
      Logger.info(s"Emulator log save to ${emulatorLogPath}")

    if (trace.value) then
      Logger.info(s"Trace file save to ${outputPath}/wave.fst")
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

    val (unProcessedData, normalData) =
      allCycleData.partition:
        case (_, cycle) => cycle <= 0

    // Initialize a list of buckets
    val cargoInit =
      (0 until math.min(bucketSize, allCycleData.length)).map(_ => Bucket())
    // Group tests that have cycle data into subset by their cycle size
    val cargoStaged = normalData
      .sortBy(_._2)(Ordering[Int].reverse)
      .foldLeft(cargoInit): (cargo, elem) =>
        val smallest = cargo.minBy(_.totalCycle)
        cargo.updated(cargo.indexOf(smallest), smallest.cons(elem))

    // For unprocessed data, just split them into subset that have equal size
    if unProcessedData.nonEmpty then
      val chunkSize = unProcessedData.length.toDouble / bucketSize.toDouble
      val cargoFinal = unProcessedData
        .grouped(math.ceil(chunkSize).toInt)
        .zipWithIndex
        .foldLeft(cargoStaged): (cargo, chunkWithIndex) =>
          val (chunk, idx) = chunkWithIndex
          val newBucket = chunk.foldLeft(cargoStaged.apply(idx)):
            (bucket, data) => bucket.cons(data)
          cargo.updated(idx, newBucket)

      cargoFinal.map(_.buffer.mkString(";")).toSeq
    else cargoStaged.map(_.buffer.mkString(";")).toSeq
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
      ))

  // Read default tests information from '.github/cases/default.txt' file, and use that information to generate GitHub CI matrix.
  // The result will be printed to stdout, and should be pipe into $GITHUB_OUTPUT
  @main
  def generateCiMatrix(
      runnersAmount: Int,
      testPlanFile: String = "default.json"
  ) = {
    val testPlans =
      os.walk(os.pwd / ".github" / "cases").filter(_.last == testPlanFile)
    println(toMatrixJson(scheduleTasks(testPlans, runnersAmount)))
  }

  // Run jobs and give a brief result report
  // - Log of tailed tests will be tailed and copied into $resultDir/failed-logs/$testName.log
  // - List of failed tests will be written into $resultDir/failed-tests.md
  // - Report of cycle updates will be written into $resultDir/cycle-updates.md
  // - New cycle file will be written into $resultDir/$config_$runConfig_cycle.json
  //
  // @param: jobs A semicolon-separated list of job names of the form $config,$caseName,$runConfig
  // @param: resultDir output directory of the test results, default to ./test-results
  // @param: dontBail don't throw exception when test fail. Useful for postpr.
  @main
  def runTests(
      jobs: String,
      dontBail: Flag = Flag(false)
  ): Unit =
    if jobs == "" then
      Logger.info("No test found, exiting")
      return

    val allJobs = jobs.split(";")
    def findFailedTests() = allJobs.zipWithIndex.foldLeft(Seq[String]()):
      (allFailedTest, currentTest) =>
        val (testName, index) = currentTest
        val Array(config, caseName) = testName.split(",")
        println("\n")
        Logger.info(
          s"${BOLD}[${index + 1}/${allJobs.length}]${RESET} Running test case $caseName with config $config"
        )

        val testResultPath =
          os.Path(nixResolvePath(s".#t1.$config.cases.$caseName.emu-result"))
        val testSuccess =
          os.read(testResultPath / "emu-success").trim().toInt == 1

        if !testSuccess then
          Logger.error(s"Test case $testName failed")
          val err = os.read(testResultPath / "emu.log")
          Logger.error(s"Detail error: $err")

        Logger.info("Running difftest")
        val diffTestSuccess =
          try
            difftest(
              config = config,
              caseAttr = caseName,
              logLevel = "ERROR"
            )
            true
          catch
            err =>
              Logger.error(s"difftest run failed: $err")
              false

        if diffTestSuccess != testSuccess then
          Logger.fatal(
            "Got different online and offline difftest result, please check this test manually. CI aborted."
          )

        if !testSuccess then allFailedTest :+ s"t1.$config.cases.$caseName"
        else allFailedTest
    end findFailedTests

    val failedTests = findFailedTests()
    if failedTests.isEmpty then Logger.info(s"All tests passed")
    else
      val listOfFailJobs =
        failedTests.map(job => s"* $job").appended("").mkString("\n")
      val failedJobsWithError = failedTests
        .map(testName =>
          val testResult = os.Path(nixResolvePath(s".#$testName.emu-result"))
          val emuLog = os.read(testResult / "emu.log")
          if emuLog.nonEmpty then
            s"* $testName\n     >>> ERROR SUMMARY <<<\n${emuLog}"
          else
            s"* $testName\n     >>> OTHER ERROR   <<<\n${os.read(testResult / "emu.journal")}"
        )
        .appended("")
        .mkString("\n")

      if dontBail.value then
        Logger.error(
          s"${BOLD}${failedTests.length} tests failed${RESET}:\n${failedJobsWithError}"
        )
      else
        Logger.fatal(
          s"${BOLD}${failedTests.length} tests failed${RESET}:\n${failedJobsWithError}"
        )
  end runTests

  // PostCI do the below four things:
  //   * read default.json at .github/cases/$config/default.json
  //   * generate case information for each entry in default.json (cycle, run success)
  //   * collect and report failed tests
  //   * collect and report cycle update
  @main
  def postCI(
      @arg(
        name = "failed-test-file-path",
        doc = "specify the failed test markdown file output path"
      ) failedTestsFilePath: String,
      @arg(
        name = "cycle-update-file-path",
        doc = "specify the cycle update markdown file output path"
      ) cycleUpdateFilePath: String
  ) =
    case class CaseStatus(
        caseName: String,
        isFailed: Boolean,
        oldCycle: Int,
        newCycle: Int
    )

    def collectCaseStatus(
        config: String,
        caseName: String,
        cycle: Int
    ): CaseStatus =
      val emuResultPath = os.Path(nixResolvePath(s".#t1.$config.cases.$caseName.emu-result"))
      val testFail = os.read(emuResultPath / "emu-success") == "0"

      val perfCycleRegex = raw"total_cycles:\s(\d+)".r
      val newCycle = os.read
        .lines(emuResultPath / "perf.txt")
        .apply(0) match
        case perfCycleRegex(cycle) => cycle.toInt
        case _ =>
          throw new Exception("perf.txt file is not format as expected")
      CaseStatus(
        caseName = caseName,
        isFailed = testFail,
        oldCycle = cycle,
        newCycle = newCycle
      )
    end collectCaseStatus

    val allCycleRecords =
      os.walk(os.pwd / ".github" / "cases").filter(_.last == "default.json")
    allCycleRecords.foreach: file =>
      val config = file.segments.toSeq.reverse.apply(1)
      var cycleRecord = ujson.read(os.read(file))

      nixResolvePath(s".#t1.$config.cases._allEmuResult")

      val allCaseStatus = cycleRecord.obj.map(rec =>
        rec match {
          case (caseName, cycle) =>
            collectCaseStatus(config, caseName, cycle.num.toInt)
        }
      )

      val failedCases = allCaseStatus
        .filter(c => c.isFailed)
        .map(c => s"* `.#t1.${config}.cases.${c.caseName}`")
      val failedTestsRecordFile = os.Path(failedTestsFilePath, os.pwd)
      os.write.over(failedTestsRecordFile, "## Failed tests\n")
      os.write.append(failedTestsRecordFile, failedCases)

      val cycleUpdateRecordFile = os.Path(cycleUpdateFilePath, os.pwd)
      os.write.over(cycleUpdateRecordFile, "## Cycle Update\n")
      val allCycleUpdates = allCaseStatus
        .filter(c => c.oldCycle != c.newCycle)
        .map: caseStatus =>
          caseStatus match
            case CaseStatus(caseName, _, oldCycle, newCycle) =>
              cycleRecord(caseName) = newCycle
              if oldCycle == -1 then s"* 🆕 ${caseName}: NaN -> ${newCycle}"
              else if oldCycle > newCycle then
                s"* 🚀 $caseName: $oldCycle -> $newCycle"
              else s"* 🐢 $caseName: $oldCycle -> $newCycle"
      os.write.append(cycleUpdateRecordFile, allCycleUpdates.mkString("\n"))

      os.write.over(file, ujson.write(cycleRecord, indent = 2))
  end postCI

  @main
  def generateTestPlan() =
    val allCases =
      os.walk(os.pwd / ".github" / "cases").filter(_.last == "default.json")
    val testPlans = allCases.map: caseFilePath =>
      caseFilePath.segments.dropWhile(_ != "cases").drop(1).next

    println(ujson.write(Map("config" -> testPlans)))
  end generateTestPlan

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

  @main
  def generateRegressionTestPlan(runnersAmount: Int): Unit =
    // Find emulator configs
    val emulatorConfigs: Seq[String] =
      os.walk(os.pwd / ".github" / "cases")
        .filter: path =>
          path.last == "default.json"
        .map: path =>
          // We have a list of pwd/.github/cases/<config>/default.json string,
          // but all we need is the <config> name.
          path.segments.toSeq.reverse.drop(1).head

    import scala.util.chaining._
    val testPlans: Seq[String] = emulatorConfigs.flatMap: configName =>
      val allCasesPath = nixResolvePath(s".#t1.$configName.cases.all")
      os.walk(os.Path(allCasesPath) / "configs")
        .filter: path =>
          path.ext == "json"
        .map: path =>
          // configs/ directory have a list of <testName>.json files, we need those <testName>
          val testName = path.segments.toSeq.last.stripSuffix(".json")
          s"$configName,$testName"

    def getTestPlan(filePat: String): Seq[String] =
      os.walk(os.pwd / ".github" / "cases")
        .filter: path =>
          path.last == filePat
        .flatMap: path =>
          val config = path.segments.toSeq.reverse.drop(1).head
          os.read(path)
            .pipe(raw => ujson.read(raw))
            .pipe(json => json.obj.keys.map(testName => s"$config,$testName"))

    val currentTestPlan = getTestPlan("default.json")
    val perfCases = getTestPlan("perf.json")

    // We don't have much information for this tests, so randomly split them into same size buckets
    // Merge Seq( "A", "B", "C", "D" ) into Seq( "A;B", "C;D" )
    def buckets(alltests: Seq[String], bucketSize: Int): Seq[String] =
      scala.util.Random
        .shuffle(alltests)
        .grouped(
          math.ceil(alltests.size.toDouble / bucketSize).toInt
        )
        .toSeq
        .map(_.mkString(";"))

    val finalTestPlan =
      (testPlans.toSet -- currentTestPlan.toSet -- perfCases.toSet).toSeq
    buckets(finalTestPlan, runnersAmount)
      .pipe(toMatrixJson)
      .pipe(println)
  end generateRegressionTestPlan

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
    ) logLevel: String = "ERROR"
  ): Unit =
    val difftest = nixResolvePath(".#t1-simulator")

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
    val eventLog = nixResolvePath(s"${fullCaseAttr}.emu-result")

    Logger.trace("Running zstd to get event log")
    os.proc(Seq(
      "zstd",
      "--decompress",
      "-f",
      s"${eventLog}/rtl-event.log.zstd",
      "-o",
      s"${config}-${caseAttr}.event.log"
    )).call(stdout = os.Inherit, stderr = os.Inherit)
    Logger.info(s"Starting t1-simulator with DLEN ${dLen}, VLEN ${vLen} for ${fullCaseAttr}")
    os.proc(
      Seq(
        s"${difftest}/bin/t1-simulator",
        "--vlen",
        vLen.toString(),
        "--dlen",
        dLen.toString(),
        "--elf-file",
        s"${caseElf}/bin/${caseAttr}.elf",
        "--log-file",
        s"${config}-${caseAttr}.event.log",
        "--log-level",
        s"${logLevel}"
      )
    ).call(stdout = os.Inherit, stderr = os.Inherit)
  end difftest

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
end Main
