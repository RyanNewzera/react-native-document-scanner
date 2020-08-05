#import <AVFoundation/AVFoundation.h>
#import <CoreMedia/CoreMedia.h>
#import <CoreVideo/CoreVideo.h>
#import <CoreImage/CoreImage.h>
#import <ImageIO/ImageIO.h>
#import <GLKit/GLKit.h>

#import "RNPdfScannerManager.h"
#import "DocumentScannerView.h"

@interface RNPdfScannerManager()
@property (strong, nonatomic) DocumentScannerView *scannerView;
@end

@implementation RNPdfScannerManager

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}

RCT_EXPORT_MODULE()

RCT_EXPORT_VIEW_PROPERTY(onPictureTaken, RCTDirectEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onRectangleDetect, RCTBubblingEventBlock)


RCT_EXPORT_VIEW_PROPERTY(overlayColor, UIColor)
RCT_EXPORT_VIEW_PROPERTY(enableTorch, BOOL)
RCT_EXPORT_VIEW_PROPERTY(useFrontCam, BOOL)
RCT_EXPORT_VIEW_PROPERTY(useBase64, BOOL)
RCT_EXPORT_VIEW_PROPERTY(saveInAppDocument, BOOL)
RCT_EXPORT_VIEW_PROPERTY(captureMultiple, BOOL)
RCT_EXPORT_VIEW_PROPERTY(detectionCountBeforeCapture, NSInteger)
RCT_EXPORT_VIEW_PROPERTY(detectionRefreshRateInMS, NSInteger)
RCT_EXPORT_VIEW_PROPERTY(saturation, float)
RCT_EXPORT_VIEW_PROPERTY(quality, float)
RCT_EXPORT_VIEW_PROPERTY(brightness, float)
RCT_EXPORT_VIEW_PROPERTY(contrast, float)

RCT_EXPORT_METHOD(capture) {

    [_scannerView capture];
}

- (CIRectangleFeature *)biggestRectangleInRectangles:(NSArray *)rectangles
{
    if (![rectangles count]) return nil;

    float halfPerimiterValue = 0;

    CIRectangleFeature *biggestRectangle = [rectangles firstObject];

    for (CIRectangleFeature *rect in rectangles)
    {
        CGPoint p1 = rect.topLeft;
        CGPoint p2 = rect.topRight;
        CGFloat width = hypotf(p1.x - p2.x, p1.y - p2.y);

        CGPoint p3 = rect.topLeft;
        CGPoint p4 = rect.bottomLeft;
        CGFloat height = hypotf(p3.x - p4.x, p3.y - p4.y);

        CGFloat currentHalfPerimiterValue = height + width;

        if (halfPerimiterValue < currentHalfPerimiterValue)
        {
            halfPerimiterValue = currentHalfPerimiterValue;
            biggestRectangle = rect;
        }
    }

    return biggestRectangle;
}

RCT_EXPORT_METHOD(getCoordinates:(NSString *)uri completion:(RCTResponseSenderBlock)callback) 
{
    NSString *parsedImageUri = [uri stringByReplacingOccurrencesOfString:@"file://" withString:@""];
    
    NSURL *fileURL = [NSURL fileURLWithPath:parsedImageUri];
    
    CIImage *image = [CIImage imageWithContentsOfURL:fileURL];
    
    image = [image imageByApplyingCGOrientation: kCGImagePropertyOrientationDownMirrored];

    CIDetector* detector = [CIDetector detectorOfType:CIDetectorTypeRectangle 
                            context:nil 
                            options:@{CIDetectorAccuracy : CIDetectorAccuracyHigh}];
  

    CIRectangleFeature *rectangleFeature = [self biggestRectangleInRectangles:[detector featuresInImage:image]];

    NSDictionary *rectangleCoordinates = rectangleFeature ? @{
                                     @"bottomLeft": @{ @"y": @(rectangleFeature.topLeft.y), @"x": @(rectangleFeature.topLeft.x)},
                                     @"bottomRight": @{ @"y": @(rectangleFeature.topRight.y), @"x": @(rectangleFeature.topRight.x)},
                                     @"topLeft": @{ @"y": @(rectangleFeature.bottomLeft.y), @"x": @(rectangleFeature.bottomLeft.x)},
                                     @"topRight": @{ @"y": @(rectangleFeature.bottomRight.y), @"x": @(rectangleFeature.bottomRight.x)},
                                     } : [NSNull null];
    
    callback(@[rectangleCoordinates]);
}

- (UIView*) view {
    _scannerView = [[DocumentScannerView alloc] init];
    return _scannerView;
}

@end
