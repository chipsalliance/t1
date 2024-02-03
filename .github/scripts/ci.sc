#!/usr/bin/env amm

import scala.collection.mutable.ArrayBuffer

// Merge Seq( "A", "B", "C", "D" ) into Seq( "A;B", "C;D" )
//
// @param allTests The original Seq
// @param bucketSize Specify the size of the output Seq
def buckets(alltests: Seq[String], bucketSize: Int): Seq[String] =
  scala.util.Random.shuffle(alltests).grouped(
    math.ceil(alltests.size.toDouble / bucketSize).toInt
  ).toSeq.map(_.mkString(";"))

class BucketBuffer() {
  private var _buffer: ArrayBuffer[String] = new ArrayBuffer()
  private var _total_cycles = 0;

  def push_back(test: String, cycle: Int) = {
    _buffer += test
    _total_cycles += cycle
  }

  def total_cycles: Int = _total_cycles

  def mkString(): String = {
    _buffer.toArray.mkString(";")
  }
}

// Read test case and their cycle data from the given paths.
// Test cases will be grouped into a single bucket, and then partitioned into given `bucketSize` of sub-bucket.
// Each sub-bucket will have similar weight, so that the time cost will be similar between each runners.
//
// For example:
//
//   [ {A: 100}, {B: 180}, {C: 300}, {D:200} ] => [[A,C], [B,D]]
//
// @param allTasksFile List of the passed.json file path
// @param bucketSize Specify the size of the output Seq
def scheduleTasks(allTasksFile: Seq[os.Path], bucketSize: Int): Seq[String] = {
  val init = Seq[(String, Int)]()
  val allCycleData = allTasksFile.flatMap (file => {
      System.err.println(s"Generate tests from file: $file")
      val config = file.segments.toSeq.reverse(1)
      ujson
        .read(os.read(file))
        .obj
        .map { case (caseName, cycle) =>
          (s"$config,$caseName", cycle.num.toInt)
        }
        .toSeq
  })
  // Initialize a list of buckets
  val cargo = (0 until bucketSize).map(_ => new BucketBuffer())
  // _2 is the cycle number
  val (unProcessedData, normalData) = allCycleData.partition(_._2 <= 0)
  // Group tests that have cycle data into subset by their cycle size
  normalData
    .sortBy(_._2)(Ordering[Int].reverse)
    .foreach(elem => {
      val (testName, cycle) = elem;
      cargo.minBy(_.total_cycles).push_back(testName, cycle)
    })
  // For unprocessed data, just split them into subset that have equal size
  cargo.zipWithIndex.foreach { case(buffer, i) =>
    val startIdx = i * bucketSize
    val endIdx = math.min((i + 1) * bucketSize, unProcessedData.length)
    unProcessedData.slice(startIdx, endIdx).foreach { case(name, cycle) => buffer.push_back(name, cycle) }
  }
  cargo.map(_.mkString).toSeq
}

// Turn Seq( "A,B", "C,D" ) to GitHub Action matrix style json: { "include": [ { "name": "A,B" }, { "name": "C,D" } ] }
//
// @param buckets Seq of String that is already packed into bucket using the `buckets` function
// @param outputFile Path to the output json file
def toMatrixJson(buckets: Seq[String]) =
  ujson.Obj("include" -> buckets.zipWithIndex.map{ case(a, i)  => ujson.Obj("jobs" -> ujson.Str(a), "id" -> ujson.Num(i + 1)) })

// Read default tests information from '.github/cases/default.txt' file, and use that information to generate GitHub CI matrix.
// The result will be printed to stdout, and should be pipe into $GITHUB_OUTPUT
@main
def generateCiMatrix(
    runnersAmount: Int,
) = {
  val defaultCases = os.pwd / os.RelPath(".github/cases/default.txt")
  println(toMatrixJson(
    scheduleTasks(
      os.read
        .lines(defaultCases)
        .map(defaultCases / os.up / os.RelPath(_)),
      runnersAmount
    ),
  ))
}

