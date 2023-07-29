// Generate list of tests that must be passed
def passed(passedFile: os.Path): Seq[String] = {
  val verilatorType = Seq("v1024l8b2-test");
  val runType = Seq("debug");
  verilatorType.flatMap(
    vtype => runType.flatMap(
      rtype => os.read.lines(passedFile).map(
        test => s"verilatorEmulator[$vtype,$test,$rtype].run"
      )
    )
  )
}

def all(root: os.Path): Seq[String] = os.proc("mill", "resolve", "verilatorEmulator[__].Run").call(root).out.text.split("\n").toSeq
// Turn Seq( "A", "B", "C", "D" ) to Seq( "A,B", "C,D" )
def buckets(alltests: Seq[String], bucketSize: Int): Seq[String] = scala.util.Random.shuffle(alltests).grouped(math.ceil(alltests.size.toDouble / bucketSize).toInt).toSeq.map(_.mkString(","))
// Turn Seq( "A,B", "C,D" ) to { "include": [ { "name": "A,B" }, { "name": "C,D" } ] }
def writeJson(buckets: Seq[String], outputFile: os.Path) = os.write.over(outputFile, ujson.Obj("include" -> buckets.map(a => ujson.Obj(s"name" -> ujson.Str(a)))))

// passed.txt String => Array[MillTask String] => Array[MillTaskBucket String] => Json(include: [ name: MillTaskBucket ])
@main
def passedJson(bucketSize: Int, passedFile: os.Path, outputFile: os.Path) = writeJson(buckets(passed(passedFile),bucketSize),outputFile)

@main
def unpassedJson(bucketSize: Int, root: os.Path, passedFile: os.Path, outputFile: os.Path) = writeJson(buckets((all(root).toSet -- passed(passedFile).toSet).toSeq,bucketSize),outputFile)

@main
def allJson(bucketSize: Int, root: os.Path, outputFile: os.Path) = writeJson(buckets(all(root),bucketSize),outputFile)

@main
def runTest(root: os.Path, jobs: String, outputFile: os.Path) = {
  jobs.split(",").foreach(job => os.proc("mill", job).call(root))
  val allJson = os.walk(os.pwd).filter(_.last == "ciRun.json")
  val exitCode = allJson.map(f => ujson.read(os.read(f))("value").num.toInt).reduce(_ max _)
  allJson.map(f => s"|${f.segments.toSeq.dropRight(1).last}|${ujson.Bool(ujson.read(os.read(f))("value").num.toInt == 0)}|\n").foreach(os.write.append(outputFile, _))

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

  if (exitCode != 0) {
    throw new Exception(s"runTest failed with exit code ${exitCode}")
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
    .split(',')
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
// # When $RUNNER=3, this will write the string '{ "include": [ {"name": "task1,task2"}, {"name": "task3,task4"}, {"name": "task5,task6"} ] }'
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
    println(allTasks.mkString(","))
  } else {
    writeJson(buckets(allTasks, bucketSize), outFile.get)
    println(outFile.get)
  }
}
