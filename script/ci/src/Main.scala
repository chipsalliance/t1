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
    val cargoInit   =
      (0 until math.min(bucketSize, allCycleData.length)).map(_ => Bucket())
    // Group tests that have cycle data into subset by their cycle size
    val cargoStaged = normalData
      .sortBy(_._2)(Ordering[Int].reverse)
      .foldLeft(cargoInit): (cargo, elem) =>
        val smallest = cargo.minBy(_.totalCycle)
        cargo.updated(cargo.indexOf(smallest), smallest.cons(elem))

    // For unprocessed data, just split them into subset that have equal size
    if unProcessedData.nonEmpty then
      val chunkSize  = unProcessedData.length.toDouble / bucketSize.toDouble
      val cargoFinal = unProcessedData
        .grouped(math.ceil(chunkSize).toInt)
        .zipWithIndex
        .foldLeft(cargoStaged): (cargo, chunkWithIndex) =>
          val (chunk, idx) = chunkWithIndex
          val newBucket    = chunk.foldLeft(cargoStaged.apply(idx)): (bucket, data) =>
            bucket.cons(data)
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
        "id"   -> ujson.Num(i + 1)
      ))

  // Read default tests information from '.github/cases/default.txt' file, and use that information to generate GitHub CI matrix.
  // The result will be printed to stdout, and should be pipe into $GITHUB_OUTPUT
  @main
  def generateCiMatrix(
    runnersAmount: Int,
    caseDir:       String = "designs",
    top:           String
  ) = {
    val testPlans =
      os.walk(os.pwd / ".github" / caseDir)
        .filter(_.last == s"${top}.json")
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
    jobs:   String,
    top:    String,
    emuLib: String
  ): Unit =
    if jobs == "" then
      Logger.info("No test found, exiting")
      return

    val allJobs           = jobs.split(";")
    def findFailedTests() = allJobs.zipWithIndex.foldLeft(Seq[String]()): (allFailedTest, currentTest) =>
      val (testName, index)       = currentTest
      val Array(config, caseName) = testName.split(",")
      println("\n")
      Logger.info(
        s"${BOLD}[${index + 1}/${allJobs.length}]${RESET} Simulating test case $caseName with config $config"
      )

      val testAttr       = emuLib.toLowerCase() match
        case "verilator" => s".#t1.$config.$top.run.$caseName.verilator-emu"
        case "vcs"       => s".#t1.$config.$top.run.$caseName.vcs-emu-cover"
        case _           => Logger.fatal(s"Invalid test type ${emuLib}")
      val testResultPath =
        try
          os.Path(
            nixResolvePath(
              testAttr,
              Seq("--impure")
            )
          )
        catch
          case _ =>
            Logger.error(
              s"Online driver for config $config, case $caseName fail, please check manually on local machine"
            )
            Logger.error(s"nix build $testAttr --impure")
            // Create fake path for latter assertion to fail
            val fakedir = os.temp.dir()
            os.write(fakedir / "offline-check-status", "1")
            fakedir

      Logger.info("Checking RTL event from event log")
      val testSuccess =
        os.read(testResultPath / "offline-check-status").trim() == "0"
      if !testSuccess then
        Logger.error(s"Offline check FAILED for $caseName ($config)")
        allFailedTest :+ testAttr
      else
        Logger.info(s"Offline check PASS for $caseName ($config)")
        allFailedTest
    end findFailedTests

    val failedTests = findFailedTests()
    if failedTests.isEmpty then Logger.info(s"All tests passed")
    else
      os.write.append(
        os.Path(sys.env("GITHUB_STEP_SUMMARY")),
        failedTests.map(s => s"* ${s}\n").mkString
      )
      os.write.append(os.Path(sys.env("GITHUB_STEP_SUMMARY")), s"Job URL: ${findCurrentJobURL()}")
      Logger.fatal(
        s"${BOLD}${failedTests.length} tests failed${RESET}"
      )
  end runTests

  // Search job HTML page by github run id
  @main
  def findCurrentJobURL() =
    val runID    = sys.env("GITHUB_RUN_ID")
    val jobName  = sys.env("GITHUB_JOB_NAME")
    val proxy: (String, Int) = sys.env.get("https_proxy")
      .map(proxy => new java.net.URI(proxy))
      .map(uri => (uri.getHost, uri.getPort))
      .getOrElse(null)

    Logger.info(s"Getting URL for Run ID: ${runID}, job '${jobName}'")
    val session = requests.Session(
      headers = Map("User-Agent" -> "Mozilla/5.0 (X11; Linux x86_64; rv:134.0) Gecko/20100101 Firefox/134.0"),
      proxy = proxy,
    )
    val response = session.get.stream(s"https://api.github.com/repos/chipsalliance/t1/actions/runs/${runID}/jobs")
    val ciData = ujson.read(response)
    ciData
      .obj("jobs")
      .arr
      .filter(job => job.obj("name").str == jobName)
      .map(job => job.obj("html_url").str)
      .head
  end findCurrentJobURL

  // PostCI do the below four things:
  //   * read default.json at .github/cases/$config/default.json
  //   * generate case information for each entry in default.json (cycle, run success)
  //   * collect and report cycle update
  @main
  def postCI(
    @arg(
      name = "cycle-update-file-path",
      doc = "specify the cycle update markdown file output path"
    ) cycleUpdateFilePath: String,
    @arg(
      name = "urg-report-file-path",
      doc = "specify the urg report markdown file output path"
    ) urgReportFilePath:   Option[String],
    @arg(
      name = "emu-lib",
      doc = "Specify emulator library type, verilator or vcs"
    ) emuLib:              String = "vcs",
    @arg(
      name = "top",
      doc = "Specify emulator ip top, Eg. t1rocketemu/t1emu..."
    ) top:                 String
  ) =
    os.write.over(
      os.Path(cycleUpdateFilePath, os.pwd),
      "## Cycle Update\n"
    )

    if urgReportFilePath.nonEmpty then os.write(os.Path(urgReportFilePath.get, os.pwd), "# Coverage report\n")

    os.walk(os.pwd / ".github" / "designs")
      .filter(_.last == s"${top}.json")
      .foreach: file =>
        val config      = file.segments.toSeq.reverse.apply(1)
        var cycleRecord = ujson.read(os.read(file))

        Logger.info("Fetching CI results")
        val emuResultPath = os.Path(
          nixResolvePath(
            s".#t1.$config.${top}.run._${emuLib}EmuResult",
            Seq("--impure")
          )
        )

        if cycleUpdateFilePath.nonEmpty then
          Logger.info("Collecting cycle update info")
          val allCycleUpdates = os
            .walk(emuResultPath)
            .filter(path => path.last == "perf.json")
            .map(path => {
              val cycle    = ujson.read(os.read(path)).obj("total_cycles").num.toInt
              val caseName = path.segments.toSeq.reverse.drop(1).head
              (caseName, cycle, cycleRecord.obj(caseName).num.toInt)
            })
            .filter((_, newCycle, oldCycle) => newCycle != oldCycle)
            .map:
              case (caseName, newCycle, oldCycle) =>
                cycleRecord(caseName) = newCycle
                if oldCycle == -1 then s"* 🆕 ${caseName}($config): NaN -> ${newCycle}"
                else if oldCycle > newCycle then s"* 🚀 $caseName($config): $oldCycle -> $newCycle"
                else s"* 🐢 $caseName($config): $oldCycle -> $newCycle"

          os.write.append(
            os.Path(cycleUpdateFilePath, os.pwd),
            allCycleUpdates.mkString("\n") + "\n"
          )

        if urgReportFilePath.nonEmpty then
          Logger.info("Filtering urg report")
          val finalMdPath     = os.Path(urgReportFilePath.get, os.pwd)
          val urgAssertFile   = emuResultPath / "urgReport" / "asserts.txt"
          val summaryHeading  = "^Summary for Cover Properties$".r
          val coverSummaryStr =
            os.read(urgAssertFile)
              .lines()
              .dropWhile(!summaryHeading.matches(_))
              .takeWhile(_.distinct != "-")
              .toArray
              .mkString("\n")
          val detailHeading   = "^Detail Report for Cover Properties$".r
          val coverDetailStr  =
            os.read(urgAssertFile)
              .lines()
              .dropWhile(!detailHeading.matches(_))
              .toArray
              .mkString("\n")
          os.write.append(finalMdPath, s"### Coverage for $config \n")
          os.write.append(finalMdPath, "```text\n")
          os.write.append(finalMdPath, coverSummaryStr)
          os.write.append(finalMdPath, "----------------------\n")
          os.write.append(finalMdPath, coverDetailStr)
          os.write.append(finalMdPath, "\n```\n")

        os.write.over(file, ujson.write(cycleRecord, indent = 2))
  end postCI

  @main
  def generateTestPlan(top: String) =
    val testPlans = os
      .walk(os.pwd / ".github" / "designs")
      .filter(_.last == s"${top}.json")
      .map(path => path.segments.toSeq.reverse.drop(1).head)

    println(ujson.write(Map("config" -> testPlans)))
  end generateTestPlan

  def nixResolvePath(attr: String, extraArgs: Seq[String] = Seq()): String =
    os.proc(
      Seq(
        "nix",
        "build",
        "--no-link",
        "--no-warn-dirty",
        "--print-out-paths",
        attr
      ) ++ extraArgs
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
          path.segments.toSeq.reverse.drop(1).head

    import scala.util.chaining._
    val testPlans: Seq[String] = emulatorConfigs.flatMap: configName =>
      val allCasesPath =
        nixResolvePath(s".#t1.$configName.ip.cases._all")
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
    val perfCases       = getTestPlan("perf.json")

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

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args.toSeq)
end Main
