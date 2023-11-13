#!/usr/bin/env amm

import scala.collection.mutable.ArrayBuffer

// Merge Seq( "A", "B", "C", "D" ) into Seq( "A;B", "C;D" )
//
// @param allTests The original Seq
// @param bucketSize Specify the size of the output Seq
def buckets(alltests: Seq[String], bucketSize: Int): Seq[String] =  // TODO: investigate load inbalance
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

// Read the passed.json file and parse them into verilatorEmulator tasks.
// Tasks will be partitioned into given `bucketSize` of subset by their corresponding instruction cycle 
// so that this subset have a similar time span to execute.
//
// @param allTasksFile List of the passed.json file path
// @param bucketSize Specify the size of the output Seq
def scheduleTasks(allTasksFile: Seq[os.Path], bucketSize: Int): Seq[String] = {
  val init = Seq[(String, Int)]()
  val cycleData = allTasksFile.foldLeft(init) {
    case (sum, file) => {
      System.err.println(s"Generate tests from file: $file")
      val Seq(_, runCfg, config) = file.segments.toSeq.reverse.slice(0, 3)
      ujson
        .read(os.read(file))
        .obj
        .map { case (caseName, v) =>
          (s"$config,$caseName,$runCfg", v.num.toInt)
        }
        .toSeq
    }
  }
  // Initialize a list of buckets
  val cargo = (0 until bucketSize).map(_ => new BucketBuffer())
  cycleData
    .sortBy(_._2)(Ordering[Int].reverse)
    .foreach(elem => {
      val (testName, cycle) = elem;
      cargo.minBy(_.total_cycles).push_back(testName, cycle)
    })
  cargo.map(_.mkString).toSeq
}

// Turn Seq( "A,B", "C,D" ) to GitHub Action matrix style json: { "include": [ { "name": "A,B" }, { "name": "C,D" } ] }
//
// @param buckets Seq of String that is already packed into bucket using the `buckets` function
// @param outputFile Path to the output json file
def toMatrixJson(buckets: Seq[String]) = 
  ujson.Obj("include" -> buckets.map(a => ujson.Obj(s"jobs" -> ujson.Str(a))))

// Generate a list of grouped tests from the given `defaultPassed` file.
@main
def passedMatrixJson(
    bucketSize: Int,
) = {
  val defaultPassed = os.pwd / os.RelPath(".github/passed/default.txt")
  println(toMatrixJson(
    scheduleTasks(
      os.read
        .lines(defaultPassed)
        .map(defaultPassed / os.up / os.RelPath(_)),
      bucketSize
    ),
  ))
}

// Resolve all the executable test and filter out unpassed tests, appending perf testcases
@main
def postPrMatrixJson(
  bucketSize: Int,
) = {
  val defaultPassed = os.pwd / os.RelPath(".github/passed/default.txt")
  val passedFiles = os.read.lines(defaultPassed).map(os.RelPath(_))
  val unpassedTests = passedFiles.flatMap(file => {
    val Seq(config, runCfg, _) = file.segments.toSeq
    val configFile = os.pwd / "configs" / s"$config.json"
    val isFp = ujson.read(os.read(configFile))("design")("parameter")("fpuEnable").bool
    val exists = ujson.read(os.read(defaultPassed / os.up / file))
      .obj.keys
      .map(caseName => s"$config,$caseName,$runCfg")
    val all: Seq[String] = os.list(os.Path(sys.env("TEST_CASES_DIR")) / "configs")
      .filter(f => ujson.read(os.read(f))("fp").bool == isFp)
      .map(f => s"$config,${f.baseName.toString},$runCfg")
    val perfCases = os.walk(defaultPassed / os.up)
      .filter(f => f.last == "perf-cases.txt")
      .flatMap(f => {
        val Seq(_, runCfg, config) = file.segments.toSeq.reverse.slice(0, 3)
        os.read.lines(f).filter(_.length > 0).map (caseName => 
          s"$config,$caseName,$runCfg"
        )
      })

    (all.toSet -- exists.toSet ++ perfCases).toSeq
  })
  println(toMatrixJson(buckets(unpassedTests, bucketSize)))
}

// Find the perf.txt file for tests specified in the .github/passed/*/*/perf-cases.txt file,
// and convert them into markdown format.
@main
def convertPerfToMD() = os
  .walk(os.pwd / ".github" / "passed")
  .filter(_.last == "perf-cases.txt")
  .foreach(p => {
    val testRunDir = os.pwd / "testrun"
    val perfCases = os.read.lines(p).filter(_.length > 0)
    val Seq(config, runCfg, _) =
      p.relativeTo(os.pwd / ".github" / "passed").segments.toSeq
    val existPerfFile = perfCases
      .filter {testcase =>
        val path = testRunDir / os.RelPath(
          s"$config/$testcase/$runCfg/perf.txt"
        )
        os.exists(path)
      }
      .foreach(testcase => {
        val path = testRunDir / os.RelPath(
          s"$config/$testcase/$runCfg/perf.txt"
        )
        System.err.println(s"generating perf-result-$testcase-$config-$runCfg.md")
        val output =
          s"""### $testcase
            |
            |* Config: $config
            |* Run Config: $runCfg
            |
            |```
            |${os.read(path).trim}
            |```
            |\n""".stripMargin
        os.write(os.pwd / s"perf-result-$testcase-$config-$runCfg.md", output)
      })
  })


