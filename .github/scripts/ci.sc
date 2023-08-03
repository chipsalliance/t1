// Generate `verilatorEmulator` object from the `passed.txt` path.
// A valid passedFile path should be like: /path/to/v1024l8b2-test/debug/passed.txt.
//
// @param passedFile Path to the passed.txt file
def genRunTask(passedFiles: Seq[os.Path]): Seq[String] = {
  passedFiles.flatMap(file => {
    println(s"Generate tests from file: $file")
    val Seq(_, runType, verilatorType) = file.segments.toSeq.reverse.slice(0, 3)
    os.read.lines(file)
      .map(test => s"verilatorEmulator[$verilatorType,$test,$runType].run")
  })
}

// Resolve all the executable verilatorEmulator[$vtype,$ttype,$rtype].run object and execute them all.
def all(root: os.Path): Seq[String] = os.proc("mill", "resolve", "verilatorEmulator[__].run").call(root).out.text.split("\n").toSeq

// Merge Seq( "A", "B", "C", "D" ) into Seq( "A;B", "C;D" )
//
// @param allTests The original Seq
// @param bucketSize Specify the size of the output Seq
def buckets(alltests: Seq[String], bucketSize: Int): Seq[String] = 
  scala.util.Random.shuffle(alltests).grouped(
    math.ceil(alltests.size.toDouble / bucketSize).toInt
  ).toSeq.map(_.mkString(";"))

// Turn Seq( "A,B", "C,D" ) to GitHub Action matrix style json: { "include": [ { "name": "A,B" }, { "name": "C,D" } ] }
//
// @param buckets Seq of String that is already packed into bucket using the `buckets` function
// @param outputFile Path to the output json file
def writeJson(buckets: Seq[String], outputFile: os.Path) = 
  os.write.over(outputFile, ujson.Obj("include" -> 
    buckets.map(a => ujson.Obj(s"name" -> ujson.Str(a)))))

// Read the passed.txt file path from the given defaultPassed file,
// split the content of the passed.txt file into list of String and packed them up using the `bucket` function with specified bucket size.
// Write the generated json into given outputFile path.
@main
def passedJson(bucketSize: Int, defaultPassed: os.Path, outputFile: os.Path) =
  writeJson(
    buckets(
      genRunTask(
        os.read.lines(defaultPassed).map(defaultPassed / os.up / os.RelPath(_))
      ),
      bucketSize
    ),
    outputFile
  )

@main
def unpassedJson(
    bucketSize: Int,
    root: os.Path,
    defaultPassed: os.Path,
    outputFile: os.Path
) = writeJson(
  buckets(
    (all(root).toSet -- genRunTask(
      os.read.lines(defaultPassed).map(defaultPassed / os.up / os.RelPath(_))
    ).toSet).toSeq,
    bucketSize
  ),
  outputFile
)

@main
def allJson(bucketSize: Int, root: os.Path, outputFile: os.Path) = writeJson(buckets(all(root),bucketSize),outputFile)

@main
def runPerf(root: os.Path, jobs: String, outputFile: os.Path) = {
  val perfCases = os.read(root / os.RelPath(".github/perf-cases.txt")).split('\n').filter(_.length > 0)
  val existCases = perfCases.filter(c => os.exists(root / os.RelPath(s"out/tests/run/$c/ciRun.dest/perf.txt")))
  existCases.foreach{c =>
    val output = s"""### $c
    |```
    |${os.read(root / os.RelPath(s"out/tests/run/$c/ciRun.dest/perf.txt")).trim}
    |```
    |\n""".stripMargin
    os.write(root / s"perf-result-$c.md", output)
  }
}

// This function will split the given jobs with semicolon,
// and attempt to use mill to execute them all.
// If the execution doesn't end successfully, the stdout and sterr will be writed into the loggingDir/fail directory.
//
// @param: root Path to the project root
// @param: jobs A list of test case concat with semicolon, eg. "taskA;taskB"
// @param: loggingDir Path to the loggin directory. Optional, `root/test-log` by default.
@main
def runTest(root: os.Path, jobs: String, loggingDir: Option[os.Path]) = {
  var logDir = loggingDir.getOrElse(root / "test-log")
  os.makeDir.all(logDir)
  os.makeDir.all(logDir / "fail")
  val totalJobs = jobs.split(";")
  // TODO: Use sliding(n, n) and scala.concurrent to run multiple test in parallel
  val failed = totalJobs.zipWithIndex
    .foldLeft(IndexedSeq[String]())(
      (failed, elem) => {
        val (job, i) = elem
        val logPath = logDir / s"$job.log"
        println(s"[$i/${totalJobs.length}] Running test case $job")
        val handle = os.proc("mill", "--no-server", job).call(
          cwd=root,
          check=false,
          stdout=logPath,
          mergeErrIntoOut=true,
        )
        if (handle.exitCode > 0) {
          println(s"[${i+1}/${totalJobs.length}] Test case $job failed")
          val trimmedOutput = os.proc("tail", "-n", "100", logPath).call().out.text
          os.write(logDir / "fail" / s"$job.log", trimmedOutput)
          failed :+ job
        } else {
          failed
        }
      }
    )

  if (failed.length > 0) {
    println(s"${failed.length} tests failed:\n${failed.mkString("\n")}")
    throw new Exception("Tests failed")
  } else {
    println(s"All tests passed")
  }
}


// This target will try to build all the test cases and put them into the given output directory.
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

  val rawJson = os.proc("mill", "--no-server", "-j", "0", "--silent", "show", "_[_].elf")
      .call(testSrcDir).out.text
      .split('\n')
      // Output provided by -j0 that can't be turn off. It looks like [#00] or [#0] based on $(nproc)
      .map(raw"^\[#\d+\]".r.unanchored.replaceFirstIn(_, "").trim)
      .mkString("");
  // Array[String] => Array[os.Path] A list of "ref:id:path", we need the path only.
  ujson.read(rawJson).arr
    .map(rawPathRef => os.Path(rawPathRef.str.split(":")(2)))
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
