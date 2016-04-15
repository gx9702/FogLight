// File: Pi2CExample.java
// Project: PronghornIoT
// Since: Mar 25, 2016
//
///////////////////////////////////////////////////////////////////////////////
// Copyright (c) Brandon Sanders [brandon@alicorn.io]
//
// All rights reserved.
///////////////////////////////////////////////////////////////////////////////
//
package com.ociweb.device.testApps;

import com.ociweb.device.grove.schema.GroveResponseSchema;
import com.ociweb.device.grove.schema.I2CCommandSchema;
import com.ociweb.device.impl.Grove_LCD_RGB;
import com.ociweb.pronghorn.iot.Stack;
import com.ociweb.pronghorn.iot.i2c.I2CStage;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.ThreadPerStageScheduler;
import com.ociweb.pronghorn.stage.test.ByteArrayProducerStage;
import com.ociweb.pronghorn.stage.test.ConsoleJSONDumpStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Example of controlling the I2C channels on a Raspberry Pi and GrovePi+.
 *
 * @author Brandon Sanders [brandon@alicorn.io]
 */
public final class GroveI2CExample extends Stack {
//Private//////////////////////////////////////////////////////////////////////

    private static final Logger logger = LoggerFactory.getLogger(GroveI2CExample.class);

    private static final PipeConfig<GroveResponseSchema> responseConfig = new PipeConfig<GroveResponseSchema>(GroveResponseSchema.instance, 30, 0);
    private static final PipeConfig<I2CCommandSchema> requestI2CConfig = new PipeConfig<I2CCommandSchema>(I2CCommandSchema.instance, 32, 128);

//Public///////////////////////////////////////////////////////////////////////

    @Override public void start() {
        GraphManager gm = new GraphManager();

        Pipe<GroveResponseSchema> responsePipe = new Pipe<GroveResponseSchema>(responseConfig);

//        GroveShieldV2ResponseStage groveStage = new GroveShieldV2ResponseStage(gm, responsePipe, config);
//        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 10 * 1000 * 1000, groveStage); //poll every 10 ms

        ConsoleJSONDumpStage<GroveResponseSchema> dump = new ConsoleJSONDumpStage<GroveResponseSchema>(gm, responsePipe);
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 500 * 1000 * 1000, dump);

//        if (config.configI2C) {

            Pipe<I2CCommandSchema> i2cToBusPipe = new Pipe<I2CCommandSchema>(requestI2CConfig);

            //Random string list.
            String[] randomStrings = {
                    "GrovePi+ with\nPronghorn IoT",
                    "Hello,\nPronghorn!",
                    "Embedded\nZulu Java",
                    "Hello,\nGrovePi+!",
                    "I'm sorry Dave,\nI can't do that."
            };

            //Build command.
            Random rand = new Random();
            byte[] rawData = Grove_LCD_RGB.commandForTextAndColor(randomStrings[rand.nextInt(randomStrings.length)],
                                                                  rand.nextInt(256),
                                                                  rand.nextInt(256),
                                                                  rand.nextInt(256));

            //Calculate chunk sizes; for now, we assume every chunk is 3 bytes long.
            int[] chunkSizes = new int[rawData.length / 3];
            for (int i = 0; i < chunkSizes.length; i++) chunkSizes[i] = 3;

            //Pipe that data.
            ByteArrayProducerStage prodStage = new ByteArrayProducerStage(gm, rawData, chunkSizes, i2cToBusPipe);
//            I2CStage i2cStage = new I2CStage(gm, i2cToBusPipe, new I2CGroveJavaBacking(config));
            I2CStage i2cStage = new I2CStage(gm, i2cToBusPipe);
//        }

        //TODO: need to finish ColorMinusScheduler found in same package in Pronghorn as this scheduler
        //      then for edison use only 1 or 2 threads for doing all the work.
        ThreadPerStageScheduler scheduler = new ThreadPerStageScheduler(gm);

        scheduler.startup();

        try {
            Thread.sleep(60000 * 60 * 24); //shuts off server if you leave it running for a full day.
        }

        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        scheduler.shutdown();
        scheduler.awaitTermination(5, TimeUnit.SECONDS);

        //must wait until all stages are done using the configuration
//        config.cleanup();
    }
}
