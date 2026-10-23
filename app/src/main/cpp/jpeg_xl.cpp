// JPEG XL's lossless JPEG recompression, for the photographs in a backup. JpegXl.kt says why.
//
// Single-threaded on purpose. With libjxl's thread pool spread over a phone's big and little
// cores, the thirty test photographs took 35 s to pack on a Pixel 10; on one thread, which the
// scheduler keeps on a big core, 8.4 s.

#include <jni.h>

#include <cstdint>
#include <vector>

#include <jxl/decode_cxx.h>
#include <jxl/encode_cxx.h>

namespace {

// libjxl's default. Effort 9 saved 0.3 % more of the thirty photographs and took 19 times as long.
constexpr int kEffort = 7;

std::vector<uint8_t> Pack(const std::vector<uint8_t>& jpeg) {
  auto encoder = JxlEncoderMake(nullptr);
  if (!encoder) return {};
  // The reconstruction data: everything a byte-identical JPEG needs beyond the pixels.
  if (JxlEncoderStoreJPEGMetadata(encoder.get(), JXL_TRUE) != JXL_ENC_SUCCESS) return {};
  JxlEncoderFrameSettings* settings = JxlEncoderFrameSettingsCreate(encoder.get(), nullptr);
  if (settings == nullptr) return {};
  if (JxlEncoderFrameSettingsSetOption(settings, JXL_ENC_FRAME_SETTING_EFFORT, kEffort) != JXL_ENC_SUCCESS) return {};
  if (JxlEncoderAddJPEGFrame(settings, jpeg.data(), jpeg.size()) != JXL_ENC_SUCCESS) return {};
  JxlEncoderCloseInput(encoder.get());
  std::vector<uint8_t> out(jpeg.size() / 2 + 4096);
  uint8_t* next = out.data();
  size_t available = out.size();
  while (true) {
    JxlEncoderStatus status = JxlEncoderProcessOutput(encoder.get(), &next, &available);
    if (status == JXL_ENC_SUCCESS) break;
    if (status != JXL_ENC_NEED_MORE_OUTPUT) return {};
    size_t used = next - out.data();
    out.resize(out.size() * 2);
    next = out.data() + used;
    available = out.size() - used;
  }
  out.resize(next - out.data());
  return out;
}

std::vector<uint8_t> Unpack(const std::vector<uint8_t>& jxl) {
  auto decoder = JxlDecoderMake(nullptr);
  if (!decoder) return {};
  if (JxlDecoderSubscribeEvents(decoder.get(), JXL_DEC_JPEG_RECONSTRUCTION | JXL_DEC_FULL_IMAGE) != JXL_DEC_SUCCESS) return {};
  if (JxlDecoderSetInput(decoder.get(), jxl.data(), jxl.size()) != JXL_DEC_SUCCESS) return {};
  JxlDecoderCloseInput(decoder.get());
  std::vector<uint8_t> out;
  bool reconstructing = false;
  while (true) {
    JxlDecoderStatus status = JxlDecoderProcessInput(decoder.get());
    if (status == JXL_DEC_JPEG_RECONSTRUCTION) {
      reconstructing = true;
      out.resize(jxl.size() * 2 + 65536);
      if (JxlDecoderSetJPEGBuffer(decoder.get(), out.data(), out.size()) != JXL_DEC_SUCCESS) return {};
    } else if (status == JXL_DEC_JPEG_NEED_MORE_OUTPUT) {
      size_t used = out.size() - JxlDecoderReleaseJPEGBuffer(decoder.get());
      out.resize(out.size() * 2);
      if (JxlDecoderSetJPEGBuffer(decoder.get(), out.data() + used, out.size() - used) != JXL_DEC_SUCCESS) return {};
    } else if (status == JXL_DEC_FULL_IMAGE && reconstructing) {
      out.resize(out.size() - JxlDecoderReleaseJPEGBuffer(decoder.get()));
      return out;
    } else {
      // An error, or a file without reconstruction data asking for a pixel buffer instead.
      return {};
    }
  }
}

std::vector<uint8_t> Bytes(JNIEnv* env, jbyteArray array) {
  std::vector<uint8_t> bytes(env->GetArrayLength(array));
  env->GetByteArrayRegion(array, 0, static_cast<jsize>(bytes.size()), reinterpret_cast<jbyte*>(bytes.data()));
  return bytes;
}

jbyteArray Array(JNIEnv* env, const std::vector<uint8_t>& bytes) {
  if (bytes.empty()) return nullptr;
  jbyteArray array = env->NewByteArray(static_cast<jsize>(bytes.size()));
  if (array == nullptr) {
    // A failed allocation leaves an OutOfMemoryError pending. The null already says "not packed",
    // and the caller stores the file as it is, so the error is cleared rather than thrown.
    env->ExceptionClear();
    return nullptr;
  }
  env->SetByteArrayRegion(array, 0, static_cast<jsize>(bytes.size()), reinterpret_cast<const jbyte*>(bytes.data()));
  return array;
}

}  // namespace

// The vectors above throw std::bad_alloc on a large photograph, and an exception that leaves an
// extern "C" function ends the process. Nothing may cross: a failure is a null, as a JPEG that
// libjxl refuses already is, and the photograph goes into the backup as it is.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_peringer_limen_data_JpegXl_pack(JNIEnv* env, jobject, jbyteArray jpeg) {
  try {
    return Array(env, Pack(Bytes(env, jpeg)));
  } catch (...) {
    return nullptr;
  }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_peringer_limen_data_JpegXl_unpack(JNIEnv* env, jobject, jbyteArray jxl) {
  try {
    return Array(env, Unpack(Bytes(env, jxl)));
  } catch (...) {
    return nullptr;
  }
}
