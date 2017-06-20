package com.ociweb.iot.maker;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.PubSubWriter;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.iot.hardware.HardwareImpl;
import com.ociweb.iot.hardware.impl.SerialDataSchema;
import com.ociweb.iot.hardware.impl.SerialOutputSchema;
import com.ociweb.pronghorn.iot.schema.GroveRequestSchema;
import com.ociweb.pronghorn.iot.schema.I2CCommandSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

/**
 * Represents a dedicated channel for communicating with a single device
 * or resource on an IoT system.
 * 
 */
public abstract class FogCommandChannel extends GreenCommandChannel<HardwareImpl> {

    protected final Pipe<I2CCommandSchema> i2cOutput;  //TODO: find a way to not create if not used like http or message pup/sub
    protected final Pipe<GroveRequestSchema> pinOutput; //TODO: find a way to not create if not used like http or message pup/sub

    protected final Pipe<SerialOutputSchema> serialOutput;
    
    public static final int ANALOG_BIT = 0x40; //added to connection to track if this is the analog .0vs digital
    protected static final long MS_TO_NS = 1_000_000;
    
    protected DataOutputBlobWriter<I2CCommandSchema> i2cWriter;  
    protected int runningI2CCommandCount;
    
    //TODO: need to set this as a constant driven from the known i2c devices and the final methods, what is the biggest command sequence?
    protected final int maxCommands = 50;

    public static final int I2C_WRITER      = 1<<29;
    public static final int PIN_WRITER      = 1<<28;
    public static final int SERIAL_WRITER   = 1<<27;
    public static final int BT_WRITER       = 1<<26;
    
	protected final byte i2cPipeIdx;
	private final byte serialPipeIdx;
    
	private final int MAX_COMMAND_FRAGMENTS_SIZE;
        

	
	