// Resolve all the executable test and filter out unpassed tests, appending perf testcases
@main
def postPrMatrixJson(
  runnersAmount: Int,
) = {
  val defaultCases = os.pwd / os.RelPath(".github/cases/default.txt")
  val caseFiles = os.read.lines(defaultCases).map(os.RelPath(_))
  val testCaseDir = os.proc("nix", "build", ".#t1.cases.all", "--no-link", "--print-out-paths").call(cwd=os.pwd).out.trim
  val unpassedCases = caseFiles.flatMap(file => {
    val verilateCfg = file.segments.head
    val exists = ujson.read(os.read(defaultCases / os.up / file))
      .obj.keys
      .map(caseName => s"$verilateCfg,$caseName")
    val isFpVerilator = verilateCfg.endsWith("-fp")
    val all: Seq[String] = os.list(os.Path(testCaseDir) / "configs")
      // For fp verilator, don't filter tests
      // For non-fp verilator, filter out fp tests
      .filter(f => !isFpVerilator && !ujson.read(os.read(f))("fp").bool)
      .map(f => s"$verilateCfg,${f.baseName.toString}")
    val perfCases = os.walk(defaultCases / os.up)
      .filter(f => f.last == "perf-cases.txt")
      .flatMap(f => {
        val Seq(_, verilateCfg) = file.segments.toSeq.reverse.slice(0, 2)
        os.read.lines(f).filter(_.length > 0).map (caseName =>
          s"$verilateCfg,$caseName"
        )
      })

    (all.toSet -- exists.toSet ++ perfCases).toSeq
  })
  println(toMatrixJson(buckets(unpassedCases, runnersAmount)))
}

// Find the perf.txt file for tests specified in the .github/passed/*/*/perf-cases.txt file,
// and convert them into markdown format.
@main
def convertPerfToMD() = os
  .walk(os.pwd / ".github" / "cases")
  .filter(_.last == "perf-cases.txt")
  .foreach(p => {
    val testRunDir = os.pwd / "testrun"
    val perfCases = os.read.lines(p).filter(_.length > 0)
    val config = p.relativeTo(os.pwd / ".github" / "cases").segments.toSeq(0)
    val existPerfFile = perfCases
      .filter {testcase =>
        val path = testRunDir / os.RelPath(
          s"$config/$testcase/perf.txt"
        )
        os.exists(path)
      }
      .foreach(testcase => {
        val path = testRunDir / os.RelPath(
          s"$config/$testcase/perf.txt"
        )
        System.err.println(s"generating perf-result-$testcase-$config.md")
        val output =
          s"""### $testcase
            |
            |* Config: $config
            |
            |```
            |${os.read(path).trim}
            |```
            |\n""".stripMargin
        os.write(os.pwd / s"perf-result-$testcase-$config.md", output)
      })
  })


def writeCycleUpdates(job: String, testRunDir: os.Path, resultDir: os.Path) = {
  val isEmulatorTask = raw"([^,]+),([^,]+)".r
  job match {
    case isEmulatorTask(e, t) => {
      val passedFile = os.pwd / os.RelPath(s".github/cases/$e/default.json")
      val original = ujson.read(os.read(passedFile))

      val perfCycleRegex = raw"total_cycles:\s(\d+)".r
      val newCycleCount = os
        .read
        .lines(testRunDir / os.RelPath(s"$e/$t/perf.txt"))
        .apply(0) match {
          case perfCycleRegex(cycle) => cycle.toInt
          case _ => throw new Exception("perf.txt file is not format as expected")
        }

      val oldCycleCount = original.obj.get(t).map(_.num.toInt).getOrElse(-1)
      val cycleUpdateFile = resultDir / "cycle-updates.md"
      System.err.println(f"job '$job' cycle $oldCycleCount -> $newCycleCount")
      oldCycleCount match {
        case -1 => os.write.append(cycleUpdateFile, s"* 🆕 $job: NaN -> $newCycleCount\n")
        case _ => {
          if (oldCycleCount > newCycleCount) {
            os.write.append(cycleUpdateFile, s"* 🚀 $job: $oldCycleCount -> $newCycleCount\n")
          } else if (oldCycleCount < newCycleCount) {
            os.write.append(cycleUpdateFile, s"* 🐢 $job: $oldCycleCount -> $newCycleCount\n")
          }

          val newCycleFile = resultDir / s"${e}_cycle.json"
          val newCycleRecord = if (os.exists(newCycleFile)) {
            ujson.read(os.read(newCycleFile))
          } else {
            ujson.Obj()
          }

          newCycleRecord(t) = newCycleCount
          os.write.over(newCycleFile, ujson.write(newCycleRecord, indent = 2))
        }
      }
    }
    case _ => {
      throw new Exception(f"unknown job format '$job'")
    }
  }
}


