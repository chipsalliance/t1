{ fetchurl }:
let
  fetchMaven = { name, urls, hash, installPath }: with builtins;
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
      postFetch = ''
        mkdir -p "$out"
        cp -v "$downloadedFile" "$out/${baseNameOf firstUrl}"
      '' + concatStringsSep "\n"
        (map
          (elem:
            let filename = baseNameOf elem; in ''
              downloadedFile=$TMPDIR/${filename}
              tryDownload ${elem} "$downloadedFile"
              cp -v "$TMPDIR/${filename}" "$out/"
            '')
          otherUrls);
    };
in
{

  "commons-codec_commons-codec-1.17.0" = fetchMaven {
    name = "commons-codec_commons-codec-1.17.0";
    urls = [ "https://repo1.maven.org/maven2/commons-codec/commons-codec/1.17.0/commons-codec-1.17.0.pom" ];
    hash = "sha256-rnHRsuLtYGmqFrUPwN5RSu5fuROBWI2N4SO7dWSZANA=";
    installPath = "https/repo1.maven.org/maven2/commons-codec/commons-codec/1.17.0";
  };

  "commons-codec_commons-codec-1.19.0" = fetchMaven {
    name = "commons-codec_commons-codec-1.19.0";
    urls = [ "https://repo1.maven.org/maven2/commons-codec/commons-codec/1.19.0/commons-codec-1.19.0.jar" "https://repo1.maven.org/maven2/commons-codec/commons-codec/1.19.0/commons-codec-1.19.0.pom" ];
    hash = "sha256-Tctia3qzKymGgRNoH+mVSjTp3wPVbX8HIevxQz6Qz5g=";
    installPath = "https/repo1.maven.org/maven2/commons-codec/commons-codec/1.19.0";
  };

  "commons-io_commons-io-2.20.0" = fetchMaven {
    name = "commons-io_commons-io-2.20.0";
    urls = [ "https://repo1.maven.org/maven2/commons-io/commons-io/2.20.0/commons-io-2.20.0.jar" "https://repo1.maven.org/maven2/commons-io/commons-io/2.20.0/commons-io-2.20.0.pom" ];
    hash = "sha256-o8178vHHyaONu9k/K67WJbEFC0n1G5lis2rMCZfXpS4=";
    installPath = "https/repo1.maven.org/maven2/commons-io/commons-io/2.20.0";
  };

  "com.amazonaws_aws-java-sdk-bom-1.12.791" = fetchMaven {
    name = "com.amazonaws_aws-java-sdk-bom-1.12.791";
    urls = [ "https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bom/1.12.791/aws-java-sdk-bom-1.12.791.pom" ];
    hash = "sha256-8MEZgf0LFFDrkBy+Ktvlrv0RQfnvgn4eQ+wODfqQG9Y=";
    installPath = "https/repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bom/1.12.791";
  };

  "com.amazonaws_aws-java-sdk-pom-1.12.791" = fetchMaven {
    name = "com.amazonaws_aws-java-sdk-pom-1.12.791";
    urls = [ "https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-pom/1.12.791/aws-java-sdk-pom-1.12.791.pom" ];
    hash = "sha256-x1s8cB+PbJZ3N2iu+ga7AfJqPCNjQyVsfep5mc929js=";
    installPath = "https/repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-pom/1.12.791";
  };

  "com.eed3si9n_shaded-jawn-parser_3-1.3.2" = fetchMaven {
    name = "com.eed3si9n_shaded-jawn-parser_3-1.3.2";
    urls = [ "https://repo1.maven.org/maven2/com/eed3si9n/shaded-jawn-parser_3/1.3.2/shaded-jawn-parser_3-1.3.2.jar" "https://repo1.maven.org/maven2/com/eed3si9n/shaded-jawn-parser_3/1.3.2/shaded-jawn-parser_3-1.3.2.pom" ];
    hash = "sha256-pGLVZWs9cfxuwKYy+XhLZcEQV9Ong5YKuV6zaMX0TWM=";
    installPath = "https/repo1.maven.org/maven2/com/eed3si9n/shaded-jawn-parser_3/1.3.2";
  };

  "com.eed3si9n_shaded-scalajson_3-1.0.0-M4" = fetchMaven {
    name = "com.eed3si9n_shaded-scalajson_3-1.0.0-M4";
    urls = [ "https://repo1.maven.org/maven2/com/eed3si9n/shaded-scalajson_3/1.0.0-M4/shaded-scalajson_3-1.0.0-M4.jar" "https://repo1.maven.org/maven2/com/eed3si9n/shaded-scalajson_3/1.0.0-M4/shaded-scalajson_3-1.0.0-M4.pom" ];
    hash = "sha256-9w1IZvK5lwswQfQiwfwZJDiQaT7a0XvKKWK+pfkh/co=";
    installPath = "https/repo1.maven.org/maven2/com/eed3si9n/shaded-scalajson_3/1.0.0-M4";
  };

  "com.eed3si9n_sjson-new-core_3-0.10.1" = fetchMaven {
    name = "com.eed3si9n_sjson-new-core_3-0.10.1";
    urls = [ "https://repo1.maven.org/maven2/com/eed3si9n/sjson-new-core_3/0.10.1/sjson-new-core_3-0.10.1.pom" ];
    hash = "sha256-g0aETJA9YwthKanzoO2q/g+6SH31bJsaqRZu+x/0zqg=";
    installPath = "https/repo1.maven.org/maven2/com/eed3si9n/sjson-new-core_3/0.10.1";
  };

  "com.eed3si9n_sjson-new-core_3-0.14.0-M5" = fetchMaven {
    name = "com.eed3si9n_sjson-new-core_3-0.14.0-M5";
    urls = [ "https://repo1.maven.org/maven2/com/eed3si9n/sjson-new-core_3/0.14.0-M5/sjson-new-core_3-0.14.0-M5.jar" "https://repo1.maven.org/maven2/com/eed3si9n/sjson-new-core_3/0.14.0-M5/sjson-new-core_3-0.14.0-M5.pom" ];
    hash = "sha256-BpUaTTdi6wOjsXGvKl5e6tMTf/YfGpA4wIVmRITaSsU=";
    installPath = "https/repo1.maven.org/maven2/com/eed3si9n/sjson-new-core_3/0.14.0-M5";
  };

  "com.eed3si9n_sjson-new-scalajson_3-0.14.0-M5" = fetchMaven {
    name = "com.eed3si9n_sjson-new-scalajson_3-0.14.0-M5";
    urls = [ "https://repo1.maven.org/maven2/com/eed3si9n/sjson-new-scalajson_3/0.14.0-M5/sjson-new-scalajson_3-0.14.0-M5.jar" "https://repo1.maven.org/maven2/com/eed3si9n/sjson-new-scalajson_3/0.14.0-M5/sjson-new-scalajson_3-0.14.0-M5.pom" ];
    hash = "sha256-T8lJPOwU3RCsy3e85D/yfAmZzIUhB+n+dK/S5m0i4JY=";
    installPath = "https/repo1.maven.org/maven2/com/eed3si9n/sjson-new-scalajson_3/0.14.0-M5";
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

  "com.fasterxml_oss-parent-68" = fetchMaven {
    name = "com.fasterxml_oss-parent-68";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/oss-parent/68/oss-parent-68.pom" ];
    hash = "sha256-qdxU7lCS3weCqjFAiHlT8Aa6t8bS2Yx2TV7xFruK4qw=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/oss-parent/68";
  };

  "com.fasterxml_oss-parent-70" = fetchMaven {
    name = "com.fasterxml_oss-parent-70";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/oss-parent/70/oss-parent-70.pom" ];
    hash = "sha256-DmH8861HNQ5zaKv1yEAatJ/LpT+7gIin6+pbNo+jE5w=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/oss-parent/70";
  };

  "com.lihaoyi_fansi_3-0.5.0" = fetchMaven {
    name = "com.lihaoyi_fansi_3-0.5.0";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/fansi_3/0.5.0/fansi_3-0.5.0.jar" "https://repo1.maven.org/maven2/com/lihaoyi/fansi_3/0.5.0/fansi_3-0.5.0.pom" ];
    hash = "sha256-LBJpOQv1O5lM5QVBMp4to7u3N8dCmqg/lxEi6mVmgPo=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/fansi_3/0.5.0";
  };

  "com.lihaoyi_fansi_3-0.5.1" = fetchMaven {
    name = "com.lihaoyi_fansi_3-0.5.1";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/fansi_3/0.5.1/fansi_3-0.5.1.jar" "https://repo1.maven.org/maven2/com/lihaoyi/fansi_3/0.5.1/fansi_3-0.5.1.pom" ];
    hash = "sha256-1OUhN7HxHQeI8uOzAZr1tMi2z7+LvOEJ+j9BikcSQJo=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/fansi_3/0.5.1";
  };

  "com.lihaoyi_fastparse_3-3.1.1" = fetchMaven {
    name = "com.lihaoyi_fastparse_3-3.1.1";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/fastparse_3/3.1.1/fastparse_3-3.1.1.jar" "https://repo1.maven.org/maven2/com/lihaoyi/fastparse_3/3.1.1/fastparse_3-3.1.1.pom" ];
    hash = "sha256-iz6Wj92asaujz93RjmBAaKHHV64HS26cduPsQzaD6wM=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/fastparse_3/3.1.1";
  };

  "com.lihaoyi_geny_3-1.1.1" = fetchMaven {
    name = "com.lihaoyi_geny_3-1.1.1";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/geny_3/1.1.1/geny_3-1.1.1.jar" "https://repo1.maven.org/maven2/com/lihaoyi/geny_3/1.1.1/geny_3-1.1.1.pom" ];
    hash = "sha256-DtsM1VVr7WxRM+YRjjVDOkfCqXzp2q9FwlSMgoD/+ow=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/geny_3/1.1.1";
  };

  "com.lihaoyi_mainargs_3-0.7.6" = fetchMaven {
    name = "com.lihaoyi_mainargs_3-0.7.6";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mainargs_3/0.7.6/mainargs_3-0.7.6.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mainargs_3/0.7.6/mainargs_3-0.7.6.pom" ];
    hash = "sha256-d0HGGb6XHh0K8gP9Y0gSt1pbUhMPoI/WBJSOll5ZywE=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mainargs_3/0.7.6";
  };

  "com.lihaoyi_mainargs_3-0.7.8" = fetchMaven {
    name = "com.lihaoyi_mainargs_3-0.7.8";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mainargs_3/0.7.8/mainargs_3-0.7.8.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mainargs_3/0.7.8/mainargs_3-0.7.8.pom" ];
    hash = "sha256-7s+2mHx7lZqNBQSHcR7CLAclPXnDlE/TrnWIofLa7bk=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mainargs_3/0.7.8";
  };

  "com.lihaoyi_mill-contrib-jmh_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-contrib-jmh_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-contrib-jmh_3/1.1.2/mill-contrib-jmh_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-contrib-jmh_3/1.1.2/mill-contrib-jmh_3-1.1.2.pom" ];
    hash = "sha256-XiAb972oG+e+u5yScHNCjbZvuCZBt6r+1834nLY5Css=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-contrib-jmh_3/1.1.2";
  };

  "com.lihaoyi_mill-core-api-daemon_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-core-api-daemon_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-api-daemon_3/1.1.2/mill-core-api-daemon_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-api-daemon_3/1.1.2/mill-core-api-daemon_3-1.1.2.pom" ];
    hash = "sha256-mQ7rHjSLf/Tee+NtyY30Smh8pfLIAL5/wSlnOC6JgaA=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-core-api-daemon_3/1.1.2";
  };

  "com.lihaoyi_mill-core-api-java11_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-core-api-java11_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-api-java11_3/1.1.2/mill-core-api-java11_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-api-java11_3/1.1.2/mill-core-api-java11_3-1.1.2.pom" ];
    hash = "sha256-uO4afITglhjenvWY5Lf2ACt77gZUOpv81T0TOMETrko=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-core-api-java11_3/1.1.2";
  };

  "com.lihaoyi_mill-core-api_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-core-api_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-api_3/1.1.2/mill-core-api_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-api_3/1.1.2/mill-core-api_3-1.1.2.pom" ];
    hash = "sha256-G7chgnGJcs12K7QHuCAmfy6nJw7hl4SBFaPuNKxU+Vs=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-core-api_3/1.1.2";
  };

  "com.lihaoyi_mill-core-constants-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-core-constants-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-constants/1.1.2/mill-core-constants-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-constants/1.1.2/mill-core-constants-1.1.2.pom" ];
    hash = "sha256-wkmhazNYEgbRjHmxkY64IhulZqsfssZcAOO8wK/S2es=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-core-constants/1.1.2";
  };

  "com.lihaoyi_mill-core-eval_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-core-eval_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-eval_3/1.1.2/mill-core-eval_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-eval_3/1.1.2/mill-core-eval_3-1.1.2.pom" ];
    hash = "sha256-HfPCKiVBRTp7I+9eakFyIvYo+jtvL9H8FzuIBLimfKA=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-core-eval_3/1.1.2";
  };

  "com.lihaoyi_mill-core-exec_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-core-exec_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-exec_3/1.1.2/mill-core-exec_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-exec_3/1.1.2/mill-core-exec_3-1.1.2.pom" ];
    hash = "sha256-uYDZcUEkv1aHdpiM8DgKEcW1VNMI8DeAyTHTGRu4rnM=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-core-exec_3/1.1.2";
  };

  "com.lihaoyi_mill-core-internal-cli_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-core-internal-cli_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-internal-cli_3/1.1.2/mill-core-internal-cli_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-internal-cli_3/1.1.2/mill-core-internal-cli_3-1.1.2.pom" ];
    hash = "sha256-BAogImjrH7stBJDLlZHf8UNWH3ZkY/p589JIgBRT+M0=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-core-internal-cli_3/1.1.2";
  };

  "com.lihaoyi_mill-core-internal_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-core-internal_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-internal_3/1.1.2/mill-core-internal_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-internal_3/1.1.2/mill-core-internal_3-1.1.2.pom" ];
    hash = "sha256-PR/WWhWNCvGYw+pFB2l81haIJ46AEO4omoKYGRJAWPQ=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-core-internal_3/1.1.2";
  };

  "com.lihaoyi_mill-core-resolve_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-core-resolve_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-resolve_3/1.1.2/mill-core-resolve_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-core-resolve_3/1.1.2/mill-core-resolve_3-1.1.2.pom" ];
    hash = "sha256-fLR5y5yWrJDbfFlKKIhxKTdCTWuMrXdfogfblhAA7lI=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-core-resolve_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-androidlib-databinding_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-androidlib-databinding_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-androidlib-databinding_3/1.1.2/mill-libs-androidlib-databinding_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-androidlib-databinding_3/1.1.2/mill-libs-androidlib-databinding_3-1.1.2.pom" ];
    hash = "sha256-k8J5vZdFjFcJxUoZaVlfJCXGcW0CyexkaM1JBFnpwSQ=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-androidlib-databinding_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-androidlib_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-androidlib_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-androidlib_3/1.1.2/mill-libs-androidlib_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-androidlib_3/1.1.2/mill-libs-androidlib_3-1.1.2.pom" ];
    hash = "sha256-OI4LfbpHYe3JcfRV8jIlZN7q+Ta7ZwR3LulGxEiIcUg=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-androidlib_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-daemon-client_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-daemon-client_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-daemon-client_3/1.1.2/mill-libs-daemon-client_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-daemon-client_3/1.1.2/mill-libs-daemon-client_3-1.1.2.pom" ];
    hash = "sha256-dC9ychPosVQX9GelZ163UYi7H9T4TwtZLQd1wBilLLE=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-daemon-client_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-daemon-server_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-daemon-server_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-daemon-server_3/1.1.2/mill-libs-daemon-server_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-daemon-server_3/1.1.2/mill-libs-daemon-server_3-1.1.2.pom" ];
    hash = "sha256-lBscl0VP6c6DMj5MQHcllFMfkGwMBl5Qm/P3pc/ZRhM=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-daemon-server_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-groovylib-api_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-groovylib-api_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-groovylib-api_3/1.1.2/mill-libs-groovylib-api_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-groovylib-api_3/1.1.2/mill-libs-groovylib-api_3-1.1.2.pom" ];
    hash = "sha256-wltey68WXHGxgfyetMg7HGWY8DfWycxyk1JGuBZ4zm0=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-groovylib-api_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-groovylib_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-groovylib_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-groovylib_3/1.1.2/mill-libs-groovylib_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-groovylib_3/1.1.2/mill-libs-groovylib_3-1.1.2.pom" ];
    hash = "sha256-oLdENQaBTUIpB6vxXShYVkpwTUzEW5UTmS97VJaKgHA=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-groovylib_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-init_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-init_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-init_3/1.1.2/mill-libs-init_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-init_3/1.1.2/mill-libs-init_3-1.1.2.pom" ];
    hash = "sha256-VmfKurx9hRmgSEOct0W9M2iENm/Nyl0+Sr6l8/Sov9s=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-init_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-javalib-api_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-javalib-api_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-api_3/1.1.2/mill-libs-javalib-api_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-api_3/1.1.2/mill-libs-javalib-api_3-1.1.2.pom" ];
    hash = "sha256-ljdJL19DBY6CgUWmyMAD4OszsVuEK+Nzfeo9+NnhWxo=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-api_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-javalib-classgraph-worker_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-javalib-classgraph-worker_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-classgraph-worker_3/1.1.2/mill-libs-javalib-classgraph-worker_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-classgraph-worker_3/1.1.2/mill-libs-javalib-classgraph-worker_3-1.1.2.pom" ];
    hash = "sha256-5q/IbWLH2cDRF/VbRZt1NX7lrRSHnwP1j9TRpSDhaG4=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-classgraph-worker_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-javalib-jarjarabrams-worker_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-javalib-jarjarabrams-worker_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-jarjarabrams-worker_3/1.1.2/mill-libs-javalib-jarjarabrams-worker_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-jarjarabrams-worker_3/1.1.2/mill-libs-javalib-jarjarabrams-worker_3-1.1.2.pom" ];
    hash = "sha256-+1ewNwOBjvLdFq0ftqdpH3Xg9dHVzI2+5R10Ow0QuyE=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-jarjarabrams-worker_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-javalib-testrunner-entrypoint-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-javalib-testrunner-entrypoint-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-testrunner-entrypoint/1.1.2/mill-libs-javalib-testrunner-entrypoint-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-testrunner-entrypoint/1.1.2/mill-libs-javalib-testrunner-entrypoint-1.1.2.pom" ];
    hash = "sha256-lFmlMN5C5nADicOuYRPmITyK7uZChLzsoszpRVbjASY=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-testrunner-entrypoint/1.1.2";
  };

  "com.lihaoyi_mill-libs-javalib-testrunner_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-javalib-testrunner_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-testrunner_3/1.1.2/mill-libs-javalib-testrunner_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-testrunner_3/1.1.2/mill-libs-javalib-testrunner_3-1.1.2.pom" ];
    hash = "sha256-5Vb7Y6rg9tdLeCqs8XZEilR2AZ1gedM9VjftK1YXgL4=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-testrunner_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-javalib-worker_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-javalib-worker_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-worker_3/1.1.2/mill-libs-javalib-worker_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-worker_3/1.1.2/mill-libs-javalib-worker_3-1.1.2.pom" ];
    hash = "sha256-JFY7P+f8LS7xVPw6dpoThkEDDMVk/a+H0y8bg8wo8WM=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib-worker_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-javalib_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-javalib_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib_3/1.1.2/mill-libs-javalib_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib_3/1.1.2/mill-libs-javalib_3-1.1.2.pom" ];
    hash = "sha256-gwmisVzn+jkVJ6FiZKJY4QITSTMJNNW711magqghs2o=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-javalib_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-javascriptlib_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-javascriptlib_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javascriptlib_3/1.1.2/mill-libs-javascriptlib_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-javascriptlib_3/1.1.2/mill-libs-javascriptlib_3-1.1.2.pom" ];
    hash = "sha256-OOdu3/PUn3Kpt7atgSrsDU9ph9/R3kFqLe7RKP4WPXE=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-javascriptlib_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-kotlinlib-api_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-kotlinlib-api_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-kotlinlib-api_3/1.1.2/mill-libs-kotlinlib-api_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-kotlinlib-api_3/1.1.2/mill-libs-kotlinlib-api_3-1.1.2.pom" ];
    hash = "sha256-SiQ3SLlqdkFe5t3AI1obh79ICpp9Bg+Q6MI271t2hBM=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-kotlinlib-api_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-kotlinlib-ksp2-api_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-kotlinlib-ksp2-api_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-kotlinlib-ksp2-api_3/1.1.2/mill-libs-kotlinlib-ksp2-api_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-kotlinlib-ksp2-api_3/1.1.2/mill-libs-kotlinlib-ksp2-api_3-1.1.2.pom" ];
    hash = "sha256-cLJTmS54cz/YkDTGxFkJ7jUPm0aUSNxjBsGHzYQL18Y=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-kotlinlib-ksp2-api_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-kotlinlib_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-kotlinlib_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-kotlinlib_3/1.1.2/mill-libs-kotlinlib_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-kotlinlib_3/1.1.2/mill-libs-kotlinlib_3-1.1.2.pom" ];
    hash = "sha256-gEpC2K/45ijpQ4mKXLmXMuhfPIxPDQa77OnhMtCLGoo=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-kotlinlib_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-pythonlib_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-pythonlib_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-pythonlib_3/1.1.2/mill-libs-pythonlib_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-pythonlib_3/1.1.2/mill-libs-pythonlib_3-1.1.2.pom" ];
    hash = "sha256-16m0WXcCZwtb0hmhBO8BsgM18EU0FsFWn1mzGFFfkGs=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-pythonlib_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-rpc_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-rpc_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-rpc_3/1.1.2/mill-libs-rpc_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-rpc_3/1.1.2/mill-libs-rpc_3-1.1.2.pom" ];
    hash = "sha256-uwuqKtSAYrVELwWv0TKOpBPGlNHHzDCEBPz9nY1WLlI=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-rpc_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-scalajslib-api_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-scalajslib-api_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-scalajslib-api_3/1.1.2/mill-libs-scalajslib-api_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-scalajslib-api_3/1.1.2/mill-libs-scalajslib-api_3-1.1.2.pom" ];
    hash = "sha256-S51WPLOX6Y+5WiAdVU26yk6rXEozpvXOpCB702PsTns=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-scalajslib-api_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-scalajslib_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-scalajslib_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-scalajslib_3/1.1.2/mill-libs-scalajslib_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-scalajslib_3/1.1.2/mill-libs-scalajslib_3-1.1.2.pom" ];
    hash = "sha256-TfCkj5oZIilGNtZlKkROegybhBdVsxA1dZKK8LzCmu0=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-scalajslib_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-scalalib_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-scalalib_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-scalalib_3/1.1.2/mill-libs-scalalib_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-scalalib_3/1.1.2/mill-libs-scalalib_3-1.1.2.pom" ];
    hash = "sha256-8GYp/gn0C8URvz+ZoJMKw8brKidKoJIRa3xyLmSDkp0=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-scalalib_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-scalanativelib-api_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-scalanativelib-api_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-scalanativelib-api_3/1.1.2/mill-libs-scalanativelib-api_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-scalanativelib-api_3/1.1.2/mill-libs-scalanativelib-api_3-1.1.2.pom" ];
    hash = "sha256-r1kfH9auRn2zYQpyGI4cFAh1QmHkiIyZF0NIE61y9no=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-scalanativelib-api_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-scalanativelib_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-scalanativelib_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-scalanativelib_3/1.1.2/mill-libs-scalanativelib_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-scalanativelib_3/1.1.2/mill-libs-scalanativelib_3-1.1.2.pom" ];
    hash = "sha256-8ft0TdYCOqtyL9WQypn9IUFLf1H832kYPgEW0E53jXg=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-scalanativelib_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-script_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-script_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-script_3/1.1.2/mill-libs-script_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-script_3/1.1.2/mill-libs-script_3-1.1.2.pom" ];
    hash = "sha256-wZ5T11P69UZ2mipkh88H1GdP+fDUiub9Ju4QqrQK4Ug=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-script_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-tabcomplete_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-tabcomplete_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-tabcomplete_3/1.1.2/mill-libs-tabcomplete_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-tabcomplete_3/1.1.2/mill-libs-tabcomplete_3-1.1.2.pom" ];
    hash = "sha256-RFKZn57FHr9UlWXQu2/xaJOgycE8pbwxwaq81/B+XaE=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-tabcomplete_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-util-java11_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-util-java11_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-util-java11_3/1.1.2/mill-libs-util-java11_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-util-java11_3/1.1.2/mill-libs-util-java11_3-1.1.2.pom" ];
    hash = "sha256-Prg6WWjeYtz10L0Wy5+vh+XQAqJCMPN6bHtw7iyLYcg=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-util-java11_3/1.1.2";
  };

  "com.lihaoyi_mill-libs-util_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs-util_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-util_3/1.1.2/mill-libs-util_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs-util_3/1.1.2/mill-libs-util_3-1.1.2.pom" ];
    hash = "sha256-POI2yWjNr7KWXLEtuosuxfGlVC3n5EVXC1tFssLRJ9A=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs-util_3/1.1.2";
  };

  "com.lihaoyi_mill-libs_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-libs_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs_3/1.1.2/mill-libs_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-libs_3/1.1.2/mill-libs_3-1.1.2.pom" ];
    hash = "sha256-BOLDZWmgqwwBx9btgiDAVbCm0S5KvR6zQcUphbQjysc=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-libs_3/1.1.2";
  };

  "com.lihaoyi_mill-moduledefs_3-0.13.1" = fetchMaven {
    name = "com.lihaoyi_mill-moduledefs_3-0.13.1";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-moduledefs_3/0.13.1/mill-moduledefs_3-0.13.1.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-moduledefs_3/0.13.1/mill-moduledefs_3-0.13.1.pom" ];
    hash = "sha256-FtoxYwGaNOAbIgk6dJvCB3q3LMbWRaWp1Oihk/pK1TU=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-moduledefs_3/0.13.1";
  };

  "com.lihaoyi_mill-runner-autooverride-api_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-runner-autooverride-api_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-autooverride-api_3/1.1.2/mill-runner-autooverride-api_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-autooverride-api_3/1.1.2/mill-runner-autooverride-api_3-1.1.2.pom" ];
    hash = "sha256-Nkp3U5DcLx1sJF0XSlmuFagI/UoSI2BEYdlWBdJYl08=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-runner-autooverride-api_3/1.1.2";
  };

  "com.lihaoyi_mill-runner-autooverride-plugin_3.8.1-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-runner-autooverride-plugin_3.8.1-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-autooverride-plugin_3.8.1/1.1.2/mill-runner-autooverride-plugin_3.8.1-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-autooverride-plugin_3.8.1/1.1.2/mill-runner-autooverride-plugin_3.8.1-1.1.2.pom" ];
    hash = "sha256-6eHtu3hZ5IT5c61qLtMVhbaQPbsWvT0hE/7Lpd4DTjc=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-runner-autooverride-plugin_3.8.1/1.1.2";
  };

  "com.lihaoyi_mill-runner-bsp-worker_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-runner-bsp-worker_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-bsp-worker_3/1.1.2/mill-runner-bsp-worker_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-bsp-worker_3/1.1.2/mill-runner-bsp-worker_3-1.1.2.pom" ];
    hash = "sha256-BSePy8QV1rSQTtbUqUn+Q+wH2dUPsvaawTvlMhZR+ec=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-runner-bsp-worker_3/1.1.2";
  };

  "com.lihaoyi_mill-runner-bsp_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-runner-bsp_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-bsp_3/1.1.2/mill-runner-bsp_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-bsp_3/1.1.2/mill-runner-bsp_3-1.1.2.pom" ];
    hash = "sha256-TwHkBcoyR8mHEU295zdKTywS+dFTh0HwDbnfV0A/JWg=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-runner-bsp_3/1.1.2";
  };

  "com.lihaoyi_mill-runner-codesig_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-runner-codesig_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-codesig_3/1.1.2/mill-runner-codesig_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-codesig_3/1.1.2/mill-runner-codesig_3-1.1.2.pom" ];
    hash = "sha256-vXAoUMONOtaWJeBJWli4S6SYeBjHMMwMK5KwPJFVtaY=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-runner-codesig_3/1.1.2";
  };

  "com.lihaoyi_mill-runner-daemon_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-runner-daemon_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-daemon_3/1.1.2/mill-runner-daemon_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-daemon_3/1.1.2/mill-runner-daemon_3-1.1.2.pom" ];
    hash = "sha256-ndsVHWv6W/yv9L931yp92AFlKB9wdMR8P8b51y/aKYc=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-runner-daemon_3/1.1.2";
  };

  "com.lihaoyi_mill-runner-eclipse_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-runner-eclipse_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-eclipse_3/1.1.2/mill-runner-eclipse_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-eclipse_3/1.1.2/mill-runner-eclipse_3-1.1.2.pom" ];
    hash = "sha256-A17sAuMNHada1cRCo3+pJMOFN8LMgRzEuU4/eZGryo8=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-runner-eclipse_3/1.1.2";
  };

  "com.lihaoyi_mill-runner-idea_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-runner-idea_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-idea_3/1.1.2/mill-runner-idea_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-idea_3/1.1.2/mill-runner-idea_3-1.1.2.pom" ];
    hash = "sha256-P0GrkGCVwwcQTA86AO7rK81mSDTpCzuhJfiQWqi07Q4=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-runner-idea_3/1.1.2";
  };

  "com.lihaoyi_mill-runner-launcher_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-runner-launcher_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-launcher_3/1.1.2/mill-runner-launcher_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-launcher_3/1.1.2/mill-runner-launcher_3-1.1.2.pom" ];
    hash = "sha256-Mg9kxrB3kTHtIRxwSajBSjbuxggq4z03wAIgOYTrKcs=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-runner-launcher_3/1.1.2";
  };

  "com.lihaoyi_mill-runner-meta_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-runner-meta_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-meta_3/1.1.2/mill-runner-meta_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-meta_3/1.1.2/mill-runner-meta_3-1.1.2.pom" ];
    hash = "sha256-fCML4oVhlZMx1TMkN3FRTZuiuoR83YQ40ma1Vpap8r8=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-runner-meta_3/1.1.2";
  };

  "com.lihaoyi_mill-runner-server_3-1.1.2" = fetchMaven {
    name = "com.lihaoyi_mill-runner-server_3-1.1.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-server_3/1.1.2/mill-runner-server_3-1.1.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/mill-runner-server_3/1.1.2/mill-runner-server_3-1.1.2.pom" ];
    hash = "sha256-9KPjGyZ5GQ3hfFXAQH3MigSun/Y2Csde47pzNOIwlaI=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/mill-runner-server_3/1.1.2";
  };

  "com.lihaoyi_os-lib-watch_3-0.11.8" = fetchMaven {
    name = "com.lihaoyi_os-lib-watch_3-0.11.8";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/os-lib-watch_3/0.11.8/os-lib-watch_3-0.11.8.jar" "https://repo1.maven.org/maven2/com/lihaoyi/os-lib-watch_3/0.11.8/os-lib-watch_3-0.11.8.pom" ];
    hash = "sha256-RkqtzxzH4tiaOFaDpQdvXhqdiR4Mrzk/VVbXm/0O8K8=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/os-lib-watch_3/0.11.8";
  };

  "com.lihaoyi_os-lib_3-0.11.3" = fetchMaven {
    name = "com.lihaoyi_os-lib_3-0.11.3";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_3/0.11.3/os-lib_3-0.11.3.jar" "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_3/0.11.3/os-lib_3-0.11.3.pom" ];
    hash = "sha256-oNCL91uzMsZR4hsuDStMx80VtqvudIKLv3Z92Izhnac=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/os-lib_3/0.11.3";
  };

  "com.lihaoyi_os-lib_3-0.11.8" = fetchMaven {
    name = "com.lihaoyi_os-lib_3-0.11.8";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_3/0.11.8/os-lib_3-0.11.8.jar" "https://repo1.maven.org/maven2/com/lihaoyi/os-lib_3/0.11.8/os-lib_3-0.11.8.pom" ];
    hash = "sha256-x+kzII+L0a54bp4fSurrXiunx7R7DtHKvLVaZvPZ0yM=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/os-lib_3/0.11.8";
  };

  "com.lihaoyi_os-zip-0.11.8" = fetchMaven {
    name = "com.lihaoyi_os-zip-0.11.8";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/os-zip/0.11.8/os-zip-0.11.8.jar" "https://repo1.maven.org/maven2/com/lihaoyi/os-zip/0.11.8/os-zip-0.11.8.pom" ];
    hash = "sha256-jBJhfpAe97pLxzyc4Qe17M3KbZea93pwz3lEr8Z0v18=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/os-zip/0.11.8";
  };

  "com.lihaoyi_pprint_3-0.9.0" = fetchMaven {
    name = "com.lihaoyi_pprint_3-0.9.0";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/pprint_3/0.9.0/pprint_3-0.9.0.jar" "https://repo1.maven.org/maven2/com/lihaoyi/pprint_3/0.9.0/pprint_3-0.9.0.pom" ];
    hash = "sha256-G9+cXrjL/+aawfpe5/xSRzxGiyYWUGKyE1G2sPKHwOk=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/pprint_3/0.9.0";
  };

  "com.lihaoyi_pprint_3-0.9.3" = fetchMaven {
    name = "com.lihaoyi_pprint_3-0.9.3";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/pprint_3/0.9.3/pprint_3-0.9.3.jar" "https://repo1.maven.org/maven2/com/lihaoyi/pprint_3/0.9.3/pprint_3-0.9.3.pom" ];
    hash = "sha256-1Ifl6qABoIAAD/1ahPwZ+qTVhEYfqecg9OtCi+kzEh8=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/pprint_3/0.9.3";
  };

  "com.lihaoyi_pprint_3-0.9.6" = fetchMaven {
    name = "com.lihaoyi_pprint_3-0.9.6";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/pprint_3/0.9.6/pprint_3-0.9.6.jar" "https://repo1.maven.org/maven2/com/lihaoyi/pprint_3/0.9.6/pprint_3-0.9.6.pom" ];
    hash = "sha256-rgJ1Xt7SYj05HgAYb3r+lBvbRcM++i3EMqV4a/3ibp0=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/pprint_3/0.9.6";
  };

  "com.lihaoyi_requests_3-0.9.3" = fetchMaven {
    name = "com.lihaoyi_requests_3-0.9.3";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/requests_3/0.9.3/requests_3-0.9.3.jar" "https://repo1.maven.org/maven2/com/lihaoyi/requests_3/0.9.3/requests_3-0.9.3.pom" ];
    hash = "sha256-y9tnEMrHt/e+hxfK3PksjjHIEwJ2Ej6hypRJlJhzj2c=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/requests_3/0.9.3";
  };

  "com.lihaoyi_scalac-mill-moduledefs-plugin_3.8.1-0.13.1" = fetchMaven {
    name = "com.lihaoyi_scalac-mill-moduledefs-plugin_3.8.1-0.13.1";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/scalac-mill-moduledefs-plugin_3.8.1/0.13.1/scalac-mill-moduledefs-plugin_3.8.1-0.13.1.jar" "https://repo1.maven.org/maven2/com/lihaoyi/scalac-mill-moduledefs-plugin_3.8.1/0.13.1/scalac-mill-moduledefs-plugin_3.8.1-0.13.1.pom" ];
    hash = "sha256-E2R99SLkiCMzWsjYnjfhx6Vzwg+mqztY/WW4MLQlHK8=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/scalac-mill-moduledefs-plugin_3.8.1/0.13.1";
  };

  "com.lihaoyi_sourcecode_3-0.3.0" = fetchMaven {
    name = "com.lihaoyi_sourcecode_3-0.3.0";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_3/0.3.0/sourcecode_3-0.3.0.pom" ];
    hash = "sha256-lr4/nfVXauGgxI2rR6IJs2lPSQkS39mb8QS//1agWtA=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/sourcecode_3/0.3.0";
  };

  "com.lihaoyi_sourcecode_3-0.4.0" = fetchMaven {
    name = "com.lihaoyi_sourcecode_3-0.4.0";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_3/0.4.0/sourcecode_3-0.4.0.jar" "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_3/0.4.0/sourcecode_3-0.4.0.pom" ];
    hash = "sha256-uuL36U8SXTZ1e0obeznF/zJXAspEWrmdYzOTgfrhMto=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/sourcecode_3/0.4.0";
  };

  "com.lihaoyi_sourcecode_3-0.4.2" = fetchMaven {
    name = "com.lihaoyi_sourcecode_3-0.4.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_3/0.4.2/sourcecode_3-0.4.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_3/0.4.2/sourcecode_3-0.4.2.pom" ];
    hash = "sha256-p5LcBw7u9P3PAtUq5nbl4GN7P59H9EdAI9rwbLr1P4Y=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/sourcecode_3/0.4.2";
  };

  "com.lihaoyi_sourcecode_3-0.4.4" = fetchMaven {
    name = "com.lihaoyi_sourcecode_3-0.4.4";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_3/0.4.4/sourcecode_3-0.4.4.jar" "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_3/0.4.4/sourcecode_3-0.4.4.pom" ];
    hash = "sha256-Mb4BGjFreHJodpuyXYAN4MNqYNlNeSQoTWHUnPYdF10=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/sourcecode_3/0.4.4";
  };

  "com.lihaoyi_ujson_3-4.0.2" = fetchMaven {
    name = "com.lihaoyi_ujson_3-4.0.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/ujson_3/4.0.2/ujson_3-4.0.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/ujson_3/4.0.2/ujson_3-4.0.2.pom" ];
    hash = "sha256-sh595bxDgPH+K/tgqyzxgMrwafXDlYep4ScqZ5dcwI8=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/ujson_3/4.0.2";
  };

  "com.lihaoyi_ujson_3-4.4.2" = fetchMaven {
    name = "com.lihaoyi_ujson_3-4.4.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/ujson_3/4.4.2/ujson_3-4.4.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/ujson_3/4.4.2/ujson_3-4.4.2.pom" ];
    hash = "sha256-14FQOT3YuhO/e2s/vcwY3FXfZxWswhK1OEbO0NrHU0k=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/ujson_3/4.4.2";
  };

  "com.lihaoyi_unroll-annotation_3-0.2.0" = fetchMaven {
    name = "com.lihaoyi_unroll-annotation_3-0.2.0";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/unroll-annotation_3/0.2.0/unroll-annotation_3-0.2.0.jar" "https://repo1.maven.org/maven2/com/lihaoyi/unroll-annotation_3/0.2.0/unroll-annotation_3-0.2.0.pom" ];
    hash = "sha256-ExxiEO3FCd5f41vmo6LitJC46Lq5EBcjLSsFDmrTWLA=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/unroll-annotation_3/0.2.0";
  };

  "com.lihaoyi_upack_3-4.0.2" = fetchMaven {
    name = "com.lihaoyi_upack_3-4.0.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/upack_3/4.0.2/upack_3-4.0.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/upack_3/4.0.2/upack_3-4.0.2.pom" ];
    hash = "sha256-uf+67kBS2CsvzzSUbGO3qH0KZgQpqgvATYSdL2oXlz8=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upack_3/4.0.2";
  };

  "com.lihaoyi_upack_3-4.4.2" = fetchMaven {
    name = "com.lihaoyi_upack_3-4.4.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/upack_3/4.4.2/upack_3-4.4.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/upack_3/4.4.2/upack_3-4.4.2.pom" ];
    hash = "sha256-9HjqFwwpCtV+B3KNe0SXk1GisLauM8QID0wL1js/Ek4=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upack_3/4.4.2";
  };

  "com.lihaoyi_upickle-core_3-4.0.2" = fetchMaven {
    name = "com.lihaoyi_upickle-core_3-4.0.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/upickle-core_3/4.0.2/upickle-core_3-4.0.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/upickle-core_3/4.0.2/upickle-core_3-4.0.2.pom" ];
    hash = "sha256-y+2kYNhenuu+ILt2x58+BXzpPEezMKi7hx8w+9s0a70=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle-core_3/4.0.2";
  };

  "com.lihaoyi_upickle-core_3-4.4.2" = fetchMaven {
    name = "com.lihaoyi_upickle-core_3-4.4.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/upickle-core_3/4.4.2/upickle-core_3-4.4.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/upickle-core_3/4.4.2/upickle-core_3-4.4.2.pom" ];
    hash = "sha256-otfBW6j+ww6NPz/0640cjeQbO6DZsQ+UP+4POBBxRmU=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle-core_3/4.4.2";
  };

  "com.lihaoyi_upickle-implicits-named-tuples_3-4.4.2" = fetchMaven {
    name = "com.lihaoyi_upickle-implicits-named-tuples_3-4.4.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/upickle-implicits-named-tuples_3/4.4.2/upickle-implicits-named-tuples_3-4.4.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/upickle-implicits-named-tuples_3/4.4.2/upickle-implicits-named-tuples_3-4.4.2.pom" ];
    hash = "sha256-OIPCjgTgOESSCQUt13fVJkA1CS3M5oxCe853X/oMuCU=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle-implicits-named-tuples_3/4.4.2";
  };

  "com.lihaoyi_upickle-implicits_3-4.0.2" = fetchMaven {
    name = "com.lihaoyi_upickle-implicits_3-4.0.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_3/4.0.2/upickle-implicits_3-4.0.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_3/4.0.2/upickle-implicits_3-4.0.2.pom" ];
    hash = "sha256-spEOo3MUeUwRKHSk7Oc5n6RIseeDKhPKp/fe8NKQ1Mc=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_3/4.0.2";
  };

  "com.lihaoyi_upickle-implicits_3-4.4.2" = fetchMaven {
    name = "com.lihaoyi_upickle-implicits_3-4.4.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_3/4.4.2/upickle-implicits_3-4.4.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_3/4.4.2/upickle-implicits_3-4.4.2.pom" ];
    hash = "sha256-9q7Bl8HN8LdzCpaRoLYuREqZt8ynedXoE/POkomvkos=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_3/4.4.2";
  };

  "com.lihaoyi_upickle_3-4.0.2" = fetchMaven {
    name = "com.lihaoyi_upickle_3-4.0.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/upickle_3/4.0.2/upickle_3-4.0.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/upickle_3/4.0.2/upickle_3-4.0.2.pom" ];
    hash = "sha256-9lijD0jcIIu+/InNWvfbdxF/DgmoB8ksVb7bl6BM1iU=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle_3/4.0.2";
  };

  "com.lihaoyi_upickle_3-4.4.2" = fetchMaven {
    name = "com.lihaoyi_upickle_3-4.4.2";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/upickle_3/4.4.2/upickle_3-4.4.2.jar" "https://repo1.maven.org/maven2/com/lihaoyi/upickle_3/4.4.2/upickle_3-4.4.2.pom" ];
    hash = "sha256-NI5aRO5/13CRRLV1IXF0kRo6/NanFdm1TE78MINwfkY=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/upickle_3/4.4.2";
  };

  "com.lihaoyi_utest_3-0.8.4" = fetchMaven {
    name = "com.lihaoyi_utest_3-0.8.4";
    urls = [ "https://repo1.maven.org/maven2/com/lihaoyi/utest_3/0.8.4/utest_3-0.8.4.jar" "https://repo1.maven.org/maven2/com/lihaoyi/utest_3/0.8.4/utest_3-0.8.4.pom" ];
    hash = "sha256-89ggPqD9zz3LlcKgfI6W4Zd0vrK1aQZ+UY8b4EZUAk4=";
    installPath = "https/repo1.maven.org/maven2/com/lihaoyi/utest_3/0.8.4";
  };

  "com.lmax_disruptor-3.4.2" = fetchMaven {
    name = "com.lmax_disruptor-3.4.2";
    urls = [ "https://repo1.maven.org/maven2/com/lmax/disruptor/3.4.2/disruptor-3.4.2.jar" "https://repo1.maven.org/maven2/com/lmax/disruptor/3.4.2/disruptor-3.4.2.pom" ];
    hash = "sha256-nbZsn6zL8HaJOrkMiWwvCuHQumcNQYA8e6QrAjXKKKg=";
    installPath = "https/repo1.maven.org/maven2/com/lmax/disruptor/3.4.2";
  };

  "com.lumidion_sonatype-central-client-core_3-0.6.0" = fetchMaven {
    name = "com.lumidion_sonatype-central-client-core_3-0.6.0";
    urls = [ "https://repo1.maven.org/maven2/com/lumidion/sonatype-central-client-core_3/0.6.0/sonatype-central-client-core_3-0.6.0.jar" "https://repo1.maven.org/maven2/com/lumidion/sonatype-central-client-core_3/0.6.0/sonatype-central-client-core_3-0.6.0.pom" ];
    hash = "sha256-YEkjIQPfrhNzTOyy9wivwhFF/IRA8hBAHAkx+dt2Feg=";
    installPath = "https/repo1.maven.org/maven2/com/lumidion/sonatype-central-client-core_3/0.6.0";
  };

  "com.lumidion_sonatype-central-client-requests_3-0.6.0" = fetchMaven {
    name = "com.lumidion_sonatype-central-client-requests_3-0.6.0";
    urls = [ "https://repo1.maven.org/maven2/com/lumidion/sonatype-central-client-requests_3/0.6.0/sonatype-central-client-requests_3-0.6.0.jar" "https://repo1.maven.org/maven2/com/lumidion/sonatype-central-client-requests_3/0.6.0/sonatype-central-client-requests_3-0.6.0.pom" ];
    hash = "sha256-725YZk7xMwtpthJ/lf57xTXkEn+0njn3BdTaoV98nNI=";
    installPath = "https/repo1.maven.org/maven2/com/lumidion/sonatype-central-client-requests_3/0.6.0";
  };

  "com.lumidion_sonatype-central-client-upickle_3-0.6.0" = fetchMaven {
    name = "com.lumidion_sonatype-central-client-upickle_3-0.6.0";
    urls = [ "https://repo1.maven.org/maven2/com/lumidion/sonatype-central-client-upickle_3/0.6.0/sonatype-central-client-upickle_3-0.6.0.jar" "https://repo1.maven.org/maven2/com/lumidion/sonatype-central-client-upickle_3/0.6.0/sonatype-central-client-upickle_3-0.6.0.pom" ];
    hash = "sha256-26m05j/d1yXlIpECzcGNyDS4mZE5i9CkbGTmWYEmN3Y=";
    installPath = "https/repo1.maven.org/maven2/com/lumidion/sonatype-central-client-upickle_3/0.6.0";
  };

  "com.swoval_file-tree-views-2.1.12" = fetchMaven {
    name = "com.swoval_file-tree-views-2.1.12";
    urls = [ "https://repo1.maven.org/maven2/com/swoval/file-tree-views/2.1.12/file-tree-views-2.1.12.jar" "https://repo1.maven.org/maven2/com/swoval/file-tree-views/2.1.12/file-tree-views-2.1.12.pom" ];
    hash = "sha256-QhJJFQt5LS2THa8AyPLrj0suht4eCiAEl2sf7QsZU3I=";
    installPath = "https/repo1.maven.org/maven2/com/swoval/file-tree-views/2.1.12";
  };

  "guru.nidi_graphviz-java-0.18.1" = fetchMaven {
    name = "guru.nidi_graphviz-java-0.18.1";
    urls = [ "https://repo1.maven.org/maven2/guru/nidi/graphviz-java/0.18.1/graphviz-java-0.18.1.jar" "https://repo1.maven.org/maven2/guru/nidi/graphviz-java/0.18.1/graphviz-java-0.18.1.pom" ];
    hash = "sha256-Aq/u1Ss6/IoutsGna1uDSZ/+QNdDjfZmDZDh49OHNGs=";
    installPath = "https/repo1.maven.org/maven2/guru/nidi/graphviz-java/0.18.1";
  };

  "guru.nidi_graphviz-java-min-deps-0.18.1" = fetchMaven {
    name = "guru.nidi_graphviz-java-min-deps-0.18.1";
    urls = [ "https://repo1.maven.org/maven2/guru/nidi/graphviz-java-min-deps/0.18.1/graphviz-java-min-deps-0.18.1.jar" "https://repo1.maven.org/maven2/guru/nidi/graphviz-java-min-deps/0.18.1/graphviz-java-min-deps-0.18.1.pom" ];
    hash = "sha256-IJXiFPcJ1+FIHyUzXJWIWBecUp1qj2OcmQaHpUBpXKs=";
    installPath = "https/repo1.maven.org/maven2/guru/nidi/graphviz-java-min-deps/0.18.1";
  };

  "guru.nidi_graphviz-java-parent-0.18.1" = fetchMaven {
    name = "guru.nidi_graphviz-java-parent-0.18.1";
    urls = [ "https://repo1.maven.org/maven2/guru/nidi/graphviz-java-parent/0.18.1/graphviz-java-parent-0.18.1.pom" ];
    hash = "sha256-wjX7phg6GsC6AsCunFDeqktfCfHi0oW3WjsCNnBl8oQ=";
    installPath = "https/repo1.maven.org/maven2/guru/nidi/graphviz-java-parent/0.18.1";
  };

  "guru.nidi_guru-nidi-parent-pom-1.1.36" = fetchMaven {
    name = "guru.nidi_guru-nidi-parent-pom-1.1.36";
    urls = [ "https://repo1.maven.org/maven2/guru/nidi/guru-nidi-parent-pom/1.1.36/guru-nidi-parent-pom-1.1.36.pom" ];
    hash = "sha256-RywUAzcemtEHr3u6CYSTBA+NjVhrUilz8bM/EjOnBpE=";
    installPath = "https/repo1.maven.org/maven2/guru/nidi/guru-nidi-parent-pom/1.1.36";
  };

  "io.airlift_airbase-112" = fetchMaven {
    name = "io.airlift_airbase-112";
    urls = [ "https://repo1.maven.org/maven2/io/airlift/airbase/112/airbase-112.pom" ];
    hash = "sha256-I3jUuyAfPGbPcF0yDH+fa8l5rouZdvucoXg8tMLt174=";
    installPath = "https/repo1.maven.org/maven2/io/airlift/airbase/112";
  };

  "io.airlift_aircompressor-0.27" = fetchMaven {
    name = "io.airlift_aircompressor-0.27";
    urls = [ "https://repo1.maven.org/maven2/io/airlift/aircompressor/0.27/aircompressor-0.27.jar" "https://repo1.maven.org/maven2/io/airlift/aircompressor/0.27/aircompressor-0.27.pom" ];
    hash = "sha256-mxNNsdJ5O/jd2kv4pEyG7kFfnxLqrYDMPtxys0c1wuM=";
    installPath = "https/repo1.maven.org/maven2/io/airlift/aircompressor/0.27";
  };

  "io.get-coursier_cache-util-2.1.25-M23" = fetchMaven {
    name = "io.get-coursier_cache-util-2.1.25-M23";
    urls = [ "https://repo1.maven.org/maven2/io/get-coursier/cache-util/2.1.25-M23/cache-util-2.1.25-M23.jar" "https://repo1.maven.org/maven2/io/get-coursier/cache-util/2.1.25-M23/cache-util-2.1.25-M23.pom" ];
    hash = "sha256-QGAzpcjWOzI+tzQ7F0MF1dY4ITazvoU9aZz9F1FM1XA=";
    installPath = "https/repo1.maven.org/maven2/io/get-coursier/cache-util/2.1.25-M23";
  };

  "io.get-coursier_coursier-archive-cache_2.13-2.1.25-M23" = fetchMaven {
    name = "io.get-coursier_coursier-archive-cache_2.13-2.1.25-M23";
    urls = [ "https://repo1.maven.org/maven2/io/get-coursier/coursier-archive-cache_2.13/2.1.25-M23/coursier-archive-cache_2.13-2.1.25-M23.jar" "https://repo1.maven.org/maven2/io/get-coursier/coursier-archive-cache_2.13/2.1.25-M23/coursier-archive-cache_2.13-2.1.25-M23.pom" ];
    hash = "sha256-O0yhiSph7riEvtlZf/2dR/VPfJgELNczHdY8RXjeAMU=";
    installPath = "https/repo1.maven.org/maven2/io/get-coursier/coursier-archive-cache_2.13/2.1.25-M23";
  };

  "io.get-coursier_coursier-cache_2.13-2.1.25-M23" = fetchMaven {
    name = "io.get-coursier_coursier-cache_2.13-2.1.25-M23";
    urls = [ "https://repo1.maven.org/maven2/io/get-coursier/coursier-cache_2.13/2.1.25-M23/coursier-cache_2.13-2.1.25-M23.jar" "https://repo1.maven.org/maven2/io/get-coursier/coursier-cache_2.13/2.1.25-M23/coursier-cache_2.13-2.1.25-M23.pom" ];
    hash = "sha256-lB4HKuXT+0vVTp3lIPxnwOqjqdKX2dWyQAeUKU4+4io=";
    installPath = "https/repo1.maven.org/maven2/io/get-coursier/coursier-cache_2.13/2.1.25-M23";
  };

  "io.get-coursier_coursier-core_2.13-2.1.25-M23" = fetchMaven {
    name = "io.get-coursier_coursier-core_2.13-2.1.25-M23";
    urls = [ "https://repo1.maven.org/maven2/io/get-coursier/coursier-core_2.13/2.1.25-M23/coursier-core_2.13-2.1.25-M23.jar" "https://repo1.maven.org/maven2/io/get-coursier/coursier-core_2.13/2.1.25-M23/coursier-core_2.13-2.1.25-M23.pom" ];
    hash = "sha256-OXu7XjU1PSW7jYqKlUwDHMwxm1164Lpd3qAtPLaeNOY=";
    installPath = "https/repo1.maven.org/maven2/io/get-coursier/coursier-core_2.13/2.1.25-M23";
  };

  "io.get-coursier_coursier-env_2.13-2.1.25-M23" = fetchMaven {
    name = "io.get-coursier_coursier-env_2.13-2.1.25-M23";
    urls = [ "https://repo1.maven.org/maven2/io/get-coursier/coursier-env_2.13/2.1.25-M23/coursier-env_2.13-2.1.25-M23.jar" "https://repo1.maven.org/maven2/io/get-coursier/coursier-env_2.13/2.1.25-M23/coursier-env_2.13-2.1.25-M23.pom" ];
    hash = "sha256-5mVqL+H6CHZgiUiBhJD4AksewEybZX2qWw0QJwQX02Y=";
    installPath = "https/repo1.maven.org/maven2/io/get-coursier/coursier-env_2.13/2.1.25-M23";
  };

  "io.get-coursier_coursier-exec-2.1.25-M23" = fetchMaven {
    name = "io.get-coursier_coursier-exec-2.1.25-M23";
    urls = [ "https://repo1.maven.org/maven2/io/get-coursier/coursier-exec/2.1.25-M23/coursier-exec-2.1.25-M23.jar" "https://repo1.maven.org/maven2/io/get-coursier/coursier-exec/2.1.25-M23/coursier-exec-2.1.25-M23.pom" ];
    hash = "sha256-IJQIBHaX56KwZgOKBir+Kk5w1hGYKUXP+7I6W4CBCvk=";
    installPath = "https/repo1.maven.org/maven2/io/get-coursier/coursier-exec/2.1.25-M23";
  };

  "io.get-coursier_coursier-jvm_2.13-2.1.25-M23" = fetchMaven {
    name = "io.get-coursier_coursier-jvm_2.13-2.1.25-M23";
    urls = [ "https://repo1.maven.org/maven2/io/get-coursier/coursier-jvm_2.13/2.1.25-M23/coursier-jvm_2.13-2.1.25-M23.jar" "https://repo1.maven.org/maven2/io/get-coursier/coursier-jvm_2.13/2.1.25-M23/coursier-jvm_2.13-2.1.25-M23.pom" ];
    hash = "sha256-Ao/x0fcC6720B8vRDbozO6hYtr/Itw00l8ql38YE3+4=";
    installPath = "https/repo1.maven.org/maven2/io/get-coursier/coursier-jvm_2.13/2.1.25-M23";
  };

  "io.get-coursier_coursier-paths-2.1.25-M23" = fetchMaven {
    name = "io.get-coursier_coursier-paths-2.1.25-M23";
    urls = [ "https://repo1.maven.org/maven2/io/get-coursier/coursier-paths/2.1.25-M23/coursier-paths-2.1.25-M23.jar" "https://repo1.maven.org/maven2/io/get-coursier/coursier-paths/2.1.25-M23/coursier-paths-2.1.25-M23.pom" ];
    hash = "sha256-HSYySslDptxKuHYnY3F7J09PN5Wei9RozJlHAs4t01g=";
    installPath = "https/repo1.maven.org/maven2/io/get-coursier/coursier-paths/2.1.25-M23";
  };

  "io.get-coursier_coursier-proxy-setup-2.1.25-M23" = fetchMaven {
    name = "io.get-coursier_coursier-proxy-setup-2.1.25-M23";
    urls = [ "https://repo1.maven.org/maven2/io/get-coursier/coursier-proxy-setup/2.1.25-M23/coursier-proxy-setup-2.1.25-M23.jar" "https://repo1.maven.org/maven2/io/get-coursier/coursier-proxy-setup/2.1.25-M23/coursier-proxy-setup-2.1.25-M23.pom" ];
    hash = "sha256-+prWS9oinrhtQoOyR7mYe3nFr+38GQA45+ZTz7yMmv4=";
    installPath = "https/repo1.maven.org/maven2/io/get-coursier/coursier-proxy-setup/2.1.25-M23";
  };

  "io.get-coursier_coursier-util_2.13-2.1.25-M23" = fetchMaven {
    name = "io.get-coursier_coursier-util_2.13-2.1.25-M23";
    urls = [ "https://repo1.maven.org/maven2/io/get-coursier/coursier-util_2.13/2.1.25-M23/coursier-util_2.13-2.1.25-M23.jar" "https://repo1.maven.org/maven2/io/get-coursier/coursier-util_2.13/2.1.25-M23/coursier-util_2.13-2.1.25-M23.pom" ];
    hash = "sha256-K/mA9HNe+cOyUD7tr7KhuuTP9kxN6BnLBbWnA98+Ahk=";
    installPath = "https/repo1.maven.org/maven2/io/get-coursier/coursier-util_2.13/2.1.25-M23";
  };

  "io.get-coursier_coursier_2.13-2.1.25-M23" = fetchMaven {
    name = "io.get-coursier_coursier_2.13-2.1.25-M23";
    urls = [ "https://repo1.maven.org/maven2/io/get-coursier/coursier_2.13/2.1.25-M23/coursier_2.13-2.1.25-M23.jar" "https://repo1.maven.org/maven2/io/get-coursier/coursier_2.13/2.1.25-M23/coursier_2.13-2.1.25-M23.pom" ];
    hash = "sha256-f0Z7RxsTfR17eaTKJFlklUV+8CQdLVTar0FMrQd59YQ=";
    installPath = "https/repo1.maven.org/maven2/io/get-coursier/coursier_2.13/2.1.25-M23";
  };

  "io.get-coursier_dependency_2.13-0.3.2" = fetchMaven {
    name = "io.get-coursier_dependency_2.13-0.3.2";
    urls = [ "https://repo1.maven.org/maven2/io/get-coursier/dependency_2.13/0.3.2/dependency_2.13-0.3.2.jar" "https://repo1.maven.org/maven2/io/get-coursier/dependency_2.13/0.3.2/dependency_2.13-0.3.2.pom" ];
    hash = "sha256-kLCTLMEFNrY74GFqcr7Vw/qEbR7CPpskXrpzbbH0gMg=";
    installPath = "https/repo1.maven.org/maven2/io/get-coursier/dependency_2.13/0.3.2";
  };

  "io.get-coursier_versions_2.13-0.5.1" = fetchMaven {
    name = "io.get-coursier_versions_2.13-0.5.1";
    urls = [ "https://repo1.maven.org/maven2/io/get-coursier/versions_2.13/0.5.1/versions_2.13-0.5.1.jar" "https://repo1.maven.org/maven2/io/get-coursier/versions_2.13/0.5.1/versions_2.13-0.5.1.pom" ];
    hash = "sha256-1ryxcGeeUu18sLY4gL2cDVfOkh59oRPmNnIA0N2G1/Y=";
    installPath = "https/repo1.maven.org/maven2/io/get-coursier/versions_2.13/0.5.1";
  };

  "io.netty_netty-bom-4.2.6.Final" = fetchMaven {
    name = "io.netty_netty-bom-4.2.6.Final";
    urls = [ "https://repo1.maven.org/maven2/io/netty/netty-bom/4.2.6.Final/netty-bom-4.2.6.Final.pom" ];
    hash = "sha256-LI2naW3w8iAnNZz0g1YYzwkBVZtQwqy7veDssIPxcSk=";
    installPath = "https/repo1.maven.org/maven2/io/netty/netty-bom/4.2.6.Final";
  };

  "jakarta.platform_jakarta.jakartaee-bom-9.1.0" = fetchMaven {
    name = "jakarta.platform_jakarta.jakartaee-bom-9.1.0";
    urls = [ "https://repo1.maven.org/maven2/jakarta/platform/jakarta.jakartaee-bom/9.1.0/jakarta.jakartaee-bom-9.1.0.pom" ];
    hash = "sha256-kstGe15Yw9oF6LQ3Vovx1PcCUfQtNaEM7T8E5Upp1gg=";
    installPath = "https/repo1.maven.org/maven2/jakarta/platform/jakarta.jakartaee-bom/9.1.0";
  };

  "jakarta.platform_jakartaee-api-parent-9.1.0" = fetchMaven {
    name = "jakarta.platform_jakartaee-api-parent-9.1.0";
    urls = [ "https://repo1.maven.org/maven2/jakarta/platform/jakartaee-api-parent/9.1.0/jakartaee-api-parent-9.1.0.pom" ];
    hash = "sha256-FrD7N30UkkRSQtD3+FPOC1fH2qrNnJw6UZQ/hNFXWrA=";
    installPath = "https/repo1.maven.org/maven2/jakarta/platform/jakartaee-api-parent/9.1.0";
  };

  "javax.inject_javax.inject-1" = fetchMaven {
    name = "javax.inject_javax.inject-1";
    urls = [ "https://repo1.maven.org/maven2/javax/inject/javax.inject/1/javax.inject-1.jar" "https://repo1.maven.org/maven2/javax/inject/javax.inject/1/javax.inject-1.pom" ];
    hash = "sha256-CZm6Lb7D5az8nprqBvjNerGQjB0xPaY56/RvKwSZIxE=";
    installPath = "https/repo1.maven.org/maven2/javax/inject/javax.inject/1";
  };

  "net.openhft_java-parent-pom-1.1.28" = fetchMaven {
    name = "net.openhft_java-parent-pom-1.1.28";
    urls = [ "https://repo1.maven.org/maven2/net/openhft/java-parent-pom/1.1.28/java-parent-pom-1.1.28.pom" ];
    hash = "sha256-d7bOKP/hHJElmDQtIbblYDHRc8LCpqkt5Zl8aHp7l88=";
    installPath = "https/repo1.maven.org/maven2/net/openhft/java-parent-pom/1.1.28";
  };

  "net.openhft_root-parent-pom-1.2.12" = fetchMaven {
    name = "net.openhft_root-parent-pom-1.2.12";
    urls = [ "https://repo1.maven.org/maven2/net/openhft/root-parent-pom/1.2.12/root-parent-pom-1.2.12.pom" ];
    hash = "sha256-D/M1qN+njmMZWqS5h27fl83Q+zWgIFjaYQkCpD2Oy/M=";
    installPath = "https/repo1.maven.org/maven2/net/openhft/root-parent-pom/1.2.12";
  };

  "net.openhft_zero-allocation-hashing-0.16" = fetchMaven {
    name = "net.openhft_zero-allocation-hashing-0.16";
    urls = [ "https://repo1.maven.org/maven2/net/openhft/zero-allocation-hashing/0.16/zero-allocation-hashing-0.16.jar" "https://repo1.maven.org/maven2/net/openhft/zero-allocation-hashing/0.16/zero-allocation-hashing-0.16.pom" ];
    hash = "sha256-QkNOGkyP/OFWM+pv40hqR+ii4GBAcv0bbIrpG66YDMo=";
    installPath = "https/repo1.maven.org/maven2/net/openhft/zero-allocation-hashing/0.16";
  };

  "nl.big-o_liqp-0.8.2" = fetchMaven {
    name = "nl.big-o_liqp-0.8.2";
    urls = [ "https://repo1.maven.org/maven2/nl/big-o/liqp/0.8.2/liqp-0.8.2.jar" "https://repo1.maven.org/maven2/nl/big-o/liqp/0.8.2/liqp-0.8.2.pom" ];
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
    urls = [ "https://repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.7.2/antlr4-runtime-4.7.2.jar" "https://repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.7.2/antlr4-runtime-4.7.2.pom" ];
    hash = "sha256-orSo+dX/By8iQ7guGqi/mScUKmFeAp2TizPRFWLVUvY=";
    installPath = "https/repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.7.2";
  };

  "org.apache_apache-13" = fetchMaven {
    name = "org.apache_apache-13";
    urls = [ "https://repo1.maven.org/maven2/org/apache/apache/13/apache-13.pom" ];
    hash = "sha256-sACBC2XyW8OQOMbX09EPCVL/lqUvROHaHHHiQ3XpTk4=";
    installPath = "https/repo1.maven.org/maven2/org/apache/apache/13";
  };

  "org.apache_apache-19" = fetchMaven {
    name = "org.apache_apache-19";
    urls = [ "https://repo1.maven.org/maven2/org/apache/apache/19/apache-19.pom" ];
    hash = "sha256-zhBKa7d1483sjfmn+XnLUQgYZltXXBPJayIZ44PcKHo=";
    installPath = "https/repo1.maven.org/maven2/org/apache/apache/19";
  };

  "org.apache_apache-31" = fetchMaven {
    name = "org.apache_apache-31";
    urls = [ "https://repo1.maven.org/maven2/org/apache/apache/31/apache-31.pom" ];
    hash = "sha256-Evktp+xRZ2C/VvG0UDTcFRSEvvSJINCtIe0Rom2159s=";
    installPath = "https/repo1.maven.org/maven2/org/apache/apache/31";
  };

  "org.apache_apache-35" = fetchMaven {
    name = "org.apache_apache-35";
    urls = [ "https://repo1.maven.org/maven2/org/apache/apache/35/apache-35.pom" ];
    hash = "sha256-Xi9qlMJKcB7Oc/RDG74Xmum5LLz6PVSIREBESM2qPbQ=";
    installPath = "https/repo1.maven.org/maven2/org/apache/apache/35";
  };

  "org.apache_apache-6" = fetchMaven {
    name = "org.apache_apache-6";
    urls = [ "https://repo1.maven.org/maven2/org/apache/apache/6/apache-6.pom" ];
    hash = "sha256-A7aDRlGjS4P3/QlZmvMRdVHhP4yqTFL4wZbRnp1lJ9U=";
    installPath = "https/repo1.maven.org/maven2/org/apache/apache/6";
  };

  "org.checkerframework_checker-qual-3.5.0" = fetchMaven {
    name = "org.checkerframework_checker-qual-3.5.0";
    urls = [ "https://repo1.maven.org/maven2/org/checkerframework/checker-qual/3.5.0/checker-qual-3.5.0.jar" "https://repo1.maven.org/maven2/org/checkerframework/checker-qual/3.5.0/checker-qual-3.5.0.pom" ];
    hash = "sha256-7lxlpTC52iRt4ZSq/jCM3ohl9uRC4V8WTpNF+DLWZrU=";
    installPath = "https/repo1.maven.org/maven2/org/checkerframework/checker-qual/3.5.0";
  };

  "org.fusesource_fusesource-pom-1.12" = fetchMaven {
    name = "org.fusesource_fusesource-pom-1.12";
    urls = [ "https://repo1.maven.org/maven2/org/fusesource/fusesource-pom/1.12/fusesource-pom-1.12.pom" ];
    hash = "sha256-NUD5PZ1FYYOq8yumvT5i29Vxd2ZCI6PXImXfLe4mE30=";
    installPath = "https/repo1.maven.org/maven2/org/fusesource/fusesource-pom/1.12";
  };

  "org.jetbrains_annotations-15.0" = fetchMaven {
    name = "org.jetbrains_annotations-15.0";
    urls = [ "https://repo1.maven.org/maven2/org/jetbrains/annotations/15.0/annotations-15.0.jar" "https://repo1.maven.org/maven2/org/jetbrains/annotations/15.0/annotations-15.0.pom" ];
    hash = "sha256-zKx9CDgM9iLkt5SFNiSgDzJu9AxFNPjCFWwMi9copnI=";
    installPath = "https/repo1.maven.org/maven2/org/jetbrains/annotations/15.0";
  };

  "org.jgrapht_jgrapht-1.4.0" = fetchMaven {
    name = "org.jgrapht_jgrapht-1.4.0";
    urls = [ "https://repo1.maven.org/maven2/org/jgrapht/jgrapht/1.4.0/jgrapht-1.4.0.pom" ];
    hash = "sha256-0bLt1jNIcVaLnLF7J/UT53p9nsmR1nSB3zKHCCc9xY0=";
    installPath = "https/repo1.maven.org/maven2/org/jgrapht/jgrapht/1.4.0";
  };

  "org.jgrapht_jgrapht-core-1.4.0" = fetchMaven {
    name = "org.jgrapht_jgrapht-core-1.4.0";
    urls = [ "https://repo1.maven.org/maven2/org/jgrapht/jgrapht-core/1.4.0/jgrapht-core-1.4.0.jar" "https://repo1.maven.org/maven2/org/jgrapht/jgrapht-core/1.4.0/jgrapht-core-1.4.0.pom" ];
    hash = "sha256-SDTdRtbcTa+IsuGfLrnZjaDdEkUaxolSHKwTM1uyKII=";
    installPath = "https/repo1.maven.org/maven2/org/jgrapht/jgrapht-core/1.4.0";
  };

  "org.jheaps_jheaps-0.11" = fetchMaven {
    name = "org.jheaps_jheaps-0.11";
    urls = [ "https://repo1.maven.org/maven2/org/jheaps/jheaps/0.11/jheaps-0.11.jar" "https://repo1.maven.org/maven2/org/jheaps/jheaps/0.11/jheaps-0.11.pom" ];
    hash = "sha256-LtDxiqSoClaeIxb0UUbh79Y8HiSHrgyVLCMhQSjRDUo=";
    installPath = "https/repo1.maven.org/maven2/org/jheaps/jheaps/0.11";
  };

  "org.jline_jline-3.30.6" = fetchMaven {
    name = "org.jline_jline-3.30.6";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline/3.30.6/jline-3.30.6.jar" "https://repo1.maven.org/maven2/org/jline/jline/3.30.6/jline-3.30.6.pom" ];
    hash = "sha256-yRgDgwfAr4H3oczFo1z4ktlZxAYxqQplQxvch51ef0w=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline/3.30.6";
  };

  "org.jline_jline-native-3.27.0" = fetchMaven {
    name = "org.jline_jline-native-3.27.0";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-native/3.27.0/jline-native-3.27.0.jar" "https://repo1.maven.org/maven2/org/jline/jline-native/3.27.0/jline-native-3.27.0.pom" ];
    hash = "sha256-O/J+a5uNpfdl2iYqzl0POxqp0CtLNjdWJw9LaxV82GY=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-native/3.27.0";
  };

  "org.jline_jline-native-3.27.1" = fetchMaven {
    name = "org.jline_jline-native-3.27.1";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-native/3.27.1/jline-native-3.27.1.jar" "https://repo1.maven.org/maven2/org/jline/jline-native/3.27.1/jline-native-3.27.1.pom" ];
    hash = "sha256-XyhCZMcwu/OXdQ8BTM+qGgjGzMano5DJoghn1+/yr+Q=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-native/3.27.1";
  };

  "org.jline_jline-native-3.29.0" = fetchMaven {
    name = "org.jline_jline-native-3.29.0";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-native/3.29.0/jline-native-3.29.0.jar" "https://repo1.maven.org/maven2/org/jline/jline-native/3.29.0/jline-native-3.29.0.pom" ];
    hash = "sha256-B4uPEOoZQdIyvNzjJBxzr+9m6E0Q95p2l/0iyYpz62Y=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-native/3.29.0";
  };

  "org.jline_jline-parent-3.27.0" = fetchMaven {
    name = "org.jline_jline-parent-3.27.0";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-parent/3.27.0/jline-parent-3.27.0.pom" ];
    hash = "sha256-Oh7mRotS8yhoU5Nxigu+kfgUDILIgwtkQXCkvE1JesQ=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-parent/3.27.0";
  };

  "org.jline_jline-parent-3.27.1" = fetchMaven {
    name = "org.jline_jline-parent-3.27.1";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-parent/3.27.1/jline-parent-3.27.1.pom" ];
    hash = "sha256-Oa5DgBvf5JwZH68PDIyNkEQtm7IL04ujoeniH6GZas8=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-parent/3.27.1";
  };

  "org.jline_jline-parent-3.29.0" = fetchMaven {
    name = "org.jline_jline-parent-3.29.0";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-parent/3.29.0/jline-parent-3.29.0.pom" ];
    hash = "sha256-oxKMIwjIJO0c7pcRwCh1deR9MT5oIjEQD5xiDZzCLNg=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-parent/3.29.0";
  };

  "org.jline_jline-reader-3.27.0" = fetchMaven {
    name = "org.jline_jline-reader-3.27.0";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-reader/3.27.0/jline-reader-3.27.0.jar" "https://repo1.maven.org/maven2/org/jline/jline-reader/3.27.0/jline-reader-3.27.0.pom" ];
    hash = "sha256-f/b+aWF/tmHedVWseL7hDjA4vJUrllPGnNhZH81S3Qg=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-reader/3.27.0";
  };

  "org.jline_jline-reader-3.29.0" = fetchMaven {
    name = "org.jline_jline-reader-3.29.0";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-reader/3.29.0/jline-reader-3.29.0.jar" "https://repo1.maven.org/maven2/org/jline/jline-reader/3.29.0/jline-reader-3.29.0.pom" ];
    hash = "sha256-29VGA1VapJEewqh+CohsyXSpXkH7It/GwcAK0Z4igbo=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-reader/3.29.0";
  };

  "org.jline_jline-terminal-3.27.0" = fetchMaven {
    name = "org.jline_jline-terminal-3.27.0";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-terminal/3.27.0/jline-terminal-3.27.0.jar" "https://repo1.maven.org/maven2/org/jline/jline-terminal/3.27.0/jline-terminal-3.27.0.pom" ];
    hash = "sha256-hkKgeb/Kl1hlTC9TMNkujoBwY+QS4e6ESUK9kdEbcGE=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-terminal/3.27.0";
  };

  "org.jline_jline-terminal-3.27.1" = fetchMaven {
    name = "org.jline_jline-terminal-3.27.1";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-terminal/3.27.1/jline-terminal-3.27.1.jar" "https://repo1.maven.org/maven2/org/jline/jline-terminal/3.27.1/jline-terminal-3.27.1.pom" ];
    hash = "sha256-WV77BAEncauTljUBrlYi9v3GxDDeskqQpHHD9Fdbqjw=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-terminal/3.27.1";
  };

  "org.jline_jline-terminal-3.29.0" = fetchMaven {
    name = "org.jline_jline-terminal-3.29.0";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-terminal/3.29.0/jline-terminal-3.29.0.jar" "https://repo1.maven.org/maven2/org/jline/jline-terminal/3.29.0/jline-terminal-3.29.0.pom" ];
    hash = "sha256-VSLgLannbVTHJdbWjmHtvPlbqRHgN67iVGQhd6GdBrI=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-terminal/3.29.0";
  };

  "org.jline_jline-terminal-jna-3.27.0" = fetchMaven {
    name = "org.jline_jline-terminal-jna-3.27.0";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-terminal-jna/3.27.0/jline-terminal-jna-3.27.0.jar" "https://repo1.maven.org/maven2/org/jline/jline-terminal-jna/3.27.0/jline-terminal-jna-3.27.0.pom" ];
    hash = "sha256-Pw8jJMTTBpAKxfU9gp2EN2CfVHpRpC0YJorCu/9UDsI=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-terminal-jna/3.27.0";
  };

  "org.jline_jline-terminal-jni-3.27.1" = fetchMaven {
    name = "org.jline_jline-terminal-jni-3.27.1";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-terminal-jni/3.27.1/jline-terminal-jni-3.27.1.jar" "https://repo1.maven.org/maven2/org/jline/jline-terminal-jni/3.27.1/jline-terminal-jni-3.27.1.pom" ];
    hash = "sha256-AWKC7imb/rnF39PAo3bVIW430zPkyj9WozKGkPlTTBE=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-terminal-jni/3.27.1";
  };

  "org.jline_jline-terminal-jni-3.29.0" = fetchMaven {
    name = "org.jline_jline-terminal-jni-3.29.0";
    urls = [ "https://repo1.maven.org/maven2/org/jline/jline-terminal-jni/3.29.0/jline-terminal-jni-3.29.0.jar" "https://repo1.maven.org/maven2/org/jline/jline-terminal-jni/3.29.0/jline-terminal-jni-3.29.0.pom" ];
    hash = "sha256-P5vIZblvy8nL21syHeJMNJEip/JVsVkislqKbh6ffyA=";
    installPath = "https/repo1.maven.org/maven2/org/jline/jline-terminal-jni/3.29.0";
  };

  "org.jsoup_jsoup-1.17.2" = fetchMaven {
    name = "org.jsoup_jsoup-1.17.2";
    urls = [ "https://repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar" "https://repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.pom" ];
    hash = "sha256-aex/2xWBJBV0CVGOIoNvOcnYi6sVTd3CwBJhM5ZUISU=";
    installPath = "https/repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2";
  };

  "org.junit_junit-bom-5.10.2" = fetchMaven {
    name = "org.junit_junit-bom-5.10.2";
    urls = [ "https://repo1.maven.org/maven2/org/junit/junit-bom/5.10.2/junit-bom-5.10.2.pom" ];
    hash = "sha256-AlDFqi7NIm0J1UoA6JCUM3Rhq5cNwsXq/I8viZmWLEg=";
    installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.10.2";
  };

  "org.junit_junit-bom-5.13.1" = fetchMaven {
    name = "org.junit_junit-bom-5.13.1";
    urls = [ "https://repo1.maven.org/maven2/org/junit/junit-bom/5.13.1/junit-bom-5.13.1.pom" ];
    hash = "sha256-y0fYl6j3V74Ioxxiq2/0Riiw4VDt7XG6YR/Ekd7wKDg=";
    installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.13.1";
  };

  "org.junit_junit-bom-5.13.2" = fetchMaven {
    name = "org.junit_junit-bom-5.13.2";
    urls = [ "https://repo1.maven.org/maven2/org/junit/junit-bom/5.13.2/junit-bom-5.13.2.pom" ];
    hash = "sha256-7tgy2u9U7G1G3A8bXLq3vl23h0NzJNsXtELQv3s6idg=";
    installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.13.2";
  };

  "org.junit_junit-bom-5.13.4" = fetchMaven {
    name = "org.junit_junit-bom-5.13.4";
    urls = [ "https://repo1.maven.org/maven2/org/junit/junit-bom/5.13.4/junit-bom-5.13.4.pom" ];
    hash = "sha256-uMvXRj2IJjctssr3Twwzn/xTriNqj8Wl3QeIeCzgHwE=";
    installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.13.4";
  };

  "org.junit_junit-bom-5.14.2" = fetchMaven {
    name = "org.junit_junit-bom-5.14.2";
    urls = [ "https://repo1.maven.org/maven2/org/junit/junit-bom/5.14.2/junit-bom-5.14.2.pom" ];
    hash = "sha256-DT2QOj+abniTqGo5bwp+9wBoMADOPzAOo0+v7UYiMyk=";
    installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.14.2";
  };

  "org.junit_junit-bom-5.8.0-M1" = fetchMaven {
    name = "org.junit_junit-bom-5.8.0-M1";
    urls = [ "https://repo1.maven.org/maven2/org/junit/junit-bom/5.8.0-M1/junit-bom-5.8.0-M1.pom" ];
    hash = "sha256-3suC6i7s+f+GrkY/p8I8TXqZnkP6Vz5/iHplYFZPIk4=";
    installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.8.0-M1";
  };

  "org.junit_junit-bom-5.9.2" = fetchMaven {
    name = "org.junit_junit-bom-5.9.2";
    urls = [ "https://repo1.maven.org/maven2/org/junit/junit-bom/5.9.2/junit-bom-5.9.2.pom" ];
    hash = "sha256-uGn68+1/ScKIRXjMgUllMofOsjFTxO1mfwrpSVBpP6E=";
    installPath = "https/repo1.maven.org/maven2/org/junit/junit-bom/5.9.2";
  };

  "org.mockito_mockito-bom-4.11.0" = fetchMaven {
    name = "org.mockito_mockito-bom-4.11.0";
    urls = [ "https://repo1.maven.org/maven2/org/mockito/mockito-bom/4.11.0/mockito-bom-4.11.0.pom" ];
    hash = "sha256-jtuaGRrHXNkevtfBAzk3OA+n5RNtrDQ0MQSqSRxUIfc=";
    installPath = "https/repo1.maven.org/maven2/org/mockito/mockito-bom/4.11.0";
  };

  "org.ow2_ow2-1.5.1" = fetchMaven {
    name = "org.ow2_ow2-1.5.1";
    urls = [ "https://repo1.maven.org/maven2/org/ow2/ow2/1.5.1/ow2-1.5.1.pom" ];
    hash = "sha256-4F8xYVbQg2PG/GhDEdcvENureaBF1yT/hSdLimkz5ks=";
    installPath = "https/repo1.maven.org/maven2/org/ow2/ow2/1.5.1";
  };

  "org.scala-lang_scala-library-2.13.15" = fetchMaven {
    name = "org.scala-lang_scala-library-2.13.15";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.15/scala-library-2.13.15.jar" "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.15/scala-library-2.13.15.pom" ];
    hash = "sha256-JnbDGZQKZZswRZuxauQywH/4rXzwzn++kMB4lw3OfPI=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.15";
  };

  "org.scala-lang_scala-library-2.13.16" = fetchMaven {
    name = "org.scala-lang_scala-library-2.13.16";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar" "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.pom" ];
    hash = "sha256-7/NvAxKKPtghJ/+pTNxvmIAiAdtQXRTUvDwGGXwpnpU=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16";
  };

  "org.scala-lang_scala-library-3.8.1" = fetchMaven {
    name = "org.scala-lang_scala-library-3.8.1";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala-library/3.8.1/scala-library-3.8.1.jar" "https://repo1.maven.org/maven2/org/scala-lang/scala-library/3.8.1/scala-library-3.8.1.pom" ];
    hash = "sha256-leW7FdBEEuKu5JO2w7YBY6mSP8RycQXPyZr3tVE1ByE=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-library/3.8.1";
  };

  "org.scala-lang_scala-reflect-2.13.18" = fetchMaven {
    name = "org.scala-lang_scala-reflect-2.13.18";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.18/scala-reflect-2.13.18.jar" "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.18/scala-reflect-2.13.18.pom" ];
    hash = "sha256-NUxw12IP7v+wF4LMxX3rfxlC+G6BTHcrBDrRZlHaWZU=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.18";
  };

  "org.scala-lang_scala3-compiler_3-3.6.2" = fetchMaven {
    name = "org.scala-lang_scala3-compiler_3-3.6.2";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala3-compiler_3/3.6.2/scala3-compiler_3-3.6.2.jar" "https://repo1.maven.org/maven2/org/scala-lang/scala3-compiler_3/3.6.2/scala3-compiler_3-3.6.2.pom" ];
    hash = "sha256-4wUykwZ0qw8x1WH5xNJwCP52I7326oM2XtWjeoDir+Y=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-compiler_3/3.6.2";
  };

  "org.scala-lang_scala3-compiler_3-3.8.1" = fetchMaven {
    name = "org.scala-lang_scala3-compiler_3-3.8.1";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala3-compiler_3/3.8.1/scala3-compiler_3-3.8.1.jar" "https://repo1.maven.org/maven2/org/scala-lang/scala3-compiler_3/3.8.1/scala3-compiler_3-3.8.1.pom" ];
    hash = "sha256-HXC0RuDoMn1ZuAlVJb1g0BBxr5aTNouY4/o/C75bVg8=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-compiler_3/3.8.1";
  };

  "org.scala-lang_scala3-interfaces-3.6.2" = fetchMaven {
    name = "org.scala-lang_scala3-interfaces-3.6.2";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala3-interfaces/3.6.2/scala3-interfaces-3.6.2.jar" "https://repo1.maven.org/maven2/org/scala-lang/scala3-interfaces/3.6.2/scala3-interfaces-3.6.2.pom" ];
    hash = "sha256-MQJryHiMDS2t2CsumeP0ciN9Q9ehju7Fh7jBpq4c+D0=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-interfaces/3.6.2";
  };

  "org.scala-lang_scala3-interfaces-3.8.1" = fetchMaven {
    name = "org.scala-lang_scala3-interfaces-3.8.1";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala3-interfaces/3.8.1/scala3-interfaces-3.8.1.jar" "https://repo1.maven.org/maven2/org/scala-lang/scala3-interfaces/3.8.1/scala3-interfaces-3.8.1.pom" ];
    hash = "sha256-A5FKw9xy575jFFaaaSQ5JZk0VwhhZO0w9oabsjra7FE=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-interfaces/3.8.1";
  };

  "org.scala-lang_scala3-library_3-3.3.3" = fetchMaven {
    name = "org.scala-lang_scala3-library_3-3.3.3";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.3.3/scala3-library_3-3.3.3.pom" ];
    hash = "sha256-i3C7/n+22pAbQ2xIxloGNiyrrKAsYI5z4xJ0lpNet98=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.3.3";
  };

  "org.scala-lang_scala3-library_3-3.6.2" = fetchMaven {
    name = "org.scala-lang_scala3-library_3-3.6.2";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.6.2/scala3-library_3-3.6.2.jar" "https://repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.6.2/scala3-library_3-3.6.2.pom" ];
    hash = "sha256-eBeOgZMRztMgvVXj9zFkXwPRxDgmkiBSxMtzoFxUOiU=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.6.2";
  };

  "org.scala-lang_scala3-library_3-3.7.4" = fetchMaven {
    name = "org.scala-lang_scala3-library_3-3.7.4";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.7.4/scala3-library_3-3.7.4.jar" "https://repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.7.4/scala3-library_3-3.7.4.pom" ];
    hash = "sha256-n96MbSjNHeFV9QaEinPQhEZyRvFuYIAU0o9iSSlkmyA=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.7.4";
  };

  "org.scala-lang_scala3-library_3-3.8.1" = fetchMaven {
    name = "org.scala-lang_scala3-library_3-3.8.1";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.8.1/scala3-library_3-3.8.1.jar" "https://repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.8.1/scala3-library_3-3.8.1.pom" ];
    hash = "sha256-u0vn2w5YNjqDOYbMftZSwG71uUYNwKq1YKn1Z5CyuRw=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.8.1";
  };

  "org.scala-lang_scala3-repl_3-3.8.1" = fetchMaven {
    name = "org.scala-lang_scala3-repl_3-3.8.1";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala3-repl_3/3.8.1/scala3-repl_3-3.8.1.jar" "https://repo1.maven.org/maven2/org/scala-lang/scala3-repl_3/3.8.1/scala3-repl_3-3.8.1.pom" ];
    hash = "sha256-qM6wxr2NoYkP15Ck0cm8pk64k2LNnbb7juKGcX+a/Ko=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-repl_3/3.8.1";
  };

  "org.scala-lang_scala3-sbt-bridge-3.6.2" = fetchMaven {
    name = "org.scala-lang_scala3-sbt-bridge-3.6.2";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala3-sbt-bridge/3.6.2/scala3-sbt-bridge-3.6.2.jar" "https://repo1.maven.org/maven2/org/scala-lang/scala3-sbt-bridge/3.6.2/scala3-sbt-bridge-3.6.2.pom" ];
    hash = "sha256-ouyRb3uSwsyJZkQkVqfY0ECascZSRBkzCSpi5F1GQG0=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-sbt-bridge/3.6.2";
  };

  "org.scala-lang_scala3-sbt-bridge-3.8.1" = fetchMaven {
    name = "org.scala-lang_scala3-sbt-bridge-3.8.1";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala3-sbt-bridge/3.8.1/scala3-sbt-bridge-3.8.1.jar" "https://repo1.maven.org/maven2/org/scala-lang/scala3-sbt-bridge/3.8.1/scala3-sbt-bridge-3.8.1.pom" ];
    hash = "sha256-Tz7nfuRbf03hFFLd1o/DoKdtk4cofXd3Dj0RZ/InY+U=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-sbt-bridge/3.8.1";
  };

  "org.scala-lang_scala3-tasty-inspector_3-3.6.2" = fetchMaven {
    name = "org.scala-lang_scala3-tasty-inspector_3-3.6.2";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scala3-tasty-inspector_3/3.6.2/scala3-tasty-inspector_3-3.6.2.jar" "https://repo1.maven.org/maven2/org/scala-lang/scala3-tasty-inspector_3/3.6.2/scala3-tasty-inspector_3-3.6.2.pom" ];
    hash = "sha256-PxXdYKIHwHoMfldTzPq7PqtQF+1YzPqh0Ma2ue70yxE=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scala3-tasty-inspector_3/3.6.2";
  };

  "org.scala-lang_scaladoc_3-3.6.2" = fetchMaven {
    name = "org.scala-lang_scaladoc_3-3.6.2";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/scaladoc_3/3.6.2/scaladoc_3-3.6.2.jar" "https://repo1.maven.org/maven2/org/scala-lang/scaladoc_3/3.6.2/scaladoc_3-3.6.2.pom" ];
    hash = "sha256-BxzI0JCs9p8INscqFDoOV9HJIB+YM7EI0mCel7KoeBU=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/scaladoc_3/3.6.2";
  };

  "org.scala-lang_tasty-core_3-3.6.2" = fetchMaven {
    name = "org.scala-lang_tasty-core_3-3.6.2";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/tasty-core_3/3.6.2/tasty-core_3-3.6.2.jar" "https://repo1.maven.org/maven2/org/scala-lang/tasty-core_3/3.6.2/tasty-core_3-3.6.2.pom" ];
    hash = "sha256-StXvfqkjPpP18knWBc0UZv0bqzViP2FxFGMc5A1wQxw=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/tasty-core_3/3.6.2";
  };

  "org.scala-lang_tasty-core_3-3.8.1" = fetchMaven {
    name = "org.scala-lang_tasty-core_3-3.8.1";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/tasty-core_3/3.8.1/tasty-core_3-3.8.1.jar" "https://repo1.maven.org/maven2/org/scala-lang/tasty-core_3/3.8.1/tasty-core_3-3.8.1.pom" ];
    hash = "sha256-cEmAVaHwFiFqXtpYMKYgDI9Ae9m6riihjGzHNbOMqVQ=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/tasty-core_3/3.8.1";
  };

  "org.scala-sbt_compiler-interface-1.10.7" = fetchMaven {
    name = "org.scala-sbt_compiler-interface-1.10.7";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.7/compiler-interface-1.10.7.jar" "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.7/compiler-interface-1.10.7.pom" ];
    hash = "sha256-nFVs4vEVTEPSiGce3C77TTjvffSU+SMrn9KgV9xGVP0=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.10.7";
  };

  "org.scala-sbt_compiler-interface-1.9.6" = fetchMaven {
    name = "org.scala-sbt_compiler-interface-1.9.6";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.9.6/compiler-interface-1.9.6.jar" "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.9.6/compiler-interface-1.9.6.pom" ];
    hash = "sha256-spep2us0CWZiButV6u4/nJyRqQozTEuo83z0CR/5cos=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/compiler-interface/1.9.6";
  };

  "org.scala-sbt_compiler-interface-2.0.0-M13" = fetchMaven {
    name = "org.scala-sbt_compiler-interface-2.0.0-M13";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/2.0.0-M13/compiler-interface-2.0.0-M13.jar" "https://repo1.maven.org/maven2/org/scala-sbt/compiler-interface/2.0.0-M13/compiler-interface-2.0.0-M13.pom" ];
    hash = "sha256-Vm7HxRLG5adN2PufYxbSURHFSq4vhcvzOcX0UI9ieHM=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/compiler-interface/2.0.0-M13";
  };

  "org.scala-sbt_io_3-1.10.5" = fetchMaven {
    name = "org.scala-sbt_io_3-1.10.5";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/io_3/1.10.5/io_3-1.10.5.jar" "https://repo1.maven.org/maven2/org/scala-sbt/io_3/1.10.5/io_3-1.10.5.pom" ];
    hash = "sha256-NlI2nqJd/cCVmbk+Qgv5EQ0sF+Vb3+r/ueQbUt43PPQ=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/io_3/1.10.5";
  };

  "org.scala-sbt_launcher-interface-1.5.2" = fetchMaven {
    name = "org.scala-sbt_launcher-interface-1.5.2";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/launcher-interface/1.5.2/launcher-interface-1.5.2.jar" "https://repo1.maven.org/maven2/org/scala-sbt/launcher-interface/1.5.2/launcher-interface-1.5.2.pom" ];
    hash = "sha256-6MKDhiypKx/Blnx11u6U5M+7JRobVIux55QiLAoNeyg=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/launcher-interface/1.5.2";
  };

  "org.scala-sbt_sbinary_3-0.5.1" = fetchMaven {
    name = "org.scala-sbt_sbinary_3-0.5.1";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/sbinary_3/0.5.1/sbinary_3-0.5.1.jar" "https://repo1.maven.org/maven2/org/scala-sbt/sbinary_3/0.5.1/sbinary_3-0.5.1.pom" ];
    hash = "sha256-tvZ+cEHn/1t9DEE5Q2RepIFyc1wMMpldIiXJxqDhMU8=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/sbinary_3/0.5.1";
  };

  "org.scala-sbt_test-interface-1.0" = fetchMaven {
    name = "org.scala-sbt_test-interface-1.0";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/test-interface/1.0/test-interface-1.0.jar" "https://repo1.maven.org/maven2/org/scala-sbt/test-interface/1.0/test-interface-1.0.pom" ];
    hash = "sha256-Cc5Q+4mULLHRdw+7Wjx6spCLbKrckXHeNYjIibw4LWw=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/test-interface/1.0";
  };

  "org.scala-sbt_util-control_3-2.0.0-RC8" = fetchMaven {
    name = "org.scala-sbt_util-control_3-2.0.0-RC8";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/util-control_3/2.0.0-RC8/util-control_3-2.0.0-RC8.jar" "https://repo1.maven.org/maven2/org/scala-sbt/util-control_3/2.0.0-RC8/util-control_3-2.0.0-RC8.pom" ];
    hash = "sha256-VUduUBpbhnbb8DCEXtRYKwd2iWT++ZqBy1Hnu3ulQC8=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-control_3/2.0.0-RC8";
  };

  "org.scala-sbt_util-core_3-2.0.0-RC8" = fetchMaven {
    name = "org.scala-sbt_util-core_3-2.0.0-RC8";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/util-core_3/2.0.0-RC8/util-core_3-2.0.0-RC8.jar" "https://repo1.maven.org/maven2/org/scala-sbt/util-core_3/2.0.0-RC8/util-core_3-2.0.0-RC8.pom" ];
    hash = "sha256-q5wa9sNGvZ8iYHBEAIf2MXWpthmFmZsUj++M30H6Kxc=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-core_3/2.0.0-RC8";
  };

  "org.scala-sbt_util-interface-1.10.7" = fetchMaven {
    name = "org.scala-sbt_util-interface-1.10.7";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.7/util-interface-1.10.7.jar" "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.7/util-interface-1.10.7.pom" ];
    hash = "sha256-cIOD5+vCDptOP6jwds5yG+23h2H54npBzGu3jrCQlvQ=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-interface/1.10.7";
  };

  "org.scala-sbt_util-interface-1.9.8" = fetchMaven {
    name = "org.scala-sbt_util-interface-1.9.8";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.9.8/util-interface-1.9.8.jar" "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/1.9.8/util-interface-1.9.8.pom" ];
    hash = "sha256-7PoE3Jj8JSBaNeK3IzCSlkwArEWP1Zo+XBn0OorE1I8=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-interface/1.9.8";
  };

  "org.scala-sbt_util-interface-2.0.0-RC8" = fetchMaven {
    name = "org.scala-sbt_util-interface-2.0.0-RC8";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/2.0.0-RC8/util-interface-2.0.0-RC8.jar" "https://repo1.maven.org/maven2/org/scala-sbt/util-interface/2.0.0-RC8/util-interface-2.0.0-RC8.pom" ];
    hash = "sha256-S2NnUCakCYMY3bcOtZOSB+yU9bbtmYGZx5rOtwWWN98=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-interface/2.0.0-RC8";
  };

  "org.scala-sbt_util-logging_3-2.0.0-RC8" = fetchMaven {
    name = "org.scala-sbt_util-logging_3-2.0.0-RC8";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/util-logging_3/2.0.0-RC8/util-logging_3-2.0.0-RC8.jar" "https://repo1.maven.org/maven2/org/scala-sbt/util-logging_3/2.0.0-RC8/util-logging_3-2.0.0-RC8.pom" ];
    hash = "sha256-Ob/I3T/oMKIuzBd3cNYkGBZscLDGMfq3nYenCEu36Ts=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-logging_3/2.0.0-RC8";
  };

  "org.scala-sbt_util-relation_3-2.0.0-RC8" = fetchMaven {
    name = "org.scala-sbt_util-relation_3-2.0.0-RC8";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/util-relation_3/2.0.0-RC8/util-relation_3-2.0.0-RC8.jar" "https://repo1.maven.org/maven2/org/scala-sbt/util-relation_3/2.0.0-RC8/util-relation_3-2.0.0-RC8.pom" ];
    hash = "sha256-D+5pyCusW1hQh46iYEnsSxi/v6/cylGt4mSE225uQww=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/util-relation_3/2.0.0-RC8";
  };

  "org.scala-sbt_zinc-apiinfo_3-2.0.0-M13" = fetchMaven {
    name = "org.scala-sbt_zinc-apiinfo_3-2.0.0-M13";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/zinc-apiinfo_3/2.0.0-M13/zinc-apiinfo_3-2.0.0-M13.jar" "https://repo1.maven.org/maven2/org/scala-sbt/zinc-apiinfo_3/2.0.0-M13/zinc-apiinfo_3-2.0.0-M13.pom" ];
    hash = "sha256-GQOth7Ts46wdC5evf9TI1/mYIHw8UWdX4atKLicQ4TI=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-apiinfo_3/2.0.0-M13";
  };

  "org.scala-sbt_zinc-classfile_3-2.0.0-M13" = fetchMaven {
    name = "org.scala-sbt_zinc-classfile_3-2.0.0-M13";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/zinc-classfile_3/2.0.0-M13/zinc-classfile_3-2.0.0-M13.jar" "https://repo1.maven.org/maven2/org/scala-sbt/zinc-classfile_3/2.0.0-M13/zinc-classfile_3-2.0.0-M13.pom" ];
    hash = "sha256-pFV2CV1eTtJf4X0uJQfYVS1nNjbM7IPaKfG6DqrsKiA=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-classfile_3/2.0.0-M13";
  };

  "org.scala-sbt_zinc-classpath_3-2.0.0-M13" = fetchMaven {
    name = "org.scala-sbt_zinc-classpath_3-2.0.0-M13";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/zinc-classpath_3/2.0.0-M13/zinc-classpath_3-2.0.0-M13.jar" "https://repo1.maven.org/maven2/org/scala-sbt/zinc-classpath_3/2.0.0-M13/zinc-classpath_3-2.0.0-M13.pom" ];
    hash = "sha256-mnzpVRybTPvT7yYpgrgiRRnJ3XWa0vsNXG3Ff/Ym6AQ=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-classpath_3/2.0.0-M13";
  };

  "org.scala-sbt_zinc-compile-core_3-2.0.0-M13" = fetchMaven {
    name = "org.scala-sbt_zinc-compile-core_3-2.0.0-M13";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/zinc-compile-core_3/2.0.0-M13/zinc-compile-core_3-2.0.0-M13.jar" "https://repo1.maven.org/maven2/org/scala-sbt/zinc-compile-core_3/2.0.0-M13/zinc-compile-core_3-2.0.0-M13.pom" ];
    hash = "sha256-K5PNGq7yrrpY3AFzq1nW7kHDn+8ff6aelVfocPYgxeE=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-compile-core_3/2.0.0-M13";
  };

  "org.scala-sbt_zinc-core_3-2.0.0-M13" = fetchMaven {
    name = "org.scala-sbt_zinc-core_3-2.0.0-M13";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/zinc-core_3/2.0.0-M13/zinc-core_3-2.0.0-M13.jar" "https://repo1.maven.org/maven2/org/scala-sbt/zinc-core_3/2.0.0-M13/zinc-core_3-2.0.0-M13.pom" ];
    hash = "sha256-9YI8jRA9AfqmHBHhYoyGr5C172lrsTq7R/St/B0PmDM=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-core_3/2.0.0-M13";
  };

  "org.scala-sbt_zinc-persist_3-2.0.0-M13" = fetchMaven {
    name = "org.scala-sbt_zinc-persist_3-2.0.0-M13";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/zinc-persist_3/2.0.0-M13/zinc-persist_3-2.0.0-M13.jar" "https://repo1.maven.org/maven2/org/scala-sbt/zinc-persist_3/2.0.0-M13/zinc-persist_3-2.0.0-M13.pom" ];
    hash = "sha256-AMHXbYczB/B2OBFaYtSGueP9jrsbPmJUr46SEYYYTrM=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc-persist_3/2.0.0-M13";
  };

  "org.scala-sbt_zinc_3-2.0.0-M13" = fetchMaven {
    name = "org.scala-sbt_zinc_3-2.0.0-M13";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/zinc_3/2.0.0-M13/zinc_3-2.0.0-M13.jar" "https://repo1.maven.org/maven2/org/scala-sbt/zinc_3/2.0.0-M13/zinc_3-2.0.0-M13.pom" ];
    hash = "sha256-+TPVpaGQhkQRcMB99+JIw8FbnWn0lgq2bVG05HtKP/0=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/zinc_3/2.0.0-M13";
  };

  "org.slf4j_jcl-over-slf4j-1.7.30" = fetchMaven {
    name = "org.slf4j_jcl-over-slf4j-1.7.30";
    urls = [ "https://repo1.maven.org/maven2/org/slf4j/jcl-over-slf4j/1.7.30/jcl-over-slf4j-1.7.30.jar" "https://repo1.maven.org/maven2/org/slf4j/jcl-over-slf4j/1.7.30/jcl-over-slf4j-1.7.30.pom" ];
    hash = "sha256-kCzoxU+HfO0P4FhST+3SNgi23+nCAQOAvw5/XHssymY=";
    installPath = "https/repo1.maven.org/maven2/org/slf4j/jcl-over-slf4j/1.7.30";
  };

  "org.slf4j_jul-to-slf4j-1.7.30" = fetchMaven {
    name = "org.slf4j_jul-to-slf4j-1.7.30";
    urls = [ "https://repo1.maven.org/maven2/org/slf4j/jul-to-slf4j/1.7.30/jul-to-slf4j-1.7.30.jar" "https://repo1.maven.org/maven2/org/slf4j/jul-to-slf4j/1.7.30/jul-to-slf4j-1.7.30.pom" ];
    hash = "sha256-j+ngBYFB+jb0nMfdiyjuVtUM/lpGs5cAm2x62Z1Y+6Q=";
    installPath = "https/repo1.maven.org/maven2/org/slf4j/jul-to-slf4j/1.7.30";
  };

  "org.slf4j_slf4j-api-2.0.17" = fetchMaven {
    name = "org.slf4j_slf4j-api-2.0.17";
    urls = [ "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar" "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.pom" ];
    hash = "sha256-H8Tq0N+icmvASnUUTYYRCm5dhYy03Jqvbz/pWYut/h0=";
    installPath = "https/repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.17";
  };

  "org.slf4j_slf4j-bom-2.0.17" = fetchMaven {
    name = "org.slf4j_slf4j-bom-2.0.17";
    urls = [ "https://repo1.maven.org/maven2/org/slf4j/slf4j-bom/2.0.17/slf4j-bom-2.0.17.pom" ];
    hash = "sha256-qzVo4Yw93XWPRmfJurfoPZ/b9JSCgRngTQmCG6cRwMA=";
    installPath = "https/repo1.maven.org/maven2/org/slf4j/slf4j-bom/2.0.17";
  };

  "org.slf4j_slf4j-parent-1.7.30" = fetchMaven {
    name = "org.slf4j_slf4j-parent-1.7.30";
    urls = [ "https://repo1.maven.org/maven2/org/slf4j/slf4j-parent/1.7.30/slf4j-parent-1.7.30.pom" ];
    hash = "sha256-poyNibR9n/DHgo+I/r5Qb4ZXkeeiEDT9ZvLLwX1PgeI=";
    installPath = "https/repo1.maven.org/maven2/org/slf4j/slf4j-parent/1.7.30";
  };

  "org.slf4j_slf4j-parent-2.0.17" = fetchMaven {
    name = "org.slf4j_slf4j-parent-2.0.17";
    urls = [ "https://repo1.maven.org/maven2/org/slf4j/slf4j-parent/2.0.17/slf4j-parent-2.0.17.pom" ];
    hash = "sha256-H/5UPMMiEV8gCId3abw3znMuG9wWYSMevo6t1zUGACw=";
    installPath = "https/repo1.maven.org/maven2/org/slf4j/slf4j-parent/2.0.17";
  };

  "org.snakeyaml_snakeyaml-engine-3.0.1" = fetchMaven {
    name = "org.snakeyaml_snakeyaml-engine-3.0.1";
    urls = [ "https://repo1.maven.org/maven2/org/snakeyaml/snakeyaml-engine/3.0.1/snakeyaml-engine-3.0.1.jar" "https://repo1.maven.org/maven2/org/snakeyaml/snakeyaml-engine/3.0.1/snakeyaml-engine-3.0.1.pom" ];
    hash = "sha256-7iAdeB8CZ2r3BEAfH+j7Qfk6Dk9X/ThHDOaJrAS9gXY=";
    installPath = "https/repo1.maven.org/maven2/org/snakeyaml/snakeyaml-engine/3.0.1";
  };

  "org.springframework_spring-framework-bom-5.3.39" = fetchMaven {
    name = "org.springframework_spring-framework-bom-5.3.39";
    urls = [ "https://repo1.maven.org/maven2/org/springframework/spring-framework-bom/5.3.39/spring-framework-bom-5.3.39.pom" ];
    hash = "sha256-V+sR9AvokPz2NrvEFCxdLHl3jrW2o9dP3gisCDAUUDA=";
    installPath = "https/repo1.maven.org/maven2/org/springframework/spring-framework-bom/5.3.39";
  };

  "org.testcontainers_testcontainers-bom-1.21.3" = fetchMaven {
    name = "org.testcontainers_testcontainers-bom-1.21.3";
    urls = [ "https://repo1.maven.org/maven2/org/testcontainers/testcontainers-bom/1.21.3/testcontainers-bom-1.21.3.pom" ];
    hash = "sha256-Bxij8f7vFPr7ipZu8m5yr5VfI/KrHnENaUfQtlC2xy8=";
    installPath = "https/repo1.maven.org/maven2/org/testcontainers/testcontainers-bom/1.21.3";
  };

  "org.tukaani_xz-1.10" = fetchMaven {
    name = "org.tukaani_xz-1.10";
    urls = [ "https://repo1.maven.org/maven2/org/tukaani/xz/1.10/xz-1.10.jar" "https://repo1.maven.org/maven2/org/tukaani/xz/1.10/xz-1.10.pom" ];
    hash = "sha256-VKJG7cDEWkgts0KEMgjW1RCn1YXwfFemS+tpniUTZwY=";
    installPath = "https/repo1.maven.org/maven2/org/tukaani/xz/1.10";
  };

  "org.yaml_snakeyaml-2.0" = fetchMaven {
    name = "org.yaml_snakeyaml-2.0";
    urls = [ "https://repo1.maven.org/maven2/org/yaml/snakeyaml/2.0/snakeyaml-2.0.jar" "https://repo1.maven.org/maven2/org/yaml/snakeyaml/2.0/snakeyaml-2.0.pom" ];
    hash = "sha256-4/5l8lMWWNxqv1JGr0n8QtEo0KGAUGULj7lmdy9TODI=";
    installPath = "https/repo1.maven.org/maven2/org/yaml/snakeyaml/2.0";
  };

  "ch.epfl.scala_bsp4j-2.2.0-M2" = fetchMaven {
    name = "ch.epfl.scala_bsp4j-2.2.0-M2";
    urls = [ "https://repo1.maven.org/maven2/ch/epfl/scala/bsp4j/2.2.0-M2/bsp4j-2.2.0-M2.jar" "https://repo1.maven.org/maven2/ch/epfl/scala/bsp4j/2.2.0-M2/bsp4j-2.2.0-M2.pom" ];
    hash = "sha256-p9YcDs64uhLxgsiPpx7xhTwhifvU5YdsBSs5dq5NWzU=";
    installPath = "https/repo1.maven.org/maven2/ch/epfl/scala/bsp4j/2.2.0-M2";
  };

  "ch.qos.logback_logback-classic-1.5.27" = fetchMaven {
    name = "ch.qos.logback_logback-classic-1.5.27";
    urls = [ "https://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.5.27/logback-classic-1.5.27.jar" "https://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.5.27/logback-classic-1.5.27.pom" ];
    hash = "sha256-aQXm76W3qbwufGu9GDa2ghpWMcCwq8G4P9swZMmov1o=";
    installPath = "https/repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.5.27";
  };

  "ch.qos.logback_logback-core-1.5.27" = fetchMaven {
    name = "ch.qos.logback_logback-core-1.5.27";
    urls = [ "https://repo1.maven.org/maven2/ch/qos/logback/logback-core/1.5.27/logback-core-1.5.27.jar" "https://repo1.maven.org/maven2/ch/qos/logback/logback-core/1.5.27/logback-core-1.5.27.pom" ];
    hash = "sha256-AKniiBWrrvVt7lqiktlqOw3OshSwHYROtrckTDdZ7P4=";
    installPath = "https/repo1.maven.org/maven2/ch/qos/logback/logback-core/1.5.27";
  };

  "ch.qos.logback_logback-parent-1.5.27" = fetchMaven {
    name = "ch.qos.logback_logback-parent-1.5.27";
    urls = [ "https://repo1.maven.org/maven2/ch/qos/logback/logback-parent/1.5.27/logback-parent-1.5.27.pom" ];
    hash = "sha256-odxY24K02oFV1HclbhJQ2NaPtzt02+CpDcOGArb7tAg=";
    installPath = "https/repo1.maven.org/maven2/ch/qos/logback/logback-parent/1.5.27";
  };

  "com.eed3si9n.jarjar_jarjar-1.16.0" = fetchMaven {
    name = "com.eed3si9n.jarjar_jarjar-1.16.0";
    urls = [ "https://repo1.maven.org/maven2/com/eed3si9n/jarjar/jarjar/1.16.0/jarjar-1.16.0.jar" "https://repo1.maven.org/maven2/com/eed3si9n/jarjar/jarjar/1.16.0/jarjar-1.16.0.pom" ];
    hash = "sha256-7yeu2GEZWT35zIdhx12PASb7z1iRTbfLqInMmcWgOnY=";
    installPath = "https/repo1.maven.org/maven2/com/eed3si9n/jarjar/jarjar/1.16.0";
  };

  "com.eed3si9n.jarjarabrams_jarjar-abrams-core_3-1.16.0" = fetchMaven {
    name = "com.eed3si9n.jarjarabrams_jarjar-abrams-core_3-1.16.0";
    urls = [ "https://repo1.maven.org/maven2/com/eed3si9n/jarjarabrams/jarjar-abrams-core_3/1.16.0/jarjar-abrams-core_3-1.16.0.jar" "https://repo1.maven.org/maven2/com/eed3si9n/jarjarabrams/jarjar-abrams-core_3/1.16.0/jarjar-abrams-core_3-1.16.0.pom" ];
    hash = "sha256-W5DKe0MH8VvL8ijEhNThoGF+XDs2C2AL1vYh9VdWf2I=";
    installPath = "https/repo1.maven.org/maven2/com/eed3si9n/jarjarabrams/jarjar-abrams-core_3/1.16.0";
  };

  "com.fasterxml.jackson_jackson-base-2.12.1" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-base-2.12.1";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-base/2.12.1/jackson-base-2.12.1.pom" ];
    hash = "sha256-QdwEWejSbiS//t8L9WxLqUxc0QQMY90a7ckBf6YzS2M=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-base/2.12.1";
  };

  "com.fasterxml.jackson_jackson-base-2.15.1" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-base-2.15.1";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-base/2.15.1/jackson-base-2.15.1.pom" ];
    hash = "sha256-DEG+wnRgBDaKE+g5oWHRRWcpgUH3rSj+eex3MKkiDYA=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-base/2.15.1";
  };

  "com.fasterxml.jackson_jackson-bom-2.12.1" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-bom-2.12.1";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.12.1/jackson-bom-2.12.1.pom" ];
    hash = "sha256-IVTSEkQzRB352EzD1i+FXx8n+HSzPMD5TGq4Ez0VTzc=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.12.1";
  };

  "com.fasterxml.jackson_jackson-bom-2.15.1" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-bom-2.15.1";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.15.1/jackson-bom-2.15.1.pom" ];
    hash = "sha256-xTY1hTkw6E3dYAMDZnockm2fm43WPMcIRt0k2oxO2O8=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.15.1";
  };

  "com.fasterxml.jackson_jackson-bom-2.19.1" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-bom-2.19.1";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.19.1/jackson-bom-2.19.1.pom" ];
    hash = "sha256-kP82dKMabZd8AmRR6GziNerM2PpeLKxnpJN/XjbxSAk=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.19.1";
  };

  "com.fasterxml.jackson_jackson-bom-2.20.0" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-bom-2.20.0";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.20.0/jackson-bom-2.20.0.pom" ];
    hash = "sha256-70SbdHmTvJAqbWkUg71EiB1Lus2mJv8PEA7Cogp4hNE=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.20.0";
  };

  "com.fasterxml.jackson_jackson-parent-2.12" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-parent-2.12";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.12/jackson-parent-2.12.pom" ];
    hash = "sha256-1XZX837v+3OgmuIWerAxNmHU3KA9W6GDs10dtM+w11o=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.12";
  };

  "com.fasterxml.jackson_jackson-parent-2.15" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-parent-2.15";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.15/jackson-parent-2.15.pom" ];
    hash = "sha256-Rybw8nineMf0Xjlc5GhV4ayVQMYocW1rCXiNhgdXiXc=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.15";
  };

  "com.fasterxml.jackson_jackson-parent-2.19.2" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-parent-2.19.2";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.19.2/jackson-parent-2.19.2.pom" ];
    hash = "sha256-v56LQyKnjsiVrubSyUM68hAFq28kAKkV/3eMBV/TduI=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.19.2";
  };

  "com.fasterxml.jackson_jackson-parent-2.20" = fetchMaven {
    name = "com.fasterxml.jackson_jackson-parent-2.20";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.20/jackson-parent-2.20.pom" ];
    hash = "sha256-JY6nw7tNf97JPIVDxdVeW15nSw/sLDYdveEC3KVAUis=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/jackson-parent/2.20";
  };

  "com.github.javaparser_javaparser-core-3.28.0" = fetchMaven {
    name = "com.github.javaparser_javaparser-core-3.28.0";
    urls = [ "https://repo1.maven.org/maven2/com/github/javaparser/javaparser-core/3.28.0/javaparser-core-3.28.0.jar" "https://repo1.maven.org/maven2/com/github/javaparser/javaparser-core/3.28.0/javaparser-core-3.28.0.pom" ];
    hash = "sha256-EBy0hAEHyMRNrkMC7OFEMLcfsrcKKFf2NNIP6teRwSo=";
    installPath = "https/repo1.maven.org/maven2/com/github/javaparser/javaparser-core/3.28.0";
  };

  "com.github.javaparser_javaparser-parent-3.28.0" = fetchMaven {
    name = "com.github.javaparser_javaparser-parent-3.28.0";
    urls = [ "https://repo1.maven.org/maven2/com/github/javaparser/javaparser-parent/3.28.0/javaparser-parent-3.28.0.pom" ];
    hash = "sha256-SuB9T6botzRsS9+f5CupKpCDRDKRQde+LvI2UdowjJM=";
    installPath = "https/repo1.maven.org/maven2/com/github/javaparser/javaparser-parent/3.28.0";
  };

  "com.github.luben_zstd-jni-1.5.7-4" = fetchMaven {
    name = "com.github.luben_zstd-jni-1.5.7-4";
    urls = [ "https://repo1.maven.org/maven2/com/github/luben/zstd-jni/1.5.7-4/zstd-jni-1.5.7-4.jar" "https://repo1.maven.org/maven2/com/github/luben/zstd-jni/1.5.7-4/zstd-jni-1.5.7-4.pom" ];
    hash = "sha256-2EcBFO5+wfh8ciQ9vjvr0QK5poRO50kCCG8F4E6EzWk=";
    installPath = "https/repo1.maven.org/maven2/com/github/luben/zstd-jni/1.5.7-4";
  };

  "com.google.errorprone_error_prone_annotations-2.41.0" = fetchMaven {
    name = "com.google.errorprone_error_prone_annotations-2.41.0";
    urls = [ "https://repo1.maven.org/maven2/com/google/errorprone/error_prone_annotations/2.41.0/error_prone_annotations-2.41.0.jar" "https://repo1.maven.org/maven2/com/google/errorprone/error_prone_annotations/2.41.0/error_prone_annotations-2.41.0.pom" ];
    hash = "sha256-Q+YOxLcD0WwrMI3uw1kKqAolxt4KZVdjuT6eGEubaPg=";
    installPath = "https/repo1.maven.org/maven2/com/google/errorprone/error_prone_annotations/2.41.0";
  };

  "com.google.errorprone_error_prone_parent-2.41.0" = fetchMaven {
    name = "com.google.errorprone_error_prone_parent-2.41.0";
    urls = [ "https://repo1.maven.org/maven2/com/google/errorprone/error_prone_parent/2.41.0/error_prone_parent-2.41.0.pom" ];
    hash = "sha256-wefFIBCjvUfqdaGh1inO6L3MQMqWkSRp2rE8kotNxm4=";
    installPath = "https/repo1.maven.org/maven2/com/google/errorprone/error_prone_parent/2.41.0";
  };

  "com.google.guava_failureaccess-1.0.1" = fetchMaven {
    name = "com.google.guava_failureaccess-1.0.1";
    urls = [ "https://repo1.maven.org/maven2/com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar" "https://repo1.maven.org/maven2/com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.pom" ];
    hash = "sha256-keXAVKG0tjTFYMrmNnwUhTz9Tdvv6YgMTVf3WGPaWmM=";
    installPath = "https/repo1.maven.org/maven2/com/google/guava/failureaccess/1.0.1";
  };

  "com.google.guava_guava-30.1-jre" = fetchMaven {
    name = "com.google.guava_guava-30.1-jre";
    urls = [ "https://repo1.maven.org/maven2/com/google/guava/guava/30.1-jre/guava-30.1-jre.jar" "https://repo1.maven.org/maven2/com/google/guava/guava/30.1-jre/guava-30.1-jre.pom" ];
    hash = "sha256-SILksEdHjUqzx9HshT4MC4yCKHLZ27GI9kw08BUmtXg=";
    installPath = "https/repo1.maven.org/maven2/com/google/guava/guava/30.1-jre";
  };

  "com.google.guava_guava-parent-26.0-android" = fetchMaven {
    name = "com.google.guava_guava-parent-26.0-android";
    urls = [ "https://repo1.maven.org/maven2/com/google/guava/guava-parent/26.0-android/guava-parent-26.0-android.pom" ];
    hash = "sha256-E6Ip+1cPpK0zjeeIs6nlA7UKdoaVt4c+rJic/rZqXmU=";
    installPath = "https/repo1.maven.org/maven2/com/google/guava/guava-parent/26.0-android";
  };

  "com.google.guava_guava-parent-30.1-jre" = fetchMaven {
    name = "com.google.guava_guava-parent-30.1-jre";
    urls = [ "https://repo1.maven.org/maven2/com/google/guava/guava-parent/30.1-jre/guava-parent-30.1-jre.pom" ];
    hash = "sha256-yB95NC1JrIdRkg/+VR6T5N1NbHSIt0v1mLCOqmJ5W3s=";
    installPath = "https/repo1.maven.org/maven2/com/google/guava/guava-parent/30.1-jre";
  };

  "com.google.guava_listenablefuture-9999.0-empty-to-avoid-conflict-with-guava" = fetchMaven {
    name = "com.google.guava_listenablefuture-9999.0-empty-to-avoid-conflict-with-guava";
    urls = [ "https://repo1.maven.org/maven2/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar" "https://repo1.maven.org/maven2/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.pom" ];
    hash = "sha256-RKtfF6GYbf2zSPY1m+gj8UN8qpI0GcTyMCX6xPLTdq8=";
    installPath = "https/repo1.maven.org/maven2/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava";
  };

  "com.google.j2objc_j2objc-annotations-1.3" = fetchMaven {
    name = "com.google.j2objc_j2objc-annotations-1.3";
    urls = [ "https://repo1.maven.org/maven2/com/google/j2objc/j2objc-annotations/1.3/j2objc-annotations-1.3.jar" "https://repo1.maven.org/maven2/com/google/j2objc/j2objc-annotations/1.3/j2objc-annotations-1.3.pom" ];
    hash = "sha256-66DvifOQZUx1Dp1O4uKA7mylXcgFQOBqcCIL7qVklbI=";
    installPath = "https/repo1.maven.org/maven2/com/google/j2objc/j2objc-annotations/1.3";
  };

  "com.googlecode.javaewah_JavaEWAH-1.2.3" = fetchMaven {
    name = "com.googlecode.javaewah_JavaEWAH-1.2.3";
    urls = [ "https://repo1.maven.org/maven2/com/googlecode/javaewah/JavaEWAH/1.2.3/JavaEWAH-1.2.3.jar" "https://repo1.maven.org/maven2/com/googlecode/javaewah/JavaEWAH/1.2.3/JavaEWAH-1.2.3.pom" ];
    hash = "sha256-1DO+Mxt7yFlIdMrR3v7xdPPSQcCEVLA0Lxn4ZwunmJU=";
    installPath = "https/repo1.maven.org/maven2/com/googlecode/javaewah/JavaEWAH/1.2.3";
  };

  "com.vladsch.flexmark_flexmark-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark/0.62.2/flexmark-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark/0.62.2/flexmark-0.62.2.pom" ];
    hash = "sha256-CMbMcOs3cMmCu7+sAh6qiwj63tMDlJ6qIrZRbHF2gDE=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-anchorlink-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-anchorlink-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-anchorlink/0.62.2/flexmark-ext-anchorlink-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-anchorlink/0.62.2/flexmark-ext-anchorlink-0.62.2.pom" ];
    hash = "sha256-weHNR6k/69NjAg2Vs72ce1wOZ1rwBicv4TMLDS9jnGE=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-anchorlink/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-autolink-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-autolink-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-autolink/0.62.2/flexmark-ext-autolink-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-autolink/0.62.2/flexmark-ext-autolink-0.62.2.pom" ];
    hash = "sha256-15OH05RylvbLSzEu47GBdhtKZvyP3ibjXETb+3Sn5+Y=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-autolink/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-emoji-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-emoji-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-emoji/0.62.2/flexmark-ext-emoji-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-emoji/0.62.2/flexmark-ext-emoji-0.62.2.pom" ];
    hash = "sha256-UHbh+WMLnLqFzhE9GIdc3pwFEBy94rNpWT6olRGnIvI=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-emoji/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-gfm-strikethrough-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-gfm-strikethrough-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-strikethrough/0.62.2/flexmark-ext-gfm-strikethrough-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-strikethrough/0.62.2/flexmark-ext-gfm-strikethrough-0.62.2.pom" ];
    hash = "sha256-1l/E13+s+Pc/CVD28MVSrqRUkkrfwKD6K0+2zvCQX8o=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-strikethrough/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-gfm-tasklist-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-gfm-tasklist-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-tasklist/0.62.2/flexmark-ext-gfm-tasklist-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-tasklist/0.62.2/flexmark-ext-gfm-tasklist-0.62.2.pom" ];
    hash = "sha256-gtACK+9qTISC22QYuWoyvgNeTXmuSOZxXuojXESKAvE=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-gfm-tasklist/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-ins-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-ins-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-ins/0.62.2/flexmark-ext-ins-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-ins/0.62.2/flexmark-ext-ins-0.62.2.pom" ];
    hash = "sha256-VIKNuMXAxAbmNWnk2nWPgpSzbkoGfpA6miKQuvOUmF4=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-ins/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-superscript-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-superscript-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-superscript/0.62.2/flexmark-ext-superscript-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-superscript/0.62.2/flexmark-ext-superscript-0.62.2.pom" ];
    hash = "sha256-pfRu434uIlDIkwSEaFwxZFwcUjTnU5cbuSfsG578PC4=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-superscript/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-tables-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-tables-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-tables/0.62.2/flexmark-ext-tables-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-tables/0.62.2/flexmark-ext-tables-0.62.2.pom" ];
    hash = "sha256-3Fef3ZHc6jjwTHjvOGsVvLAMbRMwJHlZ5X7SKIaCj6w=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-tables/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-wikilink-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-wikilink-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-wikilink/0.62.2/flexmark-ext-wikilink-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-wikilink/0.62.2/flexmark-ext-wikilink-0.62.2.pom" ];
    hash = "sha256-NQtfUT4F3p6+nGk6o07EwlX1kZvkXarCfWw07QQgYyE=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-wikilink/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-ext-yaml-front-matter-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-ext-yaml-front-matter-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-yaml-front-matter/0.62.2/flexmark-ext-yaml-front-matter-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-yaml-front-matter/0.62.2/flexmark-ext-yaml-front-matter-0.62.2.pom" ];
    hash = "sha256-tc0KpVAhnflMmVlFUXFqwocYsXuL3PiXeFtdO+p9Ta4=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-ext-yaml-front-matter/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-java-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-java-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-java/0.62.2/flexmark-java-0.62.2.pom" ];
    hash = "sha256-DlxcWCry0vUFs1L54guu8FLGgpuYD9+ksL2x5sv6E9c=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-java/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-jira-converter-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-jira-converter-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-jira-converter/0.62.2/flexmark-jira-converter-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-jira-converter/0.62.2/flexmark-jira-converter-0.62.2.pom" ];
    hash = "sha256-k4eeiCIqq4fE5F0MPS9FMDEdlWEb+Gd36pDNxQSFMFY=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-jira-converter/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util/0.62.2/flexmark-util-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util/0.62.2/flexmark-util-0.62.2.pom" ];
    hash = "sha256-A3coPMDIx8qFH4WcoKFEcAY6MDeICS9olH/SPgIEbeI=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-ast-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-ast-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-ast/0.62.2/flexmark-util-ast-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-ast/0.62.2/flexmark-util-ast-0.62.2.pom" ];
    hash = "sha256-bT7Cqm3k63wFdcC63M3WAtz5p0QqArmmCvpfPGuvDjw=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-ast/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-builder-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-builder-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-builder/0.62.2/flexmark-util-builder-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-builder/0.62.2/flexmark-util-builder-0.62.2.pom" ];
    hash = "sha256-+kjX932WxGRANJw+UPDyy8MJB6wKUXI7tf+PyOAYbJM=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-builder/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-collection-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-collection-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-collection/0.62.2/flexmark-util-collection-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-collection/0.62.2/flexmark-util-collection-0.62.2.pom" ];
    hash = "sha256-vsdaPDU/TcTKnim4MAWhcXp4P0upYTWIMLMSCeg6Wx4=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-collection/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-data-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-data-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-data/0.62.2/flexmark-util-data-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-data/0.62.2/flexmark-util-data-0.62.2.pom" ];
    hash = "sha256-m3S05kD1HNXWdGXPwXapNqzLv4g2WicpuaNUJjvZDW4=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-data/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-dependency-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-dependency-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-dependency/0.62.2/flexmark-util-dependency-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-dependency/0.62.2/flexmark-util-dependency-0.62.2.pom" ];
    hash = "sha256-nSFsXZXFD67UbxMv6hAZEjv6VfCmewH9PsP6zk7vLR4=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-dependency/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-format-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-format-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-format/0.62.2/flexmark-util-format-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-format/0.62.2/flexmark-util-format-0.62.2.pom" ];
    hash = "sha256-j7GbAIjjp00wTPbuXCTO//af5J5JooOPmHh2Da3jBd0=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-format/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-html-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-html-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-html/0.62.2/flexmark-util-html-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-html/0.62.2/flexmark-util-html-0.62.2.pom" ];
    hash = "sha256-9MSBM5awDcqrCDRtRKKCrxD35X5DYf+U7NmUR8OOW94=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-html/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-misc-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-misc-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-misc/0.62.2/flexmark-util-misc-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-misc/0.62.2/flexmark-util-misc-0.62.2.pom" ];
    hash = "sha256-VfG2y0OgXWkcDF0VNHFTnOsf1jjmZtSZThZABQ0yc5A=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-misc/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-options-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-options-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-options/0.62.2/flexmark-util-options-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-options/0.62.2/flexmark-util-options-0.62.2.pom" ];
    hash = "sha256-Px6MK19ozVJLQGj3fCpDhMTUtrWLzhiqdDDRdBpf8i8=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-options/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-sequence-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-sequence-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-sequence/0.62.2/flexmark-util-sequence-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-sequence/0.62.2/flexmark-util-sequence-0.62.2.pom" ];
    hash = "sha256-J8ZXFheFBaMP+b9VMZ02j5Sonvtf26k6DR7C5AspxVg=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-sequence/0.62.2";
  };

  "com.vladsch.flexmark_flexmark-util-visitor-0.62.2" = fetchMaven {
    name = "com.vladsch.flexmark_flexmark-util-visitor-0.62.2";
    urls = [ "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-visitor/0.62.2/flexmark-util-visitor-0.62.2.jar" "https://repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-visitor/0.62.2/flexmark-util-visitor-0.62.2.pom" ];
    hash = "sha256-sGUXA1qXnyVQTMPXJoAh4L1+L895QeeW7oazG3/NqyI=";
    installPath = "https/repo1.maven.org/maven2/com/vladsch/flexmark/flexmark-util-visitor/0.62.2";
  };

  "io.get-coursier.jniutils_windows-jni-utils-0.3.3" = fetchMaven {
    name = "io.get-coursier.jniutils_windows-jni-utils-0.3.3";
    urls = [ "https://repo1.maven.org/maven2/io/get-coursier/jniutils/windows-jni-utils/0.3.3/windows-jni-utils-0.3.3.jar" "https://repo1.maven.org/maven2/io/get-coursier/jniutils/windows-jni-utils/0.3.3/windows-jni-utils-0.3.3.pom" ];
    hash = "sha256-OgBT8ULqeyvpNMGSmXrwpYXR4VOAlmSIMs+BejCP56c=";
    installPath = "https/repo1.maven.org/maven2/io/get-coursier/jniutils/windows-jni-utils/0.3.3";
  };

  "io.github.alexarchambault_concurrent-reference-hash-map-1.1.0" = fetchMaven {
    name = "io.github.alexarchambault_concurrent-reference-hash-map-1.1.0";
    urls = [ "https://repo1.maven.org/maven2/io/github/alexarchambault/concurrent-reference-hash-map/1.1.0/concurrent-reference-hash-map-1.1.0.jar" "https://repo1.maven.org/maven2/io/github/alexarchambault/concurrent-reference-hash-map/1.1.0/concurrent-reference-hash-map-1.1.0.pom" ];
    hash = "sha256-949g3dbXxz773bZlkiK2Xh3XiY5Ofc+1k6i8LM6s+yI=";
    installPath = "https/repo1.maven.org/maven2/io/github/alexarchambault/concurrent-reference-hash-map/1.1.0";
  };

  "io.github.alexarchambault_is-terminal-0.1.2" = fetchMaven {
    name = "io.github.alexarchambault_is-terminal-0.1.2";
    urls = [ "https://repo1.maven.org/maven2/io/github/alexarchambault/is-terminal/0.1.2/is-terminal-0.1.2.jar" "https://repo1.maven.org/maven2/io/github/alexarchambault/is-terminal/0.1.2/is-terminal-0.1.2.pom" ];
    hash = "sha256-j9aW4Y/zyD4aYu2XykzfEpdGUXideUCkVTFSvtzlH48=";
    installPath = "https/repo1.maven.org/maven2/io/github/alexarchambault/is-terminal/0.1.2";
  };

  "io.github.classgraph_classgraph-4.8.184" = fetchMaven {
    name = "io.github.classgraph_classgraph-4.8.184";
    urls = [ "https://repo1.maven.org/maven2/io/github/classgraph/classgraph/4.8.184/classgraph-4.8.184.jar" "https://repo1.maven.org/maven2/io/github/classgraph/classgraph/4.8.184/classgraph-4.8.184.pom" ];
    hash = "sha256-TexK9sAgGTT4cAEZYYyi1pq/J2XPQFHMlvzzRSs1Eok=";
    installPath = "https/repo1.maven.org/maven2/io/github/classgraph/classgraph/4.8.184";
  };

  "net.sf.jopt-simple_jopt-simple-5.0.4" = fetchMaven {
    name = "net.sf.jopt-simple_jopt-simple-5.0.4";
    urls = [ "https://repo1.maven.org/maven2/net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar" "https://repo1.maven.org/maven2/net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.pom" ];
    hash = "sha256-/l4TgZ+p8AP432PBNvhoHUw7VjAHgsP7PBKjYnqxFB0=";
    installPath = "https/repo1.maven.org/maven2/net/sf/jopt-simple/jopt-simple/5.0.4";
  };

  "org.apache.commons_commons-compress-1.28.0" = fetchMaven {
    name = "org.apache.commons_commons-compress-1.28.0";
    urls = [ "https://repo1.maven.org/maven2/org/apache/commons/commons-compress/1.28.0/commons-compress-1.28.0.jar" "https://repo1.maven.org/maven2/org/apache/commons/commons-compress/1.28.0/commons-compress-1.28.0.pom" ];
    hash = "sha256-dT70h6cIwxdIJVO9eFn4/P1q2530bl+046ZjQ3EVGgU=";
    installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-compress/1.28.0";
  };

  "org.apache.commons_commons-lang3-3.18.0" = fetchMaven {
    name = "org.apache.commons_commons-lang3-3.18.0";
    urls = [ "https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.18.0/commons-lang3-3.18.0.jar" "https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.18.0/commons-lang3-3.18.0.pom" ];
    hash = "sha256-IzLzlGs2SlGRKZ+baXEo/jh3JY2oTXa5wdIl2KlBy2E=";
    installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.18.0";
  };

  "org.apache.commons_commons-lang3-3.8.1" = fetchMaven {
    name = "org.apache.commons_commons-lang3-3.8.1";
    urls = [ "https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.8.1/commons-lang3-3.8.1.pom" ];
    hash = "sha256-sRwL9YM4DOzOxwPnBOgJyanP0m39AKrpy4hbtdM12q0=";
    installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.8.1";
  };

  "org.apache.commons_commons-math3-3.2" = fetchMaven {
    name = "org.apache.commons_commons-math3-3.2";
    urls = [ "https://repo1.maven.org/maven2/org/apache/commons/commons-math3/3.2/commons-math3-3.2.jar" "https://repo1.maven.org/maven2/org/apache/commons/commons-math3/3.2/commons-math3-3.2.pom" ];
    hash = "sha256-0EqwMxOWH5aJIPkq0w1zL+SmdRt4hCUYKKbI1/3y5NU=";
    installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-math3/3.2";
  };

  "org.apache.commons_commons-parent-28" = fetchMaven {
    name = "org.apache.commons_commons-parent-28";
    urls = [ "https://repo1.maven.org/maven2/org/apache/commons/commons-parent/28/commons-parent-28.pom" ];
    hash = "sha256-FVG+Ox8Q+6OJu4zIZNnwjeI6mCunwb9mMvBmC11Wqn0=";
    installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-parent/28";
  };

  "org.apache.commons_commons-parent-47" = fetchMaven {
    name = "org.apache.commons_commons-parent-47";
    urls = [ "https://repo1.maven.org/maven2/org/apache/commons/commons-parent/47/commons-parent-47.pom" ];
    hash = "sha256-3nKXz/Cqz3ed8sPyeJUIYW5uQ/1nCy8N5gPATIkI9DQ=";
    installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-parent/47";
  };

  "org.apache.commons_commons-parent-69" = fetchMaven {
    name = "org.apache.commons_commons-parent-69";
    urls = [ "https://repo1.maven.org/maven2/org/apache/commons/commons-parent/69/commons-parent-69.pom" ];
    hash = "sha256-XDFSOofSIPQI87JPu4s21bhzz9SDiYXZ4rIoURJ4feI=";
    installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-parent/69";
  };

  "org.apache.commons_commons-parent-85" = fetchMaven {
    name = "org.apache.commons_commons-parent-85";
    urls = [ "https://repo1.maven.org/maven2/org/apache/commons/commons-parent/85/commons-parent-85.pom" ];
    hash = "sha256-Xj15VDFcZhPE5qzqmellS3KSQJENr1wgjG5fOKbbyJA=";
    installPath = "https/repo1.maven.org/maven2/org/apache/commons/commons-parent/85";
  };

  "org.apache.cxf_cxf-4.0.9" = fetchMaven {
    name = "org.apache.cxf_cxf-4.0.9";
    urls = [ "https://repo1.maven.org/maven2/org/apache/cxf/cxf/4.0.9/cxf-4.0.9.pom" ];
    hash = "sha256-qhV44o4nggwxSvi36Ygj7CJsf1s6MmcuxXCv4Xqi+M0=";
    installPath = "https/repo1.maven.org/maven2/org/apache/cxf/cxf/4.0.9";
  };

  "org.apache.cxf_cxf-bom-4.0.9" = fetchMaven {
    name = "org.apache.cxf_cxf-bom-4.0.9";
    urls = [ "https://repo1.maven.org/maven2/org/apache/cxf/cxf-bom/4.0.9/cxf-bom-4.0.9.pom" ];
    hash = "sha256-xMGg+zl2VVM6Si/TLlG5bzisKslYoQlmUAeSJlFK07M=";
    installPath = "https/repo1.maven.org/maven2/org/apache/cxf/cxf-bom/4.0.9";
  };

  "org.apache.groovy_groovy-bom-4.0.27" = fetchMaven {
    name = "org.apache.groovy_groovy-bom-4.0.27";
    urls = [ "https://repo1.maven.org/maven2/org/apache/groovy/groovy-bom/4.0.27/groovy-bom-4.0.27.pom" ];
    hash = "sha256-LpFKTYYoMwe70YPF1kycfpUSmm2Q+G+KtWRPny2CupQ=";
    installPath = "https/repo1.maven.org/maven2/org/apache/groovy/groovy-bom/4.0.27";
  };

  "org.apache.tika_tika-core-3.2.3" = fetchMaven {
    name = "org.apache.tika_tika-core-3.2.3";
    urls = [ "https://repo1.maven.org/maven2/org/apache/tika/tika-core/3.2.3/tika-core-3.2.3.jar" "https://repo1.maven.org/maven2/org/apache/tika/tika-core/3.2.3/tika-core-3.2.3.pom" ];
    hash = "sha256-Z6k2+gzmgtgnV/OMLzFjCAQl5soozCbOitBJ4FAv7/4=";
    installPath = "https/repo1.maven.org/maven2/org/apache/tika/tika-core/3.2.3";
  };

  "org.apache.tika_tika-parent-3.2.3" = fetchMaven {
    name = "org.apache.tika_tika-parent-3.2.3";
    urls = [ "https://repo1.maven.org/maven2/org/apache/tika/tika-parent/3.2.3/tika-parent-3.2.3.pom" ];
    hash = "sha256-e3kWun2gkU15LAFdYruTFErKBweQFe9KhmKaQMzr770=";
    installPath = "https/repo1.maven.org/maven2/org/apache/tika/tika-parent/3.2.3";
  };

  "org.apache.xbean_xbean-3.7" = fetchMaven {
    name = "org.apache.xbean_xbean-3.7";
    urls = [ "https://repo1.maven.org/maven2/org/apache/xbean/xbean/3.7/xbean-3.7.pom" ];
    hash = "sha256-7moEcdxl+B1i7xstWBlWabSFr9QLszuciySggKYvpAE=";
    installPath = "https/repo1.maven.org/maven2/org/apache/xbean/xbean/3.7";
  };

  "org.apache.xbean_xbean-reflect-3.7" = fetchMaven {
    name = "org.apache.xbean_xbean-reflect-3.7";
    urls = [ "https://repo1.maven.org/maven2/org/apache/xbean/xbean-reflect/3.7/xbean-reflect-3.7.jar" "https://repo1.maven.org/maven2/org/apache/xbean/xbean-reflect/3.7/xbean-reflect-3.7.pom" ];
    hash = "sha256-Zp97nk/YwipUj92NnhjU5tKNXgUmPWh2zWic2FoS434=";
    installPath = "https/repo1.maven.org/maven2/org/apache/xbean/xbean-reflect/3.7";
  };

  "org.codehaus.plexus_plexus-18" = fetchMaven {
    name = "org.codehaus.plexus_plexus-18";
    urls = [ "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus/18/plexus-18.pom" ];
    hash = "sha256-MW5t8h+IK6i4Gm58Lz3ucsEXD1GRupWWNKcizh2Osr0=";
    installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus/18";
  };

  "org.codehaus.plexus_plexus-23" = fetchMaven {
    name = "org.codehaus.plexus_plexus-23";
    urls = [ "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus/23/plexus-23.pom" ];
    hash = "sha256-hfuOp5V1JkqkgUp/MenShYhqaWO0rreRYZR1zYo0SHI=";
    installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus/23";
  };

  "org.codehaus.plexus_plexus-5.1" = fetchMaven {
    name = "org.codehaus.plexus_plexus-5.1";
    urls = [ "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus/5.1/plexus-5.1.pom" ];
    hash = "sha256-ywTicwjHcL7BzKPO3XzXpc9pE0M0j7Khcop85G3XqDI=";
    installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus/5.1";
  };

  "org.codehaus.plexus_plexus-6.5" = fetchMaven {
    name = "org.codehaus.plexus_plexus-6.5";
    urls = [ "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus/6.5/plexus-6.5.pom" ];
    hash = "sha256-6Hhmat92ApFn7ze2iYyOusDxXMYp98v1GNqAvKypKSQ=";
    installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus/6.5";
  };

  "org.codehaus.plexus_plexus-archiver-4.10.1" = fetchMaven {
    name = "org.codehaus.plexus_plexus-archiver-4.10.1";
    urls = [ "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-archiver/4.10.1/plexus-archiver-4.10.1.jar" "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-archiver/4.10.1/plexus-archiver-4.10.1.pom" ];
    hash = "sha256-L/etOVVp6XR3CvYhKP+fhEkMLh46agHsHdoQ76HO96M=";
    installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus-archiver/4.10.1";
  };

  "org.codehaus.plexus_plexus-classworlds-2.6.0" = fetchMaven {
    name = "org.codehaus.plexus_plexus-classworlds-2.6.0";
    urls = [ "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-classworlds/2.6.0/plexus-classworlds-2.6.0.jar" "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-classworlds/2.6.0/plexus-classworlds-2.6.0.pom" ];
    hash = "sha256-vh7/TKxdcZVxXljM5MLGppoP0Bc28QyI/WsrPc6XSEA=";
    installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus-classworlds/2.6.0";
  };

  "org.codehaus.plexus_plexus-container-default-2.1.1" = fetchMaven {
    name = "org.codehaus.plexus_plexus-container-default-2.1.1";
    urls = [ "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-container-default/2.1.1/plexus-container-default-2.1.1.jar" "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-container-default/2.1.1/plexus-container-default-2.1.1.pom" ];
    hash = "sha256-E0Dt5DQRVlxg8fddMJZpvhU5cfNwB9MJTi/GJ1PVt3A=";
    installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus-container-default/2.1.1";
  };

  "org.codehaus.plexus_plexus-containers-2.1.1" = fetchMaven {
    name = "org.codehaus.plexus_plexus-containers-2.1.1";
    urls = [ "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-containers/2.1.1/plexus-containers-2.1.1.pom" ];
    hash = "sha256-LR5FBjo4qAjwjKpHajTnuUBN7cLKbeTJRtYYc8q4FNw=";
    installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus-containers/2.1.1";
  };

  "org.codehaus.plexus_plexus-io-3.5.1" = fetchMaven {
    name = "org.codehaus.plexus_plexus-io-3.5.1";
    urls = [ "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-io/3.5.1/plexus-io-3.5.1.jar" "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-io/3.5.1/plexus-io-3.5.1.pom" ];
    hash = "sha256-v3IFzpebAB3MuOebhq2W4+qNOVYL8RLfokLiPnenDjg=";
    installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus-io/3.5.1";
  };

  "org.codehaus.plexus_plexus-utils-4.0.2" = fetchMaven {
    name = "org.codehaus.plexus_plexus-utils-4.0.2";
    urls = [ "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-utils/4.0.2/plexus-utils-4.0.2.jar" "https://repo1.maven.org/maven2/org/codehaus/plexus/plexus-utils/4.0.2/plexus-utils-4.0.2.pom" ];
    hash = "sha256-MVSJkxMgT9VJmYVXcxFx6eh4qYt0xwErey0uxIlYz9Q=";
    installPath = "https/repo1.maven.org/maven2/org/codehaus/plexus/plexus-utils/4.0.2";
  };

  "org.eclipse.ee4j_project-1.0.7" = fetchMaven {
    name = "org.eclipse.ee4j_project-1.0.7";
    urls = [ "https://repo1.maven.org/maven2/org/eclipse/ee4j/project/1.0.7/project-1.0.7.pom" ];
    hash = "sha256-1HxZiJ0aeo1n8AWjwGKEoPwVFP9kndMBye7xwgYEal8=";
    installPath = "https/repo1.maven.org/maven2/org/eclipse/ee4j/project/1.0.7";
  };

  "org.eclipse.jetty_jetty-bom-11.0.26" = fetchMaven {
    name = "org.eclipse.jetty_jetty-bom-11.0.26";
    urls = [ "https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-bom/11.0.26/jetty-bom-11.0.26.pom" ];
    hash = "sha256-eY2KApjnU+y4Gup33Oe/aFgwOyzNaUnuncQV88ZVbr8=";
    installPath = "https/repo1.maven.org/maven2/org/eclipse/jetty/jetty-bom/11.0.26";
  };

  "org.eclipse.jgit_org.eclipse.jgit-6.10.1.202505221210-r" = fetchMaven {
    name = "org.eclipse.jgit_org.eclipse.jgit-6.10.1.202505221210-r";
    urls = [ "https://repo1.maven.org/maven2/org/eclipse/jgit/org.eclipse.jgit/6.10.1.202505221210-r/org.eclipse.jgit-6.10.1.202505221210-r.jar" "https://repo1.maven.org/maven2/org/eclipse/jgit/org.eclipse.jgit/6.10.1.202505221210-r/org.eclipse.jgit-6.10.1.202505221210-r.pom" ];
    hash = "sha256-3ijBX3433YFfGKNC9IVIw1lk8kgyHNcIxtYVrqSJxec=";
    installPath = "https/repo1.maven.org/maven2/org/eclipse/jgit/org.eclipse.jgit/6.10.1.202505221210-r";
  };

  "org.eclipse.jgit_org.eclipse.jgit-parent-6.10.1.202505221210-r" = fetchMaven {
    name = "org.eclipse.jgit_org.eclipse.jgit-parent-6.10.1.202505221210-r";
    urls = [ "https://repo1.maven.org/maven2/org/eclipse/jgit/org.eclipse.jgit-parent/6.10.1.202505221210-r/org.eclipse.jgit-parent-6.10.1.202505221210-r.pom" ];
    hash = "sha256-rOXXAD10TWwE+xnSTNenN1lwS8e14eJzxT+cPa1yq6c=";
    installPath = "https/repo1.maven.org/maven2/org/eclipse/jgit/org.eclipse.jgit-parent/6.10.1.202505221210-r";
  };

  "org.eclipse.lsp4j_org.eclipse.lsp4j.generator-0.20.1" = fetchMaven {
    name = "org.eclipse.lsp4j_org.eclipse.lsp4j.generator-0.20.1";
    urls = [ "https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.generator/0.20.1/org.eclipse.lsp4j.generator-0.20.1.jar" "https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.generator/0.20.1/org.eclipse.lsp4j.generator-0.20.1.pom" ];
    hash = "sha256-YihOE4ZIhHUljdpQd9FATS7AzAkRiATjJz4r4IhsBOs=";
    installPath = "https/repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.generator/0.20.1";
  };

  "org.eclipse.lsp4j_org.eclipse.lsp4j.jsonrpc-0.20.1" = fetchMaven {
    name = "org.eclipse.lsp4j_org.eclipse.lsp4j.jsonrpc-0.20.1";
    urls = [ "https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.jsonrpc/0.20.1/org.eclipse.lsp4j.jsonrpc-0.20.1.jar" "https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.jsonrpc/0.20.1/org.eclipse.lsp4j.jsonrpc-0.20.1.pom" ];
    hash = "sha256-lLLBglY2iO5Xm7gfaKHb95jm7Yd2tKI+RUlVIqKSx5U=";
    installPath = "https/repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.jsonrpc/0.20.1";
  };

  "org.eclipse.xtend_org.eclipse.xtend.lib-2.28.0" = fetchMaven {
    name = "org.eclipse.xtend_org.eclipse.xtend.lib-2.28.0";
    urls = [ "https://repo1.maven.org/maven2/org/eclipse/xtend/org.eclipse.xtend.lib/2.28.0/org.eclipse.xtend.lib-2.28.0.jar" "https://repo1.maven.org/maven2/org/eclipse/xtend/org.eclipse.xtend.lib/2.28.0/org.eclipse.xtend.lib-2.28.0.pom" ];
    hash = "sha256-ch5uYaECYGox+AL4EU0OC614gqTK/IkWmlxCW+FYusU=";
    installPath = "https/repo1.maven.org/maven2/org/eclipse/xtend/org.eclipse.xtend.lib/2.28.0";
  };

  "org.eclipse.xtend_org.eclipse.xtend.lib.macro-2.28.0" = fetchMaven {
    name = "org.eclipse.xtend_org.eclipse.xtend.lib.macro-2.28.0";
    urls = [ "https://repo1.maven.org/maven2/org/eclipse/xtend/org.eclipse.xtend.lib.macro/2.28.0/org.eclipse.xtend.lib.macro-2.28.0.jar" "https://repo1.maven.org/maven2/org/eclipse/xtend/org.eclipse.xtend.lib.macro/2.28.0/org.eclipse.xtend.lib.macro-2.28.0.pom" ];
    hash = "sha256-xu5Ogojl3XzSTfETwsFodjvZydpdi2jZXY9wu5zMyOQ=";
    installPath = "https/repo1.maven.org/maven2/org/eclipse/xtend/org.eclipse.xtend.lib.macro/2.28.0";
  };

  "org.eclipse.xtext_org.eclipse.xtext.xbase.lib-2.28.0" = fetchMaven {
    name = "org.eclipse.xtext_org.eclipse.xtext.xbase.lib-2.28.0";
    urls = [ "https://repo1.maven.org/maven2/org/eclipse/xtext/org.eclipse.xtext.xbase.lib/2.28.0/org.eclipse.xtext.xbase.lib-2.28.0.jar" "https://repo1.maven.org/maven2/org/eclipse/xtext/org.eclipse.xtext.xbase.lib/2.28.0/org.eclipse.xtext.xbase.lib-2.28.0.pom" ];
    hash = "sha256-J9fEh2WXIT3xrhzNQZBoYphh2DUrkas0wpJPWhWRvbI=";
    installPath = "https/repo1.maven.org/maven2/org/eclipse/xtext/org.eclipse.xtext.xbase.lib/2.28.0";
  };

  "org.eclipse.xtext_xtext-dev-bom-2.28.0" = fetchMaven {
    name = "org.eclipse.xtext_xtext-dev-bom-2.28.0";
    urls = [ "https://repo1.maven.org/maven2/org/eclipse/xtext/xtext-dev-bom/2.28.0/xtext-dev-bom-2.28.0.pom" ];
    hash = "sha256-g4xSwbZW3JjZV+BF16ohvZ+v3o7JkVkv5CsiKT6ixVY=";
    installPath = "https/repo1.maven.org/maven2/org/eclipse/xtext/xtext-dev-bom/2.28.0";
  };

  "org.fusesource.jansi_jansi-2.4.1" = fetchMaven {
    name = "org.fusesource.jansi_jansi-2.4.1";
    urls = [ "https://repo1.maven.org/maven2/org/fusesource/jansi/jansi/2.4.1/jansi-2.4.1.jar" "https://repo1.maven.org/maven2/org/fusesource/jansi/jansi/2.4.1/jansi-2.4.1.pom" ];
    hash = "sha256-M9G+H9TA5eB6NwlBmDP0ghxZzjbvLimPXNRZHyxJXac=";
    installPath = "https/repo1.maven.org/maven2/org/fusesource/jansi/jansi/2.4.1";
  };

  "org.nibor.autolink_autolink-0.6.0" = fetchMaven {
    name = "org.nibor.autolink_autolink-0.6.0";
    urls = [ "https://repo1.maven.org/maven2/org/nibor/autolink/autolink/0.6.0/autolink-0.6.0.jar" "https://repo1.maven.org/maven2/org/nibor/autolink/autolink/0.6.0/autolink-0.6.0.pom" ];
    hash = "sha256-UyOje39E9ysUXMK3ey2jrm7S6e8EVQboYC46t+B6sdo=";
    installPath = "https/repo1.maven.org/maven2/org/nibor/autolink/autolink/0.6.0";
  };

  "org.openjdk.jmh_jmh-core-1.35" = fetchMaven {
    name = "org.openjdk.jmh_jmh-core-1.35";
    urls = [ "https://repo1.maven.org/maven2/org/openjdk/jmh/jmh-core/1.35/jmh-core-1.35.jar" "https://repo1.maven.org/maven2/org/openjdk/jmh/jmh-core/1.35/jmh-core-1.35.pom" ];
    hash = "sha256-x/U3O9iVa9maPYkYpxqCpn0KysMgU3liJhUwnfgRLb8=";
    installPath = "https/repo1.maven.org/maven2/org/openjdk/jmh/jmh-core/1.35";
  };

  "org.openjdk.jmh_jmh-parent-1.35" = fetchMaven {
    name = "org.openjdk.jmh_jmh-parent-1.35";
    urls = [ "https://repo1.maven.org/maven2/org/openjdk/jmh/jmh-parent/1.35/jmh-parent-1.35.pom" ];
    hash = "sha256-IQnIt0jWZjNk1M7ea1vYvyZ4A+KYuxGR0od7Rvl+Su0=";
    installPath = "https/repo1.maven.org/maven2/org/openjdk/jmh/jmh-parent/1.35";
  };

  "org.ow2.asm_asm-9.8" = fetchMaven {
    name = "org.ow2.asm_asm-9.8";
    urls = [ "https://repo1.maven.org/maven2/org/ow2/asm/asm/9.8/asm-9.8.jar" "https://repo1.maven.org/maven2/org/ow2/asm/asm/9.8/asm-9.8.pom" ];
    hash = "sha256-+veD/6/fvI/ohZYhYhoChm0qeS7TaclJO9qnsSkBUxY=";
    installPath = "https/repo1.maven.org/maven2/org/ow2/asm/asm/9.8";
  };

  "org.ow2.asm_asm-9.9.1" = fetchMaven {
    name = "org.ow2.asm_asm-9.9.1";
    urls = [ "https://repo1.maven.org/maven2/org/ow2/asm/asm/9.9.1/asm-9.9.1.jar" "https://repo1.maven.org/maven2/org/ow2/asm/asm/9.9.1/asm-9.9.1.pom" ];
    hash = "sha256-hX+OQOZwZfRiEIq3fkAzns2z2pg6gTtLPiV0TaHDq9M=";
    installPath = "https/repo1.maven.org/maven2/org/ow2/asm/asm/9.9.1";
  };

  "org.ow2.asm_asm-commons-9.8" = fetchMaven {
    name = "org.ow2.asm_asm-commons-9.8";
    urls = [ "https://repo1.maven.org/maven2/org/ow2/asm/asm-commons/9.8/asm-commons-9.8.jar" "https://repo1.maven.org/maven2/org/ow2/asm/asm-commons/9.8/asm-commons-9.8.pom" ];
    hash = "sha256-wsQ21wHx134MlpbT+REdvnECHkoXEGLw26aybgUqk1c=";
    installPath = "https/repo1.maven.org/maven2/org/ow2/asm/asm-commons/9.8";
  };

  "org.ow2.asm_asm-tree-9.8" = fetchMaven {
    name = "org.ow2.asm_asm-tree-9.8";
    urls = [ "https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/9.8/asm-tree-9.8.jar" "https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/9.8/asm-tree-9.8.pom" ];
    hash = "sha256-ZxdFTSgXy5f+gdS/FvxW+0oyf+5+RFUm3hv7G0akkQk=";
    installPath = "https/repo1.maven.org/maven2/org/ow2/asm/asm-tree/9.8";
  };

  "org.ow2.asm_asm-tree-9.9.1" = fetchMaven {
    name = "org.ow2.asm_asm-tree-9.9.1";
    urls = [ "https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/9.9.1/asm-tree-9.9.1.jar" "https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/9.9.1/asm-tree-9.9.1.pom" ];
    hash = "sha256-LhQJl+rcgdsz0DJoyYqQstX1+Z8jpE5deD+rlpKZU10=";
    installPath = "https/repo1.maven.org/maven2/org/ow2/asm/asm-tree/9.9.1";
  };

  "org.scala-lang.modules_scala-asm-9.7.0-scala-2" = fetchMaven {
    name = "org.scala-lang.modules_scala-asm-9.7.0-scala-2";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-asm/9.7.0-scala-2/scala-asm-9.7.0-scala-2.jar" "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-asm/9.7.0-scala-2/scala-asm-9.7.0-scala-2.pom" ];
    hash = "sha256-yazixBmwEaFEABrjyNXbIEXxfdreoZW8T3NmFFM7sns=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-asm/9.7.0-scala-2";
  };

  "org.scala-lang.modules_scala-asm-9.9.0-scala-1" = fetchMaven {
    name = "org.scala-lang.modules_scala-asm-9.9.0-scala-1";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-asm/9.9.0-scala-1/scala-asm-9.9.0-scala-1.jar" "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-asm/9.9.0-scala-1/scala-asm-9.9.0-scala-1.pom" ];
    hash = "sha256-0zHgDkd1xWwpw896w+ayT2x7L4YmtTgA3NcObdySv3c=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-asm/9.9.0-scala-1";
  };

  "org.scala-lang.modules_scala-collection-compat_2.13-2.13.0" = fetchMaven {
    name = "org.scala-lang.modules_scala-collection-compat_2.13-2.13.0";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.13.0/scala-collection-compat_2.13-2.13.0.jar" "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.13.0/scala-collection-compat_2.13-2.13.0.pom" ];
    hash = "sha256-aQ+I3JuE8U5GIdb4SlHbZWdPu4E/qRIoZSGMMP3g5GE=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.13.0";
  };

  "org.scala-lang.modules_scala-collection-compat_3-2.12.0" = fetchMaven {
    name = "org.scala-lang.modules_scala-collection-compat_3-2.12.0";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_3/2.12.0/scala-collection-compat_3-2.12.0.jar" "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_3/2.12.0/scala-collection-compat_3-2.12.0.pom" ];
    hash = "sha256-ne2PoJ4ge4ygNIDFAkpo++XaJNsiGE7gqtT7HbG4gVs=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_3/2.12.0";
  };

  "org.scala-lang.modules_scala-parallel-collections_3-1.2.0" = fetchMaven {
    name = "org.scala-lang.modules_scala-parallel-collections_3-1.2.0";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-parallel-collections_3/1.2.0/scala-parallel-collections_3-1.2.0.jar" "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-parallel-collections_3/1.2.0/scala-parallel-collections_3-1.2.0.pom" ];
    hash = "sha256-v1k+cav2Bl/xAhvOy6AxlyMjbcLH1wI2/2Cd/M6uFyY=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-parallel-collections_3/1.2.0";
  };

  "org.scala-lang.modules_scala-parser-combinators_3-2.1.0" = fetchMaven {
    name = "org.scala-lang.modules_scala-parser-combinators_3-2.1.0";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-parser-combinators_3/2.1.0/scala-parser-combinators_3-2.1.0.jar" "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-parser-combinators_3/2.1.0/scala-parser-combinators_3-2.1.0.pom" ];
    hash = "sha256-hsgwr5S9JNBoRdLgmEGzyBbA3i2uFyBPqUDQJEtMmsg=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-parser-combinators_3/2.1.0";
  };

  "org.scala-lang.modules_scala-xml_2.13-2.4.0" = fetchMaven {
    name = "org.scala-lang.modules_scala-xml_2.13-2.4.0";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.4.0/scala-xml_2.13-2.4.0.jar" "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.4.0/scala-xml_2.13-2.4.0.pom" ];
    hash = "sha256-e5pQSejMXF2nSlmD8wBFRkxcRN+8nEHW/89qN0Je0dY=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.4.0";
  };

  "org.scala-lang.modules_scala-xml_3-2.0.1" = fetchMaven {
    name = "org.scala-lang.modules_scala-xml_3-2.0.1";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_3/2.0.1/scala-xml_3-2.0.1.jar" "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_3/2.0.1/scala-xml_3-2.0.1.pom" ];
    hash = "sha256-OFAf/c4/dKnDP+IEpmXFg6Thbt0voLEge9R6SDZrQIc=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_3/2.0.1";
  };

  "org.scala-lang.modules_scala-xml_3-2.4.0" = fetchMaven {
    name = "org.scala-lang.modules_scala-xml_3-2.4.0";
    urls = [ "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_3/2.4.0/scala-xml_3-2.4.0.jar" "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_3/2.4.0/scala-xml_3-2.4.0.pom" ];
    hash = "sha256-+7nNhpZLvDvMGTITT2eg3S4G87M2HHyqb/AvmNet/l0=";
    installPath = "https/repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_3/2.4.0";
  };

  "org.scala-sbt.jline_jline-2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18" = fetchMaven {
    name = "org.scala-sbt.jline_jline-2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18";
    urls = [ "https://repo1.maven.org/maven2/org/scala-sbt/jline/jline/2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18/jline-2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18.jar" "https://repo1.maven.org/maven2/org/scala-sbt/jline/jline/2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18/jline-2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18.pom" ];
    hash = "sha256-1Nq7/UMXSlaZ7iwR1WMryltAmS8/fRCK6u93cm+1uh4=";
    installPath = "https/repo1.maven.org/maven2/org/scala-sbt/jline/jline/2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18";
  };

  "org.sonatype.oss_oss-parent-5" = fetchMaven {
    name = "org.sonatype.oss_oss-parent-5";
    urls = [ "https://repo1.maven.org/maven2/org/sonatype/oss/oss-parent/5/oss-parent-5.pom" ];
    hash = "sha256-nga0RHiAES0cK5iFNr5AStbaorGJjt2cRMQg2j58uUA=";
    installPath = "https/repo1.maven.org/maven2/org/sonatype/oss/oss-parent/5";
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

  "org.virtuslab.scala-cli_config_3-1.9.1" = fetchMaven {
    name = "org.virtuslab.scala-cli_config_3-1.9.1";
    urls = [ "https://repo1.maven.org/maven2/org/virtuslab/scala-cli/config_3/1.9.1/config_3-1.9.1.jar" "https://repo1.maven.org/maven2/org/virtuslab/scala-cli/config_3/1.9.1/config_3-1.9.1.pom" ];
    hash = "sha256-Ym7Z4pkBcRdYGfIt7waSYYaXm0K9TYt4V6Xda1uOlYk=";
    installPath = "https/repo1.maven.org/maven2/org/virtuslab/scala-cli/config_3/1.9.1";
  };

  "org.virtuslab.scala-cli_specification-level_3-1.9.1" = fetchMaven {
    name = "org.virtuslab.scala-cli_specification-level_3-1.9.1";
    urls = [ "https://repo1.maven.org/maven2/org/virtuslab/scala-cli/specification-level_3/1.9.1/specification-level_3-1.9.1.jar" "https://repo1.maven.org/maven2/org/virtuslab/scala-cli/specification-level_3/1.9.1/specification-level_3-1.9.1.pom" ];
    hash = "sha256-Rd2ZqrhRYnKH1rUZEH6JdezXlJf0JnxjGEoCUCpklJ4=";
    installPath = "https/repo1.maven.org/maven2/org/virtuslab/scala-cli/specification-level_3/1.9.1";
  };

  "software.amazon.awssdk_aws-sdk-java-pom-2.33.4" = fetchMaven {
    name = "software.amazon.awssdk_aws-sdk-java-pom-2.33.4";
    urls = [ "https://repo1.maven.org/maven2/software/amazon/awssdk/aws-sdk-java-pom/2.33.4/aws-sdk-java-pom-2.33.4.pom" ];
    hash = "sha256-6GjiJWtQLWP8CXDVDC5s+j05CulvnNaj3el9sc5dDO8=";
    installPath = "https/repo1.maven.org/maven2/software/amazon/awssdk/aws-sdk-java-pom/2.33.4";
  };

  "software.amazon.awssdk_bom-2.33.4" = fetchMaven {
    name = "software.amazon.awssdk_bom-2.33.4";
    urls = [ "https://repo1.maven.org/maven2/software/amazon/awssdk/bom/2.33.4/bom-2.33.4.pom" ];
    hash = "sha256-99ASsaFUHJpZV0kyf4m18gfg8dBHc/tny5E1WkwQ1XM=";
    installPath = "https/repo1.maven.org/maven2/software/amazon/awssdk/bom/2.33.4";
  };

  "ua.co.k_strftime4j-1.0.5" = fetchMaven {
    name = "ua.co.k_strftime4j-1.0.5";
    urls = [ "https://repo1.maven.org/maven2/ua/co/k/strftime4j/1.0.5/strftime4j-1.0.5.jar" "https://repo1.maven.org/maven2/ua/co/k/strftime4j/1.0.5/strftime4j-1.0.5.pom" ];
    hash = "sha256-Wrg3ftbV/dCtAhULZcti/FJ2XVbpqd9fM4Z6A/fOwAo=";
    installPath = "https/repo1.maven.org/maven2/ua/co/k/strftime4j/1.0.5";
  };

  "com.fasterxml.jackson.core_jackson-annotations-2.12.1" = fetchMaven {
    name = "com.fasterxml.jackson.core_jackson-annotations-2.12.1";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.12.1/jackson-annotations-2.12.1.pom" ];
    hash = "sha256-anUbI5JS/lVsxPul1sdmtNFsJbiyHvyz9au/cBV0L6w=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.12.1";
  };

  "com.fasterxml.jackson.core_jackson-annotations-2.15.1" = fetchMaven {
    name = "com.fasterxml.jackson.core_jackson-annotations-2.15.1";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.15.1/jackson-annotations-2.15.1.jar" "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.15.1/jackson-annotations-2.15.1.pom" ];
    hash = "sha256-hwI7CChHkZif7MNeHDjPf6OuNcncc9i7zIET6JUiSY8=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.15.1";
  };

  "com.fasterxml.jackson.core_jackson-core-2.15.1" = fetchMaven {
    name = "com.fasterxml.jackson.core_jackson-core-2.15.1";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.15.1/jackson-core-2.15.1.jar" "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.15.1/jackson-core-2.15.1.pom" ];
    hash = "sha256-07N9Rg8OKF7hTLa+0AoF1hImT3acHpQBIJhHBnLUSOs=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.15.1";
  };

  "com.fasterxml.jackson.core_jackson-databind-2.15.1" = fetchMaven {
    name = "com.fasterxml.jackson.core_jackson-databind-2.15.1";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.15.1/jackson-databind-2.15.1.jar" "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.15.1/jackson-databind-2.15.1.pom" ];
    hash = "sha256-t8Sge4HbKg8XsqNgW69/3G3RKgK09MSCsPH7XYtsrew=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.15.1";
  };

  "com.fasterxml.jackson.dataformat_jackson-dataformat-yaml-2.15.1" = fetchMaven {
    name = "com.fasterxml.jackson.dataformat_jackson-dataformat-yaml-2.15.1";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/dataformat/jackson-dataformat-yaml/2.15.1/jackson-dataformat-yaml-2.15.1.jar" "https://repo1.maven.org/maven2/com/fasterxml/jackson/dataformat/jackson-dataformat-yaml/2.15.1/jackson-dataformat-yaml-2.15.1.pom" ];
    hash = "sha256-gSpEZqpCXmFGg86xQeOlNRNdyBmiM/rN2kCTGhjhHt4=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/dataformat/jackson-dataformat-yaml/2.15.1";
  };

  "com.fasterxml.jackson.dataformat_jackson-dataformats-text-2.15.1" = fetchMaven {
    name = "com.fasterxml.jackson.dataformat_jackson-dataformats-text-2.15.1";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/dataformat/jackson-dataformats-text/2.15.1/jackson-dataformats-text-2.15.1.pom" ];
    hash = "sha256-1RiIP6cIRZoOMBV2+vmJJOYXMarqg+4l7XQ8S7OvAvg=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/dataformat/jackson-dataformats-text/2.15.1";
  };

  "com.fasterxml.jackson.datatype_jackson-datatype-jsr310-2.12.1" = fetchMaven {
    name = "com.fasterxml.jackson.datatype_jackson-datatype-jsr310-2.12.1";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/datatype/jackson-datatype-jsr310/2.12.1/jackson-datatype-jsr310-2.12.1.jar" "https://repo1.maven.org/maven2/com/fasterxml/jackson/datatype/jackson-datatype-jsr310/2.12.1/jackson-datatype-jsr310-2.12.1.pom" ];
    hash = "sha256-YH7YMZY1aeamRA6aVvF2JG3C1YLZhvaMpVCegAfdhFU=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/datatype/jackson-datatype-jsr310/2.12.1";
  };

  "com.fasterxml.jackson.module_jackson-modules-java8-2.12.1" = fetchMaven {
    name = "com.fasterxml.jackson.module_jackson-modules-java8-2.12.1";
    urls = [ "https://repo1.maven.org/maven2/com/fasterxml/jackson/module/jackson-modules-java8/2.12.1/jackson-modules-java8-2.12.1.pom" ];
    hash = "sha256-x5YmdPGcWOpCompDhApY6o5VZ+IUVHTbeday5HVW/NQ=";
    installPath = "https/repo1.maven.org/maven2/com/fasterxml/jackson/module/jackson-modules-java8/2.12.1";
  };

  "com.github.plokhotnyuk.jsoniter-scala_jsoniter-scala-core_2.13-2.13.5" = fetchMaven {
    name = "com.github.plokhotnyuk.jsoniter-scala_jsoniter-scala-core_2.13-2.13.5";
    urls = [ "https://repo1.maven.org/maven2/com/github/plokhotnyuk/jsoniter-scala/jsoniter-scala-core_2.13/2.13.5/jsoniter-scala-core_2.13-2.13.5.jar" "https://repo1.maven.org/maven2/com/github/plokhotnyuk/jsoniter-scala/jsoniter-scala-core_2.13/2.13.5/jsoniter-scala-core_2.13-2.13.5.pom" ];
    hash = "sha256-uQ7ULWW7il8C1f07v2grRCOzgxDH31UlmtAuL9m/VE8=";
    installPath = "https/repo1.maven.org/maven2/com/github/plokhotnyuk/jsoniter-scala/jsoniter-scala-core_2.13/2.13.5";
  };

  "com.google.code.findbugs_jsr305-3.0.2" = fetchMaven {
    name = "com.google.code.findbugs_jsr305-3.0.2";
    urls = [ "https://repo1.maven.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar" "https://repo1.maven.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.pom" ];
    hash = "sha256-eq7d9gzPBemWTgv/S9uUEKh7A2rqnKOhK0L4/e6N3/s=";
    installPath = "https/repo1.maven.org/maven2/com/google/code/findbugs/jsr305/3.0.2";
  };

  "com.google.code.gson_gson-2.13.2" = fetchMaven {
    name = "com.google.code.gson_gson-2.13.2";
    urls = [ "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar" "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.13.2/gson-2.13.2.pom" ];
    hash = "sha256-+Bc1Lhq2uhj/qsS+7zvNH9rQph6F+FpYMci5O551Yqk=";
    installPath = "https/repo1.maven.org/maven2/com/google/code/gson/gson/2.13.2";
  };

  "com.google.code.gson_gson-parent-2.13.2" = fetchMaven {
    name = "com.google.code.gson_gson-parent-2.13.2";
    urls = [ "https://repo1.maven.org/maven2/com/google/code/gson/gson-parent/2.13.2/gson-parent-2.13.2.pom" ];
    hash = "sha256-prNnb7pxMO5rzfYPJBbCZm0I8nanL4ZhAJita6s0ksM=";
    installPath = "https/repo1.maven.org/maven2/com/google/code/gson/gson-parent/2.13.2";
  };

  "io.github.alexarchambault.native-terminal_native-terminal-no-ffm-0.0.9.1" = fetchMaven {
    name = "io.github.alexarchambault.native-terminal_native-terminal-no-ffm-0.0.9.1";
    urls = [ "https://repo1.maven.org/maven2/io/github/alexarchambault/native-terminal/native-terminal-no-ffm/0.0.9.1/native-terminal-no-ffm-0.0.9.1.jar" "https://repo1.maven.org/maven2/io/github/alexarchambault/native-terminal/native-terminal-no-ffm/0.0.9.1/native-terminal-no-ffm-0.0.9.1.pom" ];
    hash = "sha256-fHtvFUaVlrgdz+S3mPlxXjA4mpSBWC+hjW3U7h5NFo0=";
    installPath = "https/repo1.maven.org/maven2/io/github/alexarchambault/native-terminal/native-terminal-no-ffm/0.0.9.1";
  };

  "io.github.alexarchambault.windows-ansi_windows-ansi-0.0.6" = fetchMaven {
    name = "io.github.alexarchambault.windows-ansi_windows-ansi-0.0.6";
    urls = [ "https://repo1.maven.org/maven2/io/github/alexarchambault/windows-ansi/windows-ansi/0.0.6/windows-ansi-0.0.6.jar" "https://repo1.maven.org/maven2/io/github/alexarchambault/windows-ansi/windows-ansi/0.0.6/windows-ansi-0.0.6.pom" ];
    hash = "sha256-TGUrDCPYFiXV5b2If3u4KviH3JxZttMOKL1HUHqIWRo=";
    installPath = "https/repo1.maven.org/maven2/io/github/alexarchambault/windows-ansi/windows-ansi/0.0.6";
  };

  "net.java.dev.jna_jna-5.15.0" = fetchMaven {
    name = "net.java.dev.jna_jna-5.15.0";
    urls = [ "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.15.0/jna-5.15.0.jar" "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.15.0/jna-5.15.0.pom" ];
    hash = "sha256-7iRfZecKpdVWwZgDlGA+w6bI461J7TB+lr8I5sWiaoY=";
    installPath = "https/repo1.maven.org/maven2/net/java/dev/jna/jna/5.15.0";
  };

  "net.java.dev.jna_jna-5.17.0" = fetchMaven {
    name = "net.java.dev.jna_jna-5.17.0";
    urls = [ "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.17.0/jna-5.17.0.jar" "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.17.0/jna-5.17.0.pom" ];
    hash = "sha256-q4PFCy/O+i1lIiNaf627UILQWmnOgIZTGeMzu5N3M+Q=";
    installPath = "https/repo1.maven.org/maven2/net/java/dev/jna/jna/5.17.0";
  };

  "org.apache.geronimo.genesis_genesis-2.0" = fetchMaven {
    name = "org.apache.geronimo.genesis_genesis-2.0";
    urls = [ "https://repo1.maven.org/maven2/org/apache/geronimo/genesis/genesis/2.0/genesis-2.0.pom" ];
    hash = "sha256-lcX5R64+07kRLqpdfkay87hJI6ykVn/wUXs142Elips=";
    installPath = "https/repo1.maven.org/maven2/org/apache/geronimo/genesis/genesis/2.0";
  };

  "org.apache.geronimo.genesis_genesis-default-flava-2.0" = fetchMaven {
    name = "org.apache.geronimo.genesis_genesis-default-flava-2.0";
    urls = [ "https://repo1.maven.org/maven2/org/apache/geronimo/genesis/genesis-default-flava/2.0/genesis-default-flava-2.0.pom" ];
    hash = "sha256-jkGo9ePZSnxqcIOQIuAz1ZTPNjjx2vc01oxtt6EJuUk=";
    installPath = "https/repo1.maven.org/maven2/org/apache/geronimo/genesis/genesis-default-flava/2.0";
  };

  "org.apache.geronimo.genesis_genesis-java5-flava-2.0" = fetchMaven {
    name = "org.apache.geronimo.genesis_genesis-java5-flava-2.0";
    urls = [ "https://repo1.maven.org/maven2/org/apache/geronimo/genesis/genesis-java5-flava/2.0/genesis-java5-flava-2.0.pom" ];
    hash = "sha256-CTKaQ0fTVeVBnQrWm4TCcbTONXm/N6bPXPGXx0hToLQ=";
    installPath = "https/repo1.maven.org/maven2/org/apache/geronimo/genesis/genesis-java5-flava/2.0";
  };

  "org.apache.logging.log4j_log4j-2.25.1" = fetchMaven {
    name = "org.apache.logging.log4j_log4j-2.25.1";
    urls = [ "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j/2.25.1/log4j-2.25.1.pom" ];
    hash = "sha256-/p3Ev9rCHqqFhrWRclrKjQGmF5Y9tIXZr58Jppzi2tc=";
    installPath = "https/repo1.maven.org/maven2/org/apache/logging/log4j/log4j/2.25.1";
  };

  "org.apache.logging.log4j_log4j-api-2.25.1" = fetchMaven {
    name = "org.apache.logging.log4j_log4j-api-2.25.1";
    urls = [ "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.25.1/log4j-api-2.25.1.jar" "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.25.1/log4j-api-2.25.1.pom" ];
    hash = "sha256-OQnbI65CBVg7XZjgtbIiaa+WgQFo2fmPhyElPFIBZic=";
    installPath = "https/repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.25.1";
  };

  "org.apache.logging.log4j_log4j-bom-2.25.1" = fetchMaven {
    name = "org.apache.logging.log4j_log4j-bom-2.25.1";
    urls = [ "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-bom/2.25.1/log4j-bom-2.25.1.pom" ];
    hash = "sha256-in1oVoyTFGRrT3aPzm0mZl4QiLyNVbTzi9cpiVyjZwo=";
    installPath = "https/repo1.maven.org/maven2/org/apache/logging/log4j/log4j-bom/2.25.1";
  };

  "org.apache.logging.log4j_log4j-core-2.25.1" = fetchMaven {
    name = "org.apache.logging.log4j_log4j-core-2.25.1";
    urls = [ "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.25.1/log4j-core-2.25.1.jar" "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.25.1/log4j-core-2.25.1.pom" ];
    hash = "sha256-K8D+tVs4cZwLxfam8sO+ip6MBF3LtWoF73VmJfYSJZw=";
    installPath = "https/repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.25.1";
  };

}
# Cache Identifier:sha256-9jJ1niQ+wNakC4khuY56Dq5euHx6W/ufTpSmOO138Ms=@Mill Build Tool version 1.1.2