    protected FogCommandChannel(GraphManager gm, HardwareImpl hardware, int features, int parallelInstanceId, PipeConfigManager pcm) {
    	    	
       super(gm, hardware, features, parallelInstanceId, pcm);
    	    	
       //TODO: must also check for command channel recursively when looking....
       this.pinOutput = new Pipe<GroveRequestSchema>(pcm.getConfig(GroveRequestSchema.class));
       
       boolean setupSerial = (0 != (features & SERIAL_WRITER));//if feature bit is on then set for write...
       if (setupSerial) {
    	   serialOutput = newSerialOutputPipe(pcm.getConfig(SerialOutputSchema.class), hardware);
       } else {
    	   serialOutput = null;
       }
       
       
       
       //TODO: if features is I2C and if I2C is configured both, add assert check.
       boolean setupI2C = true;//right now this must always be on expcially for pi, hardware.isUseI2C();	  
       	   
       if (setupI2C) {
    	   //yes i2c usage
	       optionalOutputPipes = new Pipe<?>[]{
		    	   this.pinOutput,
		    	   this.i2cOutput = new Pipe<I2CCommandSchema>(pcm.getConfig(I2CCommandSchema.class))
	    	   };
	                             
	       if (Pipe.sizeOf(i2cOutput, I2CCommandSchema.MSG_COMMAND_7)*maxCommands >= this.i2cOutput.sizeOfSlabRing) {
	           throw new UnsupportedOperationException("maxCommands too large or pipe is too small, pipe size must be at least "+(Pipe.sizeOf(i2cOutput, I2CCommandSchema.MSG_COMMAND_7)*maxCommands));
	       }
	       MAX_COMMAND_FRAGMENTS_SIZE = Pipe.sizeOf(i2cOutput, I2CCommandSchema.MSG_COMMAND_7)*maxCommands;
       } else {
    	   i2cOutput=null;
    	   MAX_COMMAND_FRAGMENTS_SIZE = 0;
    	   
    	   //non i2c usage (TODO: THIS IS NEW CODE UNDER TEST)
	       optionalOutputPipes = new Pipe<?>[]{
	    	   this.pinOutput
    	   }; 
       }
       
       //////////////////////////
       //////////////////////////
       
       int optionalPipeCount = 0;
       if (null != serialOutput) {
    	   optionalPipeCount++;
       }
       if (null != pinOutput) {
    	   optionalPipeCount++;
       }
       if (null != i2cOutput) {
    	   optionalPipeCount++;
       }
       optionalOutputPipes = new Pipe<?>[optionalPipeCount];
       
       
       if (null!=serialOutput) {
    	   serialPipeIdx = (byte)--optionalPipeCount;
    	   optionalOutputPipes[serialPipeIdx] = serialOutput;
       } else {
    	   serialPipeIdx = -1;
       }
       if (null!=i2cOutput) {
    	   i2cPipeIdx = (byte)(--optionalPipeCount);
    	   optionalOutputPipes[i2cPipeIdx] = i2cOutput;
       } else {
    	   i2cPipeIdx = -1;
       }
       if (null!=pinOutput) {
    	   optionalOutputPipes[--optionalPipeCount] = pinOutput;
       }
       
    }
    
    
    private static Pipe<SerialOutputSchema> newSerialOutputPipe(PipeConfig<SerialOutputSchema> config,HardwareImpl hardware) {
    	return new Pipe<SerialOutputSchema>(config) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataOutputBlobWriter<SerialOutputSchema> createNewBlobWriter() {
				return new SerialWriter(this, HardwareImpl.serialIndex(hardware));
			}    		
    	};
    }
    
    protected boolean enterBlockOk() {
        return aBool.compareAndSet(false, true);
    }
    
    protected boolean exitBlockOk() {
        return aBool.compareAndSet(true, false);
    }

    /**
     * Causes this channel to delay processing any actions until the specified
     * amount of time has elapsed.
     *
     * @param msDuration Milliseconds to delay.
     *
     * @return True if blocking was successful, and false otherwise.
     */
    public abstract boolean block(long msDuration);

    /**
     * Causes this channel to delay processing any actions on a given {@link Port}
     * until the specified amount of time has elapsed.
     *
     * @param port Port to temporarily stop processing actions on.
     * @param durationMilli Milliseconds until the port will process actions again.
     *
     * @return True if blocking was successful, and false otherwise.
     */
    public abstract boolean block(Port port, long durationMilli);

    /**
     * Causes this channel to delay processing any actions on a given {@link Port}
     * until the specified UNIX time is reached.
     *
     * @param port Port to temporarily stop processing actions on.
     * @param time Time, in milliseconds, since the UNIX epoch that indicates
     *             when actions should resume processing.
     *
     * @return True if blocking was successful, and false otherwise.
     */
    public abstract boolean blockUntil(Port port, long time);

    /**
     * Sets the value of an analog/digital port on this command channel.
     *
     * @param port {@link Port} to set the value of.
     * @param value true is set to on full and false is set to off full.
     *
     * @return True if the port could be set, and false otherwise.
     */
    public abstract boolean setValue(Port port, boolean value);
    
    /**
     * Sets the value of an analog/digital port on this command channel.
     *
     * @param port {@link Port} to set the value of.
     * @param value Value to set the port to.
     *
     * @return True if the port could be set, and false otherwise.
     */
    public abstract boolean setValue(Port port, int value);

    /**
     * Sets the value of an analog/digital port on this command channel and then
     * delays processing of all future actions on this port until a specified
     * amount of time passes.
     *
     * @param port {@link Port} to set the value of.
     * @param value Value to set the port to.
     * @param durationMilli Time in milliseconds to delay processing of future actions
     *                      on this port.
     *
     * @return True if the port could be set, and false otherwise.
     */
    public abstract boolean setValueAndBlock(Port port, boolean value, long durationMilli);
    
    /**
     * Sets the value of an analog/digital port on this command channel and then
     * delays processing of all future actions on this port until a specified
     * amount of time passes.
     *
     * @param port {@link Port} to set the value of.
     * @param value Value to set the port to.
     * @param durationMilli Time in milliseconds to delay processing of future actions
     *                      on this port.
     *
     * @return True if the port could be set, and false otherwise.
     */
    public abstract boolean setValueAndBlock(Port port, int value, long durationMilli);

    /**
     * "Pulses" a given port, setting its state to True/On and them immediately
     * setting its state to False/Off.
     *
     * @param port {@link Port} to pulse.
     *
     * @return True if the port could be pulsed, and false otherwise.
     */
    public abstract boolean digitalPulse(Port port);

    /**
     * "Pulses" a given port, setting its state to True/On and them immediately
     * setting its state to False/Off.
     *
     * @param port {@link Port} to pulse.
     * @param durationNanos Time in nanoseconds to sustain the pulse for.
     *
     * @return True if the port could be pulsed, and false otherwise.
     */
    public abstract boolean digitalPulse(Port port, long durationNanos);

    public boolean publishSerial(SerialWritable writable) {
        assert(writable != null);
        assert((0 != (initFeatures & SERIAL_WRITER))) : "CommandChannel must be created with SERIAL_WRITER flag";
        
        if (goHasRoom() && 
        	PipeWriter.tryWriteFragment(serialOutput, SerialDataSchema.MSG_CHUNKEDSTREAM_1)) {
  	
        	SerialWriter pw = (SerialWriter) Pipe.outputStream(serialOutput);
            pw.openField(SerialDataSchema.MSG_CHUNKEDSTREAM_1_FIELD_BYTEARRAY_2, this);            
            writable.write(pw);//TODO: cool feature, writable to return false to abandon write.. 
            
            pw.closeHighLevelField(SerialDataSchema.MSG_CHUNKEDSTREAM_1_FIELD_BYTEARRAY_2);
            
            PipeWriter.publishWrites(serialOutput);     
            
            GreenCommandChannel.publishGo(1, serialPipeIdx, (GreenCommandChannel<?>) this);
            
            return true;
            
        } else {
            return false;
        }
    }
    
    
    /**
     * Opens an I2C connection.
     *
     * @param targetAddress I2C address to open a connection to.
     *
     * @return An {@link DataOutputBlobWriter} with an {@link I2CCommandSchema} that's
     *         connected to the specified target address.
     *
     */
    public DataOutputBlobWriter<I2CCommandSchema> i2cCommandOpen(int targetAddress) {       
        assert(enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
        try {

            if (PipeWriter.tryWriteFragment(i2cOutput, I2CCommandSchema.MSG_COMMAND_7)) {
                PipeWriter.writeInt(i2cOutput, I2CCommandSchema.MSG_COMMAND_7_FIELD_ADDRESS_12, targetAddress);

                if (null==i2cWriter) {
                    //TODO: add init method that we we will call from stage to do this.
                    i2cWriter = new DataOutputBlobWriter<I2CCommandSchema>(i2cOutput);//hack for now until we can get this into the scheduler TODO: nathan follow up.
                }
                 
                DataOutputBlobWriter.openField(i2cWriter);
                return i2cWriter;
            } else {
                throw new UnsupportedOperationException("Pipe is too small for large volume of i2c data");
            }
        } finally {
            assert(exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
        }
        
    }

    /**
     * Triggers a delay for a given I2C address.
     *
     * @param targetAddress I2C address to trigger a delay on.
     * @param durationNanos Time in nanoseconds to delay.
     */
    public void i2cDelay(int targetAddress, long durationNanos) {
        assert(enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
        try {
            if (++runningI2CCommandCount > maxCommands) {
                throw new UnsupportedOperationException("too many commands, found "+runningI2CCommandCount+" but only left room for "+maxCommands);
            }
        
            if (goHasRoom() && PipeWriter.tryWriteFragment(i2cOutput, I2CCommandSchema.MSG_BLOCKCONNECTION_20)) {

                PipeWriter.writeInt(i2cOutput, I2CCommandSchema.MSG_BLOCKCONNECTION_20_FIELD_CONNECTOR_11, targetAddress);
                PipeWriter.writeInt(i2cOutput, I2CCommandSchema.MSG_BLOCKCONNECTION_20_FIELD_ADDRESS_12, targetAddress);
                PipeWriter.writeLong(i2cOutput, I2CCommandSchema.MSG_BLOCKCONNECTION_20_FIELD_DURATIONNANOS_13, durationNanos);

                PipeWriter.publishWrites(i2cOutput);

            }else {
                throw new UnsupportedOperationException("Pipe is too small for large volume of i2c data");
            }    
            
        } finally {
            assert(exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
        }
        
    }

    /**
     * @return True if the I2C bus is ready for communication, and false otherwise.
     */
    public boolean i2cIsReady() {
        return goHasRoom() && PipeWriter.hasRoomForFragmentOfSize(i2cOutput, MAX_COMMAND_FRAGMENTS_SIZE);
       
    }

    /**
     * Flushes all awaiting I2C data to the I2C bus for consumption.
     */
    public void i2cFlushBatch() {        
        assert(enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
        try {
        	builder.releaseI2CTraffic(runningI2CCommandCount, this);        	
            runningI2CCommandCount = 0;
        } finally {
            assert(exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
        }
    }

    /**
     * TODO: What does this do?
     */
    public void i2cCommandClose() {  
        assert(enterBlockOk()) : "Concurrent usage error, ensure this never called concurrently";
        try {
            if (++runningI2CCommandCount > maxCommands) {
                throw new UnsupportedOperationException("too many commands, found "+runningI2CCommandCount+" but only left room for "+maxCommands);
            }
            
            DataOutputBlobWriter.closeHighLevelField(i2cWriter, I2CCommandSchema.MSG_COMMAND_7_FIELD_BYTEARRAY_2);
            PipeWriter.publishWrites(i2cOutput);
        } finally {
            assert(exitBlockOk()) : "Concurrent usage error, ensure this never called concurrently";      
        }        
    }


}