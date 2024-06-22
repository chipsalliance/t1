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
        val handle = os.proc(
          "nix",
          "run",
          ".#t1-helper",
          "--",
          "difftest",
          "--config",
          config,
          "--case-attr",
          caseName,
          "--log-level",
          "ERROR"
        ).call(stdout = os.Inherit, stderr = os.Inherit, check = false)
        val diffTestSuccess = handle.exitCode == 0
        if !diffTestSuccess then
          Logger.error("difftest run failed")

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
    os.walk(os.pwd / ".github" / "cases")
      .filter(_.last == "default.json")
      .foreach: file =>
        val config = file.segments.toSeq.reverse.apply(1)
        var cycleRecord = ujson.read(os.read(file))

        Logger.info("Fetching CI results")
        val emuResultPath =
          os.Path(nixResolvePath(s".#t1.$config.cases._allEmuResult"))

        Logger.info("Collecting failed cases")
        val failedCases = os
          .walk(emuResultPath)
          .filter(path => path.last == "emu-success")
          .filter(path => os.read(path) == "0")
          .map(path => path.segments.toSeq.reverse.drop(1).head)
          .map(caseName => s"* `.#t1.${config}.cases.${caseName}`")
        val failedTestsRecordFile = os.Path(failedTestsFilePath, os.pwd)
        os.write.over(failedTestsRecordFile, "## Failed tests\n")
        os.write.append(failedTestsRecordFile, failedCases)

        Logger.info("Collecting cycle update info")
        val cycleUpdateRecordFile = os.Path(cycleUpdateFilePath, os.pwd)
        os.write.over(cycleUpdateRecordFile, "## Cycle Update\n")
        val perfCycleRegex = raw"total_cycles:\s(\d+)".r
        val allCycleUpdates = os
          .walk(emuResultPath)
          .filter(path => path.last == "perf.txt")
          .map(path => {
            val cycle = os.read.lines(path).head match
              case perfCycleRegex(cycle) => cycle.toInt
              case _ =>
                throw new Exception("perf.txt file is not format as expected")
            val caseName = path.segments.toSeq.reverse.drop(1).head
            (caseName, cycle, cycleRecord.obj(caseName).num.toInt)
          })
          .filter((_, newCycle, oldCycle) => newCycle != oldCycle)
          .map:
            case (caseName, newCycle, oldCycle) =>
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

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
end Main