def writeCycleUpdates(job: String, testRunDir: os.Path, resultDir: os.Path) = {
  val isEmulatorTask = raw"([^,]+),([^,]+),([^,]+)".r
  job match {
    case isEmulatorTask(e, t, r) => {
      val passedFile = os.pwd / os.RelPath(s".github/passed/$e/$r/passed.json")
      val original = ujson.read(os.read(passedFile))

      val perfCycleRegex = raw"total_cycles:\s(\d+)".r
      val newCycleCount = os
        .read
        .lines(testRunDir / os.RelPath(s"$e/$t/$r/perf.txt"))
        .apply(0) match {
          case perfCycleRegex(cycle) => cycle.toInt
          case _ => throw new Exception("perf.txt file is not format as expected")
        }

      val oldCycleCount = original.obj.get(t).map(_.num.toInt).getOrElse(-1)
      val cycleUpdateFile = resultDir / "cycle-updates.md"
      os.write.over(cycleUpdateFile, "")  // touch file
      System.err.println(f"job '$job' cycle $oldCycleCount -> $newCycleCount")
      oldCycleCount match {
        case -1 => os.write.append(cycleUpdateFile, s"* 🆕 $job: NaN -> $newCycleCount\n")
        case _ => {
          if (oldCycleCount > newCycleCount) {
            os.write.append(cycleUpdateFile, s"* 🔺 $job: $oldCycleCount -> $newCycleCount\n")
          } else if (oldCycleCount < newCycleCount) {
            os.write.append(cycleUpdateFile, s"* 🔻 $job: $oldCycleCount -> $newCycleCount\n")
          }

          val newCycleFile = resultDir / s"${e}_${r}_cycle.json"
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
// @param: output directory of the test results, default to ./test-results
@main
def runTests(jobs: String, resultDir: Option[os.Path]) = {
  var actualResultDir = resultDir.getOrElse(os.pwd / "test-results")
  val testRunDir = os.pwd / "testrun"
  os.makeDir.all(actualResultDir / "failed-logs")
  val totalJobs = jobs.split(";")
  val failed = totalJobs.zipWithIndex.foldLeft(Seq[String]()) {
    case(failed, (job, i)) => {
      val Array(config, caseName, runCfg) = job.split(",")
      System.err.println(s"[${i+1}/${totalJobs.length}] Running test case $config,$caseName,$runCfg")
      val handle = os
        .proc("scripts/run-test.py", "-c", config, "-r", runCfg, "--no-console-log", "--base-out-dir", testRunDir, caseName)
        .call(check=false)
      if (handle.exitCode != 0) {
        val outDir = testRunDir / config / caseName / runCfg
        System.err.println(s"Test case $job failed")
        os.proc("tail", "-n", "100", outDir / "emulator.log").call(stdout=actualResultDir / "failed-logs" / s"$job.log")
        failed :+ job
      } else {
        writeCycleUpdates(job, testRunDir, actualResultDir)
        failed
      }
    }
  }

  os.write.over(actualResultDir / "failed-tests.md", "")  // touch file, to avoid upload-artifacts warning
  if (failed.length > 0) {
    os.write.over(actualResultDir / "failed-tests.md", failed.map(f => s"* $f").mkString("\n"))
    System.err.println(s"${failed.length} tests failed:\n${failed.mkString("\n")}")
    throw new Exception("Tests failed")
  } else {
    System.err.println(s"All tests passed")
  }
}

// Run failed tests, which ensures wave files
//
// @param: jobs A semicolon-separated list of job names of the form $config,$caseName,$runConfig
// @param: output directory of the test results, default to ./test-results
@main
def runFailedTests(jobs: String) = {
  val testRunDir = os.pwd / "testrun"
  val totalJobs = jobs.split(";")
  val failed = totalJobs.zipWithIndex.foreach { case (job, i) => {
    val Array(config, caseName, runCfg) = job.split(",")
    val actualConfig = if (config.endsWith("-trace")) config else s"$config-trace"
    System.err.println(s"[${i+1}/${totalJobs.length}] Running test case $actualConfig,$caseName,$runCfg")
    val handle = os
      .proc("scripts/run-test.py", "-c", actualConfig, "-r", runCfg, "--no-console-log", "--base-out-dir", testRunDir, caseName)
      .call(check=false)
  }}
}

@main
def mergeCycleData() = {
  val original = os.walk(os.pwd / ".github" / "passed")
    .filter(_.last == "passed.json")
    .map(path => {
      val Seq(_, runConfig, config) = path.segments.toSeq.reverse.slice(0, 3)
      (s"$config,$runConfig", ujson.read(os.read(path)))
    })
    .toMap
  os.walk(os.pwd)
    .filter(_.last.endsWith("_cycle.json"))
    .map(path => {
      val Array(config, runConfig, _) = path.last.split("_")
      (s"$config,$runConfig", ujson.read(os.read(path)))
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
      val Array(config, runConfig) = name.split(",")
      os.write.over(
        os.pwd / os.RelPath(s".github/passed/$config/$runConfig/passed.json"),
        ujson.write(data, indent = 2)
      )
    }
  }
}
