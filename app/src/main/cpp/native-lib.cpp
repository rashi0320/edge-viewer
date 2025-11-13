#include <jni.h>
#include <vector>
#include <android/log.h>
#include <opencv2/opencv.hpp>

#define LOG_TAG "native-lib"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_example_edgeviewer_CameraPreview_nativeProcessFrame(JNIEnv *env, jobject thiz,
                                                             jbyteArray nv21, jint width, jint height) {
    jbyte *nv21_bytes = env->GetByteArrayElements(nv21, NULL);
    if (nv21_bytes == NULL) {
        return NULL;
    }

    // NV21 -> cv::Mat
    cv::Mat yuv(height + height / 2, width, CV_8UC1, (unsigned char *) nv21_bytes);
    cv::Mat bgr;
    try {
        cv::cvtColor(yuv, bgr, cv::COLOR_YUV2BGR_NV21);
    } catch (cv::Exception &e) {
        ALOGE("cvtColor error: %s", e.what());
    }

    cv::Mat gray, edges;
    cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
    cv::Canny(gray, edges, 50, 150);

    cv::Mat rgba;
    cv::cvtColor(edges, rgba, cv::COLOR_GRAY2RGBA);

    int outSize = rgba.total() * rgba.elemSize();
    jbyteArray out = env->NewByteArray(outSize);
    env->SetByteArrayRegion(out, 0, outSize, reinterpret_cast<jbyte *>(rgba.data));

    env->ReleaseByteArrayElements(nv21, nv21_bytes, 0);
    return out;
}
