#include <jni.h>
#include <android/log.h>
#include <fcntl.h>
#include <unistd.h>

namespace {
struct DeviceHandle {
    int fd;
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

    auto *handle = new DeviceHandle{fd};
    return reinterpret_cast<jlong>(handle);
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
