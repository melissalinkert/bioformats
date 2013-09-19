/*
 * #%L
 * OME SCIFIO package for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2005 - 2013 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */

package loci.formats.in;

import java.io.IOException;

import loci.common.Location;
import loci.formats.FormatException;
import loci.formats.meta.MetadataStore;

import ome.xml.model.primitives.Color;

/**
 * CombinedOMETiffReader is the file format reader for
 * <a href="http://ome-xml.org/wiki/OmeTiff">OME-TIFF</a> files with channel
 * names in the file name.
 *
 */
public class CombinedOMETiffReader extends OMETiffReader {

  // -- Constructor --

  /** Constructs a new OME-TIFF reader. */
  public CombinedOMETiffReader() {
    super();
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(String, boolean) */
  public boolean isThisType(String name, boolean open) {
    boolean isOMETiff = super.isThisType(name, open);
    String localName = new Location(name).getName();
    return isOMETiff && localName.startsWith("combined_");
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    super.initFile(id);

    // parse the channel names from the file name
    // names are separated by an underscore, and succeed the "combined_" prefix

    String name = new Location(id).getName();
    String[] tokens = name.split("_");
    String[] channelNames = new String[tokens.length - 1];
    System.arraycopy(tokens, 1, channelNames, 0, channelNames.length);

    // fix the last name, as it will end with ".ome.*"

    String lastName = channelNames[channelNames.length - 1];
    lastName = lastName.substring(0,
      lastName.lastIndexOf(".", lastName.lastIndexOf(".") - 1));
    channelNames[channelNames.length - 1] = lastName;

    // map the channel names to colors

    Color[] channelColors = new Color[channelNames.length];
    for (int c=0; c<channelColors.length; c++) {
      String channelName = channelNames[c];
      if (channelName.equals("DAPI")) {
        // blue
        channelColors[c] = new Color(0, 0, 255, 255);
      }
      else if (channelName.equals("FLAG")) {
        // red
        channelColors[c] = new Color(255, 0, 0, 255);
      }
      else {
        // green
        channelColors[c] = new Color(0, 255, 0, 255);
      }
    }

    // add channel data to MetadataStore

    MetadataStore store = getMetadataStore();
    for (int i=0; i<getSeriesCount(); i++) {
      for (int c=0; c<channelNames.length; c++) {
        if (c < getEffectiveSizeC()) {
          store.setChannelName(channelNames[c], i, c);
          store.setChannelColor(channelColors[c], i, c);
        }
      }
    }
  }

}
