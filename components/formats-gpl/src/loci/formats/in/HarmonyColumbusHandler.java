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

import java.util.ArrayList;
import java.util.HashMap;

import loci.common.Location;
import loci.common.xml.BaseHandler;

import org.xml.sax.Attributes;

public class HarmonyColumbusHandler extends BaseHandler {
  private static final String ROOT_ELEMENT = "EvaluationInputData";

  // -- Fields --

  private String currentId;

  private String currentName;
  private HarmonyColumbusPlane activePlane;
  private String currentUnit;

  private String displayName;
  private String plateID;
  private String measurementTime;
  private String plateName;
  private String plateDescription;
  private int plateRows, plateCols;
  private ArrayList<HarmonyColumbusPlane> planes = new ArrayList<HarmonyColumbusPlane>();

  private StringBuffer currentValue = new StringBuffer();

  private ArrayList<String> elementNames = new ArrayList<String>();

  private HashMap<String, String> metadata = new HashMap<String, String>();
  private HashMap<String, Integer> keyCounter = new HashMap<String, Integer>();

  private Attributes currentAttributes;

  public HarmonyColumbusHandler(String currentId) {
    super();
    this.currentId = currentId;
  }

  // -- HarmonyHandler API methods --

  public HashMap<String, String> getMetadataMap() {
    return metadata;
  }

  public ArrayList<HarmonyColumbusPlane> getPlanes() {
    return planes;
  }

  public String getExperimenterName() {
    return displayName;
  }

  public String getPlateIdentifier() {
    return plateID;
  }

  public String getMeasurementTime() {
    return measurementTime;
  }

  public String getPlateName() {
    return plateName;
  }

  public String getPlateDescription() {
    return plateDescription;
  }

  public int getPlateRows() {
    return plateRows;
  }

  public int getPlateColumns() {
    return plateCols;
  }

  // -- DefaultHandler API methods --

  @Override
  public void characters(char[] ch, int start, int length) {
    String value = new String(ch, start, length);
    currentValue.append(value);
  }

  @Override
  public void startElement(String uri, String localName, String qName,
    Attributes attributes)
  {
    if (!qName.equals(ROOT_ELEMENT)) {
      elementNames.add(qName);
    }
    currentValue.setLength(0);

    if (qName.equals("Image") && attributes.getValue("id") == null) {
      activePlane = new HarmonyColumbusPlane();
    }

    currentUnit = attributes.getValue("Unit");
    currentAttributes = attributes;
  }

  @Override
  public void endElement(String uri, String localName, String qName) {
    String value = currentValue.toString();
    if (value.trim().length() == 0) {
      if (qName.equals("Image") && activePlane != null) {
        planes.add(activePlane);
      }
      if (!qName.equals(ROOT_ELEMENT)) {
        elementNames.remove(elementNames.size() - 1);
      }
      currentUnit = null;
      return;
    }

    int elementCount = elementNames.size();
    String currentName = null;
    if (elementCount > 0) {
      currentName = elementNames.get(elementCount - 1);
    }
    String parentName = null;
    if (elementCount > 1) {
      parentName = elementNames.get(elementCount - 2);
    }

    if (parentName == null) {
      metadata.put(currentName, value);
    }
    else {
      int keyCount = 1;
      if (keyCounter.containsKey(parentName)) {
        keyCount = keyCounter.get(parentName);
      }

      metadata.put(parentName + " #" + keyCount + " " + currentName, value);
    }

    if (keyCounter.containsKey(currentName) || elementNames.size() == 2) {
      int keyCount = 1;
      if (keyCounter.containsKey(currentName)) {
        keyCount = keyCounter.get(currentName);
      }
      keyCounter.put(currentName, keyCount + 1);
    }

    if ("Plate".equals(parentName)) {
      if ("Name".equals(currentName)) {
        plateName = value;
      }
      else if ("PlateTypeName".equals(currentName)) {
        plateDescription = value;
      }
      else if ("PlateRows".equals(currentName)) {
        plateRows = Integer.parseInt(value);
      }
      else if ("PlateColumns".equals(currentName)) {
        plateCols = Integer.parseInt(value);
      }
      else if ("PlateID".equals(currentName)) {
        plateID = value;
      }
      else if ("MeasurementStartTime".equals(currentName)) {
        measurementTime = value;
      }
    }

    if ("User".equals(currentName)) {
      displayName = value;
    }
    else if (activePlane != null && "Image".equals(parentName)) {
      if ("URL".equals(currentName)) {
        if (value.startsWith("http")) {
          activePlane.filename = value;
        }
        else {
          Location parent =
            new Location(currentId).getAbsoluteFile().getParentFile();
          activePlane.filename = new Location(parent, value).getAbsolutePath();
        }
        String buffer = currentAttributes.getValue("BufferNo");
        if (buffer != null) {
          activePlane.fileIndex = Integer.parseInt(buffer);
        }
      }
      else if ("Row".equals(currentName)) {
        activePlane.row = Integer.parseInt(value) - 1;
      }
      else if ("Col".equals(currentName)) {
        activePlane.col = Integer.parseInt(value) - 1;
      }
      else if ("FieldID".equals(currentName)) {
        activePlane.field = Integer.parseInt(value);
      }
      else if ("PlaneID".equals(currentName)) {
        activePlane.z = Integer.parseInt(value);
      }
      else if ("ImageSizeX".equals(currentName)) {
        activePlane.x = Integer.parseInt(value);
      }
      else if ("ImageSizeY".equals(currentName)) {
        activePlane.y = Integer.parseInt(value);
      }
      else if ("TimepointID".equals(currentName)) {
        activePlane.t = Integer.parseInt(value);
      }
      else if ("ChannelID".equals(currentName)) {
        activePlane.c = Integer.parseInt(value);
      }
      else if ("ChannelName".equals(currentName)) {
        activePlane.channelName = value;
      }
      else if ("ImageResolutionX".equals(currentName)) {
        activePlane.setResolutionX(value, currentUnit);
      }
      else if ("ImageResolutionY".equals(currentName)) {
        activePlane.setResolutionY(value, currentUnit);
      }
      else if ("PositionX".equals(currentName)) {
        activePlane.setPositionX(value, currentUnit);
      }
      else if ("PositionY".equals(currentName)) {
        activePlane.setPositionY(value, currentUnit);
      }
      else if ("PositionZ".equals(currentName)) {
        activePlane.setPositionZ(value, currentUnit);
      }
      else if ("MeasurementTimeOffset".equals(currentName)) {
        activePlane.setDeltaT(value, currentUnit);
      }
      else if ("ObjectiveMagnification".equals(currentName)) {
        activePlane.magnification = Double.parseDouble(value);
      }
      else if ("ObjectiveNA".equals(currentName)) {
        activePlane.lensNA = Double.parseDouble(value);
      }
      else if ("MainEmissionWavelength".equals(currentName)) {
        activePlane.setEmWavelength(value, currentUnit);
      }
      else if ("MainExcitationWavelength".equals(currentName)) {
        activePlane.setExWavelength(value, currentUnit);
      }
      else if ("AcquisitionType".equals(currentName)) {
        activePlane.acqType = value;
      }
      else if ("ChannelType".equals(currentName)) {
        activePlane.channelType = value;
      }
      else if ("AbsTime".equals(currentName)) {
        activePlane.acqTime = value;
      }
    }

    if (qName.equals("Image") && activePlane != null) {
      planes.add(activePlane);
    }

    if (!qName.equals(ROOT_ELEMENT)) {
      elementNames.remove(elementNames.size() - 1);
    }
    currentUnit = null;
  }

}