// Run jobs and give a brief result report
// - Log of tailed tests will be tailed and copied into $resultDir/failed-logs/$job.log
// - List of failed tests will be written into $resultDir/failed-tests.md
// - Report of cycle updates will be written into $resultDir/cycle-updates.md
// - New cycle file will be written into $resultDir/$config_$runConfig_cycle.json
//
// @param: jobs A semicolon-separated list of job names of the form $config,$caseName,$runConfig
// @param: resultDir output directory of the test results, default to ./test-results
// @param: dontBail don't throw exception when test fail. Useful for postpr.
@main
def runTests(jobs: String, runTarget: String = "ip", resultDir: Option[os.Path], dontBail: Boolean = false) = {
  var actualResultDir = resultDir.getOrElse(os.pwd / "test-results")
  val testRunDir = os.pwd / "testrun"
  os.makeDir.all(actualResultDir / "failed-logs")
  val totalJobs = jobs.split(";")
  val failed = totalJobs.zipWithIndex.foldLeft(Seq[String]()) {
    case(failed, (job, i)) => {
      val Array(config, caseName) = job.split(",")
      System.err.println(s"\n\n\n>>>[${i+1}/${totalJobs.length}] Running test case $config,$caseName")
      val handle = os
        .proc("scripts/run-test.py", runTarget, "-c", config, "--no-log", "--base-out-dir", testRunDir, caseName)
        .call(check=false)
      if (handle.exitCode != 0) {
        val outDir = testRunDir / config / caseName
        System.err.println(s"Test case $job failed")
        os.write(actualResultDir / "failed-logs" / s"$job.txt", handle.out.text)
        failed :+ job
      } else {
        writeCycleUpdates(job, testRunDir, actualResultDir)
        failed
      }
    }
  }

  os.write.over(actualResultDir / "failed-tests.md", "")  // touch file, to avoid upload-artifacts warning
  if (failed.length > 0) {
    val listOfFailJobs = failed.map(job => s"* $job").appended("").mkString("\n")
    os.write.over(actualResultDir / "failed-tests.md", listOfFailJobs)
    val failedJobsWithError = failed.map(job => s"* $job\n     >>> ERROR SUMMARY <<<\n${os.read(actualResultDir / "failed-logs" / s"$job.txt")}").appended("").mkString("\n")
    System.err.println(s"\n\n${failed.length} tests failed:\n${failedJobsWithError}")
    if (!dontBail) {
      throw new Exception("Tests failed")
    }
  } else {
    System.err.println(s"All tests passed")
  }
}

// Run failed tests, which ensures wave files
//
// @param: jobs A semicolon-separated list of job names of the form $config,$caseName,$runConfig
// @param: output directory of the test results, default to ./test-results
@main
def runFailedTests(jobs: String, runTarget: String = "ip") = {
  val testRunDir = os.pwd / "testrun"
  val totalJobs = jobs.split(";")
  val failed = totalJobs.zipWithIndex.foreach { case (job, i) => {
    val Array(config, caseName) = job.split(",")
    System.err.println(s"[${i+1}/${totalJobs.length}] Running test case with trace $config,$caseName")
    val handle = os
      .proc("scripts/run-test.py", runTarget, "-c", config, "--trace", "--no-log", "--base-out-dir", testRunDir, caseName)
      .call(check=false)
  }}
}

@main
def mergeCycleData() = {
  println("Updating cycle data")
  val original = os.walk(os.pwd / ".github" / "cases")
    .filter(_.last == "default.json")
    .map(path => {
      val config = path.segments.toSeq.reverse(1)
      (config, ujson.read(os.read(path)))
    })
    .toMap
  os.walk(os.pwd)
    .filter(_.last.endsWith("_cycle.json"))
    .map(path => {
      val config = path.last.split("_")(0)
      println(s"Reading new cycle data from $path")
      (config, ujson.read(os.read(path)))
    })
    .foreach {
      case(name, latest) => {
        val old = original.apply(name)
        latest.obj.foreach {
          case (k, v) => old.update(k, v)
        }
      }
    }
  original.foreach {
    case(name, data) => {
      val config = name.split(",")(0)
      os.write.over(
        os.pwd / os.RelPath(s".github/cases/$config/default.json"),
        ujson.write(data, indent = 2)
      )
    }
  }
  println("Cycle data updated")
}
