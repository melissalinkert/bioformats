/*
 * #%L
 * OME Bio-Formats package for reading and converting biological file formats.
 * %%
 * Copyright (C) 2005 - 2013 Open Microscopy Environment:
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

import java.io.IOException;

import loci.common.GZipHandle;
import loci.common.IRandomAccess;
import loci.common.RandomAccessInputStream;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.meta.MetadataStore;
import ome.xml.model.primitives.PositiveFloat;

/**
 * MGHReader is the file format reader for MGH FreeSurfer files.
 * Specifications available at
 * http://surfer.nmr.mgh.harvard.edu/fswiki/FsTutorial/MghFormat
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/in/MGHReader.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/in/MGHReader.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public class MGHReader extends FormatReader {

  // -- Constants --

  private static final int HEADER_SIZE = 284;

  /** Pixel types. */
  private static final int UCHAR = 0;
  private static final int SHORT = 4;
  private static final int INT = 1;
  private static final int FLOAT = 3;

  // -- Fields --


  // -- Constructor --

  /** Constructs a new MGH reader. */
  public MGHReader() {
    super("Mass. General Hospital", new String[] {"mgh", "mgz"});
    domains = new String[] {FormatTools.MEDICAL_DOMAIN, FormatTools.LM_DOMAIN};
  }

  // -- IFormatReader API methods --

  /** @see loci.formats.IFormatReader#isThisType(RandomAccessInputStream) */
  public boolean isThisType(RandomAccessInputStream stream) throws IOException {
    return FormatTools.validStream(stream, HEADER_SIZE, false);
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);

    long planeSize = FormatTools.getPlaneSize(this);
    long offset = HEADER_SIZE + no * planeSize;

    in.seek(offset);
    readPlane(in, x, y, w, h, buf);

    return buf;
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (!fileOnly) {
    }
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  public void initFile(String id) throws FormatException, IOException {
    super.initFile(id);

    // GZip decompression is handled internally, if applicable
    if (checkSuffix(id, "mgz")) {
      IRandomAccess handle = new GZipHandle(id);
      in = new RandomAccessInputStream(handle);
    }
    else {
      in = new RandomAccessInputStream(id);
    }

    CoreMetadata m = core.get(0);

    LOGGER.info("Reading header");

    m.littleEndian = false;

    int version = in.readInt();
    addGlobalMeta("Version", version);

    m.sizeX = in.readInt();
    m.sizeY = in.readInt();
    m.sizeZ = in.readInt();
    m.sizeC = in.readInt();

    int type = in.readInt();
    switch (type) {
      case UCHAR:
        m.pixelType = FormatTools.UINT8;
        break;
      case SHORT:
        m.pixelType = FormatTools.INT16;
        break;
      case INT:
        m.pixelType = FormatTools.INT32;
        break;
      case FLOAT:
        m.pixelType = FormatTools.FLOAT;
        break;
    }

    int degreesOfFreedom = in.readInt();
    boolean goodRAS = in.readShort() != 0;
    double pixelSizeX = in.readFloat();
    double pixelSizeY = in.readFloat();
    double pixelSizeZ = in.readFloat();

    if (goodRAS) {
      float xr = in.readFloat();
      float xa = in.readFloat();
      float xs = in.readFloat();
      float yr = in.readFloat();
      float ya = in.readFloat();
      float ys = in.readFloat();
      float zr = in.readFloat();
      float za = in.readFloat();
      float zs = in.readFloat();
      float cr = in.readFloat();
      float ca = in.readFloat();
      float cs = in.readFloat();

      addGlobalMeta("Xr", xr);
      addGlobalMeta("Xa", xa);
      addGlobalMeta("Xs", xs);
      addGlobalMeta("Yr", yr);
      addGlobalMeta("Ya", ya);
      addGlobalMeta("Ys", ys);
      addGlobalMeta("Zr", zr);
      addGlobalMeta("Za", za);
      addGlobalMeta("Zs", zs);
      addGlobalMeta("Cr", cr);
      addGlobalMeta("Ca", ca);
      addGlobalMeta("Cs", cs);
    }

    m.sizeT = 1;
    m.imageCount = m.sizeZ * m.sizeC;
    m.dimensionOrder = "XYZCT";

    MetadataStore store = makeFilterMetadata();
    MetadataTools.populatePixels(store, this);

    store.setPixelsPhysicalSizeX(new PositiveFloat(pixelSizeX), 0);
    store.setPixelsPhysicalSizeY(new PositiveFloat(pixelSizeY), 0);
    store.setPixelsPhysicalSizeZ(new PositiveFloat(pixelSizeZ), 0);
  }

}
