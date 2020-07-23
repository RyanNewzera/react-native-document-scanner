package com.documentscanner;

import com.documentscanner.views.MainView;
import com.documentscanner.ImageProcessor;
import com.documentscanner.helpers.Quadrilateral;
import com.documentscanner.helpers.ScannedDocument;
import com.documentscanner.helpers.CustomOpenCVLoader;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import android.util.Base64;
import android.graphics.ImageDecoder;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.MatOfByte;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.core.CvType;
import org.opencv.android.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;


/**
 * Created by andre on 28/11/2017.
 */

public class DocumentScannerModule extends ReactContextBaseJavaModule{

    private String TAG = "RECTANGLE COORDINATES: ";
    ReactApplicationContext myContext;

    public DocumentScannerModule(ReactApplicationContext reactContext){
        super(reactContext);
        myContext = reactContext;
    }

    @Override
    public String getName() {
        return "RNPdfScannerManager";
    }

    @ReactMethod
    public void capture() {
        MainView view = MainView.getInstance();
        view.capture();
    }

    public Mat getMatFromURI(Uri imageUri) {
        BitmapFactory.Options bmpFactoryOptions = new BitmapFactory.Options();
        bmpFactoryOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

        ImageDecoder.Source source = ImageDecoder.createSource(myContext.getContentResolver(), imageUri);
        try {
            Bitmap bmp = ImageDecoder.decodeBitmap(source);
            Size size = new Size(bmp.getWidth(), bmp.getHeight());

            Mat obj = new Mat(size, CvType.CV_8UC4);
            Utils.bitmapToMat(bmp, obj);

            return obj;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private ScannedDocument detectDocument(Mat inputRgba) {
        ArrayList<MatOfPoint> contours = findContours(inputRgba);

        ScannedDocument sd = new ScannedDocument(inputRgba);

        sd.originalSize = inputRgba.size();
        Quadrilateral quad = getQuadrilateral(contours, sd.originalSize);

        double ratio = sd.originalSize.height / 500;
        sd.heightWithRatio = Double.valueOf(sd.originalSize.width / ratio).intValue();
        sd.widthWithRatio = Double.valueOf(sd.originalSize.height / ratio).intValue();

        if (quad != null) {
            Log.d(TAG, "Quad Detected");
            sd.originalPoints = new Point[4];

            sd.originalPoints[0] = new Point(sd.widthWithRatio - quad.points[3].y, quad.points[3].x); // Topleft
            sd.originalPoints[1] = new Point(sd.widthWithRatio - quad.points[0].y, quad.points[0].x); // TopRight
            sd.originalPoints[2] = new Point(sd.widthWithRatio - quad.points[1].y, quad.points[1].x); // BottomRight
            sd.originalPoints[3] = new Point(sd.widthWithRatio - quad.points[2].y, quad.points[2].x); // BottomLeft

            sd.quadrilateral = quad;

            Size mPreviewSize = inputRgba.size();

            Point[] rescaledPoints = new Point[4];

            for (int i = 0; i < 4; i++) {
                int x = Double.valueOf(quad.points[i].x * ratio).intValue();
                int y = Double.valueOf(quad.points[i].y * ratio).intValue();
                if (true) {
                    rescaledPoints[(i + 2) % 4] = new Point(Math.abs(x - mPreviewSize.width),
                            Math.abs(y - mPreviewSize.height));
                } else {
                    rescaledPoints[i] = new Point(x, y);
                }
            }

            sd.originalPoints = rescaledPoints;
            sd.previewPoints = sd.originalPoints;
        } else {
            Log.d(TAG, "Quad Not Detected");
        }

        return sd;
    }

    private boolean insideArea(Point[] rp, Size size) {

        int width = Double.valueOf(size.width).intValue();
        int height = Double.valueOf(size.height).intValue();

        int minimumSize = width / 3;

        boolean isANormalShape = rp[0].x != rp[1].x && rp[1].y != rp[0].y && rp[2].y != rp[3].y && rp[3].x != rp[2].x;
        boolean isBigEnough = ((rp[1].x - rp[0].x >= minimumSize) && (rp[2].x - rp[3].x >= minimumSize)
                && (rp[3].y - rp[0].y >= minimumSize) && (rp[2].y - rp[1].y >= minimumSize));

        double leftOffset = rp[0].x - rp[3].x;
        double rightOffset = rp[1].x - rp[2].x;
        double bottomOffset = rp[0].y - rp[1].y;
        double topOffset = rp[2].y - rp[3].y;

        minimumSize = width / 5;

        boolean isAnActualRectangle = ((leftOffset <= minimumSize && leftOffset >= -minimumSize)
                && (rightOffset <= minimumSize && rightOffset >= -minimumSize)
                && (bottomOffset <= minimumSize && bottomOffset >= -minimumSize)
                && (topOffset <= minimumSize && topOffset >= -minimumSize));

        return isANormalShape && isAnActualRectangle && isBigEnough;
    }

    private Quadrilateral getQuadrilateral(ArrayList<MatOfPoint> contours, Size srcSize) {

        double ratio = srcSize.height / 500;
        int height = Double.valueOf(srcSize.height / ratio).intValue();
        int width = Double.valueOf(srcSize.width / ratio).intValue();
        Size size = new Size(width, height);

        for (MatOfPoint c : contours) {
            MatOfPoint2f c2f = new MatOfPoint2f(c.toArray());
            double peri = Imgproc.arcLength(c2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true);

            Point[] points = approx.toArray();

            // select biggest 4 angles polygon
            // if (points.length == 4) {
            Point[] foundPoints = sortPoints(points);

            if (insideArea(foundPoints, size)) {
                return new Quadrilateral(c, foundPoints);
            }
            // }
        }

        return null;
    }

    private Point[] sortPoints(Point[] src) {

        ArrayList<Point> srcPoints = new ArrayList<>(Arrays.asList(src));

        Point[] result = { null, null, null, null };

        Comparator<Point> sumComparator = new Comparator<Point>() {
            @Override
            public int compare(Point lhs, Point rhs) {
                return Double.valueOf(lhs.y + lhs.x).compareTo(rhs.y + rhs.x);
            }
        };

        Comparator<Point> diffComparator = new Comparator<Point>() {

            @Override
            public int compare(Point lhs, Point rhs) {
                return Double.valueOf(lhs.y - lhs.x).compareTo(rhs.y - rhs.x);
            }
        };

        // top-left corner = minimal sum
        result[0] = Collections.min(srcPoints, sumComparator);

        // bottom-right corner = maximal sum
        result[2] = Collections.max(srcPoints, sumComparator);

        // top-right corner = minimal diference
        result[1] = Collections.min(srcPoints, diffComparator);

        // bottom-left corner = maximal diference
        result[3] = Collections.max(srcPoints, diffComparator);

        return result;
    }

    private ArrayList<MatOfPoint> findContours(Mat src) {

        Mat grayImage = null;
        Mat cannedImage = null;
        Mat resizedImage = null;

        double ratio = src.size().height / 500;
        int height = Double.valueOf(src.size().height / ratio).intValue();
        int width = Double.valueOf(src.size().width / ratio).intValue();
        Size size = new Size(width, height);

        resizedImage = new Mat(size, CvType.CV_8UC4);
        grayImage = new Mat(size, CvType.CV_8UC4);
        cannedImage = new Mat(size, CvType.CV_8UC1);

        Imgproc.resize(src, resizedImage, size);
        Imgproc.cvtColor(resizedImage, grayImage, Imgproc.COLOR_RGBA2GRAY, 4);
        Imgproc.GaussianBlur(grayImage, grayImage, new Size(5, 5), 0);
        Imgproc.Canny(grayImage, cannedImage, 80, 100, 3, false);

        ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();

        Imgproc.findContours(cannedImage, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        hierarchy.release();

        Collections.sort(contours, new Comparator<MatOfPoint>() {

            @Override
            public int compare(MatOfPoint lhs, MatOfPoint rhs) {
                return Double.valueOf(Imgproc.contourArea(rhs)).compareTo(Imgproc.contourArea(lhs));
            }
        });

        resizedImage.release();
        grayImage.release();
        cannedImage.release();

        return contours;
    }

    @ReactMethod
    public void getCoordinates(String base64Image, final Callback myCallback) {
        // Uri imgURI = Uri.parse(stringURI);
        // Mat imgMat = getMatFromURI(imgURI);

        final byte[] imgbytes = Base64.decode(base64Image, Base64.DEFAULT);

        BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(myContext) {
            @Override
            public void onManagerConnected(int status) {
                switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.d(TAG, "SUCCESS init openCV: " + status);
                    Mat imgMat = Imgcodecs.imdecode(new MatOfByte(imgbytes), Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);
                    myCallback.invoke(detectDocument(imgMat).previewPointsAsHash());
                }
                    break;
                default: {
                    Log.d(TAG, "ERROR init Opencv: " + status);
                    super.onManagerConnected(status);
                }
                    break;
                }
            }
        };

        if (!OpenCVLoader.initDebug()) {
            CustomOpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, myContext, mLoaderCallback);
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }
}
