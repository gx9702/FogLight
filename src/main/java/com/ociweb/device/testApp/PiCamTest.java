package com.ociweb.device.testApp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.iot.maker.Hardware;
import com.ociweb.iot.maker.FogApp;
import com.ociweb.iot.maker.ImageListener;

/**
 * Simple Pi image capture test.
 *
 * @author Brandon Sanders [brandon@alicorn.io]
 */
public class PiCamTest implements FogApp {

    private final List<File> images = new ArrayList<>();

    public static void main( String[] args) {
        FogRuntime.run(new PiCamTest());
    }

    @Override
    public void declareConnections(Hardware hardware) {
        hardware.setImageTriggerRate(5000);
    }

    @Override
    public void declareBehavior(FogRuntime runtime) {
        runtime.addImageListener(new ImageListener() {
            @Override
            public void onImage(String imagePath) {
                System.out.print("Grove Pi Captured Image: ");
                System.out.println(imagePath);

                File image = new File(imagePath);
                images.add(image);

                if (images.size() > 50) {
                    File f = images.get(0);
                    images.remove(0);
                    f.delete();
                }
            }
        });
    }
}
