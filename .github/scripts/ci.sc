// Generate `verilatorEmulator` object from the `passed.txt` file.
// To cross product test case between multipl verilator and runtime config,
// simply add new verilator type and runtime config into their own Seq.
//
// @param passedFile Path to the passed.txt file
def passed(passedFile: os.Path): Seq[String] = {
  val verilatorType = Seq("v1024l8b2-test");
  val runType = Seq("debug");
  verilatorType.flatMap(
    vtype => runType.flatMap(
      rtype => os.read.lines(passedFile).map(
        test => {
          val ttype = test.replace(".", "-")
          s"verilatorEmulator[$vtype,$ttype,$rtype].run"
        }
      )
    )
  )
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

// Read the passed.txt file, split the content into list of String and packed them up using the `bucket` function with specified bucket size.
// Write the generated json into given outputFile path.
@main
def passedJson(bucketSize: Int, passedFile: os.Path, outputFile: os.Path) = writeJson(buckets(passed(passedFile),bucketSize),outputFile)

@main
def unpassedJson(bucketSize: Int, root: os.Path, passedFile: os.Path, outputFile: os.Path) = writeJson(buckets((all(root).toSet -- passed(passedFile).toSet).toSeq,bucketSize),outputFile)

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
  val failedCount = totalJobs.zipWithIndex
    .foldLeft(0)(
      (failedCount, elem) => {
        val (job, i) = elem
        val logPath = logDir / s"$job.log"
        println(s"[$i/${totalJobs.length}] Running test case $job")
        val handle = os.proc("mill", "--no-server", job).call(
          cwd=root,
          check=false,
          stdout=logPath,
          mergeErrIntoOut=true,
        )
        if (handle.exitCode != 0) {
          println(s"[$i/${totalJobs.length}] Test case $job failed")
          os.move.into(logPath, logDir / "fail")
          failedCount + 1
        } else {
          failedCount
        }
      }
    )

  if (failedCount > 0) {
    throw new Exception(s"$failedCount tests failed")
  } else {
    println(s"All tests passed")
  }
}

// This run target will try to use comma `,` to split the given `taskBucket` argument into a list of tasks.
// These tasks will be used to build elf and generate its corresbonding test config.
// To generate a GitHub matrix style task bucket, user can call the following `genTestBuckets` run target.
//
// Example:
// 
// ```bash
// amm ci.sc buildTestCases ./test ./test-out "task1,task2,task3,task4"
// ```
//
// Note: Ensure cache (the out directory) is invalidated before calling this function.
//
// @param testSrcDir Specify the source directory of test cases
// @param outDir Specify the destination directory for elf and configs output
// @param taskBucket Comma separated list of test cases to be build
@main
def buildTestCases(testSrcDir: os.Path, outDir: os.Path, taskBucket: String) = {
  // Prepare output directory
  //   $outDir/
  //      configs/
  //      cases/
  //        mlir/
  //        asm/
  //        .../
  os.makeDir.all(outDir)
  val outConfigDir = outDir / "configs"
  os.makeDir.all(outConfigDir)
  val outElfDir = outDir / "cases"
  os.makeDir.all(outElfDir)

  taskBucket
    .split(';')
    .foreach(task => {
    // Compile and get abosolute path to the final elf binary
    val rawElfPath = os.proc("mill", "--no-server", "show", s"$task.elf").call(testSrcDir).out.text
    // PathRef => os.Path
    val elfPath = os.Path(ujson.read(rawElfPath).str.split(':')(2))
    // Get original test config for this task
    val rawTestConfig = os.proc("mill", "--no-server", "show", s"$task.testConfig").call(testSrcDir).out.text
    val testConfig = ujson.read(rawTestConfig)
    val taskType = testConfig("type").str
    // Insert binary path into the test config { elf: { path: "cases/$type/$elf" } }
    testConfig("elf") = ujson.Obj("path" -> s"cases/$taskType/${elfPath.last}")

    val testCategory = outElfDir / taskType
    os.makeDir.all(testCategory)
    os.move(elfPath, testCategory / elfPath.last)

    os.write(outConfigDir / s"${elfPath.baseName}-$taskType.json", ujson.write(testConfig))
  })
}

// This run target will try to resolve all the runnable test target and collect them as a sequence of String.
// Then split them into multiple bucket to parallel those build target.
// The generated task bucket will be written into the file specify in the second parameter `outFile`
// in GitHub Action Matrix json style.
//
// Example:
//
// ```bash
// # When $RUNNER=2, this will write the string
// # '{ "include": [ {"name": "bucket0", "tests": "task1,task2"}, {"name": "bucket1", "tests": "task3,task4"} ] }'
// # into test-case-matrix.json.
// amm ci.sc genTestBuckets ./tests $RUNNER ./test-case-matrix.json
// ```
// 
// @param testSrcDir Specify the source directory of all test cases
// @param bucketSize Specify the number of buckets for parallel testing
// @param outFile Optional. Specify the filepath where the output json is written
@main
def genTestBuckets(testSrcDir: os.Path, bucketSize: Int, outFile: Option[os.Path]) = {
  val allTasks = os.proc("mill", "--no-server", "resolve", "_[_]")
    .call(testSrcDir).out.text
    .split('\n')
    .toSeq
  // If user doesn't specify outFile path, we might running this target in local environment.
  // In this case, we might also want to compile all the task together.
  if (outFile.isEmpty) {
    println(allTasks.mkString(";"))
  } else {
    val genBuckets = buckets(allTasks, bucketSize)
    val json = ujson.Obj("include" -> genBuckets.zipWithIndex.map(
      elem => {
        val (tests, i) = elem
        ujson.Obj(s"name" -> ujson.Str(s"bucket$i"), s"tests" -> ujson.Str(tests))
      }
    ))
    os.write.over(outFile.get, json)
    println(outFile.get)
  }
}
