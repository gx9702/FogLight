/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ociweb.iot.astropi;

/**
 *
 * @author huydo
 */
public interface AccelListener extends AstroPiListener {
    void accelEvent(double x,double y,double z);
}
