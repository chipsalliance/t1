//===- ImgContainer.h -----------------------------------------------------===//
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
//===----------------------------------------------------------------------===//
//
// original source from https://github.com/buddy-compiler/buddy-mlir
//

#ifndef IMG_H
#define IMG_H

#include "memref.hpp"

enum ImageModes {
  DIP_GRAYSCALE = 0,
  DIP_RGB = 1,
};

template <typename T, size_t N> class Image : public MemRef<T, N> {
public:
  // Constructor initializes the image by loading from a file.
  // Params:
  //   file: Raw data to image file memory
  //   filesize: size of the file memory
  //   buffer: pre-allocated space for image modification
  //   mode: Specifies the image mode (e.g., DIP_GRAYSCALE, DIP_RGB).
  //   norm: Indicates whether to normalize pixel values (default is false).
  constexpr Image(const char *file, int32_t filesize, T *buffer,
                  ImageModes mode, bool norm = false);

  // Overload
  constexpr Image(T *data, const int32_t sizes[N]);
  constexpr Image(T *data, T init, const int32_t sizes[N]);

  // Retrieves the name of the current image format as a string.
  inline const char *getFormatName() const {
    switch (this->imageFormat) {
    case ImageFormat::BMP:
      return "BMP";
    default:
      return "Unsupported format";
    }
  }
  // Returns the width of the image in pixels.
  inline size_t getWidth() const { return this->width; }
  // Returns the height of the image in pixels.
  inline size_t getHeight() const { return this->height; }
  // Returns the bit depth of the image.
  inline int getBitDepth() const { return this->bitDepth; }

private:
  // Enum to represent supported image formats.
  enum class ImageFormat {
    BMPDecodeError, // Represents an error or unsupported format.
    BMP,            // BMP file format.
    Unsupported,
  } imageFormat;
  // Mode of the image (e.g., DIP_GRAYSCALE, DIP_RGB).
  ImageModes imageMode;
  // Width of the image in pixels.
  int32_t width;
  // Height of the image in pixels.
  int32_t height;
  // Bit depth of the image.
  int32_t bitDepth;
  // Normalization flag.
  bool isNorm;
  // Determines the image format from raw file data.
  inline void determineFormat(const uint8_t *fileData, uint32_t filesize);
  // Decodes a BMP image from raw file data.
  inline bool decodeBMP(const uint8_t *fileData, uint32_t filesize);
};

template <typename T, std::size_t N>
constexpr Image<T, N>::Image(T *data, const int32_t sizes[N])
    : MemRef<T, N>(data, sizes){};

template <typename T, std::size_t N>
constexpr Image<T, N>::Image(T *data, T init, const int32_t sizes[N])
    : MemRef<T, N>(data, init, sizes){};

// Image Container Constructor
// Constructs an image container object from the image file path.
template <typename T, std::size_t N>
constexpr Image<T, N>::Image(const char *file, int32_t filesize, T *buffer,
                             ImageModes mode, bool norm)
    : imageMode(mode), isNorm(norm) {

  this->allocated = buffer;
  this->aligned = this->allocated;

  determineFormat(file, filesize);
  if (this->imageFormat == ImageFormat::BMP) {
    bool success = decodeBMP(file, filesize);
    if (!success) {
      this->imageFormat = ImageFormat::BMPDecodeError;
    };
  } else {
    this->imageFormat = ImageFormat::Unsupported;
  }
}

// Determines the image format by inspecting the header of the file data.
template <typename T, std::size_t N>
inline void Image<T, N>::determineFormat(const uint8_t *fileData,
                                         uint32_t filesize) {
  if (filesize > 2 && fileData[0] == 'B' && fileData[1] == 'M') {
    this->imageFormat = ImageFormat::BMP;
  } else {
    this->imageFormat = ImageFormat::BMPDecodeError;
  }
}

