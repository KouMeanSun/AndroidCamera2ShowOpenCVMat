#AndroidCamera2ShowOpenCVMat

介绍

#An android camera2 demo project, show how to show c++ opencv draw result mat on application surface synchronized。 一个同步展示android c++ 利用opencv 绘制完图像返回给camera2 展示的 demo 工程。

##1.CMakeLists.txt 代码：

```# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html
# Sets the minimum version of CMake required to build the native library.
cmake_minimum_required(VERSION 3.4.1)
# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
add_library( # Sets the name of the library.
        native-lib
        # Sets the library as a shared library.
        SHARED
        # Provides a relative path to your source file(s).
        native-lib.cpp)
# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.
#find_library( # Sets the name of the path variable.
#        log-lib
#
#        # Specifies the name of the NDK library that
#        # you want CMake to locate.
#        log)
# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.
# 自定义的头文件路径设置
include_directories(${CMAKE_SOURCE_DIR}/include)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -L${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
target_link_libraries(   native-lib log opencv_java3 android )
```

##2.jniLibs里要放入opencv 的 so库

##3.java层主要代码：

```
//RGBA output
            Image.Plane Y_plane = image.getPlanes()[0];   //Y
            int Y_rowStride = Y_plane.getRowStride();
            Image.Plane U_plane = image.getPlanes()[1];   //U
            int UV_rowStride = U_plane.getRowStride();
            Image.Plane V_plane = image.getPlanes()[2];   //V
            // pass the current device's screen orientation to the c++ part
            int currentRotation = getWindowManager().getDefaultDisplay().getRotation();
            boolean isScreenRotated = currentRotation != Surface.ROTATION_90;
            long imageTimestamp = image.getTimestamp() + cameraTimestampsShiftWrtSensors;
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            Log.i(TAG, "image width: " + imageWidth + " height: " + imageHeight);
            // pass image to c++ part
            onImageAvailableNative(imageWidth,
                    imageHeight,
                    Y_rowStride,
                    Y_plane.getBuffer(),
                    UV_rowStride,
                    U_plane.getBuffer(),
                    V_plane.getBuffer(),
                    mSurface,
                    imageTimestamp,
                    isScreenRotated,
                    virtualCamDistance);
c++层主要代码：
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
    
```
    
##4:主要原理就是通过JNI传递一个Surface给c++，然后创建ANativeWindow，通过ANativeWindow锁住画布，然后opencv 绘制画面，绘制完毕之后 ANativeWindow再 解锁，post出去，把画面数据发送给Surface对应的TextureView。这样就能够实现同步显示c++绘制的图像了。
   
   
##5.Demo链接：[码云链接](https://gitee.com/KouMeanSun/android-camera2-show-open-cvmat/new/master?readme=true) ，[gitHub链接](https://github.com/KouMeanSun/AndroidCamera2ShowOpenCVMat) 
