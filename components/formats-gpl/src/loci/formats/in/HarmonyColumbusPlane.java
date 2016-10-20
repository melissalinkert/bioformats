/*
 * #%L
 * OME Bio-Formats package for reading and converting biological file formats.
 * %%
 * Copyright (C) 2005 - 2016 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package loci.formats.in;

import loci.formats.FormatTools;

import ome.units.quantity.Length;
import ome.units.quantity.Time;

import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.UnitsLength;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HarmonyColumbusPlane implements Comparable<HarmonyColumbusPlane> {

  private static final Logger LOGGER = LoggerFactory.getLogger(HarmonyColumbusPlane.class);

  public String filename;
  public int fileIndex;
  public int series;
  public int row;
  public int col;
  public int field;
  public int image = -1;
  public int x;
  public int y;
  public int z;
  public int t;
  public int c;
  public String channelName;
  public Length resolutionX;
  public Length resolutionY;
  public Length positionX;
  public Length positionY;
  public Length positionZ;
  public Time deltaT;
  public Length emWavelength;
  public Length exWavelength;
  public double magnification;
  public double lensNA;
  public String acqType;
  public String channelType;
  public String acqTime;

  @Override
  public int compareTo(HarmonyColumbusPlane p) {
     if (this.row != p.row) {
       return this.row - p.row;
     }

     if (this.col != p.col) {
       return this.col - p.col;
     }

     if (this.field != p.field) {
       return this.field - p.field;
     }

     if (this.t != p.t) {
       return this.t - p.t;
     }

     if (this.c != p.c) {
       return this.c - p.c;
     }

     return 0;
  }

  public void setPositionX(String value, String unit) {
    final double x = Double.parseDouble(value);
    try {
      UnitsLength ul = UnitsLength.fromString(unit);
      positionX = UnitsLength.create(x, ul);
    }
    catch (EnumerationException e) {
      LOGGER.debug("Could not parse unit '{}'", unit);
    }
  }

  public void setPositionY(String value, String unit) {
    final double y = Double.parseDouble(value);
    try {
      UnitsLength ul = UnitsLength.fromString(unit);
      positionY = UnitsLength.create(y, ul);
    }
    catch (EnumerationException e) {
      LOGGER.debug("Could not parse unit '{}'", unit);
    }
  }

  public void setPositionZ(String value, String unit) {
    final double z = Double.parseDouble(value);
    try {
      UnitsLength ul = UnitsLength.fromString(unit);
      positionZ = UnitsLength.create(z, ul);
    }
    catch (EnumerationException e) {
      LOGGER.debug("Could not parse unit '{}'", unit);
    }
  }

  public void setEmWavelength(String value, String unit) {
    final double wave = Double.parseDouble(value);
    if (wave > 0) {
      emWavelength = FormatTools.getWavelength(wave, unit);
    }
  }

  public void setExWavelength(String value, String unit) {
    final double wave = Double.parseDouble(value);
    if (wave > 0) {
      exWavelength = FormatTools.getWavelength(wave, unit);
    }
  }

  public void setResolutionX(String value, String unit) {
    resolutionX = FormatTools.getPhysicalSizeX(Double.parseDouble(value), unit);
  }

  public void setResolutionY(String value, String unit) {
    resolutionY = FormatTools.getPhysicalSizeY(Double.parseDouble(value), unit);
  }

  public void setDeltaT(String value, String unit) {
    final double t = Double.parseDouble(value);
    deltaT = FormatTools.getTime(t, unit);
  }

}


