{ buildFHSEnv
  # e.g. /opt/synopsys/vc_static/V-2023.12
, vcStaticInstallPath
  # e.g. port@addr
, snpsLicenseFile
}:
buildFHSEnv {
  name = "vcs-fhs-env";
  profile = ''
    export TCL_TZ=UTC
    export VC_STATIC_HOME=${vcStaticInstallPath}
    export VCS_HOME=${vcStaticInstallPath}/vcs-mx
    export VCS_TARGET_ARCH=amd64
    export VCS_ARCH_OVERRIDE=linux
    export VERDI_HOME=${vcStaticInstallPath}/verdi
    export NOVAS_HOME=${vcStaticInstallPath}/verdi
    export SPYGLASS_HOME=${vcStaticInstallPath}/SG_COMPAT/SPYGLASS_HOME
    export SNPS_VERDI_CBUG_LCA=1
    export SNPSLMD_LICENSE_FILE=${snpsLicenseFile}

    export PATH=${vcStaticInstallPath}/bin:$PATH
    export PATH=${vcStaticInstallPath}/verdi/bin:$PATH
    export PATH=${vcStaticInstallPath}/vcs-mx/bin:$PATH
    export PATH=${vcStaticInstallPath}/SG_COMPAT/SPYGLASS_HOME/bin:$PATH

    export LD_LIBRARY_PATH=/usr/lib64/
    export LD_LIBRARY_PATH=${vcStaticInstallPath}/verdi/share/PLI/lib/LINUX64:$LD_LIBRARY_PATH
    export LD_LIBRARY_PATH=${vcStaticInstallPath}/verdi/share/NPI/lib/LINUX64:$LD_LIBRARY_PATH
  '';
  targetPkgs = (ps: with ps; [
    libGL
    util-linux
    libxcrypt-legacy
    coreutils-full
    ncurses5
    gmp5
    bzip2
    glib
    bc
    time
    elfutils
    ncurses5
    e2fsprogs
    cyrus_sasl
    expat
    sqlite
    (nssmdns.overrideAttrs rec {
      version = "0.14.1";
      src = fetchFromGitHub {
        owner = "avahi";
        repo = "nss-mdns";
        rev = "v${version}";
        hash = "sha256-7RqV0hyfcZ168QfeHVtCJpyP4pI6cMeekJ2hDHNurdA=";
      };
    })
    (libkrb5.overrideAttrs rec {
      version = "1.18.2";
      src = fetchurl {
        url = "https://kerberos.org/dist/krb5/${lib.versions.majorMinor version}/krb5-${version}.tar.gz";
        hash = "sha256-xuTJ7BqYFBw/XWbd8aE1VJBQyfq06aRiDumyIIWHOuA=";
      };
      sourceRoot = "krb5-${version}/src";
    })
    (gnugrep.overrideAttrs rec {
      version = "3.1";
      doCheck = false;
      src = fetchurl {
        url = "mirror://gnu/grep/grep-${version}.tar.xz";
        hash = "sha256-22JcerO7PudXs5JqXPqNnhw5ka0kcHqD3eil7yv3oH4=";
      };
    })
    keyutils
    graphite2
    libpulseaudio
    gcc
    gnumake
    xorg.libX11
    xorg.libXft
    xorg.libXScrnSaver
    xorg.libXext
    xorg.libxcb
    xorg.libXau
    xorg.libXrender
    xorg.libXcomposite
    xorg.libXi
  ]);
}
