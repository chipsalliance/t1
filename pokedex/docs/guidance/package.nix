{
  mkTypstEnv,
  model,
}:
mkTypstEnv {
  name = "pokedex-guidance";

  sources = {
    root = ./.;
    files = [
      ./main.typ
      ./lib.typ
      ./arg_lut.json
    ];
  };

  plugins = [
    # main dependencies
    {
      name = "fletcher";
      version = "0.5.8";
      hash = "sha256-kKVp5WN/EbHEz2GCTkr8i8DRiAdqlr4R7EW6drElgWk=";
    }
    # implicit dependencies of fletcher
    {
      name = "cetz";
      version = "0.3.4";
      hash = "sha256-5w3UYRUSdi4hCvAjrp9HslzrUw7BhgDdeCiDRHGvqd4=";
    }
    {
      name = "oxifmt";
      version = "0.2.1";
      hash = "sha256-8PNPa9TGFybMZ1uuJwb5ET0WGIInmIgg8h24BmdfxlU=";
    }
  ];

  typstPhase = ''
    cp -v ${model}/docs/*.yml .
    # Normal
    typst compile --input release=true main.typ doc.pdf
    # Darkmode
    typst compile \
      --input release=true \
      --input enable_darkmode=true \
      main.typ doc-dark.pdf
  '';
}
