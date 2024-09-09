{ buildFHSEnv
, vcStaticHome
, snpslmdLicenseFile
}:
buildFHSEnv {
  name = "vcs-fhs-env";

  profile = ''
    [ ! -e "${vcStaticHome}"  ] && echo "env VC_STATIC_HOME not set" && exit 1
    [ ! -d "${vcStaticHome}"  ] && echo "VC_STATIC_HOME not accessible" && exit 1
    [ -z "${snpslmdLicenseFile}"  ] && echo "env SNPS LICENSE not set" && exit 1
    export VC_STATIC_HOME=${vcStaticHome}

    export TCL_TZ=UTC
    export VC_STATIC_HOME=$VC_STATIC_HOME
    export VCS_HOME=$VC_STATIC_HOME/vcs-mx
    export VCS_TARGET_ARCH=amd64
    export VCS_ARCH_OVERRIDE=linux
    export VERDI_HOME=$VC_STATIC_HOME/verdi
    export NOVAS_HOME=$VC_STATIC_HOME/verdi
    export SPYGLASS_HOME=$VC_STATIC_HOME/SG_COMPAT/SPYGLASS_HOME
    export SNPS_VERDI_CBUG_LCA=1
    export SNPSLMD_LICENSE_FILE=${snpslmdLicenseFile}

    export PATH=$VC_STATIC_HOME/bin:$PATH
    export PATH=$VC_STATIC_HOME/verdi/bin:$PATH
    export PATH=$VC_STATIC_HOME/vcs-mx/bin:$PATH
    export PATH=$VC_STATIC_HOME/SG_COMPAT/SPYGLASS_HOME/bin:$PATH

    export LD_LIBRARY_PATH=/usr/lib64/
    export LD_LIBRARY_PATH=$VC_STATIC_HOME/verdi/share/PLI/lib/LINUX64:$LD_LIBRARY_PATH
    export LD_LIBRARY_PATH=$VC_STATIC_HOME/verdi/share/NPI/lib/LINUX64:$LD_LIBRARY_PATH

    export _oldVcsEnvPath="$PATH"
    preHook() {
      PATH="$PATH:$_oldVcsEnvPath"
    }
    export -f preHook
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
    nssmdns
    (krb5.overrideAttrs rec {
      version = "1.18.2";
      src = fetchurl {
        url = "https://kerberos.org/dist/krb5/${lib.versions.majorMinor version}/krb5-${version}.tar.gz";
        hash = "sha256-xuTJ7BqYFBw/XWbd8aE1VJBQyfq06aRiDumyIIWHOuA=";
      };
      sourceRoot = "krb5-${version}/src";
      # error: assignment discards 'const' qualifier from pointer target type:
      #   https://gcc.gnu.org/onlinedocs/gcc/Warning-Options.html#index-Wdiscarded-qualifiers-Werror=discarded-qualifiers8
      #
      # Error when compiling with OpenSSL header, should be fixed in new version.
      # But we need to keep at 1.18.2, so here is the dirty workaround.
      env.NIX_CFLAGS_COMPILE = "-Wno-discarded-qualifiers";
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
    libxml2
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
    zlib
  ]);
}