// BMP Image File Decoder
template <typename T, std::size_t N>
inline bool Image<T, N>::decodeBMP(const uint8_t *fileData, uint32_t filesize) {
  // Check if the provided data is large enough to contain a minimal BMP header
  // (54 bytes).
  if (filesize < 54) {
    return false;
  }

  // Extract image information from BMP header
  this->width = *reinterpret_cast<const int32_t *>(&fileData[18]);
  this->height = *reinterpret_cast<const int32_t *>(&fileData[22]);
  this->bitDepth = *reinterpret_cast<const uint16_t *>(&fileData[28]);
  uint32_t compression = *reinterpret_cast<const uint32_t *>(&fileData[30]);
  size_t pixelDataOffset = *reinterpret_cast<const uint32_t *>(&fileData[10]);

  // Currently, only the BI_RGB (value 0) compression method is supported.
  if (compression != 0) {
    return false;
  }

  // Currently, only the NCHW format with 4 dimensions is supported.
  if (N == 4) {
    if (this->imageMode == ImageModes::DIP_GRAYSCALE) {
      // TODO: Add batch setting.
      this->sizes[0] = 1;
      this->sizes[1] = 1;
      this->sizes[2] = this->height;
      this->sizes[3] = this->width;
      this->setStrides();
      size_t size = this->product(this->sizes);
      // Fullfill data to memref container.
      size_t memrefIndex = 0;
      if (this->bitDepth == 32) {
        // BMP file is upside-down storage.
        for (size_t i = this->height; i > 0; i--) {
          for (size_t j = 0; j < this->width; j++) {
            // Locate the current pixel.
            size_t pixelIndex =
                pixelDataOffset + (((i - 1) * this->width) + j) * 4;
            // Extract the blue, green, and red value from the current pixel.
            int bluePixel =
                *reinterpret_cast<const uint8_t *>(&fileData[pixelIndex]);
            int greenPixel =
                *reinterpret_cast<const uint8_t *>(&fileData[pixelIndex + 1]);
            int redPixel =
                *reinterpret_cast<const uint8_t *>(&fileData[pixelIndex + 2]);
            // Calculate the gray scale value.
            int grayScaleValue = static_cast<int>(
                0.299 * redPixel + 0.587 * greenPixel + 0.114 * bluePixel);
            // Store the gray scale value into memref container.
            this->aligned[memrefIndex] =
                this->isNorm ? static_cast<T>(grayScaleValue) / 255
                             : static_cast<T>(grayScaleValue);
            memrefIndex++;
          }
        }
      } else {
        return false;
      }
    } else if (this->imageMode == ImageModes::DIP_RGB) {
      // TODO: Add batch setting.
      this->sizes[0] = 1;
      this->sizes[1] = 3;
      this->sizes[2] = this->height;
      this->sizes[3] = this->width;
      this->setStrides();
      size_t size = this->product(this->sizes);
      // Fullfill data to memref container.
      size_t memrefIndex = 0;
      size_t colorStride = this->height * this->width;
      if (this->bitDepth == 32) {
        // BMP file is upside-down storage.
        for (size_t i = height; i > 0; i--) {
          for (size_t j = 0; j < width; j++) {
            // Locate the current pixel.
            size_t pixelIndex = pixelDataOffset + (((i - 1) * width) + j) * 4;
            // Extract the blue, green, and red value from the current pixel.
            int bluePixel =
                *reinterpret_cast<const uint8_t *>(&fileData[pixelIndex]);
            int greenPixel =
                *reinterpret_cast<const uint8_t *>(&fileData[pixelIndex + 1]);
            int redPixel =
                *reinterpret_cast<const uint8_t *>(&fileData[pixelIndex + 2]);
            // Store the values into memref container as RGB order. (BGR -> RGB)
            this->aligned[memrefIndex] = this->isNorm
                                             ? static_cast<T>(redPixel) / 255
                                             : static_cast<T>(redPixel);
            this->aligned[memrefIndex + colorStride] =
                this->isNorm ? static_cast<T>(greenPixel) / 255
                             : static_cast<T>(greenPixel);
            this->aligned[memrefIndex + 2 * colorStride] =
                this->isNorm ? static_cast<T>(bluePixel) / 255
                             : static_cast<T>(bluePixel);
            memrefIndex++;
          }
        }
      } else {
        return false;
      }
    }
  } else {
    return false;
  }
  return true;
}

#endif // IMG_H
