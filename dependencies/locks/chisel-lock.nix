{ fetchurl }:
let
  fetchMaven =
    {
      name,
      urls,
      hash,
      installPath,
    }:
    with builtins;
    let
      firstUrl = head urls;
      otherUrls = filter (elem: elem != firstUrl) urls;
    in
    fetchurl {
      inherit name hash;
      passthru = { inherit installPath; };
      url = firstUrl;
      recursiveHash = true;
      downloadToTemp = true;
      postFetch =
        ''
          mkdir -p "$out"
          cp -v "$downloadedFile" "$out/${baseNameOf firstUrl}"
        ''
        + concatStringsSep "\n" (
          map (
            elem:
            let
              filename = baseNameOf elem;
            in
            ''
              downloadedFile=$TMPDIR/${filename}
              tryDownload ${elem}
              cp -v "$TMPDIR/${filename}" "$out/"
            ''
          ) otherUrls
        );
    };
in
{

  "commons-logging_commons-logging-1.2" = fetchMaven {
    name = "commons-logging_commons-logging-1.2";
    urls = [
      "https://repo1.maven.org/maven2/commons-logging/commons-logging/1.2/commons-logging-1.2.jar"
      "https://repo1.maven.org/maven2/commons-logging/commons-logging/1.2/commons-logging-1.2.pom"
    ];
    hash = "sha256-IV6PwglWdPf+kCvnR75xDQBzCdOycCc5KxHMU0xcPRs=";
    installPath = "https/repo1.maven.org/maven2/commons-logging/commons-logging/1.2";
  };

  "co.fs2_fs2-core_2.13-3.2.8" = fetchMaven {
    name = "co.fs2_fs2-core_2.13-3.2.8";
    urls = [
      "https://repo1.maven.org/maven2/co/fs2/fs2-core_2.13/3.2.8/fs2-core_2.13-3.2.8.jar"
      "https://repo1.maven.org/maven2/co/fs2/fs2-core_2.13/3.2.8/fs2-core_2.13-3.2.8.pom"
    ];
    hash = "sha256-lrzOSlrA5HqXBSVMXlp5fjPKwmk4hZdRwkDT/pSRsZU=";
    installPath = "https/repo1.maven.org/maven2/co/fs2/fs2-core_2.13/3.2.8";
  };

  "co.fs2_fs2-io_2.13-3.2.8" = fetchMaven {
    name = "co.fs2_fs2-io_2.13-3.2.8";
    urls = [
      "https://repo1.maven.org/maven2/co/fs2/fs2-io_2.13/3.2.8/fs2-io_2.13-3.2.8.jar"
      "https://repo1.maven.org/maven2/co/fs2/fs2-io_2.13/3.2.8/fs2-io_2.13-3.2.8.pom"
    ];
    hash = "sha256-eP83Qh2TTdFasbIY3v1FVTKYcxUeAr3RmUOCAEmjnB8=";
    installPath = "https/repo1.maven.org/maven2/co/fs2/fs2-io_2.13/3.2.8";
  };

  "com.47deg_github4s_2.13-0.31.1" = fetchMaven {
    name = "com.47deg_github4s_2.13-0.31.1";
    urls = [
      "https://repo1.maven.org/maven2/com/47deg/github4s_2.13/0.31.1/github4s_2.13-0.31.1.jar"
      "https://repo1.maven.org/maven2/com/47deg/github4s_2.13/0.31.1/github4s_2.13-0.31.1.pom"
    ];
    hash = "sha256-YrdkrFExmaZQdJt3tpULjIn0tgobI3DvGt2kBIFtZkU=";
    installPath = "https/repo1.maven.org/maven2/com/47deg/github4s_2.13/0.31.1";
  };

  "com.chuusai_shapeless_2.13-2.3.9" = fetchMaven {
    name = "com.chuusai_shapeless_2.13-2.3.9";
    urls = [
      "https://repo1.maven.org/maven2/com/chuusai/shapeless_2.13/2.3.9/shapeless_2.13-2.3.9.jar"
      "https://repo1.maven.org/maven2/com/chuusai/shapeless_2.13/2.3.9/shapeless_2.13-2.3.9.pom"
    ];
    hash = "sha256-9JMPn3tKH9dIb6bpetRP0BXSIWqfhExRES7rfxBiYp0=";
    installPath = "https/repo1.maven.org/maven2/com/chuusai/shapeless_2.13/2.3.9";
  };

  "com.comcast_ip4s-core_2.13-3.1.3" = fetchMaven {
    name = "com.comcast_ip4s-core_2.13-3.1.3";
    urls = [
      "https://repo1.maven.org/maven2/com/comcast/ip4s-core_2.13/3.1.3/ip4s-core_2.13-3.1.3.jar"
      "https://repo1.maven.org/maven2/com/comcast/ip4s-core_2.13/3.1.3/ip4s-core_2.13-3.1.3.pom"
    ];
    hash = "sha256-ZURGcphlOLD+EiLnZfEa0skkDICrs486WXZJ7lszBig=";
    installPath = "https/repo1.maven.org/maven2/com/comcast/ip4s-core_2.13/3.1.3";
  };

  "com.eed3si9n_shaded-jawn-parser_2.13-1.3.2" = fetchMaven {
    name = "com.eed3si9n_shaded-jawn-parser_2.13-1.3.2";
    urls = [
      "https://repo1.maven.org/maven2/com/eed3si9n/shaded-jawn-parser_2.13/1.3.2/shaded-jawn-parser_2.13-1.3.2.jar"
      "https://repo1.maven.org/maven2/com/eed3si9n/shaded-jawn-parser_2.13/1.3.2/shaded-jawn-parser_2.13-1.3.2.pom"
    ];
    hash = "sha256-k0UsS5J5CXho/H4FngEcxAkNJ2ZjpecqDmKBvxIMuBs=";
    installPath = "https/repo1.maven.org/maven2/com/eed3si9n/shaded-jawn-parser_2.13/1.3.2";
  };

  "com.eed3si9n_shaded-scalajson_2.13-1.0.0-M4" = fetchMaven {
    name = "com.eed3si9n_shaded-scalajson_2.13-1.0.0-M4";
    urls = [
      "https://repo1.maven.org/maven2/com/eed3si9n/shaded-scalajson_2.13/1.0.0-M4/shaded-scalajson_2.13-1.0.0-M4.jar"
      "https://repo1.maven.org/maven2/com/eed3si9n/shaded-scalajson_2.13/1.0.0-M4/shaded-scalajson_2.13-1.0.0-M4.pom"
    ];
    hash = "sha256-JyvPek41KleFIS5g4bqLm+qUw5FlX51/rnvv/BT2pk0=";
    installPath = "https/repo1.maven.org/maven2/com/eed3si9n/shaded-scalajson_2.13/1.0.0-M4";
  };

  "com.eed3si9n_sjson-new-core_2.13-0.10.1" = fetchMaven {
    name = "com.eed3si9n_sjson-new-core_2.13-0.10.1";
    urls = [
      "https://repo1.maven.org/maven2/com/eed3si9n/sjson-new-core_2.13/0.10.1/sjson-new-core_2.13-0.10.1.jar"
      "https://repo1.maven.org/maven2/com/eed3si9n/sjson-new-core_2.13/0.10.1/sjson-new-core_2.13-0.10.1.pom"
    ];
    hash = "sha256-sFHoDAQBTHju2EgUOPuO9tM/SLAdb8X/oNSnar0iYoQ=";
    installPath = "https/repo1.maven.org/maven2/com/eed3si9n/sjson-new-core_2.13/0.10.1";
  };

  "com.eed3si9n_sjson-new-core_2.13-0.9.0" = fetchMaven {
    name = "com.eed3si9n_sjson-new-core_2.13-0.9.0";
    urls = [
      "https://repo1.maven.org/maven2/com/eed3si9n/sjson-new-core_2.13/0.9.0/sjson-new-core_2.13-0.9.0.pom"
    ];
    hash = "sha256-WlJsXRKj77jzoFN6d1V/+jAEl37mxggg85F3o8oD+bY=";
    installPath = "https/repo1.maven.org/maven2/com/eed3si9n/sjson-new-core_2.13/0.9.0";
  };

  "com.eed3si9n_sjson-new-scalajson_2.13-0.10.1" = fetchMaven {
    name = "com.eed3si9n_sjson-new-scalajson_2.13-0.10.1";
    urls = [
      "https://repo1.maven.org/maven2/com/eed3si9n/sjson-new-scalajson_2.13/0.10.1/sjson-new-scalajson_2.13-0.10.1.jar"
      "https://repo1.maven.org/maven2/com/eed3si9n/sjson-new-scalajson_2.13/0.10.1/sjson-new-scalajson_2.13-0.10.1.pom"
    ];
    hash = "sha256-DBGJ34c7lyt3m4o5ULwsRk1xPqtHHHKcNgU4nlO/dJY=";
    installPath = "https/repo1.maven.org/maven2/com/eed3si9n/sjson-new-scalajson_2.13/0.10.1";
  };

  "com.fasterxml_oss-parent-41" = fetchMaven {
    name = "com.fasterxml_oss-parent-41";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/oss-parent/41/oss-parent-41.pom" ];
    hash = "sha256-Lz63NGj0J8xjePtb7p69ACd08meStmdjmgtoh9zp2tQ=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/oss-parent/41";
  };

  "com.fasterxml_oss-parent-50" = fetchMaven {
    name = "com.fasterxml_oss-parent-50";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/oss-parent/50/oss-parent-50.pom" ];
    hash = "sha256-2z6+ukMOEKSrgEACAV2Qo5AF5bBFbMhoZVekS4VelPQ=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/oss-parent/50";
  };

  "com.fasterxml_oss-parent-58" = fetchMaven {
    name = "com.fasterxml_oss-parent-58";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/oss-parent/58/oss-parent-58.pom" ];
    hash = "sha256-wVCyn9u4Q5PMWSigrfRD2c90jacWbffIBxjXZq/VOSw=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/oss-parent/58";
  };

  "com.lihaoyi_fansi_2.13-0.5.0" = fetchMaven {
    name = "com.lihaoyi_fansi_2.13-0.5.0";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/fansi_2.13/0.5.0/fansi_2.13-0.5.0.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/fansi_2.13/0.5.0/fansi_2.13-0.5.0.pom"
    ];
    hash = "sha256-iRaKoBsS7VOiQA0yj/wRNKo2NCHWteW0gM99kKObdns=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/fansi_2.13/0.5.0";
  };

  "com.lihaoyi_geny_2.13-1.1.0" = fetchMaven {
    name = "com.lihaoyi_geny_2.13-1.1.0";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.1.0/geny_2.13-1.1.0.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.1.0/geny_2.13-1.1.0.pom"
    ];
    hash = "sha256-z9oB4D+MOO9BqE/1pf/E4NGbxHTHsoS9N9TPW/7ofA4=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.1.0";
  };

  "com.lihaoyi_geny_2.13-1.1.1" = fetchMaven {
    name = "com.lihaoyi_geny_2.13-1.1.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.1.1/geny_2.13-1.1.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.1.1/geny_2.13-1.1.1.pom"
    ];
    hash = "sha256-+gQ8X4oSRU30RdF5kE2Gn8nxmo3RJEShiEyyzUJd088=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.1.1";
  };

  "com.lihaoyi_geny_3-1.1.0" = fetchMaven {
    name = "com.lihaoyi_geny_3-1.1.0";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/geny_3/1.1.0/geny_3-1.1.0.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/geny_3/1.1.0/geny_3-1.1.0.pom"
    ];
    hash = "sha256-Vckcyv1W77OjMiIqE6SHkCMkyCF9wiPy5YBWG6owrsU=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/geny_3/1.1.0";
  };

  "com.lihaoyi_mainargs_2.13-0.7.6" = fetchMaven {
    name = "com.lihaoyi_mainargs_2.13-0.7.6";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/mainargs_2.13/0.7.6/mainargs_2.13-0.7.6.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/mainargs_2.13/0.7.6/mainargs_2.13-0.7.6.pom"
    ];
    hash = "sha256-3VNPfYCWjDt/Sln9JRJ3/aqxZT72ZqwdVdE7ONtpGXM=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mainargs_2.13/0.7.6";
  };

  "com.lihaoyi_mill-contrib-jmh_2.13-0.12.8-1-46e216" = fetchMaven {
    name = "com.lihaoyi_mill-contrib-jmh_2.13-0.12.8-1-46e216";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-contrib-jmh_2.13/0.12.8-1-46e216/mill-contrib-jmh_2.13-0.12.8-1-46e216.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-contrib-jmh_2.13/0.12.8-1-46e216/mill-contrib-jmh_2.13-0.12.8-1-46e216.pom"
    ];
    hash = "sha256-1meI7K7hijKyXySfEAMYrRcNMhIjBpsQThyGJvmsI8o=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-contrib-jmh_2.13/0.12.8-1-46e216";
  };

  "com.lihaoyi_mill-main-api_2.13-0.12.8-1-46e216" = fetchMaven {
    name = "com.lihaoyi_mill-main-api_2.13-0.12.8-1-46e216";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-main-api_2.13/0.12.8-1-46e216/mill-main-api_2.13-0.12.8-1-46e216.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-main-api_2.13/0.12.8-1-46e216/mill-main-api_2.13-0.12.8-1-46e216.pom"
    ];
    hash = "sha256-4uPDK4pTRGogIMWaYpRhWg+D8C2gDvaX88/x47X06Ls=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-main-api_2.13/0.12.8-1-46e216";
  };

  "com.lihaoyi_mill-main-client-0.12.8-1-46e216" = fetchMaven {
    name = "com.lihaoyi_mill-main-client-0.12.8-1-46e216";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-main-client/0.12.8-1-46e216/mill-main-client-0.12.8-1-46e216.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-main-client/0.12.8-1-46e216/mill-main-client-0.12.8-1-46e216.pom"
    ];
    hash = "sha256-YMhZ7tABUyMCFXru2tjJK9IA73Z11n5w/RH5r4ia3q8=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-main-client/0.12.8-1-46e216";
  };

  "com.lihaoyi_mill-moduledefs_2.13-0.11.2" = fetchMaven {
    name = "com.lihaoyi_mill-moduledefs_2.13-0.11.2";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-moduledefs_2.13/0.11.2/mill-moduledefs_2.13-0.11.2.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-moduledefs_2.13/0.11.2/mill-moduledefs_2.13-0.11.2.pom"
    ];
    hash = "sha256-aTQtCHGjdmdIWSZgyv6EllswGet1c2gKJ0nuGxu+TpA=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-moduledefs_2.13/0.11.2";
  };

  "com.lihaoyi_mill-runner-linenumbers_2.13-0.12.8-1-46e216" = fetchMaven {
    name = "com.lihaoyi_mill-runner-linenumbers_2.13-0.12.8-1-46e216";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-linenumbers_2.13/0.12.8-1-46e216/mill-runner-linenumbers_2.13-0.12.8-1-46e216.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-linenumbers_2.13/0.12.8-1-46e216/mill-runner-linenumbers_2.13-0.12.8-1-46e216.pom"
    ];
    hash = "sha256-87nmecp5r+JPxSGxJIQz0wLptyW3yTilDK4CQaQlcsY=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-runner-linenumbers_2.13/0.12.8-1-46e216";
  };

  "com.lihaoyi_mill-scala-compiler-bridge_2.13.11-0.0.1" = fetchMaven {
    name = "com.lihaoyi_mill-scala-compiler-bridge_2.13.11-0.0.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.11/0.0.1/mill-scala-compiler-bridge_2.13.11-0.0.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.11/0.0.1/mill-scala-compiler-bridge_2.13.11-0.0.1.pom"
    ];
    hash = "sha256-85VIE0haqL2KS09/qnmszLSlEg6zwnxOdv22lIHNWEQ=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.11/0.0.1";
  };

  "com.lihaoyi_mill-scala-compiler-bridge_2.13.12-0.0.1" = fetchMaven {
    name = "com.lihaoyi_mill-scala-compiler-bridge_2.13.12-0.0.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.12/0.0.1/mill-scala-compiler-bridge_2.13.12-0.0.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.12/0.0.1/mill-scala-compiler-bridge_2.13.12-0.0.1.pom"
    ];
    hash = "sha256-GaXk8CIA96v8LO4rVEQiDCtSYt+W/jflKFVX5MSJ7Bo=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.12/0.0.1";
  };

  "com.lihaoyi_mill-scala-compiler-bridge_2.13.13-0.0.1" = fetchMaven {
    name = "com.lihaoyi_mill-scala-compiler-bridge_2.13.13-0.0.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.13/0.0.1/mill-scala-compiler-bridge_2.13.13-0.0.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.13/0.0.1/mill-scala-compiler-bridge_2.13.13-0.0.1.pom"
    ];
    hash = "sha256-jaAbMoJZqGg4nuOFVDeyYDGIQ7ZM6iG8GGjlPIuhhoQ=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.13/0.0.1";
  };

  "com.lihaoyi_mill-scala-compiler-bridge_2.13.14-0.0.1" = fetchMaven {
    name = "com.lihaoyi_mill-scala-compiler-bridge_2.13.14-0.0.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.14/0.0.1/mill-scala-compiler-bridge_2.13.14-0.0.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.14/0.0.1/mill-scala-compiler-bridge_2.13.14-0.0.1.pom"
    ];
    hash = "sha256-KkXlgNvX7H0Kfg+LjXNbkzSG6lIi4G9aGeKzyeyz73o=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.14/0.0.1";
  };

  "com.lihaoyi_mill-scala-compiler-bridge_2.13.15-0.0.1" = fetchMaven {
    name = "com.lihaoyi_mill-scala-compiler-bridge_2.13.15-0.0.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.15/0.0.1/mill-scala-compiler-bridge_2.13.15-0.0.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.15/0.0.1/mill-scala-compiler-bridge_2.13.15-0.0.1.pom"
    ];
    hash = "sha256-uTyXjgTJGlaKl8jCUp9A6uDdma97ixL65GNVD9l9oOw=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-scala-compiler-bridge_2.13.15/0.0.1";
  };

  "com.lihaoyi_mill-scalalib-api_2.13-0.12.8-1-46e216" = fetchMaven {
    name = "com.lihaoyi_mill-scalalib-api_2.13-0.12.8-1-46e216";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-scalalib-api_2.13/0.12.8-1-46e216/mill-scalalib-api_2.13-0.12.8-1-46e216.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-scalalib-api_2.13/0.12.8-1-46e216/mill-scalalib-api_2.13-0.12.8-1-46e216.pom"
    ];
    hash = "sha256-8xD1JkQ+PyCOCEYO/mlpmkQ1PpqIRjHnlwjI46Q/TNY=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-scalalib-api_2.13/0.12.8-1-46e216";
  };

  "com.lihaoyi_mill-scalalib-worker_2.13-0.12.8-1-46e216" = fetchMaven {
    name = "com.lihaoyi_mill-scalalib-worker_2.13-0.12.8-1-46e216";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-scalalib-worker_2.13/0.12.8-1-46e216/mill-scalalib-worker_2.13-0.12.8-1-46e216.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-scalalib-worker_2.13/0.12.8-1-46e216/mill-scalalib-worker_2.13-0.12.8-1-46e216.pom"
    ];
    hash = "sha256-SJG7mGWhe+4a2xkmFWQqn/QUBb+RYMpSdB7b1jv7JQw=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-scalalib-worker_2.13/0.12.8-1-46e216";
  };

  "com.lihaoyi_os-lib_2.13-0.10.0" = fetchMaven {
    name = "com.lihaoyi_os-lib_2.13-0.10.0";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.10.0/os-lib_2.13-0.10.0.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.10.0/os-lib_2.13-0.10.0.pom"
    ];
    hash = "sha256-QMLhQLNthyscyl83Zy+UB/mGwa+7JpM0upwj3oxg7Fw=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.10.0";
  };

  "com.lihaoyi_os-lib_2.13-0.11.4-M6" = fetchMaven {
    name = "com.lihaoyi_os-lib_2.13-0.11.4-M6";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.11.4-M6/os-lib_2.13-0.11.4-M6.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.11.4-M6/os-lib_2.13-0.11.4-M6.pom"
    ];
    hash = "sha256-Xfo/y+4tKe7wAUFY1nyyh9is8M0l4sYU4OnheEljEL8=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.11.4-M6";
  };

  "com.lihaoyi_os-lib_3-0.10.0" = fetchMaven {
    name = "com.lihaoyi_os-lib_3-0.10.0";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_3/0.10.0/os-lib_3-0.10.0.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_3/0.10.0/os-lib_3-0.10.0.pom"
    ];
    hash = "sha256-EB9xqrVDVwK52Zrmy4cCe3nwm74Cl5mnghe8CnWX9hA=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/os-lib_3/0.10.0";
  };

  "com.lihaoyi_pprint_2.13-0.9.0" = fetchMaven {
    name = "com.lihaoyi_pprint_2.13-0.9.0";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/pprint_2.13/0.9.0/pprint_2.13-0.9.0.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/pprint_2.13/0.9.0/pprint_2.13-0.9.0.pom"
    ];
    hash = "sha256-RUmk2jO7irTaoMYgRK6Ui/SeyLEFCAspCehIccoQoeE=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/pprint_2.13/0.9.0";
  };

  "com.lihaoyi_scalac-mill-moduledefs-plugin_2.13.15-0.11.2" = fetchMaven {
    name = "com.lihaoyi_scalac-mill-moduledefs-plugin_2.13.15-0.11.2";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/scalac-mill-moduledefs-plugin_2.13.15/0.11.2/scalac-mill-moduledefs-plugin_2.13.15-0.11.2.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/scalac-mill-moduledefs-plugin_2.13.15/0.11.2/scalac-mill-moduledefs-plugin_2.13.15-0.11.2.pom"
    ];
    hash = "sha256-l+0NBdEWKEMILT44bK6ohiaon09cQvORWtDrCjUkn0A=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/scalac-mill-moduledefs-plugin_2.13.15/0.11.2";
  };

  "com.lihaoyi_sourcecode_2.13-0.3.0" = fetchMaven {
    name = "com.lihaoyi_sourcecode_2.13-0.3.0";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.13/0.3.0/sourcecode_2.13-0.3.0.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.13/0.3.0/sourcecode_2.13-0.3.0.pom"
    ];
    hash = "sha256-Y+QhWVO6t2oYpWS/s2aG1fHO+QZ726LyJYaGh3SL4ko=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.13/0.3.0";
  };

  "com.lihaoyi_sourcecode_2.13-0.4.0" = fetchMaven {
    name = "com.lihaoyi_sourcecode_2.13-0.4.0";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.13/0.4.0/sourcecode_2.13-0.4.0.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.13/0.4.0/sourcecode_2.13-0.4.0.pom"
    ];
    hash = "sha256-pi/E3F43hJcUYTx3hqUfOa/SGWmIcCl7z+3vCWDDrXc=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.13/0.4.0";
  };

  "com.lihaoyi_ujson_2.13-3.3.1" = fetchMaven {
    name = "com.lihaoyi_ujson_2.13-3.3.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/ujson_2.13/3.3.1/ujson_2.13-3.3.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/ujson_2.13/3.3.1/ujson_2.13-3.3.1.pom"
    ];
    hash = "sha256-tS5BVFeMdRfzGHUlrAywtQb4mG6oel56ooMEtlsWGjI=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/ujson_2.13/3.3.1";
  };

  "com.lihaoyi_ujson_3-3.3.1" = fetchMaven {
    name = "com.lihaoyi_ujson_3-3.3.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/ujson_3/3.3.1/ujson_3-3.3.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/ujson_3/3.3.1/ujson_3-3.3.1.pom"
    ];
    hash = "sha256-WBrFmNnzUHJCiuLnaN1JnZAFYKGoD7gbzeFrMljtWj8=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/ujson_3/3.3.1";
  };

  "com.lihaoyi_upack_2.13-3.3.1" = fetchMaven {
    name = "com.lihaoyi_upack_2.13-3.3.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/upack_2.13/3.3.1/upack_2.13-3.3.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/upack_2.13/3.3.1/upack_2.13-3.3.1.pom"
    ];
    hash = "sha256-rbWiMl6+OXfWP2HjpMbvkToBzWhuW2hsJD/4dd+XmOs=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upack_2.13/3.3.1";
  };

  "com.lihaoyi_upack_3-3.3.1" = fetchMaven {
    name = "com.lihaoyi_upack_3-3.3.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/upack_3/3.3.1/upack_3-3.3.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/upack_3/3.3.1/upack_3-3.3.1.pom"
    ];
    hash = "sha256-5I+QJF9ahoCA1znfnsfSDwGHhRMh08cjyKNiYw6VGqE=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upack_3/3.3.1";
  };

  "com.lihaoyi_upickle-core_2.13-3.3.1" = fetchMaven {
    name = "com.lihaoyi_upickle-core_2.13-3.3.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/upickle-core_2.13/3.3.1/upickle-core_2.13-3.3.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/upickle-core_2.13/3.3.1/upickle-core_2.13-3.3.1.pom"
    ];
    hash = "sha256-+vXjTD3FY+FMlDpvsOkhwycDbvhnIY0SOcHKOYc+StM=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle-core_2.13/3.3.1";
  };

  "com.lihaoyi_upickle-core_3-3.3.1" = fetchMaven {
    name = "com.lihaoyi_upickle-core_3-3.3.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/upickle-core_3/3.3.1/upickle-core_3-3.3.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/upickle-core_3/3.3.1/upickle-core_3-3.3.1.pom"
    ];
    hash = "sha256-ilLrjctjuOu0Qs1RAbjy9uezHXUOfgvMaVuh6ZCNflw=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle-core_3/3.3.1";
  };

  "com.lihaoyi_upickle-implicits_2.13-3.3.1" = fetchMaven {
    name = "com.lihaoyi_upickle-implicits_2.13-3.3.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_2.13/3.3.1/upickle-implicits_2.13-3.3.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_2.13/3.3.1/upickle-implicits_2.13-3.3.1.pom"
    ];
    hash = "sha256-LKWPAok7DL+YyfLv6yTwuyAG8z/74mzMrsqgUvUw9bM=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_2.13/3.3.1";
  };

  "com.lihaoyi_upickle-implicits_3-3.3.1" = fetchMaven {
    name = "com.lihaoyi_upickle-implicits_3-3.3.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_3/3.3.1/upickle-implicits_3-3.3.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_3/3.3.1/upickle-implicits_3-3.3.1.pom"
    ];
    hash = "sha256-woCFgGb/JC+nZala0DL0reBbVXtdDUaa/lTG219HOHk=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_3/3.3.1";
  };

  "com.lihaoyi_upickle_2.13-3.3.1" = fetchMaven {
    name = "com.lihaoyi_upickle_2.13-3.3.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/upickle_2.13/3.3.1/upickle_2.13-3.3.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/upickle_2.13/3.3.1/upickle_2.13-3.3.1.pom"
    ];
    hash = "sha256-1vHU3mGQey3zvyUHK9uCx+9pUnpnWe3zEMlyb8QqUFc=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle_2.13/3.3.1";
  };

  "com.lihaoyi_upickle_3-3.3.1" = fetchMaven {
    name = "com.lihaoyi_upickle_3-3.3.1";
    urls = [
      "https://repo1.maven.org/maven2/com/lihaoyi/upickle_3/3.3.1/upickle_3-3.3.1.jar"
      "https://repo1.maven.org/maven2/com/lihaoyi/upickle_3/3.3.1/upickle_3-3.3.1.pom"
    ];
    hash = "sha256-pug2T74XKw35S+3WAI3URsvG6Eq/B8MoAFYM4hvNPPs=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle_3/3.3.1";
  };

  "com.lmax_disruptor-3.4.2" = fetchMaven {
    name = "com.lmax_disruptor-3.4.2";
    urls = [
      "https://repo1.maven.org/maven2/com/lmax/disruptor/3.4.2/disruptor-3.4.2.jar"
      "https://repo1.maven.org/maven2/com/lmax/disruptor/3.4.2/disruptor-3.4.2.pom"
    ];
    hash = "sha256-nbZsn6zL8HaJOrkMiWwvCuHQumcNQYA8e6QrAjXKKKg=";
    installPath = "https/repo1.maven.org/maven2/com/lmax/disruptor/3.4.2";
  };

  "com.openhtmltopdf_openhtmltopdf-core-1.0.10" = fetchMaven {
    name = "com.openhtmltopdf_openhtmltopdf-core-1.0.10";
    urls = [
      "https://repo1.maven.org/maven2/com/openhtmltopdf/openhtmltopdf-core/1.0.10/openhtmltopdf-core-1.0.10.jar"
      "https://repo1.maven.org/maven2/com/openhtmltopdf/openhtmltopdf-core/1.0.10/openhtmltopdf-core-1.0.10.pom"
    ];
    hash = "sha256-eMJiWzyv6bOE1TjYPW2cetH1q8bLrBmtj0IICcFugu8=";
    installPath = "https/repo1.maven.org/maven2/com/openhtmltopdf/openhtmltopdf-core/1.0.10";
  };

  "com.openhtmltopdf_openhtmltopdf-parent-1.0.10" = fetchMaven {
    name = "com.openhtmltopdf_openhtmltopdf-parent-1.0.10";
    urls = [
      "https://repo1.maven.org/maven2/com/openhtmltopdf/openhtmltopdf-parent/1.0.10/openhtmltopdf-parent-1.0.10.pom"
    ];
    hash = "sha256-IZfPUVwVrT5UZyzGqwFpAEUtY4Kv9wJHLd28vU2pDr0=";
    installPath = "https/repo1.maven.org/maven2/com/openhtmltopdf/openhtmltopdf-parent/1.0.10";
  };

  "com.openhtmltopdf_openhtmltopdf-pdfbox-1.0.10" = fetchMaven {
    name = "com.openhtmltopdf_openhtmltopdf-pdfbox-1.0.10";
    urls = [
      "https://repo1.maven.org/maven2/com/openhtmltopdf/openhtmltopdf-pdfbox/1.0.10/openhtmltopdf-pdfbox-1.0.10.jar"
      "https://repo1.maven.org/maven2/com/openhtmltopdf/openhtmltopdf-pdfbox/1.0.10/openhtmltopdf-pdfbox-1.0.10.pom"
    ];
    hash = "sha256-VVZFnitqzGRuCrE6lsqo5JP14KRLK03leOMT18wJt04=";
    installPath = "https/repo1.maven.org/maven2/com/openhtmltopdf/openhtmltopdf-pdfbox/1.0.10";
  };

  "com.openhtmltopdf_openhtmltopdf-rtl-support-1.0.10" = fetchMaven {
    name = "com.openhtmltopdf_openhtmltopdf-rtl-support-1.0.10";
    urls = [
      "https://repo1.maven.org/maven2/com/openhtmltopdf/openhtmltopdf-rtl-support/1.0.10/openhtmltopdf-rtl-support-1.0.10.jar"
      "https://repo1.maven.org/maven2/com/openhtmltopdf/openhtmltopdf-rtl-support/1.0.10/openhtmltopdf-rtl-support-1.0.10.pom"
    ];
    hash = "sha256-q8ru6sYZmSY6550JXzXW92tJ3R0bUiXDoYfyog3Wyus=";
    installPath = "https/repo1.maven.org/maven2/com/openhtmltopdf/openhtmltopdf-rtl-support/1.0.10";
  };

  "com.swoval_file-tree-views-2.1.12" = fetchMaven {
    name = "com.swoval_file-tree-views-2.1.12";
    urls = [
      "https://repo1.maven.org/maven2/com/swoval/file-tree-views/2.1.12/file-tree-views-2.1.12.jar"
      "https://repo1.maven.org/maven2/com/swoval/file-tree-views/2.1.12/file-tree-views-2.1.12.pom"
    ];
    hash = "sha256-QhJJFQt5LS2THa8AyPLrj0suht4eCiAEl2sf7QsZU3I=";
    installPath = "https/repo1.maven.org/maven2/com/swoval/file-tree-views/2.1.12";
  };

  "com.typesafe_config-1.4.3" = fetchMaven {
    name = "com.typesafe_config-1.4.3";
    urls = [
      "https://repo1.maven.org/maven2/com/typesafe/config/1.4.3/config-1.4.3.jar"
      "https://repo1.maven.org/maven2/com/typesafe/config/1.4.3/config-1.4.3.pom"
    ];
    hash = "sha256-pGJKaNOiiCbTyHbP7xVNLx8QJUTcGSMUFvFDc6fePsM=";
    installPath = "https/repo1.maven.org/maven2/com/typesafe/config/1.4.3";
  };

  "de.tototec_de.tobiasroeser.mill.vcs.version_mill0.11_2.13-0.4.0" = fetchMaven {
    name = "de.tototec_de.tobiasroeser.mill.vcs.version_mill0.11_2.13-0.4.0";
    urls = [
      "https://repo1.maven.org/maven2/de/tototec/de.tobiasroeser.mill.vcs.version_mill0.11_2.13/0.4.0/de.tobiasroeser.mill.vcs.version_mill0.11_2.13-0.4.0.jar"
      "https://repo1.maven.org/maven2/de/tototec/de.tobiasroeser.mill.vcs.version_mill0.11_2.13/0.4.0/de.tobiasroeser.mill.vcs.version_mill0.11_2.13-0.4.0.pom"
    ];
    hash = "sha256-2FbYLLM5o1PFuPL+MrLzMiYzUJ7MUSTUQ/wEQM8qqXA=";
    installPath = "https/repo1.maven.org/maven2/de/tototec/de.tobiasroeser.mill.vcs.version_mill0.11_2.13/0.4.0";
  };

  "io.chris-kipp_mill-ci-release_mill0.12_2.13-0.2.1" = fetchMaven {
    name = "io.chris-kipp_mill-ci-release_mill0.12_2.13-0.2.1";
    urls = [
      "https://repo1.maven.org/maven2/io/chris-kipp/mill-ci-release_mill0.12_2.13/0.2.1/mill-ci-release_mill0.12_2.13-0.2.1.jar"
      "https://repo1.maven.org/maven2/io/chris-kipp/mill-ci-release_mill0.12_2.13/0.2.1/mill-ci-release_mill0.12_2.13-0.2.1.pom"
    ];
    hash = "sha256-BT3ltyXaRGZdP0qnujgEKt8qW3y37XMGKTqTdXUA4s8=";
    installPath = "https/repo1.maven.org/maven2/io/chris-kipp/mill-ci-release_mill0.12_2.13/0.2.1";
  };

  "io.circe_circe-core_2.13-0.14.2" = fetchMaven {
    name = "io.circe_circe-core_2.13-0.14.2";
    urls = [
      "https://repo1.maven.org/maven2/io/circe/circe-core_2.13/0.14.2/circe-core_2.13-0.14.2.jar"
      "https://repo1.maven.org/maven2/io/circe/circe-core_2.13/0.14.2/circe-core_2.13-0.14.2.pom"
    ];
    hash = "sha256-Gyy4U6eab+H/VhLhJ9WAZmE5FWsRmf4pmlU0qhjdbL0=";
    installPath = "https/repo1.maven.org/maven2/io/circe/circe-core_2.13/0.14.2";
  };

  "io.circe_circe-generic_2.13-0.14.2" = fetchMaven {
    name = "io.circe_circe-generic_2.13-0.14.2";
    urls = [
      "https://repo1.maven.org/maven2/io/circe/circe-generic_2.13/0.14.2/circe-generic_2.13-0.14.2.jar"
      "https://repo1.maven.org/maven2/io/circe/circe-generic_2.13/0.14.2/circe-generic_2.13-0.14.2.pom"
    ];
    hash = "sha256-+U4jvR9A2HqAnlqsX/qACY5gaKPBD0+nKI0m9bJ0z44=";
    installPath = "https/repo1.maven.org/maven2/io/circe/circe-generic_2.13/0.14.2";
  };

  "io.circe_circe-jawn_2.13-0.14.2" = fetchMaven {
    name = "io.circe_circe-jawn_2.13-0.14.2";
    urls = [
      "https://repo1.maven.org/maven2/io/circe/circe-jawn_2.13/0.14.2/circe-jawn_2.13-0.14.2.jar"
      "https://repo1.maven.org/maven2/io/circe/circe-jawn_2.13/0.14.2/circe-jawn_2.13-0.14.2.pom"
    ];
    hash = "sha256-/bwVlwA/eHF//oGXDb+LUezzAg/R7KWrQlwEmTZnonE=";
    installPath = "https/repo1.maven.org/maven2/io/circe/circe-jawn_2.13/0.14.2";
  };

  "io.circe_circe-numbers_2.13-0.14.2" = fetchMaven {
    name = "io.circe_circe-numbers_2.13-0.14.2";
    urls = [
      "https://repo1.maven.org/maven2/io/circe/circe-numbers_2.13/0.14.2/circe-numbers_2.13-0.14.2.jar"
      "https://repo1.maven.org/maven2/io/circe/circe-numbers_2.13/0.14.2/circe-numbers_2.13-0.14.2.pom"
    ];
    hash = "sha256-pa2oNzqOPZ0wxnbs76qrNEVKCzFqLC9P+fo+cMF4UMQ=";
    installPath = "https/repo1.maven.org/maven2/io/circe/circe-numbers_2.13/0.14.2";
  };

  "io.get-coursier_interface-1.0.28" = fetchMaven {
    name = "io.get-coursier_interface-1.0.28";
    urls = [
      "https://repo1.maven.org/maven2/io/get-coursier/interface/1.0.28/interface-1.0.28.jar"
      "https://repo1.maven.org/maven2/io/get-coursier/interface/1.0.28/interface-1.0.28.pom"
    ];
    hash = "sha256-ilqO9pRagNeDDD9UlIXzkEXhBZEJQNLyGU/FzBhqgaY=";
    installPath = "https/repo1.maven.org/maven2/io/get-coursier/interface/1.0.28";
  };

  "io.methvin_directory-watcher-0.18.0" = fetchMaven {
    name = "io.methvin_directory-watcher-0.18.0";
    urls = [
      "https://repo1.maven.org/maven2/io/methvin/directory-watcher/0.18.0/directory-watcher-0.18.0.jar"
      "https://repo1.maven.org/maven2/io/methvin/directory-watcher/0.18.0/directory-watcher-0.18.0.pom"
    ];
    hash = "sha256-SMz8VKvqIjW0kwlqHxnDnBWRBn1fQXRdKHSNwd7ejRg=";
    installPath = "https/repo1.maven.org/maven2/io/methvin/directory-watcher/0.18.0";
  };

  "io.undertow_undertow-core-2.2.30.Final" = fetchMaven {
    name = "io.undertow_undertow-core-2.2.30.Final";
    urls = [
      "https://repo1.maven.org/maven2/io/undertow/undertow-core/2.2.30.Final/undertow-core-2.2.30.Final.jar"
      "https://repo1.maven.org/maven2/io/undertow/undertow-core/2.2.30.Final/undertow-core-2.2.30.Final.pom"
    ];
    hash = "sha256-ElcNbmU35DsNNkomYMPjliG9sQaJPmn/1uMjteVQsMg=";
    installPath = "https/repo1.maven.org/maven2/io/undertow/undertow-core/2.2.30.Final";
  };

  "io.undertow_undertow-parent-2.2.30.Final" = fetchMaven {
    name = "io.undertow_undertow-parent-2.2.30.Final";
    urls = [
      "https://repo1.maven.org/maven2/io/undertow/undertow-parent/2.2.30.Final/undertow-parent-2.2.30.Final.pom"
    ];
    hash = "sha256-8FKDgkXIzj/wXav43A3YNSspRDWdClSZu8WtV9Rfr80=";
    installPath = "https/repo1.maven.org/maven2/io/undertow/undertow-parent/2.2.30.Final";
  };

  "jakarta.platform_jakarta.jakartaee-bom-9.1.0" = fetchMaven {
    name = "jakarta.platform_jakarta.jakartaee-bom-9.1.0";
    urls = [
      "https://repo1.maven.org/maven2/jakarta/platform/jakarta.jakartaee-bom/9.1.0/jakarta.jakartaee-bom-9.1.0.pom"
    ];
    hash = "sha256-kstGe15Yw9oF6LQ3Vovx1PcCUfQtNaEM7T8E5Upp1gg=";
    installPath = "https/repo1.maven.org/maven2/jakarta/platform/jakarta.jakartaee-bom/9.1.0";
  };

  "jakarta.platform_jakartaee-api-parent-9.1.0" = fetchMaven {
    name = "jakarta.platform_jakartaee-api-parent-9.1.0";
    urls = [
      "https://repo1.maven.org/maven2/jakarta/platform/jakartaee-api-parent/9.1.0/jakartaee-api-parent-9.1.0.pom"
    ];
    hash = "sha256-FrD7N30UkkRSQtD3+FPOC1fH2qrNnJw6UZQ/hNFXWrA=";
    installPath = "https/repo1.maven.org/maven2/jakarta/platform/jakartaee-api-parent/9.1.0";
  };

  "net.openhft_java-parent-pom-1.1.28" = fetchMaven {
    name = "net.openhft_java-parent-pom-1.1.28";
    urls = [
      "https://repo1.maven.org/maven2/net/openhft/java-parent-pom/1.1.28/java-parent-pom-1.1.28.pom"
    ];
    hash = "sha256-d7bOKP/hHJElmDQtIbblYDHRc8LCpqkt5Zl8aHp7l88=";
    installPath = "https/repo1.maven.org/maven2/net/openhft/java-parent-pom/1.1.28";
  };

  "net.openhft_root-parent-pom-1.2.12" = fetchMaven {
    name = "net.openhft_root-parent-pom-1.2.12";
    urls = [
      "https://repo1.maven.org/maven2/net/openhft/root-parent-pom/1.2.12/root-parent-pom-1.2.12.pom"
    ];
    hash = "sha256-D/M1qN+njmMZWqS5h27fl83Q+zWgIFjaYQkCpD2Oy/M=";
    installPath = "https/repo1.maven.org/maven2/net/openhft/root-parent-pom/1.2.12";
  };

  "net.openhft_zero-allocation-hashing-0.16" = fetchMaven {
    name = "net.openhft_zero-allocation-hashing-0.16";
    urls = [
      "https://repo1.maven.org/maven2/net/openhft/zero-allocation-hashing/0.16/zero-allocation-hashing-0.16.jar"
      "https://repo1.maven.org/maven2/net/openhft/zero-allocation-hashing/0.16/zero-allocation-hashing-0.16.pom"
    ];
    hash = "sha256-QkNOGkyP/OFWM+pv40hqR+ii4GBAcv0bbIrpG66YDMo=";
    installPath = "https/repo1.maven.org/maven2/net/openhft/zero-allocation-hashing/0.16";
  };

  "nl.big-o_liqp-0.8.2" = fetchMaven {
    name = "nl.big-o_liqp-0.8.2";
    urls = [
      "https://repo1.maven.org/maven2/nl/big-o/liqp/0.8.2/liqp-0.8.2.jar"
      "https://repo1.maven.org/maven2/nl/big-o/liqp/0.8.2/liqp-0.8.2.pom"
    ];
    hash = "sha256-yamgRk2t6//LGTLwLSNJ28rGL0mQFOU1XCThtpWwmMM=";
    installPath = "https/repo1.maven.org/maven2/nl/big-o/liqp/0.8.2";
  };

  "org.antlr_antlr4-master-4.7.2" = fetchMaven {
    name = "org.antlr_antlr4-master-4.7.2";
    urls = [ "https://repo1.maven.org/maven2/org/antlr/antlr4-master/4.7.2/antlr4-master-4.7.2.pom" ];
    hash = "sha256-Z+4f52KXe+J8mvu6l3IryRrYdsxjwj4Cztrn0OEs2dM=";
    installPath = "https/repo1.maven.org/maven2/org/antlr/antlr4-master/4.7.2";
  };

  "org.antlr_antlr4-runtime-4.7.2" = fetchMaven {
    name = "org.antlr_antlr4-runtime-4.7.2";
    urls = [
      "https://repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.7.2/antlr4-runtime-4.7.2.jar"
      "https://repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.7.2/antlr4-runtime-4.7.2.pom"
    ];
    hash = "sha256-orSo+dX/By8iQ7guGqi/mScUKmFeAp2TizPRFWLVUvY=";
    installPath = "https/repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.7.2";
  };

  "org.apache_apache-13" = fetchMaven {
    name = "org.apache_apache-13";
    urls = [ "https://repo1.maven.org/maven2/org/apache/apache/13/apache-13.pom" ];
    hash = "sha256-sACBC2XyW8OQOMbX09EPCVL/lqUvROHaHHHiQ3XpTk4=";
    installPath = "https/repo1.maven.org/maven2/org/apache/apache/13";
  };

  "org.apache_apache-16" = fetchMaven {
    name = "org.apache_apache-16";
    urls = [ "https://repo1.maven.org/maven2/org/apache/apache/16/apache-16.pom" ];
    hash = "sha256-Ffy1Lw2d5Roxr4FhpSRU4zow5rkuKRQB6kMvH52swiQ=";
    installPath = "https/repo1.maven.org/maven2/org/apache/apache/16";
  };

  "org.apache_apache-19" = fetchMaven {
    name = "org.apache_apache-19";
    urls = [ "https://repo1.maven.org/maven2/org/apache/apache/19/apache-19.pom" ];
    hash = "sha256-zhBKa7d1483sjfmn+XnLUQgYZltXXBPJayIZ44PcKHo=";
    installPath = "https/repo1.maven.org/maven2/org/apache/apache/19";
  };

  "org.apache_apache-33" = fetchMaven {
    name = "org.apache_apache-33";
    urls = [ "https://repo1.maven.org/maven2/org/apache/apache/33/apache-33.pom" ];
    hash = "sha256-Hwj0S/ETiRxq9ObIzy9OGjGShFgbWuJOEoV6skSMQzI=";
    installPath = "https/repo1.maven.org/maven2/org/apache/apache/33";
  };

  "org.chipsalliance_firtool-resolver_2.13-2.0.1" = fetchMaven {
    name = "org.chipsalliance_firtool-resolver_2.13-2.0.1";
    urls = [
      "https://repo1.maven.org/maven2/org/chipsalliance/firtool-resolver_2.13/2.0.1/firtool-resolver_2.13-2.0.1.jar"
      "https://repo1.maven.org/maven2/org/chipsalliance/firtool-resolver_2.13/2.0.1/firtool-resolver_2.13-2.0.1.pom"
    ];
    hash = "sha256-CGJ1TtugVYKbdzR1NWZunPLyxQRgKZPGQPWhTOGOeHI=";
    installPath = "https/repo1.maven.org/maven2/org/chipsalliance/firtool-resolver_2.13/2.0.1";
  };

  "org.fusesource_fusesource-pom-1.12" = fetchMaven {
    name = "org.fusesource_fusesource-pom-1.12";
    urls = [
      "https://repo1.maven.org/maven2/org/fusesource/fusesource-pom/1.12/fusesource-pom-1.12.pom"
    ];
    hash = "sha256-NUD5PZ1FYYOq8yumvT5i29Vxd2ZCI6PXImXfLe4mE30=";
    installPath = "https/repo1.maven.org/maven2/org/fusesource/fusesource-pom/1.12";
  };

  "org.http4s_http4s-circe_2.13-0.23.13" = fetchMaven {
    name = "org.http4s_http4s-circe_2.13-0.23.13";
    urls = [
      "https://repo1.maven.org/maven2/org/http4s/http4s-circe_2.13/0.23.13/http4s-circe_2.13-0.23.13.jar"
      "https://repo1.maven.org/maven2/org/http4s/http4s-circe_2.13/0.23.13/http4s-circe_2.13-0.23.13.pom"
    ];
    hash = "sha256-F5A/24Ca8CVrr7ZzHMAUxC6GXSlgJvoZ5fy7YSPG9nU=";
    installPath = "https/repo1.maven.org/maven2/org/http4s/http4s-circe_2.13/0.23.13";
  };

  "org.http4s_http4s-client_2.13-0.23.13" = fetchMaven {
    name = "org.http4s_http4s-client_2.13-0.23.13";
    urls = [
      "https://repo1.maven.org/maven2/org/http4s/http4s-client_2.13/0.23.13/http4s-client_2.13-0.23.13.jar"
      "https://repo1.maven.org/maven2/org/http4s/http4s-client_2.13/0.23.13/http4s-client_2.13-0.23.13.pom"
    ];
    hash = "sha256-tMhMS0xTLs6+zpXwjHjbwfW5a2+T+avJ53PQrgI4944=";
    installPath = "https/repo1.maven.org/maven2/org/http4s/http4s-client_2.13/0.23.13";
  };

  "org.http4s_http4s-core_2.13-0.23.13" = fetchMaven {
    name = "org.http4s_http4s-core_2.13-0.23.13";
    urls = [
      "https://repo1.maven.org/maven2/org/http4s/http4s-core_2.13/0.23.13/http4s-core_2.13-0.23.13.jar"
      "https://repo1.maven.org/maven2/org/http4s/http4s-core_2.13/0.23.13/http4s-core_2.13-0.23.13.pom"
    ];
    hash = "sha256-tNU0qURss8wyBSMGNmywrYEhMvj5NacE34vCJSrMOSk=";
    installPath = "https/repo1.maven.org/maven2/org/http4s/http4s-core_2.13/0.23.13";
  };

  "org.http4s_http4s-crypto_2.13-0.2.3" = fetchMaven {
    name = "org.http4s_http4s-crypto_2.13-0.2.3";
    urls = [
      "https://repo1.maven.org/maven2/org/http4s/http4s-crypto_2.13/0.2.3/http4s-crypto_2.13-0.2.3.jar"
      "https://repo1.maven.org/maven2/org/http4s/http4s-crypto_2.13/0.2.3/http4s-crypto_2.13-0.2.3.pom"
    ];
    hash = "sha256-a1qC2TfwUuW7+nFM0Q0kCRVVon56CxXv86Ci3xqI0r4=";
    installPath = "https/repo1.maven.org/maven2/org/http4s/http4s-crypto_2.13/0.2.3";
  };

  "org.http4s_http4s-jawn_2.13-0.23.13" = fetchMaven {
    name = "org.http4s_http4s-jawn_2.13-0.23.13";
    urls = [
      "https://repo1.maven.org/maven2/org/http4s/http4s-jawn_2.13/0.23.13/http4s-jawn_2.13-0.23.13.jar"
      "https://repo1.maven.org/maven2/org/http4s/http4s-jawn_2.13/0.23.13/http4s-jawn_2.13-0.23.13.pom"
    ];
    hash = "sha256-GN+djwLSC7YurflH6Xdhz3k1uUffRQznayhMEGUMJG8=";
    installPath = "https/repo1.maven.org/maven2/org/http4s/http4s-jawn_2.13/0.23.13";
  };

  "org.jboss_jboss-parent-23" = fetchMaven {
    name = "org.jboss_jboss-parent-23";
    urls = [ "https://repo1.maven.org/maven2/org/jboss/jboss-parent/23/jboss-parent-23.pom" ];
    hash = "sha256-NmkKsTbW8td3q4leFJinAt6IeqwtIi0cuUbsjpNyBCs=";
    installPath = "https/repo1.maven.org/maven2/org/jboss/jboss-parent/23";
  };

  "org.jboss_jboss-parent-34" = fetchMaven {
    name = "org.jboss_jboss-parent-34";
    urls = [ "https://repo1.maven.org/maven2/org/jboss/jboss-parent/34/jboss-parent-34.pom" ];
    hash = "sha256-TgbquaeRtRsZIozVtie6s0k9NM534WnDNbqu+/unM04=";
    installPath = "https/repo1.maven.org/maven2/org/jboss/jboss-parent/34";
  };

  "org.jboss_jboss-parent-35" = fetchMaven {
    name = "org.jboss_jboss-parent-35";
    urls = [ "https://repo1.maven.org/maven2/org/jboss/jboss-parent/35/jboss-parent-35.pom" ];
    hash = "sha256-dBipKKOVeA+QsqHm/ndBTRYyCYcqCLUOcx8rO3GZBvY=";
    installPath = "https/repo1.maven.org/maven2/org/jboss/jboss-parent/35";
  };

  "org.jboss_jboss-parent-36" = fetchMaven {
    name = "org.jboss_jboss-parent-36";
    urls = [ "https://repo1.maven.org/maven2/org/jboss/jboss-parent/36/jboss-parent-36.pom" ];
    hash = "sha256-q8N3JNtfAL7fx00KqtUmyir4NOKdP10JbclYN+KDMLw=";
    installPath = "https/repo1.maven.org/maven2/org/jboss/jboss-parent/36";
  };

  "org.jboss_jboss-parent-39" = fetchMaven {
    name = "org.jboss_jboss-parent-39";
    urls = [ "https://repo1.maven.org/maven2/org/jboss/jboss-parent/39/jboss-parent-39.pom" ];
    hash = "sha256-iGyoeNg1UuXVm1Vp1B8uEYgoo8ZQs9tMTPwTt9tTNfM=";
    installPath = "https/repo1.maven.org/maven2/org/jboss/jboss-parent/39";
  };

  "org.jboss_jboss-parent-43" = fetchMaven {
    name = "org.jboss_jboss-parent-43";
    urls = [ "https://repo1.maven.org/maven2/org/jboss/jboss-parent/43/jboss-parent-43.pom" ];
    hash = "sha256-Yo2fv3pSFZhbjErJZbnRrMMYLLOGpxNWwWkbvo5MjK4=";
    installPath = "https/repo1.maven.org/maven2/org/jboss/jboss-parent/43";
  };

  "org.jetbrains_annotations-15.0" = fetchMaven {
    name = "org.jetbrains_annotations-15.0";
    urls = [
      "https://repo1.maven.org/maven2/org/jetbrains/annotations/15.0/annotations-15.0.jar"
      "https://repo1.maven.org/maven2/org/jetbrains/annotations/15.0/annotations-15.0.pom"
    ];
    hash = "sha256-zKx9CDgM9iLkt5SFNiSgDzJu9AxFNPjCFWwMi9copnI=";
    installPath = "https/repo1.maven.org/maven2/org/jetbrains/annotations/15.0";
  };

  "org.jetbrains_annotations-24.0.1" = fetchMaven {
    name = "org.jetbrains_annotations-24.0.1";
    urls = [
      "https://repo1.maven.org/maven2/org/jetbrains/annotations/24.0.1/annotations-24.0.1.jar"
      "https://repo1.maven.org/maven2/org/jetbrains/annotations/24.0.1/annotations-24.0.1.pom"
    ];
    hash = "sha256-7jECYkmiX+IueCRTVx3m+ZvMhcCSGj76dzASyBxFKlc=";
    installPath = "https/repo1.maven.org/maven2/org/jetbrains/annotations/24.0.1";
  };

  "org.jline_jline-3.22.0" = fetchMaven {
    name = "org.jline_jline-3.22.0";
    urls = [
      "https://repo1.maven.org/maven2/org/jline/jline/3.22.0/jline-3.22.0.jar"
      "https://repo1.maven.org/maven2/org/jline/jline/3.22.0/jline-3.22.0.pom"
    ];
    hash = "sha256-I0ovz3Ra27RXAszepdlSnNz+M7u/+NyhBq2ZffnrU8k=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline/3.22.0";
  };

  "org.jline_jline-3.24.1" = fetchMaven {
    name = "org.jline_jline-3.24.1";
    urls = [
      "https://repo1.maven.org/maven2/org/jline/jline/3.24.1/jline-3.24.1.jar"
      "https://repo1.maven.org/maven2/org/jline/jline/3.24.1/jline-3.24.1.pom"
    ];
    hash = "sha256-UTsMeQWtJKmzb0cgJ+tjX9KC2m17SSZn8tESnJjihD0=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline/3.24.1";
  };

  "org.jline_jline-3.25.1" = fetchMaven {
    name = "org.jline_jline-3.25.1";
    urls = [
      "https://repo1.maven.org/maven2/org/jline/jline/3.25.1/jline-3.25.1.jar"
      "https://repo1.maven.org/maven2/org/jline/jline/3.25.1/jline-3.25.1.pom"
    ];
    hash = "sha256-A7XFvMymmp7sLkQQEFZdVJJZicA+cvXwPyyA0JMww2U=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline/3.25.1";
  };

  "org.jline_jline-3.26.3" = fetchMaven {
    name = "org.jline_jline-3.26.3";
    urls = [
      "https://repo1.maven.org/maven2/org/jline/jline/3.26.3/jline-3.26.3.jar"
      "https://repo1.maven.org/maven2/org/jline/jline/3.26.3/jline-3.26.3.pom"
    ];
    hash = "sha256-CVg5HR6GRYVCZ+0Y3yMsCUlgFCzd7MhgMqaZIQZEus0=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline/3.26.3";
  };

  "org.jline_jline-3.27.1" = fetchMaven {
    name = "org.jline_jline-3.27.1";
    urls = [
      "https://repo1.maven.org/maven2/org/jline/jline/3.27.1/jline-3.27.1-jdk8.jar"
      "https://repo1.maven.org/maven2/org/jline/jline/3.27.1/jline-3.27.1.pom"
    ];
    hash = "sha256-GnI5uLuXJN7AvsltUpzwzGNuFYkfSQ4mxy4XLOODsmU=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline/3.27.1";
  };

  "org.jline_jline-native-3.25.1" = fetchMaven {
    name = "org.jline_jline-native-3.25.1";
    urls = [
      "https://repo1.maven.org/maven2/org/jline/jline-native/3.25.1/jline-native-3.25.1.jar"
      "https://repo1.maven.org/maven2/org/jline/jline-native/3.25.1/jline-native-3.25.1.pom"
    ];
    hash = "sha256-693lRtrr078jNfaxvkTVseTIQHpjfbJD4vD7pDPQ7LI=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-native/3.25.1";
  };

  "org.jline_jline-native-3.27.1" = fetchMaven {
    name = "org.jline_jline-native-3.27.1";
    urls = [
      "https://repo1.maven.org/maven2/org/jline/jline-native/3.27.1/jline-native-3.27.1.jar"
      "https://repo1.maven.org/maven2/org/jline/jline-native/3.27.1/jline-native-3.27.1.pom"
    ];
    hash = "sha256-XyhCZMcwu/OXdQ8BTM+qGgjGzMano5DJoghn1+/yr+Q=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-native/3.27.1";
  };

  "org.jline_jline-parent-3.22.0" = fetchMaven {
    name = "org.jline_jline-parent-3.22.0";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-parent/3.22.0/jline-parent-3.22.0.pom" ];
    hash = "sha256-onEcBbRLFP9zt0OMtf6/SNhzQNZDxFbosPRVdINwbyU=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-parent/3.22.0";
  };

  "org.jline_jline-parent-3.24.1" = fetchMaven {
    name = "org.jline_jline-parent-3.24.1";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-parent/3.24.1/jline-parent-3.24.1.pom" ];
    hash = "sha256-TmNt3xgCMMZ/wwxBKzmqlU5UAEg4F4VVkVaUsMJsGK8=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-parent/3.24.1";
  };

  "org.jline_jline-parent-3.25.1" = fetchMaven {
    name = "org.jline_jline-parent-3.25.1";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-parent/3.25.1/jline-parent-3.25.1.pom" ];
    hash = "sha256-+yzWFZBCONNCOAeh6VqkYoH+N8hZllPCKfv+93cTn18=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-parent/3.25.1";
  };

  "org.jline_jline-parent-3.27.1" = fetchMaven {
    name = "org.jline_jline-parent-3.27.1";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-parent/3.27.1/jline-parent-3.27.1.pom" ];
    hash = "sha256-Oa5DgBvf5JwZH68PDIyNkEQtm7IL04ujoeniH6GZas8=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-parent/3.27.1";
  };

  "org.jline_jline-reader-3.25.1" = fetchMaven {
    name = "org.jline_jline-reader-3.25.1";
    urls = [
      "https://repo1.maven.org/maven2/org/jline/jline-reader/3.25.1/jline-reader-3.25.1.jar"
      "https://repo1.maven.org/maven2/org/jline/jline-reader/3.25.1/jline-reader-3.25.1.pom"
    ];
    hash = "sha256-d8W/YHtMX6thHsUkm3BO8wYwA6Oz+rDBcasUArMwMXY=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-reader/3.25.1";
  };

  "org.jline_jline-terminal-3.25.1" = fetchMaven {
    name = "org.jline_jline-terminal-3.25.1";
    urls = [
      "https://repo1.maven.org/maven2/org/jline/jline-terminal/3.25.1/jline-terminal-3.25.1.jar"
      "https://repo1.maven.org/maven2/org/jline/jline-terminal/3.25.1/jline-terminal-3.25.1.pom"
    ];
    hash = "sha256-Pm00LXlWGtEWHVogvZH9suEHXk/ArOq/FNw35Hk8MJc=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-terminal/3.25.1";
  };

  "org.jline_jline-terminal-3.27.1" = fetchMaven {
    name = "org.jline_jline-terminal-3.27.1";
    urls = [
      "https://repo1.maven.org/maven2/org/jline/jline-terminal/3.27.1/jline-terminal-3.27.1.jar"
      "https://repo1.maven.org/maven2/org/jline/jline-terminal/3.27.1/jline-terminal-3.27.1.pom"
    ];
    hash = "sha256-WV77BAEncauTljUBrlYi9v3GxDDeskqQpHHD9Fdbqjw=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-terminal/3.27.1";
  };

  "org.jline_jline-terminal-jna-3.25.1" = fetchMaven {
    name = "org.jline_jline-terminal-jna-3.25.1";
    urls = [
      "https://repo1.maven.org/maven2/org/jline/jline-terminal-jna/3.25.1/jline-terminal-jna-3.25.1.jar"
      "https://repo1.maven.org/maven2/org/jline/jline-terminal-jna/3.25.1/jline-terminal-jna-3.25.1.pom"
    ];
    hash = "sha256-5+WKhwPEvBhaZr4myFh39kMp8COzHSPWbvyM6QFqUuk=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-terminal-jna/3.25.1";
  };

  "org.jline_jline-terminal-jni-3.27.1" = fetchMaven {
    name = "org.jline_jline-terminal-jni-3.27.1";
    urls = [
      "https://repo1.maven.org/maven2/org/jline/jline-terminal-jni/3.27.1/jline-terminal-jni-3.27.1.jar"
      "https://repo1.maven.org/maven2/org/jline/jline-terminal-jni/3.27.1/jline-terminal-jni-3.27.1.pom"
    ];
    hash = "sha256-AWKC7imb/rnF39PAo3bVIW430zPkyj9WozKGkPlTTBE=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-terminal-jni/3.27.1";
  };

  "org.json4s_json4s-ast_2.13-4.0.7" = fetchMaven {
    name = "org.json4s_json4s-ast_2.13-4.0.7";
    urls = [
      "https://repo1.maven.org/maven2/org/json4s/json4s-ast_2.13/4.0.7/json4s-ast_2.13-4.0.7.jar"
      "https://repo1.maven.org/maven2/org/json4s/json4s-ast_2.13/4.0.7/json4s-ast_2.13-4.0.7.pom"
    ];
    hash = "sha256-krtrf0SfBd8Jn0JaAL0ocE/QOc3yt4HomZOCzaA3Zn8=";
    installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-ast_2.13/4.0.7";
  };

  "org.json4s_json4s-ast_3-4.0.7" = fetchMaven {
    name = "org.json4s_json4s-ast_3-4.0.7";
    urls = [
      "https://repo1.maven.org/maven2/org/json4s/json4s-ast_3/4.0.7/json4s-ast_3-4.0.7.jar"
      "https://repo1.maven.org/maven2/org/json4s/json4s-ast_3/4.0.7/json4s-ast_3-4.0.7.pom"
    ];
    hash = "sha256-D2CLuSqt35FNUT3oEAOQvWRSaVZKqjblirJo3gALvR0=";
    installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-ast_3/4.0.7";
  };

  "org.json4s_json4s-core_2.13-4.0.7" = fetchMaven {
    name = "org.json4s_json4s-core_2.13-4.0.7";
    urls = [
      "https://repo1.maven.org/maven2/org/json4s/json4s-core_2.13/4.0.7/json4s-core_2.13-4.0.7.jar"
      "https://repo1.maven.org/maven2/org/json4s/json4s-core_2.13/4.0.7/json4s-core_2.13-4.0.7.pom"
    ];
    hash = "sha256-Uk2Ars+BB+5lr+8ec0RtwtRsqfsVf1TyW2Z6YhK56Kw=";
    installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-core_2.13/4.0.7";
  };

  "org.json4s_json4s-core_3-4.0.7" = fetchMaven {
    name = "org.json4s_json4s-core_3-4.0.7";
    urls = [
      "https://repo1.maven.org/maven2/org/json4s/json4s-core_3/4.0.7/json4s-core_3-4.0.7.jar"
      "https://repo1.maven.org/maven2/org/json4s/json4s-core_3/4.0.7/json4s-core_3-4.0.7.pom"
    ];
    hash = "sha256-tkIO3w6wLFAf9H5RNezqaflNgnwe6UPTWSIOMpIjavs=";
    installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-core_3/4.0.7";
  };

  "org.json4s_json4s-native-core_2.13-4.0.7" = fetchMaven {
    name = "org.json4s_json4s-native-core_2.13-4.0.7";
    urls = [
      "https://repo1.maven.org/maven2/org/json4s/json4s-native-core_2.13/4.0.7/json4s-native-core_2.13-4.0.7.jar"
      "https://repo1.maven.org/maven2/org/json4s/json4s-native-core_2.13/4.0.7/json4s-native-core_2.13-4.0.7.pom"
    ];
    hash = "sha256-pHs9ANRm8I8p24Lq+aAufLHoCOF823qACydlsAPkpYk=";
    installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-native-core_2.13/4.0.7";
  };

  "org.json4s_json4s-native-core_3-4.0.7" = fetchMaven {
    name = "org.json4s_json4s-native-core_3-4.0.7";
    urls = [
      "https://repo1.maven.org/maven2/org/json4s/json4s-native-core_3/4.0.7/json4s-native-core_3-4.0.7.jar"
      "https://repo1.maven.org/maven2/org/json4s/json4s-native-core_3/4.0.7/json4s-native-core_3-4.0.7.pom"
    ];
    hash = "sha256-kxOo3oZ1SrxJzOnU+ZJxZDxR+DPbCaUI96ZJ9meGb6I=";
    installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-native-core_3/4.0.7";
  };

  "org.json4s_json4s-native_2.13-4.0.7" = fetchMaven {
    name = "org.json4s_json4s-native_2.13-4.0.7";
    urls = [
      "https://repo1.maven.org/maven2/org/json4s/json4s-native_2.13/4.0.7/json4s-native_2.13-4.0.7.jar"
      "https://repo1.maven.org/maven2/org/json4s/json4s-native_2.13/4.0.7/json4s-native_2.13-4.0.7.pom"
    ];
    hash = "sha256-2CB0UN+Az/tda5vKKr8BvTZC+fehONFmB1V3liEcjpg=";
    installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-native_2.13/4.0.7";
  };

  "org.json4s_json4s-native_3-4.0.7" = fetchMaven {
    name = "org.json4s_json4s-native_3-4.0.7";
    urls = [
      "https://repo1.maven.org/maven2/org/json4s/json4s-native_3/4.0.7/json4s-native_3-4.0.7.jar"
      "https://repo1.maven.org/maven2/org/json4s/json4s-native_3/4.0.7/json4s-native_3-4.0.7.pom"
    ];
    hash = "sha256-L6B5TzR8t1X5AbBEXcZSfhJSUnYMBxzEkwFyIj0TQtE=";
    installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-native_3/4.0.7";
  };

  "org.json4s_json4s-scalap_2.13-4.0.7" = fetchMaven {
    name = "org.json4s_json4s-scalap_2.13-4.0.7";
    urls = [
      "https://repo1.maven.org/maven2/org/json4s/json4s-scalap_2.13/4.0.7/json4s-scalap_2.13-4.0.7.jar"
      "https://repo1.maven.org/maven2/org/json4s/json4s-scalap_2.13/4.0.7/json4s-scalap_2.13-4.0.7.pom"
    ];
    hash = "sha256-lw8n76iJsJlcZ7yhphvKbccinZAg+XeEN/fPjh5G6ak=";
    installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-scalap_2.13/4.0.7";
  };

  "org.json4s_json4s-scalap_3-4.0.7" = fetchMaven {
    name = "org.json4s_json4s-scalap_3-4.0.7";
    urls = [
      "https://repo1.maven.org/maven2/org/json4s/json4s-scalap_3/4.0.7/json4s-scalap_3-4.0.7.jar"
      "https://repo1.maven.org/maven2/org/json4s/json4s-scalap_3/4.0.7/json4s-scalap_3-4.0.7.pom"
    ];
    hash = "sha256-/9hhBdyLhhezNbjLB1Wmr1KVq0YGLHiYGeZUXj1n/nU=";
    installPath = "https/repo1.maven.org/maven2/org/json4s/json4s-scalap_3/4.0.7";
  };

  "org.jsoup_jsoup-1.15.4" = fetchMaven {
    name = "org.jsoup_jsoup-1.15.4";
    urls = [
      "https://repo1.maven.org/maven2/org/jsoup/jsoup/1.15.4/jsoup-1.15.4.jar"
      "https://repo1.maven.org/maven2/org/jsoup/jsoup/1.15.4/jsoup-1.15.4.pom"
    ];
    hash = "sha256-3Nk1Vety11VNjlGaP57Ybb2o1iaB59eXZ8xXjVFQbug=";
    installPath = "https/repo1.maven.org/maven2/org/jsoup/jsoup/1.15.4";
  };

  "org.jsoup_jsoup-1.17.2" = fetchMaven {
    name = "org.jsoup_jsoup-1.17.2";
    urls = [
      "https://repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar"
      "https://repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.pom"
    ];
    hash = "sha256-aex/2xWBJBV0CVGOIoNvOcnYi6sVTd3CwBJhM5ZUISU=";
    installPath = "https/repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2";
  };

  "org.junit_junit-bom-5.10.3" = fetchMaven {
    name = "org.junit_junit-bom-5.10.3";
    urls = [ "https://repo1.maven.org/maven2/org/junit/junit-bom/5.10.3/junit-bom-5.10.3.pom" ];
    hash = "sha256-V+Pp8ndKoaD1fkc4oK9oU0+rrJ5hFRyuVcUnD0LI2Fw=";
    installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.10.3";
  };

  "org.junit_junit-bom-5.11.0" = fetchMaven {
    name = "org.junit_junit-bom-5.11.0";
    urls = [ "https://repo1.maven.org/maven2/org/junit/junit-bom/5.11.0/junit-bom-5.11.0.pom" ];
    hash = "sha256-8Gnv8IxzEhI2ssVV5CpjvPEv7CDcoexu3wmHBi9ktkA=";
    installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.11.0";
  };

  "org.junit_junit-bom-5.11.2" = fetchMaven {
    name = "org.junit_junit-bom-5.11.2";
    urls = [ "https://repo1.maven.org/maven2/org/junit/junit-bom/5.11.2/junit-bom-5.11.2.pom" ];
    hash = "sha256-cGHayaCE9Q75/hyJE3iFhnmKFYtzLY/MLSHDid0QSHY=";
    installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.11.2";
  };

  "org.junit_junit-bom-5.8.2" = fetchMaven {
    name = "org.junit_junit-bom-5.8.2";
    urls = [ "https://repo1.maven.org/maven2/org/junit/junit-bom/5.8.2/junit-bom-5.8.2.pom" ];
    hash = "sha256-3uZs6ouEx/m0uaNQk0y7oMqoPXeNsL4K1VOhYJm9lmk=";
    installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.8.2";
  };

  "org.junit_junit-bom-5.9.2" = fetchMaven {
    name = "org.junit_junit-bom-5.9.2";
    urls = [ "https://repo1.maven.org/maven2/org/junit/junit-bom/5.9.2/junit-bom-5.9.2.pom" ];
    hash = "sha256-uGn68+1/ScKIRXjMgUllMofOsjFTxO1mfwrpSVBpP6E=";
    installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.9.2";
  };

  "org.log4s_log4s_2.13-1.10.0" = fetchMaven {
    name = "org.log4s_log4s_2.13-1.10.0";
    urls = [
      "https://repo1.maven.org/maven2/org/log4s/log4s_2.13/1.10.0/log4s_2.13-1.10.0.jar"
      "https://repo1.maven.org/maven2/org/log4s/log4s_2.13/1.10.0/log4s_2.13-1.10.0.pom"
    ];
    hash = "sha256-gBmwtvXeFmekW37S4yvlxzW3pN73Q7J75HkxO02fT5E=";
    installPath = "https/repo1.maven.org/maven2/org/log4s/log4s_2.13/1.10.0";
  };

  "org.mockito_mockito-bom-4.11.0" = fetchMaven {
    name = "org.mockito_mockito-bom-4.11.0";
    urls = [ "https://repo1.maven.org/maven2/org/mockito/mockito-bom/4.11.0/mockito-bom-4.11.0.pom" ];
    hash = "sha256-jtuaGRrHXNkevtfBAzk3OA+n5RNtrDQ0MQSqSRxUIfc=";
    installPath = "https/repo1.maven.org/maven2/org/mockito/mockito-bom/4.11.0";
  };

  "org.scala-lang_scala-compiler-2.13.11" = fetchMaven {
    name = "org.scala-lang_scala-compiler-2.13.11";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.11/scala-compiler-2.13.11.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.11/scala-compiler-2.13.11.pom"
    ];
    hash = "sha256-2bZLGDF2jy/fsP/ceKtfydCUMMjpZXCHzPcYSksyvqM=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.11";
  };

  "org.scala-lang_scala-compiler-2.13.12" = fetchMaven {
    name = "org.scala-lang_scala-compiler-2.13.12";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.12/scala-compiler-2.13.12.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.12/scala-compiler-2.13.12.pom"
    ];
    hash = "sha256-cVcD6CK1r2M07sg3/MvclRAvtoCKusp2lJFS5Bw/CaU=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.12";
  };

  "org.scala-lang_scala-compiler-2.13.13" = fetchMaven {
    name = "org.scala-lang_scala-compiler-2.13.13";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.13/scala-compiler-2.13.13.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.13/scala-compiler-2.13.13.pom"
    ];
    hash = "sha256-lHIX4OLYQ6PsAVqjFMEzct6Z/lCeUtfLw1OhFAUoMd8=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.13";
  };

  "org.scala-lang_scala-compiler-2.13.14" = fetchMaven {
    name = "org.scala-lang_scala-compiler-2.13.14";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.14/scala-compiler-2.13.14.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.14/scala-compiler-2.13.14.pom"
    ];
    hash = "sha256-R1MSUh8rAiJm6O114agQNoQyY+DBCLvl4TUgmWeKi0A=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.14";
  };

  "org.scala-lang_scala-compiler-2.13.15" = fetchMaven {
    name = "org.scala-lang_scala-compiler-2.13.15";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.15/scala-compiler-2.13.15.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.15/scala-compiler-2.13.15.pom"
    ];
    hash = "sha256-kvqWoFLNy3LGIbD6l67f66OyJq/K2L4rTStLiDzIzm8=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.15";
  };

  "org.scala-lang_scala-compiler-2.13.16" = fetchMaven {
    name = "org.scala-lang_scala-compiler-2.13.16";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.16/scala-compiler-2.13.16.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.16/scala-compiler-2.13.16.pom"
    ];
    hash = "sha256-uPxnpCaIbviBXMJjY9+MSQCPa6iqEx/zgtO926dxv+U=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.16";
  };

  "org.scala-lang_scala-library-2.13.11" = fetchMaven {
    name = "org.scala-lang_scala-library-2.13.11";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.11/scala-library-2.13.11.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.11/scala-library-2.13.11.pom"
    ];
    hash = "sha256-xmgPZ4eig7KPdRJjU1G010gZYe1jbDujcXyMoDhTcOw=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.11";
  };

  "org.scala-lang_scala-library-2.13.12" = fetchMaven {
    name = "org.scala-lang_scala-library-2.13.12";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.12/scala-library-2.13.12.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.12/scala-library-2.13.12.pom"
    ];
    hash = "sha256-lXKrUcaYvYFyltW8AxZb1apsFCr5H/5I8oF8/QWDOKQ=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.12";
  };

  "org.scala-lang_scala-library-2.13.13" = fetchMaven {
    name = "org.scala-lang_scala-library-2.13.13";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.13/scala-library-2.13.13.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.13/scala-library-2.13.13.pom"
    ];
    hash = "sha256-CnAqcbFDxIG1EhrQ+yqEUzQT3emZE9umT9NKLdTTefI=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.13";
  };

  "org.scala-lang_scala-library-2.13.14" = fetchMaven {
    name = "org.scala-lang_scala-library-2.13.14";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.14/scala-library-2.13.14.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.14/scala-library-2.13.14.pom"
    ];
    hash = "sha256-JD7ng4Rp55SXRO5Jkx8UHbSpvuXPxYuirQfj75hRnhM=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.14";
  };

  "org.scala-lang_scala-library-2.13.15" = fetchMaven {
    name = "org.scala-lang_scala-library-2.13.15";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.15/scala-library-2.13.15.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.15/scala-library-2.13.15.pom"
    ];
    hash = "sha256-JnbDGZQKZZswRZuxauQywH/4rXzwzn++kMB4lw3OfPI=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.15";
  };

  "org.scala-lang_scala-library-2.13.16" = fetchMaven {
    name = "org.scala-lang_scala-library-2.13.16";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.pom"
    ];
    hash = "sha256-7/NvAxKKPtghJ/+pTNxvmIAiAdtQXRTUvDwGGXwpnpU=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16";
  };

  "org.scala-lang_scala-reflect-2.13.11" = fetchMaven {
    name = "org.scala-lang_scala-reflect-2.13.11";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.11/scala-reflect-2.13.11.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.11/scala-reflect-2.13.11.pom"
    ];
    hash = "sha256-uOmyHJxL4YS7gAVBbeN19gC/FtEG7wxvTRM/oD2GHeU=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.11";
  };

  "org.scala-lang_scala-reflect-2.13.12" = fetchMaven {
    name = "org.scala-lang_scala-reflect-2.13.12";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.12/scala-reflect-2.13.12.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.12/scala-reflect-2.13.12.pom"
    ];
    hash = "sha256-876jILtSkA9ukYfoR7hmf9IHypGGe0DoTxyiYlVVtRU=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.12";
  };

  "org.scala-lang_scala-reflect-2.13.13" = fetchMaven {
    name = "org.scala-lang_scala-reflect-2.13.13";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.13/scala-reflect-2.13.13.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.13/scala-reflect-2.13.13.pom"
    ];
    hash = "sha256-tfmrmWZpXJi5SQ7v+gZ34nsYQ+Y44rJX+Q9JsygbGPM=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.13";
  };

  "org.scala-lang_scala-reflect-2.13.14" = fetchMaven {
    name = "org.scala-lang_scala-reflect-2.13.14";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.14/scala-reflect-2.13.14.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.14/scala-reflect-2.13.14.pom"
    ];
    hash = "sha256-khLNhLU3TwEfUUxeTeFbOxtJ31okA8grgSsVSlQGV8w=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.14";
  };

  "org.scala-lang_scala-reflect-2.13.15" = fetchMaven {
    name = "org.scala-lang_scala-reflect-2.13.15";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.15/scala-reflect-2.13.15.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.15/scala-reflect-2.13.15.pom"
    ];
    hash = "sha256-zmUU4hTEf5HC311UaNIHmzjSwWSbjXn6DyPP7ZzFy/8=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.15";
  };

  "org.scala-lang_scala-reflect-2.13.16" = fetchMaven {
    name = "org.scala-lang_scala-reflect-2.13.16";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.16/scala-reflect-2.13.16.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.16/scala-reflect-2.13.16.pom"
    ];
    hash = "sha256-Y/cXrptUKnH51rsTo8reYZbqbrWuO+fohzQW3z9Nx90=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.16";
  };

  "org.scala-lang_scala3-compiler_3-3.3.4" = fetchMaven {
    name = "org.scala-lang_scala3-compiler_3-3.3.4";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala3-compiler_3/3.3.4/scala3-compiler_3-3.3.4.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala3-compiler_3/3.3.4/scala3-compiler_3-3.3.4.pom"
    ];
    hash = "sha256-15qkuy7k4kWNHUk/JSqd2hwWB8WzMMPx1cfYCFjm4Mk=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-compiler_3/3.3.4";
  };

  "org.scala-lang_scala3-interfaces-3.3.4" = fetchMaven {
    name = "org.scala-lang_scala3-interfaces-3.3.4";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala3-interfaces/3.3.4/scala3-interfaces-3.3.4.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala3-interfaces/3.3.4/scala3-interfaces-3.3.4.pom"
    ];
    hash = "sha256-B3z36x1NYCYoZRIiOWOe5J07K8PMTiQsBFZ2vjqrtyU=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-interfaces/3.3.4";
  };

  "org.scala-lang_scala3-library_3-3.3.4" = fetchMaven {
    name = "org.scala-lang_scala3-library_3-3.3.4";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.3.4/scala3-library_3-3.3.4.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.3.4/scala3-library_3-3.3.4.pom"
    ];
    hash = "sha256-+jxXazzk+mRl2N2ynxdrRNY4Z96+qxcs8ycCLKO8M5c=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.3.4";
  };

  "org.scala-lang_scala3-sbt-bridge-3.3.4" = fetchMaven {
    name = "org.scala-lang_scala3-sbt-bridge-3.3.4";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala3-sbt-bridge/3.3.4/scala3-sbt-bridge-3.3.4.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala3-sbt-bridge/3.3.4/scala3-sbt-bridge-3.3.4.pom"
    ];
    hash = "sha256-h9Fr8l1spGFWsy3PAoC035KzmjHh6fXMuaL+gNmujvA=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-sbt-bridge/3.3.4";
  };

  "org.scala-lang_scala3-tasty-inspector_3-3.3.4" = fetchMaven {
    name = "org.scala-lang_scala3-tasty-inspector_3-3.3.4";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scala3-tasty-inspector_3/3.3.4/scala3-tasty-inspector_3-3.3.4.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scala3-tasty-inspector_3/3.3.4/scala3-tasty-inspector_3-3.3.4.pom"
    ];
    hash = "sha256-S2NR6M5BVD2/vHGGcGiVSu0R6t23UskxXvIX6Fqf47w=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-tasty-inspector_3/3.3.4";
  };

  "org.scala-lang_scaladoc_3-3.3.4" = fetchMaven {
    name = "org.scala-lang_scaladoc_3-3.3.4";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scaladoc_3/3.3.4/scaladoc_3-3.3.4.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scaladoc_3/3.3.4/scaladoc_3-3.3.4.pom"
    ];
    hash = "sha256-PI2XYc1qPkTMmpbPv6gEgBYvAen+U+xAkxNz+P64Sc8=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scaladoc_3/3.3.4";
  };

  "org.scala-lang_scalap-2.13.15" = fetchMaven {
    name = "org.scala-lang_scalap-2.13.15";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/scalap/2.13.15/scalap-2.13.15.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/scalap/2.13.15/scalap-2.13.15.pom"
    ];
    hash = "sha256-JMnmdCcFUakGj+seqTp15VYMzcq90jGjQPmKbCzY28A=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scalap/2.13.15";
  };

  "org.scala-lang_tasty-core_3-3.3.4" = fetchMaven {
    name = "org.scala-lang_tasty-core_3-3.3.4";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/tasty-core_3/3.3.4/tasty-core_3-3.3.4.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/tasty-core_3/3.3.4/tasty-core_3-3.3.4.pom"
    ];
    hash = "sha256-K0tg8Cy+B/fhui5yF4aL+a1bd3q8DF3g6wOAvPSRc+I=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/tasty-core_3/3.3.4";
  };

  "org.scala-sbt_collections_2.13-1.10.7" = fetchMaven {
    name = "org.scala-sbt_collections_2.13-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/collections_2.13/1.10.7/collections_2.13-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/collections_2.13/1.10.7/collections_2.13-1.10.7.pom"
    ];
    hash = "sha256-y4FuwehuxB+70YBIKj5jH9L8tQpHrWFpPc9VrBUzM6Y=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/collections_2.13/1.10.7";
  };

  "org.scala-sbt_compiler-bridge_2.13-1.10.7" = fetchMaven {
    name = "org.scala-sbt_compiler-bridge_2.13-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-bridge_2.13/1.10.7/compiler-bridge_2.13-1.10.7-sources.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-bridge_2.13/1.10.7/compiler-bridge_2.13-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-bridge_2.13/1.10.7/compiler-bridge_2.13-1.10.7.pom"
    ];
    hash = "sha256-jDtX3vTy7c5Ju7Yk792idscpXxfzqyRm0tubEazpQSY=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/compiler-bridge_2.13/1.10.7";
  };

  "org.scala-sbt_compiler-interface-1.10.0" = fetchMaven {
    name = "org.scala-sbt_compiler-interface-1.10.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.0/compiler-interface-1.10.0.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.0/compiler-interface-1.10.0.pom"
    ];
    hash = "sha256-bpYU74YwRESRBkSMomtxjgzATk3HkTpJYnEBK+/LJ+w=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.0";
  };

  "org.scala-sbt_compiler-interface-1.10.3" = fetchMaven {
    name = "org.scala-sbt_compiler-interface-1.10.3";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.3/compiler-interface-1.10.3.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.3/compiler-interface-1.10.3.pom"
    ];
    hash = "sha256-eUpVhTZhe/6qSWs+XkD7bDhrqCv893HCNme7G4yPyeg=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.3";
  };

  "org.scala-sbt_compiler-interface-1.10.7" = fetchMaven {
    name = "org.scala-sbt_compiler-interface-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.7/compiler-interface-1.10.7-sources.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.7/compiler-interface-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.7/compiler-interface-1.10.7.pom"
    ];
    hash = "sha256-kTQDHARJUF88Se2cOxq+vFt6hIPCn2rSQyGr96AMZWQ=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.7";
  };

  "org.scala-sbt_compiler-interface-1.8.1" = fetchMaven {
    name = "org.scala-sbt_compiler-interface-1.8.1";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.8.1/compiler-interface-1.8.1.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.8.1/compiler-interface-1.8.1.pom"
    ];
    hash = "sha256-ZDa2ylrKTUBR6cz497JOsWG6Ry1wCR6WDiDANIa1yPk=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.8.1";
  };

  "org.scala-sbt_compiler-interface-1.9.5" = fetchMaven {
    name = "org.scala-sbt_compiler-interface-1.9.5";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.9.5/compiler-interface-1.9.5.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.9.5/compiler-interface-1.9.5.pom"
    ];
    hash = "sha256-/kx55BDpsnMpIqSGTHMg+zwfn4/8Ezvl/Lv3z+ClpnI=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.9.5";
  };

  "org.scala-sbt_compiler-interface-1.9.6" = fetchMaven {
    name = "org.scala-sbt_compiler-interface-1.9.6";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.9.6/compiler-interface-1.9.6.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.9.6/compiler-interface-1.9.6.pom"
    ];
    hash = "sha256-spep2us0CWZiButV6u4/nJyRqQozTEuo83z0CR/5cos=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.9.6";
  };

  "org.scala-sbt_core-macros_2.13-1.10.7" = fetchMaven {
    name = "org.scala-sbt_core-macros_2.13-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/core-macros_2.13/1.10.7/core-macros_2.13-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/core-macros_2.13/1.10.7/core-macros_2.13-1.10.7.pom"
    ];
    hash = "sha256-rsDP4K+yiTgLhmdDP7G5iL3i43v+Dwki9pKXPeWUp4c=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/core-macros_2.13/1.10.7";
  };

  "org.scala-sbt_io_2.13-1.10.3" = fetchMaven {
    name = "org.scala-sbt_io_2.13-1.10.3";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/io_2.13/1.10.3/io_2.13-1.10.3.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/io_2.13/1.10.3/io_2.13-1.10.3.pom"
    ];
    hash = "sha256-+v1VvZGVtuyxaFCTxa66IGrvdqCDSJXPBAtHwDmdNQI=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/io_2.13/1.10.3";
  };

  "org.scala-sbt_launcher-interface-1.4.4" = fetchMaven {
    name = "org.scala-sbt_launcher-interface-1.4.4";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/launcher-interface/1.4.4/launcher-interface-1.4.4.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/launcher-interface/1.4.4/launcher-interface-1.4.4.pom"
    ];
    hash = "sha256-HWiEWRS8Grm7uQME6o7FYZFhWvJgvrvyxKXMATB0Z7E=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/launcher-interface/1.4.4";
  };

  "org.scala-sbt_sbinary_2.13-0.5.1" = fetchMaven {
    name = "org.scala-sbt_sbinary_2.13-0.5.1";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/sbinary_2.13/0.5.1/sbinary_2.13-0.5.1.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/sbinary_2.13/0.5.1/sbinary_2.13-0.5.1.pom"
    ];
    hash = "sha256-+TrjPjSy8WVXq3IYHkHHIzttvHQbgwMLkwwWBys/ryw=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/sbinary_2.13/0.5.1";
  };

  "org.scala-sbt_test-interface-1.0" = fetchMaven {
    name = "org.scala-sbt_test-interface-1.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/test-interface/1.0/test-interface-1.0.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/test-interface/1.0/test-interface-1.0.pom"
    ];
    hash = "sha256-Cc5Q+4mULLHRdw+7Wjx6spCLbKrckXHeNYjIibw4LWw=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/test-interface/1.0";
  };

  "org.scala-sbt_util-control_2.13-1.10.7" = fetchMaven {
    name = "org.scala-sbt_util-control_2.13-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/util-control_2.13/1.10.7/util-control_2.13-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/util-control_2.13/1.10.7/util-control_2.13-1.10.7.pom"
    ];
    hash = "sha256-CCG/nXpVyd7YrtCYr47tPYIQs/G6vzb/3fCyZ21drhM=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-control_2.13/1.10.7";
  };

  "org.scala-sbt_util-interface-1.10.0" = fetchMaven {
    name = "org.scala-sbt_util-interface-1.10.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.0/util-interface-1.10.0.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.0/util-interface-1.10.0.pom"
    ];
    hash = "sha256-M5aec33ZuPmuY6CNjd9qhNlpxqxG5ktQBxb7rUUpdA4=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.0";
  };

  "org.scala-sbt_util-interface-1.10.3" = fetchMaven {
    name = "org.scala-sbt_util-interface-1.10.3";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.3/util-interface-1.10.3.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.3/util-interface-1.10.3.pom"
    ];
    hash = "sha256-uu+2jvXfm2FaHkvJb44uRGdelrtS9pLfolU977MMQj0=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.3";
  };

  "org.scala-sbt_util-interface-1.10.7" = fetchMaven {
    name = "org.scala-sbt_util-interface-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.7/util-interface-1.10.7-sources.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.7/util-interface-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.7/util-interface-1.10.7.pom"
    ];
    hash = "sha256-k9TTANJrA3RAapizDe0pMLT/CkPCLweVuT8fuc40Re0=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.7";
  };

  "org.scala-sbt_util-interface-1.8.2" = fetchMaven {
    name = "org.scala-sbt_util-interface-1.8.2";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.8.2/util-interface-1.8.2.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.8.2/util-interface-1.8.2.pom"
    ];
    hash = "sha256-lPAZzAlj36mUYzNyjEeVuUw3iLCaxrgDiRG+9e7ttWM=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-interface/1.8.2";
  };

  "org.scala-sbt_util-interface-1.9.4" = fetchMaven {
    name = "org.scala-sbt_util-interface-1.9.4";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.9.4/util-interface-1.9.4.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.9.4/util-interface-1.9.4.pom"
    ];
    hash = "sha256-tljMmr/UKrmc5bPiEaEd962zf5zS1iavRbjSWZg+jxE=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-interface/1.9.4";
  };

  "org.scala-sbt_util-interface-1.9.8" = fetchMaven {
    name = "org.scala-sbt_util-interface-1.9.8";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.9.8/util-interface-1.9.8.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.9.8/util-interface-1.9.8.pom"
    ];
    hash = "sha256-7PoE3Jj8JSBaNeK3IzCSlkwArEWP1Zo+XBn0OorE1I8=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-interface/1.9.8";
  };

  "org.scala-sbt_util-logging_2.13-1.10.7" = fetchMaven {
    name = "org.scala-sbt_util-logging_2.13-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/util-logging_2.13/1.10.7/util-logging_2.13-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/util-logging_2.13/1.10.7/util-logging_2.13-1.10.7.pom"
    ];
    hash = "sha256-WfmccbZodef+h77nl7kEe6VxAsyzYlaHudZX0iyTRAs=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-logging_2.13/1.10.7";
  };

  "org.scala-sbt_util-position_2.13-1.10.7" = fetchMaven {
    name = "org.scala-sbt_util-position_2.13-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/util-position_2.13/1.10.7/util-position_2.13-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/util-position_2.13/1.10.7/util-position_2.13-1.10.7.pom"
    ];
    hash = "sha256-hhRemdHTn5rI6IpViSG7KUxU/F2idL0AQf9CdNrF6xA=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-position_2.13/1.10.7";
  };

  "org.scala-sbt_util-relation_2.13-1.10.7" = fetchMaven {
    name = "org.scala-sbt_util-relation_2.13-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/util-relation_2.13/1.10.7/util-relation_2.13-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/util-relation_2.13/1.10.7/util-relation_2.13-1.10.7.pom"
    ];
    hash = "sha256-r2kRBeuvusfdZwqZsRRuwp1Sr1PjWDuchmXbVPcSUOM=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-relation_2.13/1.10.7";
  };

  "org.scala-sbt_zinc-apiinfo_2.13-1.10.7" = fetchMaven {
    name = "org.scala-sbt_zinc-apiinfo_2.13-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc-apiinfo_2.13/1.10.7/zinc-apiinfo_2.13-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc-apiinfo_2.13/1.10.7/zinc-apiinfo_2.13-1.10.7.pom"
    ];
    hash = "sha256-nRr38N6FO18MM0+mlb9lK2EOhfl7GZ1y7ez6Eg3Ip8w=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-apiinfo_2.13/1.10.7";
  };

  "org.scala-sbt_zinc-classfile_2.13-1.10.7" = fetchMaven {
    name = "org.scala-sbt_zinc-classfile_2.13-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc-classfile_2.13/1.10.7/zinc-classfile_2.13-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc-classfile_2.13/1.10.7/zinc-classfile_2.13-1.10.7.pom"
    ];
    hash = "sha256-0UOFRvovrzzXFILxniSzo5MHr/XmSDGP4o3wh05uCxE=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-classfile_2.13/1.10.7";
  };

  "org.scala-sbt_zinc-classpath_2.13-1.10.7" = fetchMaven {
    name = "org.scala-sbt_zinc-classpath_2.13-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc-classpath_2.13/1.10.7/zinc-classpath_2.13-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc-classpath_2.13/1.10.7/zinc-classpath_2.13-1.10.7.pom"
    ];
    hash = "sha256-ozsxGbCrycacvLvk8tf0SHjdR9DU+5+494IJdkotRjg=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-classpath_2.13/1.10.7";
  };

  "org.scala-sbt_zinc-compile-core_2.13-1.10.7" = fetchMaven {
    name = "org.scala-sbt_zinc-compile-core_2.13-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc-compile-core_2.13/1.10.7/zinc-compile-core_2.13-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc-compile-core_2.13/1.10.7/zinc-compile-core_2.13-1.10.7.pom"
    ];
    hash = "sha256-E7vR41TZnHQWM6FyVr48WDhplRHFfFta4E4JEl8/CtQ=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-compile-core_2.13/1.10.7";
  };

  "org.scala-sbt_zinc-core_2.13-1.10.7" = fetchMaven {
    name = "org.scala-sbt_zinc-core_2.13-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc-core_2.13/1.10.7/zinc-core_2.13-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc-core_2.13/1.10.7/zinc-core_2.13-1.10.7.pom"
    ];
    hash = "sha256-eQFUHHuNND26t29kyYz7vbAscVCJej/Lc8eQRktDTGA=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-core_2.13/1.10.7";
  };

  "org.scala-sbt_zinc-persist-core-assembly-1.10.7" = fetchMaven {
    name = "org.scala-sbt_zinc-persist-core-assembly-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc-persist-core-assembly/1.10.7/zinc-persist-core-assembly-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc-persist-core-assembly/1.10.7/zinc-persist-core-assembly-1.10.7.pom"
    ];
    hash = "sha256-KNr16Jjhbu3hrKUn/rTpEiEqWV/mC/iFhbO0YmToUCA=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-persist-core-assembly/1.10.7";
  };

  "org.scala-sbt_zinc-persist_2.13-1.10.7" = fetchMaven {
    name = "org.scala-sbt_zinc-persist_2.13-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc-persist_2.13/1.10.7/zinc-persist_2.13-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc-persist_2.13/1.10.7/zinc-persist_2.13-1.10.7.pom"
    ];
    hash = "sha256-aO6mPsEjKHbl0ZqB/a/hZ/FArCqXpR63D8y86bxkwpU=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-persist_2.13/1.10.7";
  };

  "org.scala-sbt_zinc_2.13-1.10.7" = fetchMaven {
    name = "org.scala-sbt_zinc_2.13-1.10.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc_2.13/1.10.7/zinc_2.13-1.10.7.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/zinc_2.13/1.10.7/zinc_2.13-1.10.7.pom"
    ];
    hash = "sha256-G87j0JvuTu7cEu4gUZ268vxJ79vs7qR0p8J1wT+wwD4=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc_2.13/1.10.7";
  };

  "org.scalacheck_scalacheck_2.13-1.18.0" = fetchMaven {
    name = "org.scalacheck_scalacheck_2.13-1.18.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scalacheck/scalacheck_2.13/1.18.0/scalacheck_2.13-1.18.0.jar"
      "https://repo1.maven.org/maven2/org/scalacheck/scalacheck_2.13/1.18.0/scalacheck_2.13-1.18.0.pom"
    ];
    hash = "sha256-ZkAtOjkLULHSf0IgmrR0y61dYLYo4GPil981lX/Oe+k=";
    installPath = "https/repo1.maven.org/maven2/org/scalacheck/scalacheck_2.13/1.18.0";
  };

  "org.scalacheck_scalacheck_3-1.18.0" = fetchMaven {
    name = "org.scalacheck_scalacheck_3-1.18.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scalacheck/scalacheck_3/1.18.0/scalacheck_3-1.18.0.jar"
      "https://repo1.maven.org/maven2/org/scalacheck/scalacheck_3/1.18.0/scalacheck_3-1.18.0.pom"
    ];
    hash = "sha256-T4HLW91uTkm6TeG+vRlgI+Tjn7WRBEQzi9THYuSZ+lk=";
    installPath = "https/repo1.maven.org/maven2/org/scalacheck/scalacheck_3/1.18.0";
  };

  "org.scalactic_scalactic_2.13-3.2.19" = fetchMaven {
    name = "org.scalactic_scalactic_2.13-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalactic/scalactic_2.13/3.2.19/scalactic_2.13-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalactic/scalactic_2.13/3.2.19/scalactic_2.13-3.2.19.pom"
    ];
    hash = "sha256-qac/w1XSNFIJ3jVR0xFmX3cyaYqCQrXYHjt7U4lmzUY=";
    installPath = "https/repo1.maven.org/maven2/org/scalactic/scalactic_2.13/3.2.19";
  };

  "org.scalactic_scalactic_3-3.2.19" = fetchMaven {
    name = "org.scalactic_scalactic_3-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalactic/scalactic_3/3.2.19/scalactic_3-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalactic/scalactic_3/3.2.19/scalactic_3-3.2.19.pom"
    ];
    hash = "sha256-Jqhhu6THq0KRcOjgCCQTiDGaUujLx6W+qK15Tocv6+8=";
    installPath = "https/repo1.maven.org/maven2/org/scalactic/scalactic_3/3.2.19";
  };

  "org.scalameta_common_2.13-4.12.7" = fetchMaven {
    name = "org.scalameta_common_2.13-4.12.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scalameta/common_2.13/4.12.7/common_2.13-4.12.7.jar"
      "https://repo1.maven.org/maven2/org/scalameta/common_2.13/4.12.7/common_2.13-4.12.7.pom"
    ];
    hash = "sha256-+e6pD1W2h9+gSBhGgavku7HCzsHWGr9PMf/RdFV4GmQ=";
    installPath = "https/repo1.maven.org/maven2/org/scalameta/common_2.13/4.12.7";
  };

  "org.scalameta_mdoc-cli_2.13-2.6.4" = fetchMaven {
    name = "org.scalameta_mdoc-cli_2.13-2.6.4";
    urls = [
      "https://repo1.maven.org/maven2/org/scalameta/mdoc-cli_2.13/2.6.4/mdoc-cli_2.13-2.6.4.jar"
      "https://repo1.maven.org/maven2/org/scalameta/mdoc-cli_2.13/2.6.4/mdoc-cli_2.13-2.6.4.pom"
    ];
    hash = "sha256-iBMBu2sTky5re0qybptDcmDG1CPQnqv4kwaaA4psP9o=";
    installPath = "https/repo1.maven.org/maven2/org/scalameta/mdoc-cli_2.13/2.6.4";
  };

  "org.scalameta_mdoc-interfaces-2.6.4" = fetchMaven {
    name = "org.scalameta_mdoc-interfaces-2.6.4";
    urls = [
      "https://repo1.maven.org/maven2/org/scalameta/mdoc-interfaces/2.6.4/mdoc-interfaces-2.6.4.jar"
      "https://repo1.maven.org/maven2/org/scalameta/mdoc-interfaces/2.6.4/mdoc-interfaces-2.6.4.pom"
    ];
    hash = "sha256-p/JORzjFkMlUeLVcLL1nZ/OVgz5nNS2u/5JoKjUhBac=";
    installPath = "https/repo1.maven.org/maven2/org/scalameta/mdoc-interfaces/2.6.4";
  };

  "org.scalameta_mdoc-parser_2.13-2.6.4" = fetchMaven {
    name = "org.scalameta_mdoc-parser_2.13-2.6.4";
    urls = [
      "https://repo1.maven.org/maven2/org/scalameta/mdoc-parser_2.13/2.6.4/mdoc-parser_2.13-2.6.4.jar"
      "https://repo1.maven.org/maven2/org/scalameta/mdoc-parser_2.13/2.6.4/mdoc-parser_2.13-2.6.4.pom"
    ];
    hash = "sha256-2AogYtUo9lB8iMw2hhOkU/rIo7ukaoI+eFtbzhNBng0=";
    installPath = "https/repo1.maven.org/maven2/org/scalameta/mdoc-parser_2.13/2.6.4";
  };

  "org.scalameta_mdoc-runtime_2.13-2.6.4" = fetchMaven {
    name = "org.scalameta_mdoc-runtime_2.13-2.6.4";
    urls = [
      "https://repo1.maven.org/maven2/org/scalameta/mdoc-runtime_2.13/2.6.4/mdoc-runtime_2.13-2.6.4.jar"
      "https://repo1.maven.org/maven2/org/scalameta/mdoc-runtime_2.13/2.6.4/mdoc-runtime_2.13-2.6.4.pom"
    ];
    hash = "sha256-9ZX7uSghIWcST8q4Bj2A3TTKFBnILxazqe5OCRXCtOY=";
    installPath = "https/repo1.maven.org/maven2/org/scalameta/mdoc-runtime_2.13/2.6.4";
  };

  "org.scalameta_mdoc_2.13-2.6.4" = fetchMaven {
    name = "org.scalameta_mdoc_2.13-2.6.4";
    urls = [
      "https://repo1.maven.org/maven2/org/scalameta/mdoc_2.13/2.6.4/mdoc_2.13-2.6.4.jar"
      "https://repo1.maven.org/maven2/org/scalameta/mdoc_2.13/2.6.4/mdoc_2.13-2.6.4.pom"
    ];
    hash = "sha256-Hagsa/Wq+TSNKuuIM1Kq3TP/1d9Zz5ybck1ed8nmcUk=";
    installPath = "https/repo1.maven.org/maven2/org/scalameta/mdoc_2.13/2.6.4";
  };

  "org.scalameta_metaconfig-core_2.13-0.15.0" = fetchMaven {
    name = "org.scalameta_metaconfig-core_2.13-0.15.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scalameta/metaconfig-core_2.13/0.15.0/metaconfig-core_2.13-0.15.0.jar"
      "https://repo1.maven.org/maven2/org/scalameta/metaconfig-core_2.13/0.15.0/metaconfig-core_2.13-0.15.0.pom"
    ];
    hash = "sha256-C9LVQs7XsErYles9JWlsd5lN2x6PUVOIyfJ3d5itA8Q=";
    installPath = "https/repo1.maven.org/maven2/org/scalameta/metaconfig-core_2.13/0.15.0";
  };

  "org.scalameta_metaconfig-pprint_2.13-0.15.0" = fetchMaven {
    name = "org.scalameta_metaconfig-pprint_2.13-0.15.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scalameta/metaconfig-pprint_2.13/0.15.0/metaconfig-pprint_2.13-0.15.0.jar"
      "https://repo1.maven.org/maven2/org/scalameta/metaconfig-pprint_2.13/0.15.0/metaconfig-pprint_2.13-0.15.0.pom"
    ];
    hash = "sha256-8jCnP93T+3bbDbiFYaapWBit4MaZw7yV/nFO0jPD9yg=";
    installPath = "https/repo1.maven.org/maven2/org/scalameta/metaconfig-pprint_2.13/0.15.0";
  };

  "org.scalameta_metaconfig-typesafe-config_2.13-0.15.0" = fetchMaven {
    name = "org.scalameta_metaconfig-typesafe-config_2.13-0.15.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scalameta/metaconfig-typesafe-config_2.13/0.15.0/metaconfig-typesafe-config_2.13-0.15.0.jar"
      "https://repo1.maven.org/maven2/org/scalameta/metaconfig-typesafe-config_2.13/0.15.0/metaconfig-typesafe-config_2.13-0.15.0.pom"
    ];
    hash = "sha256-HZS7T/0/BlGTfMfgwV9oAST9n/LP3HDeYc2HgycGVxo=";
    installPath = "https/repo1.maven.org/maven2/org/scalameta/metaconfig-typesafe-config_2.13/0.15.0";
  };

  "org.scalameta_parsers_2.13-4.12.7" = fetchMaven {
    name = "org.scalameta_parsers_2.13-4.12.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scalameta/parsers_2.13/4.12.7/parsers_2.13-4.12.7.jar"
      "https://repo1.maven.org/maven2/org/scalameta/parsers_2.13/4.12.7/parsers_2.13-4.12.7.pom"
    ];
    hash = "sha256-PFOa9O/AO7Z52d+WAXFh8pST5NTEdfJ9qE32ljujwEw=";
    installPath = "https/repo1.maven.org/maven2/org/scalameta/parsers_2.13/4.12.7";
  };

  "org.scalameta_scalameta_2.13-4.12.7" = fetchMaven {
    name = "org.scalameta_scalameta_2.13-4.12.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scalameta/scalameta_2.13/4.12.7/scalameta_2.13-4.12.7.jar"
      "https://repo1.maven.org/maven2/org/scalameta/scalameta_2.13/4.12.7/scalameta_2.13-4.12.7.pom"
    ];
    hash = "sha256-SMpXEeY8r1y9auwATTuN0W12rkZSGmBos9b1ng6naNY=";
    installPath = "https/repo1.maven.org/maven2/org/scalameta/scalameta_2.13/4.12.7";
  };

  "org.scalameta_trees_2.13-4.12.7" = fetchMaven {
    name = "org.scalameta_trees_2.13-4.12.7";
    urls = [
      "https://repo1.maven.org/maven2/org/scalameta/trees_2.13/4.12.7/trees_2.13-4.12.7.jar"
      "https://repo1.maven.org/maven2/org/scalameta/trees_2.13/4.12.7/trees_2.13-4.12.7.pom"
    ];
    hash = "sha256-HmYP+OWuKwIXnuaDw0Yq1XEq+oo9IfxcJnc6EQGpZhQ=";
    installPath = "https/repo1.maven.org/maven2/org/scalameta/trees_2.13/4.12.7";
  };

  "org.scalatest_scalatest-compatible-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-compatible-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-compatible/3.2.19/scalatest-compatible-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-compatible/3.2.19/scalatest-compatible-3.2.19.pom"
    ];
    hash = "sha256-u8EPlJzg0p/4ysBFoSEN9GC6qlacv1f5vQoShjWXZHc=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-compatible/3.2.19";
  };

  "org.scalatest_scalatest-core_2.13-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-core_2.13-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-core_2.13/3.2.19/scalatest-core_2.13-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-core_2.13/3.2.19/scalatest-core_2.13-3.2.19.pom"
    ];
    hash = "sha256-m4W3GpDNdw6Mycpf18sb0HrRf/6d6+SIqthBYV/8fbA=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-core_2.13/3.2.19";
  };

  "org.scalatest_scalatest-core_3-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-core_3-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-core_3/3.2.19/scalatest-core_3-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-core_3/3.2.19/scalatest-core_3-3.2.19.pom"
    ];
    hash = "sha256-hWwLH3Ax2wtIPtrR4HVtELM5+MZ8V5I8xce8ywUOMe0=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-core_3/3.2.19";
  };

  "org.scalatest_scalatest-diagrams_2.13-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-diagrams_2.13-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-diagrams_2.13/3.2.19/scalatest-diagrams_2.13-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-diagrams_2.13/3.2.19/scalatest-diagrams_2.13-3.2.19.pom"
    ];
    hash = "sha256-v0VWhShh7OFf3Ef43Aqwc9uOpSvQbpuu9XXjSp33k3c=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-diagrams_2.13/3.2.19";
  };

  "org.scalatest_scalatest-diagrams_3-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-diagrams_3-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-diagrams_3/3.2.19/scalatest-diagrams_3-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-diagrams_3/3.2.19/scalatest-diagrams_3-3.2.19.pom"
    ];
    hash = "sha256-ZP7MZptMf4KS/MKSOOfs+IWLd5DE5FrG/h8P3N56mFY=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-diagrams_3/3.2.19";
  };

  "org.scalatest_scalatest-featurespec_2.13-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-featurespec_2.13-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-featurespec_2.13/3.2.19/scalatest-featurespec_2.13-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-featurespec_2.13/3.2.19/scalatest-featurespec_2.13-3.2.19.pom"
    ];
    hash = "sha256-cFTf4dhKLKJ3ZgoTyfFVq5th1ZQr5ZyGrtVEMaqpSvU=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-featurespec_2.13/3.2.19";
  };

  "org.scalatest_scalatest-featurespec_3-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-featurespec_3-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-featurespec_3/3.2.19/scalatest-featurespec_3-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-featurespec_3/3.2.19/scalatest-featurespec_3-3.2.19.pom"
    ];
    hash = "sha256-n9z5Uf0dnUgr5vnY2MqO1thAF5s5MWw9WY5ER2dnUig=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-featurespec_3/3.2.19";
  };

  "org.scalatest_scalatest-flatspec_2.13-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-flatspec_2.13-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-flatspec_2.13/3.2.19/scalatest-flatspec_2.13-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-flatspec_2.13/3.2.19/scalatest-flatspec_2.13-3.2.19.pom"
    ];
    hash = "sha256-5AjN1bMUpI6fEdx9C1I88i3YM0OtNpP507wUeL0W7TQ=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-flatspec_2.13/3.2.19";
  };

  "org.scalatest_scalatest-flatspec_3-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-flatspec_3-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-flatspec_3/3.2.19/scalatest-flatspec_3-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-flatspec_3/3.2.19/scalatest-flatspec_3-3.2.19.pom"
    ];
    hash = "sha256-3ctmJs7UIAYVCe5TpeECYW77tGmdssgRUk7l6Minr5w=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-flatspec_3/3.2.19";
  };

  "org.scalatest_scalatest-freespec_2.13-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-freespec_2.13-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-freespec_2.13/3.2.19/scalatest-freespec_2.13-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-freespec_2.13/3.2.19/scalatest-freespec_2.13-3.2.19.pom"
    ];
    hash = "sha256-7j1qHb8rHakvE1ETE9iuYu4P1zWuXPr5blUfTt0dzaE=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-freespec_2.13/3.2.19";
  };

  "org.scalatest_scalatest-freespec_3-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-freespec_3-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-freespec_3/3.2.19/scalatest-freespec_3-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-freespec_3/3.2.19/scalatest-freespec_3-3.2.19.pom"
    ];
    hash = "sha256-+OrXSBoOl764ZWyFcrZwbia9DnmLy3Kf2rl8uEvZtK4=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-freespec_3/3.2.19";
  };

  "org.scalatest_scalatest-funspec_2.13-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-funspec_2.13-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-funspec_2.13/3.2.19/scalatest-funspec_2.13-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-funspec_2.13/3.2.19/scalatest-funspec_2.13-3.2.19.pom"
    ];
    hash = "sha256-lWSulAZ067lOVTDuBXAo9x9f5MyWsw6jySLo0BOj17o=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-funspec_2.13/3.2.19";
  };

  "org.scalatest_scalatest-funspec_3-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-funspec_3-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-funspec_3/3.2.19/scalatest-funspec_3-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-funspec_3/3.2.19/scalatest-funspec_3-3.2.19.pom"
    ];
    hash = "sha256-hSAtMZn+y4DXQQRiZ52axHnTAsAphdupbFgg4fKtlCc=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-funspec_3/3.2.19";
  };

  "org.scalatest_scalatest-funsuite_2.13-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-funsuite_2.13-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-funsuite_2.13/3.2.19/scalatest-funsuite_2.13-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-funsuite_2.13/3.2.19/scalatest-funsuite_2.13-3.2.19.pom"
    ];
    hash = "sha256-oalqD91ZejCnNRNNucPovRCeweTqkTD5xQnM5ayl354=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-funsuite_2.13/3.2.19";
  };

  "org.scalatest_scalatest-funsuite_3-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-funsuite_3-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-funsuite_3/3.2.19/scalatest-funsuite_3-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-funsuite_3/3.2.19/scalatest-funsuite_3-3.2.19.pom"
    ];
    hash = "sha256-yL3iRTPXdIQFtEuDZkZEu+dRD4+aDyfoS5qe0jFcCKg=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-funsuite_3/3.2.19";
  };

  "org.scalatest_scalatest-matchers-core_2.13-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-matchers-core_2.13-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-matchers-core_2.13/3.2.19/scalatest-matchers-core_2.13-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-matchers-core_2.13/3.2.19/scalatest-matchers-core_2.13-3.2.19.pom"
    ];
    hash = "sha256-FLUXDbItfwWmKJvRuSA4ge8jl2b78ZRY068fe0M9oFM=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-matchers-core_2.13/3.2.19";
  };

  "org.scalatest_scalatest-matchers-core_3-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-matchers-core_3-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-matchers-core_3/3.2.19/scalatest-matchers-core_3-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-matchers-core_3/3.2.19/scalatest-matchers-core_3-3.2.19.pom"
    ];
    hash = "sha256-bYuUz5fT4OHJlM5VJojJoCERCNBxYMQAFeYI08zIP3Q=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-matchers-core_3/3.2.19";
  };

  "org.scalatest_scalatest-mustmatchers_2.13-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-mustmatchers_2.13-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-mustmatchers_2.13/3.2.19/scalatest-mustmatchers_2.13-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-mustmatchers_2.13/3.2.19/scalatest-mustmatchers_2.13-3.2.19.pom"
    ];
    hash = "sha256-b2BgcFJtCWlI07llJDrY7oX8XrWkORc3zflvugUhdxk=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-mustmatchers_2.13/3.2.19";
  };

  "org.scalatest_scalatest-mustmatchers_3-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-mustmatchers_3-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-mustmatchers_3/3.2.19/scalatest-mustmatchers_3-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-mustmatchers_3/3.2.19/scalatest-mustmatchers_3-3.2.19.pom"
    ];
    hash = "sha256-OTt9o2fMMC0DBTk3V73CmCaBDKKNVOGYl/UZbcCQgx8=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-mustmatchers_3/3.2.19";
  };

  "org.scalatest_scalatest-propspec_2.13-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-propspec_2.13-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-propspec_2.13/3.2.19/scalatest-propspec_2.13-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-propspec_2.13/3.2.19/scalatest-propspec_2.13-3.2.19.pom"
    ];
    hash = "sha256-TWd3bVVJ/KeK1ZHR5CJRtBup7wjGkWQhnXxKlZZSSOI=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-propspec_2.13/3.2.19";
  };

  "org.scalatest_scalatest-propspec_3-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-propspec_3-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-propspec_3/3.2.19/scalatest-propspec_3-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-propspec_3/3.2.19/scalatest-propspec_3-3.2.19.pom"
    ];
    hash = "sha256-njtKYeDP7bey+YUm0DzQeQD0H6QxPNVbhNG676B4Klc=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-propspec_3/3.2.19";
  };

  "org.scalatest_scalatest-refspec_2.13-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-refspec_2.13-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-refspec_2.13/3.2.19/scalatest-refspec_2.13-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-refspec_2.13/3.2.19/scalatest-refspec_2.13-3.2.19.pom"
    ];
    hash = "sha256-T0zYHq2z7fQR9XES70Oj1jxGnbrpegI+5fAUunkfTqM=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-refspec_2.13/3.2.19";
  };

  "org.scalatest_scalatest-refspec_3-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-refspec_3-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-refspec_3/3.2.19/scalatest-refspec_3-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-refspec_3/3.2.19/scalatest-refspec_3-3.2.19.pom"
    ];
    hash = "sha256-fdoTTaa71TzwEZ1T20y3Vc55riNOcsOjyA4DIEe3RIA=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-refspec_3/3.2.19";
  };

  "org.scalatest_scalatest-shouldmatchers_2.13-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-shouldmatchers_2.13-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-shouldmatchers_2.13/3.2.19/scalatest-shouldmatchers_2.13-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-shouldmatchers_2.13/3.2.19/scalatest-shouldmatchers_2.13-3.2.19.pom"
    ];
    hash = "sha256-kLbWJx6QCEm6PyXJ3RLzHTSckQGTdybv1ShaLHWDyZc=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-shouldmatchers_2.13/3.2.19";
  };

  "org.scalatest_scalatest-shouldmatchers_3-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-shouldmatchers_3-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-shouldmatchers_3/3.2.19/scalatest-shouldmatchers_3-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-shouldmatchers_3/3.2.19/scalatest-shouldmatchers_3-3.2.19.pom"
    ];
    hash = "sha256-+QcipBjJRhpMJ7PtT0TvFOlRI/Qkes5gYrt7JXNYyRQ=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-shouldmatchers_3/3.2.19";
  };

  "org.scalatest_scalatest-wordspec_2.13-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-wordspec_2.13-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-wordspec_2.13/3.2.19/scalatest-wordspec_2.13-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-wordspec_2.13/3.2.19/scalatest-wordspec_2.13-3.2.19.pom"
    ];
    hash = "sha256-Wpji+6EMpjq+1+EXZPtgzEKVhwJeQMQGxF9HOvzAr9A=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-wordspec_2.13/3.2.19";
  };

  "org.scalatest_scalatest-wordspec_3-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest-wordspec_3-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-wordspec_3/3.2.19/scalatest-wordspec_3-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest-wordspec_3/3.2.19/scalatest-wordspec_3-3.2.19.pom"
    ];
    hash = "sha256-wR707dnpvBMMHsnecyAVChN3PeJzUnH4VLeLZqgUn/A=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest-wordspec_3/3.2.19";
  };

  "org.scalatest_scalatest_2.13-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest_2.13-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest_2.13/3.2.19/scalatest_2.13-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest_2.13/3.2.19/scalatest_2.13-3.2.19.pom"
    ];
    hash = "sha256-5KO8Mdk4Jo2ZkH87x21+ykvtSsxl602WSWXtfAoN/hM=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest_2.13/3.2.19";
  };

  "org.scalatest_scalatest_3-3.2.19" = fetchMaven {
    name = "org.scalatest_scalatest_3-3.2.19";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatest/scalatest_3/3.2.19/scalatest_3-3.2.19.jar"
      "https://repo1.maven.org/maven2/org/scalatest/scalatest_3/3.2.19/scalatest_3-3.2.19.pom"
    ];
    hash = "sha256-0pdgKuDpBMcv61IS75Jd7Kh2Hf4eu1Y1uhLB9dUED3c=";
    installPath = "https/repo1.maven.org/maven2/org/scalatest/scalatest_3/3.2.19";
  };

  "org.scalatestplus_scalacheck-1-18_2.13-3.2.19.0" = fetchMaven {
    name = "org.scalatestplus_scalacheck-1-18_2.13-3.2.19.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatestplus/scalacheck-1-18_2.13/3.2.19.0/scalacheck-1-18_2.13-3.2.19.0.jar"
      "https://repo1.maven.org/maven2/org/scalatestplus/scalacheck-1-18_2.13/3.2.19.0/scalacheck-1-18_2.13-3.2.19.0.pom"
    ];
    hash = "sha256-PSYMGrma7wLPs4MzMlLl7KbpFshRB+06ngwNHCbaqDk=";
    installPath = "https/repo1.maven.org/maven2/org/scalatestplus/scalacheck-1-18_2.13/3.2.19.0";
  };

  "org.scalatestplus_scalacheck-1-18_3-3.2.19.0" = fetchMaven {
    name = "org.scalatestplus_scalacheck-1-18_3-3.2.19.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scalatestplus/scalacheck-1-18_3/3.2.19.0/scalacheck-1-18_3-3.2.19.0.jar"
      "https://repo1.maven.org/maven2/org/scalatestplus/scalacheck-1-18_3/3.2.19.0/scalacheck-1-18_3-3.2.19.0.pom"
    ];
    hash = "sha256-sdEWYByZhBNnpanGWMvd3SqVbgWJAF5TosPpof1dXIM=";
    installPath = "https/repo1.maven.org/maven2/org/scalatestplus/scalacheck-1-18_3/3.2.19.0";
  };

  "org.scodec_scodec-bits_2.13-1.1.34" = fetchMaven {
    name = "org.scodec_scodec-bits_2.13-1.1.34";
    urls = [
      "https://repo1.maven.org/maven2/org/scodec/scodec-bits_2.13/1.1.34/scodec-bits_2.13-1.1.34.jar"
      "https://repo1.maven.org/maven2/org/scodec/scodec-bits_2.13/1.1.34/scodec-bits_2.13-1.1.34.pom"
    ];
    hash = "sha256-3X+e5iafWOBcF8GzKdfLcjEpo43xCVbH0nU3WyU89KQ=";
    installPath = "https/repo1.maven.org/maven2/org/scodec/scodec-bits_2.13/1.1.34";
  };

  "org.slf4j_slf4j-api-2.0.16" = fetchMaven {
    name = "org.slf4j_slf4j-api-2.0.16";
    urls = [
      "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar"
      "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.pom"
    ];
    hash = "sha256-DTTfHW73wo7guf3qls7YCCtxLOW+nQfBtC8iGlngIhg=";
    installPath = "https/repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.16";
  };

  "org.slf4j_slf4j-bom-2.0.16" = fetchMaven {
    name = "org.slf4j_slf4j-bom-2.0.16";
    urls = [ "https://repo1.maven.org/maven2/org/slf4j/slf4j-bom/2.0.16/slf4j-bom-2.0.16.pom" ];
    hash = "sha256-57CmnZTTjeAyWOFnpSVmPT8waKxeOQosvvyHZDdDHg0=";
    installPath = "https/repo1.maven.org/maven2/org/slf4j/slf4j-bom/2.0.16";
  };

  "org.slf4j_slf4j-parent-2.0.16" = fetchMaven {
    name = "org.slf4j_slf4j-parent-2.0.16";
    urls = [ "https://repo1.maven.org/maven2/org/slf4j/slf4j-parent/2.0.16/slf4j-parent-2.0.16.pom" ];
    hash = "sha256-PHcUu7tbYLAd/qhe6V7TCF1I5d4Fs+nQqjM3wwYYEUc=";
    installPath = "https/repo1.maven.org/maven2/org/slf4j/slf4j-parent/2.0.16";
  };

  "org.springframework_spring-framework-bom-5.3.39" = fetchMaven {
    name = "org.springframework_spring-framework-bom-5.3.39";
    urls = [
      "https://repo1.maven.org/maven2/org/springframework/spring-framework-bom/5.3.39/spring-framework-bom-5.3.39.pom"
    ];
    hash = "sha256-V+sR9AvokPz2NrvEFCxdLHl3jrW2o9dP3gisCDAUUDA=";
    installPath = "https/repo1.maven.org/maven2/org/springframework/spring-framework-bom/5.3.39";
  };

  "org.typelevel_case-insensitive_2.13-1.2.0" = fetchMaven {
    name = "org.typelevel_case-insensitive_2.13-1.2.0";
    urls = [
      "https://repo1.maven.org/maven2/org/typelevel/case-insensitive_2.13/1.2.0/case-insensitive_2.13-1.2.0.jar"
      "https://repo1.maven.org/maven2/org/typelevel/case-insensitive_2.13/1.2.0/case-insensitive_2.13-1.2.0.pom"
    ];
    hash = "sha256-yMGkbTe0eXV9iaxMzehjaToTXs2yutb4HIOkLa5+3JY=";
    installPath = "https/repo1.maven.org/maven2/org/typelevel/case-insensitive_2.13/1.2.0";
  };

  "org.typelevel_cats-core_2.13-2.8.0" = fetchMaven {
    name = "org.typelevel_cats-core_2.13-2.8.0";
    urls = [
      "https://repo1.maven.org/maven2/org/typelevel/cats-core_2.13/2.8.0/cats-core_2.13-2.8.0.jar"
      "https://repo1.maven.org/maven2/org/typelevel/cats-core_2.13/2.8.0/cats-core_2.13-2.8.0.pom"
    ];
    hash = "sha256-7mf6iYRNTplOCWAXI8dvLEj5aEmyfik70VZOPMo1tMg=";
    installPath = "https/repo1.maven.org/maven2/org/typelevel/cats-core_2.13/2.8.0";
  };

  "org.typelevel_cats-effect-kernel_2.13-3.3.12" = fetchMaven {
    name = "org.typelevel_cats-effect-kernel_2.13-3.3.12";
    urls = [
      "https://repo1.maven.org/maven2/org/typelevel/cats-effect-kernel_2.13/3.3.12/cats-effect-kernel_2.13-3.3.12.jar"
      "https://repo1.maven.org/maven2/org/typelevel/cats-effect-kernel_2.13/3.3.12/cats-effect-kernel_2.13-3.3.12.pom"
    ];
    hash = "sha256-8WrFmDzMHB9CNYiYPMMJ7JK8q3CtSu0YFKJRHQjBtoc=";
    installPath = "https/repo1.maven.org/maven2/org/typelevel/cats-effect-kernel_2.13/3.3.12";
  };

  "org.typelevel_cats-effect-std_2.13-3.3.12" = fetchMaven {
    name = "org.typelevel_cats-effect-std_2.13-3.3.12";
    urls = [
      "https://repo1.maven.org/maven2/org/typelevel/cats-effect-std_2.13/3.3.12/cats-effect-std_2.13-3.3.12.jar"
      "https://repo1.maven.org/maven2/org/typelevel/cats-effect-std_2.13/3.3.12/cats-effect-std_2.13-3.3.12.pom"
    ];
    hash = "sha256-1d+Wsnu8nG384qJ9szL65K9y3bvLMDLjV+LJGpXihIE=";
    installPath = "https/repo1.maven.org/maven2/org/typelevel/cats-effect-std_2.13/3.3.12";
  };

  "org.typelevel_cats-effect_2.13-3.3.12" = fetchMaven {
    name = "org.typelevel_cats-effect_2.13-3.3.12";
    urls = [
      "https://repo1.maven.org/maven2/org/typelevel/cats-effect_2.13/3.3.12/cats-effect_2.13-3.3.12.jar"
      "https://repo1.maven.org/maven2/org/typelevel/cats-effect_2.13/3.3.12/cats-effect_2.13-3.3.12.pom"
    ];
    hash = "sha256-6RHBeEAPjD+DkFTedcrjLl6JPwGsrFZaDbsJflcsvG0=";
    installPath = "https/repo1.maven.org/maven2/org/typelevel/cats-effect_2.13/3.3.12";
  };

  "org.typelevel_cats-kernel_2.13-2.8.0" = fetchMaven {
    name = "org.typelevel_cats-kernel_2.13-2.8.0";
    urls = [
      "https://repo1.maven.org/maven2/org/typelevel/cats-kernel_2.13/2.8.0/cats-kernel_2.13-2.8.0.jar"
      "https://repo1.maven.org/maven2/org/typelevel/cats-kernel_2.13/2.8.0/cats-kernel_2.13-2.8.0.pom"
    ];
    hash = "sha256-s6L0OArPjCAClIWdjTgRepGjR+kJXhlqHClMbNi3yUs=";
    installPath = "https/repo1.maven.org/maven2/org/typelevel/cats-kernel_2.13/2.8.0";
  };

  "org.typelevel_cats-parse_2.13-0.3.7" = fetchMaven {
    name = "org.typelevel_cats-parse_2.13-0.3.7";
    urls = [
      "https://repo1.maven.org/maven2/org/typelevel/cats-parse_2.13/0.3.7/cats-parse_2.13-0.3.7.jar"
      "https://repo1.maven.org/maven2/org/typelevel/cats-parse_2.13/0.3.7/cats-parse_2.13-0.3.7.pom"
    ];
    hash = "sha256-hbj1QBrKWxNXLyP9DIwZzZWLdN+dsaUaoCppCB++3pU=";
    installPath = "https/repo1.maven.org/maven2/org/typelevel/cats-parse_2.13/0.3.7";
  };

  "org.typelevel_jawn-fs2_2.13-2.2.0" = fetchMaven {
    name = "org.typelevel_jawn-fs2_2.13-2.2.0";
    urls = [
      "https://repo1.maven.org/maven2/org/typelevel/jawn-fs2_2.13/2.2.0/jawn-fs2_2.13-2.2.0.jar"
      "https://repo1.maven.org/maven2/org/typelevel/jawn-fs2_2.13/2.2.0/jawn-fs2_2.13-2.2.0.pom"
    ];
    hash = "sha256-H+apj9M2+00QOP0Ndeu+6+yjnaHI9qJu/LpnWXJEIV0=";
    installPath = "https/repo1.maven.org/maven2/org/typelevel/jawn-fs2_2.13/2.2.0";
  };

  "org.typelevel_jawn-parser_2.13-1.3.2" = fetchMaven {
    name = "org.typelevel_jawn-parser_2.13-1.3.2";
    urls = [
      "https://repo1.maven.org/maven2/org/typelevel/jawn-parser_2.13/1.3.2/jawn-parser_2.13-1.3.2.jar"
      "https://repo1.maven.org/maven2/org/typelevel/jawn-parser_2.13/1.3.2/jawn-parser_2.13-1.3.2.pom"
    ];
    hash = "sha256-V7QImVUSIy+g9pwh1MsoAiXoiDh8uOklymKObpi0s24=";
    installPath = "https/repo1.maven.org/maven2/org/typelevel/jawn-parser_2.13/1.3.2";
  };

  "org.typelevel_literally_2.13-1.0.2" = fetchMaven {
    name = "org.typelevel_literally_2.13-1.0.2";
    urls = [
      "https://repo1.maven.org/maven2/org/typelevel/literally_2.13/1.0.2/literally_2.13-1.0.2.jar"
      "https://repo1.maven.org/maven2/org/typelevel/literally_2.13/1.0.2/literally_2.13-1.0.2.pom"
    ];
    hash = "sha256-dLwxcP1SsV/sqMDeZcjDG7Mt3TKRT4Ca3A92Jvlk+F4=";
    installPath = "https/repo1.maven.org/maven2/org/typelevel/literally_2.13/1.0.2";
  };

  "org.typelevel_paiges-core_2.13-0.4.4" = fetchMaven {
    name = "org.typelevel_paiges-core_2.13-0.4.4";
    urls = [
      "https://repo1.maven.org/maven2/org/typelevel/paiges-core_2.13/0.4.4/paiges-core_2.13-0.4.4.jar"
      "https://repo1.maven.org/maven2/org/typelevel/paiges-core_2.13/0.4.4/paiges-core_2.13-0.4.4.pom"
    ];
    hash = "sha256-jDLknbLWlHezQZNFHFhHJEebufEOKEwK3ZyMIatxvoY=";
    installPath = "https/repo1.maven.org/maven2/org/typelevel/paiges-core_2.13/0.4.4";
  };

  "org.typelevel_vault_2.13-3.2.1" = fetchMaven {
    name = "org.typelevel_vault_2.13-3.2.1";
    urls = [
      "https://repo1.maven.org/maven2/org/typelevel/vault_2.13/3.2.1/vault_2.13-3.2.1.jar"
      "https://repo1.maven.org/maven2/org/typelevel/vault_2.13/3.2.1/vault_2.13-3.2.1.pom"
    ];
    hash = "sha256-d7XOOXbt2hVTAooWkqxhJ0qY/jzXIiQ3PdoZANPNu8s=";
    installPath = "https/repo1.maven.org/maven2/org/typelevel/vault_2.13/3.2.1";
  };

  "org.yaml_snakeyaml-2.0" = fetchMaven {
    name = "org.yaml_snakeyaml-2.0";
    urls = [
      "https://repo1.maven.org/maven2/org/yaml/snakeyaml/2.0/snakeyaml-2.0.jar"
      "https://repo1.maven.org/maven2/org/yaml/snakeyaml/2.0/snakeyaml-2.0.pom"
    ];
    hash = "sha256-4/5l8lMWWNxqv1JGr0n8QtEo0KGAUGULj7lmdy9TODI=";
    installPath = "https/repo1.maven.org/maven2/org/yaml/snakeyaml/2.0";
  };

  "com.fasterxml.jackson_jackson-base-2.12.1" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-base-2.12.1";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-base/2.12.1/jackson-base-2.12.1.pom"
    ];
    hash = "sha256-QdwEWejSbiS//t8L9WxLqUxc0QQMY90a7ckBf6YzS2M=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-base/2.12.1";
  };

  "com.fasterxml.jackson_jackson-base-2.15.1" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-base-2.15.1";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-base/2.15.1/jackson-base-2.15.1.pom"
    ];
    hash = "sha256-DEG+wnRgBDaKE+g5oWHRRWcpgUH3rSj+eex3MKkiDYA=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-base/2.15.1";
  };

  "com.fasterxml.jackson_jackson-bom-2.12.1" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-bom-2.12.1";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.12.1/jackson-bom-2.12.1.pom"
    ];
    hash = "sha256-IVTSEkQzRB352EzD1i+FXx8n+HSzPMD5TGq4Ez0VTzc=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.12.1";
  };

  "com.fasterxml.jackson_jackson-bom-2.15.1" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-bom-2.15.1";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.15.1/jackson-bom-2.15.1.pom"
    ];
    hash = "sha256-xTY1hTkw6E3dYAMDZnockm2fm43WPMcIRt0k2oxO2O8=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.15.1";
  };

  "com.fasterxml.jackson_jackson-bom-2.17.2" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-bom-2.17.2";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.17.2/jackson-bom-2.17.2.pom"
    ];
    hash = "sha256-uAhCPZKxSJE8I5PhUlyXZOF9QVS/Xh+BQiYGmUYA86E=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.17.2";
  };

  "com.fasterxml.jackson_jackson-parent-2.12" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-parent-2.12";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.12/jackson-parent-2.12.pom"
    ];
    hash = "sha256-1XZX837v+3OgmuIWerAxNmHU3KA9W6GDs10dtM+w11o=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.12";
  };

  "com.fasterxml.jackson_jackson-parent-2.15" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-parent-2.15";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.15/jackson-parent-2.15.pom"
    ];
    hash = "sha256-Rybw8nineMf0Xjlc5GhV4ayVQMYocW1rCXiNhgdXiXc=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.15";
  };

  "com.fasterxml.jackson_jackson-parent-2.17" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-parent-2.17";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.17/jackson-parent-2.17.pom"
    ];
    hash = "sha256-bwpdlIPUrYpG6AmpG+vbSgz7gRpEaUy7i1k2ZxRlYGc=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.17";
  };

  "com.github.scopt_scopt_2.13-4.1.0" = fetchMaven {
    name = "com.github.scopt_scopt_2.13-4.1.0";
    urls = [
      "https://repo1.maven.org/maven2/com/github/scopt/scopt_2.13/4.1.0/scopt_2.13-4.1.0.jar"
      "https://repo1.maven.org/maven2/com/github/scopt/scopt_2.13/4.1.0/scopt_2.13-4.1.0.pom"
    ];
    hash = "sha256-8vlB7LBM6HNfmGOrsljlfCJ0SbMMpqR2Kmo9QWAKzJ8=";
    installPath = "https/repo1.maven.org/maven2/com/github/scopt/scopt_2.13/4.1.0";
  };

  "com.github.scopt_scopt_3-4.1.0" = fetchMaven {
    name = "com.github.scopt_scopt_3-4.1.0";
    urls = [
      "https://repo1.maven.org/maven2/com/github/scopt/scopt_3/4.1.0/scopt_3-4.1.0.jar"
      "https://repo1.maven.org/maven2/com/github/scopt/scopt_3/4.1.0/scopt_3-4.1.0.pom"
    ];
    hash = "sha256-Ivb94CUxeQR5SBz5sk2xXWRiRhbOlF3xrcB5y0trKG4=";
    installPath = "https/repo1.maven.org/maven2/com/github/scopt/scopt_3/4.1.0";
  };

  "com.googlecode.java-diff-utils_diffutils-1.3.0" = fetchMaven {
    name = "com.googlecode.java-diff-utils_diffutils-1.3.0";
    urls = [
      "https://repo1.maven.org/maven2/com/googlecode/java-diff-utils/diffutils/1.3.0/diffutils-1.3.0.jar"
      "https://repo1.maven.org/maven2/com/googlecode/java-diff-utils/diffutils/1.3.0/diffutils-1.3.0.pom"
    ];
    hash = "sha256-kazx/KomS1zOIS6BYlZXlYk5HzaJ47XlreWkjSKRXDg=";
    installPath = "https/repo1.maven.org/maven2/com/googlecode/java-diff-utils/diffutils/1.3.0";
  };

  "com.ibm.icu_icu4j-72.1" = fetchMaven {
    name = "com.ibm.icu_icu4j-72.1";
    urls = [
      "https://repo1.maven.org/maven2/com/ibm/icu/icu4j/72.1/icu4j-72.1.jar"
      "https://repo1.maven.org/maven2/com/ibm/icu/icu4j/72.1/icu4j-72.1.pom"
    ];
    hash = "sha256-3p8gpca1T8Q+zF/vIsgWBK5IpGsOH7roKSM1DCT+tBE=";
    installPath = "https/repo1.maven.org/maven2/com/ibm/icu/icu4j/72.1";
  };

  "com.thoughtworks.paranamer_paranamer-2.8" = fetchMaven {
    name = "com.thoughtworks.paranamer_paranamer-2.8";
    urls = [
      "https://repo1.maven.org/maven2/com/thoughtworks/paranamer/paranamer/2.8/paranamer-2.8.jar"
      "https://repo1.maven.org/maven2/com/thoughtworks/paranamer/paranamer/2.8/paranamer-2.8.pom"
    ];
    hash = "sha256-ehB753YLCaI3R/EkbYk+Ev08o90TnNoC+uiJDOcm3Q8=";
    installPath = "https/repo1.maven.org/maven2/com/thoughtworks/paranamer/paranamer/2.8";
  };

  "com.thoughtworks.paranamer_paranamer-parent-2.8" = fetchMaven {
    name = "com.thoughtworks.paranamer_paranamer-parent-2.8";
    urls = [
      "https://repo1.maven.org/maven2/com/thoughtworks/paranamer/paranamer-parent/2.8/paranamer-parent-2.8.pom"
    ];
    hash = "sha256-+LBfeaWVmAiGP63PaqEd/M5Ks3RLQQyFanUgMAy8mkU=";
    installPath = "https/repo1.maven.org/maven2/com/thoughtworks/paranamer/paranamer-parent/2.8";
  };

  "com.vladsch.flexmark_flexmark-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark/0.62.2/flexmark-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark/0.62.2/flexmark-0.62.2.pom"
    ];
    hash = "sha256-CMbMcOs3cMmCu7+sAh6qiwj63tMDlJ6qIrZRbHF2gDE=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark/0.64.8/flexmark-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark/0.64.8/flexmark-0.64.8.pom"
    ];
    hash = "sha256-92WRsOx/dAeEv+eFiXs5TOpndP2K3YiswytHaSbqXT4=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-all-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-all-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-all/0.64.8/flexmark-all-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-all/0.64.8/flexmark-all-0.64.8.pom"
    ];
    hash = "sha256-1zmirrJkV0JFCffmiEGFNOsMNBfdUb9f6OaNuwHj144=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-all/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-abbreviation-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-abbreviation-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-abbreviation/0.64.8/flexmark-ext-abbreviation-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-abbreviation/0.64.8/flexmark-ext-abbreviation-0.64.8.pom"
    ];
    hash = "sha256-AlgqgNuOPqUuX9zMyChz+RM3u8e9rXyskWMushENhOk=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-abbreviation/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-admonition-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-admonition-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-admonition/0.64.8/flexmark-ext-admonition-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-admonition/0.64.8/flexmark-ext-admonition-0.64.8.pom"
    ];
    hash = "sha256-SsQiTKo7EgJlX+agBipijxyJCYnG3o+GXQ78zpIkO0U=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-admonition/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-anchorlink-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-anchorlink-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-anchorlink/0.62.2/flexmark-ext-anchorlink-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-anchorlink/0.62.2/flexmark-ext-anchorlink-0.62.2.pom"
    ];
    hash = "sha256-weHNR6k/69NjAg2Vs72ce1wOZ1rwBicv4TMLDS9jnGE=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-anchorlink/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-anchorlink-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-anchorlink-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-anchorlink/0.64.8/flexmark-ext-anchorlink-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-anchorlink/0.64.8/flexmark-ext-anchorlink-0.64.8.pom"
    ];
    hash = "sha256-WPH5S3+E6Hq9f6P0Hg88TFQC77fjVYj4LrTfJ7u1O6g=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-anchorlink/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-aside-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-aside-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-aside/0.64.8/flexmark-ext-aside-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-aside/0.64.8/flexmark-ext-aside-0.64.8.pom"
    ];
    hash = "sha256-u8MVmPOkCjjBXXVaU06bOjNYgP1bHsoibMoRQtIW3lw=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-aside/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-attributes-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-attributes-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-attributes/0.64.8/flexmark-ext-attributes-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-attributes/0.64.8/flexmark-ext-attributes-0.64.8.pom"
    ];
    hash = "sha256-OOYFacg8Osm3t2Nvwp6ev1iFtAIplHWigPnmiS+UxVc=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-attributes/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-autolink-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-autolink-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-autolink/0.62.2/flexmark-ext-autolink-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-autolink/0.62.2/flexmark-ext-autolink-0.62.2.pom"
    ];
    hash = "sha256-15OH05RylvbLSzEu47GBdhtKZvyP3ibjXETb+3Sn5+Y=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-autolink/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-autolink-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-autolink-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-autolink/0.64.8/flexmark-ext-autolink-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-autolink/0.64.8/flexmark-ext-autolink-0.64.8.pom"
    ];
    hash = "sha256-jmm081mvpaM4gq6hEmaJsKB+jXHp24Ld6G4+4pU/Iek=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-autolink/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-definition-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-definition-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-definition/0.64.8/flexmark-ext-definition-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-definition/0.64.8/flexmark-ext-definition-0.64.8.pom"
    ];
    hash = "sha256-95HZNsLWJTnpV/+O+qyPZGSAiB/My6NGa+Etc6zDXdk=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-definition/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-emoji-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-emoji-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-emoji/0.62.2/flexmark-ext-emoji-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-emoji/0.62.2/flexmark-ext-emoji-0.62.2.pom"
    ];
    hash = "sha256-UHbh+WMLnLqFzhE9GIdc3pwFEBy94rNpWT6olRGnIvI=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-emoji/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-emoji-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-emoji-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-emoji/0.64.8/flexmark-ext-emoji-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-emoji/0.64.8/flexmark-ext-emoji-0.64.8.pom"
    ];
    hash = "sha256-mvlxVlLTcOD8jMO0XjFDd5H/JrrEQK38ohEG5tgWAOo=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-emoji/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-enumerated-reference-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-enumerated-reference-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-enumerated-reference/0.64.8/flexmark-ext-enumerated-reference-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-enumerated-reference/0.64.8/flexmark-ext-enumerated-reference-0.64.8.pom"
    ];
    hash = "sha256-WR/iUP1E5IvbmUDxD7R23MrMg0K38hEvbhvw7tp9znI=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-enumerated-reference/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-escaped-character-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-escaped-character-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-escaped-character/0.64.8/flexmark-ext-escaped-character-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-escaped-character/0.64.8/flexmark-ext-escaped-character-0.64.8.pom"
    ];
    hash = "sha256-rhZ4W3hSz+rvR/qV8whRBDS2GOM86JKZszbc0z5/U/A=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-escaped-character/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-footnotes-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-footnotes-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-footnotes/0.64.8/flexmark-ext-footnotes-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-footnotes/0.64.8/flexmark-ext-footnotes-0.64.8.pom"
    ];
    hash = "sha256-tSxgeVxXDbAEcUvT8TT504gQrLSGuEJGr31C85YZTQ0=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-footnotes/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-gfm-issues-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-gfm-issues-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-issues/0.64.8/flexmark-ext-gfm-issues-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-issues/0.64.8/flexmark-ext-gfm-issues-0.64.8.pom"
    ];
    hash = "sha256-8wb9gYvA93Tif964YCBARvJ8nRVyTd49JFvoOmQYnLc=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-issues/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-gfm-strikethrough-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-gfm-strikethrough-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-strikethrough/0.62.2/flexmark-ext-gfm-strikethrough-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-strikethrough/0.62.2/flexmark-ext-gfm-strikethrough-0.62.2.pom"
    ];
    hash = "sha256-1l/E13+s+Pc/CVD28MVSrqRUkkrfwKD6K0+2zvCQX8o=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-strikethrough/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-gfm-strikethrough-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-gfm-strikethrough-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-strikethrough/0.64.8/flexmark-ext-gfm-strikethrough-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-strikethrough/0.64.8/flexmark-ext-gfm-strikethrough-0.64.8.pom"
    ];
    hash = "sha256-zNDfvFkH8sbZHjRDmtTwWklkSxBO7K7/Sa1K/wxclZY=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-strikethrough/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-gfm-tasklist-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-gfm-tasklist-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-tasklist/0.62.2/flexmark-ext-gfm-tasklist-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-tasklist/0.62.2/flexmark-ext-gfm-tasklist-0.62.2.pom"
    ];
    hash = "sha256-gtACK+9qTISC22QYuWoyvgNeTXmuSOZxXuojXESKAvE=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-tasklist/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-gfm-tasklist-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-gfm-tasklist-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-tasklist/0.64.8/flexmark-ext-gfm-tasklist-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-tasklist/0.64.8/flexmark-ext-gfm-tasklist-0.64.8.pom"
    ];
    hash = "sha256-67B3bimRIZMTml19nYEFCY+0gMQGMJsbUakTysESq7c=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-tasklist/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-gfm-users-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-gfm-users-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-users/0.64.8/flexmark-ext-gfm-users-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-users/0.64.8/flexmark-ext-gfm-users-0.64.8.pom"
    ];
    hash = "sha256-5HxajGHbwijC9jMK9olXZwIODSDOXPEo6lmGPFqE14g=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-users/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-gitlab-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-gitlab-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gitlab/0.64.8/flexmark-ext-gitlab-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gitlab/0.64.8/flexmark-ext-gitlab-0.64.8.pom"
    ];
    hash = "sha256-nv1ZSJxPZyexZ8QG2HK/d+enHeFAZv/EEGg2D6uCcmQ=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gitlab/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-ins-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-ins-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-ins/0.62.2/flexmark-ext-ins-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-ins/0.62.2/flexmark-ext-ins-0.62.2.pom"
    ];
    hash = "sha256-VIKNuMXAxAbmNWnk2nWPgpSzbkoGfpA6miKQuvOUmF4=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-ins/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-ins-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-ins-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-ins/0.64.8/flexmark-ext-ins-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-ins/0.64.8/flexmark-ext-ins-0.64.8.pom"
    ];
    hash = "sha256-J31G3JnuDiQmp/K0/E7xbgauwtVJNHWYmjuIneH9J9Q=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-ins/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-jekyll-front-matter-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-jekyll-front-matter-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-jekyll-front-matter/0.64.8/flexmark-ext-jekyll-front-matter-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-jekyll-front-matter/0.64.8/flexmark-ext-jekyll-front-matter-0.64.8.pom"
    ];
    hash = "sha256-/I0CVRsqoqMpXe2DzhpbEnI15W2tgXVfNArS8P7n7zI=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-jekyll-front-matter/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-jekyll-tag-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-jekyll-tag-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-jekyll-tag/0.64.8/flexmark-ext-jekyll-tag-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-jekyll-tag/0.64.8/flexmark-ext-jekyll-tag-0.64.8.pom"
    ];
    hash = "sha256-ZjKn2HPnEcYcFu2WlHhYL/VfLTfSpblK6mGGA2Oi3dw=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-jekyll-tag/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-macros-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-macros-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-macros/0.64.8/flexmark-ext-macros-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-macros/0.64.8/flexmark-ext-macros-0.64.8.pom"
    ];
    hash = "sha256-2z4B5Cda+wwnTvBUVAiD5oIp/iyQa+e4Kr7/xhZkmaY=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-macros/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-media-tags-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-media-tags-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-media-tags/0.64.8/flexmark-ext-media-tags-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-media-tags/0.64.8/flexmark-ext-media-tags-0.64.8.pom"
    ];
    hash = "sha256-+NOkiJ8LT2kg0v2ndJrQsFgRFiFi0Ldiq6ibUc1Fn/8=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-media-tags/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-resizable-image-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-resizable-image-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-resizable-image/0.64.8/flexmark-ext-resizable-image-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-resizable-image/0.64.8/flexmark-ext-resizable-image-0.64.8.pom"
    ];
    hash = "sha256-AfK2ExZ0gD7dk5XC68byUFpf53qL9+ZRDIMVGr3NbUQ=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-resizable-image/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-superscript-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-superscript-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-superscript/0.62.2/flexmark-ext-superscript-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-superscript/0.62.2/flexmark-ext-superscript-0.62.2.pom"
    ];
    hash = "sha256-pfRu434uIlDIkwSEaFwxZFwcUjTnU5cbuSfsG578PC4=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-superscript/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-superscript-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-superscript-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-superscript/0.64.8/flexmark-ext-superscript-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-superscript/0.64.8/flexmark-ext-superscript-0.64.8.pom"
    ];
    hash = "sha256-uOc872jXRxOHS2gdv40fgau9JNG88YjVB+TzT2g5WUc=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-superscript/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-tables-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-tables-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-tables/0.62.2/flexmark-ext-tables-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-tables/0.62.2/flexmark-ext-tables-0.62.2.pom"
    ];
    hash = "sha256-3Fef3ZHc6jjwTHjvOGsVvLAMbRMwJHlZ5X7SKIaCj6w=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-tables/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-tables-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-tables-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-tables/0.64.8/flexmark-ext-tables-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-tables/0.64.8/flexmark-ext-tables-0.64.8.pom"
    ];
    hash = "sha256-4dbI3CvpiM4YzNKRN3vNgmR4mWKUTfJ+xk3G5+w1t1A=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-tables/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-toc-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-toc-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-toc/0.64.8/flexmark-ext-toc-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-toc/0.64.8/flexmark-ext-toc-0.64.8.pom"
    ];
    hash = "sha256-BF0aQe+S00NVwNsw57jywGSpelGpSZp6GPu3FgimwmY=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-toc/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-typographic-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-typographic-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-typographic/0.64.8/flexmark-ext-typographic-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-typographic/0.64.8/flexmark-ext-typographic-0.64.8.pom"
    ];
    hash = "sha256-xvZoylw/O55Frg0RuD+cAJ8ubrOF9xPTuOInu0Sq1bg=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-typographic/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-wikilink-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-wikilink-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-wikilink/0.62.2/flexmark-ext-wikilink-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-wikilink/0.62.2/flexmark-ext-wikilink-0.62.2.pom"
    ];
    hash = "sha256-NQtfUT4F3p6+nGk6o07EwlX1kZvkXarCfWw07QQgYyE=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-wikilink/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-wikilink-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-wikilink-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-wikilink/0.64.8/flexmark-ext-wikilink-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-wikilink/0.64.8/flexmark-ext-wikilink-0.64.8.pom"
    ];
    hash = "sha256-AmdM6/UcOQnj+6J7BofGMU0bT4Gensge6Cd5Mv9WRtQ=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-wikilink/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-xwiki-macros-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-xwiki-macros-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-xwiki-macros/0.64.8/flexmark-ext-xwiki-macros-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-xwiki-macros/0.64.8/flexmark-ext-xwiki-macros-0.64.8.pom"
    ];
    hash = "sha256-ilFcAdy5Os3KLt6a2kL8ELZMJYyTSFAlEZETEHTrOLE=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-xwiki-macros/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-yaml-front-matter-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-yaml-front-matter-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-yaml-front-matter/0.62.2/flexmark-ext-yaml-front-matter-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-yaml-front-matter/0.62.2/flexmark-ext-yaml-front-matter-0.62.2.pom"
    ];
    hash = "sha256-tc0KpVAhnflMmVlFUXFqwocYsXuL3PiXeFtdO+p9Ta4=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-yaml-front-matter/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-yaml-front-matter-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-yaml-front-matter-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-yaml-front-matter/0.64.8/flexmark-ext-yaml-front-matter-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-yaml-front-matter/0.64.8/flexmark-ext-yaml-front-matter-0.64.8.pom"
    ];
    hash = "sha256-V6UI6lpxcApE3qu0m4rB6U897uBfmoG6objZ5OFDdhk=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-yaml-front-matter/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-ext-youtube-embedded-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-youtube-embedded-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-youtube-embedded/0.64.8/flexmark-ext-youtube-embedded-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-youtube-embedded/0.64.8/flexmark-ext-youtube-embedded-0.64.8.pom"
    ];
    hash = "sha256-sIhCO2F41Z2TCN/JZ4ZiOAB8yp0Bgq5JKapNZTuh++o=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-youtube-embedded/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-html2md-converter-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-html2md-converter-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-html2md-converter/0.64.8/flexmark-html2md-converter-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-html2md-converter/0.64.8/flexmark-html2md-converter-0.64.8.pom"
    ];
    hash = "sha256-q6uV+sG+hXlvXarWO4mhBZJNqK/o90viLhg0sGdmZ5A=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-html2md-converter/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-java-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-java-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-java/0.62.2/flexmark-java-0.62.2.pom"
    ];
    hash = "sha256-DlxcWCry0vUFs1L54guu8FLGgpuYD9+ksL2x5sv6E9c=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-java/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-java-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-java-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-java/0.64.8/flexmark-java-0.64.8.pom"
    ];
    hash = "sha256-9t8wYGad4ScGv1NTAfdPCRBQmzGHKfBWCEe7u20zf5o=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-java/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-jira-converter-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-jira-converter-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-jira-converter/0.62.2/flexmark-jira-converter-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-jira-converter/0.62.2/flexmark-jira-converter-0.62.2.pom"
    ];
    hash = "sha256-k4eeiCIqq4fE5F0MPS9FMDEdlWEb+Gd36pDNxQSFMFY=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-jira-converter/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-jira-converter-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-jira-converter-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-jira-converter/0.64.8/flexmark-jira-converter-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-jira-converter/0.64.8/flexmark-jira-converter-0.64.8.pom"
    ];
    hash = "sha256-Rh+v0qIN10o4eO/87wBajGSm8Vqjrfks3Zm0F7yDUW4=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-jira-converter/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-pdf-converter-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-pdf-converter-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-pdf-converter/0.64.8/flexmark-pdf-converter-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-pdf-converter/0.64.8/flexmark-pdf-converter-0.64.8.pom"
    ];
    hash = "sha256-LYhyIU3XPcpwKYI0HmUzsc/vPV78xq43KyXjnYIgpIk=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-pdf-converter/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-profile-pegdown-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-profile-pegdown-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-profile-pegdown/0.64.8/flexmark-profile-pegdown-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-profile-pegdown/0.64.8/flexmark-profile-pegdown-0.64.8.pom"
    ];
    hash = "sha256-rf4JAwATbuU70MTXxnewoebOqnLwe009svUnuN00GWY=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-profile-pegdown/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-util-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util/0.62.2/flexmark-util-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util/0.62.2/flexmark-util-0.62.2.pom"
    ];
    hash = "sha256-A3coPMDIx8qFH4WcoKFEcAY6MDeICS9olH/SPgIEbeI=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util/0.64.8/flexmark-util-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util/0.64.8/flexmark-util-0.64.8.pom"
    ];
    hash = "sha256-6FXXKHH/0JhQt8nWJne0pYsDxldZz6Ro2KXoQGNnjEg=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-util-ast-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-ast-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-ast/0.62.2/flexmark-util-ast-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-ast/0.62.2/flexmark-util-ast-0.62.2.pom"
    ];
    hash = "sha256-bT7Cqm3k63wFdcC63M3WAtz5p0QqArmmCvpfPGuvDjw=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-ast/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-ast-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-ast-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-ast/0.64.8/flexmark-util-ast-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-ast/0.64.8/flexmark-util-ast-0.64.8.pom"
    ];
    hash = "sha256-wmS6AiF8kOYu/C/0LVSNtTPAFtp1v6F7yE0tqNLpOgs=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-ast/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-util-builder-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-builder-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-builder/0.62.2/flexmark-util-builder-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-builder/0.62.2/flexmark-util-builder-0.62.2.pom"
    ];
    hash = "sha256-+kjX932WxGRANJw+UPDyy8MJB6wKUXI7tf+PyOAYbJM=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-builder/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-builder-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-builder-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-builder/0.64.8/flexmark-util-builder-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-builder/0.64.8/flexmark-util-builder-0.64.8.pom"
    ];
    hash = "sha256-kPW7NjRgtQbDS/b21kCX1D4RkhUZCXp9GOgUJJMkfKQ=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-builder/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-util-collection-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-collection-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-collection/0.62.2/flexmark-util-collection-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-collection/0.62.2/flexmark-util-collection-0.62.2.pom"
    ];
    hash = "sha256-vsdaPDU/TcTKnim4MAWhcXp4P0upYTWIMLMSCeg6Wx4=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-collection/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-collection-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-collection-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-collection/0.64.8/flexmark-util-collection-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-collection/0.64.8/flexmark-util-collection-0.64.8.pom"
    ];
    hash = "sha256-6CLn1LMnS3NiOPKvXKTP5i2cqBNvK+G/WdOZLKUreXE=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-collection/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-util-data-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-data-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-data/0.62.2/flexmark-util-data-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-data/0.62.2/flexmark-util-data-0.62.2.pom"
    ];
    hash = "sha256-m3S05kD1HNXWdGXPwXapNqzLv4g2WicpuaNUJjvZDW4=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-data/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-data-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-data-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-data/0.64.8/flexmark-util-data-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-data/0.64.8/flexmark-util-data-0.64.8.pom"
    ];
    hash = "sha256-zjv3PUBdz0/uVhh+M0MnRyPEIjRCnNCcgjCnsDjB9Lk=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-data/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-util-dependency-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-dependency-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-dependency/0.62.2/flexmark-util-dependency-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-dependency/0.62.2/flexmark-util-dependency-0.62.2.pom"
    ];
    hash = "sha256-nSFsXZXFD67UbxMv6hAZEjv6VfCmewH9PsP6zk7vLR4=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-dependency/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-dependency-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-dependency-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-dependency/0.64.8/flexmark-util-dependency-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-dependency/0.64.8/flexmark-util-dependency-0.64.8.pom"
    ];
    hash = "sha256-QBPR6rl4wVjXgxMG+cQwKzeuBu3rMK29YGtLuhXLjEk=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-dependency/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-util-format-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-format-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-format/0.62.2/flexmark-util-format-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-format/0.62.2/flexmark-util-format-0.62.2.pom"
    ];
    hash = "sha256-j7GbAIjjp00wTPbuXCTO//af5J5JooOPmHh2Da3jBd0=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-format/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-format-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-format-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-format/0.64.8/flexmark-util-format-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-format/0.64.8/flexmark-util-format-0.64.8.pom"
    ];
    hash = "sha256-NJ6eaKI162fMED4aE6XY16sRn05Pa/bpvlm006Hh+w8=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-format/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-util-html-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-html-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-html/0.62.2/flexmark-util-html-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-html/0.62.2/flexmark-util-html-0.62.2.pom"
    ];
    hash = "sha256-9MSBM5awDcqrCDRtRKKCrxD35X5DYf+U7NmUR8OOW94=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-html/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-html-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-html-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-html/0.64.8/flexmark-util-html-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-html/0.64.8/flexmark-util-html-0.64.8.pom"
    ];
    hash = "sha256-ZyHVY1zlxf/enFsHwu6plMpg4Udoj6uWUao1mUkS5Bk=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-html/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-util-misc-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-misc-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-misc/0.62.2/flexmark-util-misc-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-misc/0.62.2/flexmark-util-misc-0.62.2.pom"
    ];
    hash = "sha256-VfG2y0OgXWkcDF0VNHFTnOsf1jjmZtSZThZABQ0yc5A=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-misc/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-misc-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-misc-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-misc/0.64.8/flexmark-util-misc-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-misc/0.64.8/flexmark-util-misc-0.64.8.pom"
    ];
    hash = "sha256-bnfhnTTht1CpZRYjn+GPfvDf+dg4fFFlF4WWteG3A3k=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-misc/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-util-options-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-options-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-options/0.62.2/flexmark-util-options-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-options/0.62.2/flexmark-util-options-0.62.2.pom"
    ];
    hash = "sha256-Px6MK19ozVJLQGj3fCpDhMTUtrWLzhiqdDDRdBpf8i8=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-options/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-options-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-options-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-options/0.64.8/flexmark-util-options-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-options/0.64.8/flexmark-util-options-0.64.8.pom"
    ];
    hash = "sha256-h5rTaSPmzDzGgmJt6oO4mmL/HFsnjL23r03fuqzIkHk=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-options/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-util-sequence-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-sequence-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-sequence/0.62.2/flexmark-util-sequence-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-sequence/0.62.2/flexmark-util-sequence-0.62.2.pom"
    ];
    hash = "sha256-J8ZXFheFBaMP+b9VMZ02j5Sonvtf26k6DR7C5AspxVg=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-sequence/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-sequence-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-sequence-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-sequence/0.64.8/flexmark-util-sequence-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-sequence/0.64.8/flexmark-util-sequence-0.64.8.pom"
    ];
    hash = "sha256-zPgr56DT8JdZ2hKOTIMA29n4BVJNhCsYlaWokKvSd6k=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-sequence/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-util-visitor-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-visitor-0.62.2";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-visitor/0.62.2/flexmark-util-visitor-0.62.2.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-visitor/0.62.2/flexmark-util-visitor-0.62.2.pom"
    ];
    hash = "sha256-sGUXA1qXnyVQTMPXJoAh4L1+L895QeeW7oazG3/NqyI=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-visitor/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-visitor-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-visitor-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-visitor/0.64.8/flexmark-util-visitor-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-visitor/0.64.8/flexmark-util-visitor-0.64.8.pom"
    ];
    hash = "sha256-ui7YSn3e66rvQQYL+sHI9JGAr1JaXpLSV9E3SgTC9Vg=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-visitor/0.64.8";
  };

  "com.vladsch.flexmark_flexmark-youtrack-converter-0.64.8" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-youtrack-converter-0.64.8";
    urls = [
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-youtrack-converter/0.64.8/flexmark-youtrack-converter-0.64.8.jar"
      "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-youtrack-converter/0.64.8/flexmark-youtrack-converter-0.64.8.pom"
    ];
    hash = "sha256-LN7CLPKCgZRDIbt6QG456qSZykEsGfAMPLuL+VtldbI=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-youtrack-converter/0.64.8";
  };

  "de.rototor.pdfbox_graphics2d-0.32" = fetchMaven {
    name = "de.rototor.pdfbox_graphics2d-0.32";
    urls = [
      "https://repo1.maven.org/maven2/de/rototor/pdfbox/graphics2d/0.32/graphics2d-0.32.jar"
      "https://repo1.maven.org/maven2/de/rototor/pdfbox/graphics2d/0.32/graphics2d-0.32.pom"
    ];
    hash = "sha256-FnyCH+Gp4EVeY1og2+nuUG/pNZ0FpJAqqNQxK1/OAOo=";
    installPath = "https/repo1.maven.org/maven2/de/rototor/pdfbox/graphics2d/0.32";
  };

  "de.rototor.pdfbox_pdfboxgraphics2d-parent-0.32" = fetchMaven {
    name = "de.rototor.pdfbox_pdfboxgraphics2d-parent-0.32";
    urls = [
      "https://repo1.maven.org/maven2/de/rototor/pdfbox/pdfboxgraphics2d-parent/0.32/pdfboxgraphics2d-parent-0.32.pom"
    ];
    hash = "sha256-T8C23/tDcJVUSICquql+croeT0rQuoWdi02l2XZFIDI=";
    installPath = "https/repo1.maven.org/maven2/de/rototor/pdfbox/pdfboxgraphics2d-parent/0.32";
  };

  "io.github.alexarchambault_data-class_2.13-0.2.7" = fetchMaven {
    name = "io.github.alexarchambault_data-class_2.13-0.2.7";
    urls = [
      "https://repo1.maven.org/maven2/io/github/alexarchambault/data-class_2.13/0.2.7/data-class_2.13-0.2.7.jar"
      "https://repo1.maven.org/maven2/io/github/alexarchambault/data-class_2.13/0.2.7/data-class_2.13-0.2.7.pom"
    ];
    hash = "sha256-PZ9by0bd2Rv4MWgWqJnmsVhQVLMaEOf7nmlfKB34JJs=";
    installPath = "https/repo1.maven.org/maven2/io/github/alexarchambault/data-class_2.13/0.2.7";
  };

  "io.github.java-diff-utils_java-diff-utils-4.12" = fetchMaven {
    name = "io.github.java-diff-utils_java-diff-utils-4.12";
    urls = [
      "https://repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils/4.12/java-diff-utils-4.12.jar"
      "https://repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils/4.12/java-diff-utils-4.12.pom"
    ];
    hash = "sha256-SMNRfv+BvfxjgwFH0fHU16fd1bDn/QMrPQN8Eyb6deA=";
    installPath = "https/repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils/4.12";
  };

  "io.github.java-diff-utils_java-diff-utils-4.15" = fetchMaven {
    name = "io.github.java-diff-utils_java-diff-utils-4.15";
    urls = [
      "https://repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils/4.15/java-diff-utils-4.15.jar"
      "https://repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils/4.15/java-diff-utils-4.15.pom"
    ];
    hash = "sha256-SfOhFqK/GsStfRZLQm3yGJat/CQWb3YbJnoXd84l/R0=";
    installPath = "https/repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils/4.15";
  };

  "io.github.java-diff-utils_java-diff-utils-parent-4.12" = fetchMaven {
    name = "io.github.java-diff-utils_java-diff-utils-parent-4.12";
    urls = [
      "https://repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils-parent/4.12/java-diff-utils-parent-4.12.pom"
    ];
    hash = "sha256-l9MekOAkDQrHpgMMLkbZQJtiaSmyE7h0XneiHciAFOI=";
    installPath = "https/repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils-parent/4.12";
  };

  "io.github.java-diff-utils_java-diff-utils-parent-4.15" = fetchMaven {
    name = "io.github.java-diff-utils_java-diff-utils-parent-4.15";
    urls = [
      "https://repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils-parent/4.15/java-diff-utils-parent-4.15.pom"
    ];
    hash = "sha256-7U+fEo0qYFash7diRi0E8Ejv0MY8T70NzU+HswbmO34=";
    installPath = "https/repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils-parent/4.15";
  };

  "net.sf.jopt-simple_jopt-simple-5.0.4" = fetchMaven {
    name = "net.sf.jopt-simple_jopt-simple-5.0.4";
    urls = [
      "https://repo1.maven.org/maven2/net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar"
      "https://repo1.maven.org/maven2/net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.pom"
    ];
    hash = "sha256-/l4TgZ+p8AP432PBNvhoHUw7VjAHgsP7PBKjYnqxFB0=";
    installPath = "https/repo1.maven.org/maven2/net/sf/jopt-simple/jopt-simple/5.0.4";
  };

  "org.apache.commons_commons-lang3-3.17.0" = fetchMaven {
    name = "org.apache.commons_commons-lang3-3.17.0";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.17.0/commons-lang3-3.17.0.jar"
      "https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.17.0/commons-lang3-3.17.0.pom"
    ];
    hash = "sha256-4R7rcq58WVs8WJr0JGIZkM43P3DmztsodHV7jK9v208=";
    installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.17.0";
  };

  "org.apache.commons_commons-math3-3.6.1" = fetchMaven {
    name = "org.apache.commons_commons-math3-3.6.1";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar"
      "https://repo1.maven.org/maven2/org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.pom"
    ];
    hash = "sha256-sqDIGsP9PknsQ5oCeL3PHftqLMZG5tWNBB6q0r6KPbc=";
    installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-math3/3.6.1";
  };

  "org.apache.commons_commons-parent-34" = fetchMaven {
    name = "org.apache.commons_commons-parent-34";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/commons/commons-parent/34/commons-parent-34.pom"
    ];
    hash = "sha256-4uhgIF+JAhewPTk4Ooi+2m732h2khAjtCDELWlCkCkc=";
    installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-parent/34";
  };

  "org.apache.commons_commons-parent-39" = fetchMaven {
    name = "org.apache.commons_commons-parent-39";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/commons/commons-parent/39/commons-parent-39.pom"
    ];
    hash = "sha256-13xay430GiGOMcS0VRxQl7mimf26s+wMLChdekIoyuY=";
    installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-parent/39";
  };

  "org.apache.commons_commons-parent-73" = fetchMaven {
    name = "org.apache.commons_commons-parent-73";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/commons/commons-parent/73/commons-parent-73.pom"
    ];
    hash = "sha256-obPRPljEVPSQkbOlYYnlGJZ6+GcuuofouNFpVPt/R4g=";
    installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-parent/73";
  };

  "org.apache.commons_commons-parent-78" = fetchMaven {
    name = "org.apache.commons_commons-parent-78";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/commons/commons-parent/78/commons-parent-78.pom"
    ];
    hash = "sha256-0aJAoMZMen5VZmg8WT/tz9MMHFaXx6DgdiAVpYrCsac=";
    installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-parent/78";
  };

  "org.apache.commons_commons-text-1.13.0" = fetchMaven {
    name = "org.apache.commons_commons-text-1.13.0";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/commons/commons-text/1.13.0/commons-text-1.13.0.jar"
      "https://repo1.maven.org/maven2/org/apache/commons/commons-text/1.13.0/commons-text-1.13.0.pom"
    ];
    hash = "sha256-bmb1fpgZoCG1UTUFOyUm+wonq12KTLYmp/P9GqPatOM=";
    installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-text/1.13.0";
  };

  "org.apache.groovy_groovy-bom-4.0.22" = fetchMaven {
    name = "org.apache.groovy_groovy-bom-4.0.22";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/groovy/groovy-bom/4.0.22/groovy-bom-4.0.22.pom"
    ];
    hash = "sha256-9hsejVx5kj/oQtf+JvuKqOuzRfJIJbPoys04ArDEu9o=";
    installPath = "https/repo1.maven.org/maven2/org/apache/groovy/groovy-bom/4.0.22";
  };

  "org.apache.logging_logging-parent-11.3.0" = fetchMaven {
    name = "org.apache.logging_logging-parent-11.3.0";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/logging/logging-parent/11.3.0/logging-parent-11.3.0.pom"
    ];
    hash = "sha256-06rPgZ5cRXf8cg84KMl7HVR3vcgvV0ThY76UsgAFf+w=";
    installPath = "https/repo1.maven.org/maven2/org/apache/logging/logging-parent/11.3.0";
  };

  "org.apache.pdfbox_fontbox-2.0.24" = fetchMaven {
    name = "org.apache.pdfbox_fontbox-2.0.24";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/pdfbox/fontbox/2.0.24/fontbox-2.0.24.jar"
      "https://repo1.maven.org/maven2/org/apache/pdfbox/fontbox/2.0.24/fontbox-2.0.24.pom"
    ];
    hash = "sha256-cuUQSLL9pFUKbLSmi56cJjBSp7AEOHq6LJOtbcAeNRs=";
    installPath = "https/repo1.maven.org/maven2/org/apache/pdfbox/fontbox/2.0.24";
  };

  "org.apache.pdfbox_pdfbox-2.0.24" = fetchMaven {
    name = "org.apache.pdfbox_pdfbox-2.0.24";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox/2.0.24/pdfbox-2.0.24.jar"
      "https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox/2.0.24/pdfbox-2.0.24.pom"
    ];
    hash = "sha256-guOQxczlj6ICcJbTOOFRCyAX/5TGjut4RIaLDLjKeEI=";
    installPath = "https/repo1.maven.org/maven2/org/apache/pdfbox/pdfbox/2.0.24";
  };

  "org.apache.pdfbox_pdfbox-parent-2.0.24" = fetchMaven {
    name = "org.apache.pdfbox_pdfbox-parent-2.0.24";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox-parent/2.0.24/pdfbox-parent-2.0.24.pom"
    ];
    hash = "sha256-/SfCH4zCIlX1+GFvfZx5F/4STtkBlbIxbvCcg5Ys+h0=";
    installPath = "https/repo1.maven.org/maven2/org/apache/pdfbox/pdfbox-parent/2.0.24";
  };

  "org.apache.pdfbox_xmpbox-2.0.24" = fetchMaven {
    name = "org.apache.pdfbox_xmpbox-2.0.24";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/pdfbox/xmpbox/2.0.24/xmpbox-2.0.24.jar"
      "https://repo1.maven.org/maven2/org/apache/pdfbox/xmpbox/2.0.24/xmpbox-2.0.24.pom"
    ];
    hash = "sha256-DzuKiRs1dVnFW9j8oL/MXQx1p2pCMfdqgylz+wSCwOQ=";
    installPath = "https/repo1.maven.org/maven2/org/apache/pdfbox/xmpbox/2.0.24";
  };

  "org.eclipse.ee4j_project-1.0.7" = fetchMaven {
    name = "org.eclipse.ee4j_project-1.0.7";
    urls = [ "https://repo1.maven.org/maven2/org/eclipse/ee4j/project/1.0.7/project-1.0.7.pom" ];
    hash = "sha256-1HxZiJ0aeo1n8AWjwGKEoPwVFP9kndMBye7xwgYEal8=";
    installPath = "https/repo1.maven.org/maven2/org/eclipse/ee4j/project/1.0.7";
  };

  "org.fusesource.jansi_jansi-2.4.1" = fetchMaven {
    name = "org.fusesource.jansi_jansi-2.4.1";
    urls = [
      "https://repo1.maven.org/maven2/org/fusesource/jansi/jansi/2.4.1/jansi-2.4.1.jar"
      "https://repo1.maven.org/maven2/org/fusesource/jansi/jansi/2.4.1/jansi-2.4.1.pom"
    ];
    hash = "sha256-M9G+H9TA5eB6NwlBmDP0ghxZzjbvLimPXNRZHyxJXac=";
    installPath = "https/repo1.maven.org/maven2/org/fusesource/jansi/jansi/2.4.1";
  };

  "org.jboss.logging_jboss-logging-3.4.1.Final" = fetchMaven {
    name = "org.jboss.logging_jboss-logging-3.4.1.Final";
    urls = [
      "https://repo1.maven.org/maven2/org/jboss/logging/jboss-logging/3.4.1.Final/jboss-logging-3.4.1.Final.pom"
    ];
    hash = "sha256-g+WbzY4Tia4lIUqhWxfJCNGaOzi0Pr95TmCChsoPZlg=";
    installPath = "https/repo1.maven.org/maven2/org/jboss/logging/jboss-logging/3.4.1.Final";
  };

  "org.jboss.logging_jboss-logging-3.4.3.Final" = fetchMaven {
    name = "org.jboss.logging_jboss-logging-3.4.3.Final";
    urls = [
      "https://repo1.maven.org/maven2/org/jboss/logging/jboss-logging/3.4.3.Final/jboss-logging-3.4.3.Final.jar"
      "https://repo1.maven.org/maven2/org/jboss/logging/jboss-logging/3.4.3.Final/jboss-logging-3.4.3.Final.pom"
    ];
    hash = "sha256-1ZB/2DFjnX2Em7N38ve7X9KhcKBNX3RFiUKI9nk4CII=";
    installPath = "https/repo1.maven.org/maven2/org/jboss/logging/jboss-logging/3.4.3.Final";
  };

  "org.jboss.threads_jboss-threads-3.1.0.Final" = fetchMaven {
    name = "org.jboss.threads_jboss-threads-3.1.0.Final";
    urls = [
      "https://repo1.maven.org/maven2/org/jboss/threads/jboss-threads/3.1.0.Final/jboss-threads-3.1.0.Final.jar"
      "https://repo1.maven.org/maven2/org/jboss/threads/jboss-threads/3.1.0.Final/jboss-threads-3.1.0.Final.pom"
    ];
    hash = "sha256-pv+dRK27px5papyth5tDQxKhP6KKsRzwrbEoVxiaE14=";
    installPath = "https/repo1.maven.org/maven2/org/jboss/threads/jboss-threads/3.1.0.Final";
  };

  "org.jboss.xnio_xnio-all-3.8.16.Final" = fetchMaven {
    name = "org.jboss.xnio_xnio-all-3.8.16.Final";
    urls = [
      "https://repo1.maven.org/maven2/org/jboss/xnio/xnio-all/3.8.16.Final/xnio-all-3.8.16.Final.pom"
    ];
    hash = "sha256-CMpwH7HkPFYVt1392rXjjRAytnLie8i1E5Bw8semC4g=";
    installPath = "https/repo1.maven.org/maven2/org/jboss/xnio/xnio-all/3.8.16.Final";
  };

  "org.jboss.xnio_xnio-api-3.8.16.Final" = fetchMaven {
    name = "org.jboss.xnio_xnio-api-3.8.16.Final";
    urls = [
      "https://repo1.maven.org/maven2/org/jboss/xnio/xnio-api/3.8.16.Final/xnio-api-3.8.16.Final.jar"
      "https://repo1.maven.org/maven2/org/jboss/xnio/xnio-api/3.8.16.Final/xnio-api-3.8.16.Final.pom"
    ];
    hash = "sha256-+N27/kGWXVgyic7f8wQGBrWW0caZKfFMnZjO2XiuVdw=";
    installPath = "https/repo1.maven.org/maven2/org/jboss/xnio/xnio-api/3.8.16.Final";
  };

  "org.jboss.xnio_xnio-nio-3.8.16.Final" = fetchMaven {
    name = "org.jboss.xnio_xnio-nio-3.8.16.Final";
    urls = [
      "https://repo1.maven.org/maven2/org/jboss/xnio/xnio-nio/3.8.16.Final/xnio-nio-3.8.16.Final.jar"
      "https://repo1.maven.org/maven2/org/jboss/xnio/xnio-nio/3.8.16.Final/xnio-nio-3.8.16.Final.pom"
    ];
    hash = "sha256-hdLkn9qpCHBPfaiTqT3fSlZnG9JpuyZzo5e42OkIpCk=";
    installPath = "https/repo1.maven.org/maven2/org/jboss/xnio/xnio-nio/3.8.16.Final";
  };

  "org.nibor.autolink_autolink-0.6.0" = fetchMaven {
    name = "org.nibor.autolink_autolink-0.6.0";
    urls = [
      "https://repo1.maven.org/maven2/org/nibor/autolink/autolink/0.6.0/autolink-0.6.0.jar"
      "https://repo1.maven.org/maven2/org/nibor/autolink/autolink/0.6.0/autolink-0.6.0.pom"
    ];
    hash = "sha256-UyOje39E9ysUXMK3ey2jrm7S6e8EVQboYC46t+B6sdo=";
    installPath = "https/repo1.maven.org/maven2/org/nibor/autolink/autolink/0.6.0";
  };

  "org.openjdk.jmh_jmh-core-1.37" = fetchMaven {
    name = "org.openjdk.jmh_jmh-core-1.37";
    urls = [
      "https://repo1.maven.org/maven2/org/openjdk/jmh/jmh-core/1.37/jmh-core-1.37.jar"
      "https://repo1.maven.org/maven2/org/openjdk/jmh/jmh-core/1.37/jmh-core-1.37.pom"
    ];
    hash = "sha256-XfjmLTPtwJteUowBApjmp45P8wlykncXi6hBD1lupeY=";
    installPath = "https/repo1.maven.org/maven2/org/openjdk/jmh/jmh-core/1.37";
  };

  "org.openjdk.jmh_jmh-parent-1.37" = fetchMaven {
    name = "org.openjdk.jmh_jmh-parent-1.37";
    urls = [ "https://repo1.maven.org/maven2/org/openjdk/jmh/jmh-parent/1.37/jmh-parent-1.37.pom" ];
    hash = "sha256-ooKN68/Sh/Ub5/ABMHW3czGNvMgoHDIWG9a3Lkhq9yo=";
    installPath = "https/repo1.maven.org/maven2/org/openjdk/jmh/jmh-parent/1.37";
  };

  "org.scala-lang.modules_scala-asm-9.6.0-scala-1" = fetchMaven {
    name = "org.scala-lang.modules_scala-asm-9.6.0-scala-1";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-asm/9.6.0-scala-1/scala-asm-9.6.0-scala-1.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-asm/9.6.0-scala-1/scala-asm-9.6.0-scala-1.pom"
    ];
    hash = "sha256-O85WTbOphRTQU4Q4jwUyuJ+umdkPoo9VCT+EmGlqxdc=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-asm/9.6.0-scala-1";
  };

  "org.scala-lang.modules_scala-collection-compat_2.13-2.11.0" = fetchMaven {
    name = "org.scala-lang.modules_scala-collection-compat_2.13-2.11.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.11.0/scala-collection-compat_2.13-2.11.0.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.11.0/scala-collection-compat_2.13-2.11.0.pom"
    ];
    hash = "sha256-++tF6j10SwWogS7WQJHMqj7uSzo7UVjub4dDQPdogPw=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.11.0";
  };

  "org.scala-lang.modules_scala-collection-compat_2.13-2.12.0" = fetchMaven {
    name = "org.scala-lang.modules_scala-collection-compat_2.13-2.12.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.12.0/scala-collection-compat_2.13-2.12.0.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.12.0/scala-collection-compat_2.13-2.12.0.pom"
    ];
    hash = "sha256-gdUFn7dadEj342MYKz1lj4dLYz+AkOzRiIC0spS8CXk=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.12.0";
  };

  "org.scala-lang.modules_scala-collection-compat_2.13-2.13.0" = fetchMaven {
    name = "org.scala-lang.modules_scala-collection-compat_2.13-2.13.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.13.0/scala-collection-compat_2.13-2.13.0.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.13.0/scala-collection-compat_2.13-2.13.0.pom"
    ];
    hash = "sha256-aQ+I3JuE8U5GIdb4SlHbZWdPu4E/qRIoZSGMMP3g5GE=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.13.0";
  };

  "org.scala-lang.modules_scala-parallel-collections_2.13-0.2.0" = fetchMaven {
    name = "org.scala-lang.modules_scala-parallel-collections_2.13-0.2.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-parallel-collections_2.13/0.2.0/scala-parallel-collections_2.13-0.2.0.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-parallel-collections_2.13/0.2.0/scala-parallel-collections_2.13-0.2.0.pom"
    ];
    hash = "sha256-chqRhtzyMJjeR4ohA5YhNjGV8kLHTy5yZjNCyYIO/wo=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-parallel-collections_2.13/0.2.0";
  };

  "org.scala-lang.modules_scala-parser-combinators_2.13-1.1.2" = fetchMaven {
    name = "org.scala-lang.modules_scala-parser-combinators_2.13-1.1.2";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-parser-combinators_2.13/1.1.2/scala-parser-combinators_2.13-1.1.2.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-parser-combinators_2.13/1.1.2/scala-parser-combinators_2.13-1.1.2.pom"
    ];
    hash = "sha256-sM5GWZ8/K1Jchj4V3FTvaWhfSJiHq0PKtQpd5W94Hps=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-parser-combinators_2.13/1.1.2";
  };

  "org.scala-lang.modules_scala-xml_2.13-2.1.0" = fetchMaven {
    name = "org.scala-lang.modules_scala-xml_2.13-2.1.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.1.0/scala-xml_2.13-2.1.0.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.1.0/scala-xml_2.13-2.1.0.pom"
    ];
    hash = "sha256-CYRcBgRmprVNrgpZ5OB8pJN00UwmQnc6vNftX4Z4EqE=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.1.0";
  };

  "org.scala-lang.modules_scala-xml_2.13-2.2.0" = fetchMaven {
    name = "org.scala-lang.modules_scala-xml_2.13-2.2.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.2.0/scala-xml_2.13-2.2.0.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.2.0/scala-xml_2.13-2.2.0.pom"
    ];
    hash = "sha256-Vy0piitgB2wPXiORd+dcBEZVcMZSjcbKJz4lNKZgeec=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.2.0";
  };

  "org.scala-lang.modules_scala-xml_2.13-2.3.0" = fetchMaven {
    name = "org.scala-lang.modules_scala-xml_2.13-2.3.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.3.0/scala-xml_2.13-2.3.0.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.3.0/scala-xml_2.13-2.3.0.pom"
    ];
    hash = "sha256-TZaDZ9UjQB20IMvxqxub63LbqSNDMAhFDRtYfvbzI58=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.3.0";
  };

  "org.scala-lang.modules_scala-xml_3-2.1.0" = fetchMaven {
    name = "org.scala-lang.modules_scala-xml_3-2.1.0";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_3/2.1.0/scala-xml_3-2.1.0.jar"
      "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_3/2.1.0/scala-xml_3-2.1.0.pom"
    ];
    hash = "sha256-0D7YYVRGQqauXGiT/3d1TAoDxTbccs7WqMGmXe1AeRo=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_3/2.1.0";
  };

  "org.scala-sbt.jline_jline-2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18" = fetchMaven {
    name = "org.scala-sbt.jline_jline-2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18";
    urls = [
      "https://repo1.maven.org/maven2/org/scala-sbt/jline/jline/2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18/jline-2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18.jar"
      "https://repo1.maven.org/maven2/org/scala-sbt/jline/jline/2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18/jline-2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18.pom"
    ];
    hash = "sha256-1Nq7/UMXSlaZ7iwR1WMryltAmS8/fRCK6u93cm+1uh4=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/jline/jline/2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18";
  };

  "org.sonatype.oss_oss-parent-7" = fetchMaven {
    name = "org.sonatype.oss_oss-parent-7";
    urls = [ "https://repo1.maven.org/maven2/org/sonatype/oss/oss-parent/7/oss-parent-7.pom" ];
    hash = "sha256-HDM4YUA2cNuWnhH7wHWZfxzLMdIr2AT36B3zuJFrXbE=";
    installPath = "https/repo1.maven.org/maven2/org/sonatype/oss/oss-parent/7";
  };

  "org.sonatype.oss_oss-parent-9" = fetchMaven {
    name = "org.sonatype.oss_oss-parent-9";
    urls = [ "https://repo1.maven.org/maven2/org/sonatype/oss/oss-parent/9/oss-parent-9.pom" ];
    hash = "sha256-kJ3QfnDTAvamYaHQowpAKW1gPDFDXbiP2lNPzNllIWY=";
    installPath = "https/repo1.maven.org/maven2/org/sonatype/oss/oss-parent/9";
  };

  "org.wildfly.client_wildfly-client-config-1.0.1.Final" = fetchMaven {
    name = "org.wildfly.client_wildfly-client-config-1.0.1.Final";
    urls = [
      "https://repo1.maven.org/maven2/org/wildfly/client/wildfly-client-config/1.0.1.Final/wildfly-client-config-1.0.1.Final.jar"
      "https://repo1.maven.org/maven2/org/wildfly/client/wildfly-client-config/1.0.1.Final/wildfly-client-config-1.0.1.Final.pom"
    ];
    hash = "sha256-FoAeD3KR3Mw+AzI3vhTDdAYRE8Svj8y2WsJNpdC7sSY=";
    installPath = "https/repo1.maven.org/maven2/org/wildfly/client/wildfly-client-config/1.0.1.Final";
  };

  "org.wildfly.common_wildfly-common-1.5.4.Final" = fetchMaven {
    name = "org.wildfly.common_wildfly-common-1.5.4.Final";
    urls = [
      "https://repo1.maven.org/maven2/org/wildfly/common/wildfly-common/1.5.4.Final/wildfly-common-1.5.4.Final.jar"
      "https://repo1.maven.org/maven2/org/wildfly/common/wildfly-common/1.5.4.Final/wildfly-common-1.5.4.Final.pom"
    ];
    hash = "sha256-K+wCKR+vIsp6Ju6SKc0+tdjmLd7Y8VmMFC6NELsy25w=";
    installPath = "https/repo1.maven.org/maven2/org/wildfly/common/wildfly-common/1.5.4.Final";
  };

  "ua.co.k_strftime4j-1.0.5" = fetchMaven {
    name = "ua.co.k_strftime4j-1.0.5";
    urls = [
      "https://repo1.maven.org/maven2/ua/co/k/strftime4j/1.0.5/strftime4j-1.0.5.jar"
      "https://repo1.maven.org/maven2/ua/co/k/strftime4j/1.0.5/strftime4j-1.0.5.pom"
    ];
    hash = "sha256-Wrg3ftbV/dCtAhULZcti/FJ2XVbpqd9fM4Z6A/fOwAo=";
    installPath = "https/repo1.maven.org/maven2/ua/co/k/strftime4j/1.0.5";
  };

  "com.fasterxml.jackson.core_jackson-annotations-2.12.1" = fetchMaven {
    name = "com.fasterxml.jackson.core_jackson-annotations-2.12.1";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.12.1/jackson-annotations-2.12.1.pom"
    ];
    hash = "sha256-anUbI5JS/lVsxPul1sdmtNFsJbiyHvyz9au/cBV0L6w=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.12.1";
  };

  "com.fasterxml.jackson.core_jackson-annotations-2.15.1" = fetchMaven {
    name = "com.fasterxml.jackson.core_jackson-annotations-2.15.1";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.15.1/jackson-annotations-2.15.1.jar"
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.15.1/jackson-annotations-2.15.1.pom"
    ];
    hash = "sha256-hwI7CChHkZif7MNeHDjPf6OuNcncc9i7zIET6JUiSY8=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.15.1";
  };

  "com.fasterxml.jackson.core_jackson-core-2.15.1" = fetchMaven {
    name = "com.fasterxml.jackson.core_jackson-core-2.15.1";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.15.1/jackson-core-2.15.1.jar"
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.15.1/jackson-core-2.15.1.pom"
    ];
    hash = "sha256-07N9Rg8OKF7hTLa+0AoF1hImT3acHpQBIJhHBnLUSOs=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.15.1";
  };

  "com.fasterxml.jackson.core_jackson-databind-2.15.1" = fetchMaven {
    name = "com.fasterxml.jackson.core_jackson-databind-2.15.1";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.15.1/jackson-databind-2.15.1.jar"
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.15.1/jackson-databind-2.15.1.pom"
    ];
    hash = "sha256-t8Sge4HbKg8XsqNgW69/3G3RKgK09MSCsPH7XYtsrew=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.15.1";
  };

  "com.fasterxml.jackson.dataformat_jackson-dataformat-yaml-2.15.1" = fetchMaven {
    name = "com.fasterxml.jackson.dataformat_jackson-dataformat-yaml-2.15.1";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/dataformat/jackson-dataformat-yaml/2.15.1/jackson-dataformat-yaml-2.15.1.jar"
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/dataformat/jackson-dataformat-yaml/2.15.1/jackson-dataformat-yaml-2.15.1.pom"
    ];
    hash = "sha256-gSpEZqpCXmFGg86xQeOlNRNdyBmiM/rN2kCTGhjhHt4=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/dataformat/jackson-dataformat-yaml/2.15.1";
  };

  "com.fasterxml.jackson.dataformat_jackson-dataformats-text-2.15.1" = fetchMaven {
    name = "com.fasterxml.jackson.dataformat_jackson-dataformats-text-2.15.1";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/dataformat/jackson-dataformats-text/2.15.1/jackson-dataformats-text-2.15.1.pom"
    ];
    hash = "sha256-1RiIP6cIRZoOMBV2+vmJJOYXMarqg+4l7XQ8S7OvAvg=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/dataformat/jackson-dataformats-text/2.15.1";
  };

  "com.fasterxml.jackson.datatype_jackson-datatype-jsr310-2.12.1" = fetchMaven {
    name = "com.fasterxml.jackson.datatype_jackson-datatype-jsr310-2.12.1";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/datatype/jackson-datatype-jsr310/2.12.1/jackson-datatype-jsr310-2.12.1.jar"
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/datatype/jackson-datatype-jsr310/2.12.1/jackson-datatype-jsr310-2.12.1.pom"
    ];
    hash = "sha256-YH7YMZY1aeamRA6aVvF2JG3C1YLZhvaMpVCegAfdhFU=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/datatype/jackson-datatype-jsr310/2.12.1";
  };

  "com.fasterxml.jackson.module_jackson-modules-java8-2.12.1" = fetchMaven {
    name = "com.fasterxml.jackson.module_jackson-modules-java8-2.12.1";
    urls = [
      "https://repo1.maven.org/maven2/com/fasterxml/jackson/module/jackson-modules-java8/2.12.1/jackson-modules-java8-2.12.1.pom"
    ];
    hash = "sha256-x5YmdPGcWOpCompDhApY6o5VZ+IUVHTbeday5HVW/NQ=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/module/jackson-modules-java8/2.12.1";
  };

  "com.github.plokhotnyuk.jsoniter-scala_jsoniter-scala-core_2.13-2.33.0" = fetchMaven {
    name = "com.github.plokhotnyuk.jsoniter-scala_jsoniter-scala-core_2.13-2.33.0";
    urls = [
      "https://repo1.maven.org/maven2/com/github/plokhotnyuk/jsoniter-scala/jsoniter-scala-core_2.13/2.33.0/jsoniter-scala-core_2.13-2.33.0.jar"
      "https://repo1.maven.org/maven2/com/github/plokhotnyuk/jsoniter-scala/jsoniter-scala-core_2.13/2.33.0/jsoniter-scala-core_2.13-2.33.0.pom"
    ];
    hash = "sha256-yP6gtxLXdHy8J14LUf//MA/QF7xiLZux3afeysXFGK8=";
    installPath = "https/repo1.maven.org/maven2/com/github/plokhotnyuk/jsoniter-scala/jsoniter-scala-core_2.13/2.33.0";
  };

  "com.github.plokhotnyuk.jsoniter-scala_jsoniter-scala-macros_2.13-2.33.0" = fetchMaven {
    name = "com.github.plokhotnyuk.jsoniter-scala_jsoniter-scala-macros_2.13-2.33.0";
    urls = [
      "https://repo1.maven.org/maven2/com/github/plokhotnyuk/jsoniter-scala/jsoniter-scala-macros_2.13/2.33.0/jsoniter-scala-macros_2.13-2.33.0.jar"
      "https://repo1.maven.org/maven2/com/github/plokhotnyuk/jsoniter-scala/jsoniter-scala-macros_2.13/2.33.0/jsoniter-scala-macros_2.13-2.33.0.pom"
    ];
    hash = "sha256-w4oCUuIq1z9nFmRgDobSUnZRU8gupzv5KulKBy7txMg=";
    installPath = "https/repo1.maven.org/maven2/com/github/plokhotnyuk/jsoniter-scala/jsoniter-scala-macros_2.13/2.33.0";
  };

  "net.java.dev.jna_jna-5.12.1" = fetchMaven {
    name = "net.java.dev.jna_jna-5.12.1";
    urls = [
      "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.12.1/jna-5.12.1.jar"
      "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.12.1/jna-5.12.1.pom"
    ];
    hash = "sha256-xyspXesCQvsXEo4NmmKY17wiNZM6cvMTOzaH1bVi/p4=";
    installPath = "https/repo1.maven.org/maven2/net/java/dev/jna/jna/5.12.1";
  };

  "net.java.dev.jna_jna-5.13.0" = fetchMaven {
    name = "net.java.dev.jna_jna-5.13.0";
    urls = [
      "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.13.0/jna-5.13.0.jar"
      "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.13.0/jna-5.13.0.pom"
    ];
    hash = "sha256-LP1W3fVxMEP6po1dlkAseu3pSeSnobemZJaxKivwqDs=";
    installPath = "https/repo1.maven.org/maven2/net/java/dev/jna/jna/5.13.0";
  };

  "net.java.dev.jna_jna-5.14.0" = fetchMaven {
    name = "net.java.dev.jna_jna-5.14.0";
    urls = [
      "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar"
      "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.14.0/jna-5.14.0.pom"
    ];
    hash = "sha256-mvzJykzd4Cz473vRi15E0NReFk7YN7hPOtS5ZHUhCIg=";
    installPath = "https/repo1.maven.org/maven2/net/java/dev/jna/jna/5.14.0";
  };

  "org.apache.logging.log4j_log4j-2.24.3" = fetchMaven {
    name = "org.apache.logging.log4j_log4j-2.24.3";
    urls = [ "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j/2.24.3/log4j-2.24.3.pom" ];
    hash = "sha256-bWuk6kxsiWW675JezWblZ8RdkKFg9C/3CgzdMGJr1Z8=";
    installPath = "https/repo1.maven.org/maven2/org/apache/logging/log4j/log4j/2.24.3";
  };

  "org.apache.logging.log4j_log4j-api-2.24.3" = fetchMaven {
    name = "org.apache.logging.log4j_log4j-api-2.24.3";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.24.3/log4j-api-2.24.3.jar"
      "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.24.3/log4j-api-2.24.3.pom"
    ];
    hash = "sha256-y6wgpqMFwL3B3CrUbTI4HQTBjc4YSWxn0WF8QQSjpFw=";
    installPath = "https/repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.24.3";
  };

  "org.apache.logging.log4j_log4j-bom-2.24.3" = fetchMaven {
    name = "org.apache.logging.log4j_log4j-bom-2.24.3";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-bom/2.24.3/log4j-bom-2.24.3.pom"
    ];
    hash = "sha256-UNEo/UyoskA/8X62/rwMQObDQxfHDiJKj2pBP9SNoek=";
    installPath = "https/repo1.maven.org/maven2/org/apache/logging/log4j/log4j-bom/2.24.3";
  };

  "org.apache.logging.log4j_log4j-core-2.24.3" = fetchMaven {
    name = "org.apache.logging.log4j_log4j-core-2.24.3";
    urls = [
      "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.24.3/log4j-core-2.24.3.jar"
      "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.24.3/log4j-core-2.24.3.pom"
    ];
    hash = "sha256-kRXpkDJtXT0VoEyxj5hIc8Z8foh8rKnFqCpjohdh5LQ=";
    installPath = "https/repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.24.3";
  };

}
# Project Source Hash:sha256-sXrQAYU6oBq+yvoXbxwkso6xdnYAkkbae+qMmS98nes=
