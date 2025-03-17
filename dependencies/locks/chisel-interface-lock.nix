{ fetchurl }: {

  "org.json4s_json4s-native_2.13-4.0.6" = fetchurl {
    name = "org.json4s_json4s-native_2.13-4.0.6";
    hash = "sha256-XUzKONu0ZyQRFYQgSJMdZZ6EWIcfGcMznRj5oEPkJQk=";
    url = "https://repo1.maven.org/maven2/org/json4s/json4s-native_2.13/4.0.6/json4s-native_2.13-4.0.6.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/json4s-native_2.13-4.0.6.pom"
            
      downloadedFile=$TMPDIR/json4s-native_2.13-4.0.6.jar
      tryDownload "https://repo1.maven.org/maven2/org/json4s/json4s-native_2.13/4.0.6/json4s-native_2.13-4.0.6.jar"
      cp -v "$TMPDIR/json4s-native_2.13-4.0.6.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-native_2.13/4.0.6";
  };

  "com.github.plokhotnyuk.jsoniter-scala_jsoniter-scala-core_2.13-2.13.5.2" = fetchurl {
    name = "com.github.plokhotnyuk.jsoniter-scala_jsoniter-scala-core_2.13-2.13.5.2";
    hash = "sha256-XEP6s9Yt6W4PRIC7jIUkDYZ5htlM1ndxEsF6UQPSUwM=";
    url = "https://repo1.maven.org/maven2/com/github/plokhotnyuk/jsoniter-scala/jsoniter-scala-core_2.13/2.13.5.2/jsoniter-scala-core_2.13-2.13.5.2.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jsoniter-scala-core_2.13-2.13.5.2.pom"
            
      downloadedFile=$TMPDIR/jsoniter-scala-core_2.13-2.13.5.2.jar
      tryDownload "https://repo1.maven.org/maven2/com/github/plokhotnyuk/jsoniter-scala/jsoniter-scala-core_2.13/2.13.5.2/jsoniter-scala-core_2.13-2.13.5.2.jar"
      cp -v "$TMPDIR/jsoniter-scala-core_2.13-2.13.5.2.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/github/plokhotnyuk/jsoniter-scala/jsoniter-scala-core_2.13/2.13.5.2";
  };

  "com.outr_scribe_2.13-3.13.0" = fetchurl {
    name = "com.outr_scribe_2.13-3.13.0";
    hash = "sha256-LFVZJmr2Mzn9ozx9rZApo1cRhcH15kL0RrkRnBIH/8s=";
    url = "https://repo1.maven.org/maven2/com/outr/scribe_2.13/3.13.0/scribe_2.13-3.13.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/scribe_2.13-3.13.0.pom"
            
      downloadedFile=$TMPDIR/scribe_2.13-3.13.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/outr/scribe_2.13/3.13.0/scribe_2.13-3.13.0.jar"
      cp -v "$TMPDIR/scribe_2.13-3.13.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/outr/scribe_2.13/3.13.0";
  };

  "org.apache.commons_commons-compress-1.24.0" = fetchurl {
    name = "org.apache.commons_commons-compress-1.24.0";
    hash = "sha256-IFKadLhVuLV6th587VCUPWPAh+RscgP54/65Dn5j6OU=";
    url = "https://repo1.maven.org/maven2/org/apache/commons/commons-compress/1.24.0/commons-compress-1.24.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/commons-compress-1.24.0.pom"
            
      downloadedFile=$TMPDIR/commons-compress-1.24.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/apache/commons/commons-compress/1.24.0/commons-compress-1.24.0.jar"
      cp -v "$TMPDIR/commons-compress-1.24.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-compress/1.24.0";
  };

  "com.lihaoyi_upickle_2.13-3.3.1" = fetchurl {
    name = "com.lihaoyi_upickle_2.13-3.3.1";
    hash = "sha256-1vHU3mGQey3zvyUHK9uCx+9pUnpnWe3zEMlyb8QqUFc=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/upickle_2.13/3.3.1/upickle_2.13-3.3.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/upickle_2.13-3.3.1.pom"
            
      downloadedFile=$TMPDIR/upickle_2.13-3.3.1.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/upickle_2.13/3.3.1/upickle_2.13-3.3.1.jar"
      cp -v "$TMPDIR/upickle_2.13-3.3.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle_2.13/3.3.1";
  };

  "org.chipsalliance_firtool-resolver_2.13-2.0.1" = fetchurl {
    name = "org.chipsalliance_firtool-resolver_2.13-2.0.1";
    hash = "sha256-CGJ1TtugVYKbdzR1NWZunPLyxQRgKZPGQPWhTOGOeHI=";
    url = "https://repo1.maven.org/maven2/org/chipsalliance/firtool-resolver_2.13/2.0.1/firtool-resolver_2.13-2.0.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/firtool-resolver_2.13-2.0.1.pom"
            
      downloadedFile=$TMPDIR/firtool-resolver_2.13-2.0.1.jar
      tryDownload "https://repo1.maven.org/maven2/org/chipsalliance/firtool-resolver_2.13/2.0.1/firtool-resolver_2.13-2.0.1.jar"
      cp -v "$TMPDIR/firtool-resolver_2.13-2.0.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/chipsalliance/firtool-resolver_2.13/2.0.1";
  };

  "org.junit_junit-bom-5.10.0" = fetchurl {
    name = "org.junit_junit-bom-5.10.0";
    hash = "sha256-luQjQgOITEqh2Y+/2XwfXzgggI8aRglNmIXZGpcJEgY=";
    url = "https://repo1.maven.org/maven2/org/junit/junit-bom/5.10.0/junit-bom-5.10.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/junit-bom-5.10.0.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.10.0";
  };

  "org.codehaus.plexus_plexus-archiver-4.9.0" = fetchurl {
    name = "org.codehaus.plexus_plexus-archiver-4.9.0";
    hash = "sha256-N9iDDf7SWw+dcNU8ACkWDnhcSLnXCOJv/ghovWidXw8=";
    url = "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-archiver/4.9.0/plexus-archiver-4.9.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/plexus-archiver-4.9.0.pom"
            
      downloadedFile=$TMPDIR/plexus-archiver-4.9.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-archiver/4.9.0/plexus-archiver-4.9.0.jar"
      cp -v "$TMPDIR/plexus-archiver-4.9.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus-archiver/4.9.0";
  };

  "com.lmax_disruptor-3.4.2" = fetchurl {
    name = "com.lmax_disruptor-3.4.2";
    hash = "sha256-nbZsn6zL8HaJOrkMiWwvCuHQumcNQYA8e6QrAjXKKKg=";
    url = "https://repo1.maven.org/maven2/com/lmax/disruptor/3.4.2/disruptor-3.4.2.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/disruptor-3.4.2.pom"
            
      downloadedFile=$TMPDIR/disruptor-3.4.2.jar
      tryDownload "https://repo1.maven.org/maven2/com/lmax/disruptor/3.4.2/disruptor-3.4.2.jar"
      cp -v "$TMPDIR/disruptor-3.4.2.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lmax/disruptor/3.4.2";
  };

  "org.apache.logging.log4j_log4j-core-2.24.3" = fetchurl {
    name = "org.apache.logging.log4j_log4j-core-2.24.3";
    hash = "sha256-kRXpkDJtXT0VoEyxj5hIc8Z8foh8rKnFqCpjohdh5LQ=";
    url = "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.24.3/log4j-core-2.24.3.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/log4j-core-2.24.3.pom"
            
      downloadedFile=$TMPDIR/log4j-core-2.24.3.jar
      tryDownload "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.24.3/log4j-core-2.24.3.jar"
      cp -v "$TMPDIR/log4j-core-2.24.3.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.24.3";
  };

  "com.lihaoyi_os-lib_2.13-0.10.0" = fetchurl {
    name = "com.lihaoyi_os-lib_2.13-0.10.0";
    hash = "sha256-QMLhQLNthyscyl83Zy+UB/mGwa+7JpM0upwj3oxg7Fw=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.10.0/os-lib_2.13-0.10.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/os-lib_2.13-0.10.0.pom"
            
      downloadedFile=$TMPDIR/os-lib_2.13-0.10.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.10.0/os-lib_2.13-0.10.0.jar"
      cp -v "$TMPDIR/os-lib_2.13-0.10.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.10.0";
  };

  "org.scala-lang.modules_scala-xml_2.13-2.3.0" = fetchurl {
    name = "org.scala-lang.modules_scala-xml_2.13-2.3.0";
    hash = "sha256-TZaDZ9UjQB20IMvxqxub63LbqSNDMAhFDRtYfvbzI58=";
    url = "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.3.0/scala-xml_2.13-2.3.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/scala-xml_2.13-2.3.0.pom"
            
      downloadedFile=$TMPDIR/scala-xml_2.13-2.3.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.3.0/scala-xml_2.13-2.3.0.jar"
      cp -v "$TMPDIR/scala-xml_2.13-2.3.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.3.0";
  };

  "com.lihaoyi_sourcecode_2.13-0.3.1" = fetchurl {
    name = "com.lihaoyi_sourcecode_2.13-0.3.1";
    hash = "sha256-myEHnVSI+Bd93OCgAPfzKze9q3W2lvGYonbnqD+wFsY=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.13/0.3.1/sourcecode_2.13-0.3.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/sourcecode_2.13-0.3.1.pom"
            
      downloadedFile=$TMPDIR/sourcecode_2.13-0.3.1.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.13/0.3.1/sourcecode_2.13-0.3.1.jar"
      cp -v "$TMPDIR/sourcecode_2.13-0.3.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.13/0.3.1";
  };

  "com.lihaoyi_os-lib_2.13-0.11.4-M6" = fetchurl {
    name = "com.lihaoyi_os-lib_2.13-0.11.4-M6";
    hash = "sha256-Xfo/y+4tKe7wAUFY1nyyh9is8M0l4sYU4OnheEljEL8=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.11.4-M6/os-lib_2.13-0.11.4-M6.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/os-lib_2.13-0.11.4-M6.pom"
            
      downloadedFile=$TMPDIR/os-lib_2.13-0.11.4-M6.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.11.4-M6/os-lib_2.13-0.11.4-M6.jar"
      cp -v "$TMPDIR/os-lib_2.13-0.11.4-M6.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.11.4-M6";
  };

  "org.apache.logging.log4j_log4j-2.24.3" = fetchurl {
    name = "org.apache.logging.log4j_log4j-2.24.3";
    hash = "sha256-bWuk6kxsiWW675JezWblZ8RdkKFg9C/3CgzdMGJr1Z8=";
    url = "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j/2.24.3/log4j-2.24.3.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/log4j-2.24.3.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/logging/log4j/log4j/2.24.3";
  };

  "org.scala-sbt_core-macros_2.13-1.10.7" = fetchurl {
    name = "org.scala-sbt_core-macros_2.13-1.10.7";
    hash = "sha256-rsDP4K+yiTgLhmdDP7G5iL3i43v+Dwki9pKXPeWUp4c=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/core-macros_2.13/1.10.7/core-macros_2.13-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/core-macros_2.13-1.10.7.pom"
            
      downloadedFile=$TMPDIR/core-macros_2.13-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/core-macros_2.13/1.10.7/core-macros_2.13-1.10.7.jar"
      cp -v "$TMPDIR/core-macros_2.13-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/core-macros_2.13/1.10.7";
  };

  "org.scala-lang_scala-reflect-2.13.15" = fetchurl {
    name = "org.scala-lang_scala-reflect-2.13.15";
    hash = "sha256-zmUU4hTEf5HC311UaNIHmzjSwWSbjXn6DyPP7ZzFy/8=";
    url = "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.15/scala-reflect-2.13.15.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/scala-reflect-2.13.15.pom"
            
      downloadedFile=$TMPDIR/scala-reflect-2.13.15.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.15/scala-reflect-2.13.15.jar"
      cp -v "$TMPDIR/scala-reflect-2.13.15.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.15";
  };

  "io.get-coursier_coursier_2.13-2.1.8" = fetchurl {
    name = "io.get-coursier_coursier_2.13-2.1.8";
    hash = "sha256-b5fncEkG5SQcW5J9UTBJR70KKV2pIiZcX5qMFDsFvQE=";
    url = "https://repo1.maven.org/maven2/io/get-coursier/coursier_2.13/2.1.8/coursier_2.13-2.1.8.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/coursier_2.13-2.1.8.pom"
            
      downloadedFile=$TMPDIR/coursier_2.13-2.1.8.jar
      tryDownload "https://repo1.maven.org/maven2/io/get-coursier/coursier_2.13/2.1.8/coursier_2.13-2.1.8.jar"
      cp -v "$TMPDIR/coursier_2.13-2.1.8.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/io/get-coursier/coursier_2.13/2.1.8";
  };

  "org.apache_apache-30" = fetchurl {
    name = "org.apache_apache-30";
    hash = "sha256-Wo5syVryUH2A6IG2gydSxmAb8DYNxV6MmKxGHd1FxcE=";
    url = "https://repo1.maven.org/maven2/org/apache/apache/30/apache-30.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/apache-30.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/apache/30";
  };

  "org.junit_junit-bom-5.9.3" = fetchurl {
    name = "org.junit_junit-bom-5.9.3";
    hash = "sha256-X9DjgXGbAVQU9wJfHfw6JGAGx/jhvbklGM2h4V/lOi4=";
    url = "https://repo1.maven.org/maven2/org/junit/junit-bom/5.9.3/junit-bom-5.9.3.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/junit-bom-5.9.3.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.9.3";
  };

  "com.lihaoyi_pprint_2.13-0.9.0" = fetchurl {
    name = "com.lihaoyi_pprint_2.13-0.9.0";
    hash = "sha256-RUmk2jO7irTaoMYgRK6Ui/SeyLEFCAspCehIccoQoeE=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/pprint_2.13/0.9.0/pprint_2.13-0.9.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/pprint_2.13-0.9.0.pom"
            
      downloadedFile=$TMPDIR/pprint_2.13-0.9.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/pprint_2.13/0.9.0/pprint_2.13-0.9.0.jar"
      cp -v "$TMPDIR/pprint_2.13-0.9.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/pprint_2.13/0.9.0";
  };

  "com.lihaoyi_scalac-mill-moduledefs-plugin_2.13.15-0.11.2" = fetchurl {
    name = "com.lihaoyi_scalac-mill-moduledefs-plugin_2.13.15-0.11.2";
    hash = "sha256-l+0NBdEWKEMILT44bK6ohiaon09cQvORWtDrCjUkn0A=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/scalac-mill-moduledefs-plugin_2.13.15/0.11.2/scalac-mill-moduledefs-plugin_2.13.15-0.11.2.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/scalac-mill-moduledefs-plugin_2.13.15-0.11.2.pom"
            
      downloadedFile=$TMPDIR/scalac-mill-moduledefs-plugin_2.13.15-0.11.2.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/scalac-mill-moduledefs-plugin_2.13.15/0.11.2/scalac-mill-moduledefs-plugin_2.13.15-0.11.2.jar"
      cp -v "$TMPDIR/scalac-mill-moduledefs-plugin_2.13.15-0.11.2.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/scalac-mill-moduledefs-plugin_2.13.15/0.11.2";
  };

  "net.openhft_java-parent-pom-1.1.28" = fetchurl {
    name = "net.openhft_java-parent-pom-1.1.28";
    hash = "sha256-d7bOKP/hHJElmDQtIbblYDHRc8LCpqkt5Zl8aHp7l88=";
    url = "https://repo1.maven.org/maven2/net/openhft/java-parent-pom/1.1.28/java-parent-pom-1.1.28.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/java-parent-pom-1.1.28.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/net/openhft/java-parent-pom/1.1.28";
  };

  "org.scala-sbt_zinc-classpath_2.13-1.10.7" = fetchurl {
    name = "org.scala-sbt_zinc-classpath_2.13-1.10.7";
    hash = "sha256-ozsxGbCrycacvLvk8tf0SHjdR9DU+5+494IJdkotRjg=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/zinc-classpath_2.13/1.10.7/zinc-classpath_2.13-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/zinc-classpath_2.13-1.10.7.pom"
            
      downloadedFile=$TMPDIR/zinc-classpath_2.13-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/zinc-classpath_2.13/1.10.7/zinc-classpath_2.13-1.10.7.jar"
      cp -v "$TMPDIR/zinc-classpath_2.13-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-classpath_2.13/1.10.7";
  };

  "com.lihaoyi_mill-moduledefs_2.13-0.11.2" = fetchurl {
    name = "com.lihaoyi_mill-moduledefs_2.13-0.11.2";
    hash = "sha256-aTQtCHGjdmdIWSZgyv6EllswGet1c2gKJ0nuGxu+TpA=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/mill-moduledefs_2.13/0.11.2/mill-moduledefs_2.13-0.11.2.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/mill-moduledefs_2.13-0.11.2.pom"
            
      downloadedFile=$TMPDIR/mill-moduledefs_2.13-0.11.2.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/mill-moduledefs_2.13/0.11.2/mill-moduledefs_2.13-0.11.2.jar"
      cp -v "$TMPDIR/mill-moduledefs_2.13-0.11.2.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-moduledefs_2.13/0.11.2";
  };

  "org.scala-sbt_compiler-interface-1.10.3" = fetchurl {
    name = "org.scala-sbt_compiler-interface-1.10.3";
    hash = "sha256-eUpVhTZhe/6qSWs+XkD7bDhrqCv893HCNme7G4yPyeg=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.3/compiler-interface-1.10.3.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/compiler-interface-1.10.3.pom"
            
      downloadedFile=$TMPDIR/compiler-interface-1.10.3.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.3/compiler-interface-1.10.3.jar"
      cp -v "$TMPDIR/compiler-interface-1.10.3.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.3";
  };

  "org.scala-sbt_test-interface-1.0" = fetchurl {
    name = "org.scala-sbt_test-interface-1.0";
    hash = "sha256-Cc5Q+4mULLHRdw+7Wjx6spCLbKrckXHeNYjIibw4LWw=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/test-interface/1.0/test-interface-1.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/test-interface-1.0.pom"
            
      downloadedFile=$TMPDIR/test-interface-1.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/test-interface/1.0/test-interface-1.0.jar"
      cp -v "$TMPDIR/test-interface-1.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/test-interface/1.0";
  };

  "com.github.nscala-time_nscala-time_2.13-2.22.0" = fetchurl {
    name = "com.github.nscala-time_nscala-time_2.13-2.22.0";
    hash = "sha256-jdYQyBD4bJT5AYOMWHZEvP+3q5tR1UUYC5qlUGfuEYA=";
    url = "https://repo1.maven.org/maven2/com/github/nscala-time/nscala-time_2.13/2.22.0/nscala-time_2.13-2.22.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/nscala-time_2.13-2.22.0.pom"
            
      downloadedFile=$TMPDIR/nscala-time_2.13-2.22.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/github/nscala-time/nscala-time_2.13/2.22.0/nscala-time_2.13-2.22.0.jar"
      cp -v "$TMPDIR/nscala-time_2.13-2.22.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/github/nscala-time/nscala-time_2.13/2.22.0";
  };

  "org.scala-sbt_zinc-compile-core_2.13-1.10.7" = fetchurl {
    name = "org.scala-sbt_zinc-compile-core_2.13-1.10.7";
    hash = "sha256-E7vR41TZnHQWM6FyVr48WDhplRHFfFta4E4JEl8/CtQ=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/zinc-compile-core_2.13/1.10.7/zinc-compile-core_2.13-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/zinc-compile-core_2.13-1.10.7.pom"
            
      downloadedFile=$TMPDIR/zinc-compile-core_2.13-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/zinc-compile-core_2.13/1.10.7/zinc-compile-core_2.13-1.10.7.jar"
      cp -v "$TMPDIR/zinc-compile-core_2.13-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-compile-core_2.13/1.10.7";
  };

  "com.lihaoyi_mill-scalalib-worker_2.13-0.12.8-1-46e216" = fetchurl {
    name = "com.lihaoyi_mill-scalalib-worker_2.13-0.12.8-1-46e216";
    hash = "sha256-SJG7mGWhe+4a2xkmFWQqn/QUBb+RYMpSdB7b1jv7JQw=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/mill-scalalib-worker_2.13/0.12.8-1-46e216/mill-scalalib-worker_2.13-0.12.8-1-46e216.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/mill-scalalib-worker_2.13-0.12.8-1-46e216.pom"
            
      downloadedFile=$TMPDIR/mill-scalalib-worker_2.13-0.12.8-1-46e216.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/mill-scalalib-worker_2.13/0.12.8-1-46e216/mill-scalalib-worker_2.13-0.12.8-1-46e216.jar"
      cp -v "$TMPDIR/mill-scalalib-worker_2.13-0.12.8-1-46e216.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-scalalib-worker_2.13/0.12.8-1-46e216";
  };

  "joda-time_joda-time-2.10.1" = fetchurl {
    name = "joda-time_joda-time-2.10.1";
    hash = "sha256-u5xaDYvjUa4Xwac9jy9YpKOIGma2Fv+B43oNVAiOzQ0=";
    url = "https://repo1.maven.org/maven2/joda-time/joda-time/2.10.1/joda-time-2.10.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/joda-time-2.10.1.pom"
            
      downloadedFile=$TMPDIR/joda-time-2.10.1.jar
      tryDownload "https://repo1.maven.org/maven2/joda-time/joda-time/2.10.1/joda-time-2.10.1.jar"
      cp -v "$TMPDIR/joda-time-2.10.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/joda-time/joda-time/2.10.1";
  };

  "org.jline_jline-terminal-3.27.1" = fetchurl {
    name = "org.jline_jline-terminal-3.27.1";
    hash = "sha256-WV77BAEncauTljUBrlYi9v3GxDDeskqQpHHD9Fdbqjw=";
    url = "https://repo1.maven.org/maven2/org/jline/jline-terminal/3.27.1/jline-terminal-3.27.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jline-terminal-3.27.1.pom"
            
      downloadedFile=$TMPDIR/jline-terminal-3.27.1.jar
      tryDownload "https://repo1.maven.org/maven2/org/jline/jline-terminal/3.27.1/jline-terminal-3.27.1.jar"
      cp -v "$TMPDIR/jline-terminal-3.27.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/jline/jline-terminal/3.27.1";
  };

  "com.thoughtworks.paranamer_paranamer-parent-2.8" = fetchurl {
    name = "com.thoughtworks.paranamer_paranamer-parent-2.8";
    hash = "sha256-+LBfeaWVmAiGP63PaqEd/M5Ks3RLQQyFanUgMAy8mkU=";
    url = "https://repo1.maven.org/maven2/com/thoughtworks/paranamer/paranamer-parent/2.8/paranamer-parent-2.8.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/paranamer-parent-2.8.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/thoughtworks/paranamer/paranamer-parent/2.8";
  };

  "org.scala-sbt_compiler-interface-1.10.7" = fetchurl {
    name = "org.scala-sbt_compiler-interface-1.10.7";
    hash = "sha256-nFVs4vEVTEPSiGce3C77TTjvffSU+SMrn9KgV9xGVP0=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.7/compiler-interface-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/compiler-interface-1.10.7.pom"
            
      downloadedFile=$TMPDIR/compiler-interface-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.7/compiler-interface-1.10.7.jar"
      cp -v "$TMPDIR/compiler-interface-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.7";
  };

  "com.eed3si9n_sjson-new-scalajson_2.13-0.10.1" = fetchurl {
    name = "com.eed3si9n_sjson-new-scalajson_2.13-0.10.1";
    hash = "sha256-DBGJ34c7lyt3m4o5ULwsRk1xPqtHHHKcNgU4nlO/dJY=";
    url = "https://repo1.maven.org/maven2/com/eed3si9n/sjson-new-scalajson_2.13/0.10.1/sjson-new-scalajson_2.13-0.10.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/sjson-new-scalajson_2.13-0.10.1.pom"
            
      downloadedFile=$TMPDIR/sjson-new-scalajson_2.13-0.10.1.jar
      tryDownload "https://repo1.maven.org/maven2/com/eed3si9n/sjson-new-scalajson_2.13/0.10.1/sjson-new-scalajson_2.13-0.10.1.jar"
      cp -v "$TMPDIR/sjson-new-scalajson_2.13-0.10.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/eed3si9n/sjson-new-scalajson_2.13/0.10.1";
  };

  "io.get-coursier.jniutils_windows-jni-utils-0.3.3" = fetchurl {
    name = "io.get-coursier.jniutils_windows-jni-utils-0.3.3";
    hash = "sha256-OgBT8ULqeyvpNMGSmXrwpYXR4VOAlmSIMs+BejCP56c=";
    url = "https://repo1.maven.org/maven2/io/get-coursier/jniutils/windows-jni-utils/0.3.3/windows-jni-utils-0.3.3.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/windows-jni-utils-0.3.3.pom"
            
      downloadedFile=$TMPDIR/windows-jni-utils-0.3.3.jar
      tryDownload "https://repo1.maven.org/maven2/io/get-coursier/jniutils/windows-jni-utils/0.3.3/windows-jni-utils-0.3.3.jar"
      cp -v "$TMPDIR/windows-jni-utils-0.3.3.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/io/get-coursier/jniutils/windows-jni-utils/0.3.3";
  };

  "jakarta.platform_jakartaee-api-parent-9.1.0" = fetchurl {
    name = "jakarta.platform_jakartaee-api-parent-9.1.0";
    hash = "sha256-FrD7N30UkkRSQtD3+FPOC1fH2qrNnJw6UZQ/hNFXWrA=";
    url = "https://repo1.maven.org/maven2/jakarta/platform/jakartaee-api-parent/9.1.0/jakartaee-api-parent-9.1.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jakartaee-api-parent-9.1.0.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/jakarta/platform/jakartaee-api-parent/9.1.0";
  };

  "io.github.java-diff-utils_java-diff-utils-4.12" = fetchurl {
    name = "io.github.java-diff-utils_java-diff-utils-4.12";
    hash = "sha256-SMNRfv+BvfxjgwFH0fHU16fd1bDn/QMrPQN8Eyb6deA=";
    url = "https://repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils/4.12/java-diff-utils-4.12.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/java-diff-utils-4.12.pom"
            
      downloadedFile=$TMPDIR/java-diff-utils-4.12.jar
      tryDownload "https://repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils/4.12/java-diff-utils-4.12.jar"
      cp -v "$TMPDIR/java-diff-utils-4.12.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils/4.12";
  };

  "org.chipsalliance_chisel_2.13-6.6.0" = fetchurl {
    name = "org.chipsalliance_chisel_2.13-6.6.0";
    hash = "sha256-4enMFpmUGLE6NV0cqQ57M/5UW81ujay3QcHnH+333fw=";
    url = "https://repo1.maven.org/maven2/org/chipsalliance/chisel_2.13/6.6.0/chisel_2.13-6.6.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/chisel_2.13-6.6.0.pom"
            
      downloadedFile=$TMPDIR/chisel_2.13-6.6.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/chipsalliance/chisel_2.13/6.6.0/chisel_2.13-6.6.0.jar"
      cp -v "$TMPDIR/chisel_2.13-6.6.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/chipsalliance/chisel_2.13/6.6.0";
  };

  "org.fusesource.jansi_jansi-2.4.1" = fetchurl {
    name = "org.fusesource.jansi_jansi-2.4.1";
    hash = "sha256-M9G+H9TA5eB6NwlBmDP0ghxZzjbvLimPXNRZHyxJXac=";
    url = "https://repo1.maven.org/maven2/org/fusesource/jansi/jansi/2.4.1/jansi-2.4.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jansi-2.4.1.pom"
            
      downloadedFile=$TMPDIR/jansi-2.4.1.jar
      tryDownload "https://repo1.maven.org/maven2/org/fusesource/jansi/jansi/2.4.1/jansi-2.4.1.jar"
      cp -v "$TMPDIR/jansi-2.4.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/fusesource/jansi/jansi/2.4.1";
  };

  "org.tukaani_xz-1.9" = fetchurl {
    name = "org.tukaani_xz-1.9";
    hash = "sha256-qS7mXrLbWChlkYWhtNTIEPFzgTW6ZMdLoD2a2HzwrHo=";
    url = "https://repo1.maven.org/maven2/org/tukaani/xz/1.9/xz-1.9.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/xz-1.9.pom"
            
      downloadedFile=$TMPDIR/xz-1.9.jar
      tryDownload "https://repo1.maven.org/maven2/org/tukaani/xz/1.9/xz-1.9.jar"
      cp -v "$TMPDIR/xz-1.9.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/tukaani/xz/1.9";
  };

  "com.github.lolgab_mill-mima_mill0.11_2.13-0.0.23" = fetchurl {
    name = "com.github.lolgab_mill-mima_mill0.11_2.13-0.0.23";
    hash = "sha256-NROI7sYrbrmiQ3csj2Ht0YIUAbyZ5VYbL1h+Mge/wvo=";
    url = "https://repo1.maven.org/maven2/com/github/lolgab/mill-mima_mill0.11_2.13/0.0.23/mill-mima_mill0.11_2.13-0.0.23.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/mill-mima_mill0.11_2.13-0.0.23.pom"
            
      downloadedFile=$TMPDIR/mill-mima_mill0.11_2.13-0.0.23.jar
      tryDownload "https://repo1.maven.org/maven2/com/github/lolgab/mill-mima_mill0.11_2.13/0.0.23/mill-mima_mill0.11_2.13-0.0.23.jar"
      cp -v "$TMPDIR/mill-mima_mill0.11_2.13-0.0.23.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/github/lolgab/mill-mima_mill0.11_2.13/0.0.23";
  };

  "org.json4s_json4s-ast_2.13-4.0.6" = fetchurl {
    name = "org.json4s_json4s-ast_2.13-4.0.6";
    hash = "sha256-vStiE4Lymy+nSqIlQiQ8rKvb9NDJqhca+kUWRKKt3xQ=";
    url = "https://repo1.maven.org/maven2/org/json4s/json4s-ast_2.13/4.0.6/json4s-ast_2.13-4.0.6.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/json4s-ast_2.13-4.0.6.pom"
            
      downloadedFile=$TMPDIR/json4s-ast_2.13-4.0.6.jar
      tryDownload "https://repo1.maven.org/maven2/org/json4s/json4s-ast_2.13/4.0.6/json4s-ast_2.13-4.0.6.jar"
      cp -v "$TMPDIR/json4s-ast_2.13-4.0.6.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-ast_2.13/4.0.6";
  };

  "org.apache.geronimo.genesis_genesis-2.0" = fetchurl {
    name = "org.apache.geronimo.genesis_genesis-2.0";
    hash = "sha256-lcX5R64+07kRLqpdfkay87hJI6ykVn/wUXs142Elips=";
    url = "https://repo1.maven.org/maven2/org/apache/geronimo/genesis/genesis/2.0/genesis-2.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/genesis-2.0.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/geronimo/genesis/genesis/2.0";
  };

  "io.github.java-diff-utils_java-diff-utils-parent-4.12" = fetchurl {
    name = "io.github.java-diff-utils_java-diff-utils-parent-4.12";
    hash = "sha256-l9MekOAkDQrHpgMMLkbZQJtiaSmyE7h0XneiHciAFOI=";
    url = "https://repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils-parent/4.12/java-diff-utils-parent-4.12.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/java-diff-utils-parent-4.12.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils-parent/4.12";
  };

  "org.apache.commons_commons-lang3-3.12.0" = fetchurl {
    name = "org.apache.commons_commons-lang3-3.12.0";
    hash = "sha256-EfwT+UqhFa+0KTUpZLY3Gez4ywqDu3vEl61xKJuUn0Y=";
    url = "https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/commons-lang3-3.12.0.pom"
            
      downloadedFile=$TMPDIR/commons-lang3-3.12.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar"
      cp -v "$TMPDIR/commons-lang3-3.12.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.12.0";
  };

  "org.apache.geronimo.genesis_genesis-default-flava-2.0" = fetchurl {
    name = "org.apache.geronimo.genesis_genesis-default-flava-2.0";
    hash = "sha256-jkGo9ePZSnxqcIOQIuAz1ZTPNjjx2vc01oxtt6EJuUk=";
    url = "https://repo1.maven.org/maven2/org/apache/geronimo/genesis/genesis-default-flava/2.0/genesis-default-flava-2.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/genesis-default-flava-2.0.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/geronimo/genesis/genesis-default-flava/2.0";
  };

  "org.scala-sbt_collections_2.13-1.10.7" = fetchurl {
    name = "org.scala-sbt_collections_2.13-1.10.7";
    hash = "sha256-y4FuwehuxB+70YBIKj5jH9L8tQpHrWFpPc9VrBUzM6Y=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/collections_2.13/1.10.7/collections_2.13-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/collections_2.13-1.10.7.pom"
            
      downloadedFile=$TMPDIR/collections_2.13-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/collections_2.13/1.10.7/collections_2.13-1.10.7.jar"
      cp -v "$TMPDIR/collections_2.13-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/collections_2.13/1.10.7";
  };

  "org.apache_apache-6" = fetchurl {
    name = "org.apache_apache-6";
    hash = "sha256-A7aDRlGjS4P3/QlZmvMRdVHhP4yqTFL4wZbRnp1lJ9U=";
    url = "https://repo1.maven.org/maven2/org/apache/apache/6/apache-6.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/apache-6.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/apache/6";
  };

  "com.lihaoyi_ujson_2.13-3.3.1" = fetchurl {
    name = "com.lihaoyi_ujson_2.13-3.3.1";
    hash = "sha256-tS5BVFeMdRfzGHUlrAywtQb4mG6oel56ooMEtlsWGjI=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/ujson_2.13/3.3.1/ujson_2.13-3.3.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/ujson_2.13-3.3.1.pom"
            
      downloadedFile=$TMPDIR/ujson_2.13-3.3.1.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/ujson_2.13/3.3.1/ujson_2.13-3.3.1.jar"
      cp -v "$TMPDIR/ujson_2.13-3.3.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/ujson_2.13/3.3.1";
  };

  "com.fasterxml.jackson_jackson-parent-2.17" = fetchurl {
    name = "com.fasterxml.jackson_jackson-parent-2.17";
    hash = "sha256-bwpdlIPUrYpG6AmpG+vbSgz7gRpEaUy7i1k2ZxRlYGc=";
    url = "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.17/jackson-parent-2.17.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jackson-parent-2.17.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.17";
  };

  "io.github.alexarchambault_concurrent-reference-hash-map-1.1.0" = fetchurl {
    name = "io.github.alexarchambault_concurrent-reference-hash-map-1.1.0";
    hash = "sha256-949g3dbXxz773bZlkiK2Xh3XiY5Ofc+1k6i8LM6s+yI=";
    url = "https://repo1.maven.org/maven2/io/github/alexarchambault/concurrent-reference-hash-map/1.1.0/concurrent-reference-hash-map-1.1.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/concurrent-reference-hash-map-1.1.0.pom"
            
      downloadedFile=$TMPDIR/concurrent-reference-hash-map-1.1.0.jar
      tryDownload "https://repo1.maven.org/maven2/io/github/alexarchambault/concurrent-reference-hash-map/1.1.0/concurrent-reference-hash-map-1.1.0.jar"
      cp -v "$TMPDIR/concurrent-reference-hash-map-1.1.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/io/github/alexarchambault/concurrent-reference-hash-map/1.1.0";
  };

  "org.scala-sbt_util-position_2.13-1.10.7" = fetchurl {
    name = "org.scala-sbt_util-position_2.13-1.10.7";
    hash = "sha256-hhRemdHTn5rI6IpViSG7KUxU/F2idL0AQf9CdNrF6xA=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/util-position_2.13/1.10.7/util-position_2.13-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/util-position_2.13-1.10.7.pom"
            
      downloadedFile=$TMPDIR/util-position_2.13-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/util-position_2.13/1.10.7/util-position_2.13-1.10.7.jar"
      cp -v "$TMPDIR/util-position_2.13-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-position_2.13/1.10.7";
  };

  "org.fusesource.jansi_jansi-1.18" = fetchurl {
    name = "org.fusesource.jansi_jansi-1.18";
    hash = "sha256-CxlGe+r7TDpytDSY57QMYz50BMpmVNOPU8qFcAy2jjw=";
    url = "https://repo1.maven.org/maven2/org/fusesource/jansi/jansi/1.18/jansi-1.18.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jansi-1.18.pom"
            
      downloadedFile=$TMPDIR/jansi-1.18.jar
      tryDownload "https://repo1.maven.org/maven2/org/fusesource/jansi/jansi/1.18/jansi-1.18.jar"
      cp -v "$TMPDIR/jansi-1.18.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/fusesource/jansi/jansi/1.18";
  };

  "org.scala-lang.modules_scala-collection-compat_2.13-2.12.0" = fetchurl {
    name = "org.scala-lang.modules_scala-collection-compat_2.13-2.12.0";
    hash = "sha256-gdUFn7dadEj342MYKz1lj4dLYz+AkOzRiIC0spS8CXk=";
    url = "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.12.0/scala-collection-compat_2.13-2.12.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/scala-collection-compat_2.13-2.12.0.pom"
            
      downloadedFile=$TMPDIR/scala-collection-compat_2.13-2.12.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.12.0/scala-collection-compat_2.13-2.12.0.jar"
      cp -v "$TMPDIR/scala-collection-compat_2.13-2.12.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.12.0";
  };

  "com.thoughtworks.paranamer_paranamer-2.8" = fetchurl {
    name = "com.thoughtworks.paranamer_paranamer-2.8";
    hash = "sha256-ehB753YLCaI3R/EkbYk+Ev08o90TnNoC+uiJDOcm3Q8=";
    url = "https://repo1.maven.org/maven2/com/thoughtworks/paranamer/paranamer/2.8/paranamer-2.8.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/paranamer-2.8.pom"
            
      downloadedFile=$TMPDIR/paranamer-2.8.jar
      tryDownload "https://repo1.maven.org/maven2/com/thoughtworks/paranamer/paranamer/2.8/paranamer-2.8.jar"
      cp -v "$TMPDIR/paranamer-2.8.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/thoughtworks/paranamer/paranamer/2.8";
  };

  "org.virtuslab.scala-cli_config_2.13-0.2.1" = fetchurl {
    name = "org.virtuslab.scala-cli_config_2.13-0.2.1";
    hash = "sha256-BClA/L7r5VPIpm0I4RT1k17E26ZDnVHKZJ86J5otPCU=";
    url = "https://repo1.maven.org/maven2/org/virtuslab/scala-cli/config_2.13/0.2.1/config_2.13-0.2.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/config_2.13-0.2.1.pom"
            
      downloadedFile=$TMPDIR/config_2.13-0.2.1.jar
      tryDownload "https://repo1.maven.org/maven2/org/virtuslab/scala-cli/config_2.13/0.2.1/config_2.13-0.2.1.jar"
      cp -v "$TMPDIR/config_2.13-0.2.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/virtuslab/scala-cli/config_2.13/0.2.1";
  };

  "com.github.plokhotnyuk.jsoniter-scala_jsoniter-scala-core_2.13-2.13.5" = fetchurl {
    name = "com.github.plokhotnyuk.jsoniter-scala_jsoniter-scala-core_2.13-2.13.5";
    hash = "sha256-EtWOnWyOgOJsLq2hjHgCG/2lc4NfCFIWtjyzBsPTBGs=";
    url = "https://repo1.maven.org/maven2/com/github/plokhotnyuk/jsoniter-scala/jsoniter-scala-core_2.13/2.13.5/jsoniter-scala-core_2.13-2.13.5.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jsoniter-scala-core_2.13-2.13.5.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/github/plokhotnyuk/jsoniter-scala/jsoniter-scala-core_2.13/2.13.5";
  };

  "org.scala-lang.modules_scala-parallel-collections_2.13-0.2.0" = fetchurl {
    name = "org.scala-lang.modules_scala-parallel-collections_2.13-0.2.0";
    hash = "sha256-chqRhtzyMJjeR4ohA5YhNjGV8kLHTy5yZjNCyYIO/wo=";
    url = "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-parallel-collections_2.13/0.2.0/scala-parallel-collections_2.13-0.2.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/scala-parallel-collections_2.13-0.2.0.pom"
            
      downloadedFile=$TMPDIR/scala-parallel-collections_2.13-0.2.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-parallel-collections_2.13/0.2.0/scala-parallel-collections_2.13-0.2.0.jar"
      cp -v "$TMPDIR/scala-parallel-collections_2.13-0.2.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-parallel-collections_2.13/0.2.0";
  };

  "org.jline_jline-parent-3.27.1" = fetchurl {
    name = "org.jline_jline-parent-3.27.1";
    hash = "sha256-Oa5DgBvf5JwZH68PDIyNkEQtm7IL04ujoeniH6GZas8=";
    url = "https://repo1.maven.org/maven2/org/jline/jline-parent/3.27.1/jline-parent-3.27.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jline-parent-3.27.1.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/jline/jline-parent/3.27.1";
  };

  "org.yaml_snakeyaml-1.26" = fetchurl {
    name = "org.yaml_snakeyaml-1.26";
    hash = "sha256-HA7ZQeV2pRZmpGS8WKPjWayZmIovGDFMBK8VliOeYNQ=";
    url = "https://repo1.maven.org/maven2/org/yaml/snakeyaml/1.26/snakeyaml-1.26.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/snakeyaml-1.26.pom"
            
      downloadedFile=$TMPDIR/snakeyaml-1.26.jar
      tryDownload "https://repo1.maven.org/maven2/org/yaml/snakeyaml/1.26/snakeyaml-1.26.jar"
      cp -v "$TMPDIR/snakeyaml-1.26.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/yaml/snakeyaml/1.26";
  };

  "org.apache.commons_commons-text-1.12.0" = fetchurl {
    name = "org.apache.commons_commons-text-1.12.0";
    hash = "sha256-k31WUz1GrDa1SqMb7NoX17el3+rY4nJQA7amM24n+VA=";
    url = "https://repo1.maven.org/maven2/org/apache/commons/commons-text/1.12.0/commons-text-1.12.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/commons-text-1.12.0.pom"
            
      downloadedFile=$TMPDIR/commons-text-1.12.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/apache/commons/commons-text/1.12.0/commons-text-1.12.0.jar"
      cp -v "$TMPDIR/commons-text-1.12.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-text/1.12.0";
  };

  "org.joda_joda-convert-2.2.0" = fetchurl {
    name = "org.joda_joda-convert-2.2.0";
    hash = "sha256-5B4Y6W/LIguA9aRjSzsDNwexWsJoCWU4yKUyysMC2Lo=";
    url = "https://repo1.maven.org/maven2/org/joda/joda-convert/2.2.0/joda-convert-2.2.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/joda-convert-2.2.0.pom"
            
      downloadedFile=$TMPDIR/joda-convert-2.2.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/joda/joda-convert/2.2.0/joda-convert-2.2.0.jar"
      cp -v "$TMPDIR/joda-convert-2.2.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/joda/joda-convert/2.2.0";
  };

  "dev.dirs_directories-26" = fetchurl {
    name = "dev.dirs_directories-26";
    hash = "sha256-e8lgDDTp/j5pm7kMR0B1ZePyfJXim8jJxFAqSI8g8BQ=";
    url = "https://repo1.maven.org/maven2/dev/dirs/directories/26/directories-26.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/directories-26.pom"
            
      downloadedFile=$TMPDIR/directories-26.jar
      tryDownload "https://repo1.maven.org/maven2/dev/dirs/directories/26/directories-26.jar"
      cp -v "$TMPDIR/directories-26.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/dev/dirs/directories/26";
  };

  "org.scala-sbt.jline_jline-2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18" = fetchurl {
    name = "org.scala-sbt.jline_jline-2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18";
    hash = "sha256-1Nq7/UMXSlaZ7iwR1WMryltAmS8/fRCK6u93cm+1uh4=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/jline/jline/2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18/jline-2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jline-2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18.pom"
            
      downloadedFile=$TMPDIR/jline-2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/jline/jline/2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18/jline-2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18.jar"
      cp -v "$TMPDIR/jline-2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/jline/jline/2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18";
  };

  "org.jline_jline-3.26.3" = fetchurl {
    name = "org.jline_jline-3.26.3";
    hash = "sha256-CVg5HR6GRYVCZ+0Y3yMsCUlgFCzd7MhgMqaZIQZEus0=";
    url = "https://repo1.maven.org/maven2/org/jline/jline/3.26.3/jline-3.26.3.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jline-3.26.3.pom"
            
      downloadedFile=$TMPDIR/jline-3.26.3.jar
      tryDownload "https://repo1.maven.org/maven2/org/jline/jline/3.26.3/jline-3.26.3.jar"
      cp -v "$TMPDIR/jline-3.26.3.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/jline/jline/3.26.3";
  };

  "com.lihaoyi_upickle_2.13-3.1.0" = fetchurl {
    name = "com.lihaoyi_upickle_2.13-3.1.0";
    hash = "sha256-2+LYKGBv3G2bBvjBuAxSXOOalo49SvmCFUK9STqZw1s=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/upickle_2.13/3.1.0/upickle_2.13-3.1.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/upickle_2.13-3.1.0.pom"
            
      downloadedFile=$TMPDIR/upickle_2.13-3.1.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/upickle_2.13/3.1.0/upickle_2.13-3.1.0.jar"
      cp -v "$TMPDIR/upickle_2.13-3.1.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle_2.13/3.1.0";
  };

  "com.github.luben_zstd-jni-1.5.5-10" = fetchurl {
    name = "com.github.luben_zstd-jni-1.5.5-10";
    hash = "sha256-dnEUwb4rG31QspIIUfar0Ctcy5TyPWG8fM8VrlaJ6YM=";
    url = "https://repo1.maven.org/maven2/com/github/luben/zstd-jni/1.5.5-10/zstd-jni-1.5.5-10.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/zstd-jni-1.5.5-10.pom"
            
      downloadedFile=$TMPDIR/zstd-jni-1.5.5-10.jar
      tryDownload "https://repo1.maven.org/maven2/com/github/luben/zstd-jni/1.5.5-10/zstd-jni-1.5.5-10.jar"
      cp -v "$TMPDIR/zstd-jni-1.5.5-10.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/github/luben/zstd-jni/1.5.5-10";
  };

  "com.github.scopt_scopt_2.13-4.1.0" = fetchurl {
    name = "com.github.scopt_scopt_2.13-4.1.0";
    hash = "sha256-8vlB7LBM6HNfmGOrsljlfCJ0SbMMpqR2Kmo9QWAKzJ8=";
    url = "https://repo1.maven.org/maven2/com/github/scopt/scopt_2.13/4.1.0/scopt_2.13-4.1.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/scopt_2.13-4.1.0.pom"
            
      downloadedFile=$TMPDIR/scopt_2.13-4.1.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/github/scopt/scopt_2.13/4.1.0/scopt_2.13-4.1.0.jar"
      cp -v "$TMPDIR/scopt_2.13-4.1.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/github/scopt/scopt_2.13/4.1.0";
  };

  "org.apache.geronimo.genesis_genesis-java5-flava-2.0" = fetchurl {
    name = "org.apache.geronimo.genesis_genesis-java5-flava-2.0";
    hash = "sha256-CTKaQ0fTVeVBnQrWm4TCcbTONXm/N6bPXPGXx0hToLQ=";
    url = "https://repo1.maven.org/maven2/org/apache/geronimo/genesis/genesis-java5-flava/2.0/genesis-java5-flava-2.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/genesis-java5-flava-2.0.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/geronimo/genesis/genesis-java5-flava/2.0";
  };

  "net.openhft_zero-allocation-hashing-0.16" = fetchurl {
    name = "net.openhft_zero-allocation-hashing-0.16";
    hash = "sha256-QkNOGkyP/OFWM+pv40hqR+ii4GBAcv0bbIrpG66YDMo=";
    url = "https://repo1.maven.org/maven2/net/openhft/zero-allocation-hashing/0.16/zero-allocation-hashing-0.16.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/zero-allocation-hashing-0.16.pom"
            
      downloadedFile=$TMPDIR/zero-allocation-hashing-0.16.jar
      tryDownload "https://repo1.maven.org/maven2/net/openhft/zero-allocation-hashing/0.16/zero-allocation-hashing-0.16.jar"
      cp -v "$TMPDIR/zero-allocation-hashing-0.16.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/net/openhft/zero-allocation-hashing/0.16";
  };

  "org.codehaus.plexus_plexus-5.1" = fetchurl {
    name = "org.codehaus.plexus_plexus-5.1";
    hash = "sha256-ywTicwjHcL7BzKPO3XzXpc9pE0M0j7Khcop85G3XqDI=";
    url = "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus/5.1/plexus-5.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/plexus-5.1.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus/5.1";
  };

  "org.codehaus.plexus_plexus-io-3.4.1" = fetchurl {
    name = "org.codehaus.plexus_plexus-io-3.4.1";
    hash = "sha256-8Av5TWqShLZgxRc1sDdxdIHZoUsyTaTW+UzWR46D5io=";
    url = "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-io/3.4.1/plexus-io-3.4.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/plexus-io-3.4.1.pom"
            
      downloadedFile=$TMPDIR/plexus-io-3.4.1.jar
      tryDownload "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-io/3.4.1/plexus-io-3.4.1.jar"
      cp -v "$TMPDIR/plexus-io-3.4.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus-io/3.4.1";
  };

  "org.scala-lang_scala-library-2.13.15" = fetchurl {
    name = "org.scala-lang_scala-library-2.13.15";
    hash = "sha256-JnbDGZQKZZswRZuxauQywH/4rXzwzn++kMB4lw3OfPI=";
    url = "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.15/scala-library-2.13.15.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/scala-library-2.13.15.pom"
            
      downloadedFile=$TMPDIR/scala-library-2.13.15.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.15/scala-library-2.13.15.jar"
      cp -v "$TMPDIR/scala-library-2.13.15.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.15";
  };

  "com.lihaoyi_mill-main-client-0.12.8-1-46e216" = fetchurl {
    name = "com.lihaoyi_mill-main-client-0.12.8-1-46e216";
    hash = "sha256-YMhZ7tABUyMCFXru2tjJK9IA73Z11n5w/RH5r4ia3q8=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/mill-main-client/0.12.8-1-46e216/mill-main-client-0.12.8-1-46e216.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/mill-main-client-0.12.8-1-46e216.pom"
            
      downloadedFile=$TMPDIR/mill-main-client-0.12.8-1-46e216.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/mill-main-client/0.12.8-1-46e216/mill-main-client-0.12.8-1-46e216.jar"
      cp -v "$TMPDIR/mill-main-client-0.12.8-1-46e216.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-main-client/0.12.8-1-46e216";
  };

  "net.java.dev.jna_jna-5.14.0" = fetchurl {
    name = "net.java.dev.jna_jna-5.14.0";
    hash = "sha256-mvzJykzd4Cz473vRi15E0NReFk7YN7hPOtS5ZHUhCIg=";
    url = "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.14.0/jna-5.14.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jna-5.14.0.pom"
            
      downloadedFile=$TMPDIR/jna-5.14.0.jar
      tryDownload "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar"
      cp -v "$TMPDIR/jna-5.14.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/net/java/dev/jna/jna/5.14.0";
  };

  "org.scala-lang.modules_scala-parser-combinators_2.13-1.1.2" = fetchurl {
    name = "org.scala-lang.modules_scala-parser-combinators_2.13-1.1.2";
    hash = "sha256-sM5GWZ8/K1Jchj4V3FTvaWhfSJiHq0PKtQpd5W94Hps=";
    url = "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-parser-combinators_2.13/1.1.2/scala-parser-combinators_2.13-1.1.2.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/scala-parser-combinators_2.13-1.1.2.pom"
            
      downloadedFile=$TMPDIR/scala-parser-combinators_2.13-1.1.2.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-parser-combinators_2.13/1.1.2/scala-parser-combinators_2.13-1.1.2.jar"
      cp -v "$TMPDIR/scala-parser-combinators_2.13-1.1.2.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-parser-combinators_2.13/1.1.2";
  };

  "com.eed3si9n_sjson-new-core_2.13-0.9.0" = fetchurl {
    name = "com.eed3si9n_sjson-new-core_2.13-0.9.0";
    hash = "sha256-WlJsXRKj77jzoFN6d1V/+jAEl37mxggg85F3o8oD+bY=";
    url = "https://repo1.maven.org/maven2/com/eed3si9n/sjson-new-core_2.13/0.9.0/sjson-new-core_2.13-0.9.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/sjson-new-core_2.13-0.9.0.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/eed3si9n/sjson-new-core_2.13/0.9.0";
  };

  "org.apache.commons_commons-parent-52" = fetchurl {
    name = "org.apache.commons_commons-parent-52";
    hash = "sha256-dqVIUNPKTtXecIQpdWWBzjqj723nzq5Qns010Sihuls=";
    url = "https://repo1.maven.org/maven2/org/apache/commons/commons-parent/52/commons-parent-52.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/commons-parent-52.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-parent/52";
  };

  "com.lihaoyi_mill-main-api_2.13-0.12.8-1-46e216" = fetchurl {
    name = "com.lihaoyi_mill-main-api_2.13-0.12.8-1-46e216";
    hash = "sha256-4uPDK4pTRGogIMWaYpRhWg+D8C2gDvaX88/x47X06Ls=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/mill-main-api_2.13/0.12.8-1-46e216/mill-main-api_2.13-0.12.8-1-46e216.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/mill-main-api_2.13-0.12.8-1-46e216.pom"
            
      downloadedFile=$TMPDIR/mill-main-api_2.13-0.12.8-1-46e216.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/mill-main-api_2.13/0.12.8-1-46e216/mill-main-api_2.13-0.12.8-1-46e216.jar"
      cp -v "$TMPDIR/mill-main-api_2.13-0.12.8-1-46e216.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-main-api_2.13/0.12.8-1-46e216";
  };

  "org.apache_apache-33" = fetchurl {
    name = "org.apache_apache-33";
    hash = "sha256-Hwj0S/ETiRxq9ObIzy9OGjGShFgbWuJOEoV6skSMQzI=";
    url = "https://repo1.maven.org/maven2/org/apache/apache/33/apache-33.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/apache-33.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/apache/33";
  };

  "org.scala-sbt_util-relation_2.13-1.10.7" = fetchurl {
    name = "org.scala-sbt_util-relation_2.13-1.10.7";
    hash = "sha256-r2kRBeuvusfdZwqZsRRuwp1Sr1PjWDuchmXbVPcSUOM=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/util-relation_2.13/1.10.7/util-relation_2.13-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/util-relation_2.13-1.10.7.pom"
            
      downloadedFile=$TMPDIR/util-relation_2.13-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/util-relation_2.13/1.10.7/util-relation_2.13-1.10.7.jar"
      cp -v "$TMPDIR/util-relation_2.13-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-relation_2.13/1.10.7";
  };

  "com.lihaoyi_upack_2.13-3.3.1" = fetchurl {
    name = "com.lihaoyi_upack_2.13-3.3.1";
    hash = "sha256-rbWiMl6+OXfWP2HjpMbvkToBzWhuW2hsJD/4dd+XmOs=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/upack_2.13/3.3.1/upack_2.13-3.3.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/upack_2.13-3.3.1.pom"
            
      downloadedFile=$TMPDIR/upack_2.13-3.3.1.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/upack_2.13/3.3.1/upack_2.13-3.3.1.jar"
      cp -v "$TMPDIR/upack_2.13-3.3.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upack_2.13/3.3.1";
  };

  "com.outr_perfolation_2.13-1.2.9" = fetchurl {
    name = "com.outr_perfolation_2.13-1.2.9";
    hash = "sha256-4kOe8zVfrjmXQvFgagLvOFCPXoF2tz0L4cqwYFEkxjs=";
    url = "https://repo1.maven.org/maven2/com/outr/perfolation_2.13/1.2.9/perfolation_2.13-1.2.9.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/perfolation_2.13-1.2.9.pom"
            
      downloadedFile=$TMPDIR/perfolation_2.13-1.2.9.jar
      tryDownload "https://repo1.maven.org/maven2/com/outr/perfolation_2.13/1.2.9/perfolation_2.13-1.2.9.jar"
      cp -v "$TMPDIR/perfolation_2.13-1.2.9.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/outr/perfolation_2.13/1.2.9";
  };

  "io.get-coursier_coursier-proxy-setup-2.1.8" = fetchurl {
    name = "io.get-coursier_coursier-proxy-setup-2.1.8";
    hash = "sha256-re16kficuiv2luELB8JeuIdgQOFojMqu8nFxq+ApwWM=";
    url = "https://repo1.maven.org/maven2/io/get-coursier/coursier-proxy-setup/2.1.8/coursier-proxy-setup-2.1.8.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/coursier-proxy-setup-2.1.8.pom"
            
      downloadedFile=$TMPDIR/coursier-proxy-setup-2.1.8.jar
      tryDownload "https://repo1.maven.org/maven2/io/get-coursier/coursier-proxy-setup/2.1.8/coursier-proxy-setup-2.1.8.jar"
      cp -v "$TMPDIR/coursier-proxy-setup-2.1.8.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/io/get-coursier/coursier-proxy-setup/2.1.8";
  };

  "org.fusesource_fusesource-pom-1.12" = fetchurl {
    name = "org.fusesource_fusesource-pom-1.12";
    hash = "sha256-NUD5PZ1FYYOq8yumvT5i29Vxd2ZCI6PXImXfLe4mE30=";
    url = "https://repo1.maven.org/maven2/org/fusesource/fusesource-pom/1.12/fusesource-pom-1.12.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/fusesource-pom-1.12.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/fusesource/fusesource-pom/1.12";
  };

  "org.scala-lang.modules_scala-xml_2.13-2.2.0" = fetchurl {
    name = "org.scala-lang.modules_scala-xml_2.13-2.2.0";
    hash = "sha256-Vy0piitgB2wPXiORd+dcBEZVcMZSjcbKJz4lNKZgeec=";
    url = "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.2.0/scala-xml_2.13-2.2.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/scala-xml_2.13-2.2.0.pom"
            
      downloadedFile=$TMPDIR/scala-xml_2.13-2.2.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.2.0/scala-xml_2.13-2.2.0.jar"
      cp -v "$TMPDIR/scala-xml_2.13-2.2.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.2.0";
  };

  "org.json4s_json4s-scalap_2.13-4.0.7" = fetchurl {
    name = "org.json4s_json4s-scalap_2.13-4.0.7";
    hash = "sha256-lw8n76iJsJlcZ7yhphvKbccinZAg+XeEN/fPjh5G6ak=";
    url = "https://repo1.maven.org/maven2/org/json4s/json4s-scalap_2.13/4.0.7/json4s-scalap_2.13-4.0.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/json4s-scalap_2.13-4.0.7.pom"
            
      downloadedFile=$TMPDIR/json4s-scalap_2.13-4.0.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/json4s/json4s-scalap_2.13/4.0.7/json4s-scalap_2.13-4.0.7.jar"
      cp -v "$TMPDIR/json4s-scalap_2.13-4.0.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-scalap_2.13/4.0.7";
  };

  "org.json4s_json4s-native_2.13-4.0.7" = fetchurl {
    name = "org.json4s_json4s-native_2.13-4.0.7";
    hash = "sha256-2CB0UN+Az/tda5vKKr8BvTZC+fehONFmB1V3liEcjpg=";
    url = "https://repo1.maven.org/maven2/org/json4s/json4s-native_2.13/4.0.7/json4s-native_2.13-4.0.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/json4s-native_2.13-4.0.7.pom"
            
      downloadedFile=$TMPDIR/json4s-native_2.13-4.0.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/json4s/json4s-native_2.13/4.0.7/json4s-native_2.13-4.0.7.jar"
      cp -v "$TMPDIR/json4s-native_2.13-4.0.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-native_2.13/4.0.7";
  };

  "org.mockito_mockito-bom-4.11.0" = fetchurl {
    name = "org.mockito_mockito-bom-4.11.0";
    hash = "sha256-jtuaGRrHXNkevtfBAzk3OA+n5RNtrDQ0MQSqSRxUIfc=";
    url = "https://repo1.maven.org/maven2/org/mockito/mockito-bom/4.11.0/mockito-bom-4.11.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/mockito-bom-4.11.0.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/mockito/mockito-bom/4.11.0";
  };

  "org.scala-sbt_util-control_2.13-1.10.7" = fetchurl {
    name = "org.scala-sbt_util-control_2.13-1.10.7";
    hash = "sha256-CCG/nXpVyd7YrtCYr47tPYIQs/G6vzb/3fCyZ21drhM=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/util-control_2.13/1.10.7/util-control_2.13-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/util-control_2.13-1.10.7.pom"
            
      downloadedFile=$TMPDIR/util-control_2.13-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/util-control_2.13/1.10.7/util-control_2.13-1.10.7.jar"
      cp -v "$TMPDIR/util-control_2.13-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-control_2.13/1.10.7";
  };

  "org.iq80.snappy_snappy-0.4" = fetchurl {
    name = "org.iq80.snappy_snappy-0.4";
    hash = "sha256-2lUP0Ah5s14rm0SG5jef8iE7wjdWkbRNoUYjsvZcOgI=";
    url = "https://repo1.maven.org/maven2/org/iq80/snappy/snappy/0.4/snappy-0.4.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/snappy-0.4.pom"
            
      downloadedFile=$TMPDIR/snappy-0.4.jar
      tryDownload "https://repo1.maven.org/maven2/org/iq80/snappy/snappy/0.4/snappy-0.4.jar"
      cp -v "$TMPDIR/snappy-0.4.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/iq80/snappy/snappy/0.4";
  };

  "org.codehaus.plexus_plexus-13" = fetchurl {
    name = "org.codehaus.plexus_plexus-13";
    hash = "sha256-+xSFmG+Lk1hag9eNbwO+EU1JgDbmNR9dHW2RwbpHUx0=";
    url = "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus/13/plexus-13.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/plexus-13.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus/13";
  };

  "org.json4s_json4s-core_2.13-4.0.7" = fetchurl {
    name = "org.json4s_json4s-core_2.13-4.0.7";
    hash = "sha256-Uk2Ars+BB+5lr+8ec0RtwtRsqfsVf1TyW2Z6YhK56Kw=";
    url = "https://repo1.maven.org/maven2/org/json4s/json4s-core_2.13/4.0.7/json4s-core_2.13-4.0.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/json4s-core_2.13-4.0.7.pom"
            
      downloadedFile=$TMPDIR/json4s-core_2.13-4.0.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/json4s/json4s-core_2.13/4.0.7/json4s-core_2.13-4.0.7.jar"
      cp -v "$TMPDIR/json4s-core_2.13-4.0.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-core_2.13/4.0.7";
  };

  "org.scala-lang_scalap-2.13.15" = fetchurl {
    name = "org.scala-lang_scalap-2.13.15";
    hash = "sha256-JMnmdCcFUakGj+seqTp15VYMzcq90jGjQPmKbCzY28A=";
    url = "https://repo1.maven.org/maven2/org/scala-lang/scalap/2.13.15/scalap-2.13.15.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/scalap-2.13.15.pom"
            
      downloadedFile=$TMPDIR/scalap-2.13.15.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-lang/scalap/2.13.15/scalap-2.13.15.jar"
      cp -v "$TMPDIR/scalap-2.13.15.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-lang/scalap/2.13.15";
  };

  "org.junit_junit-bom-5.9.0" = fetchurl {
    name = "org.junit_junit-bom-5.9.0";
    hash = "sha256-2Q+RMEkCBh0onvTyUHJrOh8MgL66H5hrKYzzSgKslo4=";
    url = "https://repo1.maven.org/maven2/org/junit/junit-bom/5.9.0/junit-bom-5.9.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/junit-bom-5.9.0.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.9.0";
  };

  "org.apache.commons_commons-lang3-3.14.0" = fetchurl {
    name = "org.apache.commons_commons-lang3-3.14.0";
    hash = "sha256-b5ZfCjrVKvpTFeW1SMtspKLtvI/uZuczaizc6Oj0xsI=";
    url = "https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/commons-lang3-3.14.0.pom"
            
      downloadedFile=$TMPDIR/commons-lang3-3.14.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar"
      cp -v "$TMPDIR/commons-lang3-3.14.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.14.0";
  };

  "com.lihaoyi_fansi_2.13-0.5.0" = fetchurl {
    name = "com.lihaoyi_fansi_2.13-0.5.0";
    hash = "sha256-iRaKoBsS7VOiQA0yj/wRNKo2NCHWteW0gM99kKObdns=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/fansi_2.13/0.5.0/fansi_2.13-0.5.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/fansi_2.13-0.5.0.pom"
            
      downloadedFile=$TMPDIR/fansi_2.13-0.5.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/fansi_2.13/0.5.0/fansi_2.13-0.5.0.jar"
      cp -v "$TMPDIR/fansi_2.13-0.5.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/fansi_2.13/0.5.0";
  };

  "org.apache.logging.log4j_log4j-api-2.24.3" = fetchurl {
    name = "org.apache.logging.log4j_log4j-api-2.24.3";
    hash = "sha256-y6wgpqMFwL3B3CrUbTI4HQTBjc4YSWxn0WF8QQSjpFw=";
    url = "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.24.3/log4j-api-2.24.3.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/log4j-api-2.24.3.pom"
            
      downloadedFile=$TMPDIR/log4j-api-2.24.3.jar
      tryDownload "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.24.3/log4j-api-2.24.3.jar"
      cp -v "$TMPDIR/log4j-api-2.24.3.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.24.3";
  };

  "org.scala-sbt_launcher-interface-1.4.4" = fetchurl {
    name = "org.scala-sbt_launcher-interface-1.4.4";
    hash = "sha256-HWiEWRS8Grm7uQME6o7FYZFhWvJgvrvyxKXMATB0Z7E=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/launcher-interface/1.4.4/launcher-interface-1.4.4.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/launcher-interface-1.4.4.pom"
            
      downloadedFile=$TMPDIR/launcher-interface-1.4.4.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/launcher-interface/1.4.4/launcher-interface-1.4.4.jar"
      cp -v "$TMPDIR/launcher-interface-1.4.4.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/launcher-interface/1.4.4";
  };

  "org.apache.logging.log4j_log4j-bom-2.24.3" = fetchurl {
    name = "org.apache.logging.log4j_log4j-bom-2.24.3";
    hash = "sha256-UNEo/UyoskA/8X62/rwMQObDQxfHDiJKj2pBP9SNoek=";
    url = "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-bom/2.24.3/log4j-bom-2.24.3.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/log4j-bom-2.24.3.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/logging/log4j/log4j-bom/2.24.3";
  };

  "com.lihaoyi_upickle-core_2.13-3.3.1" = fetchurl {
    name = "com.lihaoyi_upickle-core_2.13-3.3.1";
    hash = "sha256-+vXjTD3FY+FMlDpvsOkhwycDbvhnIY0SOcHKOYc+StM=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/upickle-core_2.13/3.3.1/upickle-core_2.13-3.3.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/upickle-core_2.13-3.3.1.pom"
            
      downloadedFile=$TMPDIR/upickle-core_2.13-3.3.1.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/upickle-core_2.13/3.3.1/upickle-core_2.13-3.3.1.jar"
      cp -v "$TMPDIR/upickle-core_2.13-3.3.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle-core_2.13/3.3.1";
  };

  "org.scala-sbt_util-interface-1.10.3" = fetchurl {
    name = "org.scala-sbt_util-interface-1.10.3";
    hash = "sha256-uu+2jvXfm2FaHkvJb44uRGdelrtS9pLfolU977MMQj0=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.3/util-interface-1.10.3.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/util-interface-1.10.3.pom"
            
      downloadedFile=$TMPDIR/util-interface-1.10.3.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.3/util-interface-1.10.3.jar"
      cp -v "$TMPDIR/util-interface-1.10.3.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.3";
  };

  "com.lihaoyi_mill-scala-compiler-bridge_2.13.15-0.0.1" = fetchurl {
    name = "com.lihaoyi_mill-scala-compiler-bridge_2.13.15-0.0.1";
    hash = "sha256-uTyXjgTJGlaKl8jCUp9A6uDdma97ixL65GNVD9l9oOw=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.15/0.0.1/mill-scala-compiler-bridge_2.13.15-0.0.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/mill-scala-compiler-bridge_2.13.15-0.0.1.pom"
            
      downloadedFile=$TMPDIR/mill-scala-compiler-bridge_2.13.15-0.0.1.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.15/0.0.1/mill-scala-compiler-bridge_2.13.15-0.0.1.jar"
      cp -v "$TMPDIR/mill-scala-compiler-bridge_2.13.15-0.0.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.15/0.0.1";
  };

  "org.apache.commons_commons-parent-61" = fetchurl {
    name = "org.apache.commons_commons-parent-61";
    hash = "sha256-TDkhTCXOjSzkuSZDSPq4/BpwSl5SR5Q3pTs/mHnNctE=";
    url = "https://repo1.maven.org/maven2/org/apache/commons/commons-parent/61/commons-parent-61.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/commons-parent-61.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-parent/61";
  };

  "org.codehaus.plexus_plexus-15" = fetchurl {
    name = "org.codehaus.plexus_plexus-15";
    hash = "sha256-8XR60cpYRudlgtupqWSB5ybF/4oS+CcL/qnmjTeUFUE=";
    url = "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus/15/plexus-15.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/plexus-15.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus/15";
  };

  "de.tototec_de.tobiasroeser.mill.vcs.version_mill0.11_2.13-0.4.1" = fetchurl {
    name = "de.tototec_de.tobiasroeser.mill.vcs.version_mill0.11_2.13-0.4.1";
    hash = "sha256-x5XWd12u2wr+LPtkNlI8tVuoYpopT0Ox24qkf83L308=";
    url = "https://repo1.maven.org/maven2/de/tototec/de.tobiasroeser.mill.vcs.version_mill0.11_2.13/0.4.1/de.tobiasroeser.mill.vcs.version_mill0.11_2.13-0.4.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/de.tobiasroeser.mill.vcs.version_mill0.11_2.13-0.4.1.pom"
            
      downloadedFile=$TMPDIR/de.tobiasroeser.mill.vcs.version_mill0.11_2.13-0.4.1.jar
      tryDownload "https://repo1.maven.org/maven2/de/tototec/de.tobiasroeser.mill.vcs.version_mill0.11_2.13/0.4.1/de.tobiasroeser.mill.vcs.version_mill0.11_2.13-0.4.1.jar"
      cp -v "$TMPDIR/de.tobiasroeser.mill.vcs.version_mill0.11_2.13-0.4.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/de/tototec/de.tobiasroeser.mill.vcs.version_mill0.11_2.13/0.4.1";
  };

  "org.codehaus.plexus_plexus-utils-4.0.0" = fetchurl {
    name = "org.codehaus.plexus_plexus-utils-4.0.0";
    hash = "sha256-sAa8WuY4BOBYt0PRBt19vhGidKrM0+X8jG9Hy/hRZKg=";
    url = "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-utils/4.0.0/plexus-utils-4.0.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/plexus-utils-4.0.0.pom"
            
      downloadedFile=$TMPDIR/plexus-utils-4.0.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-utils/4.0.0/plexus-utils-4.0.0.jar"
      cp -v "$TMPDIR/plexus-utils-4.0.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus-utils/4.0.0";
  };

  "io.get-coursier_coursier-util_2.13-2.1.8" = fetchurl {
    name = "io.get-coursier_coursier-util_2.13-2.1.8";
    hash = "sha256-yBBc+OkjfbQpDeI12PJrW3TD/828jbQei07j3LWKMgQ=";
    url = "https://repo1.maven.org/maven2/io/get-coursier/coursier-util_2.13/2.1.8/coursier-util_2.13-2.1.8.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/coursier-util_2.13-2.1.8.pom"
            
      downloadedFile=$TMPDIR/coursier-util_2.13-2.1.8.jar
      tryDownload "https://repo1.maven.org/maven2/io/get-coursier/coursier-util_2.13/2.1.8/coursier-util_2.13-2.1.8.jar"
      cp -v "$TMPDIR/coursier-util_2.13-2.1.8.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/io/get-coursier/coursier-util_2.13/2.1.8";
  };

  "com.eed3si9n_sjson-new-core_2.13-0.10.1" = fetchurl {
    name = "com.eed3si9n_sjson-new-core_2.13-0.10.1";
    hash = "sha256-sFHoDAQBTHju2EgUOPuO9tM/SLAdb8X/oNSnar0iYoQ=";
    url = "https://repo1.maven.org/maven2/com/eed3si9n/sjson-new-core_2.13/0.10.1/sjson-new-core_2.13-0.10.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/sjson-new-core_2.13-0.10.1.pom"
            
      downloadedFile=$TMPDIR/sjson-new-core_2.13-0.10.1.jar
      tryDownload "https://repo1.maven.org/maven2/com/eed3si9n/sjson-new-core_2.13/0.10.1/sjson-new-core_2.13-0.10.1.jar"
      cp -v "$TMPDIR/sjson-new-core_2.13-0.10.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/eed3si9n/sjson-new-core_2.13/0.10.1";
  };

  "net.openhft_root-parent-pom-1.2.12" = fetchurl {
    name = "net.openhft_root-parent-pom-1.2.12";
    hash = "sha256-D/M1qN+njmMZWqS5h27fl83Q+zWgIFjaYQkCpD2Oy/M=";
    url = "https://repo1.maven.org/maven2/net/openhft/root-parent-pom/1.2.12/root-parent-pom-1.2.12.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/root-parent-pom-1.2.12.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/net/openhft/root-parent-pom/1.2.12";
  };

  "org.junit_junit-bom-5.7.1" = fetchurl {
    name = "org.junit_junit-bom-5.7.1";
    hash = "sha256-BezVHVoj10EFGYNWSm+GE34SEt/8RD/2kFWa5juVSGo=";
    url = "https://repo1.maven.org/maven2/org/junit/junit-bom/5.7.1/junit-bom-5.7.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/junit-bom-5.7.1.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.7.1";
  };

  "org.scala-sbt_zinc-core_2.13-1.10.7" = fetchurl {
    name = "org.scala-sbt_zinc-core_2.13-1.10.7";
    hash = "sha256-eQFUHHuNND26t29kyYz7vbAscVCJej/Lc8eQRktDTGA=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/zinc-core_2.13/1.10.7/zinc-core_2.13-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/zinc-core_2.13-1.10.7.pom"
            
      downloadedFile=$TMPDIR/zinc-core_2.13-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/zinc-core_2.13/1.10.7/zinc-core_2.13-1.10.7.jar"
      cp -v "$TMPDIR/zinc-core_2.13-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-core_2.13/1.10.7";
  };

  "org.apache.xbean_xbean-3.7" = fetchurl {
    name = "org.apache.xbean_xbean-3.7";
    hash = "sha256-7moEcdxl+B1i7xstWBlWabSFr9QLszuciySggKYvpAE=";
    url = "https://repo1.maven.org/maven2/org/apache/xbean/xbean/3.7/xbean-3.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/xbean-3.7.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/xbean/xbean/3.7";
  };

  "org.json4s_json4s-ast_2.13-4.0.7" = fetchurl {
    name = "org.json4s_json4s-ast_2.13-4.0.7";
    hash = "sha256-krtrf0SfBd8Jn0JaAL0ocE/QOc3yt4HomZOCzaA3Zn8=";
    url = "https://repo1.maven.org/maven2/org/json4s/json4s-ast_2.13/4.0.7/json4s-ast_2.13-4.0.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/json4s-ast_2.13-4.0.7.pom"
            
      downloadedFile=$TMPDIR/json4s-ast_2.13-4.0.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/json4s/json4s-ast_2.13/4.0.7/json4s-ast_2.13-4.0.7.jar"
      cp -v "$TMPDIR/json4s-ast_2.13-4.0.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-ast_2.13/4.0.7";
  };

  "io.github.alexarchambault.windows-ansi_windows-ansi-0.0.5" = fetchurl {
    name = "io.github.alexarchambault.windows-ansi_windows-ansi-0.0.5";
    hash = "sha256-/gQXVEmWXVB0spEplWkvbOgXNeYz18N+JTf6cTCw5hA=";
    url = "https://repo1.maven.org/maven2/io/github/alexarchambault/windows-ansi/windows-ansi/0.0.5/windows-ansi-0.0.5.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/windows-ansi-0.0.5.pom"
            
      downloadedFile=$TMPDIR/windows-ansi-0.0.5.jar
      tryDownload "https://repo1.maven.org/maven2/io/github/alexarchambault/windows-ansi/windows-ansi/0.0.5/windows-ansi-0.0.5.jar"
      cp -v "$TMPDIR/windows-ansi-0.0.5.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/io/github/alexarchambault/windows-ansi/windows-ansi/0.0.5";
  };

  "org.codehaus.plexus_plexus-classworlds-2.6.0" = fetchurl {
    name = "org.codehaus.plexus_plexus-classworlds-2.6.0";
    hash = "sha256-vh7/TKxdcZVxXljM5MLGppoP0Bc28QyI/WsrPc6XSEA=";
    url = "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-classworlds/2.6.0/plexus-classworlds-2.6.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/plexus-classworlds-2.6.0.pom"
            
      downloadedFile=$TMPDIR/plexus-classworlds-2.6.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-classworlds/2.6.0/plexus-classworlds-2.6.0.jar"
      cp -v "$TMPDIR/plexus-classworlds-2.6.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus-classworlds/2.6.0";
  };

  "org.jline_jline-terminal-jni-3.27.1" = fetchurl {
    name = "org.jline_jline-terminal-jni-3.27.1";
    hash = "sha256-AWKC7imb/rnF39PAo3bVIW430zPkyj9WozKGkPlTTBE=";
    url = "https://repo1.maven.org/maven2/org/jline/jline-terminal-jni/3.27.1/jline-terminal-jni-3.27.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jline-terminal-jni-3.27.1.pom"
            
      downloadedFile=$TMPDIR/jline-terminal-jni-3.27.1.jar
      tryDownload "https://repo1.maven.org/maven2/org/jline/jline-terminal-jni/3.27.1/jline-terminal-jni-3.27.1.jar"
      cp -v "$TMPDIR/jline-terminal-jni-3.27.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/jline/jline-terminal-jni/3.27.1";
  };

  "org.apache_apache-31" = fetchurl {
    name = "org.apache_apache-31";
    hash = "sha256-Evktp+xRZ2C/VvG0UDTcFRSEvvSJINCtIe0Rom2159s=";
    url = "https://repo1.maven.org/maven2/org/apache/apache/31/apache-31.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/apache-31.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/apache/31";
  };

  "org.apache.groovy_groovy-bom-4.0.22" = fetchurl {
    name = "org.apache.groovy_groovy-bom-4.0.22";
    hash = "sha256-9hsejVx5kj/oQtf+JvuKqOuzRfJIJbPoys04ArDEu9o=";
    url = "https://repo1.maven.org/maven2/org/apache/groovy/groovy-bom/4.0.22/groovy-bom-4.0.22.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/groovy-bom-4.0.22.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/groovy/groovy-bom/4.0.22";
  };

  "com.lihaoyi_geny_2.13-1.0.0" = fetchurl {
    name = "com.lihaoyi_geny_2.13-1.0.0";
    hash = "sha256-NV0rvNt5hyo27OvkyQVogEYEpZsP8Vmxt+RIHFejVYI=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.0.0/geny_2.13-1.0.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/geny_2.13-1.0.0.pom"
            
      downloadedFile=$TMPDIR/geny_2.13-1.0.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.0.0/geny_2.13-1.0.0.jar"
      cp -v "$TMPDIR/geny_2.13-1.0.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.0.0";
  };

  "org.apache_apache-23" = fetchurl {
    name = "org.apache_apache-23";
    hash = "sha256-se+GoyCLRNTik1Rjwr8lNwZ6obSZFX00JZqJpThu4fo=";
    url = "https://repo1.maven.org/maven2/org/apache/apache/23/apache-23.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/apache-23.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/apache/23";
  };

  "com.lihaoyi_upickle-core_2.13-3.1.0" = fetchurl {
    name = "com.lihaoyi_upickle-core_2.13-3.1.0";
    hash = "sha256-aah3uPtmecvsukgDXGUAvs5W86S3FUAr2GwI7eynsis=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/upickle-core_2.13/3.1.0/upickle-core_2.13-3.1.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/upickle-core_2.13-3.1.0.pom"
            
      downloadedFile=$TMPDIR/upickle-core_2.13-3.1.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/upickle-core_2.13/3.1.0/upickle-core_2.13-3.1.0.jar"
      cp -v "$TMPDIR/upickle-core_2.13-3.1.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle-core_2.13/3.1.0";
  };

  "org.slf4j_slf4j-parent-1.7.36" = fetchurl {
    name = "org.slf4j_slf4j-parent-1.7.36";
    hash = "sha256-XOPBamOj/h7sQV4eY3tVJqwkhSPdS1EAqfeZruNTLGM=";
    url = "https://repo1.maven.org/maven2/org/slf4j/slf4j-parent/1.7.36/slf4j-parent-1.7.36.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/slf4j-parent-1.7.36.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/slf4j/slf4j-parent/1.7.36";
  };

  "org.slf4j_slf4j-api-1.7.36" = fetchurl {
    name = "org.slf4j_slf4j-api-1.7.36";
    hash = "sha256-Y5+xtmk/NH4v8ol1MqMr+2spKmRMVkcTL6QS1ko2EGM=";
    url = "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/slf4j-api-1.7.36.pom"
            
      downloadedFile=$TMPDIR/slf4j-api-1.7.36.jar
      tryDownload "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar"
      cp -v "$TMPDIR/slf4j-api-1.7.36.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36";
  };

  "com.lihaoyi_geny_2.13-1.1.1" = fetchurl {
    name = "com.lihaoyi_geny_2.13-1.1.1";
    hash = "sha256-+gQ8X4oSRU30RdF5kE2Gn8nxmo3RJEShiEyyzUJd088=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.1.1/geny_2.13-1.1.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/geny_2.13-1.1.1.pom"
            
      downloadedFile=$TMPDIR/geny_2.13-1.1.1.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.1.1/geny_2.13-1.1.1.jar"
      cp -v "$TMPDIR/geny_2.13-1.1.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.1.1";
  };

  "com.eed3si9n_shaded-scalajson_2.13-1.0.0-M4" = fetchurl {
    name = "com.eed3si9n_shaded-scalajson_2.13-1.0.0-M4";
    hash = "sha256-JyvPek41KleFIS5g4bqLm+qUw5FlX51/rnvv/BT2pk0=";
    url = "https://repo1.maven.org/maven2/com/eed3si9n/shaded-scalajson_2.13/1.0.0-M4/shaded-scalajson_2.13-1.0.0-M4.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/shaded-scalajson_2.13-1.0.0-M4.pom"
            
      downloadedFile=$TMPDIR/shaded-scalajson_2.13-1.0.0-M4.jar
      tryDownload "https://repo1.maven.org/maven2/com/eed3si9n/shaded-scalajson_2.13/1.0.0-M4/shaded-scalajson_2.13-1.0.0-M4.jar"
      cp -v "$TMPDIR/shaded-scalajson_2.13-1.0.0-M4.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/eed3si9n/shaded-scalajson_2.13/1.0.0-M4";
  };

  "org.codehaus.plexus_plexus-container-default-2.1.1" = fetchurl {
    name = "org.codehaus.plexus_plexus-container-default-2.1.1";
    hash = "sha256-E0Dt5DQRVlxg8fddMJZpvhU5cfNwB9MJTi/GJ1PVt3A=";
    url = "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-container-default/2.1.1/plexus-container-default-2.1.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/plexus-container-default-2.1.1.pom"
            
      downloadedFile=$TMPDIR/plexus-container-default-2.1.1.jar
      tryDownload "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-container-default/2.1.1/plexus-container-default-2.1.1.jar"
      cp -v "$TMPDIR/plexus-container-default-2.1.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus-container-default/2.1.1";
  };

  "org.scala-sbt_zinc-persist-core-assembly-1.10.7" = fetchurl {
    name = "org.scala-sbt_zinc-persist-core-assembly-1.10.7";
    hash = "sha256-KNr16Jjhbu3hrKUn/rTpEiEqWV/mC/iFhbO0YmToUCA=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/zinc-persist-core-assembly/1.10.7/zinc-persist-core-assembly-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/zinc-persist-core-assembly-1.10.7.pom"
            
      downloadedFile=$TMPDIR/zinc-persist-core-assembly-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/zinc-persist-core-assembly/1.10.7/zinc-persist-core-assembly-1.10.7.jar"
      cp -v "$TMPDIR/zinc-persist-core-assembly-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-persist-core-assembly/1.10.7";
  };

  "com.lihaoyi_mill-runner-linenumbers_2.13-0.12.8-1-46e216" = fetchurl {
    name = "com.lihaoyi_mill-runner-linenumbers_2.13-0.12.8-1-46e216";
    hash = "sha256-87nmecp5r+JPxSGxJIQz0wLptyW3yTilDK4CQaQlcsY=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-linenumbers_2.13/0.12.8-1-46e216/mill-runner-linenumbers_2.13-0.12.8-1-46e216.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/mill-runner-linenumbers_2.13-0.12.8-1-46e216.pom"
            
      downloadedFile=$TMPDIR/mill-runner-linenumbers_2.13-0.12.8-1-46e216.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-linenumbers_2.13/0.12.8-1-46e216/mill-runner-linenumbers_2.13-0.12.8-1-46e216.jar"
      cp -v "$TMPDIR/mill-runner-linenumbers_2.13-0.12.8-1-46e216.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-runner-linenumbers_2.13/0.12.8-1-46e216";
  };

  "com.lihaoyi_os-lib_2.13-0.9.2" = fetchurl {
    name = "com.lihaoyi_os-lib_2.13-0.9.2";
    hash = "sha256-Hi17y4mVAPOlH/aT0clhgNyGFobAdhNfeEMlcfPnN8w=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.9.2/os-lib_2.13-0.9.2.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/os-lib_2.13-0.9.2.pom"
            
      downloadedFile=$TMPDIR/os-lib_2.13-0.9.2.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.9.2/os-lib_2.13-0.9.2.jar"
      cp -v "$TMPDIR/os-lib_2.13-0.9.2.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.9.2";
  };

  "org.codehaus.plexus_plexus-containers-2.1.1" = fetchurl {
    name = "org.codehaus.plexus_plexus-containers-2.1.1";
    hash = "sha256-LR5FBjo4qAjwjKpHajTnuUBN7cLKbeTJRtYYc8q4FNw=";
    url = "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-containers/2.1.1/plexus-containers-2.1.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/plexus-containers-2.1.1.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus-containers/2.1.1";
  };

  "com.lihaoyi_mainargs_2.13-0.7.6" = fetchurl {
    name = "com.lihaoyi_mainargs_2.13-0.7.6";
    hash = "sha256-3VNPfYCWjDt/Sln9JRJ3/aqxZT72ZqwdVdE7ONtpGXM=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/mainargs_2.13/0.7.6/mainargs_2.13-0.7.6.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/mainargs_2.13-0.7.6.pom"
            
      downloadedFile=$TMPDIR/mainargs_2.13-0.7.6.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/mainargs_2.13/0.7.6/mainargs_2.13-0.7.6.jar"
      cp -v "$TMPDIR/mainargs_2.13-0.7.6.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mainargs_2.13/0.7.6";
  };

  "org.scala-sbt_util-logging_2.13-1.10.7" = fetchurl {
    name = "org.scala-sbt_util-logging_2.13-1.10.7";
    hash = "sha256-WfmccbZodef+h77nl7kEe6VxAsyzYlaHudZX0iyTRAs=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/util-logging_2.13/1.10.7/util-logging_2.13-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/util-logging_2.13-1.10.7.pom"
            
      downloadedFile=$TMPDIR/util-logging_2.13-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/util-logging_2.13/1.10.7/util-logging_2.13-1.10.7.jar"
      cp -v "$TMPDIR/util-logging_2.13-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-logging_2.13/1.10.7";
  };

  "org.json4s_json4s-scalap_2.13-4.0.6" = fetchurl {
    name = "org.json4s_json4s-scalap_2.13-4.0.6";
    hash = "sha256-f+vroJIB1GCvgTNqqw/FuSGgrd2+EfzHQL7EBjqgeTw=";
    url = "https://repo1.maven.org/maven2/org/json4s/json4s-scalap_2.13/4.0.6/json4s-scalap_2.13-4.0.6.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/json4s-scalap_2.13-4.0.6.pom"
            
      downloadedFile=$TMPDIR/json4s-scalap_2.13-4.0.6.jar
      tryDownload "https://repo1.maven.org/maven2/org/json4s/json4s-scalap_2.13/4.0.6/json4s-scalap_2.13-4.0.6.jar"
      cp -v "$TMPDIR/json4s-scalap_2.13-4.0.6.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-scalap_2.13/4.0.6";
  };

  "org.scala-sbt_sbinary_2.13-0.5.1" = fetchurl {
    name = "org.scala-sbt_sbinary_2.13-0.5.1";
    hash = "sha256-+TrjPjSy8WVXq3IYHkHHIzttvHQbgwMLkwwWBys/ryw=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/sbinary_2.13/0.5.1/sbinary_2.13-0.5.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/sbinary_2.13-0.5.1.pom"
            
      downloadedFile=$TMPDIR/sbinary_2.13-0.5.1.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/sbinary_2.13/0.5.1/sbinary_2.13-0.5.1.jar"
      cp -v "$TMPDIR/sbinary_2.13-0.5.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/sbinary_2.13/0.5.1";
  };

  "org.apache_apache-27" = fetchurl {
    name = "org.apache_apache-27";
    hash = "sha256-2ckA7GrC5pHno8sDiq6rTNP6vCBCEXgFYZW64jZdYiU=";
    url = "https://repo1.maven.org/maven2/org/apache/apache/27/apache-27.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/apache-27.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/apache/27";
  };

  "org.junit_junit-bom-5.10.2" = fetchurl {
    name = "org.junit_junit-bom-5.10.2";
    hash = "sha256-AlDFqi7NIm0J1UoA6JCUM3Rhq5cNwsXq/I8viZmWLEg=";
    url = "https://repo1.maven.org/maven2/org/junit/junit-bom/5.10.2/junit-bom-5.10.2.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/junit-bom-5.10.2.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.10.2";
  };

  "org.apache.xbean_xbean-reflect-3.7" = fetchurl {
    name = "org.apache.xbean_xbean-reflect-3.7";
    hash = "sha256-Zp97nk/YwipUj92NnhjU5tKNXgUmPWh2zWic2FoS434=";
    url = "https://repo1.maven.org/maven2/org/apache/xbean/xbean-reflect/3.7/xbean-reflect-3.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/xbean-reflect-3.7.pom"
            
      downloadedFile=$TMPDIR/xbean-reflect-3.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/apache/xbean/xbean-reflect/3.7/xbean-reflect-3.7.jar"
      cp -v "$TMPDIR/xbean-reflect-3.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/xbean/xbean-reflect/3.7";
  };

  "com.lihaoyi_sourcecode_2.13-0.4.0" = fetchurl {
    name = "com.lihaoyi_sourcecode_2.13-0.4.0";
    hash = "sha256-pi/E3F43hJcUYTx3hqUfOa/SGWmIcCl7z+3vCWDDrXc=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.13/0.4.0/sourcecode_2.13-0.4.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/sourcecode_2.13-0.4.0.pom"
            
      downloadedFile=$TMPDIR/sourcecode_2.13-0.4.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.13/0.4.0/sourcecode_2.13-0.4.0.jar"
      cp -v "$TMPDIR/sourcecode_2.13-0.4.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.13/0.4.0";
  };

  "org.json4s_json4s-native-core_2.13-4.0.7" = fetchurl {
    name = "org.json4s_json4s-native-core_2.13-4.0.7";
    hash = "sha256-pHs9ANRm8I8p24Lq+aAufLHoCOF823qACydlsAPkpYk=";
    url = "https://repo1.maven.org/maven2/org/json4s/json4s-native-core_2.13/4.0.7/json4s-native-core_2.13-4.0.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/json4s-native-core_2.13-4.0.7.pom"
            
      downloadedFile=$TMPDIR/json4s-native-core_2.13-4.0.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/json4s/json4s-native-core_2.13/4.0.7/json4s-native-core_2.13-4.0.7.jar"
      cp -v "$TMPDIR/json4s-native-core_2.13-4.0.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-native-core_2.13/4.0.7";
  };

  "com.lihaoyi_mainargs_2.13-0.7.0" = fetchurl {
    name = "com.lihaoyi_mainargs_2.13-0.7.0";
    hash = "sha256-JLN3zk3VYI8RkiLMqnB1RGGW+xyvQyEdUMpY+RPtY3s=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/mainargs_2.13/0.7.0/mainargs_2.13-0.7.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/mainargs_2.13-0.7.0.pom"
            
      downloadedFile=$TMPDIR/mainargs_2.13-0.7.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/mainargs_2.13/0.7.0/mainargs_2.13-0.7.0.jar"
      cp -v "$TMPDIR/mainargs_2.13-0.7.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mainargs_2.13/0.7.0";
  };

  "com.lihaoyi_upack_2.13-3.1.0" = fetchurl {
    name = "com.lihaoyi_upack_2.13-3.1.0";
    hash = "sha256-flYIKY1mOQuPYdE7yT+Vq/W+/0B7Oe8KpK+Z198O1C0=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/upack_2.13/3.1.0/upack_2.13-3.1.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/upack_2.13-3.1.0.pom"
            
      downloadedFile=$TMPDIR/upack_2.13-3.1.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/upack_2.13/3.1.0/upack_2.13-3.1.0.jar"
      cp -v "$TMPDIR/upack_2.13-3.1.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upack_2.13/3.1.0";
  };

  "org.scala-lang_scala-compiler-2.13.15" = fetchurl {
    name = "org.scala-lang_scala-compiler-2.13.15";
    hash = "sha256-kvqWoFLNy3LGIbD6l67f66OyJq/K2L4rTStLiDzIzm8=";
    url = "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.15/scala-compiler-2.13.15.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/scala-compiler-2.13.15.pom"
            
      downloadedFile=$TMPDIR/scala-compiler-2.13.15.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.15/scala-compiler-2.13.15.jar"
      cp -v "$TMPDIR/scala-compiler-2.13.15.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.15";
  };

  "io.get-coursier_coursier-cache_2.13-2.1.8" = fetchurl {
    name = "io.get-coursier_coursier-cache_2.13-2.1.8";
    hash = "sha256-X8YwAtpt4YrL0RG5e7LOe23cBz5twfV6NwsPaHwjGaQ=";
    url = "https://repo1.maven.org/maven2/io/get-coursier/coursier-cache_2.13/2.1.8/coursier-cache_2.13-2.1.8.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/coursier-cache_2.13-2.1.8.pom"
            
      downloadedFile=$TMPDIR/coursier-cache_2.13-2.1.8.jar
      tryDownload "https://repo1.maven.org/maven2/io/get-coursier/coursier-cache_2.13/2.1.8/coursier-cache_2.13-2.1.8.jar"
      cp -v "$TMPDIR/coursier-cache_2.13-2.1.8.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/io/get-coursier/coursier-cache_2.13/2.1.8";
  };

  "io.github.alexarchambault_data-class_2.13-0.2.6" = fetchurl {
    name = "io.github.alexarchambault_data-class_2.13-0.2.6";
    hash = "sha256-9uE8XneZOj05VTtr/9unuK17dn/PYPmFPUxNSs5L1KY=";
    url = "https://repo1.maven.org/maven2/io/github/alexarchambault/data-class_2.13/0.2.6/data-class_2.13-0.2.6.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/data-class_2.13-0.2.6.pom"
            
      downloadedFile=$TMPDIR/data-class_2.13-0.2.6.jar
      tryDownload "https://repo1.maven.org/maven2/io/github/alexarchambault/data-class_2.13/0.2.6/data-class_2.13-0.2.6.jar"
      cp -v "$TMPDIR/data-class_2.13-0.2.6.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/io/github/alexarchambault/data-class_2.13/0.2.6";
  };

  "org.codehaus.plexus_plexus-10" = fetchurl {
    name = "org.codehaus.plexus_plexus-10";
    hash = "sha256-K8/wCgXsR3UUtf3H0qG7i9xieMDGcIIlni5nveROID0=";
    url = "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus/10/plexus-10.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/plexus-10.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus/10";
  };

  "org.codehaus.plexus_plexus-6.5" = fetchurl {
    name = "org.codehaus.plexus_plexus-6.5";
    hash = "sha256-6Hhmat92ApFn7ze2iYyOusDxXMYp98v1GNqAvKypKSQ=";
    url = "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus/6.5/plexus-6.5.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/plexus-6.5.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus/6.5";
  };

  "com.fasterxml.jackson_jackson-bom-2.17.2" = fetchurl {
    name = "com.fasterxml.jackson_jackson-bom-2.17.2";
    hash = "sha256-uAhCPZKxSJE8I5PhUlyXZOF9QVS/Xh+BQiYGmUYA86E=";
    url = "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.17.2/jackson-bom-2.17.2.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jackson-bom-2.17.2.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.17.2";
  };

  "jakarta.platform_jakarta.jakartaee-bom-9.1.0" = fetchurl {
    name = "jakarta.platform_jakarta.jakartaee-bom-9.1.0";
    hash = "sha256-kstGe15Yw9oF6LQ3Vovx1PcCUfQtNaEM7T8E5Upp1gg=";
    url = "https://repo1.maven.org/maven2/jakarta/platform/jakarta.jakartaee-bom/9.1.0/jakarta.jakartaee-bom-9.1.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jakarta.jakartaee-bom-9.1.0.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/jakarta/platform/jakarta.jakartaee-bom/9.1.0";
  };

  "org.apache.commons_commons-parent-64" = fetchurl {
    name = "org.apache.commons_commons-parent-64";
    hash = "sha256-Q6095muAB/pbFjv41RzuqBbVSWMz8zN47Okjv96TqWI=";
    url = "https://repo1.maven.org/maven2/org/apache/commons/commons-parent/64/commons-parent-64.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/commons-parent-64.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-parent/64";
  };

  "com.lihaoyi_os-lib_2.13-0.9.1" = fetchurl {
    name = "com.lihaoyi_os-lib_2.13-0.9.1";
    hash = "sha256-B6w5qlg7ougJK0Thmb0Wrt0UTXeNepnJkwUv2qyuXOQ=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.9.1/os-lib_2.13-0.9.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/os-lib_2.13-0.9.1.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.9.1";
  };

  "commons-io_commons-io-2.15.0" = fetchurl {
    name = "commons-io_commons-io-2.15.0";
    hash = "sha256-pv6pWA7QOLaDngSyVABLDgn0a2SgEdnIEpDIz6kM4Ro=";
    url = "https://repo1.maven.org/maven2/commons-io/commons-io/2.15.0/commons-io-2.15.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/commons-io-2.15.0.pom"
            
      downloadedFile=$TMPDIR/commons-io-2.15.0.jar
      tryDownload "https://repo1.maven.org/maven2/commons-io/commons-io/2.15.0/commons-io-2.15.0.jar"
      cp -v "$TMPDIR/commons-io-2.15.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/commons-io/commons-io/2.15.0";
  };

  "net.jcazevedo_moultingyaml_2.13-0.4.2" = fetchurl {
    name = "net.jcazevedo_moultingyaml_2.13-0.4.2";
    hash = "sha256-/mHEM0ekAhm1Wytlzzu2zB6Qc7gfhN1hTOabXBxDx58=";
    url = "https://repo1.maven.org/maven2/net/jcazevedo/moultingyaml_2.13/0.4.2/moultingyaml_2.13-0.4.2.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/moultingyaml_2.13-0.4.2.pom"
            
      downloadedFile=$TMPDIR/moultingyaml_2.13-0.4.2.jar
      tryDownload "https://repo1.maven.org/maven2/net/jcazevedo/moultingyaml_2.13/0.4.2/moultingyaml_2.13-0.4.2.jar"
      cp -v "$TMPDIR/moultingyaml_2.13-0.4.2.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/net/jcazevedo/moultingyaml_2.13/0.4.2";
  };

  "org.eclipse.ee4j_project-1.0.7" = fetchurl {
    name = "org.eclipse.ee4j_project-1.0.7";
    hash = "sha256-1HxZiJ0aeo1n8AWjwGKEoPwVFP9kndMBye7xwgYEal8=";
    url = "https://repo1.maven.org/maven2/org/eclipse/ee4j/project/1.0.7/project-1.0.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/project-1.0.7.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/eclipse/ee4j/project/1.0.7";
  };

  "org.springframework_spring-framework-bom-5.3.39" = fetchurl {
    name = "org.springframework_spring-framework-bom-5.3.39";
    hash = "sha256-V+sR9AvokPz2NrvEFCxdLHl3jrW2o9dP3gisCDAUUDA=";
    url = "https://repo1.maven.org/maven2/org/springframework/spring-framework-bom/5.3.39/spring-framework-bom-5.3.39.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/spring-framework-bom-5.3.39.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/springframework/spring-framework-bom/5.3.39";
  };

  "org.joda_joda-parent-1.4.0" = fetchurl {
    name = "org.joda_joda-parent-1.4.0";
    hash = "sha256-IKpTTW5AT7H4Uo8hBjezLVsbzAvCblFAL7N8+Oeb1jo=";
    url = "https://repo1.maven.org/maven2/org/joda/joda-parent/1.4.0/joda-parent-1.4.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/joda-parent-1.4.0.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/joda/joda-parent/1.4.0";
  };

  "com.lihaoyi_sourcecode_2.13-0.3.0" = fetchurl {
    name = "com.lihaoyi_sourcecode_2.13-0.3.0";
    hash = "sha256-Y+QhWVO6t2oYpWS/s2aG1fHO+QZ726LyJYaGh3SL4ko=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.13/0.3.0/sourcecode_2.13-0.3.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/sourcecode_2.13-0.3.0.pom"
            
      downloadedFile=$TMPDIR/sourcecode_2.13-0.3.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.13/0.3.0/sourcecode_2.13-0.3.0.jar"
      cp -v "$TMPDIR/sourcecode_2.13-0.3.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.13/0.3.0";
  };

  "org.scala-sbt_zinc-apiinfo_2.13-1.10.7" = fetchurl {
    name = "org.scala-sbt_zinc-apiinfo_2.13-1.10.7";
    hash = "sha256-nRr38N6FO18MM0+mlb9lK2EOhfl7GZ1y7ez6Eg3Ip8w=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/zinc-apiinfo_2.13/1.10.7/zinc-apiinfo_2.13-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/zinc-apiinfo_2.13-1.10.7.pom"
            
      downloadedFile=$TMPDIR/zinc-apiinfo_2.13-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/zinc-apiinfo_2.13/1.10.7/zinc-apiinfo_2.13-1.10.7.jar"
      cp -v "$TMPDIR/zinc-apiinfo_2.13-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-apiinfo_2.13/1.10.7";
  };

  "org.scala-sbt_compiler-bridge_2.13-1.10.7" = fetchurl {
    name = "org.scala-sbt_compiler-bridge_2.13-1.10.7";
    hash = "sha256-9l1vxfLu6JEyJKdPaBO6RkEn/KoiW7d59C/xAQdeb+Y=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/compiler-bridge_2.13/1.10.7/compiler-bridge_2.13-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/compiler-bridge_2.13-1.10.7.pom"
            
      downloadedFile=$TMPDIR/compiler-bridge_2.13-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/compiler-bridge_2.13/1.10.7/compiler-bridge_2.13-1.10.7.jar"
      cp -v "$TMPDIR/compiler-bridge_2.13-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/compiler-bridge_2.13/1.10.7";
  };

  "org.json4s_json4s-core_2.13-4.0.6" = fetchurl {
    name = "org.json4s_json4s-core_2.13-4.0.6";
    hash = "sha256-3RL111Z2gLsb2W/SuKsffFOXSvg/jQM4Foowf4t2deo=";
    url = "https://repo1.maven.org/maven2/org/json4s/json4s-core_2.13/4.0.6/json4s-core_2.13-4.0.6.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/json4s-core_2.13-4.0.6.pom"
            
      downloadedFile=$TMPDIR/json4s-core_2.13-4.0.6.jar
      tryDownload "https://repo1.maven.org/maven2/org/json4s/json4s-core_2.13/4.0.6/json4s-core_2.13-4.0.6.jar"
      cp -v "$TMPDIR/json4s-core_2.13-4.0.6.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-core_2.13/4.0.6";
  };

  "com.lihaoyi_mill-scalalib-api_2.13-0.12.8-1-46e216" = fetchurl {
    name = "com.lihaoyi_mill-scalalib-api_2.13-0.12.8-1-46e216";
    hash = "sha256-8xD1JkQ+PyCOCEYO/mlpmkQ1PpqIRjHnlwjI46Q/TNY=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/mill-scalalib-api_2.13/0.12.8-1-46e216/mill-scalalib-api_2.13-0.12.8-1-46e216.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/mill-scalalib-api_2.13-0.12.8-1-46e216.pom"
            
      downloadedFile=$TMPDIR/mill-scalalib-api_2.13-0.12.8-1-46e216.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/mill-scalalib-api_2.13/0.12.8-1-46e216/mill-scalalib-api_2.13-0.12.8-1-46e216.jar"
      cp -v "$TMPDIR/mill-scalalib-api_2.13-0.12.8-1-46e216.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-scalalib-api_2.13/0.12.8-1-46e216";
  };

  "com.fasterxml_oss-parent-58" = fetchurl {
    name = "com.fasterxml_oss-parent-58";
    hash = "sha256-wVCyn9u4Q5PMWSigrfRD2c90jacWbffIBxjXZq/VOSw=";
    url = "https://repo1.maven.org/maven2/com/fasterxml/oss-parent/58/oss-parent-58.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/oss-parent-58.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/fasterxml/oss-parent/58";
  };

  "org.sonatype.oss_oss-parent-9" = fetchurl {
    name = "org.sonatype.oss_oss-parent-9";
    hash = "sha256-kJ3QfnDTAvamYaHQowpAKW1gPDFDXbiP2lNPzNllIWY=";
    url = "https://repo1.maven.org/maven2/org/sonatype/oss/oss-parent/9/oss-parent-9.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/oss-parent-9.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/sonatype/oss/oss-parent/9";
  };

  "com.outr_moduload_2.13-1.1.7" = fetchurl {
    name = "com.outr_moduload_2.13-1.1.7";
    hash = "sha256-x328NE46FkPXXZyacQOkFhAlSWwiUONE0/Nt5wRu9h8=";
    url = "https://repo1.maven.org/maven2/com/outr/moduload_2.13/1.1.7/moduload_2.13-1.1.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/moduload_2.13-1.1.7.pom"
            
      downloadedFile=$TMPDIR/moduload_2.13-1.1.7.jar
      tryDownload "https://repo1.maven.org/maven2/com/outr/moduload_2.13/1.1.7/moduload_2.13-1.1.7.jar"
      cp -v "$TMPDIR/moduload_2.13-1.1.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/outr/moduload_2.13/1.1.7";
  };

  "org.chipsalliance_firtool-resolver_2.13-1.3.0" = fetchurl {
    name = "org.chipsalliance_firtool-resolver_2.13-1.3.0";
    hash = "sha256-PGBKJm4yTDFbV3GcCswHYD/ytw25zk2VhNBEEDWT4vU=";
    url = "https://repo1.maven.org/maven2/org/chipsalliance/firtool-resolver_2.13/1.3.0/firtool-resolver_2.13-1.3.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/firtool-resolver_2.13-1.3.0.pom"
            
      downloadedFile=$TMPDIR/firtool-resolver_2.13-1.3.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/chipsalliance/firtool-resolver_2.13/1.3.0/firtool-resolver_2.13-1.3.0.jar"
      cp -v "$TMPDIR/firtool-resolver_2.13-1.3.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/chipsalliance/firtool-resolver_2.13/1.3.0";
  };

  "com.lihaoyi_ujson_2.13-3.1.0" = fetchurl {
    name = "com.lihaoyi_ujson_2.13-3.1.0";
    hash = "sha256-6lPtY2spkrwhhTwg4gJisbWrCTcRqEP2h9B0Ppeqg5I=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/ujson_2.13/3.1.0/ujson_2.13-3.1.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/ujson_2.13-3.1.0.pom"
            
      downloadedFile=$TMPDIR/ujson_2.13-3.1.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/ujson_2.13/3.1.0/ujson_2.13-3.1.0.jar"
      cp -v "$TMPDIR/ujson_2.13-3.1.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/ujson_2.13/3.1.0";
  };

  "org.apache.commons_commons-parent-69" = fetchurl {
    name = "org.apache.commons_commons-parent-69";
    hash = "sha256-XDFSOofSIPQI87JPu4s21bhzz9SDiYXZ4rIoURJ4feI=";
    url = "https://repo1.maven.org/maven2/org/apache/commons/commons-parent/69/commons-parent-69.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/commons-parent-69.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-parent/69";
  };

  "org.scala-sbt_zinc-classfile_2.13-1.10.7" = fetchurl {
    name = "org.scala-sbt_zinc-classfile_2.13-1.10.7";
    hash = "sha256-0UOFRvovrzzXFILxniSzo5MHr/XmSDGP4o3wh05uCxE=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/zinc-classfile_2.13/1.10.7/zinc-classfile_2.13-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/zinc-classfile_2.13-1.10.7.pom"
            
      downloadedFile=$TMPDIR/zinc-classfile_2.13-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/zinc-classfile_2.13/1.10.7/zinc-classfile_2.13-1.10.7.jar"
      cp -v "$TMPDIR/zinc-classfile_2.13-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-classfile_2.13/1.10.7";
  };

  "com.lihaoyi_mill-contrib-versionfile_2.13-0.12.8-1-46e216" = fetchurl {
    name = "com.lihaoyi_mill-contrib-versionfile_2.13-0.12.8-1-46e216";
    hash = "sha256-UNRz3Q/1I36aS5wuKoEZaqXeIy42vd9J74f0yLG+jcg=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/mill-contrib-versionfile_2.13/0.12.8-1-46e216/mill-contrib-versionfile_2.13-0.12.8-1-46e216.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/mill-contrib-versionfile_2.13-0.12.8-1-46e216.pom"
            
      downloadedFile=$TMPDIR/mill-contrib-versionfile_2.13-0.12.8-1-46e216.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/mill-contrib-versionfile_2.13/0.12.8-1-46e216/mill-contrib-versionfile_2.13-0.12.8-1-46e216.jar"
      cp -v "$TMPDIR/mill-contrib-versionfile_2.13-0.12.8-1-46e216.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-contrib-versionfile_2.13/0.12.8-1-46e216";
  };

  "io.get-coursier_coursier-core_2.13-2.1.8" = fetchurl {
    name = "io.get-coursier_coursier-core_2.13-2.1.8";
    hash = "sha256-h+VweGWGkJH2dZKgE5xzLbGVuti4jUDIAznV/CGGYdU=";
    url = "https://repo1.maven.org/maven2/io/get-coursier/coursier-core_2.13/2.1.8/coursier-core_2.13-2.1.8.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/coursier-core_2.13-2.1.8.pom"
            
      downloadedFile=$TMPDIR/coursier-core_2.13-2.1.8.jar
      tryDownload "https://repo1.maven.org/maven2/io/get-coursier/coursier-core_2.13/2.1.8/coursier-core_2.13-2.1.8.jar"
      cp -v "$TMPDIR/coursier-core_2.13-2.1.8.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/io/get-coursier/coursier-core_2.13/2.1.8";
  };

  "com.swoval_file-tree-views-2.1.12" = fetchurl {
    name = "com.swoval_file-tree-views-2.1.12";
    hash = "sha256-QhJJFQt5LS2THa8AyPLrj0suht4eCiAEl2sf7QsZU3I=";
    url = "https://repo1.maven.org/maven2/com/swoval/file-tree-views/2.1.12/file-tree-views-2.1.12.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/file-tree-views-2.1.12.pom"
            
      downloadedFile=$TMPDIR/file-tree-views-2.1.12.jar
      tryDownload "https://repo1.maven.org/maven2/com/swoval/file-tree-views/2.1.12/file-tree-views-2.1.12.jar"
      cp -v "$TMPDIR/file-tree-views-2.1.12.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/swoval/file-tree-views/2.1.12";
  };

  "com.lihaoyi_upickle-implicits_2.13-3.1.0" = fetchurl {
    name = "com.lihaoyi_upickle-implicits_2.13-3.1.0";
    hash = "sha256-3fFRVyipnVGpHjwmwCLiJFEcZdxkJ4GO00cqZ349+40=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_2.13/3.1.0/upickle-implicits_2.13-3.1.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/upickle-implicits_2.13-3.1.0.pom"
            
      downloadedFile=$TMPDIR/upickle-implicits_2.13-3.1.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_2.13/3.1.0/upickle-implicits_2.13-3.1.0.jar"
      cp -v "$TMPDIR/upickle-implicits_2.13-3.1.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_2.13/3.1.0";
  };

  "com.lihaoyi_upickle-implicits_2.13-3.3.1" = fetchurl {
    name = "com.lihaoyi_upickle-implicits_2.13-3.3.1";
    hash = "sha256-LKWPAok7DL+YyfLv6yTwuyAG8z/74mzMrsqgUvUw9bM=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_2.13/3.3.1/upickle-implicits_2.13-3.3.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/upickle-implicits_2.13-3.3.1.pom"
            
      downloadedFile=$TMPDIR/upickle-implicits_2.13-3.3.1.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_2.13/3.3.1/upickle-implicits_2.13-3.3.1.jar"
      cp -v "$TMPDIR/upickle-implicits_2.13-3.3.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_2.13/3.3.1";
  };

  "org.chipsalliance_chisel-plugin_2.13.15-6.6.0" = fetchurl {
    name = "org.chipsalliance_chisel-plugin_2.13.15-6.6.0";
    hash = "sha256-tUk4PuDbMCOb5Ri7WOYdtMZv9T7HBrJivEpBq5YPjqE=";
    url = "https://repo1.maven.org/maven2/org/chipsalliance/chisel-plugin_2.13.15/6.6.0/chisel-plugin_2.13.15-6.6.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/chisel-plugin_2.13.15-6.6.0.pom"
            
      downloadedFile=$TMPDIR/chisel-plugin_2.13.15-6.6.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/chipsalliance/chisel-plugin_2.13.15/6.6.0/chisel-plugin_2.13.15-6.6.0.jar"
      cp -v "$TMPDIR/chisel-plugin_2.13.15-6.6.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/chipsalliance/chisel-plugin_2.13.15/6.6.0";
  };

  "org.scala-sbt_zinc_2.13-1.10.7" = fetchurl {
    name = "org.scala-sbt_zinc_2.13-1.10.7";
    hash = "sha256-G87j0JvuTu7cEu4gUZ268vxJ79vs7qR0p8J1wT+wwD4=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/zinc_2.13/1.10.7/zinc_2.13-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/zinc_2.13-1.10.7.pom"
            
      downloadedFile=$TMPDIR/zinc_2.13-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/zinc_2.13/1.10.7/zinc_2.13-1.10.7.jar"
      cp -v "$TMPDIR/zinc_2.13-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc_2.13/1.10.7";
  };

  "org.fusesource.jansi_jansi-project-1.18" = fetchurl {
    name = "org.fusesource.jansi_jansi-project-1.18";
    hash = "sha256-tw0DptIEvGdb1UkHbxYAQUUVUVVgpV5IAt2ouwgI2iQ=";
    url = "https://repo1.maven.org/maven2/org/fusesource/jansi/jansi-project/1.18/jansi-project-1.18.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jansi-project-1.18.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/fusesource/jansi/jansi-project/1.18";
  };

  "org.scala-lang.modules_scala-parallel-collections_2.13-1.0.4" = fetchurl {
    name = "org.scala-lang.modules_scala-parallel-collections_2.13-1.0.4";
    hash = "sha256-v9KVThoqaxVkBvA56qoGA4aQcn1uHbvV7t1NsCbhpC0=";
    url = "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-parallel-collections_2.13/1.0.4/scala-parallel-collections_2.13-1.0.4.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/scala-parallel-collections_2.13-1.0.4.pom"
            
      downloadedFile=$TMPDIR/scala-parallel-collections_2.13-1.0.4.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-parallel-collections_2.13/1.0.4/scala-parallel-collections_2.13-1.0.4.jar"
      cp -v "$TMPDIR/scala-parallel-collections_2.13-1.0.4.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-parallel-collections_2.13/1.0.4";
  };

  "org.scala-sbt_io_2.13-1.10.3" = fetchurl {
    name = "org.scala-sbt_io_2.13-1.10.3";
    hash = "sha256-+v1VvZGVtuyxaFCTxa66IGrvdqCDSJXPBAtHwDmdNQI=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/io_2.13/1.10.3/io_2.13-1.10.3.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/io_2.13-1.10.3.pom"
            
      downloadedFile=$TMPDIR/io_2.13-1.10.3.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/io_2.13/1.10.3/io_2.13-1.10.3.jar"
      cp -v "$TMPDIR/io_2.13-1.10.3.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/io_2.13/1.10.3";
  };

  "com.github.lolgab_mill-mima-worker-api_2.13-0.0.23" = fetchurl {
    name = "com.github.lolgab_mill-mima-worker-api_2.13-0.0.23";
    hash = "sha256-JxP3wz4XpsAlNj0u5iP6aQvPJa6QbB9RvBgY+TppWZE=";
    url = "https://repo1.maven.org/maven2/com/github/lolgab/mill-mima-worker-api_2.13/0.0.23/mill-mima-worker-api_2.13-0.0.23.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/mill-mima-worker-api_2.13-0.0.23.pom"
            
      downloadedFile=$TMPDIR/mill-mima-worker-api_2.13-0.0.23.jar
      tryDownload "https://repo1.maven.org/maven2/com/github/lolgab/mill-mima-worker-api_2.13/0.0.23/mill-mima-worker-api_2.13-0.0.23.jar"
      cp -v "$TMPDIR/mill-mima-worker-api_2.13-0.0.23.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/github/lolgab/mill-mima-worker-api_2.13/0.0.23";
  };

  "org.fusesource_fusesource-pom-1.11" = fetchurl {
    name = "org.fusesource_fusesource-pom-1.11";
    hash = "sha256-llJud8AvCnxs6bNmT3rsDinF3V2XIlfkEXyflCJQBgQ=";
    url = "https://repo1.maven.org/maven2/org/fusesource/fusesource-pom/1.11/fusesource-pom-1.11.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/fusesource-pom-1.11.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/fusesource/fusesource-pom/1.11";
  };

  "javax.inject_javax.inject-1" = fetchurl {
    name = "javax.inject_javax.inject-1";
    hash = "sha256-CZm6Lb7D5az8nprqBvjNerGQjB0xPaY56/RvKwSZIxE=";
    url = "https://repo1.maven.org/maven2/javax/inject/javax.inject/1/javax.inject-1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/javax.inject-1.pom"
            
      downloadedFile=$TMPDIR/javax.inject-1.jar
      tryDownload "https://repo1.maven.org/maven2/javax/inject/javax.inject/1/javax.inject-1.jar"
      cp -v "$TMPDIR/javax.inject-1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/javax/inject/javax.inject/1";
  };

  "org.scala-sbt_zinc-persist_2.13-1.10.7" = fetchurl {
    name = "org.scala-sbt_zinc-persist_2.13-1.10.7";
    hash = "sha256-aO6mPsEjKHbl0ZqB/a/hZ/FArCqXpR63D8y86bxkwpU=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/zinc-persist_2.13/1.10.7/zinc-persist_2.13-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/zinc-persist_2.13-1.10.7.pom"
            
      downloadedFile=$TMPDIR/zinc-persist_2.13-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/zinc-persist_2.13/1.10.7/zinc-persist_2.13-1.10.7.jar"
      cp -v "$TMPDIR/zinc-persist_2.13-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-persist_2.13/1.10.7";
  };

  "com.lihaoyi_geny_2.13-1.1.0" = fetchurl {
    name = "com.lihaoyi_geny_2.13-1.1.0";
    hash = "sha256-z9oB4D+MOO9BqE/1pf/E4NGbxHTHsoS9N9TPW/7ofA4=";
    url = "https://repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.1.0/geny_2.13-1.1.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/geny_2.13-1.1.0.pom"
            
      downloadedFile=$TMPDIR/geny_2.13-1.1.0.jar
      tryDownload "https://repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.1.0/geny_2.13-1.1.0.jar"
      cp -v "$TMPDIR/geny_2.13-1.1.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.1.0";
  };

  "org.scala-sbt_util-interface-1.10.7" = fetchurl {
    name = "org.scala-sbt_util-interface-1.10.7";
    hash = "sha256-cIOD5+vCDptOP6jwds5yG+23h2H54npBzGu3jrCQlvQ=";
    url = "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.7/util-interface-1.10.7.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/util-interface-1.10.7.pom"
            
      downloadedFile=$TMPDIR/util-interface-1.10.7.jar
      tryDownload "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.7/util-interface-1.10.7.jar"
      cp -v "$TMPDIR/util-interface-1.10.7.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.7";
  };

  "org.apache.commons_commons-parent-54" = fetchurl {
    name = "org.apache.commons_commons-parent-54";
    hash = "sha256-IxtRnSX/ML4/nBBCQM71RnlOAiTa1JCC4FBZiP7VxQ4=";
    url = "https://repo1.maven.org/maven2/org/apache/commons/commons-parent/54/commons-parent-54.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/commons-parent-54.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-parent/54";
  };

  "com.eed3si9n_shaded-jawn-parser_2.13-1.3.2" = fetchurl {
    name = "com.eed3si9n_shaded-jawn-parser_2.13-1.3.2";
    hash = "sha256-k0UsS5J5CXho/H4FngEcxAkNJ2ZjpecqDmKBvxIMuBs=";
    url = "https://repo1.maven.org/maven2/com/eed3si9n/shaded-jawn-parser_2.13/1.3.2/shaded-jawn-parser_2.13-1.3.2.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/shaded-jawn-parser_2.13-1.3.2.pom"
            
      downloadedFile=$TMPDIR/shaded-jawn-parser_2.13-1.3.2.jar
      tryDownload "https://repo1.maven.org/maven2/com/eed3si9n/shaded-jawn-parser_2.13/1.3.2/shaded-jawn-parser_2.13-1.3.2.jar"
      cp -v "$TMPDIR/shaded-jawn-parser_2.13-1.3.2.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/com/eed3si9n/shaded-jawn-parser_2.13/1.3.2";
  };

  "org.junit_junit-bom-5.10.3" = fetchurl {
    name = "org.junit_junit-bom-5.10.3";
    hash = "sha256-V+Pp8ndKoaD1fkc4oK9oU0+rrJ5hFRyuVcUnD0LI2Fw=";
    url = "https://repo1.maven.org/maven2/org/junit/junit-bom/5.10.3/junit-bom-5.10.3.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/junit-bom-5.10.3.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.10.3";
  };

  "org.apache.commons_commons-text-1.10.0" = fetchurl {
    name = "org.apache.commons_commons-text-1.10.0";
    hash = "sha256-88i8dIC8Y90V2Mgi0WgSUqbu0JvFmAoF8Ue0B3jlXP4=";
    url = "https://repo1.maven.org/maven2/org/apache/commons/commons-text/1.10.0/commons-text-1.10.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/commons-text-1.10.0.pom"
            
      downloadedFile=$TMPDIR/commons-text-1.10.0.jar
      tryDownload "https://repo1.maven.org/maven2/org/apache/commons/commons-text/1.10.0/commons-text-1.10.0.jar"
      cp -v "$TMPDIR/commons-text-1.10.0.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-text/1.10.0";
  };

  "org.jline_jline-native-3.27.1" = fetchurl {
    name = "org.jline_jline-native-3.27.1";
    hash = "sha256-XyhCZMcwu/OXdQ8BTM+qGgjGzMano5DJoghn1+/yr+Q=";
    url = "https://repo1.maven.org/maven2/org/jline/jline-native/3.27.1/jline-native-3.27.1.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/jline-native-3.27.1.pom"
            
      downloadedFile=$TMPDIR/jline-native-3.27.1.jar
      tryDownload "https://repo1.maven.org/maven2/org/jline/jline-native/3.27.1/jline-native-3.27.1.jar"
      cp -v "$TMPDIR/jline-native-3.27.1.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/jline/jline-native/3.27.1";
  };

  "org.apache.logging_logging-parent-11.3.0" = fetchurl {
    name = "org.apache.logging_logging-parent-11.3.0";
    hash = "sha256-06rPgZ5cRXf8cg84KMl7HVR3vcgvV0ThY76UsgAFf+w=";
    url = "https://repo1.maven.org/maven2/org/apache/logging/logging-parent/11.3.0/logging-parent-11.3.0.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/logging-parent-11.3.0.pom"
      
    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/apache/logging/logging-parent/11.3.0";
  };

  "org.json4s_json4s-native-core_2.13-4.0.6" = fetchurl {
    name = "org.json4s_json4s-native-core_2.13-4.0.6";
    hash = "sha256-1hUyxRb3JEnYpGTlidXIHBnoRjhFz2x/0WgcZQvqEr4=";
    url = "https://repo1.maven.org/maven2/org/json4s/json4s-native-core_2.13/4.0.6/json4s-native-core_2.13-4.0.6.pom";
    recursiveHash = true;
    downloadToTemp = true;
    postFetch = ''
      mkdir -p "$out"
      cp -v "$downloadedFile" "$out/json4s-native-core_2.13-4.0.6.pom"
            
      downloadedFile=$TMPDIR/json4s-native-core_2.13-4.0.6.jar
      tryDownload "https://repo1.maven.org/maven2/org/json4s/json4s-native-core_2.13/4.0.6/json4s-native-core_2.13-4.0.6.jar"
      cp -v "$TMPDIR/json4s-native-core_2.13-4.0.6.jar" "$out/"

    '';
    passthru.installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-native-core_2.13/4.0.6";
  };

}
