#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <log_config.h>

using namespace cv;
//ANativeWindow *window = 0;

//extern "C"
//JNIEXPORT void JNICALL
//Java_com_gmy_camera_MainActivity_setSurface(JNIEnv *env, jobject instance,
//                                                    jobject surface) {
//    if (window) {
//        ANativeWindow_release(window);
//        window = 0;
//    }
//    window = ANativeWindow_fromSurface(env, surface);
//}

/*
 * UpdateFrameBuffer():
 *     Internal function to perform bits copying onto current frame buffer
 *     src:
 *        - if nullptr, blank it
 *        - otherwise,  copy to given buf
 *     assumption:
 *         src and bug MUST be in the same geometry format & layout
 */
void UpdateFrameBuffer(ANativeWindow_Buffer *buf, uint8_t *src) {
    // src is either null: to blank the screen
    //     or holding exact pixels with the same fmt [stride is the SAME]
    uint8_t *dst = reinterpret_cast<uint8_t *> (buf->bits);
    uint32_t bpp;
    switch (buf->format) {
        case WINDOW_FORMAT_RGB_565:
            bpp = 2;
            break;
        case WINDOW_FORMAT_RGBA_8888:
        case WINDOW_FORMAT_RGBX_8888:
            bpp = 4;
            break;
        default:
            assert(0);
            return;
    }
    uint32_t stride, width;
    stride = buf->stride * bpp;
    width = buf->width * bpp;
    if (src) {
        for (auto height = 0; height < buf->height; ++height) {
            memcpy(dst, src, width);
            dst += stride, src += width;
        }
    } else {
        for (auto height = 0; height < buf->height; ++height) {
            memset(dst, 0, width);
            dst += stride;
        }
    }
}

//return //millsc second
uint64_t get_timestamp() {
    struct timespec tv;
    uint64_t pts;
    clock_gettime(CLOCK_BOOTTIME, &tv);
    pts = tv.tv_sec;
    pts *= 1000000000;
    pts += tv.tv_nsec;
    pts /= 1000000;
    return pts;
}


extern "C"
JNIEXPORT bool JNICALL
Java_com_gmy_camera_MainActivity_onImageAvailableNative(JNIEnv *env, jclass type,
                                                  jint width, jint height,
                                                  jint rowStrideY,
                                                  jobject bufferY,
                                                  jint rowStrideUV,
                                                  jobject bufferU,
                                                  jobject bufferV,
                                                  jobject surface,
                                                  jlong timeStamp,
                                                  jboolean isScreenRotated,
                                                  jfloat virtualCamDistance) {
    LOGI("Received image with width: %d height: %d timestamp: %ld", width, height,timeStamp);


    uint8_t *srcYPtr = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(bufferY));
    uint8_t *srcUPtr = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(bufferU));
    uint8_t *srcVPtr = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(bufferV));
    if(srcYPtr == NULL || srcVPtr == NULL || srcUPtr == NULL)
    {
        LOGE("blit NULL pointer ERROR");
        return false;
    }

    cv::Mat mYuv(height * 3 / 2, width, CV_8UC1);
    unsigned char *data = mYuv.data;
    memcpy(data,srcYPtr,height * rowStrideY);
    data += height * rowStrideY;
    memcpy(data,srcVPtr,height / 2 * rowStrideUV);

    ANativeWindow *win = ANativeWindow_fromSurface(env, surface);

    ANativeWindow_acquire(win);
    ANativeWindow_Buffer buf;

    int rotatedWidth = height; // 360
    int rotatedHeight = width; // 640

    ANativeWindow_setBuffersGeometry(win, width, height, 0);

    if (int32_t err = ANativeWindow_lock(win, &buf, NULL)) {
        LOGE("ANativeWindow_lock failed with error code %d\n", err);
        ANativeWindow_release(win);
        return false;
    }

//    LOGI("buf.stride: %d", buf.stride);

    uint8_t *dstPtr = reinterpret_cast<uint8_t *>(buf.bits);
    Mat dstRgba(height, buf.stride, CV_8UC4, dstPtr); // TextureView buffer, use stride as width
    Mat srcRgba(height, width, CV_8UC4);
    Mat rotatedRgba(rotatedHeight, rotatedWidth, CV_8UC4);


    // convert YUV to RGBA
    cv::cvtColor(mYuv, srcRgba, CV_YUV2RGBA_NV21);

    // Rotate 90 degree ,顺时针90度
    cv::rotate(srcRgba, rotatedRgba, cv::ROTATE_90_CLOCKWISE);

    //顺时针270度
    cv::rotate(rotatedRgba, srcRgba, cv::ROTATE_90_COUNTERCLOCKWISE);


    char charbuf[22];
    sprintf(charbuf, "gmy");
    cv::putText(srcRgba, charbuf, cv::Point(10, 30), cv::FONT_HERSHEY_PLAIN, 1.5,
                cv::Scalar(255, 0, 255), 2, 8);

    // copy to TextureView surface
    uchar *dbuf = dstRgba.data;
    uchar *sbuf = srcRgba.data;

    for (int i = 0; i < srcRgba.rows; i++) {
        dbuf = dstRgba.data + i * buf.stride * 4;
        memcpy(dbuf, sbuf, srcRgba.cols * 4); //TODO: threw a SIGSEGV SEGV_ACCERR once
        sbuf += srcRgba.cols * 4;
    }

    ANativeWindow_unlockAndPost(win);
    ANativeWindow_release(win);

    bool finishFlag = true;
    return finishFlag;
}
