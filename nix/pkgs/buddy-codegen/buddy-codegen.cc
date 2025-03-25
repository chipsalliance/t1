#include <argparse/argparse.hpp>
#include <buddy/Core/Container.h>
#include <buddy/DIP/DIP.h>
#include <buddy/DIP/ImgContainer.h>
#include <filesystem>
#include <fstream>
#include <iomanip> // For precision control
#include <sstream>
#include <string>

template <typename T>
std::string generate_c_array_code(T *data, size_t size,
                                  const std::string &declare,
                                  const std::string &array_name) {
  std::ostringstream oss;
  oss << declare << " " << array_name << "[" << size << "] = {\n    ";
  for (size_t i = 0; i < size; ++i) {
    oss << std::fixed << std::setprecision(6) << data[i];
    if (i != size - 1)
      oss << ", ";
    if ((i + 1) % 10 == 0)
      oss << "\n    ";
  }

  oss << "\n};\n";
  return oss.str();
}

int main(int argc, char *argv[]) {
  argparse::ArgumentParser program("buddy-codegen");

  argparse::ArgumentParser img_cmd("img");
  img_cmd.add_description("Convert image to MLIR memref C code");
  img_cmd.add_argument("-i", "--input")
      .required()
      .help("specify the input file");
  img_cmd.add_argument("-o", "--output")
      .required()
      .help("specify the output file.");
  // TODO: support other format
  img_cmd.add_argument("-m", "--image-mode").default_value("rgb");

  argparse::ArgumentParser arg_cmd("arg");
  arg_cmd.add_description("Convert PyTorch parameter to MLIR memref C code");
  arg_cmd.add_argument("-i", "--input")
      .required()
      .help("specify the input file");
  arg_cmd.add_argument("-o", "--output")
      .required()
      .help("specify the output file.");
  arg_cmd.add_argument("-s", "--size")
      .scan<'d', size_t>()
      .required()
      .help("specify the parameter size.");

  program.add_subparser(img_cmd);
  program.add_subparser(arg_cmd);

  try {
    program.parse_args(argc, argv);
  } catch (const std::exception &err) {
    std::cerr << err.what() << std::endl;
    std::cerr << program;
    std::exit(1);
  }

  if (program.is_subcommand_used(img_cmd)) {
    std::string imgPath = img_cmd.get("input");
    std::ofstream outPath;
    outPath.open(img_cmd.get("output"));

    std::cout << "[buddy-codegen] Loading image..." << std::endl;
    dip::Image<float, 4> input(imgPath, dip::DIP_RGB, true);
    // TODO read the col, row using PNG lib
    MemRef<float, 4> inputResize = dip::Resize4D_NCHW(
        &input, dip::INTERPOLATION_TYPE::BILINEAR_INTERPOLATION,
        {1, 3, 224, 224} /*{image_cols, image_rows}*/);
    auto sizes = inputResize.getSizes();
    outPath << "#include <cstdint>" << std::endl;
    outPath << generate_c_array_code(sizes, 4, "extern const int32_t",
                                     "IMAGE_SIZES")
            << std::endl;
    outPath << generate_c_array_code(
                   inputResize.getData(), inputResize.getSize(),
                   "__attribute((section(\".vdata\"))) float", "IMAGE")
            << std::endl;
    outPath.close();
    std::cout << "[buddy-codegen] code generated" << std::endl;
  } else if (program.is_subcommand_used(arg_cmd)) {
    std::string argPath = arg_cmd.get("input");
    std::ofstream outPath;
    outPath.open(arg_cmd.get("output"));
    auto params_size = arg_cmd.get<size_t>("size");

    MemRef<float, 1> params({params_size});

    const auto loadStart = std::chrono::high_resolution_clock::now();
    // Open the parameter file in binary mode.
    std::ifstream paramFile(argPath, std::ios::in | std::ios::binary);
    if (!paramFile.is_open()) {
      throw std::runtime_error("[Error] Failed to open params file!");
    }
    std::cout << "[buddy-codegen] Loading params..." << std::endl;
    // Read the parameter data into the provided memory reference.
    paramFile.read(reinterpret_cast<char *>(params.getData()),
                   sizeof(float) * (params.getSize()));
    if (paramFile.fail()) {
      throw std::runtime_error("Error occurred while reading params file!");
    }
    paramFile.close();

    outPath << "#include <cstdint>" << std::endl;
    outPath << generate_c_array_code(params.getSizes(), 1,
                                     "extern const int32_t", "PARAMS_SIZES")
            << std::endl;
    outPath << generate_c_array_code(
                   params.getData(), params.getSize(),
                   "extern __attribute((section(\".vdata\"))) float", "PARAMS")
            << std::endl;
    outPath.close();

    const auto loadEnd = std::chrono::high_resolution_clock::now();
    const std::chrono::duration<double, std::milli> loadTime =
        loadEnd - loadStart;
    std::cout << "[buddy-codegen] Params load time: "
              << (double)(loadTime.count()) / 1000 << "s\n"
              << std::endl;
  }
}
