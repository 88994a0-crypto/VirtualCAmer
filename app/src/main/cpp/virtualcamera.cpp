#include <jni.h>
#include <android/log.h>
#include <fcntl.h>
#include <linux/videodev2.h>
#include <sys/ioctl.h>
#include <unistd.h>

namespace {
struct DeviceHandle {
    int fd;
    int width;
    int height;
};

constexpr const char *kLogTag = "VirtualCamera";
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_virtualcamer_VirtualCameraBridge_nativeOpenDevice(
    JNIEnv *env,
    jobject /* thiz */,
    jstring path) {
    if (path == nullptr) {
        return 0L;
    }

    const char *pathChars = env->GetStringUTFChars(path, nullptr);
    if (pathChars == nullptr) {
        return 0L;
    }

    int fd = open(pathChars, O_RDWR | O_NONBLOCK);
    env->ReleaseStringUTFChars(path, pathChars);

    if (fd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Failed to open device");
        return 0L;
    }

    auto *handle = new DeviceHandle{fd, 0, 0};
    return reinterpret_cast<jlong>(handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_virtualcamer_VirtualCameraBridge_nativeConfigureStream(
    JNIEnv *env,
    jobject /* thiz */,
    jlong handle,
    jint width,
    jint height) {
    if (handle == 0 || width <= 0 || height <= 0) {
        return JNI_FALSE;
    }

    auto *deviceHandle = reinterpret_cast<DeviceHandle *>(handle);
    if (deviceHandle->width == width && deviceHandle->height == height) {
        return JNI_TRUE;
    }

    v4l2_format format = {};
    format.type = V4L2_BUF_TYPE_VIDEO_OUTPUT;
    format.fmt.pix.width = static_cast<uint32_t>(width);
    format.fmt.pix.height = static_cast<uint32_t>(height);
    format.fmt.pix.pixelformat = V4L2_PIX_FMT_YUV420;
    format.fmt.pix.field = V4L2_FIELD_NONE;
    format.fmt.pix.bytesperline = static_cast<uint32_t>(width);
    format.fmt.pix.sizeimage =
        static_cast<uint32_t>(width) * static_cast<uint32_t>(height) * 3 / 2;

    if (ioctl(deviceHandle->fd, VIDIOC_S_FMT, &format) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Failed to configure stream");
        return JNI_FALSE;
    }

    deviceHandle->width = width;
    deviceHandle->height = height;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_virtualcamer_VirtualCameraBridge_nativeWriteFrame(
    JNIEnv *env,
    jobject /* thiz */,
    jlong handle,
    jobject buffer,
    jint length) {
    if (handle == 0 || buffer == nullptr || length <= 0) {
        return JNI_FALSE;
    }

    auto *deviceHandle = reinterpret_cast<DeviceHandle *>(handle);
    void *data = env->GetDirectBufferAddress(buffer);
    if (data == nullptr) {
        return JNI_FALSE;
    }

    ssize_t result = write(deviceHandle->fd, data, static_cast<size_t>(length));
    if (result < 0) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Failed to write frame");
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_virtualcamer_VirtualCameraBridge_nativeCloseDevice(
    JNIEnv *env,
    jobject /* thiz */,
    jlong handle) {
    if (handle == 0) {
        return;
    }

    auto *deviceHandle = reinterpret_cast<DeviceHandle *>(handle);
    if (deviceHandle->fd >= 0) {
        close(deviceHandle->fd);
    }
    delete deviceHandle;
}
