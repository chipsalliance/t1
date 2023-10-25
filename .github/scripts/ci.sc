import scala.collection.mutable.ArrayBuffer

// Generate `verilatorEmulator` object from the `passed.txt` path.
// A valid passedFile path should be like: /path/to/v1024l8b2-test/debug/passed.txt.
//
// @param passedFile Path to the passed.txt file
def genRunTask(passedFiles: Seq[os.Path]): Seq[String] = {
  passedFiles.flatMap(file => {
    println(s"Generate tests from file: $file")
    val Seq(_, runType, verilatorType) = file.segments.toSeq.reverse.slice(0, 3)
    ujson.read(os.read(file)).obj.keys
      .map(test => s"verilatorEmulator[$verilatorType,$test,$runType].run")
  })
}

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
      println(s"Generate tests from file: $file")
      val Seq(_, run, emu) = file.segments.toSeq.reverse.slice(0, 3)
      ujson
        .read(os.read(file))
        .obj
        .map { case (k, v) =>
          (s"verilatorEmulator[$emu,$k,$run].run", v.num.toInt)
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
def writeJson(buckets: Seq[String], outputFile: os.Path) = 
  os.write.over(outputFile, ujson.Obj("include" ->
    buckets.map(a => ujson.Obj(s"name" -> ujson.Str(a)))))

// Generate a list of grouped tests from the given `defaultPassed` file.
@main
def passedJson(
    bucketSize: Int,
    defaultPassed: os.Path,
    cycleDataPath: os.Path,
    outputFile: os.Path
) =
  writeJson(
    scheduleTasks(
      os.read
        .lines(defaultPassed)
        .map(defaultPassed / os.up / os.RelPath(_)),
      bucketSize
    ),
    outputFile
  )

// Resolve all the executable test and filter out unpassed tests
@main
def unpassedJson(
  bucketSize: Int,
  root: os.Path,
  defaultPassed: os.Path,
  outputFile: os.Path
) = {
  val passedFiles = os.read.lines(defaultPassed).map(os.RelPath(_))
  val unpassedTests = passedFiles.flatMap(file => {
      val Seq(emulator, runCfg, _) = file.segments.toSeq
      val exists = ujson.read(os.read(defaultPassed / os.up / file))
        .obj.keys
        .map(test => s"verilatorEmulator[$emulator,$test,$runCfg].run")
      // Mill will write rubbish into stdout, so we to do a warm up
      os.proc("mill", "-i", "resolve", "__.run").call(cwd = root, stdout = os.Path("/dev/null"))
      val all = os.proc("mill", "-i", "resolve", s"verilatorEmulator[$emulator,_,$runCfg].run")
        .call(root).out.text
        .split("\n")
      (all.toSet -- exists.toSet).toSeq
  })
  writeJson(
    buckets(unpassedTests, bucketSize),
    outputFile
  )
}

// Find the perf.txt file for tests specified in the .github/passed/*/*/perf-cases.txt file,
// and convert them into markdown format.
@main
def convertPerfToMD(root: os.Path) = os
  .walk(root / ".github" / "passed")
  .filter(_.last == "perf-cases.txt")
  .foreach(p => {
    val perfCases = os.read.lines(p).filter(_.length > 0)
    val Seq(emulator, runCfg, _) =
      p.relativeTo(root / ".github" / "passed").segments.toSeq
    val existPerfFile = perfCases
      .filter {testcase =>
        val path = root / os.RelPath(
          s"out/verilatorEmulator/$emulator/$testcase/$runCfg/run.dest/perf.txt"
        )
        os.exists(path)
      }
      .foreach(testcase => {
        val path = root / os.RelPath(
          s"out/verilatorEmulator/$emulator/$testcase/$runCfg/run.dest/perf.txt"
        )
        val output =
          s"""### $testcase
            |
            |* Emulator: $emulator
            |* Run Config: $runCfg
            |
            |```
            |${os.read(path).trim}
            |```
            |\n""".stripMargin
        os.write(root / s"perf-result-$testcase-$emulator-$runCfg.md", output)
      })
  })


def updCycleResult(root: os.Path, task: String, resultDir: os.Path) = {
  val isEmulatorTask = raw"verilatorEmulator\[([^,]+),([^,]+),([^,]+)\].run".r
  task match {
    case isEmulatorTask(e, t, r) => {
      val passedFile = root / os.RelPath(s".github/passed/$e/$r/passed.json")
      val original = ujson.read(os.read(passedFile))
      val totalCycle = raw"total_cycles:\s(\d+)".r

      val newCycleCount = os
        .read
        .lines(root / os.RelPath(s"out/verilatorEmulator/$e/$t/$r/run.dest/perf.txt"))
        .apply(0) match {
          case totalCycle(cycle) => cycle.toInt
          case _ => throw new Exception("perf.txt file is not format as expected")
        }

      val oldCycleCount = original.obj.get(t).map(_.num.toInt).getOrElse(-1)
      val logFile = resultDir / "result.md"
      oldCycleCount match {
        case -1 => os.write.append(logFile, s"* $t: NaN -> $newCycleCount")
        case _ => {
          if (oldCycleCount > newCycleCount) {
            os.write.append(logFile, s"* 📈 $t: $oldCycleCount -> $newCycleCount\n")
          } else if (oldCycleCount < newCycleCount) {
            os.write.append(logFile, s"* 🔻 $t: $oldCycleCount -> $newCycleCount\n")
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
    case _ => {}
  }
}


// This function will split the given jobs with semicolon,
// and attempt to use mill to execute them all.
// If the execution doesn't end successfully, the stdout and sterr will be writed into the loggingDir/fail directory.
//
// ```bash
// # Each test is in the form of `verilatorEmulator[$emulator,$testname,$runcfg]`
// amm .github/scripts/ci.sc $PWD "test1;test2;test3"
// ```
//
// @param: root Path to the project root
// @param: jobs A list of test case concat with semicolon, eg. "taskA;taskB"
// @param: outDir Path to the test output directory. Optional, `root/test-log` by default.
@main
def runTest(root: os.Path, jobs: String, outDir: Option[os.Path]) = {
  var logDir = outDir.getOrElse(root / "test-log")
  os.remove.all(logDir)
  os.makeDir.all(logDir / "fail")
  // we need the unique filename to avoid file overwrite by multi-machine CI
  val md5 = java.security.MessageDigest
    .getInstance("MD5")
    .digest(jobs.getBytes("UTF-8"))
    .map("%02x".format(_))
    .mkString
  os.makeDir.all(logDir / s"result-${md5}")
  val totalJobs = jobs.split(";")
  val failed = totalJobs.zipWithIndex.foldLeft(Seq[String]()) {
    case(failed, (job, i)) => {
      val logPath = os.temp()
      println(s"[${i+1}/${totalJobs.length}] Running test case $job")
      val handle = os
        .proc("mill", "--no-server", job)
        .call(cwd=root,
          check=false,
          stdout=logPath,
          mergeErrIntoOut=true)
      if (handle.exitCode > 0) {
        val trimmedOutput = os.proc("tail", "-n", "100", logPath).call().out.text
        os.write(logDir / "fail" / s"$job.log", trimmedOutput)
        failed :+ job
      } else {
        updCycleResult(root, job, logDir / s"result-${md5}")
        failed
      }
    }
  }

  if (failed.length > 0) {
    os.write.over(logDir / "fail" / s"fail-test-${md5}.md", failed.map(f => s"* $f").mkString("\n"))
    println(s"${failed.length} tests failed:\n${failed.mkString("\n")}")
    throw new Exception("Tests failed")
  } else {
    println(s"All tests passed")
  }
}


// This target will try to build all the test cases in the given testSrcDir and group them into the given output directory.
//
// ```bash
// # Run this in the project root
// amm .github/scripts/ci.sc $PWD/tests ./test-out
// ```
//
// @param testSrcDir Path to the tests directory
// @param outDir Path to the output directory
@main
def buildAllTestCase(testSrcDir: os.Path, outDir: os.Path) = {
  os.remove.all(outDir)
  os.remove.all(testSrcDir / "out")
  os.makeDir.all(outDir)
  val outConfigDir = outDir / "configs"
  os.makeDir.all(outConfigDir)
  val outElfDir = outDir / "cases"
  os.makeDir.all(outElfDir)

  // Mill mistakenly write all the stuff into stdout, so we need to clear all the non-json output first
  // TODO: This will be fixed in mill 0.11.2
  os.proc("mill", "-i", "resolve", "_[_].elf").call(cwd = testSrcDir, stdout = os.Path("/dev/null"))
  val rawJson = os.proc("mill", "--no-server", "-j", "0", "--silent", "show", "_[_].elf")
      .call(testSrcDir).out.text
      .split('\n')
      // Output provided by -j0 that can't be turn off. It looks like [#00] or [#0] based on $(nproc)
      .map(raw"^\[#\d+\]".r.unanchored.replaceFirstIn(_, "").trim)
      .mkString("");
  // Array[String] => Array[os.Path] A list of "ref:id:path", we need the path only.
  ujson.read(rawJson).obj.values
    .map(rawPathRef => os.Path(rawPathRef.str.split(":").apply(3)))
    .foreach(elfPath => {
      val IndexedSeq(module, name) = elfPath.relativeTo(testSrcDir / "out").segments.slice(0, 2)
      val configPath = testSrcDir / "configs" / s"$name-$module.json"
      val origConfig = ujson.read(os.read(configPath))
      origConfig("elf") = ujson.Obj("path" -> s"cases/$module/${elfPath.last}")

      os.makeDir.all(outElfDir / module)
      os.move(elfPath, outElfDir / module / elfPath.last)
      os.write(outConfigDir / s"$name-$module.json", ujson.write(origConfig))
    })
}

@main
def mergeCycleData(root: os.Path) = {
  val original = os.walk(root / ".github" / "passed")
    .filter(_.last == "passed.json")
    .map(path => {
      val Seq(_, run, emu) = path.segments.toSeq.reverse.slice(0, 3)
      (s"$emu,$run", ujson.read(os.read(path)))
    })
    .toMap
  os.walk(root)
    .filter(_.last.endsWith("_cycle.json"))
    .map(path => {
      val Array(emu, run, _) = path.last.split("_")
      (s"$emu,$run", ujson.read(os.read(path)))
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
      val Array(emu, run) = name.split(",")
      os.write.over(
        root / os.RelPath(s".github/passed/$emu/$run/passed.json"),
        ujson.write(data, indent = 2)
      )
    }
  }
}
